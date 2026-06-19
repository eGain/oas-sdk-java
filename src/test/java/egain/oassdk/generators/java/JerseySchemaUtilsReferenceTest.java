package egain.oassdk.generators.java;

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
