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
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@code BooleanValidator} call sites in generated {@code QueryParamValidators}.
 * Guards against reintroducing a duplicate {@code paramName} argument (see commit a2ec82f).
 */
@DisplayName("QueryParamValidators BooleanValidator emission")
class JerseyQueryParamValidatorBooleanTest {

    private static final Pattern DUPLICATE_PARAM_NAME =
            Pattern.compile("new BooleanValidator\\(\"([^\"]+)\", \"\\1\", ");

    private static final String BINDINGS_PACKAGE = "com.egain.bindings.ws.model.xsds.common.v4";
    private static final String BINDINGS_PACKAGE_PATH = BINDINGS_PACKAGE.replace('.', '/');
    private static final Path BINDINGS_ARTIFACT_ROOT =
            Path.of("target", "generated-bindings-query-param-validators", BINDINGS_PACKAGE_PATH);

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("Full generate: BooleanValidator uses parameterName then l10nKey only")
    void booleanValidatorCallSiteHasNoDuplicateParamName() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("openapi4-full");
        String packageName = "com.test.api";
        String packagePath = packageName.replace('.', '/');

        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec("src/test/resources/openapi4.yaml");
            sdk.generateApplication("java", "jersey", packageName, outputDir.toString());
        }

        Path queryParamValidators = outputDir.resolve("src/main/java/" + packagePath + "/QueryParamValidators.java");
        assertTrue(Files.exists(queryParamValidators), "QueryParamValidators.java should exist");

        String content = Files.readString(queryParamValidators, StandardCharsets.UTF_8);
        assertTrue(content.contains("new BooleanValidator(\"strictMode\""),
                "Should emit BooleanValidator for strictMode query parameter");
        assertFalse(hasDuplicateParamName(content),
                "BooleanValidator must not repeat paramName as second string argument");
        assertTrue(content.contains(
                        "new BooleanValidator(\"strictMode\", \"L10N_INVALID_VALUE_FOR_QUERY_PARAM_INVALID_BOOLEAN\""),
                "Second string argument after parameter name must be l10n key");
    }

    @Test
    @DisplayName("modelsOnly bindings package: boolean query params match expected call shape")
    void modelsOnlyBindingsPackageBooleanValidatorLines() throws OASSDKException, IOException {
        Path specPath = Path.of("examples/bundle-openapi 3.yaml").toAbsolutePath();
        assertTrue(Files.isRegularFile(specPath), "Missing bundle spec: " + specPath);

        Path outputDir = tempOutputDir.resolve("bindings-models-only");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName(BINDINGS_PACKAGE)
                .outputDir(outputDir.toString())
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", BINDINGS_PACKAGE, outputDir.toString());
        }

        Path queryParamValidatorsTxt = outputDir.resolve(BINDINGS_PACKAGE_PATH + "/QueryParamValidators.txt");
        assertTrue(Files.exists(queryParamValidatorsTxt), "QueryParamValidators.txt should exist under bindings package");

        String content = Files.readString(queryParamValidatorsTxt, StandardCharsets.UTF_8);
        assertFalse(hasDuplicateParamName(content),
                "Bindings QueryParamValidators must not use duplicate paramName in BooleanValidator");

        assertTrue(content.contains(
                        "v.add(new BooleanValidator(\"languages\", \"L10N_INVALID_VALUE_FOR_QUERY_PARAM_INVALID_BOOLEAN\""),
                "languages boolean query param");
        assertTrue(content.contains(
                        "v.add(new BooleanValidator(\"getLastSavedQuickPickForInteraction\", \"L10N_INVALID_VALUE_FOR_QUERY_PARAM_INVALID_BOOLEAN\""),
                "getLastSavedQuickPickForInteraction boolean query param");

        writeBindingsArtifactsToTarget(outputDir);
    }

    @Test
    @DisplayName("Write bindings validation artifacts to target/ for WS project integration")
    void writeBindingsArtifactsToTargetFromBundle() throws OASSDKException, IOException {
        Path specPath = Path.of("examples/bundle-openapi 3.yaml").toAbsolutePath();
        Path outputDir = tempOutputDir.resolve("bindings-target-copy");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName(BINDINGS_PACKAGE)
                .outputDir(outputDir.toString())
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specPath.toString());
            sdk.generateApplication("java", "jersey", BINDINGS_PACKAGE, outputDir.toString());
        }

        writeBindingsArtifactsToTarget(outputDir);
        assertTrue(Files.exists(BINDINGS_ARTIFACT_ROOT.resolve("QueryParamValidators.txt")),
                "QueryParamValidators.txt should be written under target/");

        verifyBindingsBooleanSmokeTest();
    }

    @Test
    @DisplayName("target/ bindings artifact: boolean endpoints match golden fragments")
    void targetBindingsArtifactBooleanCallSites() throws IOException, OASSDKException {
        if (!Files.exists(BINDINGS_ARTIFACT_ROOT.resolve("QueryParamValidators.txt"))) {
            Path specPath = Path.of("examples/bundle-openapi 3.yaml").toAbsolutePath();
            Path outputDir = tempOutputDir.resolve("bindings-smoke");
            GeneratorConfig config = GeneratorConfig.builder()
                    .modelsOnly(true)
                    .packageName(BINDINGS_PACKAGE)
                    .outputDir(outputDir.toString())
                    .build();
            try (OASSDK sdk = new OASSDK(config, null, null)) {
                sdk.loadSpec(specPath.toString());
                sdk.generateApplication("java", "jersey", BINDINGS_PACKAGE, outputDir.toString());
            }
            writeBindingsArtifactsToTarget(outputDir);
        }
        verifyBindingsBooleanSmokeTest();
    }

    private static void verifyBindingsBooleanSmokeTest() throws IOException {
        Path queryParamValidators = BINDINGS_ARTIFACT_ROOT.resolve("QueryParamValidators.txt");
        assertTrue(Files.exists(queryParamValidators),
                "Run full JerseyQueryParamValidatorBooleanTest suite first to populate " + queryParamValidators);

        String content = Files.readString(queryParamValidators, StandardCharsets.UTF_8);
        assertFalse(hasDuplicateParamName(content),
                "Stale BooleanValidator(param, param, l10n) pattern must not appear in bindings artifact");

        for (String line : readGoldenLines("/parity/boolean_validator_call_sites.fragment")) {
            assertTrue(content.contains(line.trim()),
                    "Bindings artifact should contain boolean validator line: " + line.trim());
        }
    }

    private static List<String> readGoldenLines(String classpathPath) throws IOException {
        try (InputStream in = JerseyQueryParamValidatorBooleanTest.class.getResourceAsStream(classpathPath)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }

    private static void writeBindingsArtifactsToTarget(Path generationOutputDir) throws IOException {
        Path sourceDir = generationOutputDir.resolve(BINDINGS_PACKAGE_PATH);
        Files.createDirectories(BINDINGS_ARTIFACT_ROOT);
        copyIfPresent(sourceDir.resolve("QueryParamValidators.txt"), BINDINGS_ARTIFACT_ROOT.resolve("QueryParamValidators.txt"));
        copyIfPresent(sourceDir.resolve("ValidationMapHelper.txt"), BINDINGS_ARTIFACT_ROOT.resolve("ValidationMapHelper.txt"));
    }

    private static void copyIfPresent(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean hasDuplicateParamName(String generatedContent) {
        Matcher matcher = DUPLICATE_PARAM_NAME.matcher(generatedContent);
        return matcher.find();
    }
}
