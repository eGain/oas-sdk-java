package egain.oassdk.integration;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;

/**
 * Test to generate SDK from openapi3.yaml
 */
@DisplayName("Generate SDK from openapi3.yaml")
public class GenerateOpenAPI18NovSDKTest {
    
    @Test
    @DisplayName("Generate OpenAPI 3 SDK")
    public void testGenerateOpenAPI3SDK() throws OASSDKException, IOException {
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.egain.openapi3.api";
        Path outputDir = Paths.get("generated-code/openapi3");
        
        System.out.println("\n=== Generating SDK from openapi3.yaml ===");
        System.out.println("Output directory: " + outputDir.toAbsolutePath());
        
        // Create SDK instance
        OASSDK sdk = new OASSDK();
        
        // Load specification
        System.out.println("\n1. Loading OpenAPI specification...");
        sdk.loadSpec(yamlFile);
        System.out.println("   ✓ Specification loaded");
        
        // Generate application
        System.out.println("\n2. Generating Jersey application...");
        System.out.println("   Package: " + packageName);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        System.out.println("   ✓ Application generated");
        
        // Verify generated files
        System.out.println("\n3. Verifying generated files...");
        assertTrue(Files.exists(outputDir), "Output directory should exist");
        
        Path pomFile = outputDir.resolve("pom.xml");
        assertTrue(Files.exists(pomFile), "pom.xml should exist");
        System.out.println("   ✓ pom.xml exists");
        
        Path srcMainJava = outputDir.resolve("src/main/java");
        assertTrue(Files.exists(srcMainJava), "src/main/java should exist");
        System.out.println("   ✓ src/main/java directory exists");
        
        // Count generated Java files
        long javaFileCount = Files.walk(outputDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .count();
        
        System.out.println("   ✓ Generated " + javaFileCount + " Java files");
        
        // Verify Validation package was generated
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        if (Files.exists(validationDir)) {
            long validationJavaCount = Files.list(validationDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .count();
            long validationTxtCount = Files.list(validationDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .count();
            System.out.println("   ✓ Validation package generated with " + validationJavaCount
                + " validator classes and " + validationTxtCount + " merge artifacts");
        } else {
            System.out.println("   ⚠ Validation package directory not found");
        }

        // Verify ValidationMapHelper has validate method
        Path validationMapHelper = outputDir.resolve(
            "src/main/java/com/egain/openapi3/api/ValidationMapHelper.java");
        if (Files.exists(validationMapHelper)) {
            String content = new String(Files.readAllBytes(validationMapHelper));
            if (content.contains("public static") && content.contains("validate(")) {
                System.out.println("   ✓ ValidationMapHelper.validate() method exists");
            } else {
                System.out.println("   ⚠ ValidationMapHelper.validate() method not found");
            }
        }
        
        System.out.println("\n=== SDK Generation Complete ===");
        System.out.println("Generated SDK location: " + outputDir.toAbsolutePath());
    }
}
