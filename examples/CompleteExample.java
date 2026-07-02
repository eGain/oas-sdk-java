package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.List;
import java.util.Map;

/**
 * Complete example demonstrating all SDK features
 */
public class CompleteExample {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== OAS SDK Complete Example ===\n");
            
            // Create SDK instance with configurations
            GeneratorConfig generatorConfig = new GeneratorConfig();
            generatorConfig.setLanguage("java");
            generatorConfig.setFramework("jersey");
            generatorConfig.setPackageName("com.example.helloapi");
            
            TestConfig testConfig = new TestConfig();
            testConfig.setTestFramework("junit5");
            testConfig.setIncludePerformanceTests(true);
            testConfig.setIncludeSecurityTests(true);
            
            SLAConfig slaConfig = new SLAConfig();
            slaConfig.setSlaFile("sla.yaml");
            slaConfig.setMonitoringStack(List.of("prometheus", "grafana"));
            
            OASSDK sdk = new OASSDK(generatorConfig, testConfig, slaConfig);
            
            // Step 1: Load specifications
            System.out.println("1. Loading OpenAPI specification...");
            sdk.loadSpec("src/test/resources/openapi.yaml");
            System.out.println("   ✓ OpenAPI spec loaded successfully");
            
            System.out.println("2. Loading SLA specification...");
            sdk.loadSLA("src/test/resources/sla.yaml");
            System.out.println("   ✓ SLA spec loaded successfully");
            
            // Step 2: Validate specifications
            System.out.println("3. Validating OpenAPI specification...");
            boolean isValid = sdk.validateSpec();
            System.out.println("   ✓ OpenAPI spec is valid: " + isValid);
            
            // Step 3: Extract metadata
            System.out.println("4. Extracting metadata...");
            Map<String, Object> metadata = sdk.getMetadata();
            System.out.println("   ✓ Metadata extracted: " + metadata.keySet().size() + " sections");
            
            // Step 4: Generate application code
            System.out.println("5. Generating Jersey application...");
            sdk.generateApplication("java", "jersey", "com.example.helloapi", "./generated/hello-api");
            System.out.println("   ✓ Jersey application generated");
            
            // Step 5: Generate comprehensive test suite
            System.out.println("6. Generating test suite...");
            sdk.generateTests(List.of("unit", "integration", "nfr", "performance", "security"), "junit5", "./generated/tests");
            System.out.println("   ✓ Test suite generated");
            
            // Step 6: Generate Postman collection
            System.out.println("7. Generating Postman collection...");
            sdk.generateTests(List.of("postman"), null, "./generated/postman");
            System.out.println("   ✓ Postman collection generated");
            
            // Step 7: Generate mock data
            System.out.println("8. Generating mock data...");
            sdk.generateMockData("./generated/mock-data");
            System.out.println("   ✓ Mock data generated");
            
            // Step 8: Generate SLA enforcement
            System.out.println("9. Generating SLA enforcement...");
            sdk.generateSLAEnforcement("src/test/resources/sla.yaml", "./generated/sla-enforcement");
            System.out.println("   ✓ SLA enforcement generated");
            
            // Step 9: Generate monitoring setup
            System.out.println("10. Generating monitoring setup...");
            sdk.generateMonitoring(List.of("prometheus", "grafana"), "./generated/monitoring");
            System.out.println("   ✓ Monitoring setup generated");
            
            // Step 10: Generate documentation
            System.out.println("11. Generating documentation...");
            sdk.generateDocumentation("./generated/docs");
            System.out.println("   ✓ Documentation generated");
            
            // Step 11: Generate complete project
            System.out.println("12. Generating complete project...");
            sdk.generateAll("./generated/complete-project");
            System.out.println("   ✓ Complete project generated");
            
            // Step 12: Run tests (if available)
            System.out.println("13. Running generated tests...");
            boolean testsPassed = sdk.runTests("./generated/tests");
            System.out.println("   ✓ Tests completed: " + (testsPassed ? "PASSED" : "FAILED"));
            
            System.out.println("\n=== Generation Complete ===");
            System.out.println("Generated artifacts:");
            System.out.println("  - Jersey application: ./generated/hello-api/");
            System.out.println("  - Test suite: ./generated/tests/");
            System.out.println("  - Postman collection: ./generated/postman/");
            System.out.println("  - Mock data: ./generated/mock-data/");
            System.out.println("  - SLA enforcement: ./generated/sla-enforcement/");
            System.out.println("  - Monitoring: ./generated/monitoring/");
            System.out.println("  - Documentation: ./generated/docs/");
            System.out.println("  - Complete project: ./generated/complete-project/");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
