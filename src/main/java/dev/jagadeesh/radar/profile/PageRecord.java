package dev.jagadeesh.radar.profile;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Crawl log: one row per fetched URL, with a content hash to detect changes on the next crawl. */
@Document("pages")
public record PageRecord(@Id String url, String site, String title, int depth, String contentHash, Instant fetchedAt) {
}
