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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: PAI #981 inline {@code attribValues.items} with {@code title: L10NString} must
 * generate {@code List<L10NString>}, not {@code List<Object>}, when {@code L10NString} is in spec.
 */
class JerseyModelGeneratorCustomAttributeL10NParityTest {

    @Test
    @DisplayName("CustomAttributeL10N.attribValues resolves to List<L10NString> from bundled spec")
    void customAttributeL10nAttribValuesTypedFromBundledSpec(@TempDir Path outputDir) throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/custom_attribute_l10n_title.yaml");
        assertTrue(Files.isRegularFile(specPath), "Missing bundled spec: " + specPath);

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path customAttrJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            customAttrJava = walk
                    .filter(p -> p.getFileName().toString().equals("CustomAttributeL10N.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("CustomAttributeL10N.java not found under " + outputDir));
        }
        String generated = Files.readString(customAttrJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("private List<L10NString> attribValues"),
                "Expected List<L10NString> in:\n" + generated);
    }
}
