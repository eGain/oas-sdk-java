package egain.oassdk.generators.python;

import egain.oassdk.Util;

import java.util.List;
import java.util.Map;

/**
 * Merges OpenAPI schema properties for Python model generation.
 */
public final class PythonSchemaMergeUtils {

    private PythonSchemaMergeUtils() {
    }

    public static void mergeSchemaProperties(Map<String, Object> schema,
                                             Map<String, Object> allProperties,
                                             List<String> allRequired,
                                             Map<String, Object> spec) {
        if (schema == null) {
            return;
        }

        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        mergeSchemaProperties(Util.asStringObjectMap(schemas.get(schemaName)), allProperties, allRequired, spec);
                    }
                }
            }
            return;
        }

        if (schema.containsKey("allOf")) {
            for (Map<String, Object> subSchema : Util.asStringObjectMapList(schema.get("allOf"))) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
            return;
        }

        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> subSchema : schemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
            return;
        }

        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties != null) {
            allProperties.putAll(properties);
        }

        List<String> required = Util.asStringList(schema.get("required"));
        if (required != null) {
            for (String field : required) {
                if (!allRequired.contains(field)) {
                    allRequired.add(field);
                }
            }
        }
    }
}
