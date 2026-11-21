# ADR 0005: Use Coordinator-Owned Deduplication

Status: Accepted

Date: 2026-10-13

## Context

Concurrent Workers may discover the same URL, and pages may link back to earlier
pages. Deduplication must cover the whole crawl, including work that is queued
or still running.

## Decision

I will use one Coordinator coroutine to own a `HashSet<String>` of normalized
URLs, the pending queue, and outstanding-task accounting. The Coordinator records
a URL before enqueueing it. A fixed number of Workers in the same process fetch
and parse pages, then return results without modifying that state.

The [Coordinator implementation](../src/main/kotlin/Coordinator.kt) contains
the scheduling and completion logic.

## Alternatives and Consequences

A concurrent set shared by Workers would protect individual insertions, but
queue updates and completion would still need coordination. Single ownership
keeps those decisions together and allows an ordinary `HashSet`.

The set stores complete URLs so hash collisions do not discard distinct URLs.
Failed requests remain recorded; retries would need an explicit policy. Memory
grows with unique discovered URLs. Cross-process coordination is outside this
implementation.
