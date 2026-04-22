# Randomized Sequence Testing (RST)

This document describes how randomized sequence testing is implemented in OAS SDK
Java. It is a *meta-test-generator*: given an OpenAPI spec, it emits a
self-contained JUnit 5 + JAX-RS harness that, at runtime, executes randomized
(and optionally scenario-biased) sequences of calls against a live API under
test.

Primary source: `src/main/java/egain/oassdk/test/sequence/RandomizedSequenceTester.java`
Tests: `src/test/java/egain/oassdk/test/sequence/RandomizedSequenceTesterTest.java`

---

## 0. Read this first — the idea in 60 seconds

**What problem this solves.** You have an OpenAPI spec describing, say, a
Folder API. Writing end-to-end integration tests by hand is tedious:
*"create a folder, then read it back, then delete it, then try to read the
deleted one."* RST does it for you by reading the spec.

**What you give it.** One thing: your OpenAPI spec and a base URL.

```java
new RandomizedSequenceTester()
    .generateSequenceTests(spec, "generated/sequence",
                           "https://api.example.com/v1");
```

**What it gives back.** A folder of five Java files — a compile-and-run JUnit
project. You don't write a line of test code.

**How "sequence" works — the one idea to remember.** A *sequence* is just a
list of API calls run in order. The framework carries a shared `state` map
across the steps. Whenever a response contains an `id` (or a `Location`
header), it's stashed in `state`. When a later step has a path like
`/folders/{folderID}`, the framework fills in `{folderID}` from `state`
before sending the request.

That one trick — **save the id, reuse it** — is what makes a random pile of
calls behave like a realistic workflow. Without it, a GET on
`/folders/{folderID}` could never work because the ID doesn't exist yet.

**How it decides what to call.** Two modes, both driven by the same list of
endpoints extracted from the spec:

1. **Random** — pick a random endpoint, N times. Rough, fuzz-style.
2. **Scenario-biased** — 45% of the time splice in a *known-good* little
   recipe like `[POST /folders, GET /folders/{id}]` instead of one random
   call. Those recipes are generated from the spec by matching `operationId`s
   like `createFolder` + `getFolder`.

**A concrete trace — `create then get`.**

| Step | Call sent | What happens in `state` | Why it works |
| --- | --- | --- | --- |
| 1 | `POST /folders` body `{"name":"mock_name"}` | Server returns `{"id":"f-123"}`. Framework stores `state = {id: f-123, folderID: f-123}`. | Body mock is auto-generated from the `FolderCreate` schema. The `folderID` key is populated because the spec has a path `/folders/{folderID}` somewhere. |
| 2 | `GET /folders/f-123?kbLanguage=en-US&$level=0` | Server returns 200. | The path template `/folders/{folderID}` was resolved from `state` *before* the call was sent. `kbLanguage` comes from the enum's first value; `$level` from the schema's `minimum`. |
| 3 | *(teardown)* `DELETE /folders/f-123` | cleared | Any POST that returned an id is auto-registered for deletion after the test. |

**Where to go next.**
- **Want to see the actual generated Java for this example?** → §8 (worked example, with real snippets).
- **Want to know what sources of data the generator reads from the spec?** → §2.
- **Want to know what the generated framework does at runtime?** → §4.
- **Curious about the five sequence-building strategies (random / weighted / dependent / stateful / scenario-biased)?** → §3.
- **"How does it know which random sequences are valid?"** → §9 (short answer: it doesn't, and that's the point).

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

## 8. Method-by-method walkthrough

This section takes **one YAML**, feeds it into
`RandomizedSequenceTester`, and walks through **every method on the class in
the order it runs**, showing for each one: what goes in, what comes out, and
why. If you only read one section, read this one.

### 8.1 The input YAML we'll trace

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

### 8.2 The caller

```java
Map<String, Object> spec = new OASParser().parse(Path.of("folders.yaml"));
new RandomizedSequenceTester()
    .generateSequenceTests(spec, "generated/sequence",
                           "https://api.example.com/v1");
```

One call in. Five `.java` files on disk as output. Everything below happens
inside that one call.

---

### 8.3 `generateSequenceTests(spec, outputDir, baseUrl)` — the entry point

