package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;

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
            
            // Verify generated files
            System.out.println("\n3. Verifying generated files...");
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
            System.out.println("\nNote: Executor files have been generated in the executor package.");
            System.out.println("      You can now implement the business logic in the TODO sections.");
            
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
