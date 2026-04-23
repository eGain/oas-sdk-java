package egain.oassdk.core.sequence;

import java.util.List;

/**
 * One chain emitted by {@link ChainEnumerator}. Every chain is anchored on
 * a single POST — its {@link #seedPost() seedPost} — which is the operation
 * whose coverage the chain exists to provide. Prefix steps (before the
 * seed) are predecessor POSTs whose outputs resolve the seed's path
 * parameters; tail steps (after the seed) are path-templated consumers
 * that live under the seed's resource tree.
 *
 * @param seedPost    the POST the chain is built around (always somewhere
 *                    in {@code steps}, usually after any prefix steps)
 * @param steps       the full ordered step list: {@code [prefix..., seedPost, tail...]}
 * @param unresolved  {@code true} if the prefix chain could not fully
 *                    resolve the seed's path parameters and the chain
 *                    was nevertheless emitted per
 *                    {@link ChainConfig.UnresolvedParamPolicy#EMIT_WITH_MARKER}.
 *                    Generator renders a {@code pytest.skip} in this case.
 */
public record EnumeratedChain(ApiCallInfo seedPost, List<ApiCallInfo> steps, boolean unresolved) {

    public EnumeratedChain {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public EnumeratedChain(ApiCallInfo seedPost, List<ApiCallInfo> steps) {
        this(seedPost, steps, false);
    }
}
