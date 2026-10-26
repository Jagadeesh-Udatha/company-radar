package dev.jagadeesh.radar;

import dev.jagadeesh.radar.crawl.Crawler;
import dev.jagadeesh.radar.crawl.HostRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerTest {

    static final HostRateLimiter NO_WAIT = new HostRateLimiter(() -> 0L, nanos -> { });

    @Test
    void crawlsSameSiteBreadthFirstAndSkipsEverythingItShould() throws Exception {
        FakeSite site = FakeSite.acme();

        Crawler.Result result = new Crawler(site, NO_WAIT, "RadarBot").crawl("https://ACME.example/",
                new Crawler.Options(50, 2, Duration.ZERO));

        assertThat(result.site()).isEqualTo("acme.example");
        assertThat(result.pages()).extracting(Crawler.CrawledPage::url).containsExactly(
                "https://acme.example/",
                "https://acme.example/about",
                "https://acme.example/careers",
                "https://acme.example/team");
        assertThat(result.blockedByRobots()).isEqualTo(1);        // /private/admin
        assertThat(result.duplicateBodies()).isEqualTo(1);        // /mirror has the same body as /about
        assertThat(site.fetched).noneMatch(u -> u.contains("other.example"))
                .noneMatch(u -> u.endsWith(".pdf"))
                .noneMatch(u -> u.contains("/private"))
                .noneMatch(u -> u.endsWith("/deep"));             // depth 3 > maxDepth 2
        assertThat(site.fetched).filteredOn(u -> u.equals("https://acme.example/")).hasSize(1);
    }

    @Test
    void stopsAtMaxPages() throws Exception {
        Crawler.Result result = new Crawler(FakeSite.acme(), NO_WAIT, "RadarBot").crawl("https://acme.example/",
                new Crawler.Options(2, 5, Duration.ZERO));

        assertThat(result.pages()).hasSize(2);
    }

    @Test
    void serverErrorOnRobotsTxtMeansDoNotCrawl() throws Exception {
        FakeSite site = FakeSite.acme().text("https://acme.example/robots.txt", 503, "busy");

        Crawler.Result result = new Crawler(site, NO_WAIT, "RadarBot").crawl("https://acme.example/",
                new Crawler.Options(10, 2, Duration.ZERO));

        assertThat(result.pages()).isEmpty();
        assertThat(result.blockedByRobots()).isEqualTo(1);
    }

    @Test
    void missingRobotsTxtAllowsEverything() throws Exception {
        FakeSite site = FakeSite.acme().text("https://acme.example/robots.txt", 404, "");

        Crawler.Result result = new Crawler(site, NO_WAIT, "RadarBot").crawl("https://acme.example/",
                new Crawler.Options(10, 1, Duration.ZERO));

        assertThat(site.fetched).contains("https://acme.example/private/admin");
        assertThat(result.blockedByRobots()).isZero();
    }

    @Test
    void rejectsNonHttpSeeds() {
        assertThatThrownBy(() -> new Crawler(new FakeSite(), NO_WAIT, "RadarBot")
                .crawl("ftp://acme.example", new Crawler.Options(1, 1, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
