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
 * Comprehensive test cases for JerseyGenerator XSD generation functionality
 */
@DisplayName("JerseyGenerator XSD Generation Tests")
public class JerseyGeneratorXSDTest {
    
    @TempDir
    Path tempOutputDir;
    
    /**
     * Helper method to generate XSD from YAML content
     */
    private Path generateXSDFromYaml(String yamlContent, String testName) throws OASSDKException, IOException {
        Path testSpecFile = tempOutputDir.resolve(testName + "-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        Path outputDir = tempOutputDir.resolve(testName + "-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        return outputDir;
    }
    
    /**
     * Helper method to get XSD file path
     */
    private Path getXSDFile(Path outputDir, String schemaName) {
        return outputDir.resolve("src/main/resources/xsd/" + schemaName + ".xsd");
    }
    
    /**
     * Helper method to assert XSD file exists and contains content
     */
    private void assertXSDContains(Path xsdFile, String expectedContent, String message) throws IOException {
        assertTrue(Files.exists(xsdFile), "XSD file should exist: " + xsdFile.getFileName());
        String content = Files.readString(xsdFile);
        assertTrue(content.contains(expectedContent), message);
    }
    
    /**
     * Helper method to assert XSD file exists and matches pattern
     */
    private void assertXSDMatches(Path xsdFile, String pattern, String message) throws IOException {
        assertTrue(Files.exists(xsdFile), "XSD file should exist: " + xsdFile.getFileName());
        String content = Files.readString(xsdFile);
        assertTrue(content.matches("(?s).*" + pattern + ".*"), message);
    }
    
    @Test
    @DisplayName("Test XSD directory is created")
    public void testXSDDirectoryCreated() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("xsd-dir-test");
        String yamlFile = "src/test/resources/openapi3.yaml";
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(yamlFile);
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Check XSD directory exists
        Path xsdDir = outputDir.resolve("src/main/resources/xsd");
        assertTrue(Files.exists(xsdDir), "XSD directory should exist");
        assertTrue(Files.isDirectory(xsdDir), "XSD path should be a directory");
    }
    
    @Test
    @DisplayName("Test XSD files are generated for schemas")
    public void testXSDFilesGenerated() throws OASSDKException, IOException {
        // Create a test OpenAPI spec with multiple schemas
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
                  responses:
                    '200':
                      description: Success
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/User'
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
                Link:
                  type: object
                  properties:
                    href:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        // Generate SDK
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        // Check XSD files are generated
        Path xsdDir = outputDir.resolve("src/main/resources/xsd");
        assertTrue(Files.exists(xsdDir), "XSD directory should exist");
        
        List<Path> xsdFiles = Files.list(xsdDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".xsd"))
            .collect(Collectors.toList());
        
        assertFalse(xsdFiles.isEmpty(), "At least one XSD file should be generated");
        
        // Check User.xsd exists
        Path userXsd = xsdDir.resolve("User.xsd");
        assertTrue(Files.exists(userXsd), "User.xsd should be generated");
        