Signature: `public void generateSequenceTests(Map<String,Object>, String, String)` (`:34`).

**Input:**
- `spec` — the parsed OpenAPI map.
- `outputDir = "generated/sequence"`.
- `baseUrl = "https://api.example.com/v1"`.

**What it does:** orchestrates six steps, in this exact order:

```java
Files.createDirectories(outputDir);                          // 1. mkdir
List<APICallInfo> apiCalls = extractAPICallsFromSpec(spec);  // 2. read spec
generateSequenceTestFramework(spec, outputDir, baseUrl, apiCalls);   // 3. file 1
generateRandomSequenceGenerator(spec, outputDir, baseUrl);           // 4. file 2
generateSequenceTestCases(spec, outputDir, baseUrl, apiCalls);       // 5. file 3
generateSequenceTestRunner(spec, outputDir, baseUrl);                // 6. file 4
generateSequenceTestConfig(spec, outputDir, baseUrl, apiCalls);      // 7. file 5
```

**Output:** five files written. No return value.

We now walk step by step.

---

### 8.4 `extractAPICallsFromSpec(spec)` — turn the spec into a flat list

Signature: `private List<APICallInfo> extractAPICallsFromSpec(Map<String,Object>)` (`:64`).

**Input:** the spec map (shown above in 8.1).

**What it does:** walks `spec.paths`, pairs each path with each of its HTTP
verbs, and builds one `APICallInfo` per pair. To fill the fields it calls
three helpers: `extractResourceName`, `extractPathParamNames`,
`buildDefaultQueryParams`.

**Output:** `List<APICallInfo>` of length 2 for our spec.

```
apiCalls[0] = APICallInfo {
  method             = "POST"
  path               = "/folders"
  operationId        = "createFolder"
  hasPathParams      = false                  (path has no '{')
  hasRequestBody     = true                   (operation has "requestBody")
  resourceName       = "folders"              ← from extractResourceName
  pathParamNames     = []                     ← from extractPathParamNames
  defaultQueryParams = {}                     ← from buildDefaultQueryParams
  operation          = <raw POST map>
}

apiCalls[1] = APICallInfo {
  method             = "GET"
  path               = "/folders/{folderID}"
  operationId        = "getFolder"
  hasPathParams      = true
  hasRequestBody     = false
  resourceName       = "folders"
  pathParamNames     = ["folderID"]
  defaultQueryParams = {"kbLanguage": "en-US", "$level": "0"}
  operation          = <raw GET map>
}
```

The three helpers below are all called from here.

---

### 8.5 `extractResourceName(path)`

Signature: `private String extractResourceName(String)` (`:102`).

**Input:** one path string.

**What it does:** walks path segments right-to-left, returns the first
non-empty, non-`{...}` segment. Fallback: `"resource"`.

**Outputs:**
- `"/folders"`             → `"folders"`
- `"/folders/{folderID}"`  → `"folders"` (skips `{folderID}`, finds `folders`)

---

### 8.6 `extractPathParamNames(path)`

Signature: `private List<String> extractPathParamNames(String)` (`:113`).

**Input:** one path string.

**What it does:** matches the regex `\{([^}]+)}` and collects group 1.

**Outputs:**
- `"/folders"`             → `[]`
- `"/folders/{folderID}"`  → `["folderID"]`

---

### 8.7 `buildDefaultQueryParams(operation, spec)`

Signature: `private Map<String,String> buildDefaultQueryParams(Map,Map)` (`:122`).

**Input:** one operation map + the spec (needed for `$ref` resolution in
`listOperationParameters`).

**What it does:**
1. Gets the operation's parameters via `listOperationParameters` (resolves
   `$ref`s).
2. Keeps only `in: query` params.
3. For each, calls `pickExampleForQueryParam` on its schema to get a default
   value.
4. If the param is `required` and no default could be found, uses `"1"` so
   the request still has *something*.

**Outputs for our spec:**
- `POST /folders`           → `{}` (no parameters)
- `GET /folders/{folderID}` → `{"kbLanguage": "en-US", "$level": "0"}`

The two helpers that back this one are next.

---

### 8.8 `listOperationParameters(operation, spec)`

Signature: `private List<Map<String,Object>> listOperationParameters(Map,Map)` (`:146`).

