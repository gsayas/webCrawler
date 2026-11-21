# ADR 0003: Use jsoup for HTML Parsing

Status: Accepted

Date: 2026-10-12

## Context and Decision

I will use jsoup to extract links from fetched HTML and resolve
relative URLs, supplying the page URL as the document's base URI. This avoids
writing and maintaining my own HTML parser.

## Consequences

I accept an additional dependency. HTTP fetching stays with
[Ktor and OkHttp](0002-use-ktor-with-okhttp.md); URL eligibility, deduplication,
and scheduling remain my responsibility.

Reference: [jsoup URL resolution](https://jsoup.org/cookbook/extracting-data/working-with-urls).
