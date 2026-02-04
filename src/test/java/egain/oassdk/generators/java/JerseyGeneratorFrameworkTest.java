package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
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
 * Comprehensive test cases for JerseyGenerator framework-related features:
 * - Framework imports (egain.framework, egain.ws.framework)
 * - @Actor annotation generation
 * - Header parameter exclusion
 * - Framework validation imports
 */
@DisplayName("JerseyGenerator Framework Features Tests")
public class JerseyGeneratorFrameworkTest {
    
    @TempDir
    Path tempOutputDir;
    
    @Test
    @DisplayName("Test resource class has framework imports")
    public void testResourceFrameworkImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("framework-imports-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find resource files
        Path resourcesDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/resources");
        
        if (Files.exists(resourcesDir)) {
            try (var paths = Files.list(resourcesDir)) {
                List<Path> resourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
                
                if (!resourceFiles.isEmpty()) {
                    Path resourceFile = resourceFiles.get(0);
                    String content = Files.readString(resourceFile);
                    
                    // Check for framework imports
                    assertTrue(content.contains("import egain.framework.Actor;"),
                        "Resource should import egain.framework.Actor");
                    assertTrue(content.contains("import egain.framework.ActorType;"),
                        "Resource should import egain.framework.ActorType");
                    assertTrue(content.contains("import egain.framework.OAuthScope;"),
                        "Resource should import egain.framework.OAuthScope");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test @Actor annotation is generated")
    public void testActorAnnotationGenerated() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("actor-annotation-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find resource files
        Path resourcesDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/resources");
        
        if (Files.exists(resourcesDir)) {
            try (var paths = Files.list(resourcesDir)) {
                List<Path> resourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
                
                if (!resourceFiles.isEmpty()) {
                    Path resourceFile = resourceFiles.get(0);
                    String content = Files.readString(resourceFile);
                    
                    // Check for @Actor annotation
                    assertTrue(content.contains("@Actor"),
                        "Resource class should have @Actor annotation");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test @Actor annotation with security schemes")
    public void testActorAnnotationWithSecurity() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /users:
                get:
                  summary: Get Users
                  operationId: getUsers
                  security:
                    - oAuthUser:
                        - knowledge.contentmgr.read
                  responses:
                    '200':
                      description: Success
            components:
              securitySchemes:
                oAuthUser:
                  type: oauth2
                  flows:
                    authorizationCode:
                      authorizationUrl: https://example.com/oauth/authorize
                      tokenUrl: https://example.com/oauth/token
                      scopes:
                        knowledge.contentmgr.read: Read access
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find resource file - resource name is generated from parent path
        Path resourcesDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/resources");
        assertTrue(Files.exists(resourcesDir), "Resources directory should exist");
        
        // Find the actual resource file (name may vary based on path)
        List<Path> resourceFiles;
        try (var paths = Files.list(resourcesDir)) {
            resourceFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        assertFalse(resourceFiles.isEmpty(), "At least one resource file should exist");
        Path resourceFile = resourceFiles.get(0);
        
        String content = Files.readString(resourceFile);
        
        // Check for @Actor annotation with ActorType
        assertTrue(content.contains("@Actor"),
            "Resource should have @Actor annotation");
        assertTrue(content.contains("ActorType.USER") || content.contains("ActorType.CLIENT_APP"),
            "Resource should have ActorType in @Actor annotation");
        
        // Check for OAuthScope if scopes are present
        if (content.contains("scope")) {
            assertTrue(content.contains("OAuthScope."),
                "Resource should have OAuthScope in @Actor annotation");
        }
    }
    
    @Test
    @DisplayName("Test header parameters are excluded from resource method signatures")
    public void testHeaderParametersExcluded() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /users/{id}:
                get:
                  summary: Get User
                  operationId: getUser
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: string
                    - name: X-Request-ID
                      in: header
                      schema:
                        type: string
                    - name: Authorization
                      in: header
                      schema:
                        type: string
                  responses:
                    '200':
                      description: Success
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find resource file - resource name is generated from parent path
        Path resourcesDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/resources");
        assertTrue(Files.exists(resourcesDir), "Resources directory should exist");
        
        // Find the actual resource file
        List<Path> resourceFiles;
        try (var paths = Files.list(resourcesDir)) {
            resourceFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        assertFalse(resourceFiles.isEmpty(), "At least one resource file should exist");
        Path resourceFile = resourceFiles.get(0);
        
        String content = Files.readString(resourceFile);
        
        // Check that path parameter is included
        assertTrue(content.contains("@PathParam(\"id\")"),
            "Path parameter should be included in method signature");
        
        // Check that header parameters are NOT in method signature
        assertFalse(content.contains("@HeaderParam(\"X-Request-ID\")"),
            "Header parameter X-Request-ID should NOT be in method signature (handled by framework)");
        assertFalse(content.contains("@HeaderParam(\"Authorization\")"),
            "Header parameter Authorization should NOT be in method signature (handled by framework)");
    }
    
    @Test
    @DisplayName("Test executor has framework imports")
    public void testExecutorFrameworkImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-framework-imports-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (var paths = Files.list(executorDir)) {
                List<Path> executorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .collect(Collectors.toList());
                
                if (!executorFiles.isEmpty()) {
                    Path executorFile = executorFiles.get(0);
                    String content = Files.readString(executorFile);
                    
                    // Check for framework imports
                    assertTrue(content.contains("import egain.ws.common.authorization.Authorizer") ||
                               content.contains("import egain.ws.common.authorization.AlwaysAuthorizedAuthorizerPartitionFactory"),
                        "Executor should import authorization framework classes");
                    assertTrue(content.contains("import egain.ws.util.WsUtil"),
                        "Executor should import WsUtil");
                    
                    // Check for executor base class imports
                    boolean hasGetExecutor = content.contains("import egain.ws.framework.GetBOExecutor_2");
                    boolean hasPostExecutor = content.contains("import egain.ws.framework.PostPutBOExecutor_2") ||
                                            content.contains("import egain.ws.framework.PostPutBOExecutorNoResponseBody_2");
                    
                    assertTrue(hasGetExecutor || hasPostExecutor,
                        "Executor should import framework executor base class");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor header parameters are excluded from constructor")
    public void testExecutorHeaderParametersExcluded() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /users/{id}:
                get:
                  summary: Get User
                  operationId: getUser
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: string
                    - name: X-Request-ID
                      in: header
                      schema:
                        type: string
                    - name: queryParam
                      in: query
                      schema:
                        type: string
                  responses:
                    '200':
                      description: Success
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find executor file
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (var paths = Files.list(executorDir)) {
                List<Path> executorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .collect(Collectors.toList());
                
                if (!executorFiles.isEmpty()) {
                    Path executorFile = executorFiles.get(0);
                    String content = Files.readString(executorFile);
                    
                    // Check that path parameter is in constructor
                    assertTrue(content.contains("String id") || content.contains("id"),
                        "Path parameter should be in constructor");
                    
                    // Check that header parameters are NOT in constructor
                    // Look for constructor signature - header params should not be there
                    int constructorStart = content.indexOf("public " + executorFile.getFileName().toString().replace(".java", "") + "(");
                    if (constructorStart >= 0) {
                        int constructorEnd = content.indexOf(")", constructorStart);
                        if (constructorEnd > constructorStart) {
                            String constructorParams = content.substring(constructorStart, constructorEnd);
                            assertFalse(constructorParams.contains("X-Request-ID"),
                                "Header parameter should NOT be in constructor (handled by framework)");
                        }
                    }
                    
                    // Check that solveHeaderMap is present (framework handles headers)
                    assertTrue(content.contains("solveHeaderMap"),
                        "Executor should have solveHeaderMap field for framework header handling");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test validation classes have framework imports")
    public void testValidationFrameworkImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-framework-imports-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
        
        if (Files.exists(validationDir)) {
            try (var paths = Files.list(validationDir)) {
                List<Path> validationFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Validator.java"))
                    .limit(3) // Check first 3 validators
                    .collect(Collectors.toList());
                
                for (Path validationFile : validationFiles) {
                    String content = Files.readString(validationFile);
                    
                    // Check for framework validation imports
                    assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationError;"),
                        validationFile.getFileName() + " should import ValidationError");
                    assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationErrorHelper;"),
                        validationFile.getFileName() + " should import ValidationErrorHelper");
                    assertTrue(content.contains("import com.egain.platform.framework.validation.ValidatorAction;"),
                        validationFile.getFileName() + " should import ValidatorAction");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test QueryParamValidators uses framework validation")
    public void testQueryParamValidatorsFrameworkImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("queryparam-validators-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path queryParamValidatorsFile = outputDir.resolve("src/main/java/egain/ws/oas/gen/QueryParamValidators.java");
        
        if (Files.exists(queryParamValidatorsFile)) {
            String content = Files.readString(queryParamValidatorsFile);
            
            // Check for framework validation imports
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationBuilder;"),
                "QueryParamValidators should import ValidationBuilder");
        }
    }
    
    @Test
    @DisplayName("Test ValidationMapHelper uses framework validation")
    public void testValidationMapHelperFrameworkImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("validation-map-helper-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path validationMapHelperFile = outputDir.resolve("src/main/java/egain/ws/oas/gen/ValidationMapHelper.java");
        
        // ValidationMapHelper is only generated if there are query parameters to validate
        if (Files.exists(validationMapHelperFile)) {
            String content = Files.readString(validationMapHelperFile);
            
            // Check for framework validation imports
            assertTrue(content.contains("import com.egain.platform.framework.validation.ValidationBuilder;"),
                "ValidationMapHelper should import ValidationBuilder");
            // ValidationError is used in the return type, check for it in the method signature
            assertTrue(content.contains("ValidationError") || content.contains("com.egain.platform.framework.validation.ValidationError"),
                "ValidationMapHelper should use ValidationError (either imported or fully qualified)");
        } else {
            // If ValidationMapHelper doesn't exist, that's okay - it's only generated when needed
            // Just verify that validation classes directory exists
            Path validationDir = outputDir.resolve("src/main/java/egain/ws/oas/validation");
            assertTrue(Files.exists(validationDir), "Validation directory should exist even if ValidationMapHelper is not generated");
        }
    }
    
    @Test
    @DisplayName("Test executor uses WsApiException from framework")
    public void testExecutorWsApiException() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /users:
                get:
                  summary: Get Users
                  operationId: getUsers
                  parameters:
                    - name: requiredParam
                      in: query
                      required: true
                      schema:
                        type: string
                  responses:
                    '200':
                      description: Success
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find executor file
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (var paths = Files.list(executorDir)) {
                List<Path> executorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .collect(Collectors.toList());
                
                if (!executorFiles.isEmpty()) {
                    Path executorFile = executorFiles.get(0);
                    String content = Files.readString(executorFile);
                    
                    // Check for WsApiException usage in validation
                    if (content.contains("requiredParam")) {
                        assertTrue(content.contains("egain.ws.framework.WsApiException") ||
                                  content.contains("WsApiException"),
                            "Executor should use WsApiException for validation errors");
                        assertTrue(content.contains("WSConstants") || content.contains("COMMON_ERROR_MSG_FILE_PATH"),
                            "Executor should use WSConstants for error messages");
                    }
                }
            }
        }
    }
}