**Input:** operation map + spec.

**What it does:** returns `operation.parameters` as a list of maps. If an
entry is a `$ref`, substitutes the resolved parameter in its place (via
`resolveParameterRef`).

**Output for our GET:** two maps — one for `kbLanguage`, one for `$level`.
Neither uses `$ref`, so no resolution happens here.

---

### 8.9 `resolveParameterRef(ref, spec)`

Signature: `private Map<String,Object> resolveParameterRef(String,Map)` (`:169`).

**Input:** a string like `"#/components/parameters/Foo"`.

**What it does:** only handles refs into `components.parameters`. Looks up
the referenced parameter map; returns `null` if anything is missing.

**Output for our spec:** not called (no parameter `$ref`s).

---

### 8.10 `pickExampleForQueryParam(schema)`

Signature: `private String pickExampleForQueryParam(Map<String,Object>)` (`:185`).

**Input:** one parameter's `schema` map.

**Decision order (stops at first hit):**
1. `schema.example`     (string/number/boolean)
2. `schema.default`     (string/number/boolean)
3. First entry of `schema.enum`
4. Typed fallback: integer/number → `"<minimum>"` if set else `"0"`;
   boolean → `"true"`; string → `"test"`; else `null`.

**Outputs for our spec:**
- `{type:"string", enum:["en-US","fr-FR"]}` → `"en-US"` (rule 3)
- `{type:"integer", minimum:0}`              → `"0"` (rule 4, `minimum`)

---

At this point `apiCalls` is fully populated. The remaining steps each write
one file, using `apiCalls` and helper methods below.

---

### 8.11 `generateSequenceTestFramework(spec, outputDir, baseUrl, apiCalls)`

Signature: `private void generateSequenceTestFramework(Map,String,String,List<APICallInfo>)` (`:440`).

**Input:** the four shown.

**What it does:**
1. Unions `pathParamNames` across every `APICallInfo` → `{"folderID"}` in
   our case.
2. Builds two strings, one line per path-param name:
   - `bodyIdBootstrap`  = `state.putIfAbsent("folderID", extractedId);\n`
   - `locationBootstrap`= `state.putIfAbsent("folderID", lastSegment);\n`
3. Interpolates four values into a **fixed text-block template**:
   `baseUrl`, `locationBootstrap`, `bodyIdBootstrap`, and
   `Constants.DEFAULT_MAX_RESPONSE_TIME_MS` (5000).

**Output:** writes `generated/sequence/SequenceTestFramework.java` —
a generic JAX-RS harness. Nothing else about your spec influences this file.
The two bootstrap lines are the *only* spec-derived customization.

The framework's bodies of interest (what those bootstrap lines land inside):

```java
// inside captureLocationState(...)
state.putIfAbsent("locationResourceId", lastSegment);
state.putIfAbsent("id", lastSegment);
state.putIfAbsent("folderID", lastSegment);        // ← locationBootstrap

// inside updateState(...) when extractedId != null
state.putIfAbsent("id", extractedId);
state.putIfAbsent("folderID", extractedId);        // ← bodyIdBootstrap
```

That is how the framework knows which `state` keys future path templates
will ask for: the generator seeds every one of them at response time.

---

### 8.12 `generateRandomSequenceGenerator(spec, outputDir, baseUrl)`

Signature: `private void generateRandomSequenceGenerator(Map,String,String)` (`:849`).

**Input:** three args, but only `outputDir` is used.

**What it does:** writes a **verbatim** Java source file — no interpolation
at all.

**Output:** `generated/sequence/RandomSequenceGenerator.java`. Identical
bytes for every spec. Contains the five sequence-building strategies:
`generateRandomSequence`, `generateWeightedSequence`,
`generateDependentSequence`, `generateStatefulSequence`,
`generateScenarioBiasedSequence`.

---

### 8.13 `generateSequenceTestCases(spec, outputDir, baseUrl, apiCalls)`

Signature: `private void generateSequenceTestCases(Map,String,String,List<APICallInfo>)` (`:1035`).

This is the **most spec-specific file**. It builds five chunks of source
code, then stitches them into a text-block template.

**Chunk A — `initBody`** (calls `appendAPICallConstructor` once per call):

