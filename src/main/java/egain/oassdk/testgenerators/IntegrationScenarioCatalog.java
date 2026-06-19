package egain.oassdk.testgenerators;

import com.fasterxml.jackson.databind.ObjectMapper;
import egain.oassdk.Util;
import egain.oassdk.generators.java.JerseySchemaOneOfXor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OneOf variants, declared error cases, and response-schema lookup for integration tests.
 */
final class IntegrationScenarioCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IntegrationScenarioCatalog() {
    }

    static List<IntegrationScenarioSupport.OneOfVariantBody> buildOneOfVariantBodies(Map<String, Object> schema,
                                                                                     Map<String, Object> spec) {
        List<IntegrationScenarioSupport.OneOfVariantBody> out = new ArrayList<>();
        if (schema == null || spec == null) {
            return out;
        }
        IntegrationScenarioSupport.FlattenedObjectSchema flat = IntegrationScenarioSupport.flattenObjectSchema(schema, spec);
        JerseySchemaOneOfXor.SimpleOneOfXorInfo xor = JerseySchemaOneOfXor.findSimpleOneOfXorInfo(
                flat.sourceSchema(), spec, new IdentityHashMap<>(), 0);
        if (xor == null) {
            return out;
        }
        out.add(new IntegrationScenarioSupport.OneOfVariantBody(
                capitalizeLabel(xor.sortedJson0()),
                IntegrationScenarioSupport.buildOneOfBranchBody(
                        flat, spec, new HashSet<>(), xor.sortedJson0(), xor.nestedIdRequiredForSorted0())));
        out.add(new IntegrationScenarioSupport.OneOfVariantBody(
                capitalizeLabel(xor.sortedJson1()),
                IntegrationScenarioSupport.buildOneOfBranchBody(
                        flat, spec, new HashSet<>(), xor.sortedJson1(), xor.nestedIdRequiredForSorted1())));
        return out;
    }

    static List<IntegrationScenarioSupport.OneOfXorNegativeBody> buildOneOfXorNegativeBodies(Map<String, Object> schema,
                                                                                             Map<String, Object> spec) {
        List<IntegrationScenarioSupport.OneOfXorNegativeBody> out = new ArrayList<>();
        if (schema == null || spec == null) {
            return out;
        }
        IntegrationScenarioSupport.FlattenedObjectSchema flat = IntegrationScenarioSupport.flattenObjectSchema(schema, spec);
        JerseySchemaOneOfXor.SimpleOneOfXorInfo xor = JerseySchemaOneOfXor.findSimpleOneOfXorInfo(
                flat.sourceSchema(), spec, new IdentityHashMap<>(), 0);
        if (xor == null) {
            return out;
        }

        StringBuilder missing = new StringBuilder("{");
        boolean first = true;
        for (String req : flat.required()) {
            if (req.equals(xor.sortedJson0()) || req.equals(xor.sortedJson1())) {
                continue;
            }
            if (!flat.properties().containsKey(req)) {
                continue;
            }
            if (!first) {
                missing.append(", ");
            }
            first = false;
            missing.append('"').append(IntegrationScenarioSupport.escapeJsonString(req)).append("\": ");
            IntegrationScenarioSupport.appendPropertyValueForField(
                    missing, req, flat.properties().get(req), spec, new HashSet<>());
        }
        missing.append("}");
        out.add(new IntegrationScenarioSupport.OneOfXorNegativeBody("MissingBranches", missing.toString()));

        String branch0 = IntegrationScenarioSupport.buildOneOfBranchBody(
                flat, spec, new HashSet<>(), xor.sortedJson0(), xor.nestedIdRequiredForSorted0());
        String branch1 = IntegrationScenarioSupport.buildOneOfBranchBody(
                flat, spec, new HashSet<>(), xor.sortedJson1(), xor.nestedIdRequiredForSorted1());
        String both = mergeJsonObjects(branch0, branch1);
        out.add(new IntegrationScenarioSupport.OneOfXorNegativeBody("BothBranches", both));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<IntegrationScenarioSupport.DeclaredErrorCase> buildDeclaredErrorCases(Map<String, Object> operation,
                                                                                      Map<String, String> baseQueryParams) {
        List<IntegrationScenarioSupport.DeclaredErrorCase> out = new ArrayList<>();
        if (operation == null) {
            return out;
        }
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null || !responses.containsKey("412")) {
            return out;
        }
        Map<String, Object> resp412 = Util.asStringObjectMap(responses.get("412"));
        String description = resp412 != null ? String.valueOf(resp412.getOrDefault("description", "")) : "";
        if (description.isBlank()) {
            return out;
        }
        List<Map<String, Object>> parameters = operation.containsKey("parameters")
                ? Util.asStringObjectMapList(operation.get("parameters"))
                : List.of();
        for (Map<String, Object> param : parameters) {
            if (!"query".equals(param.get("in"))) {
                continue;
            }
            String paramName = (String) param.get("name");
            if (paramName == null || !description.toLowerCase(Locale.ROOT).contains(paramName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Map<String, String> query = new LinkedHashMap<>();
            if (baseQueryParams != null) {
                query.putAll(baseQueryParams);
            }
            query.remove(paramName);
            out.add(new IntegrationScenarioSupport.DeclaredErrorCase("PreconditionFailed_" + paramName, query, 412));
            break;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveResponseSchema(String statusCode,
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
            responseObj = IntegrationScenarioSupport.resolveRef((String) responseObj.get("$ref"), spec);
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
            Map<String, Object> resolved = IntegrationScenarioSupport.resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        return schema;
    }

    static List<String> standardErrorStatusCodes() {
        return List.of("400", "401", "403", "404", "422");
    }

    private static String mergeJsonObjects(String json0, String json1) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m0 = OBJECT_MAPPER.readValue(json0, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> m1 = OBJECT_MAPPER.readValue(json1, Map.class);
            Map<String, Object> merged = new LinkedHashMap<>(m0);
            merged.putAll(m1);
            return OBJECT_MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            return json0;
        }
    }

    private static String capitalizeLabel(String name) {
        if (name == null || name.isEmpty()) {
            return "Variant";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
