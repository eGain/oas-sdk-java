package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.List;

/**
 * Example usage of the OAS SDK
 */
public class HelloWorldExample {
    
    public static void main(String[] args) {
        try {
            // Create SDK instance
            OASSDK sdk = new OASSDK();
            
            // Load OpenAPI specification
            sdk.loadSpec("openapi.yaml");
            
            // Load SLA specification
            sdk.loadSLA("sla.yaml");
            
            // Generate Jersey application
            sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
            
            // Generate tests
            sdk.generateTests(List.of("unit", "integration", "nfr"), "junit5", "./generated-tests");
            
            // Generate Postman collection
            sdk.generateTests(List.of("postman"), null, "./generated-postman");
            
            // Generate mock data
            sdk.generateMockData("./generated-mock-data");
            
            // Generate SLA enforcement
            sdk.generateSLAEnforcement("sla.yaml", "./generated-sla");
            
            // Generate monitoring
            sdk.generateMonitoring(List.of("prometheus", "grafana"), "./generated-monitoring");
            
            // Generate documentation
            sdk.generateDocumentation("./generated-docs");
            
            // Generate complete project
            sdk.generateAll("./complete-project");
            
            System.out.println("All artifacts generated successfully!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}