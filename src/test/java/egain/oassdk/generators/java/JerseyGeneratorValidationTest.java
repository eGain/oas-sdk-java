package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive test cases for JerseyGenerator validation class generation methods
 */
@DisplayName("JerseyGenerator Validation Class Generation Tests")
public class JerseyGeneratorValidationTest {
    
    @TempDir
    Path tempOutputDir;
    
    // Expected validation classes
    private static final List<String> EXPECTED_VALIDATION_CLASSES = Arrays.asList(
        "IsRequiredValidator",
        "PatternValidator",
        "MaxLengthValidator",
        "MinLengthValidator",
        "NumericMaxValidator",
        "NumericMinValidator",
        "NumericMultipleOfValidator",
        "EnumValidator",
        "BooleanValidator",
        "FormatValidator",
        "AllowedParameterValidator",
        "ArrayMaxItemsValidators",
        "ArrayMinItemsValidator",
        "ArrayUniqueItemsValidators",
        "ArraySimpleStyleValidator",
        "IsAllowEmptyValueValidator",
        "IsAllowReservedValidator"
    );
    
    @Test
    @DisplayName("Test that all validation classes are generated")
    public void testAllValidationClassesGenerated() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Check validation directory exists
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        assertTrue(Files.exists(validationDir), "Validation directory should exist");
        assertTrue(Files.isDirectory(validationDir), "Validation path should be a directory");
        
        // Get all generated validation class files
        List<String> generatedClasses = Files.list(validationDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .map(path -> path.getFileName().toString().replace(".java", ""))
            .collect(Collectors.toList());
        
        System.out.println("Generated validation classes: " + generatedClasses);
        System.out.println("Expected validation classes: " + EXPECTED_VALIDATION_CLASSES);
        
        // Verify all expected classes are generated
        for (String expectedClass : EXPECTED_VALIDATION_CLASSES) {
            assertTrue(generatedClasses.contains(expectedClass),
                "Validation class " + expectedClass + " should be generated");
        }
        
        assertEquals(EXPECTED_VALIDATION_CLASSES.size(), generatedClasses.size(),
            "All expected validation classes should be generated");
    }
    
    @Test
    @DisplayName("Test validation classes have correct package name")
    public void testValidationClassesPackageName() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-package-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        
        // Check a few key validation classes for correct package
        String[] classesToCheck = {
            "IsRequiredValidator",
            "PatternValidator",
            "MaxLengthValidator",
            "EnumValidator"
        };
        
