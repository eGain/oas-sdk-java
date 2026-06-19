package egain.oassdk.generators.java;

import egain.oassdk.generators.common.OpenApiOneOfXor;

import java.util.Map;

/**
 * Jersey-facing facade for {@link OpenApiOneOfXor}. Prefer {@code generators.common} for
 * language-agnostic oneOf XOR discovery in new code.
 */
public final class JerseySchemaOneOfXor {

    private JerseySchemaOneOfXor() {
    }

    /**
     * Metadata for a two-branch {@code oneOf} XOR pattern.
     *
     * @see OpenApiOneOfXor.SimpleOneOfXorInfo
     */
    public record SimpleOneOfXorInfo(
            String sortedJson0,
            String sortedJson1,
            boolean nestedIdRequiredForSorted0,
            boolean nestedIdRequiredForSorted1) {

        static SimpleOneOfXorInfo from(OpenApiOneOfXor.SimpleOneOfXorInfo info) {
            return new SimpleOneOfXorInfo(
                    info.sortedJson0(),
                    info.sortedJson1(),
                    info.nestedIdRequiredForSorted0(),
                    info.nestedIdRequiredForSorted1());
        }
    }

    public static String[] findSimpleOneOfXorPair(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        return OpenApiOneOfXor.findSimpleOneOfXorPair(schema, spec, visited, depth);
    }

    public static SimpleOneOfXorInfo findSimpleOneOfXorInfo(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        OpenApiOneOfXor.SimpleOneOfXorInfo info = OpenApiOneOfXor.findSimpleOneOfXorInfo(schema, spec, visited, depth);
        return info == null ? null : SimpleOneOfXorInfo.from(info);
    }
}
