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
}
