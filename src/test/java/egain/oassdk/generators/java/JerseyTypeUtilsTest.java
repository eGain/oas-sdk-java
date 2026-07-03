package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for JerseyTypeUtils.
 */
class JerseyTypeUtilsTest {

    /**
     * Create a JerseyTypeUtils backed by a minimal spec for testing.
     */
    private JerseyTypeUtils createTypeUtils(Map<String, Object> spec) {
        JerseyGenerationContext ctx = new JerseyGenerationContext(
                spec != null ? spec : Map.of(), null, null, null);
        return new JerseyTypeUtils(ctx);
    }

    private JerseyTypeUtils createTypeUtils() {
        return createTypeUtils(null);
    }

    private JerseyTypeUtils createTypeUtils(boolean useBoxedPrimitives) {
        GeneratorConfig config = GeneratorConfig.builder().useBoxedPrimitives(useBoxedPrimitives).build();
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), null, config, null);
        return new JerseyTypeUtils(ctx);
    }

    // -----------------------------------------------------------------------
    //  getJavaType - simple types
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType returns String for type: string")
    void getJavaType_string() {
        Map<String, Object> schema = Map.of("type", "string");
        assertEquals("String", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns int for type: integer (no default, not nullable)")
    void getJavaType_integer() {
        Map<String, Object> schema = Map.of("type", "integer");
        assertEquals("int", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Integer for type: integer with default")
    void getJavaType_integerWithDefault() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("default", 0);
        assertEquals("Integer", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns long for type: integer, format: int64")
    void getJavaType_long() {
        Map<String, Object> schema = Map.of("type", "integer", "format", "int64");
        assertEquals("long", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Long for type: integer, format: int64 with default")
    void getJavaType_longWithDefault() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("format", "int64");
        schema.put("default", 0);
        assertEquals("Long", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Boolean for type: boolean")
    void getJavaType_boolean() {
        Map<String, Object> schema = Map.of("type", "boolean");
        assertEquals("Boolean", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns double for type: number (no default, not nullable)")
    void getJavaType_number() {
        Map<String, Object> schema = Map.of("type", "number");
        assertEquals("double", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Double for type: number with default")
    void getJavaType_numberWithDefault() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "number");
        schema.put("default", 0.0);
        assertEquals("Double", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Object for null schema")
    void getJavaType_null() {
        assertEquals("Object", createTypeUtils().getJavaType(null));
    }

    // -----------------------------------------------------------------------
    //  getJavaType - array type
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType returns List<String> for string array")
    void getJavaType_stringArray() {
        Map<String, Object> items = Map.of("type", "string");
        Map<String, Object> schema = Map.of("type", "array", "items", items);
        assertEquals("List<String>", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns List<Object> for array without items")
    void getJavaType_arrayNoItems() {
        Map<String, Object> schema = Map.of("type", "array");
        assertEquals("List<Object>", createTypeUtils().getJavaType(schema));
    }

    // -----------------------------------------------------------------------
    //  getJavaType - $ref
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType resolves $ref to class name")
    void getJavaType_ref() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertEquals("User", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType resolves array items $ref")
    void getJavaType_arrayRef() {
        Map<String, Object> items = Map.of("$ref", "#/components/schemas/User");
        Map<String, Object> schema = Map.of("type", "array", "items", items);
        assertEquals("List<User>", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType uses x-resolved-ref named schema for inlined oneOf union (IdentityPayload)")
    void getJavaType_oneOfWithXResolvedRef_returnsComponentModelType() {
        Map<String, Object> userBranch = new LinkedHashMap<>();
        userBranch.put("type", "object");
        userBranch.put("properties", Map.of("user", Map.of("$ref", "#/components/schemas/Identity")));
        Map<String, Object> groupBranch = new LinkedHashMap<>();
        groupBranch.put("type", "object");
        groupBranch.put("properties", Map.of("group", Map.of("$ref", "#/components/schemas/GroupIdentity")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("description", "The subject receiving the permissions.");
        schema.put("oneOf", List.of(userBranch, groupBranch));
        schema.put("x-resolved-ref", "#/components/schemas/IdentityPayload");
        assertEquals("IdentityPayload", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getFieldTypeForModelProperty returns IdentityPayload for inlined oneOf + x-resolved-ref")
    void getFieldTypeForModelProperty_oneOfWithXResolvedRef() {
        Map<String, Object> userBranch = new LinkedHashMap<>();
        userBranch.put("type", "object");
        userBranch.put("properties", Map.of("user", Map.of("$ref", "#/components/schemas/Identity")));
        Map<String, Object> groupBranch = new LinkedHashMap<>();
        groupBranch.put("type", "object");
        groupBranch.put("properties", Map.of("group", Map.of("$ref", "#/components/schemas/GroupIdentity")));
        Map<String, Object> fieldSchema = new LinkedHashMap<>();
        fieldSchema.put("oneOf", List.of(userBranch, groupBranch));
        fieldSchema.put("x-resolved-ref", "#/components/schemas/IdentityPayload");
        JerseyTypeUtils typeUtils = createTypeUtils(Map.of());
        assertEquals("IdentityPayload",
                typeUtils.getFieldTypeForModelProperty("EditFolderPermissionsEntry", "identity", fieldSchema, false, Map.of()));
    }

    // -----------------------------------------------------------------------
    //  getJavaType - date types
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType returns Identity for Identity allOf overlay registered in spec")
    void getJavaType_identityAllOfOverlay_returnsIdentityNotBasicUser() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("type", "string", "readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Identity", identity);
        schemas.put("BasicUser", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string", "readOnly", true))));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        assertEquals("Identity", createTypeUtils(spec).getJavaType(identity));
    }

    @Test
    @DisplayName("computeFieldTypeForProperty returns Identity for registered Identity allOf schema")
    void computeFieldTypeForProperty_identityAllOf_returnsIdentity() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("type", "string", "readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Identity", identity);
        schemas.put("BasicUser", Map.of("type", "object"));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        JerseyTypeUtils typeUtils = createTypeUtils(spec);
        assertEquals("Identity",
                typeUtils.computeFieldTypeForProperty("user", identity, false, spec));
    }

    @Test
    @DisplayName("isInlineObjectProperty is true for unregistered inline allOf overlay with BasicUser ref")
    void isInlineObjectProperty_inlineAllOfOverlay_returnsTrue() {
        Map<String, Object> userSchema = new LinkedHashMap<>();
        userSchema.put("type", "object");
        userSchema.put("required", List.of("id"));
        userSchema.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of(
                "BasicUser", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string"))))));

        assertTrue(createTypeUtils(spec).isInlineObjectProperty(userSchema, spec));
    }

    @Test
    @DisplayName("isInlineObjectProperty is true for type object + properties + ref-only allOf")
    void isInlineObjectProperty_envelopePropertiesWithRefOnlyAllOf_returnsTrue() {
        Map<String, Object> inlineSchema = new LinkedHashMap<>();
        inlineSchema.put("type", "object");
        inlineSchema.put("properties", Map.of("extraField", Map.of("type", "string")));
        inlineSchema.put("allOf", List.of(Map.of("$ref", "#/components/schemas/Base")));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of(
                "Base", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string"))))));

        assertTrue(createTypeUtils(spec).isInlineObjectProperty(inlineSchema, spec));
    }

    @Test
    @DisplayName("isInlineObjectProperty is false for $ref to registered Identity component")
    void isInlineObjectProperty_refToIdentity_returnsFalse() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("type", "string", "readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Identity", identity);
        schemas.put("BasicUser", Map.of("type", "object"));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", schemas));

        assertFalse(createTypeUtils(spec).isInlineObjectProperty(
                Map.of("$ref", "#/components/schemas/Identity"), spec));
    }

    @Test
    @DisplayName("getFieldTypeForModelProperty returns IdentityPayload.User for inline allOf overlay property")
    void getFieldTypeForModelProperty_inlineAllOfOverlay_returnsInnerClassType() {
        Map<String, Object> userSchema = new LinkedHashMap<>();
        userSchema.put("type", "object");
        userSchema.put("required", List.of("id"));
        userSchema.put("allOf", List.of(
                Map.of("properties", Map.of("id", Map.of("readOnly", false))),
                Map.of("$ref", "#/components/schemas/BasicUser")));
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of(
                "BasicUser", Map.of("type", "object"))));

        JerseyTypeUtils typeUtils = createTypeUtils(spec);
        assertEquals("IdentityPayload.User",
                typeUtils.getFieldTypeForModelProperty("IdentityPayload", "user", userSchema, false, spec));
    }

    @Test
    @DisplayName("getJavaType returns XMLGregorianCalendar for date format")
    void getJavaType_date() {
        Map<String, Object> schema = Map.of("type", "string", "format", "date");
        assertEquals("XMLGregorianCalendar", createTypeUtils().getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns XMLGregorianCalendar for date-time format")
    void getJavaType_dateTime() {
        Map<String, Object> schema = Map.of("type", "string", "format", "date-time");
        assertEquals("XMLGregorianCalendar", createTypeUtils().getJavaType(schema));
    }

    // -----------------------------------------------------------------------
    //  getJavaType - float
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType returns float for number with float format")
    void getJavaType_float() {
        Map<String, Object> schema = Map.of("type", "number", "format", "float");
        assertEquals("float", createTypeUtils().getJavaType(schema));
    }

    // -----------------------------------------------------------------------
    //  getJavaType - useBoxedPrimitives
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getJavaType returns Integer when useBoxedPrimitives is true")
    void getJavaType_integer_boxed() {
        Map<String, Object> schema = Map.of("type", "integer");
        assertEquals("Integer", createTypeUtils(true).getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Long when useBoxedPrimitives is true for int64")
    void getJavaType_long_boxed() {
        Map<String, Object> schema = Map.of("type", "integer", "format", "int64");
        assertEquals("Long", createTypeUtils(true).getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Double when useBoxedPrimitives is true")
    void getJavaType_number_boxed() {
        Map<String, Object> schema = Map.of("type", "number");
        assertEquals("Double", createTypeUtils(true).getJavaType(schema));
    }

    @Test
    @DisplayName("getJavaType returns Float when useBoxedPrimitives is true for float format")
    void getJavaType_float_boxed() {
        Map<String, Object> schema = Map.of("type", "number", "format", "float");
        assertEquals("Float", createTypeUtils(true).getJavaType(schema));
    }

    // -----------------------------------------------------------------------
    //  isJavaPrimitiveOrBoxed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isJavaPrimitiveOrBoxed returns true for int")
    void isJavaPrimitiveOrBoxed_int() {
        assertTrue(createTypeUtils().isJavaPrimitiveOrBoxed("int"));
    }

    @Test
    @DisplayName("isJavaPrimitiveOrBoxed returns true for String")
    void isJavaPrimitiveOrBoxed_String() {
        assertTrue(createTypeUtils().isJavaPrimitiveOrBoxed("String"));
    }

    @Test
    @DisplayName("isJavaPrimitiveOrBoxed returns false for custom type")
    void isJavaPrimitiveOrBoxed_custom() {
        assertFalse(createTypeUtils().isJavaPrimitiveOrBoxed("User"));
    }

    @Test
    @DisplayName("isJavaPrimitiveOrBoxed returns true for Boolean")
    void isJavaPrimitiveOrBoxed_Boolean() {
        assertTrue(createTypeUtils().isJavaPrimitiveOrBoxed("Boolean"));
    }

    @Test
    @DisplayName("isJavaPrimitiveOrBoxed returns false for List")
    void isJavaPrimitiveOrBoxed_List() {
        assertFalse(createTypeUtils().isJavaPrimitiveOrBoxed("List"));
    }

    // -----------------------------------------------------------------------
    //  isErrorSchema
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isErrorSchema returns true for Error")
    void isErrorSchema_error() {
        assertTrue(createTypeUtils().isErrorSchema("Error"));
    }

    @Test
    @DisplayName("isErrorSchema returns true for ApiException")
    void isErrorSchema_exception() {
        assertTrue(createTypeUtils().isErrorSchema("ApiException"));
    }

    @Test
    @DisplayName("isErrorSchema returns true for ServiceFault")
    void isErrorSchema_fault() {
        assertTrue(createTypeUtils().isErrorSchema("ServiceFault"));
    }

    @Test
    @DisplayName("isErrorSchema returns false for User")
    void isErrorSchema_user() {
        assertFalse(createTypeUtils().isErrorSchema("User"));
    }

    @Test
    @DisplayName("isErrorSchema returns false for null")
    void isErrorSchema_null() {
        assertFalse(createTypeUtils().isErrorSchema(null));
    }

    // -----------------------------------------------------------------------
    //  isJavaPrimitiveType (static)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isJavaPrimitiveType returns true for int")
    void isJavaPrimitiveType_int() {
        assertTrue(JerseyTypeUtils.isJavaPrimitiveType("int"));
    }

    @Test
    @DisplayName("isJavaPrimitiveType returns false for Integer")
    void isJavaPrimitiveType_Integer() {
        assertFalse(JerseyTypeUtils.isJavaPrimitiveType("Integer"));
    }

    // -----------------------------------------------------------------------
    //  isEligibleForCascadingValidation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isEligibleForCascadingValidation returns false for String")
    void isEligibleForCascadingValidation_string() {
        assertFalse(createTypeUtils().isEligibleForCascadingValidation("String"));
    }

    @Test
    @DisplayName("isEligibleForCascadingValidation returns true for custom type")
    void isEligibleForCascadingValidation_custom() {
        assertTrue(createTypeUtils().isEligibleForCascadingValidation("User"));
    }

    @Test
    @DisplayName("isEligibleForCascadingValidation returns true for List<User>")
    void isEligibleForCascadingValidation_listCustom() {
        assertTrue(createTypeUtils().isEligibleForCascadingValidation("List<User>"));
    }

    @Test
    @DisplayName("isEligibleForCascadingValidation returns false for List<String>")
    void isEligibleForCascadingValidation_listString() {
        assertFalse(createTypeUtils().isEligibleForCascadingValidation("List<String>"));
    }

    // -----------------------------------------------------------------------
    //  generateValidationAnnotations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateValidationAnnotations adds @NotNull for required field")
    void generateValidationAnnotations_required() {
        Map<String, Object> schema = Map.of("type", "string");
        String annotations = createTypeUtils().generateValidationAnnotations(schema, true);
        assertTrue(annotations.contains("@NotNull"));
    }

    @Test
    @DisplayName("generateValidationAnnotations adds @Size for minLength/maxLength")
    void generateValidationAnnotations_size() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("minLength", 1);
        schema.put("maxLength", 100);
        String annotations = createTypeUtils().generateValidationAnnotations(schema, false);
        assertTrue(annotations.contains("@Size(min = 1, max = 100)"));
    }

    @Test
    @DisplayName("generateValidationAnnotations adds @Min for integer minimum")
    void generateValidationAnnotations_min() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("minimum", 0);
        String annotations = createTypeUtils().generateValidationAnnotations(schema, false);
        assertTrue(annotations.contains("@Min(value = 0)"));
    }

    @Test
    @DisplayName("generateValidationAnnotations skips CharSequence constraints for format date-time (maps to XMLGregorianCalendar)")
    void generateValidationAnnotations_dateTime_skipsPatternAndSize() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("format", "date-time");
        schema.put("pattern", "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        schema.put("minLength", 20);
        schema.put("maxLength", 24);
        String annotations = createTypeUtils().generateValidationAnnotations(schema, false);
        assertFalse(annotations.contains("@Pattern"));
        assertFalse(annotations.contains("@Size"));
        assertFalse(annotations.contains("@NotNull"));
    }

    @Test
    @DisplayName("generateValidationAnnotations for format date-time required adds only @NotNull")
    void generateValidationAnnotations_dateTime_required() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("format", "date-time");
        schema.put("pattern", "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        schema.put("minLength", 20);
        schema.put("maxLength", 24);
        String annotations = createTypeUtils().generateValidationAnnotations(schema, true);
        assertTrue(annotations.contains("@NotNull"));
        assertFalse(annotations.contains("@Pattern"));
        assertFalse(annotations.contains("@Size"));
    }

    @Test
    @DisplayName("isEligibleForCascadingValidation returns false for XMLGregorianCalendar")
    void isEligibleForCascadingValidation_xmlGregorianCalendar() {
        assertFalse(createTypeUtils().isEligibleForCascadingValidation("XMLGregorianCalendar"));
    }

    @Test
    @DisplayName("isEligibleForCascadingValidation returns false for List of XMLGregorianCalendar")
    void isEligibleForCascadingValidation_listOfXmlGregorianCalendar() {
        assertFalse(createTypeUtils().isEligibleForCascadingValidation("List<XMLGregorianCalendar>"));
    }
}
