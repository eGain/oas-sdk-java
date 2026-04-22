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

        List<List<ApiCallInfo>> chains = e.enumerate(calls);

        assertThat(chains).hasSize(1);
        assertThat(chains.get(0)).singleElement().satisfies(c -> {
            assertThat(c.method()).isEqualTo("POST");
            assertThat(c.path()).isEqualTo("/folders");
        });
    }

    @Test
    void minimalSpec_maxLen2_addsPostGet() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.minimalFolderSpec());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(2).build());

        List<List<ApiCallInfo>> chains = e.enumerate(calls);

        assertThat(chains).hasSize(2);
        assertThat(methodsOf(chains.get(1))).containsExactly("POST", "GET");
    }

    @Test
    void crudSpec_deleteLastOnly_rejectsDeleteInMiddle() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.folderSpecWithCrud());
        ChainEnumerator e = new ChainEnumerator(
                ChainConfig.builder().maxChainLength(4).deleteLastOnly(true).build());

        List<List<ApiCallInfo>> chains = e.enumerate(calls);

        for (List<ApiCallInfo> chain : chains) {
            for (int i = 0; i < chain.size() - 1; i++) {
                assertThat(chain.get(i).method())
                        .as("chain %s has DELETE before end at index %d", methodsOf(chain), i)
                        .isNotEqualTo("DELETE");
            }
        }
    }

    @Test
    void crudSpec_chainSizes_matchExpectedPermutations() {
        // 4 consumers: GET, PUT, PATCH, DELETE
        // deleteLastOnly = true, allowRepeats = false
        // Length 1 — [POST]                                                   1
        // Length 2 — P(4,1) = 4; DELETE at end is allowed                     4
        // Length 3 — P(4,2) = 12; reject tails starting with DELETE (3)       9
        // Length 4 — P(4,3) = 24; tails without DELETE anywhere = P(3,3) = 6,
        //             plus tails ending in DELETE = P(3,2) = 6                12
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

        List<List<ApiCallInfo>> chains = new ChainEnumerator().enumerate(calls);

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

    private static List<String> methodsOf(List<ApiCallInfo> chain) {
        return chain.stream().map(ApiCallInfo::method).toList();
    }
}
