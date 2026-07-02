# Flow DSL — lifecycle integration tests

**Version:** 1.0  
**Status:** Specification (implementation in OSSDK lifecycle module — see [Redesign-OSSDK.md](../Redesign-OSSDK.md))  
**File extension:** `.flow` (plain text; no OS-assigned semantics)

## Overview

The Flow DSL describes **multi-step API lifecycles**: call operations, extract values from responses, bind them to later calls, wait/poll async work, and run **curated negative** body tests via `poison`.

- **Hand-written** by developers or **authored** by a subagent — same syntax.
- **Not** compiled to bytecode. **Parse → validate → interpret** at test run time.
- OSSDK **generates** the harness (`FlowTestHarness`, `OpenApiCatalog`, `run-lifecycle.sh`); **users own** `src/test/flows/*.flow` (never overwritten on regen).

### Execution stack (reuse OSSDK)

```text
.flow file
  → FlowLexer / FlowParser → AST
  → FlowValidator (OpenAPI catalog + variable graph)
  → FlowInterpreter
       → OpenApiCatalog     (from spec; ApiCallExtractor metadata)
       → RequestBodyFactory (valid / poison → withViolation)
       → ApiClient          (RestAssured via TestClient)
       → TestAuth / TestEnv
       → TestContext        (cleanup ids)
```

---

## 1. Lexical rules

| Rule | Detail |
|------|--------|
| **Encoding** | UTF-8 |
| **Comments** | `#` to end of line |
| **Version** | Optional first line: `# flow-dsl 1.0` — interpreter rejects unknown major version |
| **Identifiers** | Variables: `[A-Za-z_][A-Za-z0-9_]*` (unquoted) — e.g. `articleId`, `etag` |
| **Quoted strings** | **Double quotes only** — operationIds, header names, status codes, literals |
| **Indentation** | Sub-clauses under `call` (`path`, `header`, `poison`, `expect`) indented with spaces (2+); parser accepts consistent indent |

---

## 2. Grammar (EBNF summary)

```ebnf
flowFile      ::= [ versionLine ] flowDecl ;
versionLine   ::= "# flow-dsl" NUMBER ;
flowDecl      ::= "flow" STRING NEWLINE
                  [ operationsLine NEWLINE ]
                  { step NEWLINE } ;
operationsLine ::= "operations:" operationList ;
operationList  ::= STRING { "," STRING } ;
step          ::= callStep | extractStep | waitStep | pollStep ;
callStep      ::= "call" STRING { callOption } ;
callOption    ::= pathBind | headerBind | poisonClause | expectClause ;
pathBind      ::= INDENT "path" IDENT "=" IDENT NEWLINE ;
headerBind    ::= INDENT "header" STRING "=" IDENT NEWLINE ;
poisonClause  ::= INDENT "poison" "body" STRING poisonKind NEWLINE ;
poisonKind    ::= "null" | "missing" | "tooLong" | "tooShort" | "wrongType" | "badEnum" | "badPattern"
                  | "value" STRING ;
expectClause  ::= INDENT "expect" "status" STRING NEWLINE ;
extractStep   ::= "extract" IDENT "from" "response" "." ("header" | "body")
                  ( "header" STRING [ "lastSegment" ] | "body" STRING ) NEWLINE ;
waitStep      ::= "wait" "status" STRING NEWLINE ;
pollStep      ::= "poll" IDENT "from" "response.header" STRING
                  "until" "status" STRING [ "timeout" STRING ] NEWLINE ;
```

Full grammar file (implementation): `docs/flow-dsl.ebnf` (to be added with parser).

---

## 3. Flow header

```text
# flow-dsl 1.0
flow "article-crud"
operations: "createArticle", "editArticle", "deleteArticle"
```

| Field | Required | Purpose |
|-------|----------|---------|
| `flow "name"` | Yes | Human-readable id; used in logs and optional `test.include.flows` (future) |
| `operations:` | Recommended | Index for `test.include.operations` filtering; validator warns if mismatch with `call` targets |

