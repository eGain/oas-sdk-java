package egain.oassdk.testgenerators.postman;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.parser.OASParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.Comparator;

/**
 * Test class for PostmanTestGenerator to verify fixes for:
 * 1. String escaping in description fields
 * 2. JSON request body escaping
 * 3. Variable naming conflicts
 */
@DisplayName("Postman Test Generator Tests")
public class PostmanTestGeneratorTest {
    
    private PostmanTestGenerator generator;
    private OASParser parser;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        generator = new PostmanTestGenerator();
        parser = new OASParser();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Test description fields with newlines are properly escaped")
    void testDescriptionEscaping() throws Exception {
        Map<String, Object> spec = createSpecWithMultilineDescription();
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Read generated collection
        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile, "Postman collection file should be generated");
        
        Map<String, Object> collection = Util.asStringObjectMap(
            objectMapper.readValue(collectionFile.toFile(), Map.class)
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) collection.get("item");
        assertNotNull(items, "Collection should have items");

        boolean foundEscapedDescription = false;
        for (Map<String, Object> tagFolder : items) {
            for (Map<String, Object> node : flattenPostmanItems(tagFolder)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> requestMap = (Map<String, Object>) node.get("request");
                if (requestMap != null && requestMap.containsKey("description")) {
                    String description = (String) requestMap.get("description");
                    if (description != null && description.contains("\\n")) {
                        foundEscapedDescription = true;
                        assertFalse(description.contains("\n"),
                                "Description should not contain unescaped newlines");
                    }
                }
            }
        }
        
        assertTrue(foundEscapedDescription, "Should find at least one escaped description");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    @Test
    @DisplayName("Test variable naming conflicts are resolved")
    void testVariableNamingConflicts() throws Exception {
        Map<String, Object> spec = createSpecWithServers();
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Read generated collection
        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile, "Postman collection file should be generated");
        
        Map<String, Object> collection = Util.asStringObjectMap(
            objectMapper.readValue(collectionFile.toFile(), Map.class)
        );
        
        // Check variables
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variables = (List<Map<String, Object>>) collection.get("variable");
        assertNotNull(variables, "Collection should have variables");
        
        // Count base_url variables
        long baseUrlCount = variables.stream()
            .filter(v -> "base_url".equals(v.get("key")))
            .count();
        
        assertEquals(1, baseUrlCount, "Should have exactly one base_url variable");
        
        // Verify it uses the server URL from spec
        Map<String, Object> baseUrlVar = variables.stream()
            .filter(v -> "base_url".equals(v.get("key")))
            .findFirst()
            .orElse(null);
        
        assertNotNull(baseUrlVar, "base_url variable should exist");
        String value = (String) baseUrlVar.get("value");
        assertTrue(value.contains("https://"), "Should use server URL from spec");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    @Test
    @DisplayName("Test examples are extracted from OpenAPI spec")
    void testExampleExtraction() throws Exception {
        Map<String, Object> spec = createSpecWithExamples();
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Read generated collection
        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile, "Postman collection file should be generated");
        
        String collectionJson = Files.readString(collectionFile);
        
        // Verify example values from the spec are present in the collection
        // The example should contain the actual values from the spec
        assertTrue(collectionJson.contains("AKIAIOSFODNN7EXAMPLE") || 
                   collectionJson.contains("mybucket"), 
            "Collection should contain example values from spec");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    @Test
    @DisplayName("Test generated Postman collection is valid JSON")
    void testValidJsonGeneration() throws Exception {
        Map<String, Object> spec = createBasicSpec();
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Read generated collection
        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile, "Postman collection file should be generated");
        
        // Verify it's valid JSON
        assertDoesNotThrow(() -> {
            objectMapper.readValue(collectionFile.toFile(), Map.class);
        }, "Generated collection should be valid JSON");
        
