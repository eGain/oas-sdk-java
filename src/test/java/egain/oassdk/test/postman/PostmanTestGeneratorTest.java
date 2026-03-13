package egain.oassdk.test.postman;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for egain.oassdk.test.postman.PostmanTestGenerator.
 */
class PostmanTestGeneratorTest {

    @Test
    void generateTestScripts_withMinimalSpec_createsCollectionAndEnvironmentAndScripts(@TempDir Path tempDir) throws GenerationException {
        PostmanTestGenerator generator = new PostmanTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "TestAPI", "version", "1.0", "description", "Test API"),
                "paths", Map.of()
        );

        generator.generateTestScripts(spec, tempDir.toString());

        assertThat(tempDir).exists();
        assertThat(tempDir.resolve("TestAPI-API.postman_collection.json")).exists();
        assertThat(tempDir.resolve("TestAPI-Environment.postman_environment.json")).exists();
        assertThat(tempDir.resolve("run-newman-tests.sh")).exists();
        assertThat(tempDir.resolve("github-actions.yml")).exists();
    }

    @Test
    void generate_interfaceMethod_delegatesToGenerateTestScripts(@TempDir Path tempDir) throws GenerationException {
        PostmanTestGenerator generator = new PostmanTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "MyAPI", "version", "1.0"),
                "paths", Map.of()
        );
        TestConfig config = new TestConfig();

        generator.generate(spec, tempDir.toString(), config, "postman");

        assertThat(tempDir.resolve("MyAPI-API.postman_collection.json")).exists();
        assertThat(generator.getConfig()).isSameAs(config);
    }

    @Test
    void generateTestScripts_withPaths_createsIndividualTestScripts(@TempDir Path tempDir) throws GenerationException {
        PostmanTestGenerator generator = new PostmanTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "TestAPI", "version", "1.0"),
                "paths", Map.of(
                        "/users", Map.of(
                                "get", Map.of("operationId", "listUsers", "responses", Map.of("200", Map.of("description", "OK"))),
                                "post", Map.of("operationId", "createUser", "responses", Map.of("201", Map.of("description", "Created")))
                        )
                )
        );

        generator.generateTestScripts(spec, tempDir.toString());

        assertThat(tempDir.resolve("listUsers_test.js")).exists();
        assertThat(tempDir.resolve("createUser_test.js")).exists();
    }

    @Test
    void generateTestScripts_whenOutputDirIsFile_throwsGenerationException(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "x");
        PostmanTestGenerator generator = new PostmanTestGenerator();
        Map<String, Object> spec = Map.of("info", Map.of("title", "API", "version", "1.0"), "paths", Map.of());

        GenerationException ex = assertThrows(GenerationException.class,
                () -> generator.generateTestScripts(spec, filePath.resolve("sub").toString()));

        assertThat(ex.getMessage()).contains("Failed to generate Postman test scripts");
    }

    @Test
    void getName_returnsPostmanTestGenerator() {
        PostmanTestGenerator generator = new PostmanTestGenerator();
        assertThat(generator.getName()).isEqualTo("Postman Test Generator");
    }

    @Test
    void setConfig_andGetConfig_roundTrip() {
        PostmanTestGenerator generator = new PostmanTestGenerator();
        TestConfig config = new TestConfig();
        generator.setConfig(config);
        assertThat(generator.getConfig()).isSameAs(config);
    }
}