        for (String className : classesToCheck) {
            Path classFile = validationDir.resolve(className + ".java");
            assertTrue(Files.exists(classFile), className + " should exist");
            
            String content = Files.readString(classFile);
            assertTrue(content.contains("package egain.ws.oas.validation;"),
                className + " should have package egain.ws.oas.validation");
            assertFalse(content.contains("package egain.ws.oas.Validation;"),
                className + " should not have uppercase Validation package");
        }
    }
    
    @Test
    @DisplayName("Test validation classes implement ValidatorAction interface")
    public void testValidationClassesImplementValidatorAction() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-interface-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        
        // Check all validation classes implement ValidatorAction
        List<Path> validationFiles = Files.list(validationDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path validationFile : validationFiles) {
            String content = Files.readString(validationFile);
            String className = validationFile.getFileName().toString();
            
            assertTrue(content.contains("implements ValidatorAction<RequestInfo>"),
                className + " should implement ValidatorAction<RequestInfo>");
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidatorAction;"),
                className + " should import ValidatorAction");
            assertTrue(content.contains("import egain.ws.oas.RequestInfo;"),
                className + " should import RequestInfo");
        }
    }
    
    @Test
    @DisplayName("Test IsRequiredValidator structure")
    public void testIsRequiredValidatorStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-structure-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/IsRequiredValidator.java");
        assertTrue(Files.exists(validatorFile), "IsRequiredValidator should exist");
        
        String content = Files.readString(validatorFile);
        
        // Check class structure
        assertTrue(content.contains("public class IsRequiredValidator"),
            "Should be a public class");
        assertTrue(content.contains("private final String parameterName"),
            "Should have parameterName field");
        assertTrue(content.contains("private final String requiredParameter"),
            "Should have requiredParameter field");
        assertTrue(content.contains("public ValidationError call(RequestInfo val)"),
            "Should have call method");
        assertTrue(content.contains("ValidationErrorHelper.createValidationError"),
            "Should use ValidationErrorHelper");
    }
    
    @Test
    @DisplayName("Test PatternValidator structure")
    public void testPatternValidatorStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("pattern-validator-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/PatternValidator.java");
        assertTrue(Files.exists(validatorFile), "PatternValidator should exist");
        
        String content = Files.readString(validatorFile);
        
        // Check class structure
        assertTrue(content.contains("public class PatternValidator"),
            "Should be a public class");
        assertTrue(content.contains("private final String val"),
            "Should have pattern value field");
        assertTrue(content.contains("Validations.matchesPattern.apply"),
            "Should use Validations.matchesPattern");
        assertTrue(content.contains("import egain.ws.oas.Validations;"),
            "Should import Validations");
    }
    
    @Test
    @DisplayName("Test EnumValidator structure")
    public void testEnumValidatorStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("enum-validator-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/EnumValidator.java");
        assertTrue(Files.exists(validatorFile), "EnumValidator should exist");
        
        String content = Files.readString(validatorFile);
        
        // Check class structure
        assertTrue(content.contains("public class EnumValidator"),
            "Should be a public class");
        assertTrue(content.contains("private final String enumValues"),
            "Should have enumValues field");
        assertTrue(content.contains("Arrays.asList(enumValues.split(\",\"))"),
            "Should split enum values by comma");
        assertTrue(content.contains("import java.util.Arrays;"),
            "Should import Arrays");
    }
    
    @Test
    @DisplayName("Test FormatValidator structure")
    public void testFormatValidatorStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("format-validator-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/FormatValidator.java");
        assertTrue(Files.exists(validatorFile), "FormatValidator should exist");
        
        String content = Files.readString(validatorFile);
        
        // Check class structure
        assertTrue(content.contains("public class FormatValidator"),
            "Should be a public class");
        assertTrue(content.contains("EMAIL_PATTERN"),
            "Should have EMAIL_PATTERN constant");
        assertTrue(content.contains("URI_PATTERN"),
            "Should have URI_PATTERN constant");
        assertTrue(content.contains("UUID_PATTERN"),
            "Should have UUID_PATTERN constant");
        assertTrue(content.contains("getPatternForFormat"),
            "Should have getPatternForFormat method");
        assertTrue(content.contains("import java.util.regex.Pattern;"),
            "Should import Pattern");
    }
    
    @Test
    @DisplayName("Test AllowedParameterValidator structure")
    public void testAllowedParameterValidatorStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("allowed-param-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/AllowedParameterValidator.java");
        assertTrue(Files.exists(validatorFile), "AllowedParameterValidator should exist");
        
        String content = Files.readString(validatorFile);
        
        // Check class structure
        assertTrue(content.contains("public class AllowedParameterValidator"),
            "Should be a public class");
        assertTrue(content.contains("private final List<String> allowedParameters"),
            "Should have allowedParameters field");
        assertTrue(content.contains("val.queryParameters().keySet()"),
            "Should check query parameters");
        assertTrue(content.contains("L10N_INVALID_QUERY_PARAMETER"),
            "Should use correct error code");
    }
    
    @Test
    @DisplayName("Test numeric validators structure")
    public void testNumericValidatorsStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("numeric-validators-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Test NumericMaxValidator
        Path maxValidatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/NumericMaxValidator.java");
        assertTrue(Files.exists(maxValidatorFile), "NumericMaxValidator should exist");
        String maxContent = Files.readString(maxValidatorFile);
        assertTrue(maxContent.contains("Double.parseDouble(maximum)"),
            "NumericMaxValidator should parse maximum as double");
        assertTrue(maxContent.contains("val > maxVal"),
            "NumericMaxValidator should check if value > maxVal");
        
        // Test NumericMinValidator
        Path minValidatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/NumericMinValidator.java");
        assertTrue(Files.exists(minValidatorFile), "NumericMinValidator should exist");
        String minContent = Files.readString(minValidatorFile);
        assertTrue(minContent.contains("Double.parseDouble(minimum)"),
            "NumericMinValidator should parse minimum as double");
        assertTrue(minContent.contains("val < minVal"),
            "NumericMinValidator should check if value < minVal");
        
        // Test NumericMultipleOfValidator
        Path multipleOfValidatorFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/NumericMultipleOfValidator.java");
        assertTrue(Files.exists(multipleOfValidatorFile), "NumericMultipleOfValidator should exist");
        String multipleOfContent = Files.readString(multipleOfValidatorFile);
        assertTrue(multipleOfContent.contains("Math.abs(val % multiple)"),
            "NumericMultipleOfValidator should check modulo");
    }
    
    @Test
    @DisplayName("Test array validators structure")
    public void testArrayValidatorsStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("array-validators-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Test ArrayMaxItemsValidators
        Path maxItemsFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/ArrayMaxItemsValidators.java");
        assertTrue(Files.exists(maxItemsFile), "ArrayMaxItemsValidators should exist");
        String maxItemsContent = Files.readString(maxItemsFile);
        assertTrue(maxItemsContent.contains("private final int maxItems"),
            "Should have maxItems as int");
        assertTrue(maxItemsContent.contains("items.length > maxItems"),
            "Should check array length > maxItems");
        
        // Test ArrayMinItemsValidator
        Path minItemsFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/ArrayMinItemsValidator.java");
        assertTrue(Files.exists(minItemsFile), "ArrayMinItemsValidator should exist");
        String minItemsContent = Files.readString(minItemsFile);
        assertTrue(minItemsContent.contains("private final int minItems"),
            "Should have minItems as int");
        assertTrue(minItemsContent.contains("items.length < minItems"),
            "Should check array length < minItems");
        
        // Test ArrayUniqueItemsValidators
        Path uniqueItemsFile = outputDir.resolve("src/main/java/egain/ws/oas/validation/ArrayUniqueItemsValidators.java");
        assertTrue(Files.exists(uniqueItemsFile), "ArrayUniqueItemsValidators should exist");
        String uniqueItemsContent = Files.readString(uniqueItemsFile);
        assertTrue(uniqueItemsContent.contains("Set<String> seen"),
            "Should use Set to track seen items");
        assertTrue(uniqueItemsContent.contains("import java.util.HashSet;"),
            "Should import HashSet");
    }
    
    @Test
    @DisplayName("Test validation classes are in correct directory structure")
    public void testValidationClassesDirectoryStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-dir-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify directory structure
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        assertTrue(Files.exists(validationDir), "Validation directory should exist");
        assertTrue(Files.isDirectory(validationDir), "Should be a directory");
        
        // Verify it's not at root level
        Path rootValidation = outputDir.resolve("Validation");
        assertFalse(Files.exists(rootValidation), "Validation should not be at root level");
        
        // Verify correct package path
        String expectedPath = "src/main/java/egain/ws/oas/validation";
        assertTrue(validationDir.toString().replace("\\", "/").endsWith(expectedPath),
            "Validation directory should be at " + expectedPath);
    }
    
    @Test
    @DisplayName("Test validation classes have correct imports")
    public void testValidationClassesImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-imports-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        
        // Check common imports across all validators
        List<Path> validationFiles = Files.list(validationDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path validationFile : validationFiles) {
            String content = Files.readString(validationFile);
            String className = validationFile.getFileName().toString();
            
            // Common imports that should be present
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationError;"),
                className + " should import ValidationError");
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationErrorHelper;"),
                className + " should import ValidationErrorHelper");
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidatorAction;"),
                className + " should import ValidatorAction");
            assertTrue(content.contains("import egain.ws.oas.RequestInfo;"),
                className + " should import RequestInfo");
        }
    }
    
    @Test
    @DisplayName("Test validation classes integration with QueryParamValidators")
    public void testValidationClassesIntegration() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-integration-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Check QueryParamValidators imports validation classes
        Path queryParamValidatorsFile = outputDir.resolve("src/main/java/egain/ws/oas/gen/QueryParamValidators.java");
        assertTrue(Files.exists(queryParamValidatorsFile), "QueryParamValidators should exist");
        
        String content = Files.readString(queryParamValidatorsFile);
        
        // Verify imports use lowercase validation package
        assertTrue(content.contains("import egain.ws.oas.validation.IsRequiredValidator;"),
            "Should import IsRequiredValidator from lowercase validation package");
        assertTrue(content.contains("import egain.ws.oas.validation.PatternValidator;"),
            "Should import PatternValidator from lowercase validation package");
        assertFalse(content.contains("import egain.ws.oas.Validation."),
            "Should not import from uppercase Validation package");
    }
    
    @Test
    @DisplayName("Test validation classes are generated with correct file structure")
    public void testValidationClassesFileStructure() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-file-structure-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        
        // Check that all files are valid Java files
        List<Path> validationFiles = Files.list(validationDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path validationFile : validationFiles) {
            String content = Files.readString(validationFile);
            String className = validationFile.getFileName().toString().replace(".java", "");
            
            // Basic structure checks
            assertTrue(content.contains("public class " + className),
                className + " should be a public class");
            assertTrue(content.contains("{"),
                className + " should have opening brace");
            assertTrue(content.contains("}"),
                className + " should have closing brace");
            assertTrue(content.length() > 100,
                className + " should have substantial content");
        }
    }

    @Test
    @DisplayName("Test readOnly and writeOnly generate correct JsonProperty access annotations")
    public void testReadOnlyWriteOnlyJsonPropertyAccess() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("readonly-writeonly-test");
        String yamlFile = "src/test/resources/openapi-readonly-writeonly.yaml";
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path modelDir = outputDir.resolve("src/main/java/com/test/api/model");
        Path userModel = modelDir.resolve("User.java");
        assertTrue(Files.exists(userModel), "User model should be generated");

        String content = Files.readString(userModel);

        assertTrue(content.contains("JsonProperty.Access.READ_ONLY"),
            "Generated model should contain @JsonProperty(access = JsonProperty.Access.READ_ONLY) for readOnly property");
        assertTrue(content.contains("JsonProperty.Access.WRITE_ONLY"),
            "Generated model should contain @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) for writeOnly property");

        // readOnly (id): getter only, no setter
        assertTrue(content.contains("public String getId()"),
            "readOnly property should have getter");
        assertFalse(content.contains("public void setId("),
            "readOnly property should not have setter");

        // writeOnly (password): setter only, no getter
        assertTrue(content.contains("public void setPassword("),
            "writeOnly property should have setter");
        assertFalse(content.contains("public String getPassword()"),
            "writeOnly property should not have getter");

        // setAttribute must not call setId for readOnly property (setId does not exist)
        assertTrue(content.contains("return; // readOnly, no setter"),
            "setAttribute should no-op for readOnly properties instead of calling missing setter");
        assertFalse(content.contains("setId(("),
            "setAttribute must not call setId for readOnly property id");

        // isSetAttribute must not include readOnly property (id) in its switch
        String isSetAttributeSection = content.substring(
            content.indexOf("public boolean isSetAttribute"),
            content.indexOf("public Set<String> getAttributeNames()"));
        assertFalse(isSetAttributeSection.contains("case \"id\":"),
            "isSetAttribute should not have a case for readOnly property id");

        // getAttribute must not include writeOnly property (password) in its switch
        String getAttributeSection = content.substring(
            content.indexOf("public Object getAttribute"),
            content.indexOf("public boolean isSetAttribute"));
        assertFalse(getAttributeSection.contains("case \"password\":"),
            "getAttribute should not have a case for writeOnly property password");

        // getAttributeNames must not add writeOnly property (password)
        String getAttributeNamesSection = content.substring(
            content.indexOf("public Set<String> getAttributeNames()"),
            content.indexOf("public void setAttribute"));
        assertFalse(getAttributeNamesSection.contains("allNames.add(\"password\")"),
            "getAttributeNames should not include writeOnly property password");
    }
}

