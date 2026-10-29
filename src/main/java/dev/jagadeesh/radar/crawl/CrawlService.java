package dev.jagadeesh.radar.crawl;

import dev.jagadeesh.radar.profile.CompanyProfile;
import dev.jagadeesh.radar.profile.CompanyStore;
import dev.jagadeesh.radar.profile.ProfileExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** Runs crawl jobs on virtual threads: crawl, extract a profile, save it to MongoDB (where the text index makes it searchable). */
@Service
public class CrawlService {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    public record Job(String id, String seedUrl, Status status, Integer pages, Integer blockedByRobots,
                      Integer duplicateBodies, Integer errors, CompanyProfile profile, String error,
                      Instant startedAt, Instant finishedAt) {

        Job with(Status s) {
            return new Job(id, seedUrl, s, pages, blockedByRobots, duplicateBodies, errors, profile, error, startedAt, finishedAt);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final Crawler crawler;
    private final ProfileExtractor extractor;
    private final CompanyStore store;
    private final ExecutorService executor;
    private final Clock clock;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public CrawlService(Crawler crawler, ProfileExtractor extractor, CompanyStore store,
                        ExecutorService crawlExecutor, Clock clock) {
        this.crawler = crawler;
        this.extractor = extractor;
        this.store = store;
        this.executor = crawlExecutor;
        this.clock = clock;
    }

    public Job submit(String seedUrl, Crawler.Options options) {
        UrlNormalizer.normalize(seedUrl)
                .orElseThrow(() -> new IllegalArgumentException("Not a crawlable http(s) URL: " + seedUrl));
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, seedUrl, Status.QUEUED, null, null, null, null, null, null, clock.instant(), null);
        jobs.put(id, job);
        executor.submit(() -> run(job, options));
        return job;
    }

    public Optional<Job> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void run(Job job, Crawler.Options options) {
        jobs.put(job.id(), job.with(Status.RUNNING));
        try {
            Crawler.Result result = crawler.crawl(job.seedUrl(), options);
            Instant now = clock.instant();
            CompanyProfile profile = extractor.extract(result.site(), result.pages(), now);
            store.save(profile, result.pages(), now);
            jobs.put(job.id(), new Job(job.id(), job.seedUrl(), Status.DONE, result.pages().size(),
                    result.blockedByRobots(), result.duplicateBodies(), result.errors(), profile, null,
                    job.startedAt(), now));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, "Interrupted");
        } catch (Exception e) {
            log.warn("Crawl {} failed", job.seedUrl(), e);
            fail(job, e.getMessage());
        }
    }

    private void fail(Job job, String error) {
        jobs.put(job.id(), new Job(job.id(), job.seedUrl(), Status.FAILED, null, null, null, null, null, error,
                job.startedAt(), clock.instant()));
    }
}
