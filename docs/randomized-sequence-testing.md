# Randomized Sequence Testing (RST)

This document describes how randomized sequence testing is implemented in OAS SDK
Java. It is a *meta-test-generator*: given an OpenAPI spec, it emits a
self-contained JUnit 5 + JAX-RS harness that, at runtime, executes randomized
(and optionally scenario-biased) sequences of calls against a live API under
test.

Primary source: `src/main/java/egain/oassdk/test/sequence/RandomizedSequenceTester.java`
Tests: `src/test/java/egain/oassdk/test/sequence/RandomizedSequenceTesterTest.java`

---

## 1. Two-stage design

RST operates in two clearly separated stages.

### Stage A — generation time (runs inside the SDK)

`RandomizedSequenceTester.generateSequenceTests(spec, outputDir, baseUrl)`
(`RandomizedSequenceTester.java:34`) reads the parsed OpenAPI spec (a
`Map<String, Object>` produced by `OASParser`) and emits five Java files into
`outputDir`:

| File | Role |
| --- | --- |
| `SequenceTestFramework.java`   | Base class: JAX-RS client lifecycle, shared state map, template-param resolution, per-call execution, response-driven state capture, result validation, APICall / SequenceResult types. |
| `RandomSequenceGenerator.java` | Pure sequence builder: random, weighted, dependency-gated, stateful, and scenario-biased strategies over a supplied `List<APICall>`. |
| `SequenceTestCases.java`       | JUnit 5 class extending the framework. Declares `@Test` methods for each sequencing strategy and for per-scenario templates derived from the spec. |
| `SequenceTestRunner.java`      | Standalone `main` that boots the JUnit Platform and runs `SequenceTestCases`. |
| `SequenceTestConfig.java`      | Static factory exposing the same `availableCalls` list so other harnesses can instantiate `RandomSequenceGenerator` directly. |

The generator uses Java 21 text blocks with `.formatted(...)` for every file —
no FreeMarker templates are involved, and the generated code has no build-time
dependency on the SDK itself.

### Stage B — runtime (runs against the system under test)

The generated project is compiled and run against a live base URL. The JUnit
tests construct a `RandomSequenceGenerator`, ask it for a sequence, execute the
sequence through the framework, and assert on the aggregated results.

---

## 2. Spec → `APICallInfo` extraction

`extractAPICallsFromSpec` (`RandomizedSequenceTester.java:64`) walks
`spec.paths`, matches each verb against `Constants.HTTP_METHODS`
(`src/main/java/egain/oassdk/core/Constants.java:15`), and emits one
`APICallInfo` per operation with these fields (`RandomizedSequenceTester.java:1307`):

- `method`, `path`, `operationId`, raw `operation` map
- `hasPathParams`   — detected by `path.contains("{")`
- `hasRequestBody`  — presence of `requestBody`
- `resourceName`    — last non-templated path segment, via `extractResourceName`
  (`:102`); falls back to `"resource"`
- `pathParamNames`  — regex extraction using `PATH_PARAM_PATTERN = \{([^}]+)}`
  (`:24`, `:113`)
- `defaultQueryParams` — every query parameter with a reasonable default,
  produced by `buildDefaultQueryParams` + `pickExampleForQueryParam` (`:122`, `:185`)

### Query-param default resolution (`pickExampleForQueryParam`)

Preference order:
1. `schema.example` (string / number / boolean)
2. `schema.default`
3. First entry of `schema.enum`
4. Typed fallback — `minimum` for integer/number, `"true"` for boolean,
   `"test"` for string
5. If the param is `required` and nothing above fit, `"1"` is used so the call
   still dispatches.

Refs in `parameters` are resolved via `resolveParameterRef` against
`#/components/parameters/...` (`:169`); nested refs are not followed.

### Request-body mock

`buildRequestBodyForOperation` (`:314`) looks up `application/json` content
(falling back to the first media type), resolves a top-level `$ref` into
`components.schemas`, then calls `buildMockJsonFromSchema` (`:357`) to emit a
shallow JSON object. It is deliberately minimalist:

