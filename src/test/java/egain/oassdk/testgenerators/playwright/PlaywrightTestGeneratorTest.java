package egain.oassdk.testgenerators.playwright;

import egain.oassdk.config.TestConfig;
import egain.oassdk.testgenerators.common.TestProfileSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlaywrightTestGenerator} and playwright flag filtering.
 */
public class PlaywrightTestGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGenerate_CreatesScaffoldAndSpecs() throws Exception {
        Map<String, Object> spec = minimalSpec();
        PlaywrightTestGenerator generator = new PlaywrightTestGenerator();
        TestConfig config = TestConfig.builder().build();

        generator.generate(spec, tempDir.toString(), config, null);

        Path root = tempDir.resolve("playwright");
        assertTrue(Files.isDirectory(root));
        assertTrue(Files.isRegularFile(root.resolve("package.json")));
        assertTrue(Files.isRegularFile(root.resolve("playwright.config.ts")));
        assertTrue(Files.isRegularFile(root.resolve("tsconfig.json")));
        assertTrue(Files.isRegularFile(root.resolve("apis").resolve("base.api.ts")));
        assertTrue(Files.isRegularFile(root.resolve("utilities").resolve("helpers.ts")));
        assertTrue(Files.isRegularFile(root.resolve("apis").resolve("items.api.ts")));

        Path generated = root.resolve("tests").resolve("generated");
        assertTrue(Files.isDirectory(generated));
        try (var stream = Files.walk(generated)) {
            List<Path> specs = stream.filter(p -> p.toString().endsWith(".spec.ts")).toList();
            assertFalse(specs.isEmpty(), "expected at least one .spec.ts");
            String content = Files.readString(specs.get(0));
            assertTrue(content.contains("@playwright/test"));
            assertTrue(content.contains("@generated"));
        }

        try (var stream = Files.walk(root.resolve("data").resolve("generated"))) {
            List<Path> jsonFiles = stream.filter(p -> p.toString().endsWith(".json")).toList();
            assertFalse(jsonFiles.isEmpty());
            String json = Files.readString(jsonFiles.stream()
                    .filter(p -> p.getFileName().toString().contains("-N-"))
                    .findFirst()
                    .orElse(jsonFiles.get(0)));
            assertTrue(json.contains("expectedRC") || json.contains("testCases"));
        }
    }

    @Test
    public void testGenerate_PositiveAndNegativeContentMarkers() throws Exception {
        Map<String, Object> spec = minimalSpec();
        new PlaywrightTestGenerator().generate(spec, tempDir.toString(), TestConfig.builder().build(), null);

        Path pos = findFile(tempDir.resolve("playwright"), "tc01-P-", ".spec.ts");
        Path neg = findFile(tempDir.resolve("playwright"), "tc02-N-", ".spec.ts");
        assertNotNull(pos);
        assertNotNull(neg);

        String posContent = Files.readString(pos);
        assertTrue(posContent.contains("test.describe.serial"));
        assertTrue(posContent.contains("expect.soft"));

        String negContent = Files.readString(neg);
        assertTrue(negContent.contains("expectedStatuses") || negContent.contains("expectedRC"));
        assertTrue(negContent.contains("getAnonymousAPIContext"));
    }

    @Test
    public void testGetTestType() {
        assertEquals("playwright", new PlaywrightTestGenerator().getTestType());
    }

    @Test
    public void testApplyPlaywrightFlag_DefaultAppends() {
        List<String> types = TestProfileSupport.applyPlaywrightFlag(
                List.of("unit", "integration"), TestConfig.builder().build());
        assertTrue(types.stream().anyMatch(t -> "playwright".equalsIgnoreCase(t)));
        assertEquals(3, types.size());
    }

    @Test
    public void testApplyPlaywrightFlag_DisabledStrips() {
        List<String> types = TestProfileSupport.applyPlaywrightFlag(
                List.of("unit", "playwright", "integration"),
                TestConfig.builder().playwrightTests(false).build());
        assertTrue(types.stream().noneMatch(t -> "playwright".equalsIgnoreCase(t)));
        assertEquals(2, types.size());
    }

    @Test
    public void testApplyPlaywrightFlag_DoesNotDuplicate() {
        List<String> types = TestProfileSupport.applyPlaywrightFlag(
                List.of("playwright", "unit"), TestConfig.builder().playwrightTests(true).build());
        assertEquals(1, types.stream().filter(t -> "playwright".equalsIgnoreCase(t)).count());
    }

    @Test
    public void testSmokeProfileAllowsPlaywright() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("testProfile", "smoke");
        TestConfig config = TestConfig.builder().additionalProperties(extra).build();
        List<String> filtered = TestProfileSupport.filterTestTypes(
                List.of("unit", "playwright", "integration"), config);
        assertTrue(filtered.contains("playwright"));
        assertTrue(filtered.contains("integration"));
        assertFalse(filtered.contains("unit"));
    }

    private static Path findFile(Path root, String namePrefix, String nameSuffix) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> Files.isRegularFile(p)
                            && p.getFileName().toString().startsWith(namePrefix)
                            && p.getFileName().toString().endsWith(nameSuffix))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Map<String, Object> minimalSpec() {
        Map<String, Object> propName = new LinkedHashMap<>();
        propName.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", propName);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("name"));
        schema.put("properties", properties);

        Map<String, Object> media = new LinkedHashMap<>();
        media.put("schema", schema);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", media);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", content);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("201", Map.of("description", "Created"));
        responses.put("400", Map.of("description", "Bad Request"));
        responses.put("401", Map.of("description", "Unauthorized"));

        Map<String, Object> post = new LinkedHashMap<>();
        post.put("operationId", "createItem");
        post.put("summary", "Create item");
        post.put("tags", List.of("Items"));
        post.put("security", List.of(Map.of("bearerAuth", List.of())));
        post.put("requestBody", requestBody);
        post.put("responses", responses);

        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getItem");
        get.put("summary", "Get item");
        get.put("tags", List.of("Items"));
        get.put("parameters", List.of(Map.of(
                "name", "id",
                "in", "path",
                "required", true,
                "schema", Map.of("type", "string")
        )));
        get.put("responses", Map.of("200", Map.of("description", "OK"), "404", Map.of("description", "Not found")));

        Map<String, Object> pathItem = new LinkedHashMap<>();
        pathItem.put("post", post);
        Map<String, Object> pathItemGet = new LinkedHashMap<>();
        pathItemGet.put("get", get);

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/items", pathItem);
        paths.put("/items/{id}", pathItemGet);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Demo API");
        info.put("version", "1.0.0");

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", info);
        spec.put("paths", paths);
        spec.put("servers", List.of(Map.of("url", "http://localhost:8080")));
        return spec;
    }
}
