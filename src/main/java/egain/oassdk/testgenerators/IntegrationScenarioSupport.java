package egain.oassdk.testgenerators;

import com.fasterxml.jackson.databind.ObjectMapper;
import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.generators.common.OpenApiOneOfXor;
import egain.oassdk.generators.common.OpenApiSchemaUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared OpenAPI-driven scenarios for integration test generators (Java, Python, Node).
 * Handles $ref resolution, JSON body synthesis, per-field invalid bodies, parameter negatives,
 * and error-response schema lookup.
 */
public final class IntegrationScenarioSupport {

    public static final String PROP_MAX_INVALID_BODY_FIELDS = "integrationMaxInvalidBodyFieldsPerOperation";
    public static final String PROP_MAX_INVALID_PARAM_CASES = "integrationMaxInvalidParamCasesPerOperation";
    public static final String PROP_EMIT_DECLARED_ERROR_CODES = "integrationEmitDeclaredErrorCodes";

    public static final int DEFAULT_MAX_INVALID_BODY_FIELDS = 40;
    public static final int DEFAULT_MAX_INVALID_PARAM_CASES = 25;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IntegrationScenarioSupport() {
    }

    /**
     * Merged top-level object view after resolving {@code $ref}, {@code allOf}, etc.
     */
    public record FlattenedObjectSchema(
            Map<String, Object> properties,
            List<String> required,
            Map<String, Object> sourceSchema) {
    }

    public record OneOfVariantBody(String label, String jsonBody) {
    }

    public record OneOfXorNegativeBody(String label, String jsonBody) {
    }

    public record DeclaredErrorCase(String label, Map<String, String> queryParamsOverride, int expectedStatus) {
    }

    public static boolean emitDeclaredErrorCodes(TestConfig config) {
        if (config == null || config.getAdditionalProperties() == null) {
            return true;
        }
        Object v = config.getAdditionalProperties().get(PROP_EMIT_DECLARED_ERROR_CODES);
        if (v == null) {
            return true;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString().trim());
    }

