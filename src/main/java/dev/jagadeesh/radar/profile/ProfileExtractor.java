package dev.jagadeesh.radar.profile;

import dev.jagadeesh.radar.crawl.Crawler;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds a company profile from crawled pages: name, description, sector tags, careers and LinkedIn links. */
@Component
public class ProfileExtractor {

    private static final Pattern CAREERS = Pattern.compile(
            "\\b(careers?|jobs|join[ -]us|we'?re hiring|open positions)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+[|\\-–—:]\\s+");

    /** Keywords per sector; a sector is tagged when its keywords appear at least twice across the site. */
    static final Map<String, List<String>> SECTOR_KEYWORDS = new LinkedHashMap<>();

    static {
        SECTOR_KEYWORDS.put("fintech", List.of("payments", "payment", "lending", "banking", "upi", "credit", "wallet", "insurance"));
        SECTOR_KEYWORDS.put("ai", List.of("artificial intelligence", "machine learning", "llm", "generative ai", "ai-powered"));
        SECTOR_KEYWORDS.put("logistics", List.of("logistics", "fleet", "shipping", "supply chain", "last-mile"));
        SECTOR_KEYWORDS.put("healthtech", List.of("healthcare", "patients", "clinic", "hospital", "medical"));
        SECTOR_KEYWORDS.put("edtech", List.of("students", "courses", "education", "learners"));
        SECTOR_KEYWORDS.put("ecommerce", List.of("e-commerce", "ecommerce", "checkout", "retail", "d2c", "storefront"));
        SECTOR_KEYWORDS.put("cybersecurity", List.of("cybersecurity", "threat", "ransomware", "zero trust", "soc"));
        SECTOR_KEYWORDS.put("saas", List.of("saas", "subscription", "dashboard", "workflow", "api"));
    }

    public CompanyProfile extract(String site, List<Crawler.CrawledPage> pages, Instant crawledAt) {
        if (pages.isEmpty()) {
            return new CompanyProfile(site, site, null, "other", List.of(), null, null, false, 0, crawledAt);
        }
        Document home = pages.getFirst().document();
        String careers = null;
        String linkedin = null;
        StringBuilder text = new StringBuilder();
        for (Crawler.CrawledPage page : pages) {
            Document doc = page.document();
            text.append(' ').append(doc.body() == null ? "" : doc.body().text());
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (careers == null && (CAREERS.matcher(a.text()).find() || CAREERS.matcher(href).find())) {
                    careers = href;
                }
                if (linkedin == null && href.contains("linkedin.com/company/")) {
                    linkedin = href;
                }
            }
        }
        List<String> tags = tags(text.toString().toLowerCase(Locale.ROOT));
        String sector = tags.isEmpty() ? "other" : tags.getFirst();
        return new CompanyProfile(site, name(home, site), description(home), sector, tags, careers, linkedin,
                careers != null, pages.size(), crawledAt);
    }

    static String name(Document home, String site) {
        String ogName = home.select("meta[property=og:site_name]").attr("content").trim();
        if (!ogName.isEmpty()) {
            return ogName;
        }
        String title = home.title().trim();
        if (!title.isEmpty()) {
            return TITLE_SEPARATOR.split(title)[0].trim();
        }
        return site;
    }

    static String description(Document home) {
        for (String selector : List.of("meta[name=description]", "meta[property=og:description]")) {
            String content = home.select(selector).attr("content").trim();
            if (!content.isEmpty()) {
                return content;
            }
        }
        Element p = home.selectFirst("p");
        if (p == null) {
            return null;
        }
        String t = p.text();
        return t.length() <= 300 ? t : t.substring(0, 297) + "...";
    }

    static List<String> tags(String lowerText) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        SECTOR_KEYWORDS.forEach((sector, words) -> {
            int count = 0;
            for (String w : words) {
                Matcher m = Pattern.compile("\\b" + Pattern.quote(w) + "\\b").matcher(lowerText);
                while (m.find()) {
                    count++;
                }
            }
            if (count >= 2) {
                hits.put(sector, count);
            }
        });
        List<String> sorted = new ArrayList<>(hits.keySet());
        sorted.sort(Comparator.comparing((String s) -> -hits.get(s)));
        return sorted;
    }
}
