package dev.jagadeesh.radar.api;

import dev.jagadeesh.radar.crawl.CrawlConfig;
import dev.jagadeesh.radar.crawl.CrawlService;
import dev.jagadeesh.radar.crawl.Crawler;
import dev.jagadeesh.radar.profile.CompanyProfile;
import dev.jagadeesh.radar.profile.CompanyStore;
import dev.jagadeesh.radar.search.CompanySearch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
public class RadarController {

    public record CrawlRequest(@NotBlank String url,
                               @Min(1) @Max(200) Integer maxPages,
                               @Min(0) @Max(5) Integer maxDepth) {
    }

    private final CrawlService crawls;
    private final CompanySearch search;
    private final CompanyStore store;
    private final CrawlConfig.CrawlProperties props;

    public RadarController(CrawlService crawls, CompanySearch search, CompanyStore store,
                           CrawlConfig.CrawlProperties props) {
        this.crawls = crawls;
        this.search = search;
        this.store = store;
        this.props = props;
    }

    @PostMapping("/crawls")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlService.Job crawl(@Valid @RequestBody CrawlRequest request) {
        var options = new Crawler.Options(request.maxPages() == null ? 30 : request.maxPages(),
                request.maxDepth() == null ? 2 : request.maxDepth(), props.minDelay());
        return crawls.submit(request.url(), options);
    }

    @GetMapping("/crawls/{id}")
    public CrawlService.Job job(@PathVariable String id) {
        return crawls.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No crawl " + id));
    }

    @GetMapping("/companies/search")
    public CompanySearch.Result search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String sector,
                                       @RequestParam(defaultValue = "false") boolean hiring,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search.search(q, sector, hiring, page, size);
    }

    @GetMapping("/companies/{site}")
    public CompanyProfile company(@PathVariable String site) {
        return store.find(site).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown " + site));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badUrl(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