    /**
     * Flatten composed object schemas using the same merge logic as Jersey model generation.
     */
    public static FlattenedObjectSchema flattenObjectSchema(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return new FlattenedObjectSchema(new LinkedHashMap<>(), new ArrayList<>(), schema);
        }
        Map<String, Object> node = schema;
        if (node.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) node.get("$ref"), spec);
            if (resolved != null) {
                node = resolved;
            }
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        OpenApiSchemaUtils.mergeSchemaProperties(node, properties, required, spec);
        return new FlattenedObjectSchema(properties, required, node);
    }

    /**
     * Build a synthetic object schema map from flattened properties for reuse by generators.
     */
    public static Map<String, Object> flattenedToObjectSchema(FlattenedObjectSchema flat) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", flat.properties());
        if (!flat.required().isEmpty()) {
            schema.put("required", new ArrayList<>(flat.required()));
        }
        return schema;
    }

    public static int maxInvalidBodyFields(TestConfig config) {
        return readIntProp(config, PROP_MAX_INVALID_BODY_FIELDS, DEFAULT_MAX_INVALID_BODY_FIELDS);
    }

    public static int maxInvalidParamCases(TestConfig config) {
        return readIntProp(config, PROP_MAX_INVALID_PARAM_CASES, DEFAULT_MAX_INVALID_PARAM_CASES);
    }

    private static int readIntProp(TestConfig config, String key, int defaultValue) {
        if (config == null || config.getAdditionalProperties() == null) {
            return defaultValue;
        }
        Object v = config.getAdditionalProperties().get(key);
        if (v instanceof Number n) {
            int i = n.intValue();
            return i > 0 ? i : defaultValue;
        }
        if (v != null) {
            try {
                int i = Integer.parseInt(v.toString().trim());
                return i > 0 ? i : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveRef(String ref, Map<String, Object> spec) {
        if (ref == null || !ref.startsWith("#/")) {
            return null;
        }
        String[] parts = ref.substring(2).split("/");
        Object current = spec;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current instanceof Map ? (Map<String, Object>) current : null;
    }

    public static Map<String, Object> extractRequestBodySchema(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return new LinkedHashMap<>();
        }
        if (requestBody.containsKey("$ref")) {
            requestBody = resolveRef((String) requestBody.get("$ref"), spec);
            if (requestBody == null) {
                return new LinkedHashMap<>();
            }
        }
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) {
            for (Object v : content.values()) {
                mediaType = Util.asStringObjectMap(v);
                if (mediaType != null) {
                    break;
                }
            }
        }
        if (mediaType == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) {
            return new LinkedHashMap<>();
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        return schema;
    }

    public static String getParameterExample(Map<String, Object> param) {
        if (param.containsKey("example")) {
            return formatParameterExampleValue(param.get("example"), param);
        }
        if (param.containsKey("examples")) {
            Map<String, Object> examples = Util.asStringObjectMap(param.get("examples"));
            if (examples != null && !examples.isEmpty()) {
                for (Object exObj : examples.values()) {
                    Map<String, Object> ex = Util.asStringObjectMap(exObj);
                    if (ex != null && ex.containsKey("value")) {
                        return formatParameterExampleValue(ex.get("value"), param);
                    }
                }
            }
        }
        if (param.containsKey("schema")) {
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema != null && schema.containsKey("example")) {
                return formatParameterExampleValue(schema.get("example"), param);
            }
            if (schema != null) {
                String type = (String) schema.get("type");
                if ("string".equals(type)) {
                    List<?> enumVals = schema.get("enum") instanceof List<?> l ? l : null;
                    if (enumVals != null && !enumVals.isEmpty()) {
                        return String.valueOf(enumVals.get(0));
                    }
                    return "test-value";
                } else if ("integer".equals(type) || "number".equals(type)) {
                    return "123";
                } else if ("boolean".equals(type)) {
                    return "true";
                } else if ("array".equals(type)) {
                    return arrayParameterExample(schema, param);
                }
            }
        }
        return "example";
    }

    private static String arrayParameterExample(Map<String, Object> schema, Map<String, Object> param) {
        Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
        String elem = "item";
        if (items != null) {
            List<?> enumVals = items.get("enum") instanceof List<?> l ? l : null;
            if (enumVals != null && !enumVals.isEmpty()) {
                elem = String.valueOf(enumVals.get(0));
            } else if (items.containsKey("example")) {
                elem = String.valueOf(items.get("example"));
            } else if ("string".equals(items.get("type"))) {
                elem = "value";
            }
        }
        if (schema.containsKey("example")) {
            return formatParameterExampleValue(schema.get("example"), param);
        }
        // form style, explode false: comma-separated
        return elem;
    }

    @SuppressWarnings("unchecked")
    private static String formatParameterExampleValue(Object value, Map<String, Object> param) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            String style = param != null ? (String) param.get("style") : null;
            boolean explode = param != null && Boolean.TRUE.equals(param.get("explode"));
            if ("form".equals(style) && !explode) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(list.get(i));
                }
                return sb.toString();
            }
            return list.isEmpty() ? "" : String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    public static boolean isRequestBodyRequired(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return false;
        }
        if (requestBody.containsKey("$ref")) {
            requestBody = resolveRef((String) requestBody.get("$ref"), spec);
        }
        return requestBody != null && Boolean.TRUE.equals(requestBody.get("required"));
    }

    /**
     * Prefer explicit request-body examples from the operation or components, else schema synthesis.
     */
    @SuppressWarnings("unchecked")
    public static String extractRequestBodyExampleJson(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return null;
        }
        if (requestBody.containsKey("$ref")) {
            requestBody = resolveRef((String) requestBody.get("$ref"), spec);
        }
        if (requestBody == null) {
            return null;
        }
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) {
            return null;
        }
        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) {
            for (Object v : content.values()) {
                mediaType = Util.asStringObjectMap(v);
                if (mediaType != null) {
                    break;
                }
            }
        }
        if (mediaType == null) {
            return null;
        }
        if (mediaType.containsKey("example")) {
            return valueToJson(mediaType.get("example"));
        }
        Map<String, Object> examples = Util.asStringObjectMap(mediaType.get("examples"));
        if (examples != null && !examples.isEmpty()) {
            for (Object exObj : examples.values()) {
                Map<String, Object> ex = Util.asStringObjectMap(exObj);
                if (ex != null && ex.containsKey("value")) {
                    return valueToJson(ex.get("value"));
                }
            }
        }
        return null;
    }

    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return generateJsonFromSchemaRaw(Map.of("type", "object"), Map.of());
        }
    }

    public static String generateRequestBodyFromSchemaRaw(Map<String, Object> operation, Map<String, Object> spec) {
        String example = extractRequestBodyExampleJson(operation, spec);
        if (example != null && !example.isBlank()) {
            return example;
        }
        Map<String, Object> schema = extractRequestBodySchema(operation, spec);
        return generateJsonFromSchemaRaw(schema, spec);
    }

    public static String generateMockValue(String fieldName, String type, String format) {
        if (type == null) {
            type = "string";
        }
        switch (type) {
            case "string":
                if ("date-time".equals(format)) {
                    return "2024-01-15T10:30:00Z";
                }
                if ("date".equals(format)) {
                    return "2024-01-15";
                }
                if ("email".equals(format)) {
                    return "test@example.com";
                }
                if ("uri".equals(format) || "url".equals(format)) {
                    return "https://example.com";
                }
                if ("uuid".equals(format)) {
                    return "550e8400-e29b-41d4-a716-446655440000";
                }
                if (fieldName != null) {
                    String lower = fieldName.toLowerCase(Locale.ROOT);
                    if (lower.contains("name")) {
                        return "Test Name";
                    }
                    if (lower.contains("email")) {
                        return "test@example.com";
                    }
                    if (lower.contains("phone")) {
                        return "+1-555-0100";
                    }
                    if (lower.contains("url") || lower.contains("link")) {
                        return "https://example.com";
                    }
                    if (lower.contains("description")) {
                        return "Test description";
                    }
                    if (lower.contains("id")) {
                        return "test-id-123";
                    }
                    if (lower.contains("status")) {
                        return "active";
                    }
                }
                return "test-string";
            case "integer":
                if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("age")) {
                    return "25";
                }
                if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("count")) {
                    return "10";
                }
                return "1";
            case "number":
                return "1.5";
            case "boolean":
                return "true";
            default:
                return "test-value";
        }
    }

    /**
     * Valid JSON text (not escaped for Java source).
     */
    public static String generateJsonFromSchemaRaw(Map<String, Object> schema, Map<String, Object> spec) {
        return generateJsonFromSchemaRaw(schema, spec, new HashSet<>());
    }

    // Self-referential schemas (e.g. a node type whose `children` array items
    // $ref back to itself) would otherwise recurse forever. `visitedRefs`
    // tracks refs currently on the call stack; we add before descending and
    // remove on the way out so sibling subtrees can still expand the same ref.
    @SuppressWarnings("unchecked")
    private static String generateJsonFromSchemaRaw(Map<String, Object> schema,
                                                    Map<String, Object> spec,
                                                    Set<String> visitedRefs) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }
        String enteredRef = null;
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (visitedRefs.contains(ref)) {
                return "{}";
            }
            Map<String, Object> resolved = resolveRef(ref, spec);
            if (resolved == null) {
                return "{}";
            }
            schema = resolved;
            visitedRefs.add(ref);
            enteredRef = ref;
        }
        try {
            String type = (String) schema.get("type");
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));

            // Composed top-level schemas (allOf / oneOf without direct properties)
            if ((properties == null || properties.isEmpty())
                    && (schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf"))) {
                FlattenedObjectSchema flat = flattenObjectSchema(schema, spec);
                if (!flat.properties().isEmpty()) {
                    return generateJsonFromFlattened(flat, spec, visitedRefs);
                }
            }

            if ("array".equals(type)) {
                Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                if (items != null) {
                    return "[" + generateJsonFromSchemaRaw(items, spec, visitedRefs) + "]";
                }
                return "[]";
            }

            if (!"object".equals(type) && type != null && !"null".equals(type)) {
                return generateMockValue(null, type, (String) schema.get("format"));
            }

            if (properties == null || properties.isEmpty()) {
                return "{}";
            }

            return buildObjectJson(properties, Util.asStringList(schema.get("required")), spec, visitedRefs);
        } finally {
            if (enteredRef != null) {
                visitedRefs.remove(enteredRef);
            }
        }
    }

    private static String generateJsonFromFlattened(FlattenedObjectSchema flat,
                                                    Map<String, Object> spec,
                                                    Set<String> visitedRefs) {
        OpenApiOneOfXor.SimpleOneOfXorInfo xor = OpenApiOneOfXor.findSimpleOneOfXorInfo(
                flat.sourceSchema(), spec, new IdentityHashMap<>(), 0);
        if (xor != null) {
            return buildOneOfBranchBody(flat, spec, visitedRefs, xor.sortedJson1(), xor.nestedIdRequiredForSorted1());
        }
        return buildObjectJson(flat.properties(), flat.required(), spec, visitedRefs);
    }

    static String buildOneOfBranchBody(FlattenedObjectSchema flat,
                                       Map<String, Object> spec,
                                       Set<String> visitedRefs,
                                       String branchField,
                                       boolean nestedIdRequired) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (String req : flat.required()) {
            if (req.equals(branchField)) {
                continue;
            }
            if (!flat.properties().containsKey(req)) {
                continue;
            }
            if (!first) {
                json.append(", ");
            }
            first = false;
            json.append('"').append(escapeJsonString(req)).append("\": ");
            appendPropertyValueForField(json, req, flat.properties().get(req), spec, visitedRefs);
        }
        if (!first) {
            json.append(", ");
        }
        json.append('"').append(escapeJsonString(branchField)).append("\": ");
        if (nestedIdRequired) {
            json.append("{\"id\": \"20150000000203\"}");
        } else {
            Map<String, Object> branchSchema = Util.asStringObjectMap(flat.properties().get(branchField));
            json.append(generateJsonFromSchemaRaw(branchSchema != null ? branchSchema : Map.of("type", "object"), spec, visitedRefs));
        }
        json.append("}");
        return json.toString();
    }

    static void appendPropertyValueForField(StringBuilder json,
                                            String fieldName,
                                            Object propSchemaObj,
                                            Map<String, Object> spec,
                                            Set<String> visitedRefs) {
        Map<String, Object> propSchema = Util.asStringObjectMap(propSchemaObj);
        if (propSchema == null) {
            json.append("\"\"");
            return;
        }
        Map<String, Object> effective = OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
        if (effective != null) {
            propSchema = effective;
        }
        appendPropertyJsonValue(json, fieldName, propSchema, spec, visitedRefs);
    }

    private static String buildObjectJson(Map<String, Object> properties,
                                          List<String> requiredFields,
                                          Map<String, Object> spec,
                                          Set<String> visitedRefs) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        List<String> required = requiredFields != null ? requiredFields : List.of();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema == null) {
                continue;
            }
            Map<String, Object> effective = OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
            if (effective != null) {
                propSchema = effective;
            }
            if (Boolean.TRUE.equals(propSchema.get("readOnly"))) {
                continue;
            }
            if (!first) {
                json.append(", ");
            }
            first = false;

            json.append('"').append(escapeJsonString(fieldName)).append("\": ");
            appendPropertyJsonValue(json, fieldName, propSchema, spec, visitedRefs);
        }
        json.append("}");
        return json.toString();
    }

    private static void appendPropertyJsonValue(StringBuilder json,
                                                String fieldName,
                                                Map<String, Object> propSchema,
                                                Map<String, Object> spec,
                                                Set<String> visitedRefs) {
        String propRef = null;
        if (propSchema.containsKey("$ref")) {
            propRef = (String) propSchema.get("$ref");
            if (visitedRefs.contains(propRef)) {
                json.append("{}");
                return;
            }
            Map<String, Object> resolved = resolveRef(propRef, spec);
            if (resolved != null) {
                propSchema = resolved;
            } else {
                propRef = null;
            }
        }
        if (propRef != null) {
            visitedRefs.add(propRef);
        }
        try {
            Map<String, Object> effective = OpenApiSchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
            if (effective != null) {
                propSchema = effective;
            }
            String propType = (String) propSchema.get("type");
            String propFormat = (String) propSchema.get("format");

            if ("object".equals(propType) || propSchema.containsKey("properties")
                    || propSchema.containsKey("allOf") || propSchema.containsKey("oneOf")) {
                json.append(generateJsonFromSchemaRaw(propSchema, spec, visitedRefs));
            } else if ("array".equals(propType)) {
                Map<String, Object> items = Util.asStringObjectMap(propSchema.get("items"));
                if (items != null) {
                    json.append("[").append(generateJsonFromSchemaRaw(items, spec, visitedRefs)).append("]");
                } else {
                    json.append("[]");
                }
            } else if ("integer".equals(propType) || "number".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else if ("boolean".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else {
                json.append('"').append(escapeJsonString(generateMockValue(fieldName, propType, propFormat))).append('"');
            }
        } finally {
            if (propRef != null) {
                visitedRefs.remove(propRef);
            }
        }
    }

    public static String escapeJsonString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Escape for embedding inside a Java string literal ( {@code "} ... {@code "} ).
     */
    public static String escapeJavaString(String s) {
        if (s == null) {
            return "";
        }
        final int maxLiteralChars = 16384;
        if (s.length() > maxLiteralChars) {
            s = s.substring(0, maxLiteralChars);
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Escape content for use inside a Python double-quoted string literal. */
    public static String escapeForPythonDoubleQuoted(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Escape for use inside a JavaScript double-quoted string passed to JSON.parse. */
    public static String escapeForJsDoubleQuoted(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static List<String> getRequiredFieldsFromSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return new ArrayList<>();
        }
        FlattenedObjectSchema flat = flattenObjectSchema(schema, Map.of());
        if (!flat.required().isEmpty()) {
            return new ArrayList<>(flat.required());
        }
        if (schema.containsKey("required")) {
            List<String> required = Util.asStringList(schema.get("required"));
            return required != null ? required : new ArrayList<>();
        }
        return new ArrayList<>();
    }

    public static List<String> getRequiredFieldsFromSchema(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return new ArrayList<>();
        }
        FlattenedObjectSchema flat = flattenObjectSchema(schema, spec);
        return new ArrayList<>(flat.required());
    }

    public static String generateMissingRequiredFieldsBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        return NegativeScenarioBuilder.generateMissingRequiredFieldsBodyRaw(schema, spec);
    }

    public static String generateWrongTypesBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        return NegativeScenarioBuilder.generateWrongTypesBodyRaw(schema, spec);
    }

    /**
     * One invalid JSON body per top-level property (capped).
     */
    public static List<PerFieldInvalidBody> buildPerFieldInvalidBodies(Map<String, Object> schema,
                                                                       Map<String, Object> spec,
                                                                       int maxFields) {
        return NegativeScenarioBuilder.buildPerFieldInvalidBodies(schema, spec, maxFields);
    }

    /**
     * Build one valid JSON body per simple two-branch oneOf XOR variant.
     */
    public static List<OneOfVariantBody> buildOneOfVariantBodies(Map<String, Object> schema, Map<String, Object> spec) {
        return IntegrationScenarioCatalog.buildOneOfVariantBodies(schema, spec);
    }

    /**
     * Negative bodies for oneOf XOR: missing both branches and including both branches.
     */
    public static List<OneOfXorNegativeBody> buildOneOfXorNegativeBodies(Map<String, Object> schema, Map<String, Object> spec) {
        return IntegrationScenarioCatalog.buildOneOfXorNegativeBodies(schema, spec);
    }

    /**
     * Heuristic declared-error tests (e.g. 412 when a query param is absent).
     */
    public static List<DeclaredErrorCase> buildDeclaredErrorCases(Map<String, Object> operation,
                                                                  Map<String, String> baseQueryParams) {
        return IntegrationScenarioCatalog.buildDeclaredErrorCases(operation, baseQueryParams);
    }

    /**
     * Flatten response schema for assertion generation.
     */
    public static Map<String, Object> flattenResponseSchema(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null) {
            return null;
        }
        FlattenedObjectSchema flat = flattenObjectSchema(schema, spec);
        if (flat.properties().isEmpty()) {
            return schema;
        }
        return flattenedToObjectSchema(flat);
    }

    static List<Map<String, Object>> toPostmanQueryList(Map<String, String> queryParams) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (queryParams == null) {
            return list;
        }
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", e.getKey());
            row.put("value", e.getValue() != null ? e.getValue() : "");
            list.add(row);
        }
        return list;
    }

    static Map<String, String> queryListToMap(List<Map<String, Object>> entries) {
        Map<String, String> m = new LinkedHashMap<>();
        if (entries == null) {
            return m;
        }
        for (Map<String, Object> row : entries) {
            if (row == null) {
                continue;
            }
            Object k = row.get("key");
            if (k == null) {
                continue;
            }
            Object v = row.get("value");
            m.put(String.valueOf(k), v != null ? String.valueOf(v) : "");
        }
        return m;
    }

    /**
     * Parameter-level negative cases aligned with {@link egain.oassdk.testgenerators.postman.PostmanNegativeRequestFactory}.
     */
    public static List<IntegrationParamNegativeCase> buildParamNegativeCases(String openApiPath,
                                                                             Map<String, Object> operation,
                                                                             Map<String, String> basePathParams,
                                                                             Map<String, String> baseQueryParams,
                                                                             int maxCases) {
        return NegativeScenarioBuilder.buildParamNegativeCases(
                openApiPath, operation, basePathParams, baseQueryParams, maxCases);
    }

    public static final class PerFieldInvalidBody {
        public final String fieldName;
        public final String invalidJsonBody;

        public PerFieldInvalidBody(String fieldName, String invalidJsonBody) {
            this.fieldName = fieldName;
            this.invalidJsonBody = invalidJsonBody;
        }
    }

    public static final class IntegrationParamNegativeCase {
        public final String name;
        public final Map<String, String> pathParams;
        public final Map<String, String> queryParams;
        public final List<Integer> expectedStatusCodes;

        public IntegrationParamNegativeCase(String name,
                                            Map<String, String> pathParams,
                                            Map<String, String> queryParams,
                                            List<Integer> expectedStatusCodes) {
            this.name = name;
            this.pathParams = pathParams;
            this.queryParams = queryParams;
            this.expectedStatusCodes = expectedStatusCodes;
        }
    }

    public static Map<String, Object> resolveResponseSchema(String statusCode,
                                                            Map<String, Object> responses,
                                                            Map<String, Object> spec) {
        return IntegrationScenarioCatalog.resolveResponseSchema(statusCode, responses, spec);
    }

    public static List<String> standardErrorStatusCodes() {
        return IntegrationScenarioCatalog.standardErrorStatusCodes();
    }
}