```java
calls.add(new APICall("POST", "/folders",
    "{\"name\": \"mock_name\"}",
    null, Collections.emptyMap(), -1, false));
calls.add(new APICall("GET", "/folders/{folderID}",
    null, null,
    Map.of("kbLanguage", "en-US", "$level", "0"), -1, false));
```

**Chunk B — `integrated`** (from `buildIntegratedScenarioCodegen`, §8.18).
This becomes the scenario helper methods + `@Test` methods, plus a list
expression injected into `setUp()`.

**Chunk C — `depsBody`** (from `inferDependencies`, §8.22):

```java
dependencies.put("/folders:POST",             Arrays.asList());
dependencies.put("/folders/{folderID}:GET",   Arrays.asList("/folders:POST"));
```

**Chunk D — `stateBody`** (built inline):

```java
stateTransitions.put("/folders:POST",            "folders_post");
stateTransitions.put("/folders/{folderID}:GET",  "folders_get");
```

**Chunk E — `weightsBody`** (GET weighted higher than write methods):

```java
weights.put("/folders:POST",           0.15);
weights.put("/folders/{folderID}:GET", 0.3);
```

**Output:** writes `generated/sequence/SequenceTestCases.java` — the JUnit
test class extending `SequenceTestFramework`, containing `testRandomSequence`,
`testWeightedSequence`, `testDependentSequence`, `testStatefulSequence`,
`testConcurrentSequences`, `testSequencePerformance`,
`testScenarioBiasedSequence`, and the scenario-specific `testScenario_*`
methods.

The helper methods called from here are up next.

---

### 8.14 `appendAPICallConstructor(sb, indent, type, call, spec, queryOverride, expectedStatus, noAuth)`

Signature: `private void appendAPICallConstructor(...)` (`:263`).

**Input:** a `StringBuilder` + all the metadata needed to emit one
`new APICall(...)` expression. `queryOverride` (nullable) is merged on top of
`call.defaultQueryParams`.

**What it does:** appends to `sb` the literal Java text
`new <type>("<method>", "<path>", <body>, null, <queryMap>, <expectedStatus>, <noAuth>)`.
Body is either `null` (no requestBody) or the result of
`buildRequestBodyForOperation` wrapped in `"..."`.

**Output for call[0]:**

```
new APICall("POST", "/folders", "{\"name\": \"mock_name\"}", null, Collections.emptyMap(), -1, false)
```

`expectedStatus = -1` means "any non-5xx is acceptable".

---

### 8.15 `buildRequestBodyForOperation(operation, spec)`

Signature: `private String buildRequestBodyForOperation(Map,Map)` (`:314`).

**Input:** one operation map + spec.

**What it does:**
1. Finds `operation.requestBody.content["application/json"].schema` (or falls
   back to the first content type).
2. If the schema is `{$ref: "#/components/schemas/..."}`, resolves it against
   `spec.components.schemas`.
3. Delegates to `buildMockJsonFromSchema`.

**Output for our POST:** `"{\"name\": \"mock_name\"}"` (the `\"` escapes are
already in place because this string is about to be dropped inside a Java
string literal in the emitted file).

**Output for our GET:** not called — no `requestBody` on the operation.

---

### 8.16 `buildMockJsonFromSchema(schema, spec, depth)`

Signature: `private String buildMockJsonFromSchema(Map,Map,int)` (`:357`).

**Input:** an object schema + spec + current depth.

**What it does:** iterates over `schema.properties` and emits one JSON entry
per property, chosen by `type`:

| `type` | emitted value |
| --- | --- |
| `string` (and default) | `"mock_<fieldname>"` |
| `integer`, `number`    | `1` |
| `boolean`              | `true` |
| anything else          | `"mock_value"` |

Depth cap at 5 → returns `"{}"`. **Nested objects, arrays, `allOf`/`oneOf`,
formats, and `required` are ignored** (see §10 limitations).

**Output for `FolderCreate`** (one string property `name`):

```
{\"name\": \"mock_name\"}
```

---

### 8.17 `findByOperationId` / `findByOperationIdContains`

Signatures: `:290` and `:302`.

**Inputs:** `apiCalls` + a string.

