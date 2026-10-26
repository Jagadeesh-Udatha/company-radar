package dev.jagadeesh.radar.crawl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(CrawlConfig.CrawlProperties.class)
public class CrawlConfig {

    @ConfigurationProperties("radar.crawl")
    public record CrawlProperties(String userAgent, Duration minDelay, Duration timeout, int maxBodyBytes) {
    }

    @Bean
    PageFetcher pageFetcher(CrawlProperties props) {
        return new JsoupPageFetcher(props.userAgent(), (int) props.timeout().toMillis(), props.maxBodyBytes());
    }

    @Bean
    HostRateLimiter hostRateLimiter() {
        return new HostRateLimiter();
    }

    @Bean
    Crawler crawler(PageFetcher fetcher, HostRateLimiter limiter, CrawlProperties props) {
        return new Crawler(fetcher, limiter, props.userAgent());
    }

    /** Crawling is blocking network I/O: one cheap virtual thread per job instead of a sized platform pool. */
    @Bean(destroyMethod = "close")
    ExecutorService crawlExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
