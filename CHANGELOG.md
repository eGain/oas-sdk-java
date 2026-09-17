# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Generated JAXB model fields of type `XMLGregorianCalendar` (OpenAPI `date` / `date-time`) now emit `@XmlJavaTypeAdapter(XsDateTimeAdapter.class)`, matching XJC beans such as `DateAndUser#date`.
- Sequence-chain generator now builds one chain family per POST in the spec — top-level creators and sub-resource creators alike. A POST on `/orders/{orderId}/items` is preceded by `POST /orders` as a prefix producer so its `orderId` path parameter is bound before the sub-resource call fires. Alternative top-level creators on the same resource group (e.g. `POST /users` and `POST /users/bulk`) each seed their own chain family.
- `ChainConfig.unresolvedParamPolicy` (`SKIP` default, or `EMIT_WITH_MARKER`) controls how a sub-resource POST is handled when one of its path parameters has no producer POST in the spec. `EMIT_WITH_MARKER` emits the chain with a `pytest.skip(...)` at the top so the gap is visible in the test report instead of being silently dropped.
- `EnumeratedChain(seedPost, steps, unresolved)` record; `ApiCallExtractor.findProducerForParam` (longest-prefix match with a name-stem fallback); `ApiCallExtractor.idVariableName`; `ApiCallInfo.isSubResourceCreator`.
- New `sequence.unresolvedParamPolicy` key in `TestConfig.additionalProperties`.
- Randomized sequence tests (RST) aligned with Integrated-style flows: OAS-driven scenario templates (create/edit/delete + get), query-parameter positives, optional 401 expectations, `Location` header and `{param}` path resolution, scenario-biased sequences, and `GenerateRstFromPublishedSpec` (`mvn exec:java` with env `RST_*`) for published bundles without full SDK validation.
- Comprehensive logging system with file rotation
- Configurable logging via properties file, system properties, or environment variables
- Error level logging for all exception cases

### Changed
- Sequence-chain bundle emits one file per **seed POST's** resource (keyed on `seedPost.resourceName()`). Sub-resource POSTs now land in their own `test_chain_<resource>.py` instead of being dropped.
- Each POST step in a generated chain captures its response id into a variable named after the path parameter consumers will reference (snake-cased — `orderId` → `order_id`, `folderID` → `folder_id`). Replaces the single shared `resource_id` variable used previously. `extract_id(response, hint=<paramName>)` now takes an optional hint so the conftest helper can prefer the expected field name in the response body.
- `ChainEnumerator.enumerate` return type changed from `List<List<ApiCallInfo>>` to `List<EnumeratedChain>` so each chain carries its seed POST and unresolved-param flag.
- Reorganized generated code structure to use `generated-code/` top-level folder
- Updated all integration tests to use new directory structure
- Improved reference resolution in OASParser

### Removed
- `ChainConfig.crossResource` (field, builder method, and defaults entry). It was never read anywhere in the codebase; the new `unresolvedParamPolicy` replaces it conceptually for the "what do we do at the chain boundary" question.

### Fixed
- Fixed encoding issues by using `StandardCharsets.UTF_8` consistently
- Fixed internal representation exposure with defensive copying
- Fixed locale-sensitive operations by using `Locale.ROOT`
- Fixed unchecked cast warnings using Util methods
- Fixed null pointer access issues
- Fixed raw type warnings in pattern matching
- Fixed YAML parsing errors in test resources
- Fixed System.err.println calls by replacing with proper logging
- Migrated File API to java.nio.file.Files for better robustness

## [2.0] - 2025-12-26

### Added
- Built-in Observability Framework (OpenTelemetry + Micrometer) for all generated languages
- JerseyGenerator decomposition (7,318 LOC -> 13 focused classes)
- Enhanced mock data generation with `$ref` resolution in allOf/oneOf/anyOf compositions
- OAS-driven randomized sequential testing with dependency graph inference
- Multi-language test generation (Java/JUnit5, Python/pytest, Node.js/Jest)
- Complete feature parity across Java, Python, and Node.js generators
- SLA monitoring with atomic sliding-window rate limiter and 7-panel Grafana dashboard
- Negative test case generation (empty body, malformed JSON, boundary values)
- RBAC, CORS, and rate limiting test generation
- Correlation ID propagation via CorrelationIdFilter

### Changed
- Simplified test resources (reduced from ~52,000 to ~1,100 lines)
- All file operations now use UTF-8 encoding explicitly
- String case conversions use Locale.ROOT for locale safety
- Resolved all high-priority SpotBugs warnings

### Fixed
- Encoding issues with StandardCharsets.UTF_8
- Defensive copying for mutable collections
- StackOverflowError blocks replaced with proper cycle detection
- Missing Base64 import in MockDataGenerator
- Dockerfile updated to eclipse-temurin:21-jre

## [1.17.0] - 2025-12-26

### Added
- Java Jersey/JAX-RS generator (100% feature complete)
- Python FastAPI generator (100% feature complete)
- Python Flask generator (100% feature complete)
- Node.js Express generator (100% feature complete)
- Comprehensive test generation (Java, Python, Node.js)
- Mock data generation
- SLA enforcement generation
- Professional documentation generation (Redocly, Swagger UI, Markdown)
- CLI interface for easy usage
- Support for OpenAPI 3.0 and 3.1 specifications
- Query parameter validation
- Security scheme extraction and annotation
- Inline schema model generation
- API version extraction from server URLs

### Changed
- Improved code quality with SpotBugs analysis
- Enhanced error handling and validation
- Optimized reference resolution

### Fixed
- Various bug fixes and improvements

## [1.0.0] - Initial Release

### Added
- Initial release of OAS SDK
- Basic code generation capabilities
- OpenAPI specification parsing
- Core SDK functionality

---

[Unreleased]: https://github.com/egain/oas-sdk-java/compare/v2.0...HEAD
[2.0]: https://github.com/egain/oas-sdk-java/compare/v1.17.0...v2.0
[1.17.0]: https://github.com/egain/oas-sdk-java/releases/tag/v1.17.0
[1.0.0]: https://github.com/egain/oas-sdk-java/releases/tag/v1.0.0

