package egain.oassdk.testgenerators.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestCodegenSupport;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Emits JUnit 5 integration-style Java tests from {@link GeneratedScenario}.
 */
public final class ScenarioJavaTestEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NON_JAVA = Pattern.compile("[^A-Za-z0-9_]");

    private ScenarioJavaTestEmitter() {
    }

    public static String toClassName(String sourceKey, String title) {
        String base = sourceKey != null && !sourceKey.isBlank() ? sourceKey : title;
        if (base == null || base.isBlank()) {
            base = "Scenario";
        }
        String cleaned = NON_JAVA.matcher(base).replaceAll("_");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "S_" + cleaned;
        }
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '_') {
                cap = true;
                continue;
            }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        String name = sb.toString();
        if (!name.endsWith("ScenarioIT")) {
            name = name + "ScenarioIT";
        }
        return name;
    }

    public static String emit(String basePackage, String className, GeneratedScenario scenario)
            throws GenerationException {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n");
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.net.URLEncoder;\n");
        sb.append("import java.net.http.HttpClient;\n");
        sb.append("import java.net.http.HttpRequest;\n");
        sb.append("import java.net.http.HttpResponse;\n");
        sb.append("import java.nio.charset.StandardCharsets;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));
        sb.append('\n');

        sb.append("/**\n");
        sb.append(" * AI-generated scenario test");
        if (scenario.getSourceKey() != null) {
            sb.append(" from ").append(scenario.getSourceKey());
        }
        sb.append(".\n");
        if (scenario.getTitle() != null) {
            sb.append(" * ").append(escapeComment(scenario.getTitle())).append('\n');
        }
        sb.append(" */\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static HttpClient httpClient;\n\n");
        sb.append(TestCodegenSupport.baseUrlField());
        sb.append('\n');
        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = TestHttp.client();\n");
        sb.append("    }\n\n");

        List<GeneratedScenario.ScenarioStep> steps = scenario.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            GeneratedScenario.ScenarioStep step = steps.get(i);
            int order = i + 1;
            String methodName = "step" + order + "_" + safeMethodSuffix(step);
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(escapeJava(displayName(step, order))).append("\")\n");
            sb.append("    void ").append(methodName).append("() throws Exception {\n");
            sb.append(emitStepBody(step));
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String emitStepBody(GeneratedScenario.ScenarioStep step) throws GenerationException {
        StringBuilder sb = new StringBuilder();
        String method = step.getMethod() != null ? step.getMethod().toUpperCase(Locale.ROOT) : "GET";
        String path = step.getPath() != null ? step.getPath() : "/";
        sb.append("        String path = \"").append(escapeJava(path)).append("\";\n");

        Map<String, String> query = step.getQuery();
        if (query != null && !query.isEmpty()) {
            sb.append("        StringBuilder qs = new StringBuilder();\n");
            for (Map.Entry<String, String> e : query.entrySet()) {
                sb.append("        if (qs.length() > 0) qs.append('&');\n");
                sb.append("        qs.append(URLEncoder.encode(\"").append(escapeJava(e.getKey()))
                        .append("\", StandardCharsets.UTF_8)).append('=')\n");
                sb.append("          .append(URLEncoder.encode(\"").append(escapeJava(e.getValue()))
                        .append("\", StandardCharsets.UTF_8));\n");
            }
            sb.append("        String url = baseUrl() + path + \"?\" + qs;\n");
        } else {
            sb.append("        String url = baseUrl() + path;\n");
        }

        sb.append("        HttpRequest.Builder builder = HttpRequest.newBuilder()\n");
        sb.append("                .uri(URI.create(url))\n");
        sb.append("                .timeout(REQUEST_TIMEOUT)\n");
        sb.append("                .header(\"Accept\", \"application/json\")\n");

        Map<String, String> headers = step.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append("                .header(\"").append(escapeJava(e.getKey()))
                        .append("\", \"").append(escapeJava(e.getValue())).append("\")\n");
            }
        }

        boolean hasBody = step.getBody() != null && !("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method));
        if (hasBody || "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String jsonBody;
            try {
                jsonBody = step.getBody() == null ? "{}" : MAPPER.writeValueAsString(step.getBody());
            } catch (JsonProcessingException e) {
                throw new GenerationException("Failed to serialize scenario step body: " + e.getMessage(), e);
            }
            sb.append("                .header(\"Content-Type\", \"application/json\")\n");
            sb.append("                .method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(\"")
                    .append(escapeJava(jsonBody)).append("\"));\n");
        } else {
            sb.append("                .method(\"").append(method)
                    .append("\", HttpRequest.BodyPublishers.noBody());\n");
        }

        sb.append("        HttpResponse<String> response = httpClient.send(builder.build(),\n");
        sb.append("                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));\n");
        sb.append("        assertEquals(").append(step.getExpectedStatus())
                .append(", response.statusCode(), () -> \"Unexpected status: \" + response.body());\n");
        return sb.toString();
    }

    private static String displayName(GeneratedScenario.ScenarioStep step, int order) {
        return order + ". " + step.getMethod() + " " + (step.getPath() != null ? step.getPath() : step.getOperationId());
    }

    private static String safeMethodSuffix(GeneratedScenario.ScenarioStep step) {
        String raw = (step.getMethod() != null ? step.getMethod() : "step")
                + "_"
                + (step.getOperationId() != null ? step.getOperationId()
                : (step.getPath() != null ? step.getPath() : "call"));
        String cleaned = NON_JAVA.matcher(raw).replaceAll("_");
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static String escapeJava(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String escapeComment(String s) {
        return s.replace("*/", "* /");
    }
}
