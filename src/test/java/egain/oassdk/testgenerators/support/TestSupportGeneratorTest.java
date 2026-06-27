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
        Map<String, Object> bodySchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "description", Map.of("type", "string")),
                "required", List.of("name"));
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "Vendor Neutral API"),
                "servers", List.of(Map.of("url", "https://${API_DOMAIN}/v4")),
                "paths", Map.of(
                        "/folders", Map.of(
                                "post", Map.of(
                                        "operationId", "createFolder",
                                        "requestBody", Map.of("content", Map.of(
                                                "application/json", Map.of("schema", bodySchema))))))
        );
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("test.baseUrl", "https://host.example/v4");
        config.setAdditionalProperties(props);

        new TestSupportGenerator().generate(spec, tempDir.toString(), config,
                List.of("contract", "integration"));

        assertThat(Files.exists(tempDir.resolve("test-env.properties"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("test-env.properties")))
                .contains("auth.provider=static")
                .contains("auth.login.base")
                .contains("auth.chain.1.url")
                .contains("test.include.operations")
                .contains("test.flows.dir")
                .contains("schemathesis.include.operations");
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/TestEnv.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/RequestBodyEnv.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/AuthProvider.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/StaticTokenAuth.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/CurlLoginAuth.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/HttpChainAuth.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/AuthChainExecutor.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/AuthTokenCli.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/java/com/example/api/support/RequestBodyFactory.java"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("test-support/src/test/resources/bodies/createFolder.json"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("test-env.properties")))
                .contains("test.topic.parent.folder.id");
        assertThat(Files.exists(tempDir.resolve("run-all.sh"))).isTrue();
        String pom = Files.readString(tempDir.resolve("pom.xml"));
        assertThat(pom).contains("<module>contract</module>");
        assertThat(pom).contains("<module>integration</module>");
        assertThat(pom).doesNotContain("sequence-java");
    }
}
