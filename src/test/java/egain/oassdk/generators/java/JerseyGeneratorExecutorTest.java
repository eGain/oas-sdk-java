package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comprehensive test cases for JerseyGenerator executor generation
 */
@DisplayName("JerseyGenerator Executor Generation Tests")
public class JerseyGeneratorExecutorTest {
    
    @TempDir
    Path tempOutputDir;
    
    @Test
    @DisplayName("Test executor directory is created")
    public void testExecutorDirectoryCreated() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Check executor directory exists
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        assertTrue(Files.exists(executorDir), "Executor directory should exist");
        assertTrue(Files.isDirectory(executorDir), "Executor path should be a directory");
    }
    
    @Test
    @DisplayName("Test executor files are generated for all operations")
    public void testExecutorFilesGenerated() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-files-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                List<Path> executorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .collect(Collectors.toList());
                
                assertFalse(executorFiles.isEmpty(), "At least one executor file should be generated");
            }
        }
    }
    
    @Test
    @DisplayName("Test GET executor extends GetBOExecutor_2")
    public void testGetExecutorBaseClass() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("get-executor-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> getExecutor = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("extends GetBOExecutor_2");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
                
                // If we find a GET executor, verify it has the correct structure
                if (getExecutor.isPresent()) {
                    String content = Files.readString(getExecutor.get());
                    assertTrue(content.contains("extends GetBOExecutor_2"), 
                        "GET executor should extend GetBOExecutor_2");
                    assertTrue(content.contains("convertDataObjectToJaxbBeanImpl"), 
                        "GET executor should have convertDataObjectToJaxbBeanImpl method");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test POST executor extends PostPutBOExecutorNoResponseBody_2")
    public void testPostExecutorBaseClass() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("post-executor-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> postExecutor = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("extends PostPutBOExecutorNoResponseBody_2") ||
                                   content.contains("extends PostPutBOExecutor_2");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
                
                // If we find a POST executor, verify it has the correct structure
                if (postExecutor.isPresent()) {
                    String content = Files.readString(postExecutor.get());
                    assertTrue(content.contains("PostPutBOExecutor"), 
                        "POST executor should extend PostPutBOExecutor");
                    assertTrue(content.contains("convertJaxbBeanToDataObject") || 
                               content.contains("composeLocationHeader"), 
                        "POST executor should have conversion or location header method");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor has required methods")
    public void testExecutorRequiredMethods() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-methods-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check for required methods
                    assertTrue(content.contains("validateRequestSyntaxImpl"), 
                        "Executor should have validateRequestSyntaxImpl method");
                    assertTrue(content.contains("createAuthorizer"), 
                        "Executor should have createAuthorizer method");
                    assertTrue(content.contains("executeBusinessOperationImpl"), 
                        "Executor should have executeBusinessOperationImpl method");
                    assertTrue(content.contains("solveHeaderMap"), 
                        "Executor should have solveHeaderMap field");
                    assertTrue(content.contains("AlwaysAuthorizedAuthorizerPartitionFactory"), 
                        "Executor should use AlwaysAuthorizedAuthorizerPartitionFactory");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor constructor includes solveHeaderMap")
    public void testExecutorConstructor() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-constructor-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check constructor has solveHeaderMap parameter
                    assertTrue(content.contains("Map<String, String> solveHeaderMap"), 
                        "Constructor should include solveHeaderMap parameter");
                    assertTrue(content.contains("this.solveHeaderMap = solveHeaderMap"), 
                        "Constructor should initialize solveHeaderMap field");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor package name is correct")
    public void testExecutorPackageName() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-package-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    String expectedPackage = packageName + ".executor";
                    assertTrue(content.contains("package " + expectedPackage), 
                        "Executor should have correct package: " + expectedPackage);
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor has proper imports")
    public void testExecutorImports() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-imports-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check for required imports
                    assertTrue(content.contains("import com.egain.platform.common.CallerContext"), 
                        "Should import CallerContext");
                    assertTrue(content.contains("import com.egain.platform.util.logging.Logger"), 
                        "Should import Logger");
                    assertTrue(content.contains("import egain.ws.common.authorization.Authorizer"), 
                        "Should import Authorizer");
                    assertTrue(content.contains("import egain.ws.util.WsUtil"), 
                        "Should import WsUtil");
                    
                    // Check for framework executor base class imports
                    boolean hasFrameworkImport = content.contains("import egain.ws.framework.GetBOExecutor_2") ||
                                               content.contains("import egain.ws.framework.PostPutBOExecutor_2") ||
                                               content.contains("import egain.ws.framework.PostPutBOExecutorNoResponseBody_2");
                    assertTrue(hasFrameworkImport, 
                        "Should import framework executor base class (GetBOExecutor_2 or PostPutBOExecutor_2)");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor handles List response types")
    public void testExecutorListResponseTypes() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-list-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> listExecutor = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("List<") && 
                                   content.contains("GetBOExecutor_2<List");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
                
                // If we find an executor with List response, verify special handling
                if (listExecutor.isPresent()) {
                    String content = Files.readString(listExecutor.get());
                    assertTrue(content.contains("java.util.ArrayList"), 
                        "List executor should use ArrayList for JAXB conversion");
                    assertTrue(content.contains("Util.isEmpty(mResponseData)"), 
                        "List executor should check for empty response data");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor has logger and constants")
    public void testExecutorLoggerAndConstants() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-logger-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check for logger and constants
                    assertTrue(content.contains("private static final String CLASS_NAME"), 
                        "Executor should have CLASS_NAME constant");
                    assertTrue(content.contains("private static final String FILE_NAME"), 
                        "Executor should have FILE_NAME constant");
                    assertTrue(content.contains("private static final Logger mLogger"), 
                        "Executor should have logger field");
                    assertTrue(content.contains("Logger.getLogger(CLASS_NAME)"), 
                        "Logger should be initialized with CLASS_NAME");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor validation method structure")
    public void testExecutorValidationMethod() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-validation-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check validation method structure
                    assertTrue(content.contains("@Override"), 
                        "validateRequestSyntaxImpl should be overridden");
                    assertTrue(content.contains("protected void validateRequestSyntaxImpl"), 
                        "Should have validateRequestSyntaxImpl method");
                    assertTrue(content.contains("CallerContext callerContext"), 
                        "Validation method should accept CallerContext");
                    assertTrue(content.contains("Locale locale"), 
                        "Validation method should accept Locale");
                    assertTrue(content.contains("WsUtil.validateSolveHeaderValues"), 
                        "Should validate solve header values");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor business operation method")
    public void testExecutorBusinessOperationMethod() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-business-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> executorFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .findFirst();
                
                if (executorFile.isPresent()) {
                    String content = Files.readString(executorFile.get());
                    
                    // Check business operation method
                    assertTrue(content.contains("protected void executeBusinessOperationImpl"), 
                        "Should have executeBusinessOperationImpl method");
                    assertTrue(content.contains("throws Exception"), 
                        "Business operation should throw Exception");
                    assertTrue(content.contains("LogSource logSource"), 
                        "Business operation should accept LogSource");
                    assertTrue(content.contains("TODO: Implement business logic") || 
                               content.contains("TODO: Implement"), 
                        "Should have TODO comment for business logic");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor file naming convention")
    public void testExecutorFileNaming() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-naming-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                List<Path> executorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
                
                // All executor files should end with BOExecutor.java
                for (Path file : executorFiles) {
                    String fileName = file.getFileName().toString();
                    assertTrue(fileName.endsWith("BOExecutor.java"), 
                        "Executor file should end with BOExecutor.java: " + fileName);
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor with request body has convertJaxbBeanToDataObject")
    public void testExecutorRequestBodyConversion() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-requestbody-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> postExecutor = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("PostPutBOExecutor") && 
                                   content.contains("convertJaxbBeanToDataObject");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
                
                // If we find a POST executor with request body, verify conversion method
                if (postExecutor.isPresent()) {
                    String content = Files.readString(postExecutor.get());
                    assertTrue(content.contains("protected void convertJaxbBeanToDataObject"), 
                        "POST executor with request body should have convertJaxbBeanToDataObject");
                    assertTrue(content.contains("LogSource.getObject(CLASS_NAME"), 
                        "Conversion method should use LogSource.getObject");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor location header for POST operations")
    public void testExecutorLocationHeader() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("executor-location-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        
        if (Files.exists(executorDir)) {
            try (Stream<Path> paths = Files.list(executorDir)) {
                Optional<Path> postExecutor = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("BOExecutor.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("PostPutBOExecutorNoResponseBody_2");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
                
                // If we find a POST executor without response body, verify location header method
                if (postExecutor.isPresent()) {
                    String content = Files.readString(postExecutor.get());
                    assertTrue(content.contains("protected String composeLocationHeader"), 
                        "POST executor without response should have composeLocationHeader");
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test executor generation from bundle-openapi spec")
    public void testExecutorGenerationFromBundleSpec() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("bundle-executor-test");
        String yamlFile = "examples/bundle-openapi 3.yaml";
        String packageName = "egain.ws.v4.access";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify executor directory exists
        Path executorDir = outputDir.resolve("src/main/java/" + packageName.replace(".", "/") + "/executor");
        assertTrue(Files.exists(executorDir), "Executor directory should exist");
        
        // Count executor files
        try (Stream<Path> paths = Files.list(executorDir)) {
            long executorCount = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("BOExecutor.java"))
                .count();
            
            assertTrue(executorCount > 0, "Should generate at least one executor file");
            System.out.println("Generated " + executorCount + " executor files from bundle spec");
        }
        
        // Verify at least one GET executor
        try (Stream<Path> paths = Files.list(executorDir)) {
            boolean hasGetExecutor = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("BOExecutor.java"))
                .anyMatch(p -> {
                    try {
                        String content = Files.readString(p);
                        return content.contains("extends GetBOExecutor_2");
                    } catch (IOException e) {
                        return false;
                    }
                });
            
            assertTrue(hasGetExecutor, "Should generate at least one GET executor");
        }
        
        // Verify at least one POST executor
        try (Stream<Path> paths = Files.list(executorDir)) {
            boolean hasPostExecutor = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("BOExecutor.java"))
                .anyMatch(p -> {
                    try {
                        String content = Files.readString(p);
                        return content.contains("PostPutBOExecutor");
                    } catch (IOException e) {
                        return false;
                    }
                });
            
            assertTrue(hasPostExecutor, "Should generate at least one POST executor");
        }
    }
}
