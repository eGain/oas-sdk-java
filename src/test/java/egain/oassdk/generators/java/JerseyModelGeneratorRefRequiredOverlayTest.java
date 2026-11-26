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
 * CreateArticle.macro with {@code required: [name]} and stub {@code properties} (no second Macro $ref)
 * must still generate an inner class so Bean Validation honors {@code name}, without shadowing the
 * top-level {@code Macro} used by sibling fields. A {@code oneOf} variant that tightens {@code required}
 * on a {@code $ref} property must not reshape the shared bean.
 */
@DisplayName("JerseyModelGenerator $ref + nested required overlay")
class JerseyModelGeneratorRefRequiredOverlayTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("CreateArticle.macro overlay generates inner class with required name")
    void createArticleMacro_requiredOverlay_generatesInnerClass() throws OASSDKException, IOException {
        String content = generateModel("src/test/resources/create_article_macro_required_overlay.yaml",
                "create-article-macro", "CreateArticle.java");

        assertTrue(content.contains("private CreateArticle.MacroOverlay macro"),
                "macro field must be inner class CreateArticle.MacroOverlay, not top-level Macro:\n" + content);
        assertTrue(content.contains("public static class MacroOverlay"),
                "CreateArticle must contain static inner class MacroOverlay");
        assertTrue(content.contains("name = \"name\", required = true")
                        || content.contains("@NotNull"),
                "inner MacroOverlay.name must be required for Bean Validation");
        assertTrue(content.contains("private String name"),
                "inner MacroOverlay must keep name");
        assertTrue(content.contains("isSystemCreated") || content.contains("private Boolean isSystemCreated"),
                "inner MacroOverlay must keep Macro fields from the $ref, not only the stub name overlay");
        assertFalse(content.contains("private Macro macro;"),
                "must not alias the top-level Macro type and drop required name");
    }

    @Test
    @DisplayName("Inner overlay class does not shadow top-level Macro used by sibling fields")
    void createArticleMacro_requiredOverlay_doesNotShadowSiblingMacroFields() throws OASSDKException, IOException {
        String content = generateModel("src/test/resources/create_article_macro_required_overlay.yaml",
                "create-article-macro-shadow", "CreateArticle.java");

        assertFalse(content.contains("public static class Macro implements") || content.contains("public static class Macro {"),
                "inner class must not be named Macro: it would shadow the top-level Macro model:\n" + content);
        assertTrue(content.contains("private Macro fallbackMacro;"),
                "fallbackMacro must stay the top-level Macro type:\n" + content);
        assertTrue(content.contains("private List<Macro> macros;"),
                "macros must stay List<Macro> of the top-level model:\n" + content);
    }

    @Test
    @DisplayName("oneOf variant required on a $ref property does not union required or create inner class")
    void oneOfVariantRequired_onRefProperty_keepsSharedModelOptional() throws OASSDKException, IOException {
        String content = generateModel("src/test/resources/oneof_variant_required_overlay.yaml",
                "oneof-variant-required", "Thing.java");

        assertTrue(content.contains("private Payload payload;"),
                "payload must alias the top-level Payload model:\n" + content);
        assertFalse(content.contains("Thing.Payload"),
                "oneOf variant required must not promote payload to an inner class:\n" + content);
        assertFalse(content.contains("@NotNull"),
                "variant-required fields must stay optional on the unified bean:\n" + content);
    }

    private String generateModel(String specPath, String outputName, String modelFileName)
            throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve(outputName);
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath);
            sdk.generateApplication("java", "jersey", "com.test.article", outputDir.toString());

            Path modelJava;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                modelJava = walk
                        .filter(p -> p.getFileName().toString().equals(modelFileName))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(modelFileName + " not found under " + outputDir));
            }
            return Files.readString(modelJava, StandardCharsets.UTF_8);
        }
    }
}
