# Concurrent Web Crawler

A concurrent Kotlin/JVM crawler. Starting from one URL, it reports every visited
URL and its links, while following only HTTP(S) links with the same hostname.

## Run

```shell
./gradlew run --args="https://giocode.me/ 8"
```

The worker count is optional and defaults to `8`.

## Test

```shell
./gradlew test
```

## Docs

* [A diagram of the solution](docs/diagrams/crawler-design.excalidraw)
* [A summary of the design](docs/solution-design.md)
* [Architectural decisions records](adr)
* [Testing Strategy](docs/testing-strategy.md)