**What they do:** linear scan. `findByOperationId` does an **exact match**;
`findByOperationIdContains` does a **case-insensitive substring match**.

**Outputs for our spec:**
- `findByOperationId("createFolder")`       → call[0]
- `findByOperationId("getFolder")`          → call[1]
- `findByOperationId("editFolder")`         → `null`
- `findByOperationId("deleteFolder")`       → `null`
- `findByOperationIdContains("copy")`       → `null`
- `findByOperationIdContains("move")`       → `null`
- `findByOperationIdContains("ermission")`  → `null`

---

### 8.18 `buildIntegratedScenarioCodegen(spec, apiCalls)`

Signature: `private IntegratedScenarioCodegen buildIntegratedScenarioCodegen(Map,List<APICallInfo>)` (`:1325`).

This is where the *scenario-biased* magic lives.

**Input:** spec + apiCalls.

**What it does:** looks up specific, folder-centric operations by name (see
§8.17). For each hard-coded scenario in its catalog, it asks: *"are all the
operations this scenario needs present in the spec?"*

- All present → **emit** via `emitScenarioIfPresent`: a
  `scenarioFoo()` helper returning the call list, plus `@Test testScenario_foo`.
- Missing → **disable** via `emitDisabledScenario`: a `@Disabled` stub with
  a human-readable reason.

**For our spec, the catalog resolves as:**

| Scenario (method suffix)     | Ops required                          | Verdict for our spec |
| ---                          | ---                                   | ---                  |
| `createAndGet`               | createFolder, getFolder               | **emit**             |
| `editAndGet`                 | createFolder, editFolder, getFolder   | `@Disabled` (no edit)|
| `deleteAndGet`               | create, delete, getFolder             | `@Disabled` (no del) |
| `editPermissionsAndGet`      | create, op containing "ermission", get| `@Disabled`          |
| `copyFolderAndGet`           | create, op containing "copy", get     | `@Disabled`          |
| `moveFolderAndGet`           | create, op containing "move", get     | `@Disabled`          |
| `queryLangPositive`          | createFolder, getFolder               | **emit** (override from `pickQueryParamsMatching(get, "lang")`) |
| `queryLevelPositive`         | createFolder, getFolder               | **emit**             |
| `querySortOrderPositive`     | createFolder, getFolder               | emit w/ fallback `{$sort:name, $order:asc}` |
| `queryPaginationPositive`    | createFolder, getFolder               | emit w/ fallback `{$pagenum:1, $pagesize:10}` |
| `expect401`                  | createFolder, getFolder               | **emit** (`noAuth=true`, `expectedStatus=401` on GET) |
| `expect403`                  | —                                     | always `@Disabled`   |
| `getUsingDifferentUser`      | —                                     | always `@Disabled`   |
| `editUsingDifferentUserThanCreateAndGet` | —                         | always `@Disabled`   |

**Output:** an `IntegratedScenarioCodegen` record with three fields:

```
helperMethods          = <concatenated scenario*() helpers as Java source>
testMethods            = <concatenated @Test testScenario_*() methods>
scenarioTemplateListExpr = "Arrays.asList(scenarioCreateAndGet(), scenarioQueryLangPositive(), ...)"
```

`scenarioTemplateListExpr` is dropped into `SequenceTestCases.setUp()` as
the second argument to `new RandomSequenceGenerator(...)`, which is what
lets `generateScenarioBiasedSequence` splice whole recipes into a random
walk.

The emitted `scenarioCreateAndGet()` helper looks like:

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

---

### 8.19 `emitScenarioIfPresent(helpers, tests, templateRefs, spec, suffix, comment, calls, queryOverrides, expectedStatuses, noAuthFlags)`

Signature at `:1466`.

**Input:** two `StringBuilder`s, a list for registering scenario names, the
spec, and — for the scenario being emitted — its name, a human comment, the
ordered `APICallInfo` list, and per-step override lists.

**What it does:**
1. If any required op is `null`, falls through to `emitDisabledScenario` and
   returns.
2. Pads the override lists to match `calls.size()`.
3. Appends a `/** comment */ private List<APICall> scenarioFoo() { return
   Arrays.asList(...); }` helper.
