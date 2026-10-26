package dev.jagadeesh.radar;

import dev.jagadeesh.radar.crawl.Crawler;
import dev.jagadeesh.radar.crawl.HostRateLimiter;
import dev.jagadeesh.radar.profile.CompanyProfile;
import dev.jagadeesh.radar.profile.ProfileExtractor;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileExtractorTest {

    final ProfileExtractor extractor = new ProfileExtractor();
    final Instant now = Instant.parse("2026-10-26T04:00:00Z");

    @Test
    void buildsProfileFromCrawledSite() throws Exception {
        Crawler.Result crawl = new Crawler(FakeSite.acme(), new HostRateLimiter(() -> 0L, n -> { }), "RadarBot")
                .crawl("https://acme.example/", new Crawler.Options(20, 2, Duration.ZERO));

        CompanyProfile p = extractor.extract(crawl.site(), crawl.pages(), now);

        assertThat(p.site()).isEqualTo("acme.example");
        assertThat(p.name()).isEqualTo("Acme Pay");
        assertThat(p.description()).isEqualTo("Acme Pay helps small businesses accept UPI and card payments.");
        assertThat(p.sector()).isEqualTo("fintech");
        assertThat(p.careersUrl()).isEqualTo("https://acme.example/careers");
        assertThat(p.linkedinUrl()).isEqualTo("https://www.linkedin.com/company/acme-pay");
        assertThat(p.hiring()).isTrue();
        assertThat(p.pagesCrawled()).isEqualTo(4);
    }

    @Test
    void prefersOgSiteNameAndFallsBackToFirstParagraph() {
        var doc = Jsoup.parse("""
                <html><head><title>Home - Whatever</title><meta property="og:site_name" content="FleetOS"></head>
                <body><p>Fleet and logistics software. Shipping, fleet tracking and last-mile delivery.</p></body></html>""",
                "https://fleetos.example/");
        var page = new Crawler.CrawledPage("https://fleetos.example/", 0, doc.title(), doc, "h");

        CompanyProfile p = extractor.extract("fleetos.example", List.of(page), now);

        assertThat(p.name()).isEqualTo("FleetOS");
        assertThat(p.description()).startsWith("Fleet and logistics software");
        assertThat(p.sector()).isEqualTo("logistics");
        assertThat(p.hiring()).isFalse();
    }

    @Test
    void siteWithoutSignalsIsOther() {
        var doc = Jsoup.parse("<html><body>Hello</body></html>", "https://x.example/");
        CompanyProfile p = extractor.extract("x.example",
                List.of(new Crawler.CrawledPage("https://x.example/", 0, "", doc, "h")), now);

        assertThat(p.sector()).isEqualTo("other");
        assertThat(p.tags()).isEmpty();
        assertThat(p.name()).isEqualTo("x.example");
    }
}
