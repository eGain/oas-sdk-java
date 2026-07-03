package egain.oassdk.testgenerators.mock;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockDataGenerator.
 */
public class MockDataGeneratorTest {

    @Test
    public void testGetName() {
        MockDataGenerator generator = new MockDataGenerator();
        assertNotNull(generator.getName());
    }

    @Test
    public void testGenerateWithMinimalSpecCreatesOutput(@TempDir Path tempDir) throws GenerationException {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of(),
                "components", Map.of("schemas", Map.of("User", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string")))))
        );
        generator.generate(spec, tempDir.toString(), new TestConfig(), "java");
        assertTrue(Files.exists(tempDir));
    }

    @Test
    public void operationRequestBody_emitsParentIdPlaceholder(@TempDir Path tempDir) throws Exception {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> parentSchema = Map.of(
                "type", "object",
                "properties", Map.of("id", Map.of("type", "string")),
                "required", java.util.List.of("id"));
        Map<String, Object> bodySchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "parent", parentSchema),
                "required", java.util.List.of("name", "parent"));
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "Folders", "version", "1.0"),
                "paths", Map.of("/folders", Map.of(
                        "post", Map.of(
                                "operationId", "createFolder",
                                "requestBody", Map.of(
                                        "content", Map.of(
                                                "application/json", Map.of("schema", bodySchema)))))),
                "components", Map.of("schemas", Map.of()));
        generator.generate(spec, tempDir.toString(), new TestConfig(), "java");

        Path requestFile = tempDir.resolve("operations").resolve("createFolder_request.json");
        assertTrue(Files.isRegularFile(requestFile), "operation request JSON should be generated");
        String content = Files.readString(requestFile);
        assertTrue(content.contains("${test.parent.folder.id}"),
                "parent.id should use test-env placeholder, got: " + content);
    }
}
