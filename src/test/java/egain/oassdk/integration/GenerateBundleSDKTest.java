package egain.oassdk.integration;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;

/**
 * Test to generate SDK from bundle-openapi 3.yaml
 */
@DisplayName("Generate SDK from bundle-openapi 3.yaml")
public class GenerateBundleSDKTest {

    @TempDir
    Path tempOutputDir;
    
    @Test
    @DisplayName("Generate SDK from bundle-openapi 3.yaml")
    public void testGenerateBundleSDK() throws OASSDKException, IOException {
        String yamlFile = "examples/bundle-openapi 3.yaml";
        String packageName = "egain.ws.v4.access";
        Path outputDir = tempOutputDir.resolve("bundle-sdk");
        
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
        System.out.println("   Output: " + outputDir.toAbsolutePath());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        System.out.println("   ✓ Application generated");
        
        // Verify generated files
        System.out.println("\n3. Verifying generated files...");
        Path outputPath = outputDir;
        assertTrue(Files.exists(outputPath), "Output directory should exist");
        
        Path pomFile = outputPath.resolve("pom.xml");
        assertTrue(Files.exists(pomFile), "pom.xml should exist");
        System.out.println("   ✓ pom.xml exists");
        
        Path srcMainJava = outputPath.resolve("src/main/java");
        assertTrue(Files.exists(srcMainJava), "src/main/java should exist");
        System.out.println("   ✓ src/main/java directory exists");

        Path modelDir = srcMainJava.resolve(packageName.replace(".", "/")).resolve("model");
        assertTrue(Files.exists(modelDir), "model package directory should exist");
        System.out.println("   ✓ model directory exists: " + modelDir);
        
        System.out.println("\n=== SDK Generation Complete ===");
        System.out.println("Generated SDK location: " + outputPath.toAbsolutePath());
        System.out.println("\nNote: You can now implement the business logic in the TODO sections.");
    }
}
