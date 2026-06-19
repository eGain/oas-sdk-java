package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.generators.common.OpenApiSchemaReferenceWalker;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Collects inline response schemas and registers them in the OpenAPI components map.
 */
public final class PythonSchemaCollector {

    private final PythonGenerationContext ctx;

    public PythonSchemaCollector(PythonGenerationContext ctx) {
        this.ctx = ctx;
    }

    public void collectInlinedSchemas() {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    collectInlinedSchemasFromOperation(operation, path, method);
                }
            }
        }
    }

    private void collectInlinedSchemasFromOperation(Map<String, Object> operation, String path, String method) {
        if (operation == null || !operation.containsKey("responses")) {
            return;
        }

        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) {
            return;
        }

        for (Object responseObj : responses.values()) {
            Map<String, Object> response = Util.asStringObjectMap(responseObj);
            if (response == null || !response.containsKey("content")) {
                continue;
            }
            Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
            if (content == null) {
                continue;
            }

            for (Object mediaTypeObj : content.values()) {
                Map<String, Object> mediaTypeMap = Util.asStringObjectMap(mediaTypeObj);
                if (mediaTypeMap == null || !mediaTypeMap.containsKey("schema")) {
                    continue;
                }
                Object schemaObj = mediaTypeMap.get("schema");
                if (!(schemaObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
                if (isInlineSchema(schema)) {
                    String modelName = generateInlineSchemaName(path, method);
                    if (!ctx.getInlinedSchemas().containsKey(schemaObj)) {
                        ctx.getInlinedSchemas().put(schemaObj, modelName);
                        addInlineSchemaToComponents(modelName, schema);
                    }
                }
            }
        }
    }

    private boolean isInlineSchema(Map<String, Object> schema) {
        if (schema == null) {
            return false;
        }
        if (schema.containsKey("$ref")) {
            return false;
        }
        if ("object".equals(schema.get("type")) && schema.containsKey("properties")) {
            return true;
        }
        if ("array".equals(schema.get("type")) && schema.containsKey("items")) {
            Map<String, Object> itemsMap = Util.asStringObjectMap(schema.get("items"));
            return itemsMap != null
                    && !itemsMap.containsKey("$ref")
                    && "object".equals(itemsMap.get("type"))
                    && itemsMap.containsKey("properties");
        }
        return false;
    }

    private String generateInlineSchemaName(String path, String method) {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Map<String, Object> pathItem = Util.asStringObjectMap(paths.get(path));
            if (pathItem != null) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation != null && operation.containsKey("operationId")) {
                    return PythonNamingUtils.toPascalCase((String) operation.get("operationId")) + "Response";
                }
            }
        }
        String pathPart = path.replaceAll("[^a-zA-Z0-9]", "");
        String methodPart = method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1).toLowerCase(Locale.ROOT);
        return pathPart + methodPart + "Response";
    }

    private void addInlineSchemaToComponents(String modelName, Map<String, Object> schema) {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            components = new LinkedHashMap<>();
            spec.put("components", components);
        }
        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) {
            schemas = new LinkedHashMap<>();
            components.put("schemas", schemas);
        }
        if (!schemas.containsKey(modelName)) {
            schemas.put(modelName, new LinkedHashMap<>(schema));
        }
    }

    public static boolean isErrorSchema(String schemaName, Map<String, Object> schema) {
        if (schemaName == null) {
            return false;
        }
        String lowerName = schemaName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("error") || lowerName.contains("exception") || lowerName.contains("fault")
                || lowerName.endsWith("error") || lowerName.endsWith("exception")) {
            return true;
        }
        if (schema != null) {
            Object description = schema.get("description");
            if (description instanceof String descStr) {
                return descStr.toLowerCase(Locale.ROOT).matches("(?s).*\\b(error|exception|fault)\\b.*");
            }
        }
        return false;
    }

    public static String resolvedRefSchemaName(Map<String, Object> schema) {
        return OpenApiSchemaReferenceWalker.resolvedRefSchemaName(schema);
    }
}
