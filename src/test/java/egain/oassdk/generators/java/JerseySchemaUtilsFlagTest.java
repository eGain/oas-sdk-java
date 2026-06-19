package egain.oassdk.generators.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Unit tests for JerseySchemaUtils.isSchemaFlagTrue.
 */
class JerseySchemaUtilsFlagTest {

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
}
