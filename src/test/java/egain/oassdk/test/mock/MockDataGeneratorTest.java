package egain.oassdk.test.mock;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for egain.oassdk.test.mock.MockDataGenerator.
 */
class MockDataGeneratorTest {

    @Test
    void generateMockData_withMinimalSpec_createsOutputDirectoryAndFiles(@TempDir Path tempDir) throws GenerationException {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of(),
                "components", Map.of()
        );

        generator.generateMockData(spec, tempDir.toString());

        assertThat(tempDir).exists();
        assertThat(tempDir.resolve("MockDataFactory.java")).exists();
        assertThat(tempDir.resolve("positive_test_data.json")).exists();
        assertThat(tempDir.resolve("negative_test_data.json")).exists();
        assertThat(tempDir.resolve("edge_case_test_data.json")).exists();
    }

    @Test
    void generateMockData_withSchemas_createsSchemaMockFiles(@TempDir Path tempDir) throws Exception {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of(),
                "components", Map.of("schemas", Map.of(
                        "User", Map.of("type", "object", "properties", Map.of(
                                "id", Map.of("type", "string"),
                                "name", Map.of("type", "string")
                        ))
                ))
        );

        generator.generateMockData(spec, tempDir.toString());

        Path userMock = tempDir.resolve("user_mock.json");
        assertThat(userMock).exists();
        assertThat(Files.readString(userMock)).contains("\"id\"").contains("\"name\"");
    }

    @Test
    void generateMockData_withPaths_createsRequestAndResponseMockFiles(@TempDir Path tempDir) throws GenerationException {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of(
                        "/users", Map.of(
                                "get", Map.of("operationId", "getUsers", "responses", Map.of("200", Map.of("description", "OK"))),
                                "post", Map.of("operationId", "createUser", "requestBody", Map.of("content", Map.of("application/json", Map.of("schema", Map.of("type", "object")))),
                                        "responses", Map.of("201", Map.of("description", "Created"))))
                ),
                "components", Map.of()
        );

        generator.generateMockData(spec, tempDir.toString());

        assertThat(tempDir.resolve("getUsers_request_mock.json")).exists();
        assertThat(tempDir.resolve("getUsers_response_mock.json")).exists();
        assertThat(tempDir.resolve("createUser_request_mock.json")).exists();
        assertThat(tempDir.resolve("createUser_response_mock.json")).exists();
    }

    @Test
    void generateMockData_whenOutputDirIsFile_throwsGenerationException(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "x");
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of("info", Map.of(), "paths", Map.of(), "components", Map.of());

        GenerationException ex = assertThrows(GenerationException.class,
                () -> generator.generateMockData(spec, filePath.resolve("sub").toString()));

        assertThat(ex.getMessage()).contains("Failed to generate mock data");
    }
}
