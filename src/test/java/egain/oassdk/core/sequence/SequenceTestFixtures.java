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

    /**
     * Spec with a sub-resource POST — exercises the producer-index / prefix-chain paths.
     * <ul>
     *   <li>{@code POST /orders} — {@code createOrder}</li>
     *   <li>{@code GET /orders/{orderId}} — {@code getOrder}</li>
     *   <li>{@code POST /orders/{orderId}/items} — {@code addItem} (sub-resource creator)</li>
     *   <li>{@code GET /orders/{orderId}/items/{itemId}} — {@code getItem}</li>
     *   <li>{@code DELETE /orders/{orderId}/items/{itemId}} — {@code deleteItem}</li>
     * </ul>
     */
    public static Map<String, Object> orderWithItemsSpec() {
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> ordersPath = new LinkedHashMap<>();
        ordersPath.put("post", operation("createOrder"));
        paths.put("/orders", ordersPath);

        Map<String, Object> orderByIdPath = new LinkedHashMap<>();
        orderByIdPath.put("get", operation("getOrder"));
        paths.put("/orders/{orderId}", orderByIdPath);

        Map<String, Object> itemsPath = new LinkedHashMap<>();
        itemsPath.put("post", operation("addItem"));
        paths.put("/orders/{orderId}/items", itemsPath);

        Map<String, Object> itemByIdPath = new LinkedHashMap<>();
        itemByIdPath.put("get", operation("getItem"));
        itemByIdPath.put("delete", operation("deleteItem"));
        paths.put("/orders/{orderId}/items/{itemId}", itemByIdPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        return spec;
    }

    /**
     * Spec with two top-level creators on the same resource group — exercises
     * the seed-each-POST rule for alternative creators.
     * <ul>
     *   <li>{@code POST /users} — {@code createUser}</li>
     *   <li>{@code POST /users/bulk} — {@code bulkCreateUsers} (rightmost segment
     *       {@code "bulk"}, so different resourceName but both creators)</li>
     * </ul>
     */
    public static Map<String, Object> alternativeCreatorsSpec() {
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> usersPath = new LinkedHashMap<>();
        usersPath.put("post", operation("createUser"));
        paths.put("/users", usersPath);

        Map<String, Object> bulkPath = new LinkedHashMap<>();
        bulkPath.put("post", operation("bulkCreateUsers"));
        paths.put("/users/bulk", bulkPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        return spec;
    }

    /**
     * Two-level nested resources — exercises recursive prefix chaining.
     * <ul>
     *   <li>{@code POST /a}</li>
     *   <li>{@code POST /a/{aId}/b}</li>
     *   <li>{@code POST /a/{aId}/b/{bId}/c}</li>
     * </ul>
     */
    public static Map<String, Object> twoLevelNestedSpec() {
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> aPath = new LinkedHashMap<>();
        aPath.put("post", operation("createA"));
        paths.put("/a", aPath);

        Map<String, Object> bPath = new LinkedHashMap<>();
        bPath.put("post", operation("createB"));
        paths.put("/a/{aId}/b", bPath);

        Map<String, Object> cPath = new LinkedHashMap<>();
        cPath.put("post", operation("createC"));
        paths.put("/a/{aId}/b/{bId}/c", cPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        return spec;
    }

    /**
     * Sub-resource POST with no top-level creator that can resolve its path param —
     * exercises the UnresolvedParamPolicy branch.
     * <ul>
     *   <li>{@code POST /orphans/{parentId}/children}</li>
     * </ul>
     */
    public static Map<String, Object> unresolvedParamSpec() {
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> orphansChildrenPath = new LinkedHashMap<>();
        orphansChildrenPath.put("post", operation("createChild"));
        paths.put("/orphans/{parentId}/children", orphansChildrenPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
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
