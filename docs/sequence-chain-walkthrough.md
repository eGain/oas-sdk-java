# Sequence Chain Walkthrough — A Worked Example

A concrete end-to-end example of how
[`SequenceChainTestGenerator`](sequence-chain-tests.md) turns an
OpenAPI spec into a pytest bundle. If you want the reference docs, read
[`sequence-chain-tests.md`](sequence-chain-tests.md) first; this page
just walks a single spec through the pipeline so the shape of the
output is obvious.

---

## The spec

Three POSTs (two top-level creators + one sub-resource creator), three
GETs, and one DELETE:

```yaml
openapi: 3.0.0
info: { title: demo, version: 1.0.0 }
paths:
  /users:
    post: { operationId: createUser }
  /users/{userId}:
    get:  { operationId: getUser }
  /orders:
    post: { operationId: createOrder }
  /orders/{orderId}:
    get:  { operationId: getOrder }
  /orders/{orderId}/items:
    post: { operationId: addItem }
  /orders/{orderId}/items/{itemId}:
    get:    { operationId: getItem }
    delete: { operationId: deleteItem }
```

The interesting one is `POST /orders/{orderId}/items` — a POST with a
path parameter. Before the every-POST-seeds-a-family rewrite this
operation was silently dropped from all generated chains.

---

## Flow diagram

```
                  +----------------------------------+
                  |  ApiCallExtractor.extract(spec)  |
                  |  Flattens paths x methods into   |
                  |  ApiCallInfo records             |
                  +---------------+------------------+
                                  |  7 ops
                                  v
                  +----------------------------------+
                  |  ChainEnumerator.enumerate       |
                  |  For every POST in the list:     |
                  +---------------+------------------+
                                  v
      +-----------------------------------------------------+
      |        SEED POST (one family per iteration)         |
      +---------+------------------+------------------------+
                |                  |
                v                  v
    +----------------------+  +---------------------------+
    | buildPrefix(seed)    |  | buildTailPool(seed)       |
    | For each path-param: |  | non-POST consumers where: |
    |  findProducerForParam|  |  - path startsWith seed   |
    |  (longest-prefix ->  |  |  - all params bound by    |
    |   name-stem fallback)|  |    (prefix + seed + next) |
    | Recurse on producer. |  |                           |
    +---------+------------+  +--------------+------------+
              |                              |
              |  prefix[] (may be empty)     |  tail pool
              v                              v
       +------------------------------------------+
       |  permutations of tail up to              |
       |  (maxChainLength - prefix.size - 1)      |
       |  applying deleteLastOnly / allowRepeats  |
       +----------------+-------------------------+
                        |
                        v  compose [prefix..., seed, tail...]
                +----------------+
                | EnumeratedChain|  <- dedup by method+path signature
                |  seedPost      |
                |  steps         |
                |  unresolved    |
                +--------+-------+
                         | List<EnumeratedChain>
                         v
            +------------------------------------+
            |  SequenceChainTestGenerator        |
            |  groupChainsByResource keyed on    |
            |  seedPost.resourceName()           |
            |                                    |
            |  Per-param id vars in emitted      |
            |  pytest: order_id, item_id, ...    |
            +------------------------------------+
                         |
                         v
              test_chain_<resource>.py files
```

---

## Extraction — 7 `ApiCallInfo` records

Default config used throughout (`maxChainLength=4`, `deleteLastOnly=true`,
`allowRepeats=false`, `unresolvedParamPolicy=SKIP`).

| Method | Path | resourceName | isCreator | isSubResourceCreator |
| --- | --- | --- | --- | --- |
| POST   | `/users`                                 | users  | true  | false |
| GET    | `/users/{userId}`                        | users  | false | false |
| POST   | `/orders`                                | orders | true  | false |
| GET    | `/orders/{orderId}`                      | orders | false | false |
| POST   | `/orders/{orderId}/items`                | items  | false | true  |
| GET    | `/orders/{orderId}/items/{itemId}`       | items  | false | false |
| DELETE | `/orders/{orderId}/items/{itemId}`       | items  | false | false |

`resourceName` is the rightmost non-templated segment. Note that the
sub-resource POST on `/orders/{orderId}/items` has `resourceName = "items"`
(a different group from `"orders"`) — but this no longer matters,
because the enumerator now anchors families on the POST itself, not on
the resource group.

---

## Enumeration — one family per POST

### Family 1 — seed `POST /users`

- Prefix: empty (no path params).
- Tail pool: `[GET /users/{userId}]` — the only consumer under `/users/`.
- Emits 2 chains.

### Family 2 — seed `POST /orders`

- Prefix: empty (no path params).
- Tail pool: `[GET /orders/{orderId}]`. `GET /orders/{orderId}/items/{itemId}`
  is **excluded**: it needs `itemId`, which `POST /orders` doesn't bind.
  That consumer belongs to the items family.
- Emits 2 chains.

### Family 3 — seed `POST /orders/{orderId}/items`

- Prefix resolution: seed has path-param `orderId`.
  `findProducerForParam` scans for a POST whose path equals the
  consumer's path up to the `{orderId}` token — which is `/orders`.
  Exact match: `POST /orders`.
  Prefix = `[POST /orders]`.
