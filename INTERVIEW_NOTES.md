# Interview notes: Company Radar

For data-platform roles (crawling, search, MongoDB), like Tracxn's backend role.

## Crawling

**"How do you design a crawler?"** Frontier (queue of URLs) → fetch → parse → extract links → normalise → dedupe → enqueue. Here: BFS so the home page and top-level pages come first (the most useful for a profile), bounded by `maxPages` and `maxDepth`.

**"How do you avoid crawling the same page twice?"** Two layers:
1. URL normalisation (`UrlNormalizer`): `HTTPS://Acme.com:443/About/?utm_source=x#team` and `https://acme.com/About` are the same key.
2. Content hash (SHA-256 of the body): catches the same page under different URLs (session ids, mirrors).
At web scale the `seen` set becomes a Bloom filter or a key-value store; content dedupe uses SimHash for near-duplicates.

**"How are you polite?"** `robots.txt` (`RobotsRules`), `Crawl-delay`, a per-host rate limiter shared by all jobs, an identifying User-Agent with a contact URL, timeouts and body caps. If robots.txt returns 5xx we don't crawl (same as Google's behaviour).

**"How does the rate limiter work across threads?"** `ConcurrentHashMap.compute` atomically reserves the host's next free slot and returns the start time; the caller sleeps until then. No global lock, and different hosts don't block each other. An idle host doesn't build up credit.

**"Why virtual threads?"** Crawling is blocking I/O. Virtual threads (Java 21) let each job (and each request, `spring.threads.virtual.enabled`) block cheaply without sizing a platform thread pool. Watch out for `synchronized` pinning on long I/O and for unbounded concurrency: politeness still comes from the rate limiter.

**"How would you scale it to millions of sites?"** Partition the frontier by host (Kafka topic keyed by host, so one consumer owns a host's politeness), store seen-URLs in Redis/RocksDB, schedule recrawls by change frequency (compare content hashes from the `pages` collection), and run fetchers as stateless workers.

## Search (MongoDB text index)

**"How does the search work?"** One text index on `companies` with weights `name: 3`, `tags: 2`, `description: 1` and the `english` language (lower-casing, stemming, stop words). `$text` finds matching documents and `{$meta: "textScore"}` ranks them, so a match in the name outranks one in the description. See `CompanySearch.createIndexes`.

**"Why not Elasticsearch?"** For one collection of company profiles, MongoDB's text index covers keyword search, weighting and filters with no second datastore to keep in sync. Elasticsearch would be the next step for typo tolerance (fuzzy matching), synonyms or very large indexes.

**"How are filters and facets done?"** Sector and hiring are plain equality clauses next to `$text` (with their own compound index); they narrow results without changing the score. Facets are an aggregation: `$match` (same filter) → `$group` by `sector` with a count. See `SearchQueries.build` and `SearchQueryTest`.

**"Limits of a text index?"** Only one per collection, no fuzzy matching (`paymnts` won't match), whole-word stemming only, and `$text` must be the first stage of an aggregation.

## MongoDB

- `companies` keyed by site (natural id): a re-crawl overwrites, never duplicates.
- `pages` keyed by URL with a content hash and fetch time: the crawl log, and the input for "has this page changed?" recrawl scheduling.
- Bulk upserts for the page log: one round trip per crawl.

## Testing

- Pure units for the tricky parts (normaliser, robots, rate limiter with a fake clock).
- Crawler against an in-memory `PageFetcher`, so tests are fast and deterministic and never hit the internet.
- One end-to-end test on a real MongoDB through Testcontainers.
