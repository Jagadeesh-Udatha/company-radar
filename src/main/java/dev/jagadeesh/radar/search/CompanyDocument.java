package dev.jagadeesh.radar.search;

import dev.jagadeesh.radar.profile.CompanyProfile;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;

/**
 * Search-side copy of a profile. Text fields are analysed for full-text search; keyword fields are exact values
 * used for filters and facets.
 */
@Document(indexName = "companies")
@Setting(shards = 1, replicas = 0)
public record CompanyDocument(
        @Id String site,
        @MultiField(mainField = @Field(type = FieldType.Text),
                otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)) String name,
        @Field(type = FieldType.Text, analyzer = "english") String description,
        @Field(type = FieldType.Keyword) String sector,
        @Field(type = FieldType.Keyword) List<String> tags,
        @Field(type = FieldType.Keyword, index = false) String careersUrl,
        @Field(type = FieldType.Boolean) boolean hiring) {

    static CompanyDocument from(CompanyProfile p) {
        return new CompanyDocument(p.site(), p.name(), p.description(), p.sector(), p.tags(), p.careersUrl(), p.hiring());
    }
}
