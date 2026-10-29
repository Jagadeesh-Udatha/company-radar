package dev.jagadeesh.radar.profile;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** One company profile in MongoDB; also searched through the collection's text index (see CompanySearch). */
@Document("companies")
public record CompanyProfile(@Id String site, String name, String description, String sector, List<String> tags,
                             String careersUrl, String linkedinUrl, boolean hiring, int pagesCrawled,
                             Instant crawledAt) {
}
