package dev.jagadeesh.radar.profile;

import dev.jagadeesh.radar.crawl.Crawler;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Component
public class CompanyStore {

    private final MongoTemplate mongo;

    public CompanyStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Replaces the profile and upserts the crawl log in one bulk call. Re-crawls overwrite, never duplicate. */
    public void save(CompanyProfile profile, List<Crawler.CrawledPage> pages, Instant fetchedAt) {
        mongo.save(profile);
        if (pages.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, PageRecord.class);
        for (Crawler.CrawledPage p : pages) {
            bulk.upsert(query(where("_id").is(p.url())), new Update()
                    .set("site", profile.site())
                    .set("title", p.title())
                    .set("depth", p.depth())
                    .set("contentHash", p.contentHash())
                    .set("fetchedAt", fetchedAt));
        }
        bulk.execute();
    }

    public Optional<CompanyProfile> find(String site) {
        return Optional.ofNullable(mongo.findById(site, CompanyProfile.class));
    }
}
