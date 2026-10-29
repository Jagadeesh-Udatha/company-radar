package dev.jagadeesh.radar.search;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Full-text search over the company profiles in MongoDB (text index), with sector facets and highlighted snippets. */
@Component
public class CompanySearch {

    static final String COLLECTION = "companies";

    public record Hit(String site, String name, String sector, List<String> tags, boolean hiring, String careersUrl,
                      String snippet, double score) {
    }

    public record Result(long total, List<Hit> hits, Map<String, Long> sectors) {
    }

    private final MongoTemplate mongo;

    public CompanySearch(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** One weighted text index (a collection can have only one) plus indexes for the two filters. */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        IndexOperations ops = mongo.indexOps(COLLECTION);
        ops.ensureIndex(TextIndexDefinition.builder()
                .named("company_text")
                .onField("name", 3F)
                .onField("tags", 2F)
                .onField("description")
                .withDefaultLanguage("english")
                .build());
        ops.ensureIndex(new Index().on("sector", Sort.Direction.ASC).on("hiring", Sort.Direction.ASC));
    }

    public Result search(String text, String sector, boolean hiringOnly, int page, int size) {
        boolean hasText = text != null && !text.isBlank();
        Bson filter = SearchQueries.build(text, sector, hiringOnly);
        MongoCollection<Document> companies = mongo.getCollection(COLLECTION);

        FindIterable<Document> found = companies.find(filter).skip(page * size).limit(size);
        found = hasText
                ? found.projection(Projections.metaTextScore("score")).sort(Sorts.metaTextScore("score"))
                : found.sort(Sorts.ascending("name"));

        List<Hit> hits = found.map(d -> toHit(d, text)).into(new java.util.ArrayList<>());
        return new Result(companies.countDocuments(filter), hits, sectorFacets(companies, filter));
    }

    private static Hit toHit(Document d, String text) {
        Number score = d.get("score", Number.class);
        return new Hit(d.getString("_id"), d.getString("name"), d.getString("sector"), d.getList("tags", String.class),
                Boolean.TRUE.equals(d.getBoolean("hiring")), d.getString("careersUrl"),
                highlight(d.getString("description"), text), score == null ? 0 : score.doubleValue());
    }

    /** Wraps words that start with a query term in {@code <em>}; "payments" also marks "payment" (crude stemming). */
    static String highlight(String description, String text) {
        if (description == null || text == null || text.isBlank()) {
            return description;
        }
        String[] stems = Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(w -> w.length() >= 3)
                .map(w -> w.endsWith("s") ? w.substring(0, w.length() - 1) : w)
                .map(Pattern::quote)
                .toArray(String[]::new);
        if (stems.length == 0) {
            return description;
        }
        return description.replaceAll("(?i)\\b((?:" + String.join("|", stems) + ")\\w*)", "<em>$1</em>");
    }

    private static Map<String, Long> sectorFacets(MongoCollection<Document> companies, Bson filter) {
        Map<String, Long> facets = new LinkedHashMap<>();
        companies.aggregate(List.of(
                Aggregates.match(filter),
                Aggregates.group("$sector", Accumulators.sum("count", 1L)),
                Aggregates.sort(Sorts.descending("count")),
                Aggregates.limit(20)
        )).forEach(d -> {
            if (d.get("_id") != null) {
                facets.put(d.getString("_id"), d.get("count", Number.class).longValue());
            }
        });
        return facets;
    }
}
