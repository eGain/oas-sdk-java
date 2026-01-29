package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

/**
 * Generate SDK from bundle-openapi 3.yaml
 */
public class GenerateBundleSDK {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Generating SDK from bundle-openapi 3.yaml ===\n");
            
            // Create SDK instance
            OASSDK sdk = new OASSDK();
            
            // Load OpenAPI specification
            System.out.println("1. Loading OpenAPI specification...");
            String specFile = "examples/bundle-openapi 3.yaml";
            sdk.loadSpec(specFile);
            System.out.println("   ✓ Specification loaded: " + specFile);
            
            // Generate Jersey application
            System.out.println("\n2. Generating Jersey application...");
            String packageName = "egain.ws.v4.access";
            String outputDir = "./generated-code/bundle-sdk";
            
            System.out.println("   Package: " + packageName);
            System.out.println("   Output: " + outputDir);
            
            sdk.generateApplication("java", "jersey", packageName, outputDir);
            System.out.println("   ✓ Application generated successfully");
            
            // Generate tests
            System.out.println("\n3. Generating tests...");
            String testOutputDir = "./generated-code/bundle-sdk-tests";
            List<String> testTypes = List.of("unit", "integration", "nfr", "postman");
            System.out.println("   Test types: " + String.join(", ", testTypes));
            System.out.println("   Output: " + testOutputDir);
            sdk.generateTests(testTypes, testOutputDir);
            System.out.println("   ✓ Tests generated successfully");
            
            // Verify generated files
            System.out.println("\n4. Verifying generated files...");
            java.nio.file.Path outputPath = java.nio.file.Paths.get(outputDir);
            if (java.nio.file.Files.exists(outputPath)) {
                System.out.println("   ✓ Output directory exists: " + outputPath.toAbsolutePath());
                
                // Check for executor directory
                java.nio.file.Path executorDir = outputPath.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
                if (java.nio.file.Files.exists(executorDir)) {
                    System.out.println("   ✓ Executor directory exists");
                    try {
                        long executorCount = java.nio.file.Files.list(executorDir)
                            .filter(p -> p.toString().endsWith("BOExecutor.java"))
                            .count();
                        System.out.println("   ✓ Generated " + executorCount + " executor file(s)");
                    } catch (Exception e) {
                        System.out.println("   ⚠ Could not count executor files: " + e.getMessage());
                    }
                }
            }
            
            System.out.println("\n=== SDK Generation Complete ===");
            System.out.println("Generated SDK location: " + outputPath.toAbsolutePath());
            System.out.println("Generated Tests location: " + java.nio.file.Paths.get(testOutputDir).toAbsolutePath());
            System.out.println("\nNote: Executor files have been generated in the executor package.");
            System.out.println("      You can now implement the business logic in the TODO sections.");
            System.out.println("      Tests (unit, integration, NFR, Postman) have been generated.");
            
        } catch (OASSDKException e) {
            System.err.println("\n❌ Error generating SDK: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("\n❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
