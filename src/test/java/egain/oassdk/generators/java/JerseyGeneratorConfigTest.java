package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.parser.OASParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JerseyGenerator behavior with null config and modelsOnly option.
 */
@DisplayName("JerseyGenerator Config and Null-Safety Tests")
public class JerseyGeneratorConfigTest {

    private static final String TEST_YAML = "src/test/resources/openapi3.yaml";
    private static final String OPENAPI_YAML = "src/test/resources/openapi.yaml";
    private static final String PACKAGE_NAME = "com.test.api";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Generate with null config does not throw NPE and produces output")
    public void testGenerateWithNullConfigNoNPE() throws Exception {
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(TEST_YAML);
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, TEST_YAML);

        Path outputDir = tempDir.resolve("null-config");
        JerseyGenerator generator = new JerseyGenerator();

        assertDoesNotThrow(() ->
            generator.generate(resolvedSpec, outputDir.toString(), null, PACKAGE_NAME)
        );

        // With null config, isModelsOnly is false so full generation runs: expect model dir and optionally resources
        String packagePath = PACKAGE_NAME.replace(".", "/");
        Path modelDir = outputDir.resolve("src/main/java/" + packagePath + "/model");
        assertTrue(Files.exists(modelDir), "Model directory should exist when config is null (full generation)");
    }

    @Test
    @DisplayName("Generate with modelsOnly true produces only models and no MainApplication/resources")
    public void testModelsOnlySkipsApplicationAndResources() throws Exception {
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(TEST_YAML);
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, TEST_YAML);

        Path outputDir = tempDir.resolve("models-only");
        GeneratorConfig config = new GeneratorConfig();
        config.setModelsOnly(true);
        config.setPackageName(PACKAGE_NAME);

        JerseyGenerator generator = new JerseyGenerator();
        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        String packagePath = PACKAGE_NAME.replace(".", "/");
        // modelsOnly: output is under outputDir/packagePath/ (no src/main/java)
        Path packageDir = outputDir.resolve(packagePath);
        assertTrue(Files.exists(packageDir), "Package directory should exist for modelsOnly");

        // Main application and resources must not exist for modelsOnly (they go under src/main/java)
        Path mainApp = outputDir.resolve("src/main/java/" + packagePath + "/MainApplication.java");
        Path resourcesDir = outputDir.resolve("src/main/java/" + packagePath + "/resources");
        assertFalse(Files.exists(mainApp), "MainApplication should not be generated when modelsOnly is true");
        assertFalse(Files.exists(resourcesDir), "Resources directory should not be generated when modelsOnly is true");
    }

    @Test
    @DisplayName("modelsOnly generates wrapper types for optional numeric fields without explicit useBoxedPrimitives")
    public void testModelsOnlyGeneratesBoxedOptionalNumericFields() throws Exception {
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(OPENAPI_YAML);
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, OPENAPI_YAML);

        Path outputDir = tempDir.resolve("models-only-boxed");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        config.setPackageName(PACKAGE_NAME);

        JerseyGenerator generator = new JerseyGenerator();
        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path orderJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            orderJava = walk
                    .filter(p -> p.getFileName().toString().equals("Order.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Order.java not found under " + outputDir));
        }
        String content = Files.readString(orderJava);
        assertTrue(content.contains("private Double totalAmount"),
                "modelsOnly should emit boxed Double for optional number fields");
        assertFalse(content.contains("private double totalAmount"),
                "modelsOnly must not emit primitive double for optional fields");
        assertTrue(content.contains("isSetTotalAmount()"),
                "modelsOnly should generate isSetTotalAmount for optional numeric field");
        assertTrue(content.contains("totalAmount != null"),
                "isSetTotalAmount should use null check for boxed optional numeric field");
        assertFalse(content.contains("isSetTotalAmount() {\n        return true;"),
                "isSetTotalAmount must not always return true for optional numeric field");
    }

    @Test
    @DisplayName("useBoxedPrimitives generates wrapper types in model fields")
    public void testUseBoxedPrimitivesGeneratesWrapperTypes() throws Exception {
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(OPENAPI_YAML);
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, OPENAPI_YAML);

        Path outputDir = tempDir.resolve("boxed-primitives");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .useBoxedPrimitives(true)
                .build();
        config.setPackageName(PACKAGE_NAME);

        JerseyGenerator generator = new JerseyGenerator();
        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path productJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            productJava = walk
                    .filter(p -> p.getFileName().toString().equals("Product.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Product.java not found under " + outputDir));
        }
        String content = Files.readString(productJava);
        assertTrue(content.contains("private Double price"), "Expected boxed Double for number field");
        assertFalse(content.contains("private double price"), "Must not emit primitive double when boxed mode is on");
    }

    @Test
    @DisplayName("Generate with null outputDir throws IllegalArgumentException")
    public void testNullOutputDirThrows() throws Exception {
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(TEST_YAML);
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, TEST_YAML);

        JerseyGenerator generator = new JerseyGenerator();
        assertThrows(IllegalArgumentException.class, () ->
            generator.generate(resolvedSpec, null, new GeneratorConfig(), PACKAGE_NAME)
        );
    }

    @Test
    @DisplayName("EGS-99382: modelsOnly uses numeric formats and integer fallback patterns")
    public void testModelsOnlyIntegerQueryParamValidation() throws Exception {
        String yamlContent = """
            openapi: 3.0.0
            info:
              title: Integer query parameter validation
              version: 1.0.0
            paths:
              /articles:
                get:
                  operationId: listArticles
                  parameters:
                    - name: pagenum
                      in: query
                      schema:
                        type: integer
                        format: int64
                    - name: pagesize
                      in: query
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: OK
            """;
        Path specFile = tempDir.resolve("integer-query-param.yaml");
        Files.writeString(specFile, yamlContent);

        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(specFile.toString());
        Map<String, Object> resolvedSpec = parser.resolveReferences(spec, specFile.toString());
        Path outputDir = tempDir.resolve("models-only-integer-query-param");
        GeneratorConfig config = GeneratorConfig.builder().modelsOnly(true).build();
        config.setPackageName(PACKAGE_NAME);

        new JerseyGenerator().generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path validators = outputDir.resolve(PACKAGE_NAME.replace(".", "/") + "/QueryParamValidators.txt");
        assertTrue(Files.exists(validators), "QueryParamValidators.txt should exist");
        String content = Files.readString(validators);

        assertTrue(content.contains("new FormatValidator(\"pagenum\", \"int64\""),
                "Formatted integer query params should use FormatValidator with their schema format");
        assertTrue(content.contains("new PatternValidator(\"pagesize\", \"^-?\\\\d+$\""),
                "Integer query params without a format should use PatternValidator");
        assertFalse(content.contains("new FormatValidator(\"pagesize\""),
                "Custom integer regex must not be passed to FormatValidator");
    }

}
