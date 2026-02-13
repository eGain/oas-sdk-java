package egain.oassdk.core.parser;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Comprehensive test cases for OASParser
 */
public class OASParserTest {
    
    private OASParser parser;
    
    @BeforeEach
    public void setUp() {
        parser = new OASParser();
    }
    
    @Test
    public void testParserInitialization() {
        assertNotNull(parser);
    }
    
    @Test
    public void testParseValidYAMLFile() throws IOException, OASSDKException {
        // Create a temporary YAML file
        Path tempFile = Files.createTempFile("test", ".yaml");
        try {
            String yamlContent = "openapi: 3.0.0\n" +
                    "info:\n" +
                    "  title: Test API\n" +
                    "  version: 1.0.0\n" +
                    "paths: {}\n";
            Files.writeString(tempFile, yamlContent);
            
            Map<String, Object> result = parser.parse(tempFile.toString());
            
            assertNotNull(result);
            assertEquals("3.0.0", result.get("openapi"));
            assertTrue(result.containsKey("info"));
            assertTrue(result.containsKey("paths"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Test
    public void testParseValidJSONFile() throws IOException, OASSDKException {
        // Create a temporary JSON file
        Path tempFile = Files.createTempFile("test", ".json");
        try {
            String jsonContent = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Test API\",\"version\":\"1.0.0\"},\"paths\":{}}";
            Files.writeString(tempFile, jsonContent);
            
            Map<String, Object> result = parser.parse(tempFile.toString());
            
            assertNotNull(result);
            assertEquals("3.0.0", result.get("openapi"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Test
    public void testParseNonExistentFile() {
        assertThrows(OASSDKException.class, () -> {
            parser.parse("nonexistent.yaml");
        });
    }
    
    @Test
    public void testParseContentYAML() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Test API\n" +
                "  version: 1.0.0\n" +
                "paths: {}\n";
        
        Map<String, Object> result = parser.parseContent(yamlContent, "test.yaml");
        
        assertNotNull(result);
        assertEquals("3.0.0", result.get("openapi"));
    }
    
    @Test
    public void testParseContentJSON() throws OASSDKException {
        String jsonContent = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Test API\",\"version\":\"1.0.0\"},\"paths\":{}}";
        
        Map<String, Object> result = parser.parseContent(jsonContent, "test.json");
        
        assertNotNull(result);
        assertEquals("3.0.0", result.get("openapi"));
    }
    
    @Test
    public void testParseContentAutoDetectJSON() throws OASSDKException {
        String jsonContent = "{\"openapi\":\"3.0.0\"}";
        
        Map<String, Object> result = parser.parseContent(jsonContent, "test.unknown");
        
        assertNotNull(result);
        assertEquals("3.0.0", result.get("openapi"));
    }
    
    @Test
    public void testParseContentAutoDetectYAML() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\ninfo:\n  title: Test";
        
        Map<String, Object> result = parser.parseContent(yamlContent, "test.unknown");
        
        assertNotNull(result);
        assertEquals("3.0.0", result.get("openapi"));
    }
    
    @Test
    public void testParseInvalidContent() {
        assertThrows(OASSDKException.class, () -> {
            parser.parseContent("invalid: yaml: content: [", "test.yaml");
        });
    }
    
    @Test
    public void testIsOpenAPISpecWithOpenAPI() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\ninfo:\n  title: Test\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertTrue(parser.isOpenAPISpec(spec));
    }
    
    @Test
    public void testIsOpenAPISpecWithSwagger() throws OASSDKException {
        String yamlContent = "swagger: 2.0\ninfo:\n  title: Test\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertTrue(parser.isOpenAPISpec(spec));
    }
    
    @Test
    public void testIsOpenAPISpecWithNeither() {
        Map<String, Object> spec = Map.of("info", Map.of("title", "Test"));
        
        assertFalse(parser.isOpenAPISpec(spec));
    }
    
    @Test
    public void testIsSLASpecWithSLA() throws OASSDKException {
        String yamlContent = "sla:\n  version: 1.0.0";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertTrue(parser.isSLASpec(spec));
    }
    
    @Test
    public void testIsSLASpecWithNFR() throws OASSDKException {
        String yamlContent = "nfr:\n  version: 1.0.0";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertTrue(parser.isSLASpec(spec));
    }
    
    @Test
    public void testIsSLASpecWithNonFunctionalRequirements() throws OASSDKException {
        String yamlContent = "non-functional-requirements:\n  version: 1.0.0";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertTrue(parser.isSLASpec(spec));
    }
    
    @Test
    public void testIsSLASpecWithNeither() {
        Map<String, Object> spec = Map.of("info", Map.of("title", "Test"));
        
        assertFalse(parser.isSLASpec(spec));
    }
    
    @Test
    public void testGetOpenAPIVersion() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\ninfo:\n  title: Test\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertEquals("3.0.0", parser.getOpenAPIVersion(spec));
    }
    
    @Test
    public void testGetOpenAPIVersionSwagger() throws OASSDKException {
        String yamlContent = "swagger: 2.0\ninfo:\n  title: Test\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertEquals("2.0", parser.getOpenAPIVersion(spec));
    }
    
    @Test
    public void testGetOpenAPIVersionNull() {
        Map<String, Object> spec = Map.of("info", Map.of("title", "Test"));
        
        assertNull(parser.getOpenAPIVersion(spec));
    }
    
    @Test
    public void testGetAPITitle() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertEquals("Test API", parser.getAPITitle(spec));
    }
    
    @Test
    public void testGetAPITitleNull() {
        Map<String, Object> spec = Map.of("openapi", "3.0.0", "paths", Map.of());
        
        assertNull(parser.getAPITitle(spec));
    }
    
    @Test
    public void testGetAPIVersion() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0\npaths: {}";
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        
        assertEquals("1.0.0", parser.getAPIVersion(spec));
    }
    
    @Test
    public void testGetAPIVersionNull() {
        Map<String, Object> spec = Map.of("openapi", "3.0.0", "paths", Map.of());
        
        assertNull(parser.getAPIVersion(spec));
    }
    
    @Test
    public void testResolveReferencesWithInternalRef() throws OASSDKException {
        String yamlContent = "openapi: 3.0.0\n" +
                "info:\n  title: Test\n  version: 1.0.0\n" +
                "paths:\n  /test:\n    get:\n      parameters:\n        - $ref: '#/components/parameters/TestParam'\n" +
                "components:\n  parameters:\n    TestParam:\n      name: test\n      in: query";
        
        Map<String, Object> spec = parser.parseContent(yamlContent, "test.yaml");
        Map<String, Object> resolved = parser.resolveReferences(spec, "test.yaml");
        
        assertNotNull(resolved);
        // The $ref should be resolved
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) resolved.get("paths");
        @SuppressWarnings("unchecked")
        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/test");
        @SuppressWarnings("unchecked")
        Map<String, Object> get = (Map<String, Object>) pathItem.get("get");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> params = (java.util.List<Map<String, Object>>) get.get("parameters");
        
        assertNotNull(params);
        assertFalse(params.isEmpty());
    }
    
    @Test
    public void testResolveReferencesWithEmptySpec() throws OASSDKException {
        Map<String, Object> spec = Map.of();
        Map<String, Object> result = parser.resolveReferences(spec, "test.yaml");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testResolveReferencesRegistersNestedExternalSchemaInMainSpec(@TempDir Path tempDir) throws IOException, OASSDKException {
        // Main spec references models/Users.yaml; Users.yaml has user.items $ref: './User.yaml'.
        // Parser must register User in main spec when inlining so generators see the full User schema.
        Path modelsDir = tempDir.resolve("models");
        Files.createDirectories(modelsDir);

        String userYaml = """
            type: object
            title: User
            properties:
              firstName:
                allOf:
                  - type: string
              name:
                allOf:
                  - type: string
            """;
        Files.writeString(modelsDir.resolve("User.yaml"), userYaml);

        String usersYaml = """
            type: object
            title: Users
            properties:
              user:
                allOf:
                  - type: array
                    items:
                      $ref: "./User.yaml"
            """;
        Files.writeString(modelsDir.resolve("Users.yaml"), usersYaml);

        String apiYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                Users:
                  $ref: models/Users.yaml
            """;
        Path apiPath = tempDir.resolve("api.yaml");
        Files.writeString(apiPath, apiYaml);

        Map<String, Object> spec = parser.parse(apiPath.toString());
        Map<String, Object> resolved = parser.resolveReferences(spec, apiPath.toString());

        assertNotNull(resolved);
        Map<String, Object> components = Util.asStringObjectMap(resolved.get("components"));
        assertNotNull(components, "components should exist");
        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        assertNotNull(schemas, "schemas should exist");
        assertTrue(schemas.containsKey("User"), "User schema must be registered when User.yaml is inlined from Users.yaml");
        assertTrue(schemas.containsKey("Users"), "Users schema should exist");
        Map<String, Object> userSchema = Util.asStringObjectMap(schemas.get("User"));
        assertNotNull(userSchema);
        assertTrue(userSchema.containsKey("properties"));
    }
}

