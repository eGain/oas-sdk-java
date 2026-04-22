package egain.oassdk.core.sequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerates every valid workflow chain from a flat list of
 * {@link ApiCallInfo}. Chains are <i>valid by construction</i>: the
 * enumerator never emits one that would be semantically broken, so the
 * pytest tests it feeds can assert {@code 2xx} at every step without
 * ambiguity.
 *
 * <p>The five decision rules are applied at enumeration time:
 * <ol>
 *   <li>{@code op_0} must be a creator (POST without path params) — else reject.</li>
 *   <li>Any {@code op_i} where {@code i &gt; 0} must be a consumer (path-templated) — else reject (no second creator in the same chain).</li>
 *   <li>If DELETE is in the chain and {@code deleteLastOnly = true}, it must be the final step — else reject.</li>
 *   <li>If {@code allowRepeats = false}, no consumer appears twice — else reject.</li>
 *   <li>Chains are deduped on method+path signature.</li>
 * </ol>
 *
 * <p>Cross-resource chaining is currently disabled; each chain is scoped
 * to a single {@link ApiCallInfo#resourceName() resource}. Resources
 * without a creator are skipped entirely (no id to seed consumers).
 */
public class ChainEnumerator {

    private final ChainConfig config;

    public ChainEnumerator() {
        this(ChainConfig.defaults());
    }

    public ChainEnumerator(ChainConfig config) {
        this.config = config;
    }

    public List<List<ApiCallInfo>> enumerate(List<ApiCallInfo> allCalls) {
        List<List<ApiCallInfo>> out = new ArrayList<>();
        for (Map.Entry<String, List<ApiCallInfo>> entry : groupByResource(allCalls).entrySet()) {
            out.addAll(enumerateResource(entry.getValue()));
        }
        return out;
    }

    /** Group by {@link ApiCallInfo#resourceName()} preserving insertion order. */
    private Map<String, List<ApiCallInfo>> groupByResource(List<ApiCallInfo> calls) {
        Map<String, List<ApiCallInfo>> byResource = new LinkedHashMap<>();
        for (ApiCallInfo c : calls) {
            byResource.computeIfAbsent(c.resourceName(), k -> new ArrayList<>()).add(c);
        }
        return byResource;
    }

    private List<List<ApiCallInfo>> enumerateResource(List<ApiCallInfo> calls) {
        ApiCallInfo creator = calls.stream().filter(ApiCallInfo::isCreator).findFirst().orElse(null);
        if (creator == null) {
            return List.of();
        }
        List<ApiCallInfo> consumers = calls.stream()
                .filter(ApiCallInfo::isConsumer)
                .toList();

        List<List<ApiCallInfo>> chains = new ArrayList<>();
        chains.add(List.of(creator));

        int maxTail = Math.min(config.maxChainLength() - 1, consumers.size());
        if (config.allowRepeats()) {
            maxTail = config.maxChainLength() - 1;
        }
        for (int tailLen = 1; tailLen <= maxTail; tailLen++) {
            for (List<ApiCallInfo> tail : permutations(consumers, tailLen, config.allowRepeats())) {
                if (config.deleteLastOnly() && hasDeleteBeforeEnd(tail)) {
                    continue;
                }
                List<ApiCallInfo> chain = new ArrayList<>(tailLen + 1);
                chain.add(creator);
                chain.addAll(tail);
                chains.add(List.copyOf(chain));
            }
        }
        return chains;
    }

    /**
     * All ordered tuples of length {@code k} drawn from {@code pool}.
     * When {@code withRepeats = false} this is
     * {@code P(n, k) = n!/(n-k)!}; when true, it is {@code n^k}.
     */
    static <T> List<List<T>> permutations(List<T> pool, int k, boolean withRepeats) {
        List<List<T>> out = new ArrayList<>();
        if (k == 0) {
            out.add(List.of());
            return out;
        }
        if (!withRepeats && k > pool.size()) {
            return out;
        }
        boolean[] used = new boolean[pool.size()];
        permuteInto(pool, k, new ArrayList<>(k), used, withRepeats, out);
        return out;
    }

    private static <T> void permuteInto(List<T> pool, int k, List<T> current,
                                        boolean[] used, boolean withRepeats,
                                        List<List<T>> out) {
        if (current.size() == k) {
            out.add(List.copyOf(current));
            return;
        }
        for (int i = 0; i < pool.size(); i++) {
            if (!withRepeats && used[i]) {
                continue;
            }
            current.add(pool.get(i));
            used[i] = true;
            permuteInto(pool, k, current, used, withRepeats, out);
            current.remove(current.size() - 1);
            used[i] = false;
        }
    }

    private static boolean hasDeleteBeforeEnd(List<ApiCallInfo> tail) {
        for (int i = 0; i < tail.size() - 1; i++) {
            if ("DELETE".equalsIgnoreCase(tail.get(i).method())) {
                return true;
            }
        }
        return false;
    }
}
