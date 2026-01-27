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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test cases for JerseyGenerator fixes:
 * 1. Generate model classes for array types referenced in responses
 * 2. getAttributeNames() returns Set<String> instead of List<String>
 */
@DisplayName("JerseyGenerator Array Types and Set Return Type Tests")
public class JerseyGeneratorArrayTypesAndSetTest {
    
    @TempDir
    Path tempOutputDir;
    
    @Test
    @DisplayName("Test that array types referenced in responses generate model classes")
    public void testArrayTypesReferencedInResponsesGenerateModel() throws OASSDKException, IOException {
        // Create a test OpenAPI spec with array type referenced in response
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /articleTypes:
                get:
                  summary: Get Article Types
                  operationId: getAllArticleTypes
                  responses:
                    '200':
                      $ref: '#/components/responses/ArticleTypesGetByDepartmentResponse'
            components:
              responses:
                ArticleTypesGetByDepartmentResponse:
                  description: Success
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/ArticleTypes'
              schemas:
                ArticleTypeInfo:
                  type: object
                  properties:
                    articleTypeId:
                      type: string
                    typeName:
                      type: string
                ArticleTypes:
                  type: array
                  items:
                    $ref: '#/components/schemas/ArticleTypeInfo'
                  maxItems: 250
            """;
        
        // Write test spec to temp file
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify ArticleTypes model class was generated
        Path articleTypesFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/ArticleTypes.java");
        
        assertTrue(Files.exists(articleTypesFile), 
            "ArticleTypes.java should be generated for array type referenced in response");
        
        // Verify the generated class has correct structure
        String content = Files.readString(articleTypesFile);
        
        // Should have List<ArticleTypeInfo> items field
        assertTrue(content.contains("private List<ArticleTypeInfo> items") ||
                   content.contains("private List<ArticleTypeInfo> items;"),
            "ArticleTypes should have List<ArticleTypeInfo> items field");
        
        // Should have proper getter/setter with List type
        assertTrue(content.contains("public List<ArticleTypeInfo> getItems()"),
            "Getter should return List<ArticleTypeInfo>");
        assertTrue(content.contains("public void setItems(List<ArticleTypeInfo> items)"),
            "Setter should accept List<ArticleTypeInfo>");
        
        // Should have JAXB annotations for List
        assertTrue(content.contains("@XmlElementWrapper") || content.contains("@XmlElement"),
            "Should have JAXB annotations for List field");
        
        // Should implement JAXBBean interface
        assertTrue(content.contains("implements Serializable, JAXBBean"),
            "Should implement JAXBBean interface");
    }
    
    @Test
    @DisplayName("Test that getAttributeNames() returns Set<String> in generated models")
    public void testGetAttributeNamesReturnsSet() throws OASSDKException, IOException {
        String yamlFile = "src/test/resources/openapi3.yaml";
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Find all generated model classes
        Path modelDir = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model");
        
        if (!Files.exists(modelDir)) {
            fail("Model directory should exist");
        }
        
        // Check all model classes for getAttributeNames() return type
        List<Path> modelFiles = Files.walk(modelDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.getFileName().toString().equals("ObjectFactory.java"))
            .filter(path -> !path.getFileName().toString().equals("JAXBBean.java"))
            .collect(Collectors.toList());
        
        assertFalse(modelFiles.isEmpty(), "At least one model file should be generated");
        
        int filesWithSetReturnType = 0;
        int filesWithListReturnType = 0;
        
        for (Path modelFile : modelFiles) {
            String content = Files.readString(modelFile);
            String fileName = modelFile.getFileName().toString();
            
            // Check if getAttributeNames() method exists
            if (content.contains("getAttributeNames()")) {
                // Should return Set<String>
                boolean hasSetReturnType = content.contains("public Set<String> getAttributeNames()");
                // Should NOT return List<String>
                boolean hasListReturnType = content.contains("public List<String> getAttributeNames()");
                
                if (hasSetReturnType) {
                    filesWithSetReturnType++;
                    // Verify Set import
                    assertTrue(content.contains("import java.util.Set") || 
                              content.contains("import java.util.*"),
                        fileName + " should import Set");
                    // Verify LinkedHashSet usage
                    assertTrue(content.contains("LinkedHashSet") || 
                              content.contains("new LinkedHashSet"),
                        fileName + " should use LinkedHashSet");
                } else if (hasListReturnType) {
                    filesWithListReturnType++;
                    fail(fileName + " should return Set<String>, not List<String>");
                }
            }
        }
        
        assertTrue(filesWithSetReturnType > 0, 
            "At least one model should have getAttributeNames() returning Set<String>");
        assertEquals(0, filesWithListReturnType,
            "No models should have getAttributeNames() returning List<String>");
    }
    
    @Test
    @DisplayName("Test that array type model has correct JAXB annotations")
    public void testArrayTypeModelJAXBAnnotations() throws OASSDKException, IOException {
        // Create a test OpenAPI spec with array type
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /items:
                get:
                  summary: Get Items
                  operationId: getItems
                  responses:
                    '200':
                      $ref: '#/components/responses/ItemsResponse'
            components:
              responses:
                ItemsResponse:
                  description: Success
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/Items'
              schemas:
                Item:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
                Items:
                  type: array
                  items:
                    $ref: '#/components/schemas/Item'
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-array-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify Items model class
        Path itemsFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/Items.java");
        
        assertTrue(Files.exists(itemsFile), "Items.java should be generated");
        
        String content = Files.readString(itemsFile);
        
        // Should have @XmlElementWrapper for List
        assertTrue(content.contains("@XmlElementWrapper") || content.contains("@XmlElement"),
            "Should have JAXB annotations for List field");
        
        // Should have proper List type
        assertTrue(content.contains("List<Item>"),
            "Should have List<Item> type");
        
        // Should not have single Item type (wrong)
        assertFalse(content.contains("private Item items;") && 
                    !content.contains("List<Item>"),
            "Should not have single Item type, should be List<Item>");
    }
    
    @Test
    @DisplayName("Test that getAttributeNames() uses LinkedHashSet in implementation")
    public void testGetAttributeNamesUsesLinkedHashSet() throws OASSDKException, IOException {
        String yamlFile = "src/test/resources/openapi3.yaml";
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path modelDir = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model");
        
        if (!Files.exists(modelDir)) {
            fail("Model directory should exist");
        }
        
        // Find a model file with getAttributeNames()
        List<Path> modelFiles = Files.walk(modelDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.getFileName().toString().equals("ObjectFactory.java"))
            .filter(path -> !path.getFileName().toString().equals("JAXBBean.java"))
            .collect(Collectors.toList());
        
        boolean foundLinkedHashSet = false;
        
        for (Path modelFile : modelFiles) {
            String content = Files.readString(modelFile);
            
            if (content.contains("getAttributeNames()")) {
                // Should use LinkedHashSet
                if (content.contains("new LinkedHashSet") || 
                    content.contains("LinkedHashSet<String>")) {
                    foundLinkedHashSet = true;
                    
                    // Verify import
                    assertTrue(content.contains("import java.util.LinkedHashSet") ||
                              content.contains("import java.util.*"),
                        modelFile.getFileName() + " should import LinkedHashSet");
                    
                    // Verify Set import
                    assertTrue(content.contains("import java.util.Set") ||
                              content.contains("import java.util.*"),
                        modelFile.getFileName() + " should import Set");
                    
                    break;
                }
            }
        }
        
        assertTrue(foundLinkedHashSet, 
            "At least one model should use LinkedHashSet in getAttributeNames()");
    }
    
    @Test
    @DisplayName("Test that array types not referenced in responses are still skipped")
    public void testUnreferencedArrayTypesAreSkipped() throws OASSDKException, IOException {
        // Create a test spec with array type NOT referenced in responses
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /test:
                get:
                  summary: Test endpoint
                  operationId: test
                  responses:
                    '200':
                      description: Success
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              data:
                                type: string
            components:
              schemas:
                UnreferencedArray:
                  type: array
                  items:
                    type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-unreferenced-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify UnreferencedArray model class is NOT generated
        Path unreferencedArrayFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/UnreferencedArray.java");
        
        assertFalse(Files.exists(unreferencedArrayFile), 
            "UnreferencedArray.java should NOT be generated (not referenced in responses)");
    }
    
    @Test
    @DisplayName("Test that collectSchemasReferencedInResponses works correctly")
    public void testCollectSchemasReferencedInResponses() throws OASSDKException, IOException {
        // Create a test spec with multiple array types, some referenced in responses
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /articles:
                get:
                  summary: Get Articles
                  operationId: getArticles
                  responses:
                    '200':
                      $ref: '#/components/responses/ArticlesResponse'
            components:
              responses:
                ArticlesResponse:
                  description: Success
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/Articles'
              schemas:
                Article:
                  type: object
                  properties:
                    id:
                      type: string
                Articles:
                  type: array
                  items:
                    $ref: '#/components/schemas/Article'
                UnreferencedArray:
                  type: array
                  items:
                    type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-collect-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path modelDir = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model");
        
        // Articles should be generated (referenced in response)
        Path articlesFile = modelDir.resolve("Articles.java");
        assertTrue(Files.exists(articlesFile), 
            "Articles.java should be generated (referenced in response)");
        
        // UnreferencedArray should NOT be generated (not referenced)
        Path unreferencedArrayFile = modelDir.resolve("UnreferencedArray.java");
        assertFalse(Files.exists(unreferencedArrayFile), 
            "UnreferencedArray.java should NOT be generated (not referenced in response)");
    }
}
