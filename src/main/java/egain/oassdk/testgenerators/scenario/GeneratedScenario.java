package egain.oassdk.testgenerators.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured scenario produced by the LLM and validated against the OpenAPI spec.
 */
public final class GeneratedScenario {

    private String scenarioId;
    private String title;
    private String sourceKey;
    private List<ScenarioStep> steps = new ArrayList<>();

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public List<ScenarioStep> getSteps() {
        return steps == null ? List.of() : List.copyOf(steps);
    }

    public void setSteps(List<ScenarioStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public static final class ScenarioStep {
        private String method;
        private String path;
        private String operationId;
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, String> query = new LinkedHashMap<>();
        private Object body;
        private int expectedStatus = 200;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method != null ? method.toUpperCase(Locale.ROOT) : null;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public Map<String, String> getHeaders() {
            return headers == null ? Map.of() : Map.copyOf(headers);
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
        }

        public Map<String, String> getQuery() {
            return query == null ? Map.of() : Map.copyOf(query);
        }

        public void setQuery(Map<String, String> query) {
            this.query = query != null ? new LinkedHashMap<>(query) : new LinkedHashMap<>();
        }

        public Object getBody() {
            return body;
        }

        public void setBody(Object body) {
            this.body = body;
        }

        public int getExpectedStatus() {
            return expectedStatus;
        }

        public void setExpectedStatus(int expectedStatus) {
            this.expectedStatus = expectedStatus;
        }
    }
}