- One level of properties, typed string / integer / number / boolean only
- Strings become `"mock_<fieldName>"`; numbers become `1`; booleans `true`
- Depth is capped at 5 (returns `"{}"`) — this is a hard guard, not a recursion
  budget. Nested objects, arrays, `allOf`/`oneOf`/`anyOf`, format hints, and
  required-field discrimination are intentionally ignored; richer bodies are a
  future improvement.
- The produced JSON is embedded as a *Java string literal inside a generated
  source file*, so the JSON quotes are pre-escaped (`\\\"`) — see the
  concatenation in `buildMockJsonFromSchema`.

---

## 3. Generator-side strategies (runtime behavior of `RandomSequenceGenerator`)

All strategies sample sequence length from `random.nextInt(maxLength) + 1`
(so 1 ≤ length ≤ `maxLength`) and share a single `Random` seeded at
construction. Two constructors accept an explicit seed to make failures
reproducible — see `RandomizedSequenceTester.java:867` and `:875` in the
emitted source.

| Strategy | How a step is chosen |
| --- | --- |
| `generateRandomSequence(max)`                | Uniform sample from `availableCalls`. |
| `generateWeightedSequence(max, weights)`     | Cumulative-weight draw keyed by `"<path>:<METHOD>"`; default weight 1.0. Generated `weights` emphasize reads (GET=0.3, other=0.15) — see `RandomizedSequenceTester.java:1076`. |
| `generateDependentSequence(max, deps)`       | Eligibility filter: a call is eligible only when every `deps.get(key)` entry has already been executed in this sequence. Deps that never resolve quietly drop the step. |
| `generateStatefulSequence(max, transitions)` | Keeps a `currentState` string updated by `transitions.getOrDefault(key, currentState)`. Eligibility filter here is intentionally *weak* — it admits any call present in the transitions map, regardless of source state. This is a known limitation; the transition map emitted by generation is `key -> <resource>_<verb>` (`RandomizedSequenceTester.java:1068`), so the sequences exercise the transition *table* rather than enforcing a DFA. |
| `generateScenarioBiasedSequence(max, w)`     | With probability `w` (default 0.45), splice in a full *scenario template* (see §5); otherwise draw one uniformly random call. Template steps are appended until `targetLen` is reached. |

The dependency map itself is inferred by `inferDependencies`
(`RandomizedSequenceTester.java:404`):

- POSTs without path params are declared `creationEndpoints`, keyed by
  `resourceName`.
- Any call whose path contains `{…}` is made to depend on the creation endpoint
  for the same resource (if one exists).
- Everything else is declared dependency-free.

This is a single-level heuristic — it will not, for example, make `GET /a/{x}/b/{y}`
depend on both `POST /a` and `POST /a/{x}/b`.

---

## 4. Runtime framework (`SequenceTestFramework`)

The emitted framework owns the live JAX-RS clients and the shared mutable state
that makes multi-step sequences possible. Interesting pieces:

### Two clients per test

`client` is the authenticated client; `clientNoAuth` is used when an `APICall`
has `noAuth=true`. This is how the generated `expect401` scenario asserts 401
without tearing down other state.

### `state: ConcurrentHashMap<String, Object>`

Populated from two sources as each response lands:

- **`updateState`** (`RandomizedSequenceTester.java:690`) — on a 2xx with a
  body, reads the JSON through Jackson and looks for an ID under
  `id`/`ID`/`_id`/`uuid`/`resourceId` via `extractIdFromResponse` (`:723`). If
  found, it is stored under `"id"` *and* under each path-param name discovered
  across the whole spec — `bodyIdBootstrap` at generation time expands into
  `state.putIfAbsent("<paramName>", extractedId)` lines (`:445`, `:705`). That
  is why `/folders/{folderID}` works end-to-end after a `POST /folders`: the
  generator bakes `"folderID"` into the bootstrap.
- **`captureLocationState`** (`:623`) — on any 2xx with a `Location` header,
  the last URL segment is stored under `"locationResourceId"`, `"id"`, and
  every path-param name (`locationBootstrap`, `:446`).

### Template-param resolution

