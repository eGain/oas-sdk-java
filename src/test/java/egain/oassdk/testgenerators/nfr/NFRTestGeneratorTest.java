package egain.oassdk.testgenerators.nfr;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NFRTestGenerator.
 */
public class NFRTestGeneratorTest {

    @Test
    public void testGetName() {
        NFRTestGenerator generator = new NFRTestGenerator();
        assertNotNull(generator.getName());
        assertFalse(generator.getName().isEmpty());
    }

    @Test
    public void testGenerateCreatesOutput(@TempDir Path tempDir) throws GenerationException {
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of()
        );
        generator.generate(spec, tempDir.toString(), new TestConfig(), "pytest");
        assertTrue(Files.exists(tempDir));
    }

    @Test
    public void testGeneratedNFRTestUsesRestAssured(@TempDir Path tempDir) throws Exception {
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "servers", List.of(Map.of("url", "http://localhost:9999")),
                "paths", Map.of("/ping", Map.of("get", Map.of("summary", "Health ping")))
        );
        generator.generate(spec, tempDir.toString(), new TestConfig(), "junit");

        Path testFile = tempDir.resolve("nfr/src/test/java/com/example/api/NFRTest.java");
        assertTrue(Files.exists(testFile), "Expected NFRTest.java at " + testFile);
        String content = Files.readString(testFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("static io.restassured.RestAssured.given"),
                "Generated NFR tests should static-import RestAssured given()");
        assertTrue(content.contains("TestClient.configureRestAssured"),
                "Generated NFR tests should configure RestAssured via TestClient");
        assertTrue(content.contains("TestAuth.rawToken"),
                "Generated NFR tests should resolve auth via TestAuth");
        assertFalse(content.contains("java.net.http.HttpClient"),
                "Generated NFR tests should not use java.net.http.HttpClient");
    }
}
