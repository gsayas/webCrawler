# Testing strategy

Automated tests use JUnit and a local in-process HTTP server. This keeps HTTP behaviour repeatable and independent of external network conditions.

The suite covers URL filtering and normalization, link extraction and resolution, redirects, duplicate and cyclic links, non-HTML and unsuccessful responses, output formatting, concurrency limits, and unexpected worker failures.

Manual runs against the real test site are used to evaluate performance with different worker counts. These results are treated as informal measurements because network and server conditions can vary; the local test suite remains the correctness safety net.
