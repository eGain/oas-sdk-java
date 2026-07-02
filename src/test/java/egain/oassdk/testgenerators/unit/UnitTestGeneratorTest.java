package egain.oassdk.testgenerators.unit;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for UnitTestGenerator
 */
public class UnitTestGeneratorTest {
    
    private ContractTestGenerator generator;
    private Map<String, Object> spec;
    private TestConfig testConfig;
    
    @BeforeEach
    public void setUp() {
        generator = new ContractTestGenerator();
        spec = createValidOpenAPISpec();
        testConfig = new TestConfig();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("Contract Test Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetTestType() {
        assertEquals("contract", generator.getTestType());
    }
    
    @Test
    public void testGenerate_Success(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        // Check that unit test directory was created
        Path unitDir = tempDir.resolve("contract");
        assertTrue(Files.exists(unitDir));
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
        assertTrue(Files.exists(tempDir.resolve("contract")));
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
        assertTrue(Files.exists(tempDir.resolve("contract")));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testGenerate_WithParameters(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> specWithParams = new HashMap<>(spec);
        Map<String, Object> paths = (Map<String, Object>) specWithParams.get("paths");
        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/test");
        Map<String, Object> get = (Map<String, Object>) pathItem.get("get");
        get.put("parameters", java.util.List.of(
            Map.of("name", "id", "in", "query", "required", true, 
                   "schema", Map.of("type", "string", "pattern", "^[0-9]+$"))
        ));
        specWithParams.put("paths", paths);
        
        // Act
        generator.generate(specWithParams, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("contract")));
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
        assertTrue(Files.exists(tempDir.resolve("contract")));
    }
    
    @Test
    public void testGeneratedTestMethodsArePublic(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that generated test methods are public
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        // Check that test methods are public
                        assertTrue(content.contains("public void test")
                                || content.contains("static void initRestAssured"),
                            "Generated API tests should declare public test methods or static initRestAssured in " + testFile.getFileName());
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testPathParamsAlwaysDeclared(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithPathParams = createSpecWithPathParams();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithPathParams, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that pathParams is always declared
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        // Count test methods
                        long testMethodCount = content.split("public void test").length - 1;
                        if (testMethodCount > 0) {
                            // Count pathParams declarations
                            long pathParamsDeclarations = content.split("Map<String, String> pathParams = new HashMap<>").length - 1;
                            assertTrue(pathParamsDeclarations >= testMethodCount,
                                "pathParams should be declared for each test method in " + testFile.getFileName() +
                                ". Found " + pathParamsDeclarations + " declarations for " + testMethodCount + " test methods");
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testQueryParamsAlwaysDeclared(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithQueryParams = createSpecWithQueryParams();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithQueryParams, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that queryParams is always declared
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        // Count test methods
                        long testMethodCount = content.split("public void test").length - 1;
                        if (testMethodCount > 0) {
                            // Count queryParams declarations
                            long queryParamsDeclarations = content.split("Map<String, String> queryParams = new HashMap<>").length - 1;
                            assertTrue(queryParamsDeclarations >= testMethodCount,
                                "queryParams should be declared for each test method in " + testFile.getFileName() +
                                ". Found " + queryParamsDeclarations + " declarations for " + testMethodCount + " test methods");
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testComprehensiveAssertionsInValidRequestTests(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that valid request tests have comprehensive assertions
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("_ValidRequest()")) {
                            assertTrue(content.contains("given()"),
                                "Valid request test should use RestAssured given() in " + testFile.getFileName());
                            assertTrue(content.contains(".when()"),
                                "Valid request test should use .when() in " + testFile.getFileName());
                            assertTrue(content.contains(".then()") && content.contains("statusCode"),
                                "Valid request test should use .then() and statusCode in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testEnhancedInvalidParameterTests(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithInvalidParamTests = createSpecWithPatternParameter();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithInvalidParamTests, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that invalid parameter tests have enhanced structure
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("_Invalid") && content.contains("Format")) {
                            // Check for enhanced structure
                            assertTrue(content.contains("public void test") && content.contains("Invalid"),
                                "Invalid parameter test should be public in " + testFile.getFileName());
                            assertTrue(content.contains("Map<String, String> pathParams = new HashMap<>()") ||
                                      content.contains("Map<String, String> queryParams = new HashMap<>()"),
                                "Invalid parameter test should declare pathParams/queryParams in " + testFile.getFileName());
                            assertTrue(content.contains("assertFalse") || content.contains("statusCode"),
                                "Invalid parameter test should assert pattern or status in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }

    @Test
    public void testInvalidQueryValuesGeneratedForAllConstrainedQueryParams(@TempDir Path tempDir)
            throws GenerationException, java.io.IOException {
        Map<String, Object> mixed = createSpecWithMixedQueryInvalidParameters();
        testConfig.setTestFramework("junit5");

        generator.generate(mixed, tempDir.toString(), testConfig, "junit5");

        Path testFile = tempDir.resolve("contract/src/test/java/com/example/api/CatalogApiTest.java");
        assertTrue(Files.exists(testFile), "Expected CatalogApiTest.java for tag Catalog");
        String content = Files.readString(testFile);
        assertTrue(content.contains("void testSearchItems_InvalidQFormat(String invalidValue)"), content);
        assertTrue(content.contains("void testSearchItems_InvalidPageFormat(String invalidValue)"), content);
        assertTrue(content.contains("void testSearchItems_InvalidSortFormat(String invalidValue)"), content);
        assertTrue(content.contains("void testSearchItems_InvalidActiveFormat(String invalidValue)"), content);
        assertTrue(content.contains("void testSearchItems_InvalidLabelFormat(String invalidValue)"), content);
        assertTrue(content.contains("assertFalse(invalidValue.matches("),
            "Pattern-only query param should keep local regex assertion");
    }

    @Test
    public void testInvalidPathParameterUsesInvalidValueInMap(@TempDir Path tempDir)
            throws GenerationException, java.io.IOException {
        Map<String, Object> s = createSpecWithPathPatternParameter();
        testConfig.setTestFramework("junit5");

        generator.generate(s, tempDir.toString(), testConfig, "junit5");

        Path testFile = tempDir.resolve("contract/src/test/java/com/example/api/WidgetApiTest.java");
        assertTrue(Files.exists(testFile));
        String content = Files.readString(testFile);
        assertTrue(content.contains("pathParams.put(\"widgetId\", invalidValue)"),
            "Invalid path param test should pass invalidValue into pathParams");
    }
    
    @Test
    public void testEnhancedMissingRequiredParameterTests(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithRequiredParam = createSpecWithRequiredParameter();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithRequiredParam, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that missing required parameter tests have enhanced structure
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("MissingRequiredParam")) {
                            // Check for enhanced structure
                            assertTrue(content.contains("public void test") && content.contains("MissingRequiredParam"),
                                "Missing required parameter test should be public in " + testFile.getFileName());
                            assertTrue(content.contains("Map<String, String> pathParams = new HashMap<>()"),
                                "Missing required parameter test should declare pathParams in " + testFile.getFileName());
                            assertTrue(content.contains("Map<String, String> queryParams = new HashMap<>()"),
                                "Missing required parameter test should declare queryParams in " + testFile.getFileName());
                            assertTrue(content.contains(".then()") && content.contains("statusCode"),
                                "Missing required parameter test should assert HTTP error status in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testUnauthorizedScenarioUsesInvalidToken(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithStatusCodes = createSpecWithMultipleStatusCodes();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithStatusCodes, tempDir.toString(), testConfig, "junit5");
        
        // Assert - check unauthorized scenario uses invalid token and no blind status matrix
        Path unitDir = tempDir.resolve("contract");
        Path packageDir = unitDir.resolve("src/test/java/com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        assertFalse(content.contains("_Status401"), "Should not generate blind Status401 tests");
                        assertFalse(content.contains("_Status500"), "Should not generate blind Status500 tests");
                        assertTrue(content.contains("_UnauthorizedInvalidToken"),
                                "Should generate invalid-token auth scenario");
                        assertTrue(content.contains("TestAuth.invalidToken()"),
                                "Unauthorized scenario should use TestAuth.invalidToken()");
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testBeforeAllInitRestAssuredIsPresent(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        testConfig.setTestFramework("junit5");
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        Path packageDir = tempDir.resolve("contract").resolve("src/test/java/com/example/api");
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        assertTrue(content.contains("@BeforeAll"),
                            "Generated API test should use @BeforeAll in " + testFile.getFileName());
                        assertTrue(content.contains("initRestAssured"),
                            "Generated API test should define initRestAssured in " + testFile.getFileName());
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testComposedSchemaRequestBodyIsNotEmpty(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        Map<String, Object> spec = createSpecWithAllOfRequestBody();
        testConfig.setTestFramework("junit5");

        generator.generate(spec, tempDir.toString(), testConfig, "junit5");

        Path testFile = tempDir.resolve("contract/src/test/java/com/example/api/FoldersApiTest.java");
        assertTrue(Files.exists(testFile), "Expected FoldersApiTest.java");
        String content = Files.readString(testFile);
        assertTrue(content.contains("_ValidRequest()"), content);
        assertFalse(content.contains(".body(\"{}\");"),
                "composed allOf request body should not be empty object literal");
        assertTrue(content.contains("RequestBodyFactory.forOperation(\"createFolder\").valid()"),
                "createFolder request should use RequestBodyFactory valid template");
    }

    @Test
    public void testGeneratedTestsAreTaggedWithOperationId(@TempDir Path tempDir) throws Exception {
        Map<String, Object> taggedSpec = createValidOpenAPISpec();
        generator.generate(taggedSpec, tempDir.toString(), testConfig, "junit5");
        Path testFile = tempDir.resolve("contract/src/test/java/com/example/api/TestApiTest.java");
        assertTrue(Files.exists(testFile));
        String content = Files.readString(testFile);
        assertTrue(content.contains("@Tag(\"getTest\")"), content);
    }

    private Map<String, Object> createSpecWithAllOfRequestBody() {
        Map<String, Object> folderProps = new java.util.LinkedHashMap<>();
        folderProps.put("name", Map.of("type", "string"));
        folderProps.put("description", Map.of("type", "string"));

        Map<String, Object> overlay = new java.util.LinkedHashMap<>();
        overlay.put("type", "object");
        overlay.put("required", java.util.List.of("name"));
        overlay.put("properties", Map.of("name", Map.of("type", "string")));

        Map<String, Object> createFolder = new java.util.LinkedHashMap<>();
        createFolder.put("allOf", java.util.List.of(overlay, Map.of("$ref", "#/components/schemas/Folder")));

        Map<String, Object> folder = new java.util.LinkedHashMap<>();
        folder.put("type", "object");
        folder.put("properties", folderProps);

        Map<String, Object> components = new java.util.LinkedHashMap<>();
        components.put("schemas", Map.of("Folder", folder, "createFolder", createFolder));

        Map<String, Object> post = new java.util.LinkedHashMap<>();
        post.put("operationId", "createFolder");
        post.put("summary", "Create folder");
        post.put("tags", java.util.List.of("Folders"));
        post.put("requestBody", Map.of("content", Map.of("application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/createFolder")))));
        post.put("responses", Map.of("201", Map.of("description", "Created")));

        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Folders API", "version", "1.0.0"));
        spec.put("components", components);
        spec.put("paths", Map.of("/folders", Map.of("post", post)));
        return spec;
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
    
    /**
     * Helper method to create spec with path parameters
     */
    private Map<String, Object> createSpecWithPathParams() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getUserById");
        get.put("summary", "Get user by ID");
        get.put("tags", java.util.List.of("users"));
        get.put("parameters", java.util.List.of(
            Map.of("name", "id", "in", "path", "required", true, 
                   "schema", Map.of("type", "string"))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users/{id}", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
    
    /**
     * Helper method to create spec with query parameters
     */
    private Map<String, Object> createSpecWithQueryParams() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "searchUsers");
        get.put("summary", "Search users");
        get.put("tags", java.util.List.of("users"));
        get.put("parameters", java.util.List.of(
            Map.of("name", "query", "in", "query", "required", false, 
                   "schema", Map.of("type", "string"))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
    
    /**
     * Helper method to create spec with pattern parameter
     */
    private Map<String, Object> createSpecWithMixedQueryInvalidParameters() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));

        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "searchItems");
        get.put("summary", "Search items");
        get.put("tags", java.util.List.of("Catalog"));
        get.put("parameters", java.util.List.of(
                Map.of("name", "q", "in", "query", "schema",
                        Map.of("type", "string", "pattern", "^[a-z]+$")),
                Map.of("name", "page", "in", "query", "schema",
                        Map.of("type", "integer", "minimum", 1)),
                Map.of("name", "sort", "in", "query", "schema",
                        Map.of("type", "string", "enum", java.util.List.of("asc", "desc"))),
                Map.of("name", "active", "in", "query", "schema", Map.of("type", "boolean")),
                Map.of("name", "label", "in", "query", "schema",
                        Map.of("type", "string", "minLength", 1))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));

        Map<String, Object> pathItem = new HashMap<>();
        pathItem.put("get", get);
        spec.put("paths", Map.of("/items", pathItem));
        return spec;
    }

    private Map<String, Object> createSpecWithPathPatternParameter() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));

        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getWidget");
        get.put("summary", "Get widget");
        get.put("tags", java.util.List.of("Widget"));
        get.put("parameters", java.util.List.of(
                Map.of("name", "widgetId", "in", "path", "required", true,
                        "schema", Map.of("type", "string", "pattern", "^[A-Z]{3}$"))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));

        Map<String, Object> pathItem = new HashMap<>();
        pathItem.put("get", get);
        spec.put("paths", Map.of("/widgets/{widgetId}", pathItem));
        return spec;
    }

    private Map<String, Object> createSpecWithPatternParameter() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));

        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getUserByEmail");
        get.put("summary", "Get user by email");
        get.put("tags", java.util.List.of("users"));
        get.put("parameters", java.util.List.of(
            Map.of("name", "email", "in", "query", "required", true, 
                   "schema", Map.of("type", "string", "pattern", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
    
    /**
     * Helper method to create spec with required parameter
     */
    private Map<String, Object> createSpecWithRequiredParameter() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getUser");
        get.put("summary", "Get user");
        get.put("tags", java.util.List.of("users"));
        get.put("parameters", java.util.List.of(
            Map.of("name", "userId", "in", "query", "required", true, 
                   "schema", Map.of("type", "string"))
        ));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
    
    /**
     * Helper method to create spec with multiple status codes
     */
    private Map<String, Object> createSpecWithMultipleStatusCodes() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getResource");
        get.put("summary", "Get resource");
        get.put("tags", java.util.List.of("resources"));
        get.put("responses", Map.of(
            "200", Map.of("description", "OK"),
            "401", Map.of("description", "Unauthorized"),
            "404", Map.of("description", "Not Found"),
            "500", Map.of("description", "Internal Server Error")
        ));
        pathItem.put("get", get);
        paths.put("/resources", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
}