        // Verify file is not empty
        assertTrue(Files.size(collectionFile) > 0, "Collection file should not be empty");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    @Test
    @DisplayName("Test environment file generation")
    void testEnvironmentFileGeneration() throws Exception {
        Map<String, Object> spec = createSpecWithServers();
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Find environment file
        Path envFile = findPostmanEnvironmentFile(tempDir);
        assertNotNull(envFile, "Postman environment file should be generated");
        
        // Verify it's valid JSON
        assertDoesNotThrow(() -> {
            objectMapper.readValue(envFile.toFile(), Map.class);
        }, "Generated environment file should be valid JSON");
        
        // Verify it contains base_url
        @SuppressWarnings("unchecked")
        Map<String, Object> environment = objectMapper.readValue(envFile.toFile(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) environment.get("values");
        assertNotNull(values, "Environment should have values");
        
        boolean hasBaseUrl = values.stream()
            .anyMatch(v -> "base_url".equals(v.get("key")));
        
        assertTrue(hasBaseUrl, "Environment should contain base_url variable");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    @Test
    @DisplayName("openapi4: path uses Postman {{variables}} and Negative-TCs include error body checks")
    void testOpenApi4NegativeSuiteStructure() throws Exception {
        String yamlFile = "src/test/resources/openapi4.yaml";
        Path yamlPath = Paths.get(yamlFile);
        if (!Files.exists(yamlPath)) {
            return;
        }
        Map<String, Object> spec = parser.parse(yamlFile);
        Path tempDir = Files.createTempDirectory("postman-test");
        generator.generate(spec, tempDir.toString(), new TestConfig(), null);

        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile);
        String json = Files.readString(collectionFile);

        assertTrue(json.contains("{{contentId}}"), "Path should use Postman variable for contentId");
        assertTrue(json.contains("Negative-TCs"), "Collection should include Negative-TCs folder");
        assertTrue(json.contains("developerMessage"), "Negative tests should assert developerMessage");
        assertTrue(json.contains("Happy path"), "Operation folder should include Happy path request");

        assertTrue(findNegativeTcFolderCount(Util.asStringObjectMap(
                objectMapper.readValue(collectionFile.toFile(), Map.class))) >= 1);

        deleteDirectory(tempDir);
    }

    @Test
    @DisplayName("postmanNegativeTests false skips Negative-TCs")
    void testDisableNegativeTestsViaConfig() throws Exception {
        String yamlFile = "src/test/resources/openapi4.yaml";
        Path yamlPath = Paths.get(yamlFile);
        if (!Files.exists(yamlPath)) {
            return;
        }
        Map<String, Object> spec = parser.parse(yamlFile);
        Path tempDir = Files.createTempDirectory("postman-test");
        TestConfig cfg = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("postmanNegativeTests", Boolean.FALSE);
        cfg.setAdditionalProperties(props);
        generator.generate(spec, tempDir.toString(), cfg, null);

        String json = Files.readString(findPostmanCollectionFile(tempDir));
        assertFalse(json.contains("Negative-TCs"));

        deleteDirectory(tempDir);
    }

    @SuppressWarnings("unchecked")
    private int findNegativeTcFolderCount(Map<String, Object> collection) {
        int count = 0;
        List<Map<String, Object>> tags = (List<Map<String, Object>>) collection.get("item");
        if (tags == null) {
            return 0;
        }
        for (Map<String, Object> tag : tags) {
            List<Map<String, Object>> ops = (List<Map<String, Object>>) tag.get("item");
            if (ops == null) {
                continue;
            }
            for (Map<String, Object> opFolder : ops) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) opFolder.get("item");
                if (children == null) {
                    continue;
                }
                for (Map<String, Object> child : children) {
                    if ("Negative-TCs".equals(child.get("name"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Test
    @DisplayName("Test with openapi3.yaml file")
    void testWithOpenAPI3File() throws Exception {
        String yamlFile = "src/test/resources/openapi3.yaml";
        Path yamlPath = Paths.get(yamlFile);
        
        if (!Files.exists(yamlPath)) {
            System.out.println("Skipping test - openapi3.yaml not found");
            return;
        }
        
        Map<String, Object> spec = parser.parse(yamlFile);
        
        Path tempDir = Files.createTempDirectory("postman-test");
        String outputDir = tempDir.toString();
        
        generator.generate(spec, outputDir, new TestConfig(), null);
        
        // Verify files were generated
        Path collectionFile = findPostmanCollectionFile(tempDir);
        assertNotNull(collectionFile, "Postman collection file should be generated");
        
        Path envFile = findPostmanEnvironmentFile(tempDir);
        assertNotNull(envFile, "Postman environment file should be generated");
        
        // Verify collection is valid JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> collection = objectMapper.readValue(collectionFile.toFile(), Map.class);
        assertNotNull(collection, "Collection should be valid");
        assertTrue(collection.containsKey("info"), "Collection should have info");
        assertTrue(collection.containsKey("item"), "Collection should have items");
        
        // Verify no unescaped newlines in description fields
        String collectionJson = Files.readString(collectionFile);
        // Check that descriptions don't have unescaped newlines in JSON structure
        // (newlines should be escaped as \n in the JSON string values)
        assertFalse(collectionJson.matches("(?s).*\"description\"\\s*:\\s*\"[^\"]*\n[^\"]*\".*"), 
            "Description fields should not contain unescaped newlines in JSON");
        
        // Cleanup
        deleteDirectory(tempDir);
    }
    
    // Helper methods to create test specs
    
    private Map<String, Object> createBasicSpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of(
            "title", "Test API",
            "version", "1.0.0"
        ));
        spec.put("paths", new HashMap<>());
        return spec;
    }
    
    private Map<String, Object> createSpecWithMultilineDescription() {
        Map<String, Object> spec = createBasicSpec();
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> operation = new HashMap<>();
        
        operation.put("summary", "Test Operation");
        operation.put("description", "Line 1\nLine 2\nLine 3");
        operation.put("operationId", "testOperation");
        operation.put("tags", List.of("Test"));
        
        pathItem.put("get", operation);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
    
    private Map<String, Object> createSpecWithServers() {
        Map<String, Object> spec = createBasicSpec();
        spec.put("servers", List.of(
            Map.of("url", "https://api.example.com/v1")
        ));
        return spec;
    }
    
    private Map<String, Object> createSpecWithExamples() {
        Map<String, Object> spec = createBasicSpec();
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> operation = new HashMap<>();
        
        operation.put("summary", "Test with Examples");
        operation.put("operationId", "testWithExamples");
        operation.put("tags", List.of("Test"));
        
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> jsonContent = new HashMap<>();
        
        // Add examples
        Map<String, Object> examples = new HashMap<>();
        Map<String, Object> example1 = new HashMap<>();
        example1.put("summary", "Example 1");
        example1.put("value", Map.of(
            "dataSource", Map.of(
                "type", "AWS S3 bucket",
                "path", "s3://mybucket/myfolder/",
                "region", "us-east-1",
                "credentials", Map.of(
                    "accessKeyId", "AKIAIOSFODNN7EXAMPLE",
                    "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
                )
            ),
            "operation", "import"
        ));
        examples.put("Example 1", example1);
        jsonContent.put("examples", examples);
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        jsonContent.put("schema", schema);
        content.put("application/json", jsonContent);
        requestBody.put("content", content);
        operation.put("requestBody", requestBody);
        
        pathItem.put("post", operation);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }

    @Test
    @DisplayName("Composed allOf request body uses IntegrationScenarioSupport synthesis")
    void testComposedSchemaRequestBody() throws Exception {
        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("type", "object");
        overlay.put("required", List.of("name"));
        overlay.put("properties", Map.of("name", Map.of("type", "string")));

        Map<String, Object> createFolder = new LinkedHashMap<>();
        createFolder.put("allOf", List.of(overlay, Map.of("$ref", "#/components/schemas/Folder")));

        Map<String, Object> folder = new LinkedHashMap<>();
        folder.put("type", "object");
        folder.put("properties", Map.of("description", Map.of("type", "string")));

        Map<String, Object> post = new LinkedHashMap<>();
        post.put("operationId", "createFolder");
        post.put("tags", List.of("Folders"));
        post.put("requestBody", Map.of("content", Map.of("application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/createFolder")))));
        post.put("responses", Map.of("201", Map.of("description", "Created")));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Folders", "version", "1.0"));
        spec.put("components", Map.of("schemas", Map.of("Folder", folder, "createFolder", createFolder)));
        spec.put("paths", Map.of("/folders", Map.of("post", post)));

        Path tempDir = Files.createTempDirectory("postman-allof");
        generator.generate(spec, tempDir.toString(), new TestConfig(), null);

        Path collectionFile = findPostmanCollectionFile(tempDir);
        Map<String, Object> collection = Util.asStringObjectMap(
                objectMapper.readValue(collectionFile.toFile(), Map.class));

        boolean foundNameInBody = false;
        for (Map<String, Object> tagFolder : (List<Map<String, Object>>) collection.get("item")) {
            for (Map<String, Object> node : flattenPostmanItems(tagFolder)) {
                Map<String, Object> request = (Map<String, Object>) node.get("request");
                if (request == null) {
                    continue;
                }
                Map<String, Object> body = (Map<String, Object>) request.get("body");
                if (body == null) {
                    continue;
                }
                String raw = (String) body.get("raw");
                if (raw != null && raw.contains("\"name\"") && !raw.contains("\"example\": \"value\"")) {
                    foundNameInBody = true;
                }
            }
        }
        assertTrue(foundNameInBody, "Happy-path body should include flattened name field");

        deleteDirectory(tempDir);
    }

    @Test
    @DisplayName("Array-of-enum query param defaults use first enum value")
    void testArrayEnumQueryDefault() throws Exception {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", "folderAdditionalAttributes");
        param.put("in", "query");
        param.put("schema", Map.of(
                "type", "array",
                "items", Map.of("type", "string", "enum", List.of("description", "permissions"))));

        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getFolder");
        get.put("tags", List.of("Folders"));
        get.put("parameters", List.of(param));
        get.put("responses", Map.of("200", Map.of("description", "OK")));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Folders", "version", "1.0"));
        spec.put("paths", Map.of("/folders/{id}", Map.of("get", get)));

        Path tempDir = Files.createTempDirectory("postman-array-enum");
        generator.generate(spec, tempDir.toString(), new TestConfig(), null);

        Path envFile = findPostmanEnvironmentFile(tempDir);
        Map<String, Object> env = Util.asStringObjectMap(objectMapper.readValue(envFile.toFile(), Map.class));
        List<Map<String, Object>> values = (List<Map<String, Object>>) env.get("values");

        String attrDefault = values.stream()
                .filter(v -> "folderAdditionalAttributes".equals(v.get("key")))
                .map(v -> (String) v.get("value"))
                .findFirst()
                .orElse(null);

        assertEquals("description", attrDefault);

        deleteDirectory(tempDir);
    }
    
    /**
     * Depth-first walk of Postman folder items; returns leaf items that may have a {@code request} map.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenPostmanItems(Map<String, Object> folderOrItem) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (folderOrItem == null) {
            return out;
        }
        if (folderOrItem.containsKey("request")) {
            out.add(folderOrItem);
            return out;
        }
        if (folderOrItem.containsKey("item")) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) folderOrItem.get("item");
            if (children != null) {
                for (Map<String, Object> child : children) {
                    out.addAll(flattenPostmanItems(child));
                }
            }
        }
        return out;
    }

    private Path findPostmanCollectionFile(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".postman_collection.json"))
                .findFirst()
                .orElse(null);
        }
    }
    
    private Path findPostmanEnvironmentFile(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".postman_environment.json"))
                .findFirst()
                .orElse(null);
        }
    }
    
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore deletion errors
                        }
                    });
            }
        }
    }
}

