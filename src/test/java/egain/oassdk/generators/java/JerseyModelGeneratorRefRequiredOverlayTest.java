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
 * must still generate {@code CreateArticle.Macro} so Bean Validation honors {@code name}.
 */
@DisplayName("JerseyModelGenerator $ref + nested required overlay")
class JerseyModelGeneratorRefRequiredOverlayTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("CreateArticle.macro overlay generates inner class with required name")
    void createArticleMacro_requiredOverlay_generatesInnerClass() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("create-article-macro");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec("src/test/resources/create_article_macro_required_overlay.yaml");
            sdk.generateApplication("java", "jersey", "com.test.article", outputDir.toString());

            Path modelJava;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                modelJava = walk
                        .filter(p -> p.getFileName().toString().equals("CreateArticle.java"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("CreateArticle.java not found under " + outputDir));
            }
            String content = Files.readString(modelJava, StandardCharsets.UTF_8);

            assertTrue(content.contains("private CreateArticle.Macro macro"),
                    "macro field must be inner class CreateArticle.Macro, not top-level Macro:\n" + content);
            assertTrue(content.contains("public static class Macro"),
                    "CreateArticle must contain static inner class Macro");
            assertTrue(content.contains("name = \"name\", required = true")
                            || content.contains("@NotNull"),
                    "inner Macro.name must be required for Bean Validation");
            assertTrue(content.contains("private String name"),
                    "inner Macro must keep name");
            assertTrue(content.contains("isSystemCreated") || content.contains("private Boolean isSystemCreated"),
                    "inner Macro must keep Macro fields from the $ref, not only the stub name overlay");
            assertFalse(content.contains("private Macro macro;"),
                    "must not alias the top-level Macro type and drop required name");
        }
    }
}