4. Appends a one-line `@Test void testScenario_foo() { assertTrue(...); }`.
5. Registers the name in `templateRefs`.

**Output:** no return; `helpers`, `tests`, `templateRefs` are all mutated.

---

### 8.20 `emitDisabledScenario(tests, suffix, reason)`

Signature at `:1458`.

**Input:** a `StringBuilder`, a suffix, a reason string.

**What it does:** appends

```java
@org.junit.jupiter.api.Disabled("<reason>")
@Test
void testScenario_<suffix>() {
}
```

**Output:** the stub compiles and reports as "skipped" in JUnit output. This
is why the generated test class is always complete — every scenario in the
catalog produces *some* method, even on specs that don't match the
folder-API shape.

---

### 8.21 `pickQueryParamsMatching(getOp, needle)`

Signature at `:1444`.

**Input:** one `APICallInfo` + a substring.

**What it does:** returns the entries of `getOp.defaultQueryParams` whose key
contains `needle` (case-insensitive).

**Output examples:**
- `pickQueryParamsMatching(getFolder, "lang")`  → `{"kbLanguage": "en-US"}`
  (because `"kblanguage".contains("lang")`).
- `pickQueryParamsMatching(getFolder, "level")` → `{"$level": "0"}`.
- `pickQueryParamsMatching(getFolder, "sort")`  → `{}` (nothing matches).

The empty case triggers the fallback hard-coded in §8.18
(`{$sort:name, $order:asc}`).

---

### 8.22 `inferDependencies(apiCalls)`

Signature at `:404`.

**Input:** the flat `apiCalls` list.

**What it does:**
1. Passes 1 — for every POST **without** path params, record it as the
   "creation endpoint" for its `resourceName`. Give it no deps.
2. Pass 2 — for every call **with** path params, make it depend on that
   resource's creation endpoint (if one exists); otherwise no deps.
3. Everything else — no deps.

Keys are `"<path>:<METHOD>"`.

**Output for our spec:**

```
"/folders:POST"            → []
"/folders/{folderID}:GET"  → ["/folders:POST"]
```

This feeds `testDependentSequence` (§8.13 Chunk C).

---

### 8.23 `generateSequenceTestRunner(spec, outputDir, baseUrl)`

Signature at `:1232`.

**Input:** three args; only `outputDir` is used.

**What it does:** writes a verbatim `main()` class — no interpolation.

**Output:** `generated/sequence/SequenceTestRunner.java`. Runs JUnit Platform
against `SequenceTestCases.class` and prints a summary. Identical for every
spec.

---

### 8.24 `generateSequenceTestConfig(spec, outputDir, baseUrl, apiCalls)`

Signature at `:1266`.

**Input:** same as §8.13.

**What it does:** builds the same `initBody` chunk as §8.13 Chunk A and
drops it into a tiny factory-class template.

**Output:** `generated/sequence/SequenceTestConfig.java`. Exposes
`createRandomSequenceGenerator()` for anyone who wants to reuse the extracted
`availableCalls` list outside the generated JUnit test.

---

### 8.25 Two string-emission helpers

These two are called all over the place.

**`escapeJavaStringLiteral(s)`** (`:225`): replaces `\` with `\\` and `"` with
`\"`. Used whenever a spec-derived string is going into a generated Java
string literal. Example: a path containing `"` would otherwise break the
generated source.

**`toJavaMapExpression(map)`** (`:235`):
- `null` or empty map → `"Collections.emptyMap()"`
- size ≤ 5 → `"Map.of(\"k1\", \"v1\", \"k2\", \"v2\")"`
- size > 5 → `new LinkedHashMap<>() {{ put(\"k1\", \"v1\"); ... }}`
  (preserves insertion order — matches parameter order from the spec).

---

### 8.26 What ends up on disk

```
generated/sequence/
├── SequenceTestFramework.java    (§8.11 — bootstrap-customized JAX-RS harness)
├── RandomSequenceGenerator.java  (§8.12 — verbatim, spec-agnostic)
├── SequenceTestCases.java        (§8.13 — the spec-driven JUnit class)
├── SequenceTestRunner.java       (§8.23 — verbatim main())
└── SequenceTestConfig.java       (§8.24 — factory for availableCalls)
```

