package dev.jagadeesh.radar.search;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import dev.jagadeesh.radar.profile.CompanyProfile;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CompanySearch {

    public record Hit(String site, String name, String sector, List<String> tags, boolean hiring, String careersUrl,
                      String snippet, float score) {
    }

    public record Result(long total, List<Hit> hits, Map<String, Long> sectors) {
    }

    private final ElasticsearchOperations es;

    public CompanySearch(ElasticsearchOperations es) {
        this.es = es;
    }

    /** Creates the index with the explicit mapping above, instead of letting dynamic mapping guess types. */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfMissing() {
        IndexOperations index = es.indexOps(CompanyDocument.class);
        if (!index.exists()) {
            index.createWithMapping();
        }
    }

    public void index(CompanyProfile profile) {
        es.save(CompanyDocument.from(profile));
    }

    public void refresh() {
        es.indexOps(CompanyDocument.class).refresh();
    }

    public Result search(String text, String sector, boolean hiringOnly, int page, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(SearchQueries.build(text, sector, hiringOnly))
                .withAggregation("sectors", Aggregation.of(a -> a.terms(t -> t.field("sector").size(20))))
                .withHighlightQuery(new HighlightQuery(new Highlight(
                        HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build(),
                        List.of(new HighlightField("description"))), CompanyDocument.class))
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<CompanyDocument> hits = es.search(query, CompanyDocument.class);
        List<Hit> items = hits.getSearchHits().stream().map(CompanySearch::toHit).toList();
        return new Result(hits.getTotalHits(), items, sectorFacets(hits));
    }

    private static Hit toHit(SearchHit<CompanyDocument> h) {
        CompanyDocument d = h.getContent();
        List<String> highlights = h.getHighlightField("description");
        String snippet = highlights.isEmpty() ? d.description() : String.join(" … ", highlights);
        return new Hit(d.site(), d.name(), d.sector(), d.tags(), d.hiring(), d.careersUrl(), snippet, h.getScore());
    }

    private static Map<String, Long> sectorFacets(SearchHits<CompanyDocument> hits) {
        Map<String, Long> facets = new LinkedHashMap<>();
        if (hits.getAggregations() instanceof ElasticsearchAggregations aggs && aggs.get("sectors") != null) {
            for (StringTermsBucket b : aggs.get("sectors").aggregation().getAggregate().sterms().buckets().array()) {
                facets.put(b.key().stringValue(), b.docCount());
            }
        }
        return facets;
    }
}
