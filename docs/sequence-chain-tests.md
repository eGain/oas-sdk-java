# Sequence Chain Tests

A new test type, `sequence`, that enumerates every valid workflow chain on
each resource in an OpenAPI spec and emits a compile-ready pytest bundle
that runs them end-to-end against a live server.

Primary source: `src/main/java/egain/oassdk/testgenerators/sequence/SequenceChainTestGenerator.java`
Chain logic: `src/main/java/egain/oassdk/core/sequence/`
Tests: `src/test/java/egain/oassdk/core/sequence/`, `src/test/java/egain/oassdk/testgenerators/sequence/`

---

## The idea in 60 seconds

**Problem.** For a typical REST API you want end-to-end tests that call
operations *in sequence*: create a resource, read it back, maybe update
it, maybe delete it. Hand-writing those chains is tedious. Picking
orderings randomly (fuzz-style) gives statistical coverage but produces
noisy test reports where a 404 might mean "server broken" or "the random
draw just didn't satisfy the preconditions".

**What this generator does.** Given one OpenAPI spec it:

1. Extracts every operation into a flat list (`ApiCallInfo`).
2. Groups operations by resource.
3. For each resource, mechanically enumerates every valid ordering that
   starts with the POST creator and follows with the by-id operations,
   up to a bounded length.
4. Emits one pytest file per resource, one `def test_<shape>()` per chain.
5. Each generated test asserts `2xx` at every step.

**Why enumeration, not random.** If the chain is guaranteed valid by
construction, a non-`2xx` at any step is by definition a real bug — not
a "maybe the chain was nonsense". That gives failures you can act on.

Random/property-based coverage is still useful and is handled separately
by the Schemathesis bundle (`testgenerators/schemathesis`), which
complements this generator rather than overlapping with it.

---

## Quick example

Given this minimal folder spec:

```yaml
paths:
  /folders:
    post: { operationId: createFolder, requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/FolderCreate" } } } } }
  /folders/{folderID}:
    get: { operationId: getFolder }
components:
  schemas:
    FolderCreate:
      type: object
      properties:
        name: { type: string }
```

the generator emits (at `<outputDir>/sequence/test_chain_folders.py`):

```python
from conftest import extract_id

def test_folders_post(api_client, auth_headers, base_url):
    # Step 1 — POST /folders (expected 2xx)
    r = api_client.post(f"{base_url}/folders", json={"name": "mock_name"}, headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 1 POST /folders: {r.status_code} {r.text}"


def test_folders_post_get(api_client, auth_headers, base_url):
    # Step 1 — POST /folders (expected 2xx)
    r = api_client.post(f"{base_url}/folders", json={"name": "mock_name"}, headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 1 POST /folders: {r.status_code} {r.text}"
    resource_id = extract_id(r)
    assert resource_id is not None, "Creator response had no extractable id — subsequent steps cannot proceed"
    # Step 2 — GET /folders/{folderID} (expected 2xx)
    r = api_client.get(f"{base_url}/folders/{resource_id}", headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 2 GET /folders/{{folderID}}: {r.status_code} {r.text}"
```

Running it:

```bash
cd <outputDir>/sequence
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export API_BASE_URL=http://localhost:8080
pytest -v
```

---

## Validity rules

Every emitted chain satisfies all of these at enumeration time. There is
no runtime filter — if a chain is emitted, every step is expected to be
`2xx`.

1. **Creator-first.** Step 0 is the resource's `POST` without path
   parameters. Resources with no creator are skipped entirely (no id to
   seed consumers).
2. **One creator per chain.** Steps 1..n are path-templated consumers
   (GET/PUT/PATCH/DELETE by id, POST on a sub-resource path). A second
   top-level POST cannot appear.
3. **DELETE is terminal** (when `deleteLastOnly = true`, the default).
   Once the resource is deleted the id is gone, so nothing can follow.
4. **No repeats** (when `allowRepeats = false`, the default). A consumer
   appears at most once per chain.

The decision procedure, one check per rule, is implemented in
`ChainEnumerator.enumerateResource` (`:55`) and the `hasDeleteBeforeEnd`
helper (`:125`).

### The `POST, GET, POST` case

Whether a chain with two POSTs is valid depends entirely on rule 2:

- `[POST /folders, GET /folders/{id}, POST /folders/{id}/copy]` — valid.
  The third step is a *consumer* POST (has a path parameter), so rule 2
  is satisfied.
- `[POST /folders, GET /folders/{id}, POST /folders]` — invalid. The
  third step is a second *creator*. Never emitted.

---

## Configuration

Pass `TestConfig.additionalProperties` keys:

| Key                            | Default | Meaning |
| ---                            | ---     | --- |
| `sequence.maxChainLength`      | `4`     | Cap on chain length (creator + up to N−1 consumers). |
| `sequence.allowRepeats`        | `false` | Allow a consumer to appear twice (e.g. `[POST, PATCH, PATCH]`). |
| `sequence.deleteLastOnly`      | `true`  | Filter chains where DELETE isn't terminal. Turn off to exercise post-delete behavior. |
| `sequence.baseUrl`             | first `servers[].url` → `http://localhost:8080` | Baked into `conftest.py` as the `API_BASE_URL` default. Env var overrides. |

Chain count grows fast. For `c` consumers and max length `L`, the count
is bounded by `1 + Σ P(c, k)` for `k = 1..L-1`. Four consumers at
`L = 4` yields 26 chains per resource. Tune `maxChainLength` downward
if your spec has many resources.

---

## What this generator is NOT

- **Not a random sequence tester.** Random coverage lives in the
  Schemathesis bundle — stateful mode is enabled by default
  (`schemathesis.phases = coverage,stateful`).
- **Not a response-schema validator.** Each step asserts only that the
  status is `2xx`. Schemathesis' `--checks all` covers response schema,
  status code conformance, content type, missing headers.
- **Not a cross-resource workflow tester.** Each chain is scoped to one
  resource. Cross-resource producer-consumer chains are Schemathesis'
  lane.
- **Not a payload fuzzer.** Request bodies come from a single-level
  property walk (`ApiCallExtractor.buildRequestBodyForOperation`):
  strings get `"mock_<field>"`, numbers `1`, booleans `true`. Anything
  more structured needs Schemathesis.

---

## End-to-end recipe

```bash
mvn exec:java -Dexec.mainClass=egain.oassdk.cli.OASSDKCLI \
    -Dexec.args="tests --spec openapi.yaml --output out --type sequence"
cd out/sequence
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
API_BASE_URL=http://localhost:8080 API_TOKEN="Bearer $TOKEN" pytest -v
```

Failures look like:

```
FAILED test_chain_folders.py::test_folders_post_get_delete -
  AssertionError: Step 2 GET /folders/{folderID}: 404 {"detail":"not found"}
```

The per-step location and the status/body pinpoint the bug.
