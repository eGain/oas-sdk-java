package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.generators.common.OpenApiSchemaUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression: PAI #981 inline {@code attribValues.items} with {@code title: L10NString} must
 * generate {@code List<L10NString>}, not {@code List<Object>}, when {@code L10NString} is in spec.
 */
class JerseyModelGeneratorCustomAttributeL10NParityTest {

    private static final Path PAI_ROOT = Path.of("../platform-api-interfaces").toAbsolutePath().normalize();
    private static final Path CONTENTMGR_API = PAI_ROOT.resolve("published/knowledge/contentmgr/v4/api.yaml");

    @Test
    @DisplayName("CustomAttributeL10N.attribValues resolves to List<L10NString> from real PAI contentmgr spec")
    void customAttributeL10nAttribValuesTypedFromPaiSpec(@TempDir Path outputDir) throws OASSDKException, IOException {
        assumeTrue(Files.isRegularFile(CONTENTMGR_API), () -> "Missing PAI spec at " + CONTENTMGR_API);

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .searchPaths(java.util.List.of(PAI_ROOT.toString()))
                .build();

        Map<String, Object> spec;
        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(CONTENTMGR_API.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
            spec = readSpecViaParser();
        }

        Map<String, Object> customAttr = Util.asStringObjectMap(
                Util.asStringObjectMap(
                        Util.asStringObjectMap(spec.get("components")).get("schemas"))
                        .get("CustomAttributeL10N"));
        assertNotNull(customAttr, "CustomAttributeL10N must be in loaded spec");
        Map<String, Object> attribValues = Util.asStringObjectMap(
                Util.asStringObjectMap(customAttr.get("properties")).get("attribValues"));
        Map<String, Object> items = Util.asStringObjectMap(attribValues.get("items"));
        assertNotNull(items, "attribValues.items must exist");
        String byTitle = OpenApiSchemaUtils.findComponentSchemaNameByTitle(items, spec);
        assertEquals("L10NString", byTitle,
                "items schema keys=" + items.keySet() + " title=" + items.get("title"));

        Path customAttrJava = outputDir.resolve(
                "com/egain/bindings/ws/model/xsds/common/v4/content/customattributel10n/CustomAttributeL10N.java");
        assumeTrue(Files.isRegularFile(customAttrJava), () -> "Generated file missing: " + customAttrJava);
        String generated = Files.readString(customAttrJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("private List<L10NString> attribValues"),
                "Expected List<L10NString> in:\n" + generated);
    }

    @Test
    @DisplayName("CustomAttributeL10N.attribValues typed when loaded from platform-api-interfaces ZIP like digital xjc_gen")
    void customAttributeL10nAttribValuesTypedFromPaiZip(@TempDir Path outputDir) throws Exception {
        Path zipPath = outputDir.resolve("platform-api-interfaces.zip");
        assumeTrue(Files.isDirectory(PAI_ROOT), () -> "Missing PAI root at " + PAI_ROOT);
        zipDirectory(PAI_ROOT, zipPath);

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .specZipPath(zipPath.toString())
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.resolve("zip-gen").toString())
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec("published/knowledge/contentmgr/v4/api.yaml");
            sdk.generateApplication("java", "jersey", config.getPackageName(), config.getOutputDir());
        }

        Path customAttrJava = Path.of(config.getOutputDir()).resolve(
                "com/egain/bindings/ws/model/xsds/common/v4/content/customattributel10n/CustomAttributeL10N.java");
        String generated = Files.readString(customAttrJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("private List<L10NString> attribValues"),
                "ZIP load should match filesystem load; got:\n" + generated);
    }

    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            try (var walk = Files.walk(sourceDir)) {
                for (Path path : walk.filter(p -> !Files.isDirectory(p)).toList()) {
                    String entry = sourceDir.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private static Map<String, Object> readSpecViaParser() throws OASSDKException {
        GeneratorConfig config = GeneratorConfig.builder()
                .searchPaths(java.util.List.of(PAI_ROOT.toString()))
                .build();
        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(CONTENTMGR_API.toString());
            java.lang.reflect.Field specField = OASSDK.class.getDeclaredField("spec");
            specField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> loaded = (Map<String, Object>) specField.get(sdk);
            return loaded;
        } catch (ReflectiveOperationException e) {
            throw new OASSDKException("Failed to read loaded spec", e);
        }
    }
}
