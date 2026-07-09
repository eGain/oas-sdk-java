package egain.oassdk.generators.java;

import egain.oassdk.generators.common.OpenApiSchemaUtils;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jersey-facing facade for {@link OpenApiSchemaUtils}. Prefer {@code generators.common} for
 * language-agnostic schema utilities in new code.
 */
public final class JerseySchemaUtils {

    public static final int MAX_COMPOSITION_RESOLVE_DEPTH = OpenApiSchemaUtils.MAX_COMPOSITION_RESOLVE_DEPTH;
    public static final int MAX_MERGE_SCHEMA_DEPTH = OpenApiSchemaUtils.MAX_MERGE_SCHEMA_DEPTH;

    private JerseySchemaUtils() {
    }

    public static final class ObjectWithSingleArrayInfo {
        public final String innerPropertyName;
        public final String itemTypeName;

        ObjectWithSingleArrayInfo(OpenApiSchemaUtils.ObjectWithSingleArrayInfo delegate) {
            this.innerPropertyName = delegate.innerPropertyName;
            this.itemTypeName = delegate.itemTypeName;
        }

        ObjectWithSingleArrayInfo(String innerPropertyName, String itemTypeName) {
            this.innerPropertyName = innerPropertyName;
            this.itemTypeName = itemTypeName;
        }
    }

    public static Map<String, Object> resolveCompositionToEffectiveSchema(Map<String, Object> schema, Map<String, Object> spec) {
        return OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(schema, spec);
    }

    public static Map<String, Object> resolveCompositionToEffectiveSchema(Map<String, Object> schema, Map<String, Object> spec, int depth) {
        return OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(schema, spec, depth);
    }

    public static Map<String, Object> resolveRefInSchema(Map<String, Object> schema, Map<String, Object> spec) {
        return OpenApiSchemaUtils.resolveRefInSchema(schema, spec);
    }

    public static void mergeIntoEffectiveSchema(Map<String, Object> merged, Map<String, Object> from) {
        OpenApiSchemaUtils.mergeIntoEffectiveSchema(merged, from);
    }

    public static Map<String, Object> mergePropertyDefinitionsForComposition(Map<String, Object> earlier, Map<String, Object> later) {
        return OpenApiSchemaUtils.mergePropertyDefinitionsForComposition(earlier, later);
    }

    public static Map<String, Object> mergePropertyDefinitionsForComposition(Map<String, Object> earlier,
                                                                             Map<String, Object> later,
                                                                             Map<String, Object> spec) {
        return OpenApiSchemaUtils.mergePropertyDefinitionsForComposition(earlier, later, spec);
    }

    static boolean definesOwnPropertyType(Map<String, Object> schema) {
        return OpenApiSchemaUtils.definesOwnPropertyType(schema);
    }

    public static void mergePropertiesIntoAll(Map<String, Object> allProperties, Map<String, Object> properties) {
        OpenApiSchemaUtils.mergePropertiesIntoAll(allProperties, properties);
    }

    public static void mergePropertiesIntoAll(Map<String, Object> allProperties,
                                              Map<String, Object> properties,
                                              Map<String, Object> spec) {
        OpenApiSchemaUtils.mergePropertiesIntoAll(allProperties, properties, spec);
    }

    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                             List<String> allRequired, Map<String, Object> spec) {
        OpenApiSchemaUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec);
    }

    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                             List<String> allRequired, Map<String, Object> spec,
                                             Map<Object, Boolean> visited) {
        OpenApiSchemaUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec, visited);
    }

    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                             List<String> allRequired, Map<String, Object> spec,
                                             Map<Object, Boolean> visited, int depth) {
        OpenApiSchemaUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec, visited, depth);
    }

    static boolean isAllOfRefBranch(Map<String, Object> branch) {
        return OpenApiSchemaUtils.isAllOfRefBranch(branch);
    }

    static boolean allOfHasPropertyOverlayBranches(List<Map<String, Object>> allOfSchemas) {
        return OpenApiSchemaUtils.allOfHasPropertyOverlayBranches(allOfSchemas);
    }

    public static String findComponentSchemaName(Map<String, Object> schema, Map<String, Object> spec) {
        return OpenApiSchemaUtils.findComponentSchemaName(schema, spec);
    }

    static void partitionAllOfBranches(List<Map<String, Object>> allOfSchemas,
                                       List<Map<String, Object>> refBranches,
                                       List<Map<String, Object>> overlayBranches) {
        OpenApiSchemaUtils.partitionAllOfBranches(allOfSchemas, refBranches, overlayBranches);
    }

    public static void mergeAllOfBranchesIntoProperties(List<Map<String, Object>> allOfSchemas,
                                                        Map<String, Object> allProperties,
                                                        List<String> allRequired,
                                                        Map<String, Object> spec) {
        OpenApiSchemaUtils.mergeAllOfBranchesIntoProperties(allOfSchemas, allProperties, allRequired, spec);
    }

    public static void mergeAllOfBranchesIntoProperties(List<Map<String, Object>> allOfSchemas,
                                                        Map<String, Object> allProperties,
                                                        List<String> allRequired,
                                                        Map<String, Object> spec,
                                                        Map<Object, Boolean> visited,
                                                        int depth) {
        OpenApiSchemaUtils.mergeAllOfBranchesIntoProperties(allOfSchemas, allProperties, allRequired, spec, visited, depth);
    }

    public static boolean isSchemaFlagTrue(Map<String, Object> schema, String key) {
        return OpenApiSchemaUtils.isSchemaFlagTrue(schema, key);
    }

    public static String getSchemaNameFromRef(Map<String, Object> schema) {
        return OpenApiSchemaUtils.getSchemaNameFromRef(schema);
    }

    public static String deriveSchemaNameFromExternalRef(String ref) {
        return OpenApiSchemaUtils.deriveSchemaNameFromExternalRef(ref);
    }

    public static boolean isObjectWithSingleArrayOfRef(Map<String, Object> schema, Map<String, Object> spec) {
        return OpenApiSchemaUtils.isObjectWithSingleArrayOfRef(schema, spec);
    }

    public static ObjectWithSingleArrayInfo getObjectWithSingleArrayInfo(Map<String, Object> schema, Map<String, Object> spec) {
        OpenApiSchemaUtils.ObjectWithSingleArrayInfo info = OpenApiSchemaUtils.getObjectWithSingleArrayInfo(schema, spec);
        return info == null ? null : new ObjectWithSingleArrayInfo(info);
    }

    public static String getWrapperClassName(String propertyName) {
        return JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName(propertyName));
    }

    public static String getListTypeForObjectWithSingleArrayOfRef(Map<String, Object> schema, Map<String, Object> spec) {
        return OpenApiSchemaUtils.getListTypeForObjectWithSingleArrayOfRef(schema, spec);
    }

    public static boolean isSchemaReference(Map<String, Object> schema, String schemaName) {
        return OpenApiSchemaUtils.isSchemaReference(schema, schemaName);
    }
}
