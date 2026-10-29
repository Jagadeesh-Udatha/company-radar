package dev.jagadeesh.radar;

import dev.jagadeesh.radar.crawl.PageFetcher;
import dev.jagadeesh.radar.profile.CompanyProfile;
import dev.jagadeesh.radar.profile.CompanyStore;
import dev.jagadeesh.radar.search.CompanySearch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Crawl → MongoDB → text-index search, against a real MongoDB and a fake website.
 * Uses MONGO_TEST_URI when set, otherwise Testcontainers. Skipped when neither is available.
 */
@SpringBootTest(properties = "radar.crawl.min-delay=0s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("backendsAvailable")
class RadarFlowIntegrationTest {

    static MongoDBContainer mongoContainer;

    static boolean backendsAvailable() {
        return System.getenv("MONGO_TEST_URI") != null
                || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        String mongo = System.getenv("MONGO_TEST_URI");
        if (mongo == null) {
            mongoContainer = new MongoDBContainer("mongo:7.0");
            mongoContainer.start();
            mongo = mongoContainer.getReplicaSetUrl("radar_test");
        }
        String m = mongo;
        registry.add("spring.data.mongodb.uri", () -> m);
    }

    @AfterAll
    static void stop() {
        if (mongoContainer != null) mongoContainer.stop();
    }

    @TestConfiguration
    static class FakeWeb {
        @Bean
        @Primary
        PageFetcher fakeSite() {
            return FakeSite.acme();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired CompanySearch search;
    @Autowired CompanyStore store;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().drop();
        search.createIndexes();
    }

    @Test
    void crawlJobBuildsProfileAndMakesItSearchable() throws Exception {
        String body = mvc.perform(post("/api/crawls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://acme.example/\",\"maxPages\":10,\"maxDepth\":2}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/crawls/" + jobId)).andExpect(jsonPath("$.status").value("DONE")));

        mvc.perform(get("/api/companies/acme.example"))
                .andExpect(jsonPath("$.name").value("Acme Pay"))
                .andExpect(jsonPath("$.careersUrl").value("https://acme.example/careers"));
        assertThat(mongo.getCollection("pages").countDocuments()).isEqualTo(4);

        mvc.perform(get("/api/companies/search").param("q", "payments").param("hiring", "true"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].site").value("acme.example"))
                .andExpect(jsonPath("$.hits[0].snippet").value(org.hamcrest.Matchers.containsString("<em>")))
                .andExpect(jsonPath("$.sectors.fintech").value(1));
    }

    @Test
    void filtersAndFacets() {
        Instant now = Instant.now();
        List.of(
                new CompanyProfile("acme.example", "Acme Pay", "UPI payments for merchants", "fintech",
                        List.of("fintech", "saas"), "https://acme.example/careers", null, true, 3, now),
                new CompanyProfile("ledgerly.example", "Ledgerly", "Lending and credit APIs for banks", "fintech",
                        List.of("fintech"), null, null, false, 2, now),
                new CompanyProfile("fleetos.example", "FleetOS", "Fleet tracking and last-mile logistics", "logistics",
                        List.of("logistics"), "https://fleetos.example/jobs", null, true, 5, now)
        ).forEach(mongo::save);

        var all = search.search(null, null, false, 0, 10);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.sectors()).containsEntry("fintech", 2L).containsEntry("logistics", 1L);

        assertThat(search.search(null, "fintech", true, 0, 10).hits())
                .extracting(CompanySearch.Hit::site).containsExactly("acme.example");

        assertThat(search.search("fleet tracking", null, false, 0, 10).hits().getFirst().site())
                .isEqualTo("fleetos.example");

        assertThat(search.search("ledgerly", null, false, 0, 10).hits().getFirst().site())
                .isEqualTo("ledgerly.example");   // name is boosted
    }
}