`resolveTemplateParams` (`:593`) substitutes each `{name}` in the path with
`resolveStateForPathParam(name)` (`:560`), which tries, in order:
1. `state[name]`
2. `state[name + "_id"]`
3. Case-insensitive match on `<name.toLowerCase()>_id`
4. `state["id"]`
5. `state["locationResourceId"]`
6. Any `state` key ending in `_id` with a non-null value

If nothing resolves, the original `{name}` segment is left in place — the
request will almost certainly fail validation, which is the intended behavior.

### Cleanup: auto-DELETE of created resources

When a POST returns 200/201 with an extracted ID, `updateState` appends
`DELETE <path>/<id>` to `createdResources`. `tearDown` (`:502`) reverses that
list and issues the DELETEs after each test. Errors during cleanup are
swallowed.

### Validation (`validateSequenceResults`, `:757`)

Each result must satisfy:
- `isSuccess()` (no transport exception)
- `responseTime <= Constants.DEFAULT_MAX_RESPONSE_TIME_MS` (5000 ms by default,
  baked in at generation time)
- Status not 5xx
- If `APICall.expectedStatus >= 0`, status must match exactly; otherwise any
  non-5xx is accepted

### `APICall` shape

`(method, path, body, headers, queryParams, expectedStatus, noAuth)`. The
`expectedStatus == -1` sentinel means "any non-5xx". This lets the same
emitted constructor serve both fuzzing calls and targeted assertions (e.g.
401).

### Per-call execution (`executeAPICall`, `:648`)

Standard JAX-RS invocation. PUT/POST/PATCH default the body to `"{}"` when
`call.getBody()` is null, which is what keeps randomized writes from 400ing
out trivially.

---

## 5. Integrated scenarios (operationId-driven)

`buildIntegratedScenarioCodegen` (`:1325`) is a set of hand-coded scenario
templates inspired by the eGain "Integrated sheet" of end-to-end folder flows.
It introspects the spec for folder-like operations by exact `operationId`
match (`createFolder`, `getFolder`, `editFolder`, `deleteFolder`) and by
substring match (`copy`, `move`, `ermission`).

For each scenario:
- If every required operation is present, `emitScenarioIfPresent` (`:1466`)
  writes a `scenario<Name>()` helper returning a `List<APICall>` built from
  the matched `APICallInfo`s, plus a `@Test testScenario_<name>` method.
- If any required operation is missing, `emitDisabledScenario` (`:1458`)
  writes a `@Disabled` stub with a human-readable reason. This is why the
  generated test suite compiles (and even reports scenarios as disabled) on
  specs that don't look like the folder API.

Built-in scenarios:

| Scenario | Sequence |
| --- | --- |
| `createAndGet`                          | create → get |
| `editAndGet`                            | create → edit → get |
| `deleteAndGet`                          | create → delete → get |
| `editPermissionsAndGet`                 | create → permissions op → get |
| `copyFolderAndGet`                      | create → copy → get |
| `moveFolderAndGet`                      | create → move → get |
| `queryLangPositive` / `queryLevelPositive` / `querySortOrderPositive` / `queryPaginationPositive` | create → get with specific query-param overrides, picked from the actual GET's parameters via `pickQueryParamsMatching` (`:1444`), with sensible fallbacks if the spec doesn't declare them |
| `expect401`                             | create → get with `noAuth=true` and `expectedStatus=401` |
| `expect403`, `getUsingDifferentUser`, `editUsingDifferentUserThanCreateAndGet` | Emitted disabled — require multi-credential setups the harness cannot synthesize |

The collected scenario helpers are also returned as a single
`Arrays.asList(scenarioFoo(), scenarioBar(), …)` expression and injected into
`RandomSequenceGenerator`'s constructor via `SequenceTestCases.setUp`. That is
what drives `generateScenarioBiasedSequence`: at runtime the generator has
both atomic calls *and* whole proven sequences to splice in.

---

## 6. APICall codegen details

