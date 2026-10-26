package dev.jagadeesh.radar.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

public final class SearchQueries {

    private SearchQueries() {
    }

    /**
     * Full text on name (boosted x3), tags (x2) and description, typo tolerant (fuzziness AUTO). Sector and hiring are
     * filters: exact, cached by Elasticsearch, and they don't affect the relevance score.
     */
    public static Query build(String text, String sector, boolean hiringOnly) {
        return Query.of(q -> q.bool(b -> {
            if (text == null || text.isBlank()) {
                b.must(m -> m.matchAll(all -> all));
            } else {
                b.must(m -> m.multiMatch(mm -> mm.query(text).fields("name^3", "tags^2", "description").fuzziness("AUTO")));
            }
            if (sector != null && !sector.isBlank()) {
                b.filter(f -> f.term(t -> t.field("sector").value(sector)));
            }
            if (hiringOnly) {
                b.filter(f -> f.term(t -> t.field("hiring").value(true)));
            }
            return b;
        }));
    }
}
