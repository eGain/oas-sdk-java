package egain.oassdk.core.sequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared OpenAPI fixtures used by tests across the {@code core.sequence}
 * and {@code testgenerators.sequence} packages.
 */
public final class SequenceTestFixtures {

    private SequenceTestFixtures() {
    }

    /**
     * Minimal folder-style OpenAPI map with:
     * <ul>
     *   <li>{@code POST /folders} — {@code createFolder}, body {@code FolderCreate}</li>
     *   <li>{@code GET /folders/{folderID}} — {@code getFolder} with enum + numeric query params</li>
     * </ul>
     */
    public static Map<String, Object> minimalFolderSpec() {
        Map<String, Object> folderSchema = new LinkedHashMap<>();
        folderSchema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        props.put("name", nameProp);
        folderSchema.put("properties", props);

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", Map.of("FolderCreate", folderSchema));

        Map<String, Object> postOp = new LinkedHashMap<>();
        postOp.put("operationId", "createFolder");
        Map<String, Object> rb = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schema", Map.of("$ref", "#/components/schemas/FolderCreate"));
        content.put("application/json", json);
        rb.put("content", content);
        postOp.put("requestBody", rb);

        Map<String, Object> kbLangParam = new LinkedHashMap<>();
        kbLangParam.put("name", "kbLanguage");
        kbLangParam.put("in", "query");
        kbLangParam.put("required", true);
        kbLangParam.put("schema", Map.of("type", "string", "enum", List.of("en-US", "fr-FR")));

        Map<String, Object> levelParam = new LinkedHashMap<>();
        levelParam.put("name", "$level");
        levelParam.put("in", "query");
        levelParam.put("required", false);
        levelParam.put("schema", Map.of("type", "integer", "minimum", 0));

        Map<String, Object> getOp = new LinkedHashMap<>();
        getOp.put("operationId", "getFolder");
        getOp.put("parameters", List.of(kbLangParam, levelParam));

        Map<String, Object> foldersPath = new LinkedHashMap<>();
        foldersPath.put("post", postOp);

        Map<String, Object> folderIdPath = new LinkedHashMap<>();
        folderIdPath.put("get", getOp);

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/folders", foldersPath);
        paths.put("/folders/{folderID}", folderIdPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        spec.put("components", components);
        return spec;
    }

    /**
     * Folder spec extended with {@code PUT} edit, {@code PATCH}, and
     * {@code DELETE} by id. Used to exercise the enumerator's
     * delete-last rule and fuller permutation counts.
     */
    public static Map<String, Object> folderSpecWithCrud() {
        Map<String, Object> spec = minimalFolderSpec();

        Map<String, Object> paths = castPaths(spec);
        Map<String, Object> folderIdPath = castItem(paths.get("/folders/{folderID}"));

        folderIdPath.put("put", operation("editFolder"));
        folderIdPath.put("patch", operation("patchFolder"));
        folderIdPath.put("delete", operation("deleteFolder"));
        return spec;
    }

    private static Map<String, Object> operation(String operationId) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("operationId", operationId);
        return op;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castPaths(Map<String, Object> spec) {
        return (Map<String, Object>) spec.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castItem(Object o) {
        return (Map<String, Object>) o;
    }
}
