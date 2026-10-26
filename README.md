# Company Radar

A polite **web crawler** that reads a company's website, builds a **company profile** (name, description, sector, careers page, LinkedIn), stores it in **MongoDB**, and serves **fuzzy, faceted search** from **Elasticsearch**.

Java 21 (virtual threads) · Spring Boot 3.4 · Jsoup · Spring Data MongoDB · Spring Data Elasticsearch · JUnit 5 · Testcontainers · Docker · GitHub Actions

## Flow

```
POST /api/crawls {url}
        │  job runs on a virtual thread
        ▼
robots.txt ─▶ BFS crawl (same site, depth/page limits, per-host rate limit, URL + body dedupe)
        │
        ▼
ProfileExtractor ─▶ name · description · sector tags · careers URL · LinkedIn · hiring?
        │
        ├──▶ MongoDB   companies (source of truth) + pages (crawl log with content hashes)
        └──▶ Elasticsearch index "companies" (explicit mapping)
                     ▲
GET /api/companies/search?q=paymnts&sector=fintech&hiring=true
     multi_match name^3 / tags^2 / description, fuzziness AUTO · term filters · sector facets · highlights
```

## Crawler rules

| Concern | Implementation |
|---|---|
| Don't hammer sites | `HostRateLimiter`: one request per host per interval, shared by all jobs; honours `Crawl-delay` |
| Respect site owners | `RobotsRules`: our UA group or `*`, longest-match Allow/Disallow, `*` and `$` patterns. robots.txt 5xx → don't crawl |
| Don't crawl the same page twice | `UrlNormalizer`: lowercase host, default ports, fragments, `utm_*`/click ids, sorted query, trailing slash |
| Mirrors and session-id URLs | SHA-256 of the body; a repeated body is skipped |
| Stay on target | Same site only (`www.` ignored), off-site redirects dropped, binary file extensions skipped |
| Bounded work | `maxPages`, `maxDepth`, 2 MB body cap, 10 s timeout |

## Run it

```bash
docker compose up --build
curl -X POST localhost:8080/api/crawls -H 'Content-Type: application/json' -d '{"url":"https://example.com","maxPages":20}'
curl localhost:8080/api/crawls/<id>
curl "localhost:8080/api/companies/search?q=payments&hiring=true"
```

## API

| Method | Path | |
|---|---|---|
| POST | `/api/crawls` | Start a crawl `{url, maxPages≤200, maxDepth≤5}` → 202 with a job id |
| GET | `/api/crawls/{id}` | Job status, counts (pages, robots-blocked, duplicate bodies, errors) and the profile |
| GET | `/api/companies/search` | `q`, `sector`, `hiring`, `page`, `size` → hits with highlights + sector facets |
| GET | `/api/companies/{site}` | Stored profile |

## Tests

```bash
mvn verify
```

- `CrawlBuildingBlocksTest`: URL normalisation, robots.txt groups/patterns/crawl-delay, rate limiter with a fake clock.
- `CrawlerTest`: an in-memory site with traps (robots-blocked path, mirror page, tracking params, fragments, off-site links, PDFs, depth limit, robots.txt 5xx/404).
- `ProfileExtractorTest`: name/description fallbacks, sector tagging, careers + LinkedIn detection.
- `SearchQueryTest`: the generated Elasticsearch DSL (boosts, fuzziness, filters vs scoring).
- `RadarFlowIntegrationTest`: real MongoDB + Elasticsearch (Testcontainers, or `MONGO_TEST_URI` + `ES_TEST_URI`): crawl job → profile → indexed → fuzzy search with highlight and facets; filters and boosting.

See [INTERVIEW_NOTES.md](INTERVIEW_NOTES.md).
