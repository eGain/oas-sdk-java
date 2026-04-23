package egain.oassdk.core.sequence;

/**
 * Knobs governing {@link ChainEnumerator}. Defaults keep the emitted
 * matrix small (green-path) and are tuned for a useful starting point on
 * typical specs.
 *
 * <p>For a resource with {@code c} consumers and {@code maxChainLength = L},
 * the number of emitted chains per resource is at most
 * {@code 1 + c + c*(c-1) + ... + P(c, L-1)} — roughly {@code c!} when
 * {@code L >= c + 1}. Setting {@code allowRepeats = true} inflates that
 * to {@code c^(L-1)}.
 */
public record ChainConfig(
        int maxChainLength,
        boolean deleteLastOnly,
        boolean allowRepeats,
        UnresolvedParamPolicy unresolvedParamPolicy) {

    public ChainConfig {
        if (maxChainLength < 1) {
            throw new IllegalArgumentException("maxChainLength must be >= 1, got " + maxChainLength);
        }
        if (unresolvedParamPolicy == null) {
            unresolvedParamPolicy = UnresolvedParamPolicy.SKIP;
        }
    }

    public static ChainConfig defaults() {
        return new ChainConfig(4, true, false, UnresolvedParamPolicy.SKIP);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * What to do with a sub-resource POST when one of its path parameters
     * cannot be resolved to a producer POST in the spec.
     */
    public enum UnresolvedParamPolicy {
        /** Drop the chain family. The POST does not appear in any emitted chain. */
        SKIP,
        /**
         * Emit the chain anyway. The test generator renders a {@code pytest.skip}
         * at the top of each test so the unresolved case is visible in the report
         * rather than silently dropped.
         */
        EMIT_WITH_MARKER
    }

    public static final class Builder {
        private int maxChainLength = 4;
        private boolean deleteLastOnly = true;
        private boolean allowRepeats = false;
        private UnresolvedParamPolicy unresolvedParamPolicy = UnresolvedParamPolicy.SKIP;

        public Builder maxChainLength(int v) { this.maxChainLength = v; return this; }
        public Builder deleteLastOnly(boolean v) { this.deleteLastOnly = v; return this; }
        public Builder allowRepeats(boolean v) { this.allowRepeats = v; return this; }
        public Builder unresolvedParamPolicy(UnresolvedParamPolicy v) {
            this.unresolvedParamPolicy = v == null ? UnresolvedParamPolicy.SKIP : v;
            return this;
        }

        public ChainConfig build() {
            return new ChainConfig(maxChainLength, deleteLastOnly, allowRepeats, unresolvedParamPolicy);
        }
    }
}
