package egain.oassdk.testgenerators.mock;

import com.github.javafaker.Faker;
import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;
import egain.oassdk.testgenerators.common.TestSpecUtils;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock data generator
 * Generates realistic test data based on OpenAPI schema definitions
 */
public class MockDataGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;
    private final Random random = new Random();
    private final Faker faker = new Faker();
    private Map<String, Object> fullSpec;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;
        this.fullSpec = spec;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            // Extract API information
            String apiTitle = TestSpecUtils.getApiTitle(spec);

            // Generate mock data for all schemas
            generateMockDataForSchemas(spec, outputPath.toString(), apiTitle);
            generateOperationRequestBodies(spec, outputPath.resolve("operations").toString());

            // Generate mock data generator utility class
            generateMockDataGeneratorClass(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate mock data: " + e.getMessage(), e);
        }
    }

    /**
     * Generate mock data for all schemas
     */
    private void generateMockDataForSchemas(Map<String, Object> spec, String outputDir, String apiTitle) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return;
        }

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null || schemas.isEmpty()) {
            return;
        }

        // Determine instance count from config or use default
        int instanceCount = Constants.DEFAULT_MOCK_INSTANCES;
        if (config != null && config.getAdditionalProperties() != null) {
            Object countObj = config.getAdditionalProperties().get("mockInstanceCount");
            if (countObj instanceof Number) {
                instanceCount = ((Number) countObj).intValue();
            }
        }

        // Generate mock data for each schema
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            if (schema == null) continue;

            // Generate multiple mock data instances
            for (int i = 1; i <= instanceCount; i++) {
                Object mockData = generateMockDataFromSchema(schema, schemaName, 0);
                String jsonContent;
                if (mockData instanceof Map) {
                    jsonContent = convertToJson(Util.asStringObjectMap(mockData), 0);
                } else if (mockData instanceof List) {
                    jsonContent = listToJson((List<?>) mockData, 0);
                } else {
                    jsonContent = valueToJson(mockData, 0);
                }

                String fileName = schemaName + "_" + i + ".json";
                Files.write(Paths.get(outputDir, fileName), jsonContent.getBytes());
            }
        }

        // Generate a sample data file with all schemas
        generateSampleDataFile(schemas, outputDir);
    }

    /**
     * Generate mock data from a schema with recursion depth limit
     */
    Object generateMockDataFromSchema(Map<String, Object> schema, String schemaName, int depth) {
        if (depth > Constants.MAX_SCHEMA_RECURSION_DEPTH) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> mockData = new LinkedHashMap<>();

        String type = (String) schema.get("type");
        if (type == null) {
            // Check for allOf, oneOf, anyOf
            if (schema.containsKey("allOf")) {
                List<Map<String, Object>> allOf = Util.asStringObjectMapList(schema.get("allOf"));
                for (Map<String, Object> subSchema : allOf) {
                    if (subSchema.containsKey("$ref")) {
                        // Resolve $ref and merge its properties
                        Map<String, Object> resolved = resolveRef((String) subSchema.get("$ref"));
                        if (resolved != null) {
                            Object subData = generateMockDataFromSchema(resolved, schemaName, depth + 1);
                            if (subData instanceof Map) {
                                mockData.putAll(Util.asStringObjectMap(subData));
                            }
                        }
                    } else {
                        Object subData = generateMockDataFromSchema(subSchema, schemaName, depth + 1);
                        if (subData instanceof Map) {
                            mockData.putAll(Util.asStringObjectMap(subData));
                        }
                    }
                }
                return mockData;
            }
            if (schema.containsKey("oneOf")) {
                // Pick one random variant from oneOf
                List<Map<String, Object>> oneOf = Util.asStringObjectMapList(schema.get("oneOf"));
                if (oneOf != null && !oneOf.isEmpty()) {
                    Map<String, Object> chosen = oneOf.get(random.nextInt(oneOf.size()));
                    if (chosen.containsKey("$ref")) {
                        Map<String, Object> resolved = resolveRef((String) chosen.get("$ref"));
                        if (resolved != null) {
                            return generateMockDataFromSchema(resolved, schemaName, depth + 1);
                        }
                    }
                    return generateMockDataFromSchema(chosen, schemaName, depth + 1);
                }
            }
            if (schema.containsKey("anyOf")) {
                // Merge properties from a random subset of anyOf variants
                List<Map<String, Object>> anyOf = Util.asStringObjectMapList(schema.get("anyOf"));
                if (anyOf != null && !anyOf.isEmpty()) {
                    // Pick at least one, up to all
                    int count = random.nextInt(anyOf.size()) + 1;
                    List<Map<String, Object>> shuffled = new ArrayList<>(anyOf);
                    Collections.shuffle(shuffled, random);
                    for (int i = 0; i < count; i++) {
                        Map<String, Object> variant = shuffled.get(i);
                        if (variant.containsKey("$ref")) {
                            Map<String, Object> resolved = resolveRef((String) variant.get("$ref"));
                            if (resolved != null) {
                                Object subData = generateMockDataFromSchema(resolved, schemaName, depth + 1);
                                if (subData instanceof Map) {
                                    mockData.putAll(Util.asStringObjectMap(subData));
                                }
                            }
                        } else {
                            Object subData = generateMockDataFromSchema(variant, schemaName, depth + 1);
                            if (subData instanceof Map) {
                                mockData.putAll(Util.asStringObjectMap(subData));
                            }
                        }
                    }
                    return mockData;
                }
            }
            // Handle standalone $ref at schema level
            if (schema.containsKey("$ref")) {
                Map<String, Object> resolved = resolveRef((String) schema.get("$ref"));
                if (resolved != null) {
                    return generateMockDataFromSchema(resolved, schemaName, depth + 1);
                }
            }
            return mockData;
        }

        switch (type) {
            case "object":
                Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
                List<String> required = Util.asStringList(schema.get("required"));

                if (properties != null) {
                    for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                        String propName = propEntry.getKey();
                        Map<String, Object> propSchema = Util.asStringObjectMap(propEntry.getValue());

                        // Check if required
                        boolean isRequired = required != null && required.contains(propName);

                        // Always generate required fields; generate optional fields with configurable probability
                        if (isRequired || random.nextBoolean()) {
                            Object value = generateValueFromSchema(propSchema, propName, depth + 1);
                            if (value != null) {
                                mockData.put(propName, value);
                            }
                        }
                    }
                }
                break;

            case "array":
                Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                List<Object> arrayData = new ArrayList<>();
                int arraySize = random.nextInt(3) + 1; // 1-3 items
                for (int i = 0; i < arraySize; i++) {
                    Object item = generateValueFromSchema(items, "item", depth + 1);
                    if (item != null) {
                        arrayData.add(item);
                    }
                }
                // Return the array directly, not wrapped in {"items": [...]}
                return arrayData;
        }

        return mockData;
    }

    /**
     * Resolve a $ref string to the actual schema map
     */
    private Map<String, Object> resolveRef(String ref) {
        if (ref == null || fullSpec == null) return null;
        if (ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            Map<String, Object> components = Util.asStringObjectMap(fullSpec.get("components"));
            if (components != null) {
                Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                if (schemas != null) {
                    return Util.asStringObjectMap(schemas.get(schemaName));
                }
            }
        }
        return null;
    }

    /**
     * Generate a value from a schema
     */
    Object generateValueFromSchema(Map<String, Object> schema, String fieldName, int depth) {
        if (schema == null) {
            return null;
        }
        if (depth > Constants.MAX_SCHEMA_RECURSION_DEPTH) {
            return null;
        }

        // Check for $ref first
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"));
            if (resolved != null) {
                return generateValueFromSchema(resolved, fieldName, depth + 1);
            }
            return null;
        }

        // Check for example first
        if (schema.containsKey("example")) {
            return schema.get("example");
        }

        // Check for enum
        if (schema.containsKey("enum")) {
            List<Object> enumValues = Util.asObjectList(schema.get("enum"));
            if (enumValues != null && !enumValues.isEmpty()) {
                return enumValues.get(random.nextInt(enumValues.size()));
            }
        }

        String type = (String) schema.get("type");
        if (type == null) {
            // Could be a composed schema (allOf/oneOf/anyOf)
            if (schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
                return generateMockDataFromSchema(schema, fieldName, depth);
            }
            return "unknown";
        }

        switch (type) {
            case "string":
                return generateStringValue(schema, fieldName);
            case "integer":
                return generateIntegerValue(schema);
            case "number":
                return generateNumberValue(schema);
            case "boolean":
                return random.nextBoolean();
            case "array":
                return generateArrayValue(schema, depth);
            case "object":
                return generateMockDataFromSchema(schema, fieldName, depth + 1);
            default:
                return "mock_" + fieldName;
        }
    }

    /**
     * Generate string value using Faker library
     */
    private String generateStringValue(Map<String, Object> schema, String fieldName) {
        // Check for pattern
        String pattern = (String) schema.get("pattern");
        if (pattern != null) {
            // Simple pattern matching - generate based on common patterns
            if (pattern.contains("^[a-zA-Z0-9]")) {
                return faker.regexify("[A-Z0-9]{4,10}");
            }
        }

        // Check for format
        String format = (String) schema.get("format");
        if (format != null) {
            switch (format) {
                case "date-time":
                    return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
                case "date":
                    return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE);
                case "email":
                    return faker.internet().emailAddress();
                case "uri":
                case "url":
                    return faker.internet().url();
                case "uuid":
                    return UUID.randomUUID().toString();
                case "password":
                    return faker.internet().password(8, 20, true, true, true);
                case "byte":
                    return Base64.getEncoder().encodeToString(faker.lorem().word().getBytes());
                case "binary":
                    return faker.lorem().characters(16, 32);
                case "hostname":
                    return faker.internet().domainName();
                case "ipv4":
                    return faker.internet().ipV4Address();
                case "ipv6":
                    return faker.internet().ipV6Address();
            }
        }

        // Generate based on field name using Faker
        String name = fieldName.toLowerCase();
        if (name.contains("id") || name.contains("identifier")) {
            return String.valueOf(faker.number().numberBetween(1000, 99999));
        } else if (name.contains("name") || name.contains("title")) {
            if (name.contains("first")) {
                return faker.name().firstName();
            } else if (name.contains("last")) {
                return faker.name().lastName();
            } else if (name.contains("full")) {
                return faker.name().fullName();
            } else {
                String word = faker.lorem().word();
                return word.substring(0, Math.min(20, word.length()));
            }
        } else if (name.contains("description")) {
            return faker.lorem().sentence();
        } else if (name.contains("email")) {
            return faker.internet().emailAddress();
        } else if (name.contains("url") || name.contains("uri")) {
            return faker.internet().url();
        } else if (name.contains("phone")) {
            return faker.phoneNumber().phoneNumber();
        } else if (name.contains("address") || name.contains("street")) {
            return faker.address().streetAddress();
        } else if (name.contains("city")) {
            return faker.address().city();
        } else if (name.contains("state") || name.contains("province")) {
            return faker.address().state();
        } else if (name.contains("zip") || name.contains("postal")) {
            return faker.address().zipCode();
        } else if (name.contains("country")) {
            return faker.address().country();
        } else if (name.contains("company")) {
            return faker.company().name();
        } else if (name.contains("job") || name.contains("position")) {
            return faker.job().title();
        } else if (name.contains("color")) {
            return faker.color().name();
        } else if (name.contains("animal")) {
            return faker.animal().name();
        } else if (name.contains("lorem") || name.contains("text")) {
            return faker.lorem().sentence();
        }

        // Default: use Faker to generate a random word
        return faker.lorem().word();
    }

    /**
     * Generate integer value
     */
    private Integer generateIntegerValue(Map<String, Object> schema) {
        // Handle both Integer and Long types (YAML/JSON parsers may return Long)
        Object minObj = schema.get("minimum");
        Object maxObj = schema.get("maximum");

        int min = 1;
        int max = 1000;

        if (minObj != null) {
            if (minObj instanceof Integer) {
                min = (Integer) minObj;
            } else if (minObj instanceof Long) {
                min = ((Long) minObj).intValue();
            } else if (minObj instanceof Number) {
                min = ((Number) minObj).intValue();
            }
        }

        if (maxObj != null) {
            if (maxObj instanceof Integer) {
                max = (Integer) maxObj;
            } else if (maxObj instanceof Long) {
                max = ((Long) maxObj).intValue();
            } else if (maxObj instanceof Number) {
                max = ((Number) maxObj).intValue();
            }
        }

        if (max < min) {
            int t = min;
            min = max;
            max = t;
        }
        if (min == max) {
            return min;
        }
        long range = (long) max - min + 1L;
        if (range <= 0 || range > Integer.MAX_VALUE) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt((int) range);
    }

    /**
     * Generate number value
     */
    private Double generateNumberValue(Map<String, Object> schema) {
        Number minimum = (Number) schema.get("minimum");
        Number maximum = (Number) schema.get("maximum");

        double min = minimum != null ? minimum.doubleValue() : 0.0;
        double max = maximum != null ? maximum.doubleValue() : 1000.0;

        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * Generate array value
     */
    private List<Object> generateArrayValue(Map<String, Object> schema, int depth) {
        Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
        List<Object> array = new ArrayList<>();
        int size = random.nextInt(3) + 1; // 1-3 items
        for (int i = 0; i < size; i++) {
            Object item = generateValueFromSchema(items, "item", depth + 1);
            if (item != null) {
                array.add(item);
            }
        }
        return array;
    }

    /**
     * Generate a JSON request body string from an operation's requestBody schema.
     * This is used by other generators to create realistic request bodies.
     */
    public String generateRequestBodyJson(Map<String, Object> operation, Map<String, Object> spec) {
        this.fullSpec = spec;
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) return "{}";

        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) return "{}";

        // Try application/json first, then any available content type
        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) {
            // Try first available
            for (Object val : content.values()) {
                mediaType = Util.asStringObjectMap(val);
                if (mediaType != null) break;
            }
        }
        if (mediaType == null) return "{}";

        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) return "{}";

        // Resolve $ref if present
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"));
            if (resolved != null) {
                schema = resolved;
            }
        }

        Object mockData = generateMockDataFromSchema(schema, "requestBody", 0);
        if (mockData instanceof Map) {
            return convertToJson(Util.asStringObjectMap(mockData), 0);
        } else if (mockData instanceof List) {
            return listToJson((List<?>) mockData, 0);
        }
        return "{}";
    }

    private void generateOperationRequestBodies(Map<String, Object> spec, String outputDir) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : List.of("get", "post", "put", "patch", "delete")) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null || !operation.containsKey("requestBody")) {
                    continue;
                }
                String opId = (String) operation.get("operationId");
                if (opId == null || opId.isBlank()) {
                    opId = method + pathEntry.getKey().replaceAll("[^a-zA-Z0-9]", "_");
                }
                String json = bindMockRequestPlaceholders(
                        IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec));
                Files.writeString(Paths.get(outputDir, opId + "_request.json"), json);
            }
        }
    }

    private String generateRequestBodyJsonForWrite(Map<String, Object> operation, Map<String, Object> spec) {
        String json = generateRequestBodyJson(operation, spec);
        if (json == null || json.isBlank()) {
            return "{}";
        }
        return bindMockRequestPlaceholders(json);
    }

    private static String bindMockRequestPlaceholders(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        String bound = json.replaceAll(
                "\"parent\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"[^\"]*\"",
                "\"parent\":{\"id\":\"${test.parent.folder.id}\"}");
        if (!bound.contains("\"name\"")) {
            bound = bound.replaceFirst("\\{", "{\"name\":\"SDK-mock-folder\",");
        }
        return bound;
    }

    /**
     * Generate sample data file
     */
    private void generateSampleDataFile(Map<String, Object> schemas, String outputDir) throws IOException {
        Map<String, Object> sampleData = new LinkedHashMap<>();

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            if (schema != null) {
                Object mockData = generateMockDataFromSchema(schema, schemaName, 0);
                sampleData.put(schemaName, mockData);
            }
        }

        String jsonContent = convertToJson(sampleData, 0);
        Files.write(Paths.get(outputDir, "sample-data.json"), jsonContent.getBytes());
    }

    /**
     * Generate mock data generator utility class
     */
    private void generateMockDataGeneratorClass(String outputDir) throws IOException {
        String classContent = "package com.example.api;\n\n" +
                "import java.util.*;\n" +
                "import java.util.concurrent.ThreadLocalRandom;\n" +
                "import java.time.LocalDateTime;\n" +
                "import java.time.format.DateTimeFormatter;\n" +
                "import java.util.UUID;\n\n" +
                "/**\n" +
                " * Mock Data Generator Utility\n" +
                " * Generated from OpenAPI specification\n" +
                " * \n" +
                " * This utility class provides methods to generate mock data\n" +
                " * for testing purposes based on OpenAPI schema definitions.\n" +
                " */\n" +
                "public class MockDataGenerator {\n\n" +
                "    private static final Random random = new Random();\n\n" +
                "    /**\n" +
                "     * Generate mock data for a schema\n" +
                "     */\n" +
                "    public static Map<String, Object> generateMockData(String schemaName) {\n" +
                "        // TODO: Implement schema-based mock data generation\n" +
                "        return new HashMap<>();\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * Generate random string value\n" +
                "     */\n" +
                "    public static String generateString(String fieldName, int maxLength) {\n" +
                "        return \"mock_\" + fieldName + \"_\" + random.nextInt(1000);\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * Generate random integer value\n" +
                "     */\n" +
                "    public static Integer generateInteger(int min, int max) {\n" +
                "        return ThreadLocalRandom.current().nextInt(min, max + 1);\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * Generate random boolean value\n" +
                "     */\n" +
                "    public static Boolean generateBoolean() {\n" +
                "        return random.nextBoolean();\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * Generate random date-time string\n" +
                "     */\n" +
                "    public static String generateDateTime() {\n" +
                "        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * Generate random UUID\n" +
                "     */\n" +
                "    public static String generateUUID() {\n" +
                "        return UUID.randomUUID().toString();\n" +
                "    }\n\n" +
                "}\n";

        String packageDir = outputDir + "/com/example/api";
        Files.createDirectories(Paths.get(packageDir));
        Files.write(Paths.get(packageDir, "MockDataGenerator.java"), classContent.getBytes());
    }

    /**
     * Convert map to JSON string
     */
    String convertToJson(Map<String, Object> data, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);

        if (data == null || data.isEmpty()) {
            return "{}";
        }

        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",\n");
            sb.append(indentStr).append("  \"").append(entry.getKey()).append("\": ");
            sb.append(valueToJson(entry.getValue(), indent + 1));
            first = false;
        }
        sb.append("\n").append(indentStr).append("}");

        return sb.toString();
    }

    /**
     * Convert a list to JSON array string
     */
    private String listToJson(List<?> list, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(indentStr).append("  ").append(valueToJson(list.get(i), indent + 1));
        }
        sb.append("\n").append(indentStr).append("]");
        return sb.toString();
    }

    /**
     * Convert value to JSON
     */
    String valueToJson(Object value, int indent) {
        if (value == null) {
            return "null";
        }

        if (value instanceof String str) {
            return "\"" + escapeJson(str) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            Map<String, Object> mapValue = Util.asStringObjectMap(value);
            return convertToJson(mapValue, indent);
        } else if (value instanceof List) {
            return listToJson((List<?>) value, indent);
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    /**
     * Escape JSON string
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Helper methods

    @Override
    public String getName() {
        return "Mock Data Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "mock_data";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }
}
