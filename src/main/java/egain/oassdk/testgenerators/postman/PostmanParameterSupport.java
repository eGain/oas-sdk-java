package egain.oassdk.testgenerators.postman;

import egain.oassdk.Util;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAPI parameter defaults and path parsing for Postman generation.
 */
final class PostmanParameterSupport {

    private static final Pattern PATH_TEMPLATE = Pattern.compile("\\{([^}]+)}");

    private PostmanParameterSupport() {
    }

    static List<String> pathParameterNames(String openApiPath) {
        List<String> names = new ArrayList<>();
        if (openApiPath == null) {
            return names;
        }
        Matcher m = PATH_TEMPLATE.matcher(openApiPath);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * Build Postman raw URL and path array. Path segment values are used as-is (either "{{name}}" or literals).
     */
    static Map<String, Object> buildUrlObject(String openApiPath,
                                              Map<String, String> resolvedPathValues,
                                              List<Map<String, Object>> queryParams) {
        Map<String, Object> url = new LinkedHashMap<>();
        String safePath = openApiPath != null ? openApiPath : "";

        List<String> pathSegments = new ArrayList<>();
        pathSegments.add("");
        int cursor = 0;
        Matcher m = PATH_TEMPLATE.matcher(safePath);
        StringBuilder raw = new StringBuilder();
        raw.append("{{base_url}}");
        while (m.find()) {
            raw.append(safePath, cursor, m.start());
            String name = m.group(1);
            String value = resolvedPathValues != null && resolvedPathValues.containsKey(name)
                    ? resolvedPathValues.get(name)
                    : "{{" + name + "}}";
            raw.append(value);
            pathSegments.add(value);
            cursor = m.end();
        }
        raw.append(safePath.substring(cursor));

        url.put("raw", raw.toString());
        url.put("host", List.of("{{base_url}}"));
        url.put("path", pathSegments);

        if (queryParams != null && !queryParams.isEmpty()) {
            url.put("query", queryParams);
        }

        return url;
    }

    /**
     * Default string value for a path/query parameter for collection variables (valid happy-path value).
     */
    static String defaultValueForParameter(Map<String, Object> param) {
        if (param == null) {
            return "example";
        }
        return IntegrationScenarioSupport.getParameterExample(param);
    }

    static List<Map<String, Object>> buildPositiveQueryList(Map<String, Object> operation) {
        List<Map<String, Object>> queryParams = new ArrayList<>();
        if (!operation.containsKey("parameters")) {
            return queryParams;
        }
        List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
        if (parameters == null) {
            return queryParams;
        }
        for (Map<String, Object> param : parameters) {
            if (param != null && "query".equals(param.get("in"))) {
                Object name = param.get("name");
                if (name == null) {
                    continue;
                }
                Object description = param.get("description");
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("key", name.toString());
                q.put("value", "{{" + name + "}}");
                if (description != null) {
                    q.put("description", description.toString());
                }
                queryParams.add(q);
            }
        }
        return queryParams;
    }

    static String defaultQueryVariableValue(Map<String, Object> param) {
        return defaultValueForParameter(param);
    }

    static Map<String, Object> shallowCopyQueryEntry(Map<String, Object> entry) {
        return new LinkedHashMap<>(entry);
    }

    static String probeInvalidEnumValue() {
        return "__INVALID_ENUM_OAS_SDK__";
    }

    static String repeatChar(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
