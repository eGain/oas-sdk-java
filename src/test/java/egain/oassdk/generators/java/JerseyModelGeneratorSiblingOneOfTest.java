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
 * Schemas with sibling {@code properties} and {@code oneOf} (e.g. filemgr AssetPreuploadInput) must merge
 * base properties into the generated model, not only oneOf branch overlays.
 */
@DisplayName("JerseyModelGenerator sibling properties + oneOf")
class JerseyModelGeneratorSiblingOneOfTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("AssetPreuploadInput merges sibling properties with oneOf overlays")
    void assetPreuploadInput_mergesSiblingPropertiesWithOneOf() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("asset-preupload");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        OASSDK sdk = new OASSDK(config, null, null);
        try {
            sdk.loadSpec("src/test/resources/asset_preupload_sibling_oneof.yaml");
            sdk.generateApplication("java", "jersey", "com.test.filemgr", outputDir.toString());

            Path modelJava;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                modelJava = walk
                        .filter(p -> p.getFileName().toString().equals("AssetPreuploadInput.java"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("AssetPreuploadInput.java not found under " + outputDir));
            }
            String content = Files.readString(modelJava, StandardCharsets.UTF_8);

            assertTrue(content.contains("private String fileName"), "fileName must be a String field");
            assertTrue(content.contains("private String contentType"), "contentType must be a String field");
            assertTrue(content.contains("private int size"), "size must be an int field");
            assertTrue(content.contains("private String application"), "application must be a String field");
            assertTrue(content.contains("private Boolean isInline"), "isInline must be a Boolean field");
            assertFalse(content.contains("private Object application"),
                    "application must not fall back to Object when oneOf branches redefine enum");
            assertFalse(content.contains("private Object isInline"),
                    "isInline must not fall back to Object");
            assertTrue(content.contains("@NotNull"), "base required fields must retain @NotNull");
            assertTrue(content.contains("@Pattern"), "string constraints must be emitted");
        } finally {
            sdk.close();
        }
    }
}
