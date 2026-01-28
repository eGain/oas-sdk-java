package egain.oassdk.integration;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Test to generate SDK from bundle-openapi 3.yaml
 */
@DisplayName("Generate SDK from bundle-openapi 3.yaml")
public class GenerateBundleSDKTest {
    
    @Test
    @DisplayName("Generate SDK from bundle-openapi 3.yaml with executors")
    public void testGenerateBundleSDK() throws OASSDKException, IOException {
        String yamlFile = "examples/bundle-openapi 3.yaml";
        String packageName = "egain.ws.v4.access";
        String outputDir = "./generated-code/bundle-sdk";
        
        System.out.println("\n=== Generating SDK from bundle-openapi 3.yaml ===");
        System.out.println("Output directory: " + outputDir);
        
        // Create SDK instance
        OASSDK sdk = new OASSDK();
        
        // Load specification
        System.out.println("\n1. Loading OpenAPI specification...");
        sdk.loadSpec(yamlFile);
        System.out.println("   ✓ Specification loaded");
        
        // Generate application
        System.out.println("\n2. Generating Jersey application...");
        System.out.println("   Package: " + packageName);
        System.out.println("   Output: " + outputDir);
        sdk.generateApplication("java", "jersey", packageName, outputDir);
        System.out.println("   ✓ Application generated");
        
        // Verify generated files
        System.out.println("\n3. Verifying generated files...");
        Path outputPath = Paths.get(outputDir);
        assertTrue(Files.exists(outputPath), "Output directory should exist");
        
        Path pomFile = outputPath.resolve("pom.xml");
        assertTrue(Files.exists(pomFile), "pom.xml should exist");
        System.out.println("   ✓ pom.xml exists");
        
        Path srcMainJava = outputPath.resolve("src/main/java");
        assertTrue(Files.exists(srcMainJava), "src/main/java should exist");
        System.out.println("   ✓ src/main/java directory exists");
        
        // Check for executor directory
        Path executorDir = srcMainJava.resolve(packageName.replace(".", "/")).resolve("executor");
        if (Files.exists(executorDir)) {
            System.out.println("   ✓ Executor directory exists: " + executorDir);
            try (Stream<Path> paths = Files.list(executorDir)) {
                long executorCount = paths
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .count();
                System.out.println("   ✓ Generated " + executorCount + " executor file(s)");
                
                // List a few executor files as examples
                try (Stream<Path> executorFiles = Files.list(executorDir)) {
                    executorFiles
                        .filter(p -> p.toString().endsWith("BOExecutor.java"))
                        .limit(5)
                        .forEach(p -> System.out.println("      - " + p.getFileName()));
                }
            } catch (IOException e) {
                System.out.println("   ⚠ Could not list executor files: " + e.getMessage());
            }
        } else {
            System.out.println("   ⚠ Executor directory not found: " + executorDir);
        }
        
        System.out.println("\n=== SDK Generation Complete ===");
        System.out.println("Generated SDK location: " + outputPath.toAbsolutePath());
        System.out.println("\nNote: Executor files have been generated in the executor package.");
        System.out.println("      You can now implement the business logic in the TODO sections.");
    }
}