`appendAPICallConstructor` (`:263`) is the single emission point for every
`new APICall(...)` in the generated files. It:
- Escapes strings through `escapeJavaStringLiteral` (`:225`, handles `\` and `"`)
- Chooses the body expression: raw `null` if there is no `requestBody`,
  otherwise a JSON literal produced by `buildRequestBodyForOperation`
- Merges `defaultQueryParams` with an optional per-call override map and
  emits them through `toJavaMapExpression` (`:235`): `Map.of(...)` for ≤5
  entries, otherwise a `new LinkedHashMap<>() {{ put(...); }}` block (so
  larger maps preserve insertion order, matching the spec's parameter order)
- Forwards `expectedStatus` and `noAuth` verbatim

Because headers are always emitted as `null` today, any custom auth header has
to be set by editing the generated framework — a deliberate simplification.

---

## 7. Invocation surface

Today `RandomizedSequenceTester` is *not* wired into `TestGeneratorFactory`
and is not exposed as a CLI subcommand. Callers instantiate it directly:

```java
Map<String, Object> spec = /* parsed OAS map */;
new RandomizedSequenceTester()
    .generateSequenceTests(spec, "generated/sequence", "https://api.example.com/v1");
```

The only in-tree caller is `RandomizedSequenceTesterTest` (the bundled
folder-like spec in its `minimalFolderSpec()` is the canonical integration
fixture — it is what exercises the full folder scenario pipeline). Wiring the
tester into `TestGeneratorFactory` under a new subtype (e.g. `sequence`)
alongside `unit`/`integration`/`schemathesis`/etc. is a natural extension.

---

## 8. Worked example

The snippets below trace one small spec all the way from parse to runtime.
Line and file references cite the *generator* (`RandomizedSequenceTester.java`);
the JAX-RS code in them is what actually lands in the emitted output
directory.

### 8.1 Input spec

A minimal folder API — a single creation endpoint and a single by-ID
read. This is the shape exercised by `RandomizedSequenceTesterTest.minimalFolderSpec`.

```yaml
openapi: 3.0.0
paths:
  /folders:
    post:
      operationId: createFolder
      requestBody:
        content:
          application/json:
            schema: { $ref: "#/components/schemas/FolderCreate" }
  /folders/{folderID}:
    get:
      operationId: getFolder
      parameters:
        - name: kbLanguage
          in: query
          required: true
          schema: { type: string, enum: ["en-US", "fr-FR"] }
        - name: $level
          in: query
          required: false
          schema: { type: integer, minimum: 0 }
components:
  schemas:
    FolderCreate:
      type: object
      properties:
        name: { type: string }
```

### 8.2 Caller

```java
Map<String, Object> spec = new OASParser().parse(Path.of("folders.yaml"));
new RandomizedSequenceTester()
    .generateSequenceTests(spec, "generated/sequence",
                           "https://api.example.com/v1");
```

This writes the five `.java` files listed in §1 into `generated/sequence/`.

### 8.3 What `extractAPICallsFromSpec` produces

Two `APICallInfo` records, with the fields the rest of the generator reads:

| Field | Call 1 | Call 2 |
| --- | --- | --- |
| `method`             | `POST`              | `GET` |
| `path`               | `/folders`          | `/folders/{folderID}` |
| `operationId`        | `createFolder`      | `getFolder` |
| `resourceName`       | `folders`           | `folders` *(last non-templated segment)* |
| `hasPathParams`      | `false`             | `true` |
| `hasRequestBody`     | `true`              | `false` |
| `pathParamNames`     | `[]`                | `[folderID]` |
| `defaultQueryParams` | `{}`                | `{kbLanguage=en-US, $level=0}` |

How the defaults for call 2 are chosen, per `pickExampleForQueryParam`
(`RandomizedSequenceTester.java:185`):

- `kbLanguage` — no example/default, has `enum` → first entry `"en-US"`.
- `$level` — no example/default/enum; type is `integer` with `minimum=0` → `"0"`.

### 8.4 What the generator bakes into `SequenceTestCases.java`

**`initializeAPICalls()`** — the result of `appendAPICallConstructor` for each
extracted call (`RandomizedSequenceTester.java:263`):

```java
private List<APICall> initializeAPICalls() {
    List<APICall> calls = new ArrayList<>();

    calls.add(new APICall("POST", "/folders",
        "{\"name\": \"mock_name\"}",
        null, Collections.emptyMap(), -1, false));
    calls.add(new APICall("GET", "/folders/{folderID}",
        null, null,
        Map.of("kbLanguage", "en-US", "$level", "0"), -1, false));

    return calls;
}
```

Note:
- The POST body is the output of `buildMockJsonFromSchema` on the resolved
  `FolderCreate` schema — one property, one string, so `{"name": "mock_name"}`.
- `expectedStatus = -1` means "any non-5xx" (see §4).
- The GET's query-param map preserves spec order via `toJavaMapExpression`
  (`RandomizedSequenceTester.java:235`).

**Inferred `dependencies`** emitted into `testDependentSequence`:

```java
dependencies.put("/folders:POST",             Arrays.asList());
dependencies.put("/folders/{folderID}:GET",   Arrays.asList("/folders:POST"));
```

(`inferDependencies`, `RandomizedSequenceTester.java:404` — the GET has a path
param, so it depends on the POST creation endpoint for the same
`resourceName`.)

**Emitted weights** for `testWeightedSequence`:

```java
weights.put("/folders:POST",           0.15);
weights.put("/folders/{folderID}:GET", 0.3);
```

**Integrated scenarios** — only scenarios whose operations are all present are
emitted live; the rest are `@Disabled` stubs with reasons. For this spec:

| Scenario | Status |
| --- | --- |
| `testScenario_createAndGet`           | **emitted**  (createFolder + getFolder present) |
| `testScenario_queryLangPositive`      | **emitted**  (`kbLanguage` contains `"lang"` → override `{kbLanguage: en-US}`) |
| `testScenario_queryLevelPositive`     | **emitted**  (`$level` contains `"level"` → override `{$level: 0}`) |
| `testScenario_querySortOrderPositive` | emitted with fallback `{$sort: name, $order: asc}` (no matching params in spec) |
| `testScenario_queryPaginationPositive`| emitted with fallback `{$pagenum: 1, $pagesize: 10}` |
| `testScenario_expect401`              | **emitted**  (`noAuth=true, expectedStatus=401` on the GET) |
| `testScenario_editAndGet`             | `@Disabled` — no `editFolder` in spec |
| `testScenario_deleteAndGet`           | `@Disabled` — no `deleteFolder` in spec |
| `testScenario_copyFolderAndGet`       | `@Disabled` — no operationId contains `"copy"` |
| `testScenario_moveFolderAndGet`       | `@Disabled` — no operationId contains `"move"` |
| `testScenario_editPermissionsAndGet`  | `@Disabled` — no operationId contains `"ermission"` |
| `testScenario_expect403` / two user-context scenarios | `@Disabled` — require multiple credentials |

The `createAndGet` helper, as written by `emitScenarioIfPresent`
(`RandomizedSequenceTester.java:1466`):

```java
/** Integrated: create and get */
private List<APICall> scenarioCreateAndGet() {
    return Arrays.asList(
        new APICall("POST", "/folders",
            "{\"name\": \"mock_name\"}",
            null, Collections.emptyMap(), -1, false),
        new APICall("GET", "/folders/{folderID}",
            null, null,
            Map.of("kbLanguage", "en-US", "$level", "0"), -1, false)
    );
}
```

### 8.5 Bootstrap lines baked into the framework

Because the union of path-param names across the spec is `{folderID}`,
`generateSequenceTestFramework` (`RandomizedSequenceTester.java:440`) injects
exactly one `putIfAbsent` into each of the two state-capture paths:

In `updateState`, after an ID is extracted from the JSON response body:

```java
state.putIfAbsent("id", extractedId);
state.putIfAbsent("folderID", extractedId);   // bodyIdBootstrap
```

In `captureLocationState`, after a successful response's `Location` header
tail is extracted:

```java
state.putIfAbsent("locationResourceId", lastSegment);
state.putIfAbsent("id", lastSegment);
state.putIfAbsent("folderID", lastSegment);   // locationBootstrap
```

This is the piece that makes templated GETs resolvable without writing any
per-endpoint glue: the generator knows *which* state keys paths will ask for
and wires every ID source to populate all of them.

### 8.6 A runtime trace: `testScenario_createAndGet`

Assume the server returns `201 Created` with body `{"id":"f-123"}` (no
`Location` header).

1. **Step 1 — `POST /folders`**
   - `resolveTemplateParams("/folders")` — no `{…}`, returns `/folders`.
   - Framework sends `POST /folders {"name":"mock_name"}`.
   - Response: 201, body `{"id":"f-123"}`.
   - `updateState` (`:690`):
     - `extractIdFromResponse` finds `"id"` → `"f-123"`.
     - `state` becomes `{id=f-123, folderID=f-123, last_response=..., last_status=201}`.
     - POST + extracted ID → appends `DELETE /folders/f-123` to
       `createdResources`.
2. **Step 2 — `GET /folders/{folderID}?kbLanguage=en-US&$level=0`**
   - `resolveTemplateParams("/folders/{folderID}")` calls
     `resolveStateForPathParam("folderID")` (`:560`), finds
     `state["folderID"] = "f-123"` on the first lookup → path resolves to
     `/folders/f-123`.
   - Framework sends `GET /folders/f-123?kbLanguage=en-US&$level=0`.
   - Response: 200 → passes validation (non-5xx, under 5000 ms,
     `expectedStatus=-1` so any non-5xx is accepted).
3. **Cleanup — `@AfterEach tearDown` (`:502`)**
   - Reverses `createdResources` and fires `DELETE /folders/f-123`
     (best-effort; errors swallowed).
   - Closes both JAX-RS clients.

Had the POST omitted `"id"` but returned a `Location: /folders/f-123` header,
`captureLocationState` would have set `state["folderID"] = "f-123"` from the
URL tail instead, and step 2 would still resolve.

### 8.7 A second trace: `testRandomSequence`

Same spec, seeded `RandomSequenceGenerator`. Suppose the random draw yields:

```
[GET /folders/{folderID}, POST /folders, GET /folders/{folderID}]
```

- **Step 1** — `state` is empty; `resolveTemplateParams` finds no match for
  `folderID` and leaves the literal `{folderID}` in place. The GET hits
  `/folders/%7BfolderID%7D` (URL-encoded by JAX-RS), server returns 404.
  Validation *still passes* — 404 is non-5xx and `expectedStatus=-1`.
- **Step 2** — POST succeeds, state now has `folderID=f-123`.
- **Step 3** — same GET, this time resolves to `/folders/f-123` and returns
  200.

This is the intended shape of the random mode: it fuzzes the ordering and
relies on the state machine to recover when a later step happens to supply
what an earlier one needed. The *dependent* and *scenario-biased* modes exist
specifically to avoid the step-1 pothole.

---

## 9. Known limitations

These are worth calling out before you extend the code:

1. **Shallow request bodies.** `buildMockJsonFromSchema` ignores arrays, nested
   objects, polymorphism, formats, and required-field semantics. Expect 4xx
   responses from APIs with non-trivial payload validation.
2. **Dependency inference is one hop deep** and keyed on the *last non-param
   path segment*. Deeply nested resource hierarchies will not be modeled
   correctly.
3. **Stateful strategy is a misnomer** — the eligibility filter does not
   enforce source state. Treat `generateStatefulSequence` as "call into the
   transition table" rather than a state machine walk.
4. **ID extraction is schema-blind.** The fallback key list is fixed; APIs
   that name their identifier differently need a spec-driven override.
5. **Integrated scenarios are hard-coded to folder-like APIs.** They hinge on
   specific `operationId` strings. For other domains they will all be emitted
   disabled, which is correct but means no scenario coverage out of the box.
6. **Concurrent test (`testConcurrentSequences`)** shares the same JAX-RS
   `client` across parallel invocations and mutates the same `state` map.
   Passing assertions don't imply thread safety — this test checks only that
   no exceptions propagate.
7. **No retry, rate limit, or backoff logic** in the framework. A 429 is
   treated like any other non-5xx and passes validation.
