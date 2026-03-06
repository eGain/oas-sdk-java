package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that the Alias model is generated from common.yaml and Alias.yaml with
 * id, name, and description as String attributes.
 */
@DisplayName("Alias model generation from common.yaml and Alias.yaml")
public class AliasModelFromYamlTest {

    private static final String ALIAS_TEST_SPEC = "src/test/resources/alias-test/api.yaml";

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("Generated Alias model has id, name, description as String with getters/setters")
    public void aliasModelHasIdNameDescriptionAsString() throws OASSDKException, IOException {
        Path specPath = Paths.get(ALIAS_TEST_SPEC);
        assertTrue(Files.exists(specPath), "Test spec should exist: " + ALIAS_TEST_SPEC);

        Path outputDir = tempOutputDir.resolve("generated-sdk");
        String packageName = "com.test.api";

        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specPath.toAbsolutePath().toString());
            sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        }

        Path aliasFile = outputDir.resolve("src/main/java")
                .resolve(packageName.replace(".", "/"))
                .resolve("model/Alias.java");

        assertTrue(Files.exists(aliasFile),
                "Alias.java should be generated when using common.yaml and Alias.yaml");

        String content = Files.readString(aliasFile);

        // Id, name, description must be generated as String
        assertTrue(content.contains("private String id") || content.contains("private String id;"),
                "Alias should have private String id");
        assertTrue(content.contains("private String name") || content.contains("private String name;"),
                "Alias should have private String name");
        assertTrue(content.contains("private String description") || content.contains("private String description;"),
                "Alias should have private String description");

        // Getters and setters for id, name, description
        assertTrue(content.contains("public String getId()"),
                "Alias should have getId() returning String");
        assertTrue(content.contains("public String getName()"),
                "Alias should have getName() returning String");
        assertTrue(content.contains("public String getDescription()"),
                "Alias should have getDescription() returning String");
        assertTrue(content.contains("public void setId(String "),
                "Alias should have setId(String)");
        assertTrue(content.contains("public void setName(String "),
                "Alias should have setName(String)");
        assertTrue(content.contains("public void setDescription(String "),
                "Alias should have setDescription(String)");
    }
}
