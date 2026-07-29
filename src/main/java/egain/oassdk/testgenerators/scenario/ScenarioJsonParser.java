package egain.oassdk.testgenerators.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM completion text into {@link GeneratedScenario} (strict JSON, optional markdown fences).
 */
public final class ScenarioJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private ScenarioJsonParser() {
    }

    public static GeneratedScenario parse(String llmText, String sourceKey) throws OASSDKException {
        if (llmText == null || llmText.isBlank()) {
            throw new OASSDKException("LLM returned empty scenario JSON");
        }
        String json = extractJson(llmText.trim());
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                if (root.isEmpty()) {
                    throw new OASSDKException("LLM returned an empty scenario array");
                }
                root = root.get(0);
            }
            GeneratedScenario scenario = new GeneratedScenario();
            scenario.setSourceKey(sourceKey);
            scenario.setScenarioId(textOr(root, "scenarioId", sourceKey));
            scenario.setTitle(textOr(root, "title", sourceKey));
            List<GeneratedScenario.ScenarioStep> steps = new ArrayList<>();
            JsonNode stepsNode = root.get("steps");
            if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
                throw new OASSDKException("LLM scenario JSON must include a non-empty 'steps' array");
            }
            for (JsonNode stepNode : stepsNode) {
                steps.add(parseStep(stepNode));
            }
            scenario.setSteps(steps);
            return scenario;
        } catch (IOException e) {
            throw new OASSDKException("Invalid scenario JSON from LLM: " + e.getMessage(), e);
        }
    }

    private static GeneratedScenario.ScenarioStep parseStep(JsonNode stepNode) throws OASSDKException {
        GeneratedScenario.ScenarioStep step = new GeneratedScenario.ScenarioStep();
        String method = textOr(stepNode, "method", null);
        if (method == null || method.isBlank()) {
            throw new OASSDKException("Scenario step missing 'method'");
        }
        step.setMethod(method);
        step.setPath(textOr(stepNode, "path", null));
        step.setOperationId(textOr(stepNode, "operationId", null));
        if ((step.getPath() == null || step.getPath().isBlank())
                && (step.getOperationId() == null || step.getOperationId().isBlank())) {
            throw new OASSDKException("Scenario step must include 'path' or 'operationId'");
        }
        step.setHeaders(stringMap(stepNode.get("headers")));
        step.setQuery(stringMap(stepNode.get("query")));
        if (stepNode.has("body") && !stepNode.get("body").isNull()) {
            step.setBody(MAPPER.convertValue(stepNode.get("body"), Object.class));
        }
        if (stepNode.has("expectedStatus") && stepNode.get("expectedStatus").canConvertToInt()) {
            step.setExpectedStatus(stepNode.get("expectedStatus").asInt());
        } else {
            step.setExpectedStatus(200);
        }
        return step;
    }

    private static Map<String, String> stringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            if (e.getValue() != null && !e.getValue().isNull()) {
                map.put(e.getKey(), e.getValue().asText());
            }
        }
        return map;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return fallback;
    }

    static String extractJson(String text) {
        Matcher m = FENCE.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        int aStart = text.indexOf('[');
        int aEnd = text.lastIndexOf(']');
        if (aStart >= 0 && aEnd > aStart) {
            return text.substring(aStart, aEnd + 1);
        }
        return text;
    }

    public static String systemPrompt() {
        return """
                You convert acceptance scenarios into API test steps for an OpenAPI-described service.
                Respond with ONLY valid JSON (no markdown) matching this schema:
                {
                  "scenarioId": "string",
                  "title": "string",
                  "steps": [
                    {
                      "method": "GET|POST|PUT|PATCH|DELETE",
                      "path": "/path/from/openapi",
                      "operationId": "optionalOperationId",
                      "headers": {"Header-Name": "value"},
                      "query": {"param": "value"},
                      "body": {},
                      "expectedStatus": 200
                    }
                  ]
                }
                Use only operations listed in the OpenAPI index. Prefer concrete paths from the index.
                """.trim();
    }

    public static String userPrompt(ScenarioDocument document, OpenApiOperationIndex index) {
        return "Scenario from source:\n"
                + document.toPromptText()
                + "\nOpenAPI operations:\n"
                + index.toPromptIndex()
                + "\nProduce the JSON test scenario now.";
    }
}
