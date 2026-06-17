package egain.oassdk.testgenerators;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recursion-guard regression tests for {@link IntegrationScenarioSupport#generateJsonFromSchemaRaw}.
 *
 * <p>Previously the method had no recursion guard. A schema whose array `items`
 * $ref'd back to itself (e.g. {@code NodeWithChildren.children.items: $ref: NodeWithChildren})
 * caused an unbounded recursion and StackOverflowError. The fix tracks ref strings on the
 * way down and removes them on the way out so sibling subtrees can still expand fully.
 */
public class IntegrationScenarioSupportTest {

    /** Build a spec containing {@code schemas[name] = schema}. */
    private static Map<String, Object> specWithSchemas(Map<String, Object> schemas) {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schemas);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("components", components);
        return spec;
    }

    private static Map<String, Object> ref(String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("$ref", "#/components/schemas/" + name);
        return r;
    }

    @Test
    public void selfReferencingArrayItemsDoesNotRecurseForever() {
        // NodeWithChildren-style: an object whose `children` array's items $ref back to itself.
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "string"));
        Map<String, Object> children = new LinkedHashMap<>();
        children.put("type", "array");
        children.put("items", ref("NodeWithChildren"));
        properties.put("children", children);

        Map<String, Object> nodeWithChildren = new LinkedHashMap<>();
        nodeWithChildren.put("type", "object");
        nodeWithChildren.put("properties", properties);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("NodeWithChildren", nodeWithChildren);
        Map<String, Object> spec = specWithSchemas(schemas);

        String json = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> IntegrationScenarioSupport.generateJsonFromSchemaRaw(ref("NodeWithChildren"), spec));

        assertNotNull(json);
        // The top-level object must render, but the recursive children array must be cut off.
        assertTrue(json.contains("\"id\""),       "expected id field, got: " + json);
        assertTrue(json.contains("\"children\""), "expected children field, got: " + json);
        // The cycle break for an array of $ref-cycles should produce a single {} placeholder,
        // never a deeper expansion. `[{}]` is acceptable, `[{...nested children...}]` is not.
        // We assert no nested `children` literal beyond the first occurrence.
        int firstChildren = json.indexOf("\"children\"");
        int nextChildren  = json.indexOf("\"children\"", firstChildren + 1);
        assertEquals(-1, nextChildren, "children must not appear twice — recursion broken: " + json);
    }

    @Test
    public void selfReferentialSchemaViaRefAtTopLevel() {
        // Pathological direct cycle: A $refs A. Should terminate.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "object");
        Map<String, Object> aProps = new LinkedHashMap<>();
        aProps.put("self", ref("A"));
        a.put("properties", aProps);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("A", a);
        Map<String, Object> spec = specWithSchemas(schemas);

        String json = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> IntegrationScenarioSupport.generateJsonFromSchemaRaw(ref("A"), spec));

        assertNotNull(json);
        assertTrue(json.contains("\"self\""), "expected self field, got: " + json);
    }

    @Test
    public void mutuallyRecursiveSchemasTerminate() {
        // A.b -> B, B.a -> A. Either chain must break.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "object");
        a.put("properties", Map.of("b", ref("B")));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "object");
        b.put("properties", Map.of("a", ref("A")));

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("A", a);
        schemas.put("B", b);
        Map<String, Object> spec = specWithSchemas(schemas);

        String json = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> IntegrationScenarioSupport.generateJsonFromSchemaRaw(ref("A"), spec));

        assertNotNull(json);
        assertTrue(json.contains("\"b\""), "expected b field, got: " + json);
    }

    @Test
    public void siblingsReferencingSameRefBothExpand() {
        // Critical correctness check for the unwind: two sibling properties both $ref Child.
        // Without unwind, the second sibling would be cut off ({}) because the first added
        // Child to visitedRefs. With unwind, both render fully.
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "object");
        Map<String, Object> childProps = new LinkedHashMap<>();
        childProps.put("name", Map.of("type", "string"));
        child.put("properties", childProps);

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("type", "object");
        Map<String, Object> parentProps = new LinkedHashMap<>();
        parentProps.put("first",  ref("Child"));
        parentProps.put("second", ref("Child"));
        parent.put("properties", parentProps);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Parent", parent);
        schemas.put("Child", child);
        Map<String, Object> spec = specWithSchemas(schemas);

        String json = IntegrationScenarioSupport.generateJsonFromSchemaRaw(ref("Parent"), spec);

        // Both siblings must contain the child's `name` field. Count occurrences.
        int names = countOccurrences(json, "\"name\"");
        assertEquals(2, names, "both siblings must expand Child fully, got: " + json);
    }

    @Test
    public void nonRecursiveSchemaStillRendersAllProperties() {
        // Sanity check: the guard doesn't change behavior for the common case.
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id",    Map.of("type", "string"));
        props.put("count", Map.of("type", "integer"));
        props.put("ok",    Map.of("type", "boolean"));
        schema.put("properties", props);

        String json = IntegrationScenarioSupport.generateJsonFromSchemaRaw(schema, Map.of());

        for (String field : List.of("id", "count", "ok")) {
            assertTrue(json.contains("\"" + field + "\""),
                    "expected field " + field + " in: " + json);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    public void flattenObjectSchemaMergesAllOfRequiredFields() {
        Map<String, Object> folderProps = new LinkedHashMap<>();
        folderProps.put("name", Map.of("type", "string"));
        folderProps.put("description", Map.of("type", "string"));

        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("type", "object");
        overlay.put("required", List.of("name"));
        Map<String, Object> overlayProps = new LinkedHashMap<>();
        overlayProps.put("name", Map.of("type", "string"));
        overlayProps.put("department", Map.of("type", "object", "required", List.of("id"),
                "properties", Map.of("id", Map.of("type", "string"))));
        overlayProps.put("parent", Map.of("type", "object", "required", List.of("id"),
                "properties", Map.of("id", Map.of("type", "string"))));
        overlay.put("properties", overlayProps);
        overlay.put("oneOf", List.of(
                Map.of("required", List.of("department"), "properties", Map.of(
                        "department", Map.of("type", "object", "required", List.of("id"),
                                "properties", Map.of("id", Map.of("type", "string"))))),
                Map.of("required", List.of("parent"), "properties", Map.of(
                        "parent", Map.of("type", "object", "required", List.of("id"),
                                "properties", Map.of("id", Map.of("type", "string")))))
        ));

        Map<String, Object> createFolder = new LinkedHashMap<>();
        createFolder.put("allOf", List.of(overlay, Map.of("$ref", "#/components/schemas/Folder")));

        Map<String, Object> folder = new LinkedHashMap<>();
        folder.put("type", "object");
        folder.put("properties", folderProps);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Folder", folder);
        schemas.put("createFolder", createFolder);
        Map<String, Object> spec = specWithSchemas(schemas);

        IntegrationScenarioSupport.FlattenedObjectSchema flat =
                IntegrationScenarioSupport.flattenObjectSchema(createFolder, spec);

        assertTrue(flat.required().contains("name"), "name should be required");
        assertTrue(flat.properties().containsKey("name"));
        assertTrue(flat.properties().containsKey("description"));

        String json = IntegrationScenarioSupport.generateJsonFromSchemaRaw(createFolder, spec);
        assertTrue(json.contains("\"name\""), "valid body should include name: " + json);
        assertFalse(json.equals("{}"), "composed schema should not produce empty object");

        List<String> required = IntegrationScenarioSupport.getRequiredFieldsFromSchema(createFolder, spec);
        assertTrue(required.contains("name"));

        List<IntegrationScenarioSupport.PerFieldInvalidBody> invalid =
                IntegrationScenarioSupport.buildPerFieldInvalidBodies(createFolder, spec, 10);
        assertTrue(invalid.size() >= 2, "expected per-field invalid bodies, got: " + invalid.size());

        List<IntegrationScenarioSupport.OneOfVariantBody> variants =
                IntegrationScenarioSupport.buildOneOfVariantBodies(createFolder, spec);
        assertEquals(2, variants.size());
        assertTrue(variants.get(0).jsonBody().contains("department")
                || variants.get(0).jsonBody().contains("parent"));

        List<IntegrationScenarioSupport.OneOfXorNegativeBody> xorNeg =
                IntegrationScenarioSupport.buildOneOfXorNegativeBodies(createFolder, spec);
        assertEquals(2, xorNeg.size());
    }

    @Test
    public void getParameterExampleUsesArrayEnumFirstValue() {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", "folderAdditionalAttributes");
        param.put("in", "query");
        param.put("style", "form");
        param.put("explode", false);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", Map.of("type", "string", "enum", List.of("description", "permissions")));
        param.put("schema", schema);

        assertEquals("description", IntegrationScenarioSupport.getParameterExample(param));
    }

    @Test
    public void buildParamNegativeCasesIncludesArrayEnumInvalidItem() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", Map.of("type", "string", "enum", List.of("description", "permissions")));

        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", "folderAdditionalAttributes");
        param.put("in", "query");
        param.put("schema", schema);

        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getFolder");
        get.put("parameters", List.of(param));
        get.put("responses", Map.of("200", Map.of("description", "OK")));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", Map.of("/folders/{id}", Map.of("get", get)));

        List<IntegrationScenarioSupport.IntegrationParamNegativeCase> cases =
                IntegrationScenarioSupport.buildParamNegativeCases(
                        "/folders/{id}", get, Map.of(), Map.of("folderAdditionalAttributes", "description"), 25);

        boolean hasInvalidEnumItem = cases.stream()
                .anyMatch(c -> c.name.contains("invalid enum item"));
        assertTrue(hasInvalidEnumItem, "expected array enum invalid item case among: "
                + cases.stream().map(c -> c.name).toList());
    }
}
