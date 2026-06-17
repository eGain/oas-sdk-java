package egain.oassdk.core.sequence;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Snake-cased Python variable name for a path parameter.
     * {@code folderID} → {@code folder_id}; {@code orderId} → {@code order_id};
     * {@code ABCId} → {@code abc_id}; empty/null → {@code "resource_id"}.
     */
    public static String idVariableName(String pathParamName) {
        if (pathParamName == null || pathParamName.isEmpty()) {
            return "resource_id";
        }
        String snake = pathParamName
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .toLowerCase(Locale.ROOT);
        if (snake.startsWith("_")) {
            snake = snake.substring(1);
        }
        if (snake.endsWith("_")) {
            snake = snake.substring(0, snake.length() - 1);
        }
        return snake.isEmpty() ? "resource_id" : snake;
    }

    /**
     * Find the POST operation that produces the value for {@code paramName}
     * when {@code consumer} is called. The answer is the producer that the
     * sequence-chain generator will prepend to a chain whose seed is
     * {@code consumer}.
     *
     * <p>Resolution rules, in order:
     * <ol>
     *   <li><b>Exact prefix match.</b> The substring of {@code consumer.path()}
     *       up to (but not including) {@code {paramName}} equals some POST
     *       operation's path. That POST is the producer.</li>
     *   <li><b>Name-stem fallback.</b> Strip a trailing {@code Id}/{@code ID}/{@code _id}
     *       from {@code paramName}, lowercase it, and match against the
     *       {@link ApiCallInfo#resourceName() resourceName} (and simple
     *       pluralizations) of any POST in the spec. First match wins.</li>
     * </ol>
     *
     * @return the producer POST, or {@code null} if none found.
     */
    public static ApiCallInfo findProducerForParam(ApiCallInfo consumer, String paramName,
                                                   List<ApiCallInfo> allCalls) {
        if (consumer == null || paramName == null || allCalls == null) {
            return null;
        }
        String consumerPath = consumer.path();
        String token = "{" + paramName + "}";
        int idx = consumerPath.indexOf(token);
        if (idx < 0) {
            return null;
        }
        String prefix = consumerPath.substring(0, idx);
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        for (ApiCallInfo c : allCalls) {
            if (c == consumer) {
                continue;
            }
            if (!"POST".equalsIgnoreCase(c.method())) {
                continue;
            }
            if (c.path().equals(prefix)) {
                return c;
            }
        }

        String stem = stripIdSuffix(paramName).toLowerCase(Locale.ROOT);
        if (stem.isEmpty()) {
            return null;
        }
        for (ApiCallInfo c : allCalls) {
            if (c == consumer) {
                continue;
            }
            if (!"POST".equalsIgnoreCase(c.method())) {
                continue;
            }
            String rn = c.resourceName() == null ? "" : c.resourceName().toLowerCase(Locale.ROOT);
            if (rn.isEmpty()) {
                continue;
            }
            if (rn.equals(stem) || rn.equals(stem + "s") || rn.equals(stem + "es")
                    || stem.equals(rn) || stem.equals(rn + "s")) {
                return c;
            }
        }
        return null;
    }

    private static String stripIdSuffix(String param) {
        if (param == null) {
            return "";
        }
        String s = param;
        if (s.length() > 3 && (s.endsWith("_id") || s.endsWith("_ID") || s.endsWith("_Id"))) {
            return s.substring(0, s.length() - 3);
        }
        if (s.length() > 2 && (s.endsWith("ID") || s.endsWith("Id"))) {
            return s.substring(0, s.length() - 2);
        }
        return s;
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
     * Build a JSON request-body string for one operation using
     * {@link IntegrationScenarioSupport#generateRequestBodyFromSchemaRaw}.
     *
     * @return JSON string, or {@code null} if the operation has no usable body
     */
    public String buildRequestBodyForOperation(Map<String, Object> operation, Map<String, Object> spec) {
        if (!operation.containsKey("requestBody")) {
            return null;
        }
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return null;
        }
        String json = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        if (json == null || json.isBlank()) {
            return null;
        }
        return json;
    }
}
