package dev.jagadeesh.radar.profile;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Source of truth in MongoDB; a copy is indexed into Elasticsearch for search. */
@Document("companies")
public record CompanyProfile(@Id String site, String name, String description, String sector, List<String> tags,
                             String careersUrl, String linkedinUrl, boolean hiring, int pagesCrawled,
                             Instant crawledAt) {
}
