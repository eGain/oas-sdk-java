package egain.oassdk.generators.java;

import egain.oassdk.generators.common.OpenApiSchemaUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for JerseySchemaUtils schema reference matching helpers.
 */
class JerseySchemaUtilsReferenceTest {

    @Test
    @DisplayName("findComponentSchemaName matches registered schema by object identity")
    void findComponentSchemaName_byIdentity() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("type", "string", "readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Identity", identity);
        schemas.put("BasicUser", Map.of("type", "object"));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        assertEquals("Identity", JerseySchemaUtils.findComponentSchemaName(identity, spec));
        assertEquals("BasicUser", JerseySchemaUtils.findComponentSchemaName(
                Map.of("$ref", "#/components/schemas/BasicUser"), spec));
        Map<String, Object> inlinedIdentity = new LinkedHashMap<>(identity);
        inlinedIdentity.put("x-resolved-ref", "#/components/schemas/Identity");
        assertEquals("Identity", JerseySchemaUtils.findComponentSchemaName(inlinedIdentity, spec));
    }

    @Test
    @DisplayName("findComponentSchemaNameByTitle matches exact title when type and property keys align")
    void findComponentSchemaNameByTitle_matchingStructure() {
        Map<String, Object> l10n = Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of("type", "string"),
                        "displayValue", Map.of("type", "string")));
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "object");
        items.put("title", "L10NString");
        items.put("properties", Map.of(
                "value", Map.of("type", "string", "maxLength", 255),
                "displayValue", Map.of("type", "string", "readOnly", true)));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of("L10NString", l10n)));

        assertEquals("L10NString", OpenApiSchemaUtils.findComponentSchemaNameByTitle(items, spec));
    }

    @Test
    @DisplayName("findComponentSchemaNameByTitle returns null when title matches but property keys differ")
    void findComponentSchemaNameByTitle_structureMismatch() {
        Map<String, Object> user = Map.of(
                "type", "object",
                "properties", Map.of("id", Map.of("type", "string")));
        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("type", "object");
        inline.put("title", "User");
        inline.put("properties", Map.of("name", Map.of("type", "string")));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of("User", user)));

        assertNull(OpenApiSchemaUtils.findComponentSchemaNameByTitle(inline, spec));
    }

    @Test
    @DisplayName("findComponentSchemaNameByTitle returns null for blank title or case-only match")
    void findComponentSchemaNameByTitle_blankAndCaseInsensitive() {
        Map<String, Object> l10n = Map.of(
                "type", "object",
                "properties", Map.of("value", Map.of("type", "string")));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of("L10NString", l10n)));

        Map<String, Object> blank = new LinkedHashMap<>();
        blank.put("title", "  ");
        blank.put("type", "object");
        blank.put("properties", Map.of("value", Map.of("type", "string")));
        assertNull(OpenApiSchemaUtils.findComponentSchemaNameByTitle(blank, spec));

        Map<String, Object> wrongCase = new LinkedHashMap<>();
        wrongCase.put("title", "l10nstring");
        wrongCase.put("type", "object");
        wrongCase.put("properties", Map.of("value", Map.of("type", "string")));
        assertNull(OpenApiSchemaUtils.findComponentSchemaNameByTitle(wrongCase, spec));
    }

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
