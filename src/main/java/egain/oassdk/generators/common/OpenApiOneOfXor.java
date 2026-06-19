package egain.oassdk.generators.common;

import egain.oassdk.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detects simple two-branch {@code oneOf} patterns where each branch requires exactly one distinct
 * property, for mutual-exclusion validation in generated models and integration tests.
 */
public final class OpenApiOneOfXor {

    private OpenApiOneOfXor() {
    }

    /**
     * Metadata for a two-branch {@code oneOf} where each branch declares a single distinct {@code required}
     * property (enforced as XOR on the Java model). When {@code nestedIdRequiredForSorted*} is true, that
     * branch's sole required property is an object schema with {@code required: [id]} and a defined
     * {@code id} property (e.g. {@code createFolder}: {@code department} vs {@code parent}).
     */
    public record SimpleOneOfXorInfo(
            String sortedJson0,
            String sortedJson1,
            boolean nestedIdRequiredForSorted0,
            boolean nestedIdRequiredForSorted1) {
    }

    /**
     * Find {@code oneOf} with exactly two object branches, each with a single {@code required} property name,
     * and the two names differ. Returns {@code [a,b]} in lexicographic order for stable output.
     */
    public static String[] findSimpleOneOfXorPair(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        SimpleOneOfXorInfo info = findSimpleOneOfXorInfo(schema, spec, visited, depth);
        return info == null ? null : new String[] { info.sortedJson0(), info.sortedJson1() };
    }

    /**
     * Same discovery as {@link #findSimpleOneOfXorPair}, plus whether each branch requires a nested {@code id}
     * on its sole required object property.
     */
    public static SimpleOneOfXorInfo findSimpleOneOfXorInfo(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        if (schema == null || depth > OpenApiSchemaUtils.MAX_MERGE_SCHEMA_DEPTH) {
            return null;
        }
        if (visited.containsKey(schema)) {
            return null;
        }
        visited.put(schema, Boolean.TRUE);

        Map<String, Object> node = resolveSchemaDeref(schema, spec, depth);

        if (node.containsKey("allOf")) {
            List<Map<String, Object>> parts = Util.asStringObjectMapList(node.get("allOf"));
            if (parts != null) {
                for (Map<String, Object> sub : parts) {
                    SimpleOneOfXorInfo found = findSimpleOneOfXorInfo(sub, spec, visited, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        if (node.containsKey("oneOf")) {
            List<Map<String, Object>> variants = Util.asStringObjectMapList(node.get("oneOf"));
            if (variants != null && variants.size() == 2) {
                String r0 = singleRequiredPropertyName(variants.get(0), spec);
                String r1 = singleRequiredPropertyName(variants.get(1), spec);
                if (r0 != null && r1 != null && !r0.equals(r1)) {
                    boolean n0 = nestedObjectRequiresId(variants.get(0), r0, spec);
                    boolean n1 = nestedObjectRequiresId(variants.get(1), r1, spec);
                    List<String> pair = new ArrayList<>(2);
                    pair.add(r0);
                    pair.add(r1);
                    Collections.sort(pair);
                    String s0 = pair.get(0);
                    String s1 = pair.get(1);
                    boolean ns0 = s0.equals(r0) ? n0 : n1;
                    boolean ns1 = s1.equals(r0) ? n0 : n1;
                    return new SimpleOneOfXorInfo(s0, s1, ns0, ns1);
                }
            }
            return null;
        }

        if (node.containsKey("anyOf")) {
            return null;
        }

        return null;
    }

    private static Map<String, Object> resolveSchemaDeref(Map<String, Object> schema, Map<String, Object> spec, int depth) {
        if (schema == null || depth > OpenApiSchemaUtils.MAX_COMPOSITION_RESOLVE_DEPTH) {
            return schema;
        }
        Map<String, Object> cur = schema;
        int guard = 0;
        while (cur != null && cur.containsKey("$ref") && !cur.containsKey("properties")
                && !cur.containsKey("allOf") && !cur.containsKey("oneOf") && !cur.containsKey("anyOf")
                && guard++ < 20) {
            cur = OpenApiSchemaUtils.resolveRefInSchema(cur, spec);
            if (cur == schema) {
                break;
            }
        }
        return cur;
    }

    /**
     * True when {@code branch}'s {@code soleRequiredProp} schema (resolved) lists {@code id} in {@code required}
     * and defines a {@code properties.id} sub-schema.
     */
    private static boolean nestedObjectRequiresId(
            Map<String, Object> branch,
            String soleRequiredProp,
            Map<String, Object> spec) {
        if (branch == null || soleRequiredProp == null || spec == null) {
            return false;
        }
        Map<String, Object> node = resolveSchemaDeref(branch, spec, 0);
        Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
        if (props == null) {
            return false;
        }
        Map<String, Object> propSchema = Util.asStringObjectMap(props.get(soleRequiredProp));
        if (propSchema == null) {
            return false;
        }
        Map<String, Object> resolved = OpenApiSchemaUtils.resolveRefInSchema(propSchema, spec);
        Map<String, Object> effective = OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(resolved, spec);
        if (effective == null) {
            return false;
        }
        List<String> req = Util.asStringList(effective.get("required"));
        if (req == null || !req.contains("id")) {
            return false;
        }
        Map<String, Object> innerProps = Util.asStringObjectMap(effective.get("properties"));
        return innerProps != null && innerProps.containsKey("id");
    }

    private static String singleRequiredPropertyName(Map<String, Object> branch, Map<String, Object> spec) {
        if (branch == null) {
            return null;
        }
        Map<String, Object> node = resolveSchemaDeref(branch, spec, 0);
        List<String> req = Util.asStringList(node.get("required"));
        if (req == null || req.size() != 1) {
            return null;
        }
        Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
        if (props == null || props.isEmpty()) {
            return null;
        }
        String name = req.get(0);
        if (!props.containsKey(name)) {
            return null;
        }
        return name;
    }
}
