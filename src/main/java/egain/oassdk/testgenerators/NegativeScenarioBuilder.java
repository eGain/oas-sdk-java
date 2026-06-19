package egain.oassdk.testgenerators;

import egain.oassdk.Util;
import egain.oassdk.generators.java.JerseySchemaUtils;
import egain.oassdk.testgenerators.postman.PostmanNegativeRequestFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invalid request-body and parameter scenarios for integration test generators.
 */
final class NegativeScenarioBuilder {

    private NegativeScenarioBuilder() {
    }

    static String generateMissingRequiredFieldsBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }
        IntegrationScenarioSupport.FlattenedObjectSchema flat = IntegrationScenarioSupport.flattenObjectSchema(schema, spec);
        Map<String, Object> properties = flat.properties();
        List<String> requiredFields = flat.required();
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
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null) {
                Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
                if (effective != null) {
                    propSchema = effective;
                }
                if (Boolean.TRUE.equals(propSchema.get("readOnly"))) {
                    continue;
                }
            }
            if (!first) {
                json.append(", ");
            }
            first = false;

            String propType = propSchema != null ? (String) propSchema.get("type") : "string";
            String propFormat = propSchema != null ? (String) propSchema.get("format") : null;

            json.append('"').append(IntegrationScenarioSupport.escapeJsonString(fieldName)).append("\": ");
            if ("integer".equals(propType) || "number".equals(propType)) {
                json.append(IntegrationScenarioSupport.generateMockValue(fieldName, propType, propFormat));
            } else if ("boolean".equals(propType)) {
                json.append(IntegrationScenarioSupport.generateMockValue(fieldName, propType, propFormat));
            } else if ("object".equals(propType) || (propSchema != null && propSchema.containsKey("properties"))) {
                json.append(IntegrationScenarioSupport.generateJsonFromSchemaRaw(propSchema, spec));
            } else {
                json.append('"').append(IntegrationScenarioSupport.escapeJsonString(
                        IntegrationScenarioSupport.generateMockValue(fieldName, propType, propFormat))).append('"');
            }
        }
        json.append("}");
        return json.toString();
    }

    static String generateWrongTypesBodyRaw(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return "{\"invalidField\": \"not-a-number\"}";
        }
        IntegrationScenarioSupport.FlattenedObjectSchema flat = IntegrationScenarioSupport.flattenObjectSchema(schema, spec);
        Map<String, Object> properties = flat.properties();
        if (properties == null || properties.isEmpty()) {
            return "{\"invalidField\": \"not-a-number\"}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        int count = 0;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (count >= 5) {
                break;
            }
            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null) {
                Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
                if (effective != null) {
                    propSchema = effective;
                }
                if (Boolean.TRUE.equals(propSchema.get("readOnly"))) {
                    continue;
                }
            }
            if (!first) {
                json.append(", ");
            }
            first = false;
            count++;

            String propType = propSchema != null ? (String) propSchema.get("type") : "string";

            json.append('"').append(IntegrationScenarioSupport.escapeJsonString(fieldName)).append("\": ");
            json.append(wrongTypeJsonFragment(propType));
        }
        json.append("}");
        return json.toString();
    }

    static List<IntegrationScenarioSupport.PerFieldInvalidBody> buildPerFieldInvalidBodies(Map<String, Object> schema,
                                                                                            Map<String, Object> spec,
                                                                                            int maxFields) {
        List<IntegrationScenarioSupport.PerFieldInvalidBody> out = new ArrayList<>();
        if (schema == null || maxFields <= 0) {
            return out;
        }
        IntegrationScenarioSupport.FlattenedObjectSchema flat = IntegrationScenarioSupport.flattenObjectSchema(schema, spec);
        Map<String, Object> properties = flat.properties();
        if (properties == null || properties.isEmpty()) {
            return out;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (out.size() >= maxFields) {
                break;
            }
            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null) {
                Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
                if (effective != null) {
                    propSchema = effective;
                }
                if (Boolean.TRUE.equals(propSchema.get("readOnly"))) {
                    continue;
                }
            }
            if (propSchema == null) {
                continue;
            }
            String invalidFragment = invalidValueJsonFragment(propSchema);
            String body = generateJsonFromSchemaRawWithFieldOverride(flat, spec, fieldName, invalidFragment);
            out.add(new IntegrationScenarioSupport.PerFieldInvalidBody(fieldName, body));
        }
        return out;
    }

    static List<IntegrationScenarioSupport.IntegrationParamNegativeCase> buildParamNegativeCases(String openApiPath,
                                                                                                 Map<String, Object> operation,
                                                                                                 Map<String, String> basePathParams,
                                                                                                 Map<String, String> baseQueryParams,
                                                                                                 int maxCases) {
        List<IntegrationScenarioSupport.IntegrationParamNegativeCase> out = new ArrayList<>();
        if (maxCases <= 0 || operation == null) {
            return out;
        }
        Map<String, String> basePath = basePathParams != null ? new LinkedHashMap<>(basePathParams) : new LinkedHashMap<>();
        Map<String, String> baseQuery = baseQueryParams != null ? new LinkedHashMap<>(baseQueryParams) : new LinkedHashMap<>();
        List<Map<String, Object>> positive = IntegrationScenarioSupport.toPostmanQueryList(baseQuery);

        List<PostmanNegativeRequestFactory.NegativeCase> raw = PostmanNegativeRequestFactory.buildCases(
                openApiPath, operation, positive, maxCases, 400);

        for (PostmanNegativeRequestFactory.NegativeCase nc : raw) {
            Map<String, String> path = new LinkedHashMap<>(basePath);
            if (nc.pathLiterals != null) {
                path.putAll(nc.pathLiterals);
            }
            Map<String, String> query = IntegrationScenarioSupport.queryListToMap(
                    nc.queryEntries != null ? nc.queryEntries : List.of());
            List<Integer> statuses = new ArrayList<>();
            if (nc.expectedStatusOverride != null) {
                statuses.add(nc.expectedStatusOverride);
            } else {
                statuses.add(400);
                statuses.add(422);
            }
            out.add(new IntegrationScenarioSupport.IntegrationParamNegativeCase(nc.name, path, query, statuses));
        }
        return out;
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

    @SuppressWarnings("unchecked")
    private static String generateJsonFromSchemaRawWithFieldOverride(IntegrationScenarioSupport.FlattenedObjectSchema flat,
                                                                     Map<String, Object> spec,
                                                                     String overrideField,
                                                                     String invalidFragment) {
        Map<String, Object> properties = flat.properties();
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema != null) {
                Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(propSchema, spec);
                if (effective != null) {
                    propSchema = effective;
                }
                if (Boolean.TRUE.equals(propSchema.get("readOnly")) && !fieldName.equals(overrideField)) {
                    continue;
                }
            }
            if (!first) {
                json.append(", ");
            }
            first = false;

            json.append('"').append(IntegrationScenarioSupport.escapeJsonString(fieldName)).append("\": ");

            if (fieldName.equals(overrideField)) {
                json.append(invalidFragment);
                continue;
            }

            IntegrationScenarioSupport.appendPropertyValueForField(json, fieldName, propSchema, spec, new HashSet<>());
        }
        json.append("}");
        return json.toString();
    }
}