Compile those five files in a JUnit-5 + Jersey project, point them at a live
server, and you have a working test suite — **no per-endpoint test code was
written by hand**.

---

### 8.27 What those generated files actually do at runtime

So far everything has been about generation. For completeness, here is what
happens when you run the emitted tests against a live server.

#### Trace A — `testScenario_createAndGet`

Assume the server answers `POST /folders` with `201 Created` + body
`{"id":"f-123"}` (no `Location` header).

1. **Step 1 — `POST /folders`**
   - `resolveTemplateParams("/folders")` — no `{…}` → `/folders`.
   - Sends `POST /folders {"name":"mock_name"}`.
   - Response: 201, body `{"id":"f-123"}`.
   - `updateState` (`:690`):
     - `extractIdFromResponse` finds `"id"` → `"f-123"`.
     - `state` becomes `{id=f-123, folderID=f-123, last_response=..., last_status=201}`.
       (The `folderID=` line is the one the generator baked in — see §8.11.)
     - POST + extracted id → appends `DELETE /folders/f-123` to
       `createdResources` for teardown.
2. **Step 2 — `GET /folders/{folderID}?kbLanguage=en-US&$level=0`**
   - `resolveTemplateParams("/folders/{folderID}")` calls
     `resolveStateForPathParam("folderID")` (`:560`), finds
     `state["folderID"] = "f-123"` → path resolves to `/folders/f-123`.
   - Sends `GET /folders/f-123?kbLanguage=en-US&$level=0`.
   - Response 200 → passes validation (non-5xx, under 5 s, `expectedStatus=-1`).
3. **Teardown — `@AfterEach`** (`:502`)
   - Reverses `createdResources`, fires `DELETE /folders/f-123` (best effort).
   - Closes both JAX-RS clients.

Had the POST omitted `"id"` but returned `Location: /folders/f-123` instead,
`captureLocationState` would have set `state["folderID"] = "f-123"` from the
URL tail, and Step 2 would still resolve.

#### Trace B — `testRandomSequence`

Same spec, but let's say the random draw yields

```
[GET /folders/{folderID}, POST /folders, GET /folders/{folderID}]
```

- **Step 1** — `state` is empty. `resolveTemplateParams` finds no match for
  `folderID` and leaves the literal `{folderID}` in place. The GET hits
  `/folders/%7BfolderID%7D`, server returns 404. Validation *still passes* —
  404 is non-5xx and `expectedStatus = -1`.
- **Step 2** — POST succeeds, `state["folderID"] = "f-123"`.
- **Step 3** — same GET, resolves to `/folders/f-123`, returns 200.

This is the intended shape of random mode: fuzz the ordering and rely on the
state machine to recover when a later step happens to supply what an earlier
one needed. The **dependent** and **scenario-biased** modes exist
specifically to avoid the Step-1 pothole.

---

## 9. How it decides what's "valid"

A reasonable question to ask about a *random* sequence generator is: *the
spec defines combinations that make sense (POST then GET by id) and
combinations that don't (GET by id with nothing created yet). How does RST
tell them apart?*

**Short answer: it doesn't, and that's by design.** The random/weighted
modes generate freely; the gate is permissive; three separate mechanisms
steer toward coherent workflows without ever labeling anything "invalid".
This section is the whole story.

### 9.1 What *"validity"* could mean

"Valid" is overloaded. Distinguish three senses:

1. **At generation time** — will the generator refuse to emit this step?
2. **At runtime, at the gate** — will `validateSequenceResults` mark this
   result as a failure?
3. **At runtime, in the request** — will the *server* accept the call?

RST leans almost entirely on sense 3. Senses 1 and 2 are deliberately loose.

### 9.2 Sense 1: does the generator skip it?

Of the five strategies in `RandomSequenceGenerator`, only one actually
prefilters for prerequisites:

| Strategy                         | Skips steps whose preconditions aren't met? |
| ---                              | --- |
| `generateRandomSequence`         | No — uniform draw |
| `generateWeightedSequence`       | No — weighted draw |
| `generateStatefulSequence`       | No — the "eligibility" filter admits any call named in the transitions map, regardless of `currentState`; it's cosmetic (see §3) |
| `generateDependentSequence`      | **Yes** |
| `generateScenarioBiasedSequence` | **Partial** — splices in hand-written recipes (§5) |

