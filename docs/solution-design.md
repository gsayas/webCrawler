# Solution Design

I'm breaking down the solution by identifying the main subproblems and their most relevant technical aspects. I'm also
highlighting the **NFRs** that can be derived from the functional requirements.

- Crawling isn't usually an end-user-facing operation, so it can be done as a **background**, 
**parallelizable** process. That means we don't care so much about giving an immediate response (latency), but more about 
throughput.

- Finding all the URLs of the given domain is the highest-level problem. There's a Coordinator that schedules the starting URL
for a Worker to fetch and parse. Workers return all discovered links; the Coordinator reports them and decides which
ones to schedule next.


-----

## Coordinator and Workers

The Coordinator runs in the parent coroutine and launches a fixed number of Worker child coroutines in the same process,
as recorded in [ADR 0005](../adr/0005-use-coordinator-owned-deduplication.md). Each Worker uses a Finder to fetch and
parse one URL, then returns a result. Workers do not schedule URLs or access the Coordinator's mutable state.

Workers are children of the Coordinator's `coroutineScope`. Expected request failures return results; unexpected
failures cancel the scope, including any wait for results. A `finally` block cancels Workers and joins them inside
`NonCancellable`, so cleanup also runs when the crawl fails or is cancelled.

## Performance analysis

The worker count defaults to 8 and is configurable. OkHttp's global and per-host request limits match this value;
otherwise, its default limit of five requests per host restricts the effective concurrency.

Informal runs against 42,011 URLs averaged about 124 seconds with 8 workers, 49 seconds with 16, and 32 seconds with 32.
Warm runs with 90 workers took about 30 seconds, showing little improvement beyond 32. Eight remains a conservative
default to limit load on the target; the worker count is configurable when higher throughput is appropriate.

Only successful responses explicitly marked as `text/html` are parsed for links. Other responses are still reported as
visited, but their bodies are cancelled without parsing. Requests use a five-second timeout so slow or broken URLs do
not occupy workers indefinitely.

Implementation: [Coordinator](../src/main/kotlin/Coordinator.kt) and [Finder](../src/main/kotlin/Finder.kt).
Diagram: [crawler design](diagrams/crawler-design.png).
