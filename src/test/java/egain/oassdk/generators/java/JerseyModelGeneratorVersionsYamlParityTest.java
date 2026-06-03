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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for contentmgr {@code Versions.yaml} shapes: merged {@code allOf} must keep
 * strongly typed {@code articleType} and {@code ownedBy} fields (not {@code Object}).
 */
@DisplayName("Versions.yaml VersionForCreate/EditArticle type parity")
class JerseyModelGeneratorVersionsYamlParityTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("VersionForCreateArticle uses ArticleTypeProperties and BasicUser")
    void versionForCreateArticleFieldTypes() throws OASSDKException, IOException {
        String generated = generateModel("VersionForCreateArticle.java");
        assertStrongVersionFieldTypes(generated);
    }

    @Test
    @DisplayName("VersionForEditArticle uses ArticleTypeProperties and BasicUser")
    void versionForEditArticleFieldTypes() throws OASSDKException, IOException {
        String generated = generateModel("VersionForEditArticle.java");
        assertStrongVersionFieldTypes(generated);
    }

    private String generateModel(String fileName) throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/versions_contentmgr_bundle/knowledge/models/contentmgr/v4/Versions.yaml")
                .toAbsolutePath();
        Path bundleRoot = Path.of("src/test/resources/versions_contentmgr_bundle").toAbsolutePath();

        Path outputDir = tempOutputDir.resolve(fileName.replace(".java", ""));
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.example.api.model")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundleRoot.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        try (Stream<Path> walk = Files.walk(outputDir)) {
            Path javaFile = walk
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(fileName + " not found under " + outputDir));
            return Files.readString(javaFile, StandardCharsets.UTF_8);
        }
    }

    private static void assertStrongVersionFieldTypes(String generated) {
        assertTrue(generated.contains("ArticleTypeProperties"),
                "articleType should resolve to ArticleTypeProperties");
        assertTrue(generated.contains("BasicUser"),
                "ownedBy should resolve to BasicUser");
        assertFalse(generated.contains("private Object articleType"),
                "articleType must not be Object");
        assertFalse(generated.contains("private Object ownedBy"),
                "ownedBy must not be Object");
        assertFalse(generated.contains("Object getArticleType"),
                "articleType getter must not return Object");
        assertFalse(generated.contains("Object getOwnedBy"),
                "ownedBy getter must not return Object");
    }
}
