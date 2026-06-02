package egain.oassdk.generators.java;

import egain.oassdk.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for JerseySchemaUtils static utility methods.
 */
class JerseySchemaUtilsTest {

    // -----------------------------------------------------------------------
    //  isSchemaFlagTrue
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isSchemaFlagTrue returns true for Boolean TRUE value")
    void isSchemaFlagTrue_booleanTrue() {
        Map<String, Object> schema = Map.of("readOnly", Boolean.TRUE);
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns true for string 'true'")
    void isSchemaFlagTrue_stringTrue() {
        Map<String, Object> schema = Map.of("readOnly", "true");
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false for Boolean FALSE value")
    void isSchemaFlagTrue_booleanFalse() {
        Map<String, Object> schema = Map.of("readOnly", Boolean.FALSE);
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false when key is absent")
    void isSchemaFlagTrue_absent() {
        Map<String, Object> schema = Map.of("type", "string");
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false for null schema")
    void isSchemaFlagTrue_nullSchema() {
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(null, "readOnly"));
    }

    // -----------------------------------------------------------------------
    //  getSchemaNameFromRef
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getSchemaNameFromRef extracts name from $ref")
    void getSchemaNameFromRef_dollarRef() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertEquals("User", JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef extracts name from x-resolved-ref")
    void getSchemaNameFromRef_resolvedRef() {
        Map<String, Object> schema = Map.of("x-resolved-ref", "#/components/schemas/UserView");
        assertEquals("UserView", JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null when no $ref")
    void getSchemaNameFromRef_noRef() {
        Map<String, Object> schema = Map.of("type", "string");
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null for null schema")
    void getSchemaNameFromRef_nullSchema() {
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(null));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null for non-component ref")
    void getSchemaNameFromRef_nonComponentRef() {
        Map<String, Object> schema = Map.of("$ref", "#/definitions/User");
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    // -----------------------------------------------------------------------
    //  deriveSchemaNameFromExternalRef
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef extracts name from yaml file ref")
    void deriveSchemaNameFromExternalRef_yaml() {
        assertEquals("User", JerseySchemaUtils.deriveSchemaNameFromExternalRef("./User.yaml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles path with directories")
    void deriveSchemaNameFromExternalRef_withPath() {
        assertEquals("User", JerseySchemaUtils.deriveSchemaNameFromExternalRef("models/v3/User.yaml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles yml extension")
    void deriveSchemaNameFromExternalRef_yml() {
        assertEquals("Order", JerseySchemaUtils.deriveSchemaNameFromExternalRef("Order.yml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles json extension")
    void deriveSchemaNameFromExternalRef_json() {
        assertEquals("Product", JerseySchemaUtils.deriveSchemaNameFromExternalRef("Product.json"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef returns null for null input")
    void deriveSchemaNameFromExternalRef_null() {
        assertNull(JerseySchemaUtils.deriveSchemaNameFromExternalRef(null));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef returns null for empty input")
    void deriveSchemaNameFromExternalRef_empty() {
        assertNull(JerseySchemaUtils.deriveSchemaNameFromExternalRef(""));
    }

    // -----------------------------------------------------------------------
    //  resolveRefInSchema
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveRefInSchema resolves valid $ref to component schema")
    void resolveRefInSchema_valid() {
        Map<String, Object> userSchema = new LinkedHashMap<>();
        userSchema.put("type", "object");
        userSchema.put("properties", Map.of("name", Map.of("type", "string")));

        // Build spec with mutable maps so Util.asStringObjectMap returns the original objects
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("User", userSchema);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schemas);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("components", components);

        Map<String, Object> refSchema = new LinkedHashMap<>();
        refSchema.put("$ref", "#/components/schemas/User");

        Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(refSchema, spec);
        assertEquals("object", resolved.get("type"));
        assertNotNull(resolved.get("properties"));
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when $ref target not found")
    void resolveRefInSchema_notFound() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("components", Map.of("schemas", Map.of()));

        Map<String, Object> refSchema = new LinkedHashMap<>();
        refSchema.put("$ref", "#/components/schemas/Missing");

        Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(refSchema, spec);
        assertSame(refSchema, resolved);
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when spec is null")
    void resolveRefInSchema_nullSpec() {
        Map<String, Object> refSchema = Map.of("$ref", "#/components/schemas/User");
        assertSame(refSchema, JerseySchemaUtils.resolveRefInSchema(refSchema, null));
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when no $ref")
    void resolveRefInSchema_noRef() {
        Map<String, Object> schema = Map.of("type", "string");
        Map<String, Object> spec = Map.of();
        assertSame(schema, JerseySchemaUtils.resolveRefInSchema(schema, spec));
    }

    // -----------------------------------------------------------------------
    //  resolveCompositionToEffectiveSchema
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema merges allOf schemas")
    void resolveComposition_allOf() {
        Map<String, Object> sub1 = new LinkedHashMap<>();
        sub1.put("type", "object");
        sub1.put("properties", Map.of("name", Map.of("type", "string")));

        Map<String, Object> sub2 = new LinkedHashMap<>();
        sub2.put("properties", Map.of("age", Map.of("type", "integer")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("allOf", List.of(sub1, sub2));

        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null);
        assertNotNull(effective);
        assertEquals("object", effective.get("type"));
    }

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema picks first oneOf branch")
    void resolveComposition_oneOf() {
        Map<String, Object> branch1 = new LinkedHashMap<>();
        branch1.put("type", "string");

        Map<String, Object> branch2 = new LinkedHashMap<>();
        branch2.put("type", "integer");

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("oneOf", List.of(branch1, branch2));

        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null);
        assertEquals("string", effective.get("type"));
    }

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema returns original when no composition")
    void resolveComposition_noComposition() {
        Map<String, Object> schema = Map.of("type", "string");
        assertSame(schema, JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null));
    }

    // -----------------------------------------------------------------------
    //  mergeSchemaProperties
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("mergeSchemaProperties merges direct properties")
    void mergeSchemaProperties_direct() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("properties", Map.of("name", Map.of("type", "string")));
        schema.put("required", List.of("name"));

        Map<String, Object> allProps = new LinkedHashMap<>();
        java.util.List<String> allRequired = new java.util.ArrayList<>();

        JerseySchemaUtils.mergeSchemaProperties(schema, allProps, allRequired, Map.of());

        assertTrue(allProps.containsKey("name"));
        assertTrue(allRequired.contains("name"));
    }

    @Test
    @DisplayName("mergePropertyDefinitionsForComposition keeps readOnly from earlier branch when later omits it")
    void mergePropertyDefinitions_mergeReadOnlyOverlay() {
        Map<String, Object> earlier = Map.of("readOnly", true);
        Map<String, Object> later = new LinkedHashMap<>();
        later.put("type", "string");
        later.put("pattern", "^[0-9]+$");

        Map<String, Object> merged = JerseySchemaUtils.mergePropertyDefinitionsForComposition(earlier, later);

        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(merged, "readOnly"));
        assertEquals("string", merged.get("type"));
        assertEquals("^[0-9]+$", merged.get("pattern"));
    }

    @Test
    @DisplayName("mergePropertyDefinitionsForComposition keeps writeOnly from earlier when later omits it")
    void mergePropertyDefinitions_mergeWriteOnlyOverlay() {
        Map<String, Object> earlier = Map.of("writeOnly", true);
        Map<String, Object> later = Map.of("type", "string");

        Map<String, Object> merged = JerseySchemaUtils.mergePropertyDefinitionsForComposition(earlier, later);

        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(merged, "writeOnly"));
        assertEquals("string", merged.get("type"));
    }

    @Test
    @DisplayName("mergePropertyDefinitionsForComposition later explicit readOnly false overrides earlier true")
    void mergePropertyDefinitions_laterReadOnlyFalseWins() {
        Map<String, Object> earlier = Map.of("readOnly", true);
        Map<String, Object> later = new LinkedHashMap<>();
        later.put("type", "string");
        later.put("readOnly", false);

        Map<String, Object> merged = JerseySchemaUtils.mergePropertyDefinitionsForComposition(earlier, later);

        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(merged, "readOnly"));
    }

    @Test
    @DisplayName("mergePropertyDefinitionsForComposition merges nested properties readOnly overlay")
    void mergePropertyDefinitions_nestedProperties() {
        Map<String, Object> earlier = Map.of(
                "properties", Map.of("user", Map.of("readOnly", true)));
        Map<String, Object> later = Map.of(
                "properties", Map.of("user", Map.of("type", "string")));

        Map<String, Object> merged = JerseySchemaUtils.mergePropertyDefinitionsForComposition(earlier, later);
        Map<String, Object> props = Util.asStringObjectMap(merged.get("properties"));
        assertNotNull(props);
        Map<String, Object> user = Util.asStringObjectMap(props.get("user"));
        assertNotNull(user);
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(user, "readOnly"));
        assertEquals("string", user.get("type"));
    }

    @Test
    @DisplayName("mergeSchemaProperties preserves readOnly from first allOf branch when second branch redefines property")
    void mergeSchemaProperties_allOf_preservesReadOnlyOverlay() {
        Map<String, Object> folder = new LinkedHashMap<>();
        folder.put("type", "object");
        Map<String, Object> folderProps = new LinkedHashMap<>();
        folderProps.put("id", Map.of("type", "string", "minLength", 14));
        folder.put("properties", folderProps);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Folder", folder);

        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("type", "object");
        Map<String, Object> overlayProps = new LinkedHashMap<>();
        overlayProps.put("id", Map.of("readOnly", true));
        overlay.put("properties", overlayProps);

        Map<String, Object> createFolder = new LinkedHashMap<>();
        createFolder.put("allOf", List.of(overlay, Map.of("$ref", "#/components/schemas/Folder")));

        schemas.put("CreateFolder", createFolder);

        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        Map<String, Object> allProps = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();
        JerseySchemaUtils.mergeSchemaProperties(createFolder, allProps, allRequired, spec);

        Map<String, Object> idSchema = Util.asStringObjectMap(allProps.get("id"));
        assertNotNull(idSchema);
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(idSchema, "readOnly"));
        assertEquals("string", idSchema.get("type"));
        assertEquals(14, idSchema.get("minLength"));
    }

    @Test
    @DisplayName("mergeSchemaProperties editFolder permissions keeps EditFolderPermissionsEntry not FolderPermissionsEntry")
    void mergeSchemaProperties_editFolder_permissionsUsesEditEntryType() {
        Map<String, Object> editEntryItems = Map.of("$ref", "#/components/schemas/EditFolderPermissionDetails");
        Map<String, Object> editEntryProps = Map.of(
                "identity", Map.of("$ref", "#/components/schemas/IdentityPayload"),
                "permission", Map.of("type", "array", "items", editEntryItems));
        Map<String, Object> editFolderPermissionsEntry = new LinkedHashMap<>();
        editFolderPermissionsEntry.put("type", "object");
        editFolderPermissionsEntry.put("properties", editEntryProps);

        Map<String, Object> folderEntryItems = Map.of("$ref", "#/components/schemas/FolderPermissionDetail");
        Map<String, Object> folderPermissionsEntry = new LinkedHashMap<>();
        folderPermissionsEntry.put("type", "object");
        folderPermissionsEntry.put("required", List.of("permission"));
        folderPermissionsEntry.put("properties", Map.of(
                "permission", Map.of("type", "array", "items", folderEntryItems)));

        Map<String, Object> editFolderPermissions = new LinkedHashMap<>();
        editFolderPermissions.put("type", "array");
        editFolderPermissions.put("items", Map.of("$ref", "#/components/schemas/EditFolderPermissionsEntry"));

        Map<String, Object> folderPermissions = new LinkedHashMap<>();
        folderPermissions.put("type", "array");
        folderPermissions.put("items", Map.of("$ref", "#/components/schemas/FolderPermissionsEntry"));

        Map<String, Object> folder = new LinkedHashMap<>();
        folder.put("type", "object");
        folder.put("properties", Map.of(
                "permissions", Map.of(
                        "allOf", List.of(
                                Map.of("readOnly", true),
                                Map.of("$ref", "#/components/schemas/FolderPermissions")))));

        List<Object> editPermAllOf = new ArrayList<>();
        editPermAllOf.add(Map.of(
                "description", "Folder permissions to edit",
                "writeOnly", true,
                "minItems", 1,
                "maxItems", 75));
        editPermAllOf.add(Map.of("$ref", "#/components/schemas/EditFolderPermissions"));

        Map<String, Object> editOverlay = new LinkedHashMap<>();
        editOverlay.put("type", "object");
        editOverlay.put("properties", Map.of("permissions", Map.of("allOf", editPermAllOf)));

        Map<String, Object> editFolder = new LinkedHashMap<>();
        editFolder.put("allOf", List.of(editOverlay, Map.of("$ref", "#/components/schemas/Folder")));

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("EditFolderPermissionsEntry", editFolderPermissionsEntry);
        schemas.put("FolderPermissionsEntry", folderPermissionsEntry);
        schemas.put("EditFolderPermissions", editFolderPermissions);
        schemas.put("FolderPermissions", folderPermissions);
        schemas.put("Folder", folder);
        schemas.put("editFolder", editFolder);

        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        Map<String, Object> allProps = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();
        JerseySchemaUtils.mergeSchemaProperties(editFolder, allProps, allRequired, spec);

        Map<String, Object> permissionsSchema = Util.asStringObjectMap(allProps.get("permissions"));
        assertNotNull(permissionsSchema);
        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(permissionsSchema, spec);
        assertNotNull(effective);
        assertEquals("array", effective.get("type"));
        Map<String, Object> items = Util.asStringObjectMap(effective.get("items"));
        assertNotNull(items);
        String itemRef = (String) items.get("$ref");
        if (itemRef == null) {
            itemRef = (String) items.get("x-resolved-ref");
        }
        assertNotNull(itemRef);
        assertTrue(itemRef.contains("/EditFolderPermissionsEntry"),
                "Expected EditFolderPermissionsEntry in items ref but got: " + itemRef);
        assertFalse(itemRef.contains("/FolderPermissionsEntry"),
                "Must not resolve to FolderPermissionsEntry but got: " + itemRef);
    }

    // -----------------------------------------------------------------------
    //  isSchemaReference
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isSchemaReference matches by schema name")
    void isSchemaReference_matches() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertTrue(JerseySchemaUtils.isSchemaReference(schema, "User"));
    }

    @Test
    @DisplayName("isSchemaReference returns false for different name")
    void isSchemaReference_different() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertFalse(JerseySchemaUtils.isSchemaReference(schema, "Order"));
    }

    @Test
    @DisplayName("isSchemaReference returns false for null schema")
    void isSchemaReference_null() {
        assertFalse(JerseySchemaUtils.isSchemaReference(null, "User"));
    }
}
