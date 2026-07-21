package egain.oassdk.core.validator;

import egain.oassdk.core.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Comprehensive test cases for OASValidator
 */
public class OASValidatorTest {
    
    private OASValidator validator;
    
    @BeforeEach
    public void setUp() {
        validator = new OASValidator();
    }
    
    @Test
    public void testValidatorInitialization() {
        assertNotNull(validator);
    }
    
    @Test
    public void testValidateValidOpenAPISpec() {
        Map<String, Object> spec = createValidOpenAPISpec();
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingOpenAPIField() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingInfoSection() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingPathsSection() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingTitle() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("version", "1.0.0"));
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingVersion() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test"));
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidVersionFormat() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "invalid-version");
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidVersionFormat() {
        Map<String, Object> spec = createValidOpenAPISpec();
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidEmailFormat() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "1.0.0");
        Map<String, Object> contact = new HashMap<>();
        contact.put("email", "invalid-email");
        info.put("contact", contact);
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidEmailFormat() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "1.0.0");
        Map<String, Object> contact = new HashMap<>();
        contact.put("email", "test@example.com");
        info.put("contact", contact);
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidURLFormat() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "1.0.0");
        Map<String, Object> contact = new HashMap<>();
        contact.put("url", "not-a-url");
        info.put("contact", contact);
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidURLFormat() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "1.0.0");
        Map<String, Object> contact = new HashMap<>();
        contact.put("url", "https://example.com");
        info.put("contact", contact);
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateMissingLicenseName() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test");
        info.put("version", "1.0.0");
        Map<String, Object> license = new HashMap<>();
        license.put("url", "https://example.com/license");
        info.put("license", license);
        spec.put("info", info);
        spec.put("paths", Map.of());
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateOperationMissingResponses() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        // Missing responses
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateOperationWithValidResponses() {
        Map<String, Object> spec = createValidOpenAPISpec();
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidResponseCode() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        Map<String, Object> responses = new HashMap<>();
        responses.put("999", Map.of("description", "Invalid")); // Invalid response code
        get.put("responses", responses);
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidResponseCode() {
        Map<String, Object> spec = createValidOpenAPISpec();
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidOperationId() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "123-invalid"); // Invalid operation ID
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidOperationId() {
        Map<String, Object> spec = createValidOpenAPISpec();
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }

    @Test
    public void testValidateOperationIdWithHyphens() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "get-jobId-status");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);

        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateParameterMissingName() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("in", "query");
        // Missing name
        parameters.add(param);
        get.put("parameters", parameters);
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateParameterMissingIn() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("name", "test");
        // Missing in
        parameters.add(param);
        get.put("parameters", parameters);
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateParameterInvalidLocation() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("name", "test");
        param.put("in", "invalid"); // Invalid location
        parameters.add(param);
        get.put("parameters", parameters);
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateInvalidSecuritySchemeType() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        spec.put("paths", Map.of());
        Map<String, Object> components = new HashMap<>();
        Map<String, Object> securitySchemes = new HashMap<>();
        Map<String, Object> scheme = new HashMap<>();
        scheme.put("type", "invalid"); // Invalid type
        securitySchemes.put("testScheme", scheme);
        components.put("securitySchemes", securitySchemes);
        spec.put("components", components);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateValidSecurityScheme() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        spec.put("paths", Map.of());
        Map<String, Object> components = new HashMap<>();
        Map<String, Object> securitySchemes = new HashMap<>();
        Map<String, Object> scheme = new HashMap<>();
        scheme.put("type", "apiKey");
        securitySchemes.put("testScheme", scheme);
        components.put("securitySchemes", securitySchemes);
        spec.put("components", components);
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateUndefinedSecurityScheme() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        spec.put("paths", Map.of());
        List<Map<String, Object>> security = new ArrayList<>();
        Map<String, Object> securityReq = new HashMap<>();
        securityReq.put("undefinedScheme", List.of());
        security.add(securityReq);
        spec.put("security", security);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateEmptyResponses() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("responses", Map.of()); // Empty responses
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertThrows(ValidationException.class, () -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testValidateDefaultResponseCode() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        Map<String, Object> responses = new HashMap<>();
        responses.put("default", Map.of("description", "Default response"));
        get.put("responses", responses);
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        assertDoesNotThrow(() -> {
            validator.validate(spec);
        });
    }
    
    @Test
    public void testPatternWithoutMinOrMaxLength_flagsPathParam() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getFolder");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        Map<String, Object> param = new HashMap<>();
        param.put("name", "folderID");
        param.put("in", "path");
        param.put("required", true);
        param.put("schema", Map.of(
                "type", "string",
                "pattern", "^[1-9]\\d{13,19}$"));
        get.put("parameters", List.of(param));
        pathItem.put("get", get);
        paths.put("/folders/{folderID}", pathItem);

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("has pattern but neither minLength nor maxLength"),
                ex.getMessage());
    }

    @Test
    public void testPatternWithMinLength_passes() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getFolder");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "string");
        schema.put("pattern", "^[1-9]\\d{13,19}$");
        schema.put("minLength", 14);
        schema.put("maxLength", 20);
        Map<String, Object> param = new HashMap<>();
        param.put("name", "folderID");
        param.put("in", "path");
        param.put("required", true);
        param.put("schema", schema);
        get.put("parameters", List.of(param));
        pathItem.put("get", get);
        paths.put("/folders/{folderID}", pathItem);

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @Test
    public void testBlankableStringPaginationQueryParam_flags() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getFolders");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        Map<String, Object> param = new HashMap<>();
        param.put("name", "$pagesize");
        param.put("in", "query");
        param.put("schema", Map.of("type", "string"));
        get.put("parameters", List.of(param));
        pathItem.put("get", get);
        paths.put("/folders", pathItem);

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("blankable string"), ex.getMessage());
    }

    @Test
    public void testIntegerPaginationQueryParam_passes() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getFolders");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "integer");
        schema.put("minimum", 1);
        schema.put("maximum", 75);
        Map<String, Object> param = new HashMap<>();
        param.put("name", "$pagesize");
        param.put("in", "query");
        param.put("schema", schema);
        get.put("parameters", List.of(param));
        pathItem.put("get", get);
        paths.put("/folders", pathItem);

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @Test
    public void testJsonRequestBodyMustBeObject_flagsScalarType() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> post = new HashMap<>();
        post.put("operationId", "createFolder");
        post.put("responses", Map.of("201", Map.of("description", "Created")));
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", Map.of(
                "application/json", Map.of("schema", Map.of("type", "string"))));
        post.put("requestBody", requestBody);
        pathItem.put("post", post);
        paths.put("/folders", pathItem);

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("must use an object schema"), ex.getMessage());
    }

    @Test
    public void testJsonRequestBodyObjectType_passes() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> paths = getPaths(spec);
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> post = new HashMap<>();
        post.put("operationId", "createFolder");
        post.put("responses", Map.of("201", Map.of("description", "Created")));
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", Map.of(
                "application/json", Map.of("schema", Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string"))))));
        post.put("requestBody", requestBody);
        pathItem.put("post", post);
        paths.put("/folders", pathItem);

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @Test
    public void testErrorDeveloperMessageMaxLengthFloor_flags() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> components = new HashMap<>();
        Map<String, Object> schemas = new HashMap<>();
        Map<String, Object> errorSchema = new HashMap<>();
        errorSchema.put("type", "object");
        errorSchema.put("properties", Map.of(
                "developerMessage", Map.of(
                        "type", "string",
                        "minLength", 0,
                        "maxLength", 255)));
        schemas.put("WSErrorCommon", errorSchema);
        components.put("schemas", schemas);
        spec.put("components", components);

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("developerMessage maxLength"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1024"), ex.getMessage());
    }

    @Test
    public void testErrorDeveloperMessageMaxLengthFloor_passesAt2048() {
        Map<String, Object> spec = createValidOpenAPISpec();
        Map<String, Object> components = new HashMap<>();
        Map<String, Object> schemas = new HashMap<>();
        Map<String, Object> errorSchema = new HashMap<>();
        errorSchema.put("type", "object");
        errorSchema.put("properties", Map.of(
                "developerMessage", Map.of(
                        "type", "string",
                        "minLength", 0,
                        "maxLength", 2048)));
        schemas.put("WSErrorCommon", errorSchema);
        components.put("schemas", schemas);
        spec.put("components", components);

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    /**
     * Helper method to create a valid OpenAPI specification
     */
    private Map<String, Object> createValidOpenAPISpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPaths(Map<String, Object> spec) {
        return (Map<String, Object>) spec.get("paths");
    }
}

