# ADR 0001: Use Kotlin/JVM

Status: Accepted

Date: 2026-10-12

## Context

I want to use a language I am familiar with, and put the emphasis on software design, concurrency,
and test coverage. The crawler needs to discover and visit URLs within a single
hostname and report the links found on each page.

## Decision

I will use Kotlin on the JVM. It is the language I am most familiar with, which
lets me focus on the crawler's behavior, testing, and design trade-offs.

The workload is largely network I/O, and Kotlin's coroutines are a good fit for
overlapping requests with controlled concurrency. Structured concurrency also
provides a clear way to manage task lifetimes and cancellation. The JVM ecosystem
provides HTTP clients, HTML parsers, and testing tools while leaving the crawling
logic under my control.

## Alternatives Considered

Go, Java, Python, and Scala could all satisfy the requirements. Go would be a
reasonable choice for a concurrent command-line tool; Java offers familiar JVM
libraries; Python supports asynchronous I/O; and Scala offers several concurrency
approaches. None provides a benefit required by this exercise that outweighs my
greater familiarity with Kotlin.

## Consequences

I accept the JVM runtime requirement, startup overhead, and build setup for this
exercise. There are no stated constraints that make these deciding factors.

Coroutines still require explicit concurrency limits, safe ownership of shared
state, and tests for cancellation and completion. I will choose the HTTP client,
parser, and scheduling design separately, keeping the implementation small and
the trade-offs easy to explain.
