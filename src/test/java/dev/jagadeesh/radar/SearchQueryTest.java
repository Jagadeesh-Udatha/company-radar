package dev.jagadeesh.radar;

import dev.jagadeesh.radar.search.SearchQueries;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the MongoDB filters we generate, without a database. */
class SearchQueryTest {

    @Test
    void textSearchUsesTheTextIndexAndNoFilters() {
        String json = json(SearchQueries.build("payments", null, false));

        assertThat(json).contains("\"$text\"").contains("\"$search\": \"payments\"")
                .doesNotContain("sector").doesNotContain("hiring");
    }

    @Test
    void sectorAndHiringAreExactFilters() {
        String json = json(SearchQueries.build(null, "fintech", true));

        assertThat(json).doesNotContain("$text")
                .contains("\"sector\": \"fintech\"").contains("\"hiring\": true");
    }

    @Test
    void noCriteriaMatchesEverything() {
        assertThat(json(SearchQueries.build(" ", null, false))).isEqualTo("{}");
    }

    private static String json(Bson filter) {
        return filter.toBsonDocument(BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }
}