        // Check Link.xsd exists
        Path linkXsd = xsdDir.resolve("Link.xsd");
        assertTrue(Files.exists(linkXsd), "Link.xsd should be generated");
    }
    
    @Test
    @DisplayName("Test XSD file has correct XML structure")
    public void testXSDXMLStructure() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                SimpleUser:
                  type: object
                  properties:
                    id:
                      type: string
            """;
        
        Path outputDir = generateXSDFromYaml(yamlContent, "xml-structure");
        Path xsdFile = getXSDFile(outputDir, "SimpleUser");
        assertTrue(Files.exists(xsdFile), "SimpleUser.xsd should exist");
        
        String content = Files.readString(xsdFile);
        
        // Check XML declaration
        assertTrue(content.startsWith("<?xml"), "Should start with XML declaration");
        assertTrue(content.contains("encoding=\"UTF-8\""), "Should have UTF-8 encoding");
        
        // Check schema element
        assertTrue(content.contains("<xs:schema"), "Should have xs:schema element");
        assertTrue(content.contains("xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""), 
            "Should have XMLSchema namespace");
        assertTrue(content.contains("targetNamespace"), "Should have targetNamespace");
        assertTrue(content.contains("xmlns:jaxb"), "Should have JAXB namespace");
        assertTrue(content.contains("jaxb:version=\"2.0\""), "Should have JAXB version");
        
        // Check closing schema tag
        assertTrue(content.contains("</xs:schema>"), "Should close schema element");
    }
    
    @Test
    @DisplayName("Test XSD namespace generation")
    public void testXSDNamespaceGeneration() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                TestSchema:
                  type: object
                  properties:
                    value:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/TestSchema.xsd");
        String content = Files.readString(xsdFile);
        
        // Check namespace format
        assertTrue(content.contains("http://bindings.egain.com/ws/model/xsds/common/v4/TestSchema"), 
            "Should have correct targetNamespace format");
        assertTrue(content.contains("xmlns:TestSchema="), "Should have schema namespace prefix");
    }
    
    @Test
    @DisplayName("Test XSD with $ref schema (like EditNonIntegratedUser)")
    public void testXSDWithRefSchema() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: string
                EditNonIntegratedUser:
                  $ref: '#/components/schemas/User'
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path editUserXsd = outputDir.resolve("src/main/resources/xsd/EditNonIntegratedUser.xsd");
        assertTrue(Files.exists(editUserXsd), "EditNonIntegratedUser.xsd should exist");
        
        String content = Files.readString(editUserXsd);
        
        // Check for import
        assertTrue(content.contains("<xs:import"), "Should have xs:import for User schema");
        assertTrue(content.contains("schemaLocation=\"./User.xsd\""), 
            "Should import User.xsd");
        
        // Check for wrapper complex type
        assertTrue(content.contains("<xs:element name=\"EditNonIntegratedUser\""), 
            "Should have element with schema name");
        assertTrue(content.contains("<xs:complexType name=\"EditNonIntegratedUser\""), 
            "Should have complexType");
        assertTrue(content.contains("type=\"User:User\""), 
            "Should reference User:User type");
    }
    
    @Test
    @DisplayName("Test XSD with simple type restrictions (pattern, minLength, maxLength)")
    public void testXSDSimpleTypeRestrictions() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: string
                      pattern: ^[1-9]\\d*$
                      minLength: 1
                      maxLength: 9
                    email:
                      type: string
                      pattern: ^[a-zA-Z0-9-_]+@[a-zA-Z0-9-_]+\\.[a-zA-Z0-9]+$
                      maxLength: 255
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check for simple type restrictions
        assertTrue(content.contains("<xs:simpleType>"), "Should have simpleType");
        assertTrue(content.contains("<xs:restriction base=\"xs:string\">"), 
            "Should have restriction base xs:string");
        assertTrue(content.contains("<xs:pattern"), "Should have pattern restriction");
        assertTrue(content.contains("<xs:minLength"), "Should have minLength restriction");
        assertTrue(content.contains("<xs:maxLength"), "Should have maxLength restriction");
    }
    
    @Test
    @DisplayName("Test XSD with enum types")
    public void testXSDEnumTypes() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    status:
                      type: string
                      enum:
                        - enabled
                        - disabled
                    title:
                      type: string
                      enum:
                        - Mr.
                        - Mrs.
                        - Ms.
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = getXSDFile(outputDir, "User");
        
        // Check for enum restrictions
        assertXSDContains(xsdFile, "<xs:enumeration value=\"enabled\"", 
            "Should have enabled enumeration");
        assertXSDContains(xsdFile, "<xs:enumeration value=\"disabled\"", 
            "Should have disabled enumeration");
        assertXSDContains(xsdFile, "<xs:enumeration value=\"Mr.\"", 
            "Should have Mr. enumeration");
    }
    
    @Test
    @DisplayName("Test XSD with array types")
    public void testXSDArrayTypes() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    links:
                      type: array
                      items:
                        $ref: '#/components/schemas/Link'
                      minItems: 0
                    tags:
                      type: array
                      items:
                        type: string
                      maxItems: 10
                Link:
                  type: object
                  properties:
                    href:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check for array handling
        assertTrue(content.contains("maxOccurs"), "Should have maxOccurs for arrays");
        assertTrue(content.contains("type=\"Link:Link\""), 
            "Should reference Link type for array items");
        assertTrue(content.contains("maxOccurs=\"10\""), 
            "Should respect maxItems constraint");
        
        // Check minItems is handled (links has minItems: 0)
        String linksElementPattern = "name=\"links\"[^>]*minOccurs=\"0\"";
        assertTrue(content.matches("(?s).*" + linksElementPattern + ".*"), 
            "Array with minItems: 0 should have minOccurs=\"0\"");
    }
    
    @Test
    @DisplayName("Test XSD with nested object types")
    public void testXSDNestedObjects() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    departments:
                      type: object
                      properties:
                        department:
                          type: array
                          items:
                            $ref: '#/components/schemas/Department'
                    manager:
                      type: object
                      properties:
                        id:
                          type: string
                        name:
                          type: string
                Department:
                  type: object
                  properties:
                    id:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check for nested complex types
        assertTrue(content.contains("<xs:complexType>"), 
            "Should have nested complexType for object properties");
        assertTrue(content.contains("name=\"departments\""), 
            "Should have departments element");
        assertTrue(content.contains("name=\"manager\""), 
            "Should have manager element");
    }
    
    @Test
    @DisplayName("Test XSD with required fields")
    public void testXSDRequiredFields() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  required:
                    - id
                    - name
                  properties:
                    id:
                      type: string
                    name:
                      type: string
                    email:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = getXSDFile(outputDir, "User");
        
        // Check for minOccurs on required fields - use regex to find element with name="id" and minOccurs="1"
        assertXSDMatches(xsdFile, "name=\"id\"[^>]*minOccurs=\"1\"", 
            "Required field id should have minOccurs=\"1\"");
        
        // Check optional field has minOccurs="0"
        assertXSDMatches(xsdFile, "name=\"email\"[^>]*minOccurs=\"0\"", 
            "Optional field email should have minOccurs=\"0\"");
    }
    
    @Test
    @DisplayName("Test XSD with allOf composition")
    public void testXSDAllOfComposition() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                Base:
                  type: object
                  properties:
                    id:
                      type: string
                Extended:
                  allOf:
                    - $ref: '#/components/schemas/Base'
                    - type: object
                      properties:
                        name:
                          type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/Extended.xsd");
        assertTrue(Files.exists(xsdFile), "Extended.xsd should exist");
        
        String content = Files.readString(xsdFile);
        
        // Check that all properties from allOf are included
        assertTrue(content.contains("name=\"id\""), 
            "Should include id from Base schema");
        assertTrue(content.contains("name=\"name\""), 
            "Should include name from Extended schema");
    }
    
    @Test
    @DisplayName("Test XSD schema imports for referenced schemas")
    public void testXSDSchemaImports() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    manager:
                      $ref: '#/components/schemas/UserView'
                    link:
                      type: array
                      items:
                        $ref: '#/components/schemas/Link'
                UserView:
                  type: object
                  properties:
                    id:
                      type: string
                Link:
                  type: object
                  properties:
                    href:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path userXsd = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(userXsd);
        
        // Check for imports
        assertTrue(content.contains("<xs:import"), 
            "Should have xs:import statements");
        assertTrue(content.contains("UserView"), 
            "Should import UserView schema");
        assertTrue(content.contains("Link"), 
            "Should import Link schema");
        assertTrue(content.contains("schemaLocation=\"./UserView.xsd\""), 
            "Should have correct schemaLocation for UserView");
        assertTrue(content.contains("schemaLocation=\"./Link.xsd\""), 
            "Should have correct schemaLocation for Link");
    }
    
    @Test
    @DisplayName("Test XSD with date-time format")
    public void testXSDDateTimeFormat() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    createdDate:
                      type: string
                      format: date-time
                      pattern: ^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$
                      minLength: 20
                      maxLength: 25
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check that date-time with pattern generates simple type restriction
        assertTrue(content.contains("<xs:simpleType>"), 
            "Should have simpleType for date-time with pattern");
        assertTrue(content.contains("<xs:pattern"), 
            "Should have pattern restriction");
    }
    
    @Test
    @DisplayName("Test XSD files are valid XML")
    public void testXSDValidXML() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdDir = outputDir.resolve("src/main/resources/xsd");
        List<Path> xsdFiles = Files.list(xsdDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".xsd"))
            .collect(Collectors.toList());
        
        for (Path xsdFile : xsdFiles) {
            String content = Files.readString(xsdFile);
            
            // Basic XML structure checks
            assertTrue(content.contains("<?xml"), 
                xsdFile.getFileName() + " should have XML declaration");
            assertTrue(content.contains("<xs:schema"), 
                xsdFile.getFileName() + " should have schema element");
            assertTrue(content.contains("</xs:schema>"), 
                xsdFile.getFileName() + " should close schema element");
            
            // Check for balanced tags (basic check)
            long openTags = content.chars().filter(ch -> ch == '<').count();
            long closeTags = content.chars().filter(ch -> ch == '>').count();
            assertEquals(openTags, closeTags, 
                xsdFile.getFileName() + " should have balanced angle brackets");
        }
    }
    
    @Test
    @DisplayName("Test XSD with empty object properties")
    public void testXSDEmptyObjectProperties() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                EmptyObject:
                  type: object
                  properties: {}
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/EmptyObject.xsd");
        assertTrue(Files.exists(xsdFile), "EmptyObject.xsd should exist");
        
        String content = Files.readString(xsdFile);
        
        // Should still generate valid XSD with empty sequence
        assertTrue(content.contains("<xs:sequence>"), 
            "Should have sequence element");
        assertTrue(content.contains("</xs:sequence>"), 
            "Should close sequence element");
    }
    
    @Test
    @DisplayName("Test XSD with oneOf composition")
    public void testXSDOneOfComposition() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                Base1:
                  type: object
                  properties:
                    id:
                      type: string
                Base2:
                  type: object
                  properties:
                    name:
                      type: string
                Combined:
                  oneOf:
                    - $ref: '#/components/schemas/Base1'
                    - $ref: '#/components/schemas/Base2'
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/Combined.xsd");
        assertTrue(Files.exists(xsdFile), "Combined.xsd should exist");
        
        String content = Files.readString(xsdFile);
        
        // Check that properties from oneOf are included (merged)
        assertTrue(content.contains("name=\"id\""), 
            "Should include id from Base1 schema");
        assertTrue(content.contains("name=\"name\""), 
            "Should include name from Base2 schema");
    }
    
    @Test
    @DisplayName("Test XSD with nested array of objects")
    public void testXSDNestedArrayOfObjects() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    departments:
                      type: object
                      properties:
                        department:
                          type: array
                          items:
                            type: object
                            properties:
                              id:
                                type: string
                              name:
                                type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check for nested complex type with array
        assertTrue(content.contains("name=\"departments\""), 
            "Should have departments element");
        assertTrue(content.contains("<xs:complexType>"), 
            "Should have nested complexType");
        assertTrue(content.contains("name=\"department\""), 
            "Should have department array element");
        // Verify no duplicate closing tags (was a bug)
        long closingBrackets = content.chars().filter(ch -> ch == '>').count();
        long openingBrackets = content.chars().filter(ch -> ch == '<').count();
        assertEquals(openingBrackets, closingBrackets, 
            "Should have balanced XML tags (no duplicates)");
    }
    
    @Test
    @DisplayName("Test XSD with DateAndTime format")
    public void testXSDDateAndTimeFormat() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    createdDate:
                      type: string
                      format: DateAndTime
                      pattern: ^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$
                      minLength: 20
                      maxLength: 25
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdFile = outputDir.resolve("src/main/resources/xsd/User.xsd");
        String content = Files.readString(xsdFile);
        
        // Check that DateAndTime format with pattern generates simple type restriction
        assertTrue(content.contains("<xs:simpleType>"), 
            "Should have simpleType for DateAndTime with pattern");
        assertTrue(content.contains("<xs:pattern"), 
            "Should have pattern restriction");
    }
    
    @Test
    @DisplayName("Test XSD generation excludes error schemas")
    public void testXSDExcludesErrorSchemas() throws OASSDKException, IOException {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: string
                WSErrorCommon:
                  type: object
                  properties:
                    code:
                      type: string
            """;
        
        Path testSpecFile = tempOutputDir.resolve("test-spec.yaml");
        Files.writeString(testSpecFile, yamlContent);
        
        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";
        
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(testSpecFile.toString());
        sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        
        Path xsdDir = outputDir.resolve("src/main/resources/xsd");
        
        // WSErrorCommon should not be generated
        Path errorXsd = xsdDir.resolve("WSErrorCommon.xsd");
        assertFalse(Files.exists(errorXsd), 
            "WSErrorCommon.xsd should not be generated (error schema)");
        
        // User should be generated
        Path userXsd = xsdDir.resolve("User.xsd");
        assertTrue(Files.exists(userXsd), 
            "User.xsd should be generated");
    }
}
