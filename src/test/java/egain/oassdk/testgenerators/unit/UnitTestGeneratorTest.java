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
    
    private UnitTestGenerator generator;
    private Map<String, Object> spec;
    private TestConfig testConfig;
    
    @BeforeEach
    public void setUp() {
        generator = new UnitTestGenerator();
        spec = createValidOpenAPISpec();
        testConfig = new TestConfig();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("Unit Test Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetTestType() {
        assertEquals("unit", generator.getTestType());
    }
    
    @Test
    public void testGenerate_Success(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        // Check that unit test directory was created
        Path unitDir = tempDir.resolve("unit");
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
        assertTrue(Files.exists(tempDir.resolve("unit")));
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
        assertTrue(Files.exists(tempDir.resolve("unit")));
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
        assertTrue(Files.exists(tempDir.resolve("unit")));
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
        assertTrue(Files.exists(tempDir.resolve("unit")));
    }
    
    @Test
    public void testGeneratedTestMethodsArePublic(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that generated test methods are public
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        // Check that test methods are public
                        assertTrue(content.contains("public void test") || 
                                  content.contains("public void setUp()"),
                            "Generated test methods should be public in " + testFile.getFileName());
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
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
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
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
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
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("_ValidRequest()")) {
                            // Check for comprehensive assertions
                            assertTrue(content.contains("assertNotNull(request"), 
                                "Valid request test should have assertNotNull(request) in " + testFile.getFileName());
                            assertTrue(content.contains("assertEquals") || content.contains("assertTrue(uri.toString().startsWith(BASE_URL)"),
                                "Valid request test should have URI validation assertions in " + testFile.getFileName());
                            assertTrue(content.contains("assertNotNull(uri") || content.contains("assertNotNull(path"),
                                "Valid request test should have URI/path validation in " + testFile.getFileName());
                            assertTrue(content.contains("assertNotNull(request.headers()") || 
                                      content.contains("assertTrue(request.headers().firstValue"),
                                "Valid request test should have header validation in " + testFile.getFileName());
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
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
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
                            assertTrue(content.contains("TODO: Mock HTTP client") || content.contains("assertTrue"),
                                "Invalid parameter test should have TODO or assertions in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testEnhancedMissingRequiredParameterTests(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithRequiredParam = createSpecWithRequiredParameter();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithRequiredParam, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that missing required parameter tests have enhanced structure
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
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
                            assertTrue(content.contains("assertFalse(uri.toString().contains") || 
                                      content.contains("TODO: Mock HTTP client"),
                                "Missing required parameter test should have validation or TODO in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testEnhancedStatusCodeTests(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithStatusCodes = createSpecWithMultipleStatusCodes();
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(specWithStatusCodes, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that status code tests have enhanced structure
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("_Status")) {
                            // Check for enhanced structure
                            assertTrue(content.contains("public void test") && content.contains("Status"),
                                "Status code test should be public in " + testFile.getFileName());
                            assertTrue(content.contains("Map<String, String> pathParams = new HashMap<>()") ||
                                      content.contains("Map<String, String> queryParams = new HashMap<>()"),
                                "Status code test should declare pathParams/queryParams in " + testFile.getFileName());
                            assertTrue(content.contains("TODO: Mock HTTP client") || content.contains("assertNotNull(request"),
                                "Status code test should have TODO or assertions in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
    }
    
    @Test
    public void testSetUpMethodIsPublic(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert - Check that setUp method is public
        Path unitDir = tempDir.resolve("unit");
        Path packageDir = unitDir.resolve("com/example/api");
        
        if (Files.exists(packageDir)) {
            Files.walk(packageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java"))
                .forEach(testFile -> {
                    try {
                        String content = Files.readString(testFile);
                        if (content.contains("@BeforeEach")) {
                            assertTrue(content.contains("public void setUp()"),
                                "setUp method should be public in " + testFile.getFileName());
                        }
                    } catch (java.io.IOException e) {
                        fail("Failed to read test file: " + testFile);
                    }
                });
        }
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
            "404", Map.of("description", "Not Found"),
            "500", Map.of("description", "Internal Server Error")
        ));
        pathItem.put("get", get);
        paths.put("/resources", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
}

