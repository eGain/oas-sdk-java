package egain.oassdk.testgenerators.integration;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for IntegrationTestGenerator
 */
public class IntegrationTestGeneratorTest {
    
    private IntegrationTestGenerator generator;
    private Map<String, Object> spec;
    private TestConfig testConfig;
    
    @BeforeEach
    public void setUp() {
        generator = new IntegrationTestGenerator();
        spec = createValidOpenAPISpec();
        testConfig = new TestConfig();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("Integration Test Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetTestType() {
        assertEquals("integration", generator.getTestType());
    }
    
    @Test
    public void testGenerate_Success(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        // Check that integration test directory was created
        Path integrationDir = tempDir.resolve("integration");
        assertTrue(Files.exists(integrationDir));
    }
    
    @Test
    public void testGenerate_WithEmptySpec(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> emptySpec = new HashMap<>();
        emptySpec.put("openapi", "3.0.0");
        emptySpec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        emptySpec.put("paths", Map.of());
        
        // Act
        generator.generate(emptySpec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        // Should not throw exception even with empty paths
        assertTrue(Files.exists(tempDir.resolve("integration")));
    }
    
    @Test
    public void testGenerate_WithPaths(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> specWithPaths = new HashMap<>(spec);
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getUsers");
        get.put("tags", java.util.List.of("users"));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users", pathItem);
        specWithPaths.put("paths", paths);
        
        // Act
        generator.generate(specWithPaths, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("integration")));
    }
    
    @Test
    public void testSetConfig() {
        // Arrange
        TestConfig config = new TestConfig();
        config.setTestFramework("junit5");
        
        // Act
        generator.setConfig(config);
        
        // Assert
        assertEquals(config, generator.getConfig());
    }
    
    @Test
    public void testGenerate_WithCustomPackageName(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("packageName", "com.custom.package");
        testConfig.setAdditionalProperties(additionalProps);
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("integration")));
    }
    
    @Test
    public void testGenerate_expandedIntegrationPatterns(@TempDir Path tempDir) throws Exception {
        generator.generate(buildRichSecuredPostSpec(), tempDir.toString(), testConfig, "junit5");

        Path javaFile = tempDir.resolve("integration/src/test/java/com/example/api/ItemsIntegrationTest.java");
        assertTrue(Files.exists(javaFile));
        String content = Files.readString(javaFile);
        assertTrue(content.contains("TestAuth.rawToken") || content.contains("getTokenClientApplication"));
        assertTrue(content.contains("_AnonymousNoCredentials"));
        assertTrue(content.contains("_InvalidBodyField_"));
        assertTrue(content.contains("_ParamNegative_"));
        assertTrue(content.contains("TestEnv.baseUrl()") || content.contains("TestEnv.departmentId()"));
    }

    @Test
    public void testGenerate_composedSchemaPost(@TempDir Path tempDir) throws Exception {
        generator.generate(buildComposedSchemaPostSpec(), tempDir.toString(), testConfig, "junit5");

        Path javaFile = tempDir.resolve("integration/src/test/java/com/example/api/FoldersIntegrationTest.java");
        assertTrue(Files.exists(javaFile));
        String content = Files.readString(javaFile);
        assertTrue(content.contains("_MissingRequiredFields"));
        assertTrue(content.contains("_InvalidBodyField_"));
        assertTrue(content.contains("_OneOfXor_"));
        assertTrue(content.contains("_Success_Department") || content.contains("_Success_Parent"),
                "expected oneOf variant success tests");
        assertTrue(content.contains("_EmptyBody"));
        assertTrue(content.contains("TestAuth.rawToken()") || content.contains("getAuthToken()"));
    }

    @Test
    public void testGenerate_WithServers(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> specWithServers = new HashMap<>(spec);
        specWithServers.put("servers", java.util.List.of(
            Map.of("url", "https://api.example.com", "description", "Production")
        ));
        
        // Act
        generator.generate(specWithServers, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("integration")));
    }
    
    /**
     * Helper method to create a valid OpenAPI specification
     */
    private Map<String, Object> createValidOpenAPISpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0", "description", "Test API Description"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("summary", "Get test data");
        get.put("tags", java.util.List.of("test"));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }

    private Map<String, Object> buildRichSecuredPostSpec() {
        Map<String, Object> schemas = new HashMap<>();
        Map<String, Object> itemProps = new HashMap<>();
        itemProps.put("title", Map.of("type", "string"));
        itemProps.put("count", Map.of("type", "integer"));
        schemas.put("ItemRequest", Map.of(
                "type", "object",
                "properties", itemProps,
                "required", List.of("title")
        ));
        schemas.put("Error", Map.of(
                "type", "object",
                "properties", Map.of("message", Map.of("type", "string"))
        ));

        Map<String, Object> components = new HashMap<>();
        components.put("schemas", schemas);
        components.put("securitySchemes", Map.of("bearerAuth", Map.of(
                "type", "http",
                "scheme", "bearer",
                "bearerFormat", "JWT"
        )));

        Map<String, Object> post = new HashMap<>();
        post.put("operationId", "createItem");
        post.put("summary", "Create item");
        post.put("tags", List.of("items"));
        post.put("security", List.of(Map.of("bearerAuth", List.of())));
        Map<String, Object> rbSchema = new HashMap<>();
        rbSchema.put("$ref", "#/components/schemas/ItemRequest");
        post.put("requestBody", Map.of(
                "required", true,
                "content", Map.of("application/json", Map.of("schema", rbSchema))
        ));
        Map<String, Object> badContent = Map.of(
                "description", "Bad Request",
                "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/Error")))
        );
        post.put("responses", Map.of(
                "201", Map.of("description", "Created"),
                "400", badContent
        ));

        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Rich API", "version", "1.0.0"));
        spec.put("paths", Map.of("/items", Map.of("post", post)));
        spec.put("components", components);
        return spec;
    }

    private Map<String, Object> buildComposedSchemaPostSpec() {
        Map<String, Object> folderProps = new LinkedHashMap<>();
        folderProps.put("name", Map.of("type", "string", "minLength", 1));
        folderProps.put("description", Map.of("type", "string"));

        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("type", "object");
        overlay.put("required", List.of("name"));
        Map<String, Object> overlayProps = new LinkedHashMap<>();
        overlayProps.put("name", Map.of("type", "string", "minLength", 1));
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

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Folder", Map.of("type", "object", "properties", folderProps));
        schemas.put("createFolder", createFolder);
        schemas.put("Error", Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string"))));

        Map<String, Object> components = new HashMap<>();
        components.put("schemas", schemas);
        components.put("securitySchemes", Map.of("bearerAuth", Map.of(
                "type", "http", "scheme", "bearer")));

        Map<String, Object> post = new HashMap<>();
        post.put("operationId", "createFolder");
        post.put("summary", "Create Folder");
        post.put("tags", List.of("folders"));
        post.put("security", List.of(Map.of("bearerAuth", List.of())));
        post.put("requestBody", Map.of(
                "required", true,
                "content", Map.of("application/json", Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/createFolder")))));
        post.put("responses", Map.of(
                "201", Map.of("description", "Created"),
                "400", Map.of("description", "Bad Request",
                        "content", Map.of("application/json", Map.of(
                                "schema", Map.of("$ref", "#/components/schemas/Error"))))));

        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Folders API", "version", "1.0.0"));
        spec.put("paths", Map.of("/folders", Map.of("post", post)));
        spec.put("components", components);
        return spec;
    }
}