- Tail pool: consumers under `/orders/{orderId}/items/...` whose params
  are a subset of `{orderId, itemId}`:
  `[GET /orders/{orderId}/items/{itemId}, DELETE /orders/{orderId}/items/{itemId}]`.
- Budget: `maxChainLength(4) - prefixSize(1) - seed(1) = 2` tail slots.
- Emits 4 chains (1 of length 2, 2 of length 3, 1 of length 4 —
  `[tail GET, tail DELETE]` passes; `[tail DELETE, tail GET]` is
  rejected by `deleteLastOnly`).

### All 8 chains, as they come out of the enumerator

| # | Seed | Chain |
| - | --- | --- |
| 1 | `POST /users`                      | `POST /users` |
| 2 | `POST /users`                      | `POST /users` -> `GET /users/{userId}` |
| 3 | `POST /orders`                     | `POST /orders` |
| 4 | `POST /orders`                     | `POST /orders` -> `GET /orders/{orderId}` |
| 5 | `POST /orders/{orderId}/items`     | `POST /orders` -> `POST /orders/{orderId}/items` |
| 6 | `POST /orders/{orderId}/items`     | `POST /orders` -> `POST /orders/{orderId}/items` -> `GET .../items/{itemId}` |
| 7 | `POST /orders/{orderId}/items`     | `POST /orders` -> `POST /orders/{orderId}/items` -> `DELETE .../items/{itemId}` |
| 8 | `POST /orders/{orderId}/items`     | `POST /orders` -> `POST /orders/{orderId}/items` -> `GET .../items/{itemId}` -> `DELETE .../items/{itemId}` |

---

## Emission — one file per seed's `resourceName`

```
<outputDir>/sequence/
  conftest.py
  pytest.ini
  requirements.txt
  README-sequence.md
  test_chain_users.py     # chains 1, 2
  test_chain_orders.py    # chains 3, 4
  test_chain_items.py     # chains 5, 6, 7, 8
```

### `test_chain_items.py` — chain #8 in full

```python
def test_items_post_post_get_delete(api_client, auth_headers, base_url):
    # Step 1 - POST /orders
    r = api_client.post(f"{base_url}/orders", headers=auth_headers)
    assert 200 <= r.status_code < 300, ...
    order_id = extract_id(r, hint="orderId")          # prefix POST binds orderId

    # Step 2 - POST /orders/{orderId}/items
    r = api_client.post(f"{base_url}/orders/{order_id}/items", headers=auth_headers)
    assert 200 <= r.status_code < 300, ...
    item_id = extract_id(r, hint="itemId")            # seed POST binds itemId

    # Step 3 - GET /orders/{orderId}/items/{itemId}
    r = api_client.get(f"{base_url}/orders/{order_id}/items/{item_id}", headers=auth_headers)
    assert 200 <= r.status_code < 300, ...

    # Step 4 - DELETE /orders/{orderId}/items/{itemId}
    r = api_client.delete(f"{base_url}/orders/{order_id}/items/{item_id}", headers=auth_headers)
    assert r.status_code in (200, 202, 204), ...
```

Two distinct id variables — `order_id` bound by step 1, `item_id` bound
by step 2 — and step 4 plugs both into the right positions of the
path. That's what makes multi-POST chains actually runnable instead of
collapsing every path parameter to a shared `resource_id`.

---

## How config tweaks change this output

| Setting                                             | Effect |
| ---                                                 | --- |
| `sequence.maxChainLength = 2`                       | Family 3 emits only chain #5. Chains 6, 7, 8 all exceed the 2-step budget. |
| `sequence.maxChainLength = 5`                       | Family 3 stays at 4 chains. Tail pool has only 2 consumers, so there's nothing new to add. |
| `sequence.deleteLastOnly = false`                   | Family 3 also emits `[... -> DELETE -> GET]` as a 5th chain. |
| `sequence.allowRepeats = true`                      | Family 3's tail admits repeats (`[GET, GET]`, `[GET, DELETE, GET]`, ...) — chain count grows quickly. |
| Remove `POST /orders` from the spec                 | Family 2 disappears. Family 3's `orderId` now has no producer, so by default the whole items family is dropped silently. |
| `sequence.unresolvedParamPolicy = EMIT_WITH_MARKER` | Unresolved families are emitted anyway; each generated test starts with `pytest.skip(...)`, so the gap is visible in the test report. |

---

## Reproducing this locally

```bash
mvn exec:java -Dexec.mainClass=egain.oassdk.cli.OASSDKCLI \
    -Dexec.args="tests --spec path/to/demo.yaml --output out --type sequence"
ls out/sequence/
# conftest.py  pytest.ini  requirements.txt  README-sequence.md
# test_chain_users.py  test_chain_orders.py  test_chain_items.py
```

Or directly from Java via
[`GenerateSequenceChainsFromSpec`](../examples/egain/oassdk/examples/GenerateSequenceChainsFromSpec.java)
(see its javadoc for env-var vs positional-args invocation).
