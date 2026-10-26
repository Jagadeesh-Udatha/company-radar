package dev.jagadeesh.radar;

import dev.jagadeesh.radar.search.SearchQueries;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the Elasticsearch query DSL we generate, without a cluster. */
class SearchQueryTest {

    @Test
    void textSearchIsFuzzyAndBoostsNameAndTags() {
        String json = SearchQueries.build("paymnts", null, false).toString();

        assertThat(json).contains("\"multi_match\"")
                .contains("\"name^3\"").contains("\"tags^2\"").contains("\"description\"")
                .contains("\"fuzziness\":\"AUTO\"")
                .doesNotContain("\"filter\"");
    }

    @Test
    void sectorAndHiringAreFiltersNotScoringClauses() {
        String json = SearchQueries.build(null, "fintech", true).toString();

        assertThat(json).contains("\"match_all\"")
                .contains("\"filter\":[{\"term\":{\"sector\":{\"value\":\"fintech\"}}},{\"term\":{\"hiring\":{\"value\":true}}}]");
    }
}
