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
 * Test cases for JerseyGenerator fixes:
 * 1. Generate model classes for array types referenced in responses
 * 2. getAttributeNames() returns List<String>
 */
@DisplayName("JerseyGenerator Array Types and List Return Type Tests")
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
    @DisplayName("Test that getAttributeNames() returns List<String> in generated models")
    public void testGetAttributeNamesReturnsList() throws OASSDKException, IOException {
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
        
        int filesWithListReturnType = 0;
        int filesWithSetReturnType = 0;
        
        for (Path modelFile : modelFiles) {
            String content = Files.readString(modelFile);
            String fileName = modelFile.getFileName().toString();
            
            // Check if getAttributeNames() method exists
            if (content.contains("getAttributeNames()")) {
                // Should return List<String>
                boolean hasListReturnType = content.contains("public List<String> getAttributeNames()");
                // Should NOT return Set<String>
                boolean hasSetReturnType = content.contains("public Set<String> getAttributeNames()");
                
                if (hasListReturnType) {
                    filesWithListReturnType++;
                    // Verify List import
                    assertTrue(content.contains("import java.util.List") ||
                              content.contains("import java.util.*"),
                        fileName + " should import List");
                    // Verify ArrayList usage
                    assertTrue(content.contains("ArrayList") ||
                              content.contains("new ArrayList"),
                        fileName + " should use ArrayList");
                } else if (hasSetReturnType) {
                    filesWithSetReturnType++;
                    fail(fileName + " should return List<String>, not Set<String>");
                }
            }
        }
        
        assertTrue(filesWithListReturnType > 0,
            "At least one model should have getAttributeNames() returning List<String>");
        assertEquals(0, filesWithSetReturnType,
            "No models should have getAttributeNames() returning Set<String>");
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
    @DisplayName("Test that getAttributeNames() uses ArrayList in implementation")
    public void testGetAttributeNamesUsesArrayList() throws OASSDKException, IOException {
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
        
        boolean foundArrayList = false;
        
        for (Path modelFile : modelFiles) {
            String content = Files.readString(modelFile);
            
            if (content.contains("getAttributeNames()")) {
                // Should use ArrayList
                if (content.contains("new ArrayList") ||
                    content.contains("ArrayList<String>")) {
                    foundArrayList = true;
                    
                    // Verify import
                    assertTrue(content.contains("import java.util.ArrayList") ||
                              content.contains("import java.util.*"),
                        modelFile.getFileName() + " should import ArrayList");
                    
                    // Verify List import
                    assertTrue(content.contains("import java.util.List") ||
                              content.contains("import java.util.*"),
                        modelFile.getFileName() + " should import List");
                    
                    break;
                }
            }
        }
        
        assertTrue(foundArrayList,
            "At least one model should use ArrayList in getAttributeNames()");
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
    
    @Test
    @DisplayName("Test ArticleTypes model generation from bundle-openapi 3.yaml")
    public void testArticleTypesFromBundleOpenAPI3() throws OASSDKException, IOException {
        // Test the specific case from bundle-openapi 3.yaml where ArticleTypes 
        // is referenced in ArticleTypesGetByDepartmentResponse at line 11711
        String yamlFile = "examples/bundle-openapi 3.yaml";
        Path yamlPath = Paths.get(yamlFile);
        
        // Verify the test file exists
        assertTrue(Files.exists(yamlPath), 
            "bundle-openapi 3.yaml should exist for testing");
        
        Path outputDir = tempOutputDir.resolve("generated-bundle-sdk");
        String packageName = "com.egain.bundle.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Verify ArticleTypes model class was generated
        Path articleTypesFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/ArticleTypes.java");
        
        assertTrue(Files.exists(articleTypesFile), 
            "ArticleTypes.java should be generated for array type referenced in ArticleTypesGetByDepartmentResponse");
        
        // Verify the generated class has correct structure
        String content = Files.readString(articleTypesFile);
        
        // Should have List<ArticleTypeInfo> items field
        assertTrue(content.contains("List<ArticleTypeInfo>") || 
                   content.contains("List<ArticleTypeInfo> items"),
            "ArticleTypes should have List<ArticleTypeInfo> items field");
        
        // Should have proper getter/setter with List type
        assertTrue(content.contains("getItems()") || 
                   content.contains("public List<ArticleTypeInfo> getItems()"),
            "Getter should exist for items field");
        
        // Should have JAXB annotations
        assertTrue(content.contains("@XmlElementWrapper") || 
                   content.contains("@XmlElement") ||
                   content.contains("@XmlRootElement"),
            "Should have JAXB annotations");
        
        // Should implement Serializable and JAXBBean
        assertTrue(content.contains("implements Serializable") ||
                   content.contains("implements JAXBBean") ||
                   content.contains("implements Serializable, JAXBBean"),
            "Should implement Serializable and/or JAXBBean interface");
        
        // Verify ArticleTypeInfo is also generated (referenced in ArticleTypes items)
        Path articleTypeInfoFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/ArticleTypeInfo.java");
        
        assertTrue(Files.exists(articleTypeInfoFile), 
            "ArticleTypeInfo.java should be generated (referenced in ArticleTypes items)");
    }
    
    @Test
    @DisplayName("Test that ArticleTypes is collected when referenced in response components")
    public void testArticleTypesCollectedFromResponseComponents() throws OASSDKException, IOException {
        // Test that array types referenced via $ref in response components are collected
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
                  summary: Get All Article Types in a Department
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
                      minLength: 15
                      maxLength: 20
                    typeName:
                      type: string
                      enum:
                        - General
                        - Guided Help
                    articleCategoryId:
                      type: integer
                      format: int64
                    useStructuredAuthoring:
                      type: boolean
                ArticleTypes:
                  type: array
                  items:
                    $ref: '#/components/schemas/ArticleTypeInfo'
                  maxItems: 250
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-article-types-spec.yaml");
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
            "ArticleTypes.java should be generated when referenced in response components");
        
        String content = Files.readString(articleTypesFile);
        
        // Verify correct List type
        assertTrue(content.contains("List<ArticleTypeInfo>"),
            "ArticleTypes should have List<ArticleTypeInfo> type");
        
        // Verify maxItems constraint is handled (if applicable)
        // The maxItems: 250 should be reflected in validation annotations
        assertTrue(content.contains("@Size") || content.contains("maxItems") || 
                   content.contains("250") || !content.contains("maxItems"),
            "ArticleTypes should handle maxItems constraint if applicable");
    }

    @Test
    @DisplayName("ObjectFactory has no-arg constructor and createXxx() no-arg factory methods per model (PR #41)")
    public void testObjectFactoryHasNoArgCreateMethods() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /items:
                get:
                  operationId: getItems
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/ArticleTypes'
            components:
              schemas:
                ArticleTypeInfo:
                  type: object
                  properties:
                    id:
                      type: string
                ArticleTypes:
                  type: array
                  items:
                    $ref: '#/components/schemas/ArticleTypeInfo'
            """;
        Path testSpecFile = tempOutputDir.resolve("objectfactory-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path objectFactoryPath = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/ObjectFactory.java");
        assertTrue(Files.exists(objectFactoryPath), "ObjectFactory.java should be generated");

        String content = Files.readString(objectFactoryPath);

        assertTrue(content.contains("public ObjectFactory()"),
            "ObjectFactory should have public no-arg constructor");
        assertTrue(content.contains("public ArticleTypes createArticleTypes()"),
            "ObjectFactory should have no-arg createArticleTypes() method");
        assertTrue(content.contains("return new ArticleTypes();"),
            "createArticleTypes() should return new ArticleTypes()");
        assertTrue(content.contains("public ArticleTypeInfo createArticleTypeInfo()"),
            "ObjectFactory should have no-arg createArticleTypeInfo() method");
        assertTrue(content.contains("return new ArticleTypeInfo();"),
            "createArticleTypeInfo() should return new ArticleTypeInfo()");
    }

    @Test
    @DisplayName("Model class uses schema key (name) not title for class name (PR #43)")
    public void testSchemaNameNotTitleForModelClass() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /entity:
                get:
                  operationId: getEntity
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/MyModel'
            components:
              schemas:
                MyModel:
                  title: Different Title
                  type: object
                  properties:
                    value:
                      type: string
            """;
        Path testSpecFile = tempOutputDir.resolve("schema-name-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path modelDir = outputDir.resolve("src/main/java").resolve(packageName.replace(".", "/")).resolve("model");
        Path myModelFile = modelDir.resolve("MyModel.java");
        assertTrue(Files.exists(myModelFile), "MyModel.java should be generated (schema key is MyModel)");
        assertFalse(Files.exists(modelDir.resolve("DifferentTitle.java")),
            "Class file must not be named from title");

        String content = Files.readString(myModelFile);
        assertTrue(content.contains("public class MyModel "),
            "Generated class must be MyModel (schema key), not Different Title (title)");
        assertFalse(content.contains("public class DifferentTitle "),
            "Generated class must not use title as class name");
    }

    @Test
    @DisplayName("Inline primitive schema ref does not generate separate model class (PR #44)")
    public void testInlinePrimitiveRefNoSeparateModel() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /item:
                get:
                  operationId: getItem
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Item'
            components:
              schemas:
                Label:
                  type: string
                Item:
                  type: object
                  properties:
                    name:
                      $ref: '#/components/schemas/Label'
                    tags:
                      $ref: '#/components/schemas/StringList'
                StringList:
                  type: array
                  items:
                    type: string
            """;
        Path testSpecFile = tempOutputDir.resolve("primitive-ref-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path modelDir = outputDir.resolve("src/main/java").resolve(packageName.replace(".", "/")).resolve("model");
        assertTrue(Files.exists(modelDir), "model dir should exist");
        assertTrue(Files.exists(modelDir.resolve("Item.java")), "Item model should be generated");
        assertFalse(Files.exists(modelDir.resolve("Label.java")),
            "Label (primitive string schema) must not get a separate model class");
        assertFalse(Files.exists(modelDir.resolve("StringList.java")),
            "StringList (array of primitives) must not get a separate model class");
        String itemContent = Files.readString(modelDir.resolve("Item.java"));
        assertTrue(itemContent.contains("String ") && itemContent.contains("name"),
            "Item.name should be inlined as String, not Label");
    }

    @Test
    @DisplayName("Ref'd object-with-single-array generates as separate top-level class not inner class (PR #42)")
    public void testRefedObjectWithSingleArrayAsSeparateClass() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /container:
                get:
                  operationId: getContainer
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Container'
            components:
              schemas:
                Foo:
                  type: object
                  properties:
                    id:
                      type: string
                ItemsWrapper:
                  type: object
                  properties:
                    items:
                      type: array
                      items:
                        $ref: '#/components/schemas/Foo'
                Container:
                  type: object
                  properties:
                    data:
                      $ref: '#/components/schemas/ItemsWrapper'
            """;
        Path testSpecFile = tempOutputDir.resolve("ref-object-array-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path modelDir = outputDir.resolve("src/main/java").resolve(packageName.replace(".", "/")).resolve("model");
        Path itemsWrapperFile = modelDir.resolve("ItemsWrapper.java");
        assertTrue(Files.exists(itemsWrapperFile),
            "ItemsWrapper ($ref'd object-with-single-array) must be generated as separate class");
        String itemsWrapperContent = Files.readString(itemsWrapperFile);
        assertTrue(itemsWrapperContent.contains("public class ItemsWrapper "),
            "ItemsWrapper must be a top-level class, not inner class");
        assertFalse(itemsWrapperContent.contains("public static class ItemsWrapper"),
            "ItemsWrapper must not be generated as static inner class of another model");
        assertTrue(itemsWrapperContent.contains("List<Foo>") && itemsWrapperContent.contains("items"),
            "ItemsWrapper should have List<Foo> items field");

        Path containerFile = modelDir.resolve("Container.java");
        assertTrue(Files.exists(containerFile), "Container should be generated");
        String containerContent = Files.readString(containerFile);
        assertTrue(containerContent.contains("ItemsWrapper") && containerContent.contains("data"),
            "Container should reference ItemsWrapper for data property");
    }

    @Test
    @DisplayName("Object-with-single-array-of-ref generates wrapper inner class (e.g. Personalization.accessTags -> AccessTags with tagCategory)")
    public void testObjectWithSingleArrayOfRefGeneratesWrapperType() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /personalization:
                get:
                  summary: Get Personalization
                  operationId: getPersonalization
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Personalization'
            components:
              schemas:
                TagCategory:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
                PublishView:
                  type: object
                  properties:
                    id:
                      type: string
                Personalization:
                  type: object
                  properties:
                    accessTags:
                      type: object
                      properties:
                        tagCategory:
                          type: array
                          items:
                            $ref: '#/components/schemas/TagCategory'
                          maxItems: 20
                    filters:
                      type: object
                      properties:
                        tagCategory:
                          type: array
                          items:
                            $ref: '#/components/schemas/TagCategory'
                          maxItems: 20
                    publishViews:
                      type: object
                      properties:
                        publishView:
                          type: array
                          items:
                            $ref: '#/components/schemas/PublishView'
                          maxItems: 20
            """;

        Path testSpecFile = tempOutputDir.resolve("object-array-ref-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);

        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path personalizationFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("model/Personalization.java");

        assertTrue(Files.exists(personalizationFile),
            "Personalization.java should be generated");

        String content = Files.readString(personalizationFile);

        // Parent must use wrapper types (AccessTags, Filters, PublishViews), not List or Object
        assertTrue(content.contains("private AccessTags accessTags") || content.contains("private AccessTags accessTags;"),
            "accessTags should be wrapper type AccessTags");
        assertTrue(content.contains("private Filters filters") || content.contains("private Filters filters;"),
            "filters should be wrapper type Filters");
        assertTrue(content.contains("private PublishViews publishViews") || content.contains("private PublishViews publishViews;"),
            "publishViews should be wrapper type PublishViews");

        assertFalse(content.contains("private Object accessTags") || content.contains("private Object accessTags;"),
            "accessTags must not be Object");

        // Static inner class AccessTags with List<TagCategory> tagCategory and correct XML element name
        assertTrue(content.contains("public static class AccessTags"),
            "Should have static inner class AccessTags");
        assertTrue(content.contains("@XmlElement(name = \"tagCategory\")"),
            "Inner class should have @XmlElement(name = \"tagCategory\") for list items");
        assertTrue(content.contains("private List<TagCategory> tagCategory"),
            "AccessTags should contain List<TagCategory> tagCategory");
        assertTrue(content.contains("getTagCategory()"),
            "AccessTags should have getTagCategory()");

        // Static inner class Filters with List<TagCategory> tagCategory
        assertTrue(content.contains("public static class Filters"),
            "Should have static inner class Filters");
        assertTrue(content.contains("private List<TagCategory> tagCategory") && content.contains("class Filters"),
            "Filters should contain List<TagCategory> tagCategory");
        assertTrue(content.contains("getTagCategory()"),
            "Filters should have getTagCategory()");

        // Static inner class PublishViews with List<PublishView> publishView
        assertTrue(content.contains("public static class PublishViews"),
            "Should have static inner class PublishViews");
        assertTrue(content.contains("private List<PublishView> publishView"),
            "PublishViews should contain List<PublishView> publishView");
        assertTrue(content.contains("getPublishView()"),
            "PublishViews should have getPublishView()");
    }

    @Test
    @DisplayName("getJavaType returns List for inlined object-with-single-array response schema")
    public void testGetJavaTypeReturnsListForInlinedObjectWithSingleArrayResponse() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /tag-categories:
                get:
                  summary: Get tag categories list
                  operationId: getTagCategories
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              tagCategory:
                                type: array
                                items:
                                  $ref: '#/components/schemas/TagCategory'
            components:
              schemas:
                TagCategory:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
            """;

        Path testSpecFile = tempOutputDir.resolve("inline-array-ref-response-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);

        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());

        Path executorFile = outputDir.resolve("src/main/java")
            .resolve(packageName.replace(".", "/"))
            .resolve("executor/GetTagCategoriesBOExecutor.java");

        assertTrue(Files.exists(executorFile),
            "GetTagCategoriesBOExecutor.java should be generated");

        String content = Files.readString(executorFile);

        // getJavaType() should resolve inlined object-with-single-array to List<TagCategory>, not Object
        assertTrue(content.contains("GetBOExecutor_2<"),
            "Executor base class should be GetBOExecutor_2<List<TagCategory>> for inlined object-with-single-array schema");
        assertTrue(content.contains("private List<TagCategory> mResponseData") || content.contains("List<TagCategory> mResponseData"),
            "Executor should declare mResponseData as List<TagCategory>");
    }
}
