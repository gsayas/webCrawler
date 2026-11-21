# ADR 0004: Use JUnit with a Local Test HTTP Server

Status: Accepted

Date: 2026-10-12

## Context

Tests against public websites would depend on changing content and
network availability.

## Decision

I will use JUnit for unit and integration tests. Unit tests will cover isolated
logic such as URL eligibility and link extraction. Integration tests will run
the crawler's real Ktor/OkHttp client against a local HTTP server with controlled
pages and responses.

This lets me test cycles, duplicate links, redirects, failures, and concurrency
limits repeatably. I will use synchronisation for concurrency assertions rather
than depend on request ordering or arbitrary sleeps. The implementation uses
the JDK's built-in `HttpServer`.

## Alternatives and Consequences

Mocking every HTTP interaction would simplify setup but would leave the real
client configuration untested. A local server adds setup and cleanup, which I
accept for coverage of actual HTTP behaviour.

Each server will use an available local port and be shut down after its tests.
The suite will run locally and potentially in CI without contacting public websites.
