package egain.oassdk.testgenerators.sequence;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.sequence.ApiCallInfo;
import egain.oassdk.core.sequence.SequenceTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void emittedChainsBindPathParamsToPerParamIdVariables(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.folderSpecWithCrud(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        String pytest = Files.readString(
                outputDir.resolve("sequence/test_chain_folders.py"), StandardCharsets.UTF_8);

        // Path template uses the per-param snake_case variable, not the generic resource_id.
        assertThat(pytest).contains("f\"{base_url}/folders/{folder_id}\"");
        assertThat(pytest).doesNotContain("{resource_id}");
        assertThat(pytest).doesNotContain("\"{base_url}/folders/{folderID}\"");
        // Error messages still reference the original template (braces escaped).
        assertThat(pytest).contains("/folders/{{folderID}}");
        // Strict per-step assertion.
        assertThat(pytest).contains("assert 200 <= r.status_code < 300");
        // DELETE's own accept-list.
        assertThat(pytest).contains("assert r.status_code in (200, 202, 204)");
    }

    @Test
    void creatorExtractsIdWithHintOnlyWhenLaterStepsNeedIt(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.minimalFolderSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        String pytest = Files.readString(
                outputDir.resolve("sequence/test_chain_folders.py"), StandardCharsets.UTF_8);

        assertThat(pytest).contains("def test_folders_post(");
        assertThat(pytest).contains("def test_folders_post_get(");
        int occurrences = pytest.split("folder_id = extract_id\\(r, hint=\"folderID\"\\)", -1).length - 1;
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
    void pythonPathExpression_usesPerParamIdVariable() {
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/folders"))
                .isEqualTo("f\"{base_url}/folders\"");
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/folders/{folderID}"))
                .isEqualTo("f\"{base_url}/folders/{folder_id}\"");
        assertThat(SequenceChainTestGenerator.pythonPathExpression("/orders/{orderId}/items/{itemId}"))
                .isEqualTo("f\"{base_url}/orders/{order_id}/items/{item_id}\"");
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

    @Test
    void newlyBoundByPost_identifiesOnlyProducedParams() {
        ApiCallInfo ordersPost = new ApiCallInfo("POST", "/orders", "createOrder",
                "orders", false, true, List.of(), Map.of(), Map.of());
        ApiCallInfo itemsPost = new ApiCallInfo("POST", "/orders/{orderId}/items", "addItem",
                "items", true, true, List.of("orderId"), Map.of(), Map.of());
        ApiCallInfo itemGet = new ApiCallInfo("GET", "/orders/{orderId}/items/{itemId}", "getItem",
                "items", true, false, List.of("orderId", "itemId"), Map.of(), Map.of());
        List<ApiCallInfo> chain = List.of(ordersPost, itemsPost, itemGet);

        assertThat(SequenceChainTestGenerator.newlyBoundByPost(chain, 0))
                .containsExactly("orderId");
        assertThat(SequenceChainTestGenerator.newlyBoundByPost(chain, 1))
                .containsExactly("itemId");
    }

    @Test
    void emitsOneFilePerSeedResource_withSubResources(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.orderWithItemsSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        Path bundle = outputDir.resolve("sequence");
        assertThat(bundle.resolve("test_chain_orders.py")).isRegularFile();
        assertThat(bundle.resolve("test_chain_items.py")).isRegularFile();

        String items = Files.readString(bundle.resolve("test_chain_items.py"), StandardCharsets.UTF_8);

        // The items family is anchored on /orders/{orderId}/items POST, so the chain
        // is [POST /orders, POST /orders/{orderId}/items, ...] — the prefix /orders POST
        // extracts order_id, the items POST extracts item_id, and subsequent steps use both.
        assertThat(items).contains("order_id = extract_id(r, hint=\"orderId\")");
        assertThat(items).contains("item_id = extract_id(r, hint=\"itemId\")");
        assertThat(items).contains("f\"{base_url}/orders/{order_id}/items/{item_id}\"");
    }

    @Test
    void alternativeCreators_emitEachInItsOwnFile(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.alternativeCreatorsSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        Path bundle = outputDir.resolve("sequence");
        // Both POSTs land in their own file keyed on their seed's resourceName.
        assertThat(bundle.resolve("test_chain_users.py")).isRegularFile();
        assertThat(bundle.resolve("test_chain_bulk.py")).isRegularFile();

        String users = Files.readString(bundle.resolve("test_chain_users.py"), StandardCharsets.UTF_8);
        String bulk = Files.readString(bundle.resolve("test_chain_bulk.py"), StandardCharsets.UTF_8);

        assertThat(users).contains("def test_users_post(");
        assertThat(bulk).contains("def test_bulk_post(");
    }

    @Test
    void unresolvedParam_defaultPolicy_omitsFamily(@TempDir Path outputDir) throws Exception {
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.unresolvedParamSpec(),
                outputDir.toString(),
                TestConfig.builder().language("python").framework("pytest").build());

        Path bundle = outputDir.resolve("sequence");
        // Default policy is SKIP — no test_chain_*.py emitted for an orphan op.
        try (var stream = Files.list(bundle)) {
            boolean anyChainFile = stream.anyMatch(p -> p.getFileName().toString().startsWith("test_chain_"));
            assertThat(anyChainFile).isFalse();
        }
    }

    @Test
    void unresolvedParam_emitWithMarkerPolicy_rendersPytestSkip(@TempDir Path outputDir) throws Exception {
        TestConfig tc = TestConfig.builder()
                .language("python")
                .framework("pytest")
                .additionalProperties(Map.of("sequence.unresolvedParamPolicy", "EMIT_WITH_MARKER"))
                .build();
        new SequenceChainTestGenerator().generate(
                SequenceTestFixtures.unresolvedParamSpec(),
                outputDir.toString(),
                tc);

        Path bundle = outputDir.resolve("sequence");
        assertThat(bundle.resolve("test_chain_children.py")).isRegularFile();
        String content = Files.readString(bundle.resolve("test_chain_children.py"), StandardCharsets.UTF_8);
        assertThat(content).contains("pytest.skip(");
    }
}
