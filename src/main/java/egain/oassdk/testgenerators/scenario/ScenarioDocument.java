package egain.oassdk.testgenerators.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scenario document fetched from an external source (e.g. Jira).
 */
public final class ScenarioDocument {

    private final String id;
    private final String key;
    private final String title;
    private final String description;
    private final List<String> labels;
    private final Map<String, Object> rawFields;

    public ScenarioDocument(String id, String key, String title, String description,
                            List<String> labels, Map<String, Object> rawFields) {
        this.id = id;
        this.key = key;
        this.title = title;
        this.description = description;
        this.labels = labels != null ? List.copyOf(labels) : List.of();
        this.rawFields = rawFields != null ? Map.copyOf(rawFields) : Map.of();
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getLabels() {
        return labels;
    }

    public Map<String, Object> getRawFields() {
        return rawFields;
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Key: ").append(key != null ? key : id).append('\n');
        sb.append("Title: ").append(title != null ? title : "").append('\n');
        if (labels != null && !labels.isEmpty()) {
            sb.append("Labels: ").append(String.join(", ", labels)).append('\n');
        }
        sb.append("Description:\n").append(description != null ? description : "").append('\n');
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String key;
        private String title;
        private String description;
        private List<String> labels = new ArrayList<>();
        private Map<String, Object> rawFields = new LinkedHashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder labels(List<String> labels) {
            this.labels = labels != null ? new ArrayList<>(labels) : new ArrayList<>();
            return this;
        }

        public Builder rawFields(Map<String, Object> rawFields) {
            this.rawFields = rawFields != null ? new LinkedHashMap<>(rawFields) : new LinkedHashMap<>();
            return this;
        }

        public ScenarioDocument build() {
            return new ScenarioDocument(id, key, title, description, labels, rawFields);
        }
    }
}
