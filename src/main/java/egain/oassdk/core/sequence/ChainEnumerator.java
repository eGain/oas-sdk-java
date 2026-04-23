package egain.oassdk.core.sequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enumerates workflow chains out of a flat list of {@link ApiCallInfo}.
 *
 * <p>One chain family per POST in the spec — top-level and sub-resource
 * alike. Every POST is guaranteed to appear in at least one emitted chain,
 * so no path is skipped.
 *
 * <p>A chain is always {@code [prefix..., seed POST, tail...]}:
 * <ul>
 *   <li><b>Seed POST.</b> The POST this family is built around.</li>
 *   <li><b>Prefix.</b> Predecessor POSTs whose outputs resolve the seed's
 *       path parameters. Computed recursively via
 *       {@link ApiCallExtractor#findProducerForParam}. If any path
 *       parameter cannot be resolved the family is either dropped or
 *       emitted with a marker per
 *       {@link ChainConfig.UnresolvedParamPolicy}.</li>
 *   <li><b>Tail.</b> Path-templated non-POST consumers (GET/PUT/PATCH/
 *       DELETE) whose path is the seed's path or a descendant of it and
 *       whose path parameters are all bound by (prefix + seed).
 *       Permutations of length up to {@code maxChainLength - prefix.size - 1}.</li>
 * </ul>
 *
 * <p>Filters applied after enumeration:
 * <ol>
 *   <li>{@code maxChainLength} caps the total step count, not just the tail.</li>
 *   <li>{@code deleteLastOnly} rejects tails where DELETE is not the final step.</li>
 *   <li>{@code allowRepeats} controls whether tail permutations may reuse
 *       the same consumer twice.</li>
 *   <li>Chains with an identical method+path signature across all steps
 *       are deduped globally.</li>
 * </ol>
 */
public class ChainEnumerator {

    private final ChainConfig config;

    public ChainEnumerator() {
        this(ChainConfig.defaults());
    }

    public ChainEnumerator(ChainConfig config) {
        this.config = config;
    }

    public List<EnumeratedChain> enumerate(List<ApiCallInfo> allCalls) {
        List<EnumeratedChain> out = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        for (ApiCallInfo post : allCalls) {
            if (!"POST".equalsIgnoreCase(post.method())) {
                continue;
            }
            PrefixResult prefix = buildPrefix(post, allCalls, new HashSet<>());
            boolean unresolved = prefix.hasUnresolved();
            if (unresolved && config.unresolvedParamPolicy() == ChainConfig.UnresolvedParamPolicy.SKIP) {
                continue;
            }

            int prefixSize = prefix.steps().size();
            int seedLen = prefixSize + 1;
            if (seedLen > config.maxChainLength()) {
                // Prefix alone already exceeds the budget.
                continue;
            }

            addIfUnique(composeSteps(prefix.steps(), post, List.of()), post, unresolved,
                    out, seenSignatures);

            List<ApiCallInfo> tailPool = buildTailPool(post, allCalls);
            int tailBudget = config.maxChainLength() - seedLen;
            int maxTail;
            if (config.allowRepeats()) {
                maxTail = tailBudget;
            } else {
                maxTail = Math.min(tailBudget, tailPool.size());
            }
            for (int tailLen = 1; tailLen <= maxTail; tailLen++) {
                for (List<ApiCallInfo> tail : permutations(tailPool, tailLen, config.allowRepeats())) {
                    if (config.deleteLastOnly() && hasDeleteBeforeEnd(tail)) {
                        continue;
                    }
                    addIfUnique(composeSteps(prefix.steps(), post, tail), post, unresolved,
                            out, seenSignatures);
                }
            }
        }
        return out;
    }

    /**
     * Build the prefix chain for a POST by recursively resolving each of
     * its path parameters to a producer POST. Deduplicates so a producer
     * that resolves multiple params appears once, and breaks cycles
     * defensively.
     */
    private PrefixResult buildPrefix(ApiCallInfo post, List<ApiCallInfo> allCalls,
                                     Set<ApiCallInfo> visiting) {
        if (post.pathParamNames().isEmpty()) {
            return new PrefixResult(List.of(), false);
        }
        if (visiting.contains(post)) {
            return new PrefixResult(List.of(), true);
        }
        Set<ApiCallInfo> nextVisiting = new HashSet<>(visiting);
        nextVisiting.add(post);

        List<ApiCallInfo> steps = new ArrayList<>();
        Set<ApiCallInfo> included = new HashSet<>();
        boolean anyUnresolved = false;

        for (String param : post.pathParamNames()) {
            ApiCallInfo producer = ApiCallExtractor.findProducerForParam(post, param, allCalls);
            if (producer == null) {
                anyUnresolved = true;
                continue;
            }
            if (included.contains(producer)) {
                continue;
            }
            PrefixResult sub = buildPrefix(producer, allCalls, nextVisiting);
            if (sub.hasUnresolved()) {
                anyUnresolved = true;
            }
            for (ApiCallInfo step : sub.steps()) {
                if (included.add(step)) {
                    steps.add(step);
                }
            }
            if (included.add(producer)) {
                steps.add(producer);
            }
        }
        return new PrefixResult(List.copyOf(steps), anyUnresolved);
    }

    /**
     * Tail pool for a seed POST: non-POST path-templated consumers whose
     * path is the seed's own or a descendant under the seed's tree, and
     * whose path parameters are all bound by (prefix + seed).
     *
     * <p>Ancestor consumers belong to a different seed's family. POSTs are
     * always seeds themselves, never tail members.
     */
    private static List<ApiCallInfo> buildTailPool(ApiCallInfo seed, List<ApiCallInfo> allCalls) {
        Set<String> boundParams = new HashSet<>(seed.pathParamNames());
        String seedPath = seed.path();
        String descendantScan = seedPath + "/{";
        for (ApiCallInfo c : allCalls) {
            if (c.path().startsWith(descendantScan)) {
                int start = descendantScan.length();
                int end = c.path().indexOf('}', start);
                if (end > start) {
                    boundParams.add(c.path().substring(start, end));
                    break;
                }
            }
        }

        List<ApiCallInfo> pool = new ArrayList<>();
        for (ApiCallInfo c : allCalls) {
            if (c == seed) {
                continue;
            }
            if (!c.isConsumer()) {
                continue;
            }
            if ("POST".equalsIgnoreCase(c.method())) {
                continue;
            }
            if (!(c.path().equals(seedPath) || c.path().startsWith(seedPath + "/"))) {
                continue;
            }
            if (!boundParams.containsAll(c.pathParamNames())) {
                continue;
            }
            pool.add(c);
        }
        return pool;
    }

    private static List<ApiCallInfo> composeSteps(List<ApiCallInfo> prefix, ApiCallInfo seed,
                                                  List<ApiCallInfo> tail) {
        List<ApiCallInfo> steps = new ArrayList<>(prefix.size() + 1 + tail.size());
        steps.addAll(prefix);
        steps.add(seed);
        steps.addAll(tail);
        return steps;
    }

    private static void addIfUnique(List<ApiCallInfo> steps, ApiCallInfo seed, boolean unresolved,
                                    List<EnumeratedChain> out, Set<String> seenSignatures) {
        StringBuilder sig = new StringBuilder();
        for (ApiCallInfo c : steps) {
            sig.append(c.method()).append(' ').append(c.path()).append('|');
        }
        if (seenSignatures.add(sig.toString())) {
            out.add(new EnumeratedChain(seed, steps, unresolved));
        }
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

    private record PrefixResult(List<ApiCallInfo> steps, boolean hasUnresolved) {
        PrefixResult {
            steps = List.copyOf(steps);
        }
    }
}
