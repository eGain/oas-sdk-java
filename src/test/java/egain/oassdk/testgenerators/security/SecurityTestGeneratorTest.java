package egain.oassdk.testgenerators.security;

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
 * Comprehensive test class for SecurityTestGenerator
 */
public class SecurityTestGeneratorTest {
    
    private SecurityTestGenerator generator;
    private Map<String, Object> spec;
    private TestConfig testConfig;
    
    @BeforeEach
    public void setUp() {
        generator = new SecurityTestGenerator();
        spec = createValidOpenAPISpec();
        testConfig = new TestConfig();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("Security Test Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetTestType() {
        assertEquals("security", generator.getTestType());
    }
    
    @Test
    public void testGenerate_Success(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        testConfig.setTestFramework("junit5");
        
        // Act
        generator.generate(spec, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        // Check that security test directory was created
        Path securityDir = tempDir.resolve("security");
        assertTrue(Files.exists(securityDir));
        assertTrue(Files.exists(securityDir.resolve("security-config.properties")));
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
        assertTrue(Files.exists(tempDir.resolve("security")));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testGenerate_WithSecuritySchemes(@TempDir Path tempDir) throws GenerationException, java.io.IOException {
        // Arrange
        Map<String, Object> specWithSecurity = new HashMap<>(spec);
        Map<String, Object> paths = (Map<String, Object>) specWithSecurity.get("paths");
        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/test");
        Map<String, Object> get = (Map<String, Object>) pathItem.get("get");
        get.put("security", java.util.List.of(Map.of("BearerAuth", java.util.List.of("folders.read"))));
        get.put("summary", "Get folders");
        specWithSecurity.put("paths", paths);
        Map<String, Object> components = new HashMap<>();
        components.put("securitySchemes", Map.of(
                "BearerAuth", Map.of("type", "http", "scheme", "bearer")));
        specWithSecurity.put("components", components);
        
        // Act
        generator.generate(specWithSecurity, tempDir.toString(), testConfig, "junit5");
        
        // Assert
        Path securityDir = tempDir.resolve("security");
        assertTrue(Files.exists(securityDir));
        boolean foundClientApp = Files.walk(securityDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .anyMatch(f -> {
                    try {
                        String content = Files.readString(f);
                        return content.contains("TOKEN_CLIENT_APP")
                                && content.contains("Client App Token")
                                && content.contains("TOKEN_WRONG_ROLE");
                    } catch (java.io.IOException e) {
                        return false;
                    }
                });
        assertTrue(foundClientApp,
                "Security suite should emit client-app token (CBD-8481) and wrong-role (CBD-8549) scenarios");
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
        assertTrue(Files.exists(tempDir.resolve("security")));
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
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
}

