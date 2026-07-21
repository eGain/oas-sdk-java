package egain.oassdk.testgenerators.schemathesis;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.TestGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemathesisTestGeneratorTest {

    @Test
    void generatesBundleUnderSchemathesisSubdir(@TempDir Path tempDir) throws GenerationException, IOException {
        Map<String, Object> spec = minimalSpec();
        TestGenerator gen = new SchemathesisTestGenerator();
        gen.generate(spec, tempDir.toString(), TestConfig.builder().build(), null);

        Path bundle = tempDir.resolve("schemathesis");
        assertTrue(Files.isDirectory(bundle));
        assertTrue(Files.isRegularFile(bundle.resolve("openapi.yaml")));
        assertTrue(Files.isRegularFile(bundle.resolve("schemathesis.properties")));
        assertTrue(Files.isRegularFile(bundle.resolve("run-schemathesis.sh")));
        assertTrue(Files.isRegularFile(bundle.resolve("README-schemathesis.md")));
        assertTrue(Files.isRegularFile(bundle.resolve("schemathesis.toml")));

        String toml = Files.readString(bundle.resolve("schemathesis.toml"));
        assertTrue(toml.contains("[phases.coverage]"));
        assertTrue(toml.contains("unexpected-methods = [\"GET\", \"PUT\", \"POST\", \"DELETE\", \"OPTIONS\", \"PATCH\"]"),
                "Coverage phase must list unexpected-methods (CBD-8365 regression)");
        assertTrue(toml.contains("[checks.missing_required_header]"));
        assertTrue(toml.contains("expected-statuses = [400]"));

        String props = Files.readString(bundle.resolve("schemathesis.properties"));
        assertTrue(props.contains("%HUB%"));
        assertTrue(props.contains("JUNIT_REPORT="));
        assertTrue(props.contains("%TOKEN%"));
        assertTrue(props.contains("TLS_VERIFY=false"));
        assertTrue(props.contains("EXTRA_ARGS="));
        assertTrue(props.contains("--include-operation-id createFolder"));
        assertTrue(props.contains("CHECKS=all") || props.lines().anyMatch(l -> l.startsWith("CHECKS=") && l.contains("all")),
                "Default CHECKS must be all for full contract coverage");

        String script = Files.readString(bundle.resolve("run-schemathesis.sh"));
        assertTrue(script.contains("st run"));
        assertTrue(script.contains("load_test_env"));
        assertTrue(script.contains("deleteFolder"));
        assertTrue(script.contains("--phases="));
        assertTrue(script.contains("--checks"));
        assertTrue(script.contains("--tls-verify=false"));
    }

    @Test
    void defaultTomlKeepsUnexpectedMethodsAndDoesNotRequire405Or414(@TempDir Path tempDir)
            throws GenerationException, IOException {
        new SchemathesisTestGenerator().generate(minimalSpec(), tempDir.toString(), TestConfig.builder().build(), null);
        String toml = Files.readString(tempDir.resolve("schemathesis").resolve("schemathesis.toml"));
        assertTrue(toml.contains("unexpected-methods"));
        assertFalse(toml.contains("405"), "Do not bake 405 into default schemathesis.toml (CBD-8621 Won't Fix)");
        assertFalse(toml.contains("414"), "Do not bake 414 into default schemathesis.toml (CBD-8622/8624 Won't Fix)");
    }

    @Test
    void tlsVerifyTrueOmitsInsecureFlag(@TempDir Path tempDir) throws GenerationException, IOException {
        Map<String, Object> spec = minimalSpec();
        Map<String, Object> extra = new HashMap<>();
        extra.put("schemathesis.tlsVerify", "true");
        TestConfig config = TestConfig.builder().additionalProperties(extra).build();
        new SchemathesisTestGenerator().generate(spec, tempDir.toString(), config, null);

        String props = Files.readString(tempDir.resolve("schemathesis").resolve("schemathesis.properties"));
        assertTrue(props.contains("TLS_VERIFY=true"));
    }

    @Test
    void respectsBundleDirDot(@TempDir Path tempDir) throws GenerationException, IOException {
        Map<String, Object> spec = minimalSpec();
        Map<String, Object> extra = new HashMap<>();
        extra.put("schemathesis.bundleDir", ".");
        TestConfig config = TestConfig.builder().additionalProperties(extra).build();
        new SchemathesisTestGenerator().generate(spec, tempDir.toString(), config, null);

        assertTrue(Files.isRegularFile(tempDir.resolve("openapi.yaml")));
        assertEquals(tempDir, SchemathesisTestGenerator.resolveBundleDirectory(tempDir.toString(), config));
    }

    private static Map<String, Object> minimalSpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "T", "version", "1.0.0"));
        spec.put("paths", Map.of());
        return spec;
    }
}
