package egain.oassdk;

import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test to verify query parameter validation using programmatic validation
 * (Validation is now done via ValidationMapHelper, not annotations)
 */
@DisplayName("Query Parameter Validation and Enum Handling Test")
public class QueryParameterValidationTest {

    private static final String VALIDATION_PACKAGE = "egain.ws.oas.validation";
    private static final String VALIDATION_PACKAGE_PATH = "egain/ws/oas/validation";

    @TempDir
    Path tempOutputDir;
    
    @Test
    @DisplayName("Verify Query Parameter Validation via ValidationMapHelper")
    public void testQueryParameterValidation() throws OASSDKException, IOException {
        String yamlFile = "src/test/resources/openapi4.yaml";
        String packageName = "com.egain.openapi4.api";
        Path outputDir = tempOutputDir.resolve("generated-code/openapi4");
        
        System.out.println("\n=== Testing Query Parameter Validation ===");
        System.out.println("Output directory: " + outputDir.toAbsolutePath());
        
        // Create SDK instance
        OASSDK sdk = new OASSDK();
        
        // Load specification
        System.out.println("\n1. Loading OpenAPI specification...");
        sdk.loadSpec(yamlFile);
        System.out.println("   ✓ Specification loaded");
        
        // Generate application
        System.out.println("\n2. Generating Jersey application...");
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        System.out.println("   ✓ Application generated");
        
        // Verify generated files
        assertTrue(Files.exists(outputDir), "Output directory should exist");
        
        // Check validation infrastructure (QueryParamValidators and ValidationMapHelper)
        System.out.println("\n3. Checking validation infrastructure...");
        checkValidationInfrastructure(outputDir, packageName);
        
        // Check validation classes are generated
        System.out.println("\n4. Checking validation classes generation...");
        checkValidationClassesGenerated(outputDir, packageName);
        
        // Check resources don't have validation annotations (validation is programmatic)
        System.out.println("\n5. Checking resources use programmatic validation...");
        checkResourcesUseProgrammaticValidation(outputDir, packageName);
        
        // Check query parameters exist in resources
        System.out.println("\n6. Checking query parameters in resources...");
        checkQueryParametersInResources(outputDir, packageName);
        
        System.out.println("\n=== All Validation Checks Passed ===");
    }
    
    private void checkValidationInfrastructure(Path outputDir, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");
        Path packageDir = outputDir.resolve("src/main/java").resolve(packagePath);

        // Check QueryParamValidators.java exists (under passed-in package)
        Path queryParamValidators = packageDir.resolve("QueryParamValidators.java");
        assertTrue(Files.exists(queryParamValidators), "QueryParamValidators.java should exist");
        System.out.println("   ✓ QueryParamValidators.java exists");

        String validatorsContent = Files.readString(queryParamValidators);
        assertTrue(validatorsContent.contains("public class QueryParamValidators"),
            "QueryParamValidators should be a public class");
        assertTrue(validatorsContent.contains("import " + VALIDATION_PACKAGE + ".IsRequiredValidator;"),
            "Should import validation classes from package " + VALIDATION_PACKAGE);
        assertTrue(validatorsContent.contains("import " + VALIDATION_PACKAGE + ".PatternValidator;"),
            "Should import PatternValidator from package " + VALIDATION_PACKAGE);

        // Check ValidationMapHelper.java exists and has validate() method (under passed-in package)
        Path validationMapHelper = packageDir.resolve("ValidationMapHelper.java");
        assertTrue(Files.exists(validationMapHelper), "ValidationMapHelper.java should exist");
        System.out.println("   ✓ ValidationMapHelper.java exists");

        String helperContent = Files.readString(validationMapHelper);
        assertTrue(helperContent.contains("public class ValidationMapHelper"),
            "ValidationMapHelper should be a public class");
        assertTrue(helperContent.contains("public static") && helperContent.contains("validate("),
            "ValidationMapHelper should have public static validate() method");
        assertTrue(helperContent.contains("ValidationError validate("),
            "validate() method should return ValidationError");
        System.out.println("   ✓ ValidationMapHelper.validate() method exists");
    }
    
