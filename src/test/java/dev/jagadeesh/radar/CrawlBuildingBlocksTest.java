package dev.jagadeesh.radar;

import dev.jagadeesh.radar.crawl.HostRateLimiter;
import dev.jagadeesh.radar.crawl.RobotsRules;
import dev.jagadeesh.radar.crawl.UrlNormalizer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlBuildingBlocksTest {

    @Nested
    class Normalizer {

        @Test
        void collapsesEquivalentSpellings() {
            assertThat(UrlNormalizer.normalize("HTTPS://Acme.Example:443/About/?utm_source=x&b=2&a=1#team"))
                    .contains("https://acme.example/About?a=1&b=2");
            assertThat(UrlNormalizer.normalize("http://acme.example:80")).contains("http://acme.example/");
            assertThat(UrlNormalizer.normalize("https://acme.example/a/../b")).contains("https://acme.example/b");
            assertThat(UrlNormalizer.normalize("https://acme.example:8443/x")).contains("https://acme.example:8443/x");
        }

        @Test
        void rejectsWhatIsNotAWebPage() {
            assertThat(UrlNormalizer.normalize("mailto:hr@acme.example")).isEmpty();
            assertThat(UrlNormalizer.normalize("javascript:void(0)")).isEmpty();
            assertThat(UrlNormalizer.normalize("https://acme.example/logo.PNG")).isEmpty();
            assertThat(UrlNormalizer.normalize("not a url")).isEmpty();
        }

        @Test
        void wwwIsTheSameSite() {
            assertThat(UrlNormalizer.siteKey("WWW.acme.example")).isEqualTo("acme.example");
        }
    }

    @Nested
    class Robots {

        static final String TXT = """
                # comment
                User-agent: *
                Disallow: /private
                Disallow: /*.json$
                Allow: /private/press
                Crawl-delay: 2.5

                User-agent: RadarBot
                User-agent: OtherBot
                Disallow: /search
                """;

        @Test
        void usesTheMostSpecificGroup() {
            RobotsRules ours = RobotsRules.parse(TXT, "RadarBot/1.0 (+https://x)");
            assertThat(ours.isAllowed("/search?q=a")).isFalse();
            assertThat(ours.isAllowed("/private")).isTrue();      // our group doesn't block it
            assertThat(ours.crawlDelay()).isEmpty();
        }

        @Test
        void wildcardGroupWithLongestMatchAndPatterns() {
            RobotsRules any = RobotsRules.parse(TXT, "SomeBot");
            assertThat(any.isAllowed("/private/keys")).isFalse();
            assertThat(any.isAllowed("/private/press/2026")).isTrue();   // longer Allow wins
            assertThat(any.isAllowed("/api/data.json")).isFalse();
            assertThat(any.isAllowed("/api/data.json?v=2")).isTrue();    // $ anchors the end
            assertThat(any.crawlDelay()).contains(Duration.ofMillis(2500));
        }

        @Test
        void emptyDisallowAllowsEverything() {
            assertThat(RobotsRules.parse("User-agent: *\nDisallow:", "x").isAllowed("/anything")).isTrue();
        }
    }

    @Nested
    class RateLimiter {

        @Test
        void spacesRequestsToTheSameHost() throws Exception {
            AtomicLong now = new AtomicLong(0);
            List<Long> sleeps = new ArrayList<>();
            HostRateLimiter limiter = new HostRateLimiter(now::get, sleeps::add);
            Duration second = Duration.ofSeconds(1);

            limiter.acquire("acme.example", second);       // free slot now
            limiter.acquire("www.acme.example", second);   // same site → waits 1 s
            limiter.acquire("acme.example", second);       // waits 2 s
            limiter.acquire("other.example", second);      // different host → no wait

            assertThat(sleeps).containsExactly(1_000_000_000L, 2_000_000_000L);
        }

        @Test
        void idleHostDoesNotBuildUpCredit() throws Exception {
            AtomicLong now = new AtomicLong(0);
            List<Long> sleeps = new ArrayList<>();
            HostRateLimiter limiter = new HostRateLimiter(now::get, sleeps::add);

            limiter.acquire("acme.example", Duration.ofSeconds(1));
            now.set(10_000_000_000L);                      // 10 s later
            limiter.acquire("acme.example", Duration.ofSeconds(1));
            limiter.acquire("acme.example", Duration.ofSeconds(1));

            assertThat(sleeps).containsExactly(1_000_000_000L);
        }
    }
}