If `operations:` is omitted, the harness **infers** operation ids from all `call "…"` lines.

---

## 4. Steps reference

### 4.1 `call`

Invoke an OpenAPI `operationId`. Body defaults to `RequestBodyFactory.forOperation(id).valid()` unless `poison` is set.

```text
call "createArticle"
```

With bindings (indented):

```text
call "editArticle"
  path articleID = articleId
  header "If-Match" = etag
  expect status "200"
```

- **`path <param> = <var>`** — bind OpenAPI path parameter from variable map.
- **`header "<name>" = <var>`** — request header; variable values that were extracted from quoted headers are used as-is (ETag may include quotes from server — interpreter normalizes if needed).

### 4.2 `extract`

```text
extract articleId from response.header "Location" lastSegment
extract etag from response.header "ETag"
extract jobId from response.body "$.id"
```

| Target | Rule |
|--------|------|
| **`Location` + `lastSegment`** | **Always** use last `/`-separated segment of the header value |
| **`ETag` / other headers** | Store header value; literals in later headers use double quotes when declaring names only |
| **`response.body "<jsonPath>"`** | JSONPath (or simple `$.field`) on last response body |

Extract applies to the **immediately preceding** `call` response (or `wait`/`poll` target response — see async).

### 4.3 `expect status`

```text
  expect status "201"
  expect status "2xx"
```

- Per-step assertion on the response from that `call`.
- **Required** when any `poison` clause appears in the flow.
- `2xx` = status in \[200, 300).

### 4.4 `poison` (negative body)

Mutate **one** field in the otherwise **full** valid request body (via `RequestBodyFactory.withViolation`).

```text
call "createArticle"
  poison body "folder.id" null
  expect status "400"
```

```text
call "createArticle"
  poison body "folder.id" value "not-a-valid-id"
  expect status "400"
```

```text
call "createArticle"
  poison body "versions[0].name" tooLong
  expect status "422"
```

| Kind | Maps to factory |
|------|-----------------|
| `null` | `NULL` |
| `missing` | omit field |
| `tooLong` / `tooShort` | length violation |
| `wrongType` | `WRONG_TYPE` |
| `badEnum` / `badPattern` | enum / pattern violation |
| `value "<literal>"` | explicit invalid string/number |

Only **one** `poison` per `call` (v1). Broad field matrices stay in **contract** tests.

### 4.5 `wait`

```text
call "deleteArticle"
  path articleID = articleId
wait status "202"
```

Asserts last response status before continuing (e.g. async delete accepted).

### 4.6 `poll`

```text
poll task from response.header "Location" until status "200" timeout "60s"
```

| Part | Detail |
|------|--------|
| `task` | Variable name storing poll URL (from last response `Location` or absolute URL) |
| `until status "200"` | Success condition |
| `timeout "60s"` | Optional; default from `test.flows.poll.timeout.seconds` in `test-env.properties` |

Poll implementation: generic HTTP GET with `TestAuth`; interval from `test.flows.poll.interval.seconds`.

---

## 5. Variables and chaining

- **Scope:** one flow run — map from variable name to string value.
- **Order:** steps execute top to bottom; **the flow file is the chain** (no graph enumeration).
- **Validator:** every variable used in `path` / `header` must be **defined** by a prior `extract` (or future `let` — not in v1).
- **Cleanup:** on successful create, interpreter may `TestContext.trackCreatedId` when `Location` / body id is extracted (configurable).

---

## 6. Discovery and manifest

**Properties** (`test-env.properties`):

```properties
test.flows.dir=src/test/flows
test.flows.manifest=                          # optional; empty = discover all *.flow in dir
test.flows.poll.timeout.seconds=60
test.flows.poll.interval.seconds=2
```

| Mode | Behavior |
|------|----------|
| **Directory (default)** | Glob `test.flows.dir/**/*.flow` |
| **Manifest** | If `test.flows.manifest` points to a file, list one path per line (relative or absolute); **only** those flows run |

Manifest lines starting with `#` are comments. Generator may emit `flow-manifest.txt.example` but **never** overwrites user manifest or flow files.