    private void checkValidationClassesGenerated(Path outputDir, String packageName) throws IOException {
        Path validationDir = outputDir.resolve("src/main/java/" + VALIDATION_PACKAGE_PATH);
        assertTrue(Files.exists(validationDir), "Validation directory should exist");
        assertTrue(Files.isDirectory(validationDir), "Validation path should be a directory");

        List<String> validationClasses = Files.list(validationDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .map(path -> path.getFileName().toString().replace(".java", ""))
            .collect(Collectors.toList());
        
        assertTrue(validationClasses.size() >= 10, 
            "At least 10 validation classes should be generated, found: " + validationClasses.size());
        System.out.println("   ✓ " + validationClasses.size() + " validation classes generated");
        
        // Check key validation classes exist
        String[] keyClasses = {"IsRequiredValidator", "PatternValidator", "EnumValidator", 
                              "MaxLengthValidator", "MinLengthValidator"};
        for (String className : keyClasses) {
            assertTrue(validationClasses.contains(className),
                className + " should be generated");
        }
        System.out.println("   ✓ Key validation classes present");
    }
    
    private void checkResourcesUseProgrammaticValidation(Path outputDir, String packageName) throws IOException {
        Path resourcesDir = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("resources");
        
        if (!Files.exists(resourcesDir)) {
            fail("Resources directory should exist: " + resourcesDir);
        }
        
        List<Path> resourceFiles = Files.walk(resourcesDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith("Resource.java"))
            .collect(Collectors.toList());
        
        System.out.println("   Found " + resourceFiles.size() + " resource files");
        
        int filesWithoutValidationAnnotations = 0;
        
        for (Path resourceFile : resourceFiles) {
            String content = Files.readString(resourceFile);
            String fileName = resourceFile.getFileName().toString();
            
            // Resources should NOT have validation annotations (validation is programmatic)
            boolean hasValidationAnnotations = content.contains("@NotNull") || 
                                              content.contains("@Size") || 
                                              content.contains("@Pattern") ||
                                              content.contains("@Min") ||
                                              content.contains("@Max");
            
            if (!hasValidationAnnotations) {
                filesWithoutValidationAnnotations++;
            } else {
                System.out.println("   ⚠ " + fileName + " still has validation annotations (should be removed)");
            }
        }
        
        // All or most resources should not have validation annotations
        assertTrue(filesWithoutValidationAnnotations >= resourceFiles.size() * 0.8,
            "At least 80% of resources should not have validation annotations (validation is programmatic)");
        System.out.println("   ✓ " + filesWithoutValidationAnnotations + "/" + resourceFiles.size() + 
                          " resources use programmatic validation (no annotations)");
        
        // Resources should not import validation constraints
        int filesWithoutValidationImports = 0;
        for (Path resourceFile : resourceFiles) {
            String content = Files.readString(resourceFile);
            if (!content.contains("import javax.validation.constraints")) {
                filesWithoutValidationImports++;
            }
        }
        
        assertTrue(filesWithoutValidationImports >= resourceFiles.size() * 0.8,
            "At least 80% of resources should not import validation constraints");
        System.out.println("   ✓ " + filesWithoutValidationImports + "/" + resourceFiles.size() + 
                          " resources don't import validation constraints");
    }
    
    private void checkQueryParametersInResources(Path outputDir, String packageName) throws IOException {
        Path resourcesDir = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("resources");
        
        List<Path> resourceFiles = Files.walk(resourcesDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith("Resource.java"))
            .collect(Collectors.toList());
        
        boolean foundQueryParams = false;
        
        for (Path resourceFile : resourceFiles) {
            String content = Files.readString(resourceFile);
            String fileName = resourceFile.getFileName().toString();
            
            // Check if file has query parameters
            if (content.contains("@QueryParam")) {
                foundQueryParams = true;
                System.out.println("   ✓ Found query parameters in: " + fileName);
                
                // Show example query parameter (without validation annotations)
                String[] lines = content.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].contains("@QueryParam")) {
                        int start = Math.max(0, i - 2);
                        int end = Math.min(lines.length, i + 2);
                        System.out.println("   Example query parameter (programmatic validation):");
                        for (int j = start; j < end; j++) {
                            String line = lines[j].trim();
                            if (line.startsWith("@QueryParam") || 
                                (line.startsWith("String") && j > i)) {
                                System.out.println("      " + line);
                            }
                        }
                        break;
                    }
                }
            }
        }
        
        assertTrue(foundQueryParams, 
            "At least one resource should have query parameters");
        System.out.println("\n   ✓ Query parameters found in resources");
    }
    
}

