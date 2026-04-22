package egain.oassdk.testgenerators.sequence;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.sequence.SequenceTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceChainTestGeneratorTest {

    @Test
    void emitsBundleLayoutForMinimalSpec(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.minimalFolderSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        Path bundle = outputDir.resolve("sequence");
        assertThat(bundle).isDirectory();
        assertThat(bundle.resolve("conftest.py")).isRegularFile();
        assertThat(bundle.resolve("pytest.ini")).isRegularFile();
        assertThat(bundle.resolve("requirements.txt")).isRegularFile();
        assertThat(bundle.resolve("README-sequence.md")).isRegularFile();
        assertThat(bundle.resolve("test_chain_folders.py")).isRegularFile();
    }

    @Test
    void emittedChainsResolvePathTemplatesToResourceId(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.folderSpecWithCrud(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        String pytest = Files.readString(
                outputDir.resolve("sequence/test_chain_folders.py"), StandardCharsets.UTF_8);

        // Path template substituted into an f-string, not left as {folderID}.
        assertThat(pytest).contains("f\"{base_url}/folders/{resource_id}\"");
        assertThat(pytest).doesNotContain("\"{base_url}/folders/{folderID}\"");
        // Error messages still reference the original template (braces escaped).
        assertThat(pytest).contains("/folders/{{folderID}}");
        // Strict per-step assertion.
        assertThat(pytest).contains("assert 200 <= r.status_code < 300");
        // DELETE's own accept-list.
        assertThat(pytest).contains("assert r.status_code in (200, 202, 204)");
    }

    @Test
    void creatorExtractsResourceIdOnlyWhenLaterStepsNeedIt(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.minimalFolderSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        String pytest = Files.readString(
                outputDir.resolve("sequence/test_chain_folders.py"), StandardCharsets.UTF_8);

        // Two chains emitted: [POST] and [POST, GET]. Only the second needs resource_id.
        assertThat(pytest).contains("def test_folders_post(");
        assertThat(pytest).contains("def test_folders_post_get(");
        // resource_id extraction appears exactly once (inside the longer chain).
        int occurrences = pytest.split("resource_id = extract_id\\(r\\)", -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void maxChainLengthOverride_honorsAdditionalProperty(@TempDir Path outputDir) throws Exception {
        TestConfig tc = TestConfig.builder()
                .language("python")
                .framework("pytest")
                .additionalProperties(Map.of("sequence.maxChainLength", "1"))
                .build();
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.folderSpecWithCrud(),
                outputDir.toString(),
                tc);

        String pytest = Files.readString(
                outputDir.resolve("sequence/test_chain_folders.py"), StandardCharsets.UTF_8);

        assertThat(pytest).contains("def test_folders_post(");
        assertThat(pytest).doesNotContain("def test_folders_post_get(");
    }

    @Test
    void pythonPathExpression_escapesBraces() {
        // direct helper check: no path params → plain f-string, params → resource_id.
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/folders"))
                .isEqualTo("f\"{base_url}/folders\"");
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/folders/{folderID}"))
                .isEqualTo("f\"{base_url}/folders/{resource_id}\"");
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/a/{x}/b/{y}"))
                .isEqualTo("f\"{base_url}/a/{resource_id}/b/{resource_id}\"");
    }

    @Test
    void jsonToPythonLiteral_convertsBooleansAndNull() {
        assertThat(SequenceChainTestGenerator.jsonToPythonLiteral(
                "{\"a\": true, \"b\": false, \"c\": null}"))
                .isEqualTo("{\"a\": True, \"b\": False, \"c\": None}");
    }

    @Test
    void sanitizeModuleName_normalizesIdentifiers() {
        assertThat(SequenceChainTestGenerator.sanitizeModuleName("folders")).isEqualTo("folders");
        assertThat(SequenceChainTestGenerator.sanitizeModuleName("UserProfiles")).isEqualTo("user_profiles");
        assertThat(SequenceChainTestGenerator.sanitizeModuleName("my-widget")).isEqualTo("my_widget");
        assertThat(SequenceChainTestGenerator.sanitizeModuleName(null)).isEqualTo("resource");
    }
}
