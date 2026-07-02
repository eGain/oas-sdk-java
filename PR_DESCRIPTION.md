## Summary

Phase I of the [Nebula OSSDK redesign](Redesign-OSSDK.md): vendor-neutral auth, Flow DSL lifecycle module, safer test generation, and removal of the status-code matrix that produced false failures.

- **Flow DSL + lifecycle module** — hand-authored `.flow` files interpreted at test runtime ([docs/flow-dsl.md](docs/flow-dsl.md))
- **unit → contract** rename
- **`RequestBodyFactory`** + per-operation `bodies/*.json` (when `mockData=true`)
- **Generic auth** — `HttpChainAuth` / `AuthChainExecutor`; `auth.provider=chain|static|curl`
- **`test.include.operations`** filtering at generation time
- **Status-code matrix removed** — auth negatives (401/403) only; no false 400/404 failures
- **EgainAuth helpers removed** from default generation ([MIGRATION-PHASE1.md](MIGRATION-PHASE1.md))

### Bug fix (blocker)

`IntegrationTestGenerator.appendTaggedTest` had infinite recursion (StackOverflow on all integration test generation). Fixed to emit `@Test` + optional `@Tag`.

## Breaking changes

See [MIGRATION-PHASE1.md](MIGRATION-PHASE1.md):

- `useEgainAuth` / generated `EgainAuth*` classes → `auth.provider=chain`
- Default `generateAll` modules: `lifecycle` replaces `sequence-java`
- Async poll / internal KB verify in integration → lifecycle Flow DSL

## Nebula alignment

| Item | Status |
|------|--------|
| Status-code matrix (P0) | Partial — matrix removed; auth negatives only |
| unit→contract (P1) | Done |
| Generic auth chain (P0) | Done |
| Unified config (P0) | Partial |
| Operation filter (P1) | Done |
| Lifecycle / Flow DSL (P1) | Done |
| RequestBodyFactory (P1) | Done |

Phase II (not in this PR): unify HTTP client stacks, compile gate IT, 400/404 provoking inputs, per-field contract negatives, NFR/TestEnv alignment.

## Test plan

- [x] `mvn test` (full SDK unit suite)
- [x] `IntegrationTestGeneratorTest` — generated sources include `@Test`
- [x] `OASSDKTest.testGenerateAll_skipsMockDataWhenDisabled`
- [ ] Regenerate bundle: `mvn exec:java -Dexec.mainClass=egain.oassdk.examples.RegenerateBundleExample`
- [ ] Run one lifecycle `.flow` on a live host (optional)
