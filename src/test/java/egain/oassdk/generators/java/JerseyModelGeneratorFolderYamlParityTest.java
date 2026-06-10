package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates {@code CreateFolder} from the published {@code Folder.yaml} shape (bundled under
 * {@code folder_contentmgr_bundle} with stub {@code models/v4} refs). Asserts the oneOf XOR validation
 * block matches the hand-maintained golden fragment aligned with eGain bindings.
 * <p>
 * The full generated class may still differ from a hand-written {@code CreateFolder} (e.g. OpenAPI uses
 * {@code childFolderCount} while some bindings use {@code subFolderCount}); this test locks the XOR parity only.
 * </p>
 */
@DisplayName("Folder.yaml CreateFolder oneOf XOR parity")
class JerseyModelGeneratorFolderYamlParityTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("Bundled Folder.yaml yields expected isValid* XOR block on CreateFolder")
    void createFolderXorBlockMatchesGoldenFragment() throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/folder_contentmgr_bundle/knowledge/models/contentmgr/v4/Folder.yaml")
                .toAbsolutePath();
        assertTrue(Files.isRegularFile(specPath), "Missing bundled spec: " + specPath);

        Path bundleRoot = Path.of("src/test/resources/folder_contentmgr_bundle").toAbsolutePath();
        assertTrue(Files.isDirectory(bundleRoot), "Missing bundle root: " + bundleRoot);

        Path outputDir = tempOutputDir.resolve("folder-models");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundleRoot.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path createFolderJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            createFolderJava = walk
                    .filter(p -> p.getFileName().toString().equals("CreateFolder.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("CreateFolder.java not found under " + outputDir));
        }

        String generated = Files.readString(createFolderJava, StandardCharsets.UTF_8);
        String expectedFragment = readResource("/parity/create_folder_xor_expected.fragment");
        String extracted = extractXorValidationBlock(generated);
        assertEquals(
                normalizeWs(expectedFragment),
                normalizeWs(extracted),
                "CreateFolder oneOf XOR validation block should match golden fragment (Folder.yaml / createFolder).");
    }

    @Test
    @DisplayName("Bundled Folder.yaml EditFolder.permissions uses List<EditFolderPermissionsEntry>")
    void editFolderPermissionsFieldUsesEditEntryType() throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/folder_contentmgr_bundle/knowledge/models/contentmgr/v4/Folder.yaml")
                .toAbsolutePath();
        Path bundleRoot = Path.of("src/test/resources/folder_contentmgr_bundle").toAbsolutePath();
        Path outputDir = tempOutputDir.resolve("edit-folder-models");

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundleRoot.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path editFolderJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            editFolderJava = walk
                    .filter(p -> p.getFileName().toString().equals("EditFolder.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("EditFolder.java not found under " + outputDir));
        }

        String generated = Files.readString(editFolderJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("List<EditFolderPermissionsEntry>"),
                "EditFolder.permissions should be List<EditFolderPermissionsEntry>");
        assertFalse(generated.contains("List<FolderPermissionsEntry>"),
                "EditFolder must not use List<FolderPermissionsEntry> for permissions");
    }

    @Test
    @DisplayName("Bundled Folder.yaml EditFolder keeps typed lastModified, parent, and department")
    void editFolderOverlayFieldsKeepBaseTypes() throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/folder_contentmgr_bundle/knowledge/models/contentmgr/v4/Folder.yaml")
                .toAbsolutePath();
        Path bundleRoot = Path.of("src/test/resources/folder_contentmgr_bundle").toAbsolutePath();
        Path outputDir = tempOutputDir.resolve("edit-folder-typed-fields");

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundleRoot.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path editFolderJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            editFolderJava = walk
                    .filter(p -> p.getFileName().toString().equals("EditFolder.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("EditFolder.java not found under " + outputDir));
        }

        String generated = Files.readString(editFolderJava, StandardCharsets.UTF_8);
        assertTrue(generated.contains("private DateAndUser lastModified"),
                "EditFolder.lastModified should remain DateAndUser after overlay merge");
        assertTrue(generated.contains("private FolderSummary parent"),
                "EditFolder.parent should remain FolderSummary after readOnly overlay");
        assertTrue(generated.contains("private Department department"),
                "EditFolder.department should remain Department after readOnly overlay");
        assertFalse(generated.contains("private Object lastModified"),
                "EditFolder.lastModified must not degrade to Object");
    }

    @Test
    @DisplayName("Bundled Permission.yaml IdentityPayload.user uses Identity not BasicUser")
    void identityPayloadUserFieldUsesIdentityType() throws OASSDKException, IOException {
        Path specPath = Path.of("src/test/resources/folder_contentmgr_bundle/knowledge/models/contentmgr/v4/Permission.yaml")
                .toAbsolutePath();
        Path bundleRoot = Path.of("src/test/resources/folder_contentmgr_bundle").toAbsolutePath();
        Path outputDir = tempOutputDir.resolve("permission-models");

        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4.content")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundleRoot.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path identityPayloadJava;
        Path identityJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            List<Path> javaFiles = walk.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
            identityPayloadJava = javaFiles.stream()
                    .filter(p -> p.getFileName().toString().equals("IdentityPayload.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("IdentityPayload.java not found under " + outputDir));
            identityJava = javaFiles.stream()
                    .filter(p -> p.getFileName().toString().equals("Identity.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Identity.java not found under " + outputDir));
        }

        String payload = Files.readString(identityPayloadJava, StandardCharsets.UTF_8);
        assertTrue(payload.contains("private Identity user"),
                "IdentityPayload.user should be Identity so overlay readOnly:false on id applies");
        assertFalse(payload.contains("private BasicUser user"),
                "IdentityPayload must not collapse Identity allOf to BasicUser");

        String identity = Files.readString(identityJava, StandardCharsets.UTF_8);
        int idFieldIdx = identity.indexOf("private String id;");
        assertTrue(idFieldIdx > 0, "Identity should declare private String id field");
        String idFieldBlock = identity.substring(Math.max(0, idFieldIdx - 400), idFieldIdx + 50);
        assertFalse(idFieldBlock.contains("JsonProperty.Access.READ_ONLY"),
                "Identity.id overlay readOnly:false must not emit READ_ONLY on id");
        assertTrue(identity.contains("public void setId("), "Identity.id should have a public setter");
    }

    private static String readResource(String classpathPath) throws IOException {
        try (InputStream in = JerseyModelGeneratorFolderYamlParityTest.class.getResourceAsStream(classpathPath)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * From first {@code @XmlTransient} that immediately precedes {@code isValidRequiredMutuallyExclusive}
     * through the closing brace of {@code isValidParent()}.
     */
    static String extractXorValidationBlock(String javaSource) {
        int start = javaSource.indexOf("public boolean isValidRequiredMutuallyExclusive()");
        if (start < 0) {
            throw new AssertionError("isValidRequiredMutuallyExclusive() not found");
        }
        int transientStart = javaSource.lastIndexOf("@XmlTransient", start);
        if (transientStart < 0) {
            throw new AssertionError("@XmlTransient before XOR method not found");
        }
        int lineStart = javaSource.lastIndexOf('\n', transientStart);
        int blockStart = lineStart < 0 ? 0 : lineStart + 1;

        Matcher m = Pattern.compile(
                "public boolean isValidParent\\(\\)\\s*\\{[^}]*\\}",
                Pattern.DOTALL).matcher(javaSource.substring(start));
        if (!m.find()) {
            throw new AssertionError("isValidParent() body not found");
        }
        int blockEnd = start + m.end();
        return javaSource.substring(blockStart, blockEnd);
    }

    static String normalizeWs(String s) {
        String tabExpanded = s.replace("\t", "    ");
        String[] lines = tabExpanded.split("\\R");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.stripTrailing());
        }
        return sb.toString().trim();
    }
}