The dependency-gated logic, from the emitted `RandomSequenceGenerator.java`:

```java
eligible = availableCalls.filter(call ->
    deps == null || deps.stream().allMatch(executedCalls::contains));
```

That `executedCalls` set is local to *this* sequence; it's not a global
"has this ever been called" — so dependencies must be satisfied *earlier in
the same sequence*.

Where does the deps map come from? `inferDependencies` (§8.22) — and it
encodes exactly one rule:

> **"Any call with a `{…}` in its path depends on the `POST` (no path
> params) for the same `resourceName`."**

Everything else has no deps. `GET /folders/{id}/contents/{cid}` still gets
only one dep (the POST for `folders`), not a chain. So even the "dependent"
mode can emit calls whose *deep* prerequisites aren't met — it only enforces
the first hop.

### 9.3 Sense 2: does the gate fail the sequence?

The gate is `SequenceTestFramework.validateSequenceResults` (`:757`):

```java
if (!result.isSuccess())                              return false;  // transport crash
if (result.getResponseTime() > 5000)                  return false;  // timeout
if (status >= 500)                                    return false;  // server error
if (call.getExpectedStatus() >= 0
        && status != call.getExpectedStatus())        return false;  // mismatch, only if set
// everything else passes
```

Translated: the only *failure* outcomes are

- a transport exception (connection refused, SSL error, …),
- a response that took longer than 5 seconds,
- a 5xx status,
- a declared expected status that didn't match.

**4xx is a passing response.** A 404 from a GET-before-POST, a 400 from a
missing field in the auto-generated body, a 409 from a duplicate create —
all count as the sequence behaving *correctly*.

And `expectedStatus` is `-1` ("any non-5xx") for every emitted call *except*
the handful of scenario templates that set it deliberately — today only
`expect401` does (§8.18). So in practice nearly all steps accept anything
that isn't a crash.

### 9.4 Sense 3: the state-propagation trick

When a random draw *happens to* produce `[POST /folders, GET /folders/{folderID}, …]`,
the framework rescues it (§4, §8.11):

1. Watches the POST's response body and `Location` header.
2. Seeds `state` with `id=f-123` **and** `folderID=f-123` (the generator
   baked in one `putIfAbsent` line per path-param name it saw anywhere in the
   spec).
3. Before sending the GET, `resolveTemplateParams` substitutes `{folderID}`
   with `f-123`.

The call that *looked* invalid on paper becomes a valid workflow the
moment the right predecessor fires. When it doesn't — GET draws before any
POST — the template stays unresolved, JAX-RS URL-encodes the braces into
`%7BfolderID%7D`, and the server responds 404, which still passes the gate.

### 9.5 The three lines of defense, summarized

| Mechanism | When it runs | What it buys you |
| --- | --- | --- |
| `inferDependencies` + `generateDependentSequence` | Generation, dependent mode only | No path-templated call emits until its resource's POST has already emitted *in this sequence* |
| Scenario recipes (`scenarioCreateAndGet`, …)      | Generation, scenario-biased mode | Known-good orderings spliced in whole |
| `state` map + `resolveTemplateParams`             | Runtime, every mode              | If a POST happened to run first, later templated calls resolve correctly |

The pure random and weighted modes have **none** of the first two. They
deliberately rely on #3, and on the server tolerating nonsense with 4xx.

### 9.6 What this means in practice

- **Strength.** RST catches crashes, hangs, and explicit-expectation
  mismatches against unpredictable orderings. That's the fuzz-test value.
- **Weakness.** A random sequence in which every single call returns 404
  still passes. The tests can't tell the difference between *"the server
  coherently refused a nonsense request"* and *"the server silently
  accepted my garbage and did nothing"*. If you need stronger invariants,
  use scenario templates with `expectedStatus` set — that is the lever.
- **Rule of thumb.** Treat `testRandomSequence` and `testWeightedSequence`
  as *robustness* tests, `testDependentSequence` as a prerequisite-graph
  test, and the `testScenario_*` methods as the actual business-workflow
  assertions. They are doing different jobs under one umbrella.

---

## 10. Known limitations

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
