package egain.oassdk.testsupport.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes {@code auth.chain.N.*} steps from test properties. Copied into generated test-support.
 */
public final class AuthChainExecutor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public interface PropertySource {
        String get(String key, String defaultValue);

        /** Keys that start with {@code prefix} (for discovering auth.chain.N.header.*). */
        Iterable<String> keysWithPrefix(String prefix);
    }

    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    private AuthChainExecutor() {
    }

    public static String run(PropertySource props, HttpClient client) {
        return run(props, request -> client.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    public static String run(PropertySource props, HttpSender sender) {
        Map<String, String> vars = new LinkedHashMap<>();
        int step = 1;
        while (true) {
            String urlKey = chainKey(step, "url");
            String urlTemplate = props.get(urlKey, "");
            if (urlTemplate == null || urlTemplate.isBlank()) {
                break;
            }
            String url = substitute(urlTemplate, props, vars);
            String method = props.get(chainKey(step, "method"), "POST").trim().toUpperCase(Locale.ROOT);
            String bodyTemplate = props.get(chainKey(step, "body"), "");
            String body = bodyTemplate == null || bodyTemplate.isBlank()
                    ? null
                    : substitute(bodyTemplate, props, vars);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            for (Map.Entry<String, String> header : chainHeaders(props, step).entrySet()) {
                builder.header(header.getKey(), substitute(header.getValue(), props, vars));
            }
            HttpRequest request = buildRequest(builder, method, body);
            try {
                HttpResponse<String> response = sender.send(request);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("AuthChain: step " + step + " failed HTTP " + response.statusCode()
                            + " url=" + url);
                    return null;
                }
                String extracted = extract(props, step, response);
                if (extracted == null || extracted.isBlank()) {
                    System.err.println("AuthChain: step " + step + " could not extract token/session");
                    return null;
                }
                String saveAs = props.get(chainKey(step, "save"), defaultSaveName(props, step));
                if (saveAs != null && !saveAs.isBlank()) {
                    vars.put(saveAs.trim(), extracted);
                }
            } catch (Exception e) {
                System.err.println("AuthChain: step " + step + " error: " + e.getMessage());
                return null;
            }
            step++;
        }
        if (vars.isEmpty()) {
            return null;
        }
        String finalVar = props.get("auth.chain.final", "");
        if (finalVar != null && !finalVar.isBlank()) {
            return vars.get(finalVar.trim());
        }
        String last = null;
        for (String v : vars.values()) {
            last = v;
        }
        return last;
    }

    public static String substitute(String template, PropertySource props, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String value = resolveVar(name, props, vars);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String resolveVar(String name, PropertySource props, Map<String, String> vars) {
        if (vars.containsKey(name)) {
            return vars.get(name);
        }
        if (name.startsWith("auth.")) {
            return props.get(name, "");
        }
        return props.get("auth." + name, props.get(name, ""));
    }

    static Map<String, String> chainHeaders(PropertySource props, int step) {
        String prefix = "auth.chain." + step + ".header.";
        Map<String, String> headers = new LinkedHashMap<>();
        for (String key : props.keysWithPrefix(prefix)) {
            String headerName = key.substring(prefix.length());
            if (!headerName.isBlank()) {
                headers.put(headerName, props.get(key, ""));
            }
        }
        return headers;
    }

    private static HttpRequest buildRequest(HttpRequest.Builder builder, String method, String body) {
        if ("GET".equals(method)) {
            return builder.GET().build();
        }
        if ("DELETE".equals(method)) {
            return builder.DELETE().build();
        }
        if ("PUT".equals(method)) {
            return body == null
                    ? builder.PUT(HttpRequest.BodyPublishers.noBody()).build()
                    : builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        }
        if ("PATCH".equals(method)) {
            return body == null
                    ? builder.method("PATCH", HttpRequest.BodyPublishers.noBody()).build()
                    : builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
        }
        return body == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                : builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static String extract(PropertySource props, int step, HttpResponse<String> response) {
        String headerName = props.get(chainKey(step, "extract.header"), "");
        if (headerName != null && !headerName.isBlank()) {
            Optional<String> header = response.headers().firstValue(headerName);
            if (header.isEmpty()) {
                for (Map.Entry<String, java.util.List<String>> e : response.headers().map().entrySet()) {
                    if (e.getKey().equalsIgnoreCase(headerName.trim())) {
                        if (!e.getValue().isEmpty()) {
                            return e.getValue().getFirst();
                        }
                    }
                }
                return null;
            }
            return header.get();
        }
        String jsonField = props.get(chainKey(step, "extract.json"), "");
        if (jsonField != null && !jsonField.isBlank()) {
            return extractJsonString(response.body(), jsonField.trim());
        }
        return null;
    }

    private static String defaultSaveName(PropertySource props, int step) {
        String jsonField = props.get(chainKey(step, "extract.json"), "");
        if (jsonField != null && !jsonField.isBlank()) {
            return jsonField.trim();
        }
        String headerName = props.get(chainKey(step, "extract.header"), "");
        if (headerName != null && !headerName.isBlank()) {
            return headerName.trim().replace('-', '_');
        }
        return "step" + step;
    }

    static String extractJsonString(String json, String field) {
        if (json == null || field == null || field.isBlank()) {
            return null;
        }
        String needle = "\"" + field + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static String chainKey(int step, String suffix) {
        return "auth.chain." + step + "." + suffix;
    }
}
