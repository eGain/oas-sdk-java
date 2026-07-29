package egain.oassdk.testgenerators.scenario;

import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioJsonParserTest {

    @Test
    void parse_strictJson() throws Exception {
        String json = """
                {
                  "scenarioId": "EGS-1",
                  "title": "Create folder",
                  "steps": [
                    {
                      "method": "post",
                      "path": "/folders",
                      "body": {"name": "n"},
                      "expectedStatus": 201
                    }
                  ]
                }
                """;
        GeneratedScenario scenario = ScenarioJsonParser.parse(json, "EGS-1");
        assertEquals("EGS-1", scenario.getScenarioId());
        assertEquals(1, scenario.getSteps().size());
        assertEquals("POST", scenario.getSteps().get(0).getMethod());
        assertEquals(201, scenario.getSteps().get(0).getExpectedStatus());
    }

    @Test
    void parse_stripsMarkdownFence() throws Exception {
        String text = """
                ```json
                {"scenarioId":"X","title":"t","steps":[{"method":"GET","path":"/ping","expectedStatus":200}]}
                ```
                """;
        GeneratedScenario scenario = ScenarioJsonParser.parse(text, "X");
        assertEquals("GET", scenario.getSteps().get(0).getMethod());
        assertEquals("/ping", scenario.getSteps().get(0).getPath());
    }

    @Test
    void parse_rejectsMissingSteps() {
        OASSDKException ex = assertThrows(OASSDKException.class,
                () -> ScenarioJsonParser.parse("{\"scenarioId\":\"a\",\"title\":\"t\"}", "a"));
        assertTrue(ex.getMessage().contains("steps"));
    }
}

class OpenApiOperationIndexTest {

    @Test
    void fromSpec_indexesOperationsAndResolves() throws Exception {
        Map<String, Object> spec = minimalSpec();
        OpenApiOperationIndex index = OpenApiOperationIndex.fromSpec(spec);
        assertEquals(2, index.getOperations().size());
        assertTrue(index.containsMethodPath("GET", "/ping"));
        assertTrue(index.toPromptIndex().contains("/folders"));

        GeneratedScenario.ScenarioStep step = new GeneratedScenario.ScenarioStep();
        step.setMethod("POST");
        step.setOperationId("createFolder");
        step.setExpectedStatus(201);
        GeneratedScenario.ScenarioStep resolved = index.resolveStep(step, true);
        assertEquals("/folders", resolved.getPath());
        assertEquals("POST", resolved.getMethod());
    }

    @Test
    void resolveStep_failsOnUnknownWhenConfigured() {
        OpenApiOperationIndex index = OpenApiOperationIndex.fromSpec(minimalSpec());
        GeneratedScenario.ScenarioStep step = new GeneratedScenario.ScenarioStep();
        step.setMethod("DELETE");
        step.setPath("/nope");
        OASSDKException ex = assertThrows(OASSDKException.class, () -> index.resolveStep(step, true));
        assertTrue(ex.getMessage().contains("Unmapped"));
    }

    private static Map<String, Object> minimalSpec() {
        Map<String, Object> ping = new LinkedHashMap<>();
        ping.put("operationId", "ping");
        ping.put("summary", "Ping");

        Map<String, Object> create = new LinkedHashMap<>();
        create.put("operationId", "createFolder");
        create.put("summary", "Create folder");

        Map<String, Object> pingItem = new LinkedHashMap<>();
        pingItem.put("get", ping);
        Map<String, Object> foldersItem = new LinkedHashMap<>();
        foldersItem.put("post", create);

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/ping", pingItem);
        paths.put("/folders", foldersItem);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        return spec;
    }
}
