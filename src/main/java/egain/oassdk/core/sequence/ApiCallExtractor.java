package egain.oassdk.core.sequence;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flattens an OpenAPI spec's {@code paths} into a list of {@link ApiCallInfo}
 * records — one per (path, HTTP verb) pair that exists in the spec.
 *
 * <p>The logic here was lifted out of the former
 * {@code RandomizedSequenceTester} so chain enumeration, pytest emission,
 * and any future sequence-aware generator can share the same extraction
 * without reparsing the spec.
 *
 * <p>Query-parameter defaults follow a fixed preference: {@code example},
 * then {@code default}, then first {@code enum} value, then a typed
 * fallback ({@code minimum} for numeric, {@code "true"}, {@code "test"}).
 * Required parameters without a hit fall back to {@code "1"} so the
 * request still dispatches.
 */
public class ApiCallExtractor {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    /** Mine all operations from the spec's {@code paths} section. */
    public List<ApiCallInfo> extract(Map<String, Object> spec) {
        List<ApiCallInfo> calls = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return calls;
        }
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null) {
                    continue;
                }
                calls.add(new ApiCallInfo(
                        method.toUpperCase(java.util.Locale.ROOT),
                        path,
                        (String) operation.get("operationId"),
                        extractResourceName(path),
                        path.contains("{"),
                        operation.containsKey("requestBody"),
                        extractPathParamNames(path),
                        buildDefaultQueryParams(operation, spec),
                        operation));
            }
        }
        return calls;
    }

    /** Right-most non-empty, non-templated path segment; fallback {@code "resource"}. */
    static String extractResourceName(String path) {
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isEmpty() && !seg.startsWith("{")) {
                return seg;
            }
        }
        return "resource";
    }

    static List<String> extractPathParamNames(String path) {
        List<String> names = new ArrayList<>();
        Matcher m = PATH_PARAM_PATTERN.matcher(path);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    static Map<String, String> buildDefaultQueryParams(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> param : listOperationParameters(operation, spec)) {
            if (!"query".equalsIgnoreCase((String) param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            if (name == null) {
                continue;
            }
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema == null) {
                continue;
            }
            String val = pickExampleForQueryParam(schema);
            if (val != null) {
                map.put(name, val);
            } else if (Boolean.TRUE.equals(param.get("required"))) {
                map.put(name, "1");
            }
        }
        return map;
    }

    static List<Map<String, Object>> listOperationParameters(Map<String, Object> operation, Map<String, Object> spec) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = operation.get("parameters");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            Map<String, Object> m = Util.asStringObjectMap(item);
            if (m == null) {
                continue;
            }
            if (m.containsKey("$ref")) {
                Map<String, Object> resolved = resolveParameterRef((String) m.get("$ref"), spec);
                if (resolved != null) {
                    out.add(resolved);
                }
            } else {
                out.add(m);
            }
        }
        return out;
    }

    static Map<String, Object> resolveParameterRef(String ref, Map<String, Object> spec) {
        if (ref == null || !ref.startsWith("#/components/parameters/")) {
            return null;
        }
        String paramName = ref.substring("#/components/parameters/".length());
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return null;
        }
        Map<String, Object> parameters = Util.asStringObjectMap(components.get("parameters"));
        if (parameters == null) {
            return null;
        }
        return Util.asStringObjectMap(parameters.get(paramName));
    }

    static String pickExampleForQueryParam(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Object ex = schema.get("example");
        if (ex instanceof String s) {
            return s;
        }
        if (ex instanceof Number || ex instanceof Boolean) {
            return String.valueOf(ex);
        }
        Object def = schema.get("default");
        if (def instanceof String s) {
            return s;
        }
        if (def instanceof Number || def instanceof Boolean) {
            return String.valueOf(def);
        }
        Object enumRaw = schema.get("enum");
        if (enumRaw instanceof List<?> enums && !enums.isEmpty()) {
            Object first = enums.getFirst();
            return first != null ? String.valueOf(first) : null;
        }
        String type = (String) schema.get("type");
        if ("integer".equals(type) || "number".equals(type)) {
            Object min = schema.get("minimum");
            if (min instanceof Number n) {
                return String.valueOf(n.longValue());
            }
            return "0";
        }
        if ("boolean".equals(type)) {
            return "true";
        }
        if ("string".equals(type)) {
            return "test";
        }
        return null;
    }

    /**
     * Build a small JSON request-body string for one operation. Reads
     * {@code application/json} content (or the first content type),
     * resolves a top-level {@code $ref} against {@code components.schemas},
     * and emits a single-level JSON object typed by property kind.
     *
     * <p>Does not handle nested objects, arrays, {@code allOf}/{@code oneOf},
     * {@code format}, or {@code required}. For spec shapes where those
     * matter, the Schemathesis bundle (which handles them rigorously) is
     * the right tool.
     *
     * @return JSON string, or {@code null} if the operation has no usable body
     */
    public String buildRequestBodyForOperation(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return null;
        }
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) {
            return null;
        }
        Map<String, Object> jsonContent = Util.asStringObjectMap(content.get("application/json"));
        if (jsonContent == null) {
            for (Object val : content.values()) {
                jsonContent = Util.asStringObjectMap(val);
                if (jsonContent != null) {
                    break;
                }
            }
        }
        if (jsonContent == null) {
            return null;
        }
        Map<String, Object> schema = Util.asStringObjectMap(jsonContent.get("schema"));
        if (schema == null) {
            return null;
        }
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring("#/components/schemas/".length());
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null) {
                        schema = Util.asStringObjectMap(schemas.get(schemaName));
                    }
                }
            }
        }
        if (schema == null) {
            return null;
        }
        return buildMockJsonFromSchema(schema, 0);
    }

    private String buildMockJsonFromSchema(Map<String, Object> schema, int depth) {
        if (depth > 5) {
            return "{}";
        }
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("\"").append(prop.getKey()).append("\": ");
            Map<String, Object> propSchema = Util.asStringObjectMap(prop.getValue());
            String type = propSchema != null ? (String) propSchema.get("type") : null;
            if (type == null) {
                type = "string";
            }
            switch (type) {
                case "string" -> sb.append("\"mock_").append(prop.getKey()).append("\"");
                case "integer", "number" -> sb.append("1");
                case "boolean" -> sb.append("true");
                default -> sb.append("\"mock_value\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
