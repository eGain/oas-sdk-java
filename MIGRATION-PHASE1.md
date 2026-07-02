# Phase I migration guide (Nebula redesign)

For the full redesign rationale see [Redesign-OSSDK.md](Redesign-OSSDK.md) and [docs/flow-dsl.md](docs/flow-dsl.md).

## Auth: `useEgainAuth` / `EgainAuth` removed

Phase I is **vendor-neutral**. Generated test-support no longer emits `EgainAuth.java`, `EgainAsyncTaskHelper.java`, or `EgainInternalKbHelper.java`. `TestSpecUtils.useEgainAuth()` always returns `false`.

| Old | New |
|-----|-----|
| `useEgainAuth=true` / auto eGain spec detection | `auth.provider=chain` in `test-env.properties` |
| `EgainAuth.fetchBearerToken()` | `TestAuth.rawToken()` (chain or static token) |
| `fetch-token.sh` → `EgainAuth` main | `AuthTokenCli` or chain properties |
| `INTEGRATION_TOKEN_*` env vars | Unchanged; override tokens when needed |

### eGain v20 session + OAuth2 (chain auth)

Copy [examples/auth-profiles/egain-v20-session-oauth.properties](examples/auth-profiles/egain-v20-session-oauth.properties) into `generated-tests/test-support/src/test/resources/test-env.local.properties` (do not commit secrets):

```properties
auth.provider=chain
base.url=https://YOUR_HOST/system/ws/knowledge/contentmgr/v4
auth.login.base=https://YOUR_HOST/system/ws/v20
auth.username=YOUR_USER
auth.password=YOUR_PASSWORD

auth.chain.1.url=${auth.login.base}/authentication/user/login?forceLogin=yes
auth.chain.1.method=POST
auth.chain.1.header.Content-Type=application/json
auth.chain.1.body={"userName":"${auth.username}","password":"${auth.password}"}
auth.chain.1.extract.header=x-egain-session
auth.chain.1.save=session

auth.chain.2.url=${auth.login.base}/authentication/user/advisor/oauth2/token
auth.chain.2.method=GET
auth.chain.2.header.X-egain-session=${session}
auth.chain.2.header.accept=application/json, text/plain, */*
auth.chain.2.extract.json=access_token

auth.chain.final=access_token
```

## Lifecycle hooks without Egain helpers

| Hook | Integration module | Phase I |
|------|-------------------|---------|
| **If-Match edit** | `IntegrationTestUtils.readEtag` + header | **Works** — uses bearer from `TestAuth` / chain |
| **Stale If-Match 412** negative | Generated when hook registered | **Works** |
| **v20 async task poll** | Was `EgainAsyncTaskHelper` | **Use lifecycle Flow DSL** — author `.flow` under `lifecycle/src/test/flows/` |
| **Internal KB verify** | Was `EgainInternalKbHelper` | **Use lifecycle Flow DSL** |

## Mock data split (`mockData` flag)

| Path | `mockData=false` | `mockData=true` |
|------|------------------|-----------------|
| `OASSDK.generateMockData()` | No-op | Bulk `mock-data/` via `MockDataGenerator` |
| `OASSDK.generateAll()` | Skips `mock-data/` | Generates `mock-data/` |
| `TestSupportGenerator` | Skips `test-support/.../bodies/*.json` | One JSON per `operationId` for `RequestBodyFactory` |

Contract and integration tests use **`RequestBodyFactory`** + per-operation bodies, not the legacy hundreds of schema JSON files.

## Renames and deprecations

| Old | New |
|-----|-----|
| `unit` test module | **`contract`** |
| `sequence-java` in default `generateAll` | **`lifecycle`** + Flow DSL |
| `sequence-java` generator | **Deprecated** — still in `TestGeneratorFactory` for backward compat |

## Operation filtering

Set `test.include.operations` (comma-separated `operationId` list) in test config to limit generated tests without regenerating from a trimmed spec.
