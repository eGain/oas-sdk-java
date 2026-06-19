package egain.oassdk.generators.common;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks OpenAPI specs to collect component schema names referenced by operations.
 */
public final class OpenApiSchemaReferenceWalker {

    private OpenApiSchemaReferenceWalker() {
    }

    public static String resolvedRefSchemaName(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        for (String key : new String[]{"x-resolved-ref", "$ref"}) {
            Object v = schema.get(key);
            if (v instanceof String s && s.contains("#/components/schemas/")) {
                String frag = s.substring(s.indexOf("#/components/schemas/") + "#/components/schemas/".length());
                return frag.contains("/") ? frag.substring(frag.lastIndexOf("/") + 1) : frag;
            }
        }
        return null;
    }

    public static Set<String> collectReferencedSchemas(Map<String, Object> spec) {
        Set<String> referencedSchemas = new HashSet<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return referencedSchemas;
        }

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) {
                        continue;
                    }

                    List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
                    if (parameters != null) {
                        for (Map<String, Object> param : parameters) {
                            collectSchemasFromSchemaObject(param.get("schema"), referencedSchemas, spec);
                        }
                    }

                    List<Map<String, Object>> pathParams = Util.asStringObjectMapList(pathItem.get("parameters"));
                    if (pathParams != null) {
                        for (Map<String, Object> param : pathParams) {
                            collectSchemasFromSchemaObject(param.get("schema"), referencedSchemas, spec);
                        }
                    }

                    Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
                    if (requestBody != null) {
                        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
                        if (content != null) {
                            for (Object mediaTypeObj : content.values()) {
                                Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                if (mediaType != null) {
                                    collectSchemasFromSchemaObject(mediaType.get("schema"), referencedSchemas, spec);
                                }
                            }
                        }
                    }

                    Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
                    if (responses != null) {
                        for (Object responseObj : responses.values()) {
                            Map<String, Object> response = Util.asStringObjectMap(responseObj);
                            if (response != null && response.containsKey("content")) {
                                Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                if (content != null) {
                                    for (Object mediaTypeObj : content.values()) {
                                        Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                        if (mediaType != null) {
                                            collectSchemasFromSchemaObject(mediaType.get("schema"), referencedSchemas, spec);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return referencedSchemas;
    }

    @SuppressWarnings("unchecked")
    public static void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (schemaObj == null) {
            return;
        }

        Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
        if (schema == null) {
            return;
        }

        String resolvedName = resolvedRefSchemaName(schema);
        if (resolvedName != null) {
            addSchemaAndCollectNested(resolvedName, referencedSchemas, spec);
            return;
        }

        for (String comp : new String[]{"allOf", "oneOf", "anyOf"}) {
            Object compObj = schema.get(comp);
            if (compObj instanceof List<?> compositions) {
                for (Object item : compositions) {
                    collectSchemasFromSchemaObject(item, referencedSchemas, spec);
                }
            }
        }

        if ("array".equals(schema.get("type"))) {
            collectSchemasFromSchemaObject(schema.get("items"), referencedSchemas, spec);
        }

        Object propsObj = schema.get("properties");
        if (propsObj instanceof Map<?, ?> props) {
            for (Object propSchema : props.values()) {
                collectSchemasFromSchemaObject(propSchema, referencedSchemas, spec);
            }
        }

        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
                addSchemaAndCollectNested(schemaName, referencedSchemas, spec);
            }
        }
    }

    public static void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (schemaName == null || schemaName.isEmpty() || referencedSchemas.contains(schemaName)) {
            return;
        }
        referencedSchemas.add(schemaName);

        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null) {
                Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                if (referencedSchema != null) {
                    collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec);
                }
            }
        }
    }
}
