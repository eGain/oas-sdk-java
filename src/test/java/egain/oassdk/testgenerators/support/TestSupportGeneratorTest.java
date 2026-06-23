package egain.oassdk.testgenerators.support;

import egain.oassdk.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSupportGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generate_emitsTestEnvAndSupportClasses() throws Exception {
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "eGain API", "x-vendor", "egain"),
                "servers", List.of(Map.of("url", "https://${API_DOMAIN}/v4"))
        );
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("test.baseUrl", "https://host.example/v4");
        config.setAdditionalProperties(props);

        new TestSupportGenerator().generate(spec, tempDir.toString(), config,
                List.of("unit", "integration"));

        assertThat(Files.exists(tempDir.resolve("test-env.properties"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("test-env.properties")))
                .contains("auth.login.base")
                .contains("schemathesis.include.operations");
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/TestEnv.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/RequestBodyEnv.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/EgainAuth.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("run-all.sh"))).isTrue();
        String pom = Files.readString(tempDir.resolve("pom.xml"));
        assertThat(pom).contains("<module>unit</module>");
        assertThat(pom).contains("<module>integration</module>");
        assertThat(pom).doesNotContain("sequence-java");
    }
}
