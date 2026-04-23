package egain.oassdk.core.sequence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainEnumeratorTest {

    private final ApiCallExtractor extractor = new ApiCallExtractor();

    @Test
    void minimalSpec_maxLen1_emitsOnlyCreatorChain() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.minimalFolderSpec());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(1).build());

        List<EnumeratedChain> chains = e.enumerate(calls);

        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).steps()).singleElement().satisfies(c -> {
            assertThat(c.method()).isEqualTo("POST");
            assertThat(c.path()).isEqualTo("/folders");
        });
    }

    @Test
    void minimalSpec_maxLen2_addsPostGet() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.minimalFolderSpec());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(2).build());

        List<EnumeratedChain> chains = e.enumerate(calls);

        assertThat(chains).hasSize(2);
        assertThat(methodsOf(chains.get(1))).containsExactly("POST", "GET");
    }

    @Test
    void crudSpec_deleteLastOnly_rejectsDeleteInMiddle() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.folderSpecWithCrud());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(4).deleteLastOnly(true).build());

        List<EnumeratedChain> chains = e.enumerate(calls);

        for (EnumeratedChain chain : chains) {
            List<ApiCallInfo> steps = chain.steps();
            for (int i = 0; i < steps.size() - 1; i++) {
                assertThat(steps.get(i).method())
                        .as("chain %s has DELETE before end at index %d", methodsOf(chain), i)
                        .isNotEqualTo("DELETE");
            }
        }
    }

    @Test
    void crudSpec_chainSizes_matchExpectedPermutations() {
        // One top-level POST seed with 4 consumers (GET, PUT, PATCH, DELETE on
        // /folders/{folderID}); deleteLastOnly = true, allowRepeats = false.
        // Length 1 — [POST]                                                   1
        // Length 2 — P(4,1) = 4; DELETE at end is allowed                     4
        // Length 3 — P(4,2) = 12; reject tails starting with DELETE (3)       9
        // Length 4 — P(4,3) = 24; valid = P(3,3)=6 + P(3,2)=6                 12
        //                                                                    ----
        //                                                                     26
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.folderSpecWithCrud());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(4).build());

        assertThat(e.enumerate(calls)).hasSize(26);
    }

    @Test
    void resourceWithNoCreator_isSkipped() {
        Map<String, Object> spec = Map.of("paths", Map.of(
                "/lookups/{id}", Map.of("get", Map.of("operationId", "getLookup"))));
        List<ApiCallInfo> calls = extractor.extract(spec);

        List<EnumeratedChain> chains = new ChainEnumerator().enumerate(calls);

        assertThat(chains).isEmpty();
    }

    @Test
    void permutations_noRepeats_countsAndDistinctness() {
        List<List<String>> perms = ChainEnumerator.permutations(List.of("a", "b", "c"), 2, false);
        assertThat(perms).hasSize(6);
        assertThat(perms).allMatch(p -> p.size() == 2);
        assertThat(perms).doesNotHaveDuplicates();
        assertThat(perms).allMatch(p -> !p.get(0).equals(p.get(1)));
    }

    @Test
    void permutations_withRepeats_allowsDuplicates() {
        List<List<String>> perms = ChainEnumerator.permutations(List.of("a", "b"), 2, true);
        assertThat(perms).hasSize(4).contains(List.of("a", "a"), List.of("b", "b"));
    }

    @Test
    void alternativeCreators_eachSeedsOwnFamily() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.alternativeCreatorsSpec());
        List<EnumeratedChain> chains = new ChainEnumerator().enumerate(calls);

        // Two top-level POSTs with no consumers — two length-1 chains, each
        // keyed on its own seed (resourceName).
        assertThat(chains).hasSize(2);
        assertThat(chains).extracting(c -> c.seedPost().path())
                .containsExactlyInAnyOrder("/users", "/users/bulk");
        for (EnumeratedChain c : chains) {
            assertThat(c.steps()).hasSize(1);
            assertThat(c.steps().get(0).method()).isEqualTo("POST");
        }
    }

    @Test
    void subResourcePost_prependsProducerPost() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        List<EnumeratedChain> chains = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(2).build()).enumerate(calls);

        EnumeratedChain itemsSeed = chains.stream()
                .filter(c -> c.seedPost().path().equals("/orders/{orderId}/items"))
                .findFirst().orElseThrow();

        assertThat(itemsSeed.steps())
                .extracting(ApiCallInfo::path)
                .containsExactly("/orders", "/orders/{orderId}/items");
        assertThat(itemsSeed.unresolved()).isFalse();
    }

    @Test
    void subResourcePost_tailIncludesDescendantConsumers() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        List<EnumeratedChain> chains = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(4).build()).enumerate(calls);

        // /orders/{orderId}/items POST family with tail ending in DELETE on the item.
        boolean hasItemsCreateGetDelete = chains.stream().anyMatch(c ->
                c.seedPost().path().equals("/orders/{orderId}/items")
                        && c.steps().size() == 4
                        && c.steps().get(0).path().equals("/orders")
                        && c.steps().get(1).path().equals("/orders/{orderId}/items")
                        && c.steps().get(2).path().equals("/orders/{orderId}/items/{itemId}")
                        && c.steps().get(2).method().equals("GET")
                        && c.steps().get(3).path().equals("/orders/{orderId}/items/{itemId}")
                        && c.steps().get(3).method().equals("DELETE"));
        assertThat(hasItemsCreateGetDelete).isTrue();
    }

    @Test
    void subResourceFamily_doesNotPullInAncestorConsumers() {
        // /orders/{orderId}/items POST's family should not contain /orders/{orderId} GET
        // (that belongs to the /orders POST family).
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        List<EnumeratedChain> chains = new ChainEnumerator().enumerate(calls);

        for (EnumeratedChain c : chains) {
            if (!c.seedPost().path().equals("/orders/{orderId}/items")) {
                continue;
            }
            assertThat(c.steps())
                    .noneMatch(s -> s.path().equals("/orders/{orderId}") && s.method().equals("GET"));
        }
    }

    @Test
    void twoLevelNested_prefixChainsRecursively() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.twoLevelNestedSpec());
        List<EnumeratedChain> chains = new ChainEnumerator().enumerate(calls);

        EnumeratedChain cFamily = chains.stream()
                .filter(c -> c.seedPost().path().equals("/a/{aId}/b/{bId}/c"))
                .findFirst().orElseThrow();

        assertThat(cFamily.steps())
                .extracting(ApiCallInfo::path)
                .containsExactly("/a", "/a/{aId}/b", "/a/{aId}/b/{bId}/c");
    }

    @Test
    void unresolvedParam_skipsByDefault() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.unresolvedParamSpec());
        List<EnumeratedChain> chains = new ChainEnumerator().enumerate(calls);

        assertThat(chains).isEmpty();
    }

    @Test
    void unresolvedParam_emitWithMarker_emitsWithUnresolvedFlag() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.unresolvedParamSpec());
        List<EnumeratedChain> chains = new ChainEnumerator(ChainConfig.builder()
                .unresolvedParamPolicy(ChainConfig.UnresolvedParamPolicy.EMIT_WITH_MARKER)
                .build()).enumerate(calls);

        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).unresolved()).isTrue();
        assertThat(chains.get(0).seedPost().path()).isEqualTo("/orphans/{parentId}/children");
    }

    @Test
    void maxChainLength_appliesAcrossPrefixAndTail() {
        // twoLevelNestedSpec c-family minimal chain is 3 steps (/a + /a/{aId}/b + /a/{aId}/b/{bId}/c).
        // With maxChainLength = 2, the c-family can't even emit its minimal chain.
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.twoLevelNestedSpec());
        List<EnumeratedChain> chains = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(2).build()).enumerate(calls);

        assertThat(chains).noneMatch(c -> c.seedPost().path().equals("/a/{aId}/b/{bId}/c"));
    }

    private static List<String> methodsOf(EnumeratedChain chain) {
        return chain.steps().stream().map(ApiCallInfo::method).toList();
    }
}
