package dev.jagadeesh.radar.crawl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Breadth-first crawl of one site: same site only, robots.txt respected, rate limited per host,
 * duplicate URLs and duplicate page bodies skipped, bounded by page count and link depth.
 */
public class Crawler {

    private static final Logger log = LoggerFactory.getLogger(Crawler.class);
    private static final RobotsRules DISALLOW_ALL = RobotsRules.parse("User-agent: *\nDisallow: /", "*");

    public record Options(int maxPages, int maxDepth, Duration minDelay) {
    }

    public record CrawledPage(String url, int depth, String title, Document document, String contentHash) {
    }

    public record Result(String site, List<CrawledPage> pages, int blockedByRobots, int duplicateBodies, int errors) {
    }

    private record Item(String url, int depth) {
    }

    private final PageFetcher fetcher;
    private final HostRateLimiter limiter;
    private final String userAgent;

    public Crawler(PageFetcher fetcher, HostRateLimiter limiter, String userAgent) {
        this.fetcher = fetcher;
        this.limiter = limiter;
        this.userAgent = userAgent;
    }

    public Result crawl(String seedUrl, Options options) throws InterruptedException {
        String seed = UrlNormalizer.normalize(seedUrl)
                .orElseThrow(() -> new IllegalArgumentException("Not a crawlable http(s) URL: " + seedUrl));
        URI seedUri = URI.create(seed);
        String site = UrlNormalizer.siteKey(seedUri.getHost());
        RobotsRules robots = loadRobots(seedUri);
        Duration delay = robots.crawlDelay().filter(d -> d.compareTo(options.minDelay()) > 0).orElse(options.minDelay());

        Deque<Item> frontier = new ArrayDeque<>();
        Set<String> seenUrls = new HashSet<>();
        Set<String> seenBodies = new HashSet<>();
        List<CrawledPage> pages = new ArrayList<>();
        int blocked = 0;
        int duplicates = 0;
        int errors = 0;

        frontier.add(new Item(seed, 0));
        seenUrls.add(seed);
        while (!frontier.isEmpty() && pages.size() < options.maxPages()) {
            Item item = frontier.poll();
            URI uri = URI.create(item.url());
            if (!robots.isAllowed(pathAndQuery(uri))) {
                blocked++;
                continue;
            }

            limiter.acquire(uri.getHost(), delay);
            PageFetcher.Response response;
            try {
                response = fetcher.fetch(item.url());
            } catch (IOException e) {
                log.debug("Fetch failed for {}: {}", item.url(), e.getMessage());
                errors++;
                continue;
            }
            if (!response.isHtml()) {
                continue;
            }
            String finalUrl = UrlNormalizer.normalize(response.finalUrl()).orElse(item.url());
            if (!sameSite(finalUrl, site)) {
                continue;  // redirected off-site
            }
            String hash = sha256(response.body());
            if (!seenBodies.add(hash)) {
                duplicates++;  // same content under another URL (session ids, mirrors)
                continue;
            }

            Document doc = Jsoup.parse(response.body(), finalUrl);
            pages.add(new CrawledPage(finalUrl, item.depth(), doc.title(), doc, hash));

            if (item.depth() < options.maxDepth()) {
                for (Element link : doc.select("a[href]")) {
                    Optional<String> next = UrlNormalizer.normalize(link.absUrl("href"));
                    if (next.isPresent() && sameSite(next.get(), site) && seenUrls.add(next.get())) {
                        frontier.add(new Item(next.get(), item.depth() + 1));
                    }
                }
            }
        }
        return new Result(site, pages, blocked, duplicates, errors);
    }

    private RobotsRules loadRobots(URI seed) {
        String robotsUrl = seed.getScheme() + "://" + seed.getAuthority() + "/robots.txt";
        try {
            PageFetcher.Response r = fetcher.fetch(robotsUrl);
            if (r.status() == 200) {
                return RobotsRules.parse(r.body(), userAgent);
            }
            // 4xx: no robots.txt, everything allowed. 5xx: the site is struggling, stay away.
            return r.status() >= 500 ? DISALLOW_ALL : RobotsRules.allowAll();
        } catch (IOException e) {
            return DISALLOW_ALL;
        }
    }

    private static boolean sameSite(String url, String site) {
        return UrlNormalizer.siteKey(URI.create(url).getHost()).equals(site);
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