---

## 7. `test.include.operations` integration

Read `test.include.operations` from `TestEnv` (comma-separated `operationId`s; empty = all).

For each discovered flow:

1. Compute `ops(flow)` from `operations:` line or all `call "…"` targets.
2. If include is **empty** → run flow.
3. If include is **non-empty** → run flow iff `ops(flow) ∩ include ≠ ∅`.

**Whole flow runs** — not individual steps. If include is `createArticle` and the flow also calls `editArticle` and `deleteArticle`, the **entire** script runs.

Optional future: `test.include.flows=article-crud` for explicit flow names.

---

## 8. Generated harness vs user flows

| Artifact | Regenerated? | Owner |
|----------|--------------|-------|
| `lifecycle/.../FlowTestHarness.java` | Yes | OSSDK |
| `lifecycle/.../FlowDiscovery.java` | Yes | OSSDK |
| `run-lifecycle.sh` | Yes | OSSDK |
| `OpenApiCatalog` / operation index | Yes | OSSDK |
| `src/test/flows/*.flow` | **No** | User / subagent |
| `src/test/flows/*.flow.example` | Once (scaffold) | OSSDK |

### JUnit entry (generated)

```java
@ParameterizedTest
@MethodSource("com.example.api.flow.FlowDiscovery#flowsToRun")
void runFlow(Path flowFile) {
    FlowRunner.run(flowFile, OpenApiCatalog.current());
}
```

`FlowDiscovery.flowsToRun()` applies directory/manifest + `test.include.operations`.

### Optional compile-to-JUnit (later)

CLI may emit one thin test class per flow that calls `FlowRunner.run` — for IDE debugging only; not required for CI.

---

## 9. Validation and errors

### Parse errors

```text
article-crud.flow:12:1: expected 'call' or 'extract', got 'cal'
```

### Semantic errors (pre-run)

| Code | Example |
|------|---------|
| `UNKNOWN_OPERATION` | `call "createArticl"` — not in OpenAPI catalog |
| `UNBOUND_VARIABLE` | `path articleID = articleId` before extract |
| `MISSING_PATH_PARAM` | required path param not bound |
| `POISON_NO_BODY` | `poison` on GET operation |
| `POISON_WITHOUT_EXPECT` | `poison` present but no `expect status` on that call |
| `OPERATIONS_MISMATCH` | warning: `operations:` omits `deleteArticle` |

### Runtime errors

| Situation | Message |
|-----------|---------|
| HTTP failure (positive flow) | `step call createArticle: expected 2xx, got 404` |
| Extract missing header | `extract Location: header not present on createArticle response` |
| Empty lastSegment | `extract Location lastSegment: no path segment in '…'` |
| Poll timeout | `poll: timed out after 60s waiting for status 200` |

**Policy:** fail fast; no silent skip.

---

## 10. Relation to other suites

| Suite | Role |
|-------|------|
| **Contract** | Exhaustive per-operation negatives, params, auth scenarios (generated) |
| **Flow DSL** | Curated lifecycles + targeted `poison` stories |
| **Schemathesis** | Property-based fuzz |
| **NFR** | Latency / availability smoke |

---

## 11. Examples

- [article-crud.flow](./examples/article-crud.flow) — create → extract Location + ETag → edit → delete
- [article-create-bad-folder.flow](./examples/article-create-bad-folder.flow) — `poison` negative
- [folder-delete-async.flow](./examples/folder-delete-async.flow) — `wait` / `poll`

---

## 12. Subagent appendix (non-normative)

When generating flows:

1. Read this spec and copy examples.
2. Write files only under `test.flows.dir`.
3. Always include `operations:` for filtering.
4. Use double quotes for operation ids and header names.
5. Use `lastSegment` for `Location` extracts.
6. Pair every `poison` with `expect status`.
7. Do not overwrite existing `.flow` files unless asked.

---

*See [Redesign-OSSDK.md](../Redesign-OSSDK.md) Integration section for architectural context.*
