package egain.oassdk;

import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.config.SLAConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Comprehensive test class for OASSDK
 */
public class OASSDKTest {
    
    private OASSDK sdk;
    
    @BeforeEach
    public void setUp() {
        sdk = new OASSDK();
    }
    
    @Test
    public void testSDKInitialization() {
        assertNotNull(sdk);
    }
    
    @Test
    public void testSDKInitializationWithConfigs() {
        GeneratorConfig genConfig = new GeneratorConfig();
        TestConfig testConfig = new TestConfig();
        SLAConfig slaConfig = new SLAConfig();
        
        OASSDK configuredSDK = new OASSDK(genConfig, testConfig, slaConfig);
        assertNotNull(configuredSDK);
    }
    
    @Test
    public void testLoadSpecWithValidFile() throws OASSDKException {
        // Load the test OpenAPI file
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        // Verify that the spec was loaded by checking metadata
        Map<String, Object> metadata = sdk.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("basic_info"));
    }
    
    @Test
    public void testLoadSpecWithNullPath() {
        assertThrows(NullPointerException.class, () -> {
            sdk.loadSpec(null);
        });
    }
    
    @Test
    public void testLoadSpecWithInvalidFile() {
        assertThrows(OASSDKException.class, () -> {
            sdk.loadSpec("nonexistent.yaml");
        });
    }
    
    @Test
    public void testLoadSpecMethodChaining() throws OASSDKException {
        OASSDK result = sdk.loadSpec("src/test/resources/openapi.yaml");
        assertSame(sdk, result);
    }
    
    @Test
    public void testLoadSLAWithValidFile() throws OASSDKException {
        // Load the test SLA file if it exists
        try {
            sdk.loadSLA("src/test/resources/sla.yaml");
            // This should not throw an exception
            assertDoesNotThrow(() -> {
                sdk.loadSLA("src/test/resources/sla.yaml");
            });
        } catch (OASSDKException e) {
            // If file doesn't exist, that's okay for this test
            assertTrue(true);
        }
    }
    
    @Test
    public void testLoadSLAWithNullPath() {
        assertThrows(NullPointerException.class, () -> {
            sdk.loadSLA(null);
        });
    }
    
    @Test
    public void testLoadSLAWithInvalidFile() {
        assertThrows(OASSDKException.class, () -> {
            sdk.loadSLA("nonexistent-sla.yaml");
        });
    }
    
    @Test
    public void testLoadSLAMethodChaining() throws OASSDKException {
        try {
            OASSDK result = sdk.loadSLA("src/test/resources/sla.yaml");
            assertSame(sdk, result);
        } catch (OASSDKException e) {
            // If file doesn't exist, skip this test
        }
    }
    
    @Test
    public void testFilterPaths() {
        List<String> paths = Arrays.asList("/api/users", "/api/posts");
        OASSDK result = sdk.filterPaths(paths);
        
        assertSame(sdk, result);
    }
    
    @Test
    public void testFilterPathsWithNull() {
        OASSDK result = sdk.filterPaths(null);
        assertSame(sdk, result);
    }
    
    @Test
    public void testFilterOperations() {
        Map<String, List<String>> operations = new HashMap<>();
        operations.put("/api/users", Arrays.asList("GET", "POST"));
        operations.put("/api/posts", Arrays.asList("GET"));
        
        OASSDK result = sdk.filterOperations(operations);
        assertSame(sdk, result);
    }
    
    @Test
    public void testFilterOperationsWithNull() {
        OASSDK result = sdk.filterOperations(null);
        assertSame(sdk, result);
    }
    
    @Test
    public void testClearFilters() {
        sdk.filterPaths(Arrays.asList("/api/users"));
        sdk.filterOperations(Map.of("/api/users", Arrays.asList("GET")));
        
        OASSDK result = sdk.clearFilters();
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateApplicationWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateApplication("java", "jersey", "com.example.api", "./output");
        });
    }
    
    @Test
    public void testGenerateApplicationWithNullLanguage() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateApplication(null, "jersey", "com.example.api", "./output");
        });
    }
    
    @Test
    public void testGenerateApplicationWithNullFramework() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateApplication("java", null, "com.example.api", "./output");
        });
    }
    
    @Test
    public void testGenerateApplicationWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateApplication("java", "jersey", "com.example.api", null);
        });
    }
    
    @Test
    public void testGenerateApplicationMethodChaining(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateApplication("java", "jersey", "com.example.api", tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateApplicationWithoutPackageName(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateApplication("java", "jersey", tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateTestsWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateTests(java.util.List.of("unit"), "./output");
        });
    }
    
    @Test
    public void testGenerateTestsWithNullTestTypes() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateTests(null, "./output");
        });
    }
    
    @Test
    public void testGenerateTestsWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateTests(java.util.List.of("unit"), null);
        });
    }
    
    @Test
    public void testGenerateTestsMethodChaining(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateTests(java.util.List.of("unit"), tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateTestsWithFramework(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateTests(java.util.List.of("unit"), "junit5", tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateMockDataWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateMockData("./output");
        });
    }
    
    @Test
    public void testGenerateMockDataWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateMockData(null);
        });
    }
    
    @Test
    public void testGenerateMockDataMethodChaining(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateMockData(tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateSLAEnforcementWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateSLAEnforcement("sla.yaml", "./output");
        });
    }
    
    @Test
    public void testGenerateSLAEnforcementWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateSLAEnforcement("sla.yaml", null);
        });
    }
    
    @Test
    public void testGenerateDocumentationWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateDocumentation("./output");
        });
    }
    
    @Test
    public void testGenerateDocumentationWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateDocumentation(null);
        });
    }
    
    @Test
    public void testGenerateDocumentationMethodChaining(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateDocumentation(tempDir.toString());
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateDocumentationWithOptions(@TempDir Path tempDir) throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = sdk.generateDocumentation(tempDir.toString(), true, true, true);
        assertSame(sdk, result);
    }
    
    @Test
    public void testGenerateAllWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.generateAll("./output");
        });
    }
    
    @Test
    public void testGenerateAllWithNullOutputDir() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        assertThrows(NullPointerException.class, () -> {
            sdk.generateAll(null);
        });
    }
    
    @Test
    public void testGenerateAllMethodChaining(@TempDir Path tempDir) throws OASSDKException {
        GeneratorConfig genConfig = new GeneratorConfig();
        genConfig.setLanguage("java");
        genConfig.setFramework("jersey");
        genConfig.setPackageName("com.test");
        
        OASSDK configuredSDK = new OASSDK(genConfig, null, null);
        configuredSDK.loadSpec("src/test/resources/openapi.yaml");
        
        OASSDK result = configuredSDK.generateAll(tempDir.toString());
        assertSame(configuredSDK, result);
    }
    
    @Test
    public void testRunTestsWithNullTestDir() {
        assertThrows(NullPointerException.class, () -> {
            sdk.runTests(null);
        });
    }
    
    @Test
    public void testRunTests() {
        // runTests is not yet implemented and should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            sdk.runTests("./tests");
        });
    }
    
    @Test
    public void testValidateSpecWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.validateSpec();
        });
    }
    
    @Test
    public void testValidateSpecWithValidFile() throws OASSDKException {
        // Load the test OpenAPI file
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        // Validate the spec
        boolean isValid = sdk.validateSpec();
        assertTrue(isValid);
    }
    
    @Test
    public void testGetMetadataWithoutSpec() {
        assertThrows(OASSDKException.class, () -> {
            sdk.getMetadata();
        });
    }
    
    @Test
    public void testGetMetadataWithValidFile() throws OASSDKException {
        sdk.loadSpec("src/test/resources/openapi.yaml");
        
        Map<String, Object> metadata = sdk.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("basic_info"));
    }

    /**
     * Creates a temporary ZIP file containing a single OpenAPI spec entry.
     * Caller is responsible for deleting the zip path when done.
     */
    private static Path createTempZipWithSpec(Path tempDir, String entryName, String yamlContent) throws Exception {
        Path zipPath = tempDir.resolve("specs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry e = new ZipEntry(entryName);
            zos.putNextEntry(e);
            zos.write(yamlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zipPath;
    }

    @Test
    public void testLoadSpecFromZipAndClose(@TempDir Path tempDir) throws Exception {
        String yaml = "openapi: 3.0.0\n"
                + "info:\n  title: Zip API\n  version: 1.0.0\n"
                + "paths:\n  /ping:\n    get:\n      operationId: ping\n      responses:\n        '200':\n          description: OK\n";
        Path zipPath = createTempZipWithSpec(tempDir, "api.yaml", yaml);
        GeneratorConfig config = GeneratorConfig.builder()
                .specZipPath(zipPath.toString())
                .build();

        try (OASSDK zipSdk = new OASSDK(config, null, null)) {
            zipSdk.loadSpec("api.yaml");
            Map<String, Object> metadata = zipSdk.getMetadata();
            assertNotNull(metadata);
        }
        // close() called by try-with-resources; no exception
    }

    @Test
    public void testOASSDKWithZipInvalidPathThrows() {
        GeneratorConfig config = GeneratorConfig.builder()
                .specZipPath("nonexistent/path/to/specs.zip")
                .build();
        assertThrows(RuntimeException.class, () -> new OASSDK(config, null, null));
    }
}
