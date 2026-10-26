package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: OAS array-of-array of {@code TagCategory} must keep {@code List<List<TagCategory>>}
 * but import {@code TagCategory}, not {@code List<TagCategory>} as a modelsOnly package.
 */
class JerseyModelGeneratorNestedArrayImportTest {

    @Test
    @DisplayName("PublishView nested array imports TagCategory, not List<TagCategory>")
    void publishViewNestedArrayImportsElementType(@TempDir Path outputDir) throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/publish_view_nested_array.yaml");
        assertTrue(Files.isRegularFile(specPath), "Missing bundled spec: " + specPath);

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4")
                .outputDir(outputDir.toString())
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path publishViewJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            publishViewJava = walk
                    .filter(p -> p.getFileName().toString().equals("PublishView.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("PublishView.java not found under " + outputDir));
        }
        String generated = Files.readString(publishViewJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("private List<List<TagCategory>> tagCategories"),
                "Expected nested List field in:\n" + generated);
        assertTrue(generated.contains("import com.egain.bindings.ws.model.xsds.common.v4.tagcategory.TagCategory;"),
                "Expected TagCategory import in:\n" + generated);
        assertFalse(generated.contains("list<tagcategory>"),
                "Must not treat List<TagCategory> as a package:\n" + generated);
        assertFalse(generated.contains("import com.egain.bindings.ws.model.xsds.common.v4.list"),
                "Must not import a list<> package:\n" + generated);
    }
}
