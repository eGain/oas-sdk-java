package egain.oassdk.testgenerators;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.testgenerators.postman.PostmanNegativeRequestFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    public static final int DEFAULT_MAX_INVALID_BODY_FIELDS = 40;
    public static final int DEFAULT_MAX_INVALID_PARAM_CASES = 25;

    private IntegrationScenarioSupport() {
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
            return String.valueOf(param.get("example"));
        }
        if (param.containsKey("schema")) {
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema != null && schema.containsKey("example")) {
                return String.valueOf(schema.get("example"));
            }
            if (schema != null) {
                String type = (String) schema.get("type");
                if ("string".equals(type)) {
                    return "test-value";
                } else if ("integer".equals(type)) {
                    return "123";
                } else if ("boolean".equals(type)) {
                    return "true";
                }
            }
        }
        return "example";
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
                    String lower = fieldName.toLowerCase();
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
                if (fieldName != null && fieldName.toLowerCase().contains("age")) {
                    return "25";
                }
                if (fieldName != null && fieldName.toLowerCase().contains("count")) {
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

            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            if (properties == null || properties.isEmpty()) {
                return "{}";
            }

            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (!first) {
                    json.append(", ");
                }
                first = false;

                String fieldName = entry.getKey();
                Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
                if (propSchema == null) {
                    continue;
                }
                json.append('"').append(escapeJsonString(fieldName)).append("\": ");

                // Resolve a property-level $ref, but also track it for the duration of the
                // recursive expansion so a cycle (e.g. A.self -> $ref: A) terminates instead
                // of looping. Without this the recursion enters the resolved schema directly,
                // bypassing the top-of-method ref check.
                String propRef = null;
                if (propSchema.containsKey("$ref")) {
                    propRef = (String) propSchema.get("$ref");
                    if (visitedRefs.contains(propRef)) {
                        json.append("{}");
                        continue;
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
                    String propType = (String) propSchema.get("type");
                    String propFormat = (String) propSchema.get("format");

                    if ("object".equals(propType) || propSchema.containsKey("properties")) {
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
            json.append("}");
            return json.toString();
        } finally {
            if (enteredRef != null) {
                visitedRefs.remove(enteredRef);
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

    public static String generateRequestBodyFromSchemaRaw(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> schema = extractRequestBodySchema(operation, spec);
        return generateJsonFromSchemaRaw(schema, spec);
    }

    public static List<String> getRequiredFieldsFromSchema(Map<String, Object> schema) {
        if (schema == null || !schema.containsKey("required")) {
            return new ArrayList<>();
        }
        List<String> required = Util.asStringList(schema.get("required"));
        return required != null ? required : new ArrayList<>();
    }

    public static String generateMissingRequiredFieldsBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        List<String> requiredFields = getRequiredFieldsFromSchema(schema);
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            if (requiredFields.contains(fieldName)) {
                continue;
            }
            if (!first) {
                json.append(", ");
            }
            first = false;

            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            String propType = propSchema != null ? (String) propSchema.get("type") : "string";
            String propFormat = propSchema != null ? (String) propSchema.get("format") : null;

            json.append('"').append(escapeJsonString(fieldName)).append("\": ");
            if ("integer".equals(propType) || "number".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else if ("boolean".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else {
                json.append('"').append(escapeJsonString(generateMockValue(fieldName, propType, propFormat))).append('"');
            }
        }
        json.append("}");
        return json.toString();
    }

    public static String generateWrongTypesBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return "{\"invalidField\": \"not-a-number\"}";
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{\"invalidField\": \"not-a-number\"}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            first = false;

            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null && propSchema.containsKey("$ref")) {
                Map<String, Object> resolved = resolveRef((String) propSchema.get("$ref"), spec);
                if (resolved != null) {
                    propSchema = resolved;
                }
            }
            String propType = propSchema != null ? (String) propSchema.get("type") : "string";

            json.append('"').append(escapeJsonString(fieldName)).append("\": ");
            json.append(wrongTypeJsonFragment(propType));
        }
        json.append("}");
        return json.toString();
    }

    private static String wrongTypeJsonFragment(String propType) {
        if ("string".equals(propType)) {
            return "12345";
        } else if ("integer".equals(propType) || "number".equals(propType)) {
            return "\"not-a-number\"";
        } else if ("boolean".equals(propType)) {
            return "\"not-a-boolean\"";
        } else if ("array".equals(propType)) {
            return "\"not-an-array\"";
        } else if ("object".equals(propType)) {
            return "\"not-an-object\"";
        }
        return "null";
    }

    /**
     * One invalid JSON body per top-level property (capped).
     */
    public static List<PerFieldInvalidBody> buildPerFieldInvalidBodies(Map<String, Object> schema,
                                                                       Map<String, Object> spec,
                                                                       int maxFields) {
        List<PerFieldInvalidBody> out = new ArrayList<>();
        if (schema == null || maxFields <= 0) {
            return out;
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        if (!"object".equals(schema.get("type"))) {
            return out;
        }
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return out;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (out.size() >= maxFields) {
                break;
            }
            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null && propSchema.containsKey("$ref")) {
                Map<String, Object> resolved = resolveRef((String) propSchema.get("$ref"), spec);
                if (resolved != null) {
                    propSchema = resolved;
                }
            }
            if (propSchema == null) {
                continue;
            }
            String invalidFragment = invalidValueJsonFragment(propSchema);
            String body = generateJsonFromSchemaRawWithFieldOverride(schema, spec, fieldName, invalidFragment);
            out.add(new PerFieldInvalidBody(fieldName, body));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String invalidValueJsonFragment(Map<String, Object> propSchema) {
        if (propSchema == null) {
            return "null";
        }
        String propType = (String) propSchema.get("type");
        List<?> enumVals = propSchema.get("enum") instanceof List<?> l ? l : null;
        if (enumVals != null && !enumVals.isEmpty()) {
            return "\"__invalid_enum_value_oas_sdk__\"";
        }
        if ("string".equals(propType)) {
            if (propSchema.containsKey("pattern")) {
                return "\"!!!pattern-violation!!!\"";
            }
            if (propSchema.get("minLength") instanceof Number n && n.intValue() > 0) {
                return "\"\"";
            }
            if (propSchema.get("maxLength") instanceof Number n && n.intValue() >= 0) {
                return "\"" + "x".repeat(n.intValue() + 1) + "\"";
            }
            return "12345";
        }
        if ("integer".equals(propType) || "number".equals(propType)) {
            if (propSchema.containsKey("minimum")) {
                long min = toLong(propSchema.get("minimum"), 0);
                boolean excl = Boolean.TRUE.equals(propSchema.get("exclusiveMinimum"));
                long bad = excl ? min : min - 1;
                return String.valueOf(bad);
            }
            if (propSchema.containsKey("maximum")) {
                long max = toLong(propSchema.get("maximum"), 0);
                boolean excl = Boolean.TRUE.equals(propSchema.get("exclusiveMaximum"));
                long bad = excl ? max : max + 1;
                return String.valueOf(bad);
            }
            return "\"not-a-number\"";
        }
        if ("boolean".equals(propType)) {
            return "\"not-a-boolean\"";
        }
        if ("array".equals(propType)) {
            return "\"not-an-array\"";
        }
        if ("object".equals(propType)) {
            return "\"not-an-object\"";
        }
        return "null";
    }

    private static long toLong(Object o, long dflt) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /**
     * Build object JSON like {@link #generateJsonFromSchemaRaw} but replace one field's value with {@code invalidFragment}
     * (raw JSON literal fragment, e.g. {@code "bad"} or {@code 123}).
     */
    @SuppressWarnings("unchecked")
    private static String generateJsonFromSchemaRawWithFieldOverride(Map<String, Object> schema,
                                                                     Map<String, Object> spec,
                                                                     String overrideField,
                                                                     String invalidFragment) {
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            first = false;

            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null && propSchema.containsKey("$ref")) {
                Map<String, Object> resolved = resolveRef((String) propSchema.get("$ref"), spec);
                if (resolved != null) {
                    propSchema = resolved;
                }
            }

            json.append('"').append(escapeJsonString(fieldName)).append("\": ");

            if (fieldName.equals(overrideField)) {
                json.append(invalidFragment);
                continue;
            }

            String propType = propSchema != null ? (String) propSchema.get("type") : "string";
            String propFormat = propSchema != null ? (String) propSchema.get("format") : null;

            if ("object".equals(propType) || (propSchema != null && propSchema.containsKey("properties"))) {
                json.append(generateJsonFromSchemaRaw(propSchema, spec));
            } else if ("array".equals(propType)) {
                Map<String, Object> items = Util.asStringObjectMap(propSchema.get("items"));
                if (items != null) {
                    json.append("[").append(generateJsonFromSchemaRaw(items, spec)).append("]");
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
        }
        json.append("}");
        return json.toString();
    }

    public static List<Map<String, Object>> toPostmanQueryList(Map<String, String> queryParams) {
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

    public static Map<String, String> queryListToMap(List<Map<String, Object>> entries) {
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
     * Parameter-level negative cases aligned with {@link PostmanNegativeRequestFactory}.
     */
    public static List<IntegrationParamNegativeCase> buildParamNegativeCases(String openApiPath,
                                                                             Map<String, Object> operation,
                                                                             Map<String, String> basePathParams,
                                                                             Map<String, String> baseQueryParams,
                                                                             int maxCases) {
        List<IntegrationParamNegativeCase> out = new ArrayList<>();
        if (maxCases <= 0 || operation == null) {
            return out;
        }
        Map<String, String> basePath = basePathParams != null ? new LinkedHashMap<>(basePathParams) : new LinkedHashMap<>();
        Map<String, String> baseQuery = baseQueryParams != null ? new LinkedHashMap<>(baseQueryParams) : new LinkedHashMap<>();
        List<Map<String, Object>> positive = toPostmanQueryList(baseQuery);

        List<PostmanNegativeRequestFactory.NegativeCase> raw = PostmanNegativeRequestFactory.buildCases(
                openApiPath, operation, positive, maxCases, 400);

        for (PostmanNegativeRequestFactory.NegativeCase nc : raw) {
            Map<String, String> path = new LinkedHashMap<>(basePath);
            if (nc.pathLiterals != null) {
                path.putAll(nc.pathLiterals);
            }
            Map<String, String> query = queryListToMap(
                    nc.queryEntries != null ? nc.queryEntries : List.of());
            List<Integer> statuses = new ArrayList<>();
            if (nc.expectedStatusOverride != null) {
                statuses.add(nc.expectedStatusOverride);
            } else {
                statuses.add(400);
                statuses.add(422);
            }
            out.add(new IntegrationParamNegativeCase(nc.name, path, query, statuses));
        }
        return out;
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveResponseSchema(String statusCode,
                                                            Map<String, Object> responses,
                                                            Map<String, Object> spec) {
        if (responses == null || !responses.containsKey(statusCode)) {
            return null;
        }
        Map<String, Object> responseObj = Util.asStringObjectMap(responses.get(statusCode));
        if (responseObj == null) {
            return null;
        }
        if (responseObj.containsKey("$ref")) {
            responseObj = resolveRef((String) responseObj.get("$ref"), spec);
            if (responseObj == null) {
                return null;
            }
        }
        Map<String, Object> content = Util.asStringObjectMap(responseObj.get("content"));
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
        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) {
            return null;
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        return schema;
    }

    public static List<String> standardErrorStatusCodes() {
        return List.of("400", "401", "403", "404", "422");
    }
}
