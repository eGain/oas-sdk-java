package egain.oassdk.testgenerators.common;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared OpenAPI spec metadata helpers for test generators.
 */
public final class TestSpecUtils {

    private static final Pattern SERVER_VAR = Pattern.compile("\\$\\{([^}]+)}");

    private TestSpecUtils() {
    }

    public static String getApiTitle(Map<String, Object> spec) {
        if (spec == null) {
            return "API";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "API";
        }
        Object title = info.get("title");
        return title != null ? title.toString() : "API";
    }

    public static String getBaseUrl(Map<String, Object> spec) {
        return resolveBaseUrl(spec, null);
    }

    /**
     * Resolve server URL, substituting {@code servers[].variables} from TestConfig when present.
     * Unresolved placeholders are left for runtime resolution via {@code TestEnv}.
     */
    public static String resolveBaseUrl(Map<String, Object> spec, TestConfig config) {
        if (spec == null || !spec.containsKey("servers")) {
            return defaultFromConfig(config);
        }
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers == null || servers.isEmpty()) {
            return defaultFromConfig(config);
        }
        Map<String, Object> server = servers.get(0);
        String url = (String) server.get("url");
        if (url == null || url.isBlank()) {
            return defaultFromConfig(config);
        }
        Map<String, Object> variables = Util.asStringObjectMap(server.get("variables"));
        Map<String, Object> props = config != null ? config.getAdditionalProperties() : null;

        Matcher m = SERVER_VAR.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String replacement = resolveServerVariable(varName, variables, props);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        String resolved = sb.toString();
        if (resolved.contains("${")) {
            String fromConfig = baseUrlFromConfig(props);
            if (fromConfig != null) {
                return fromConfig;
            }
        }
        return resolved;
    }

    public static boolean isEgainSpec(Map<String, Object> spec) {
        if (spec == null) {
            return false;
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info != null) {
            Object vendor = info.get("x-vendor");
            if (vendor != null && "egain".equalsIgnoreCase(vendor.toString())) {
                return true;
            }
        }
        String title = getApiTitle(spec).toLowerCase(Locale.ROOT);
        return title.contains("egain");
    }

    public static boolean useEgainAuth(TestConfig config, Map<String, Object> spec) {
        if (config != null && config.getAdditionalProperties() != null) {
            Object provider = config.getAdditionalProperties().get("auth.provider");
            if (provider != null) {
                return "egain".equalsIgnoreCase(provider.toString());
            }
        }
        return isEgainSpec(spec);
    }

    private static String defaultFromConfig(TestConfig config) {
        if (config != null && config.getAdditionalProperties() != null) {
            String fromProps = baseUrlFromConfig(config.getAdditionalProperties());
            if (fromProps != null) {
                return fromProps;
            }
        }
        return "http://localhost:8080";
    }

    private static String baseUrlFromConfig(Map<String, Object> props) {
        if (props == null) {
            return null;
        }
        for (String key : new String[]{"test.baseUrl", "schemathesis.baseUrl", "test.base.url"}) {
            Object v = props.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    private static String resolveServerVariable(String varName, Map<String, Object> variables,
                                                Map<String, Object> props) {
        if (props != null) {
            Object explicit = props.get("test.server." + varName);
            if (explicit == null) {
                explicit = props.get("test." + varName);
            }
            if (explicit != null && !explicit.toString().isBlank()) {
                return explicit.toString().trim();
            }
        }
        if (variables != null && variables.containsKey(varName)) {
            Map<String, Object> varDef = Util.asStringObjectMap(variables.get(varName));
            if (varDef != null && varDef.get("default") != null) {
                return varDef.get("default").toString();
            }
        }
        return "${" + varName + "}";
    }
}
