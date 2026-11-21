# ADR 0002: Use Ktor Client with the OkHttp Engine

Status: Accepted

Date: 2026-10-12

## Context

Following the [Kotlin decision](0001-use-kotlin.md), I need an HTTP client that
fits a coroutine-based crawler.

## Decision

I will use Ktor Client with the OkHttp engine. Ktor provides the suspending
request and response APIs, while OkHttp handles the underlying HTTP transport.
This keeps the fetching code straightforward to compose with coroutines and
provides connection pooling and HTTP/2 support through OkHttp. HTTP/2 is useful
but is not a requirement or a claimed performance advantage for this exercise.

These libraries handle individual HTTP requests. URL eligibility, deduplication,
crawl scheduling, concurrency limits, termination, and output remain part of my
implementation. Using them therefore fits my reading of the restriction on
crawling frameworks.

## Alternatives Considered

- Ktor with CIO: also suitable; I chose OkHttp for its transport capabilities,
  accepting an additional dependency.
- Direct OkHttp: fewer abstraction layers, but more care is needed to integrate
  calls and response-body reading with coroutines.
- JDK HttpClient: avoids an external HTTP library, but requires adapting its
  asynchronous API to the coroutine interface I want to use.

## Consequences

I accept Ktor's additional API and configuration layer over OkHttp. I will reuse
a client, configure timeouts, and manage its lifetime explicitly. Automatic
redirect following will be disabled so the crawler can check each redirect
target against the allowed hostname before requesting it.

Concurrency limits and failure handling still need explicit implementation and
tests; choosing this client does not provide those crawler policies.

Reference: [Ktor client engines](https://ktor.io/docs/client-engines.html).
