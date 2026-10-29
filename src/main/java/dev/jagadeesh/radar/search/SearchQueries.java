package dev.jagadeesh.radar.search;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public final class SearchQueries {

    private SearchQueries() {
    }

    /**
     * MongoDB filter for a search: {@code $text} over the weighted text index (name x3, tags x2, description) when
     * there is a query, plus exact matches on sector and hiring. The filters use their own indexes and don't change
     * the text score.
     */
    public static Bson build(String text, String sector, boolean hiringOnly) {
        List<Bson> clauses = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            clauses.add(Filters.text(text));
        }
        if (sector != null && !sector.isBlank()) {
            clauses.add(Filters.eq("sector", sector));
        }
        if (hiringOnly) {
            clauses.add(Filters.eq("hiring", true));
        }
        return clauses.isEmpty() ? new Document() : Filters.and(clauses);
    }
}
