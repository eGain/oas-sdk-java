package egain.oassdk.core.sequence;

/**
 * Knobs governing {@link ChainEnumerator}. Defaults keep the emitted
 * matrix small (single-resource, green-path) and are tuned for a useful
 * starting point on typical specs.
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
        boolean crossResource) {

    public ChainConfig {
        if (maxChainLength < 1) {
            throw new IllegalArgumentException("maxChainLength must be >= 1, got " + maxChainLength);
        }
    }

    public static ChainConfig defaults() {
        return new ChainConfig(4, true, false, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxChainLength = 4;
        private boolean deleteLastOnly = true;
        private boolean allowRepeats = false;
        private boolean crossResource = false;

        public Builder maxChainLength(int v) { this.maxChainLength = v; return this; }
        public Builder deleteLastOnly(boolean v) { this.deleteLastOnly = v; return this; }
        public Builder allowRepeats(boolean v) { this.allowRepeats = v; return this; }
        public Builder crossResource(boolean v) { this.crossResource = v; return this; }

        public ChainConfig build() {
            return new ChainConfig(maxChainLength, deleteLastOnly, allowRepeats, crossResource);
        }
    }
}
