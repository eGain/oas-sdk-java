package egain.oassdk.core.sequence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiCallExtractorTest {

    private final ApiCallExtractor extractor = new ApiCallExtractor();

    @Test
    void extractsTwoCallsFromMinimalFolderSpec() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.minimalFolderSpec());

        assertThat(calls).hasSize(2);
        ApiCallInfo post = calls.stream().filter(c -> c.method().equals("POST")).findFirst().orElseThrow();
        ApiCallInfo get = calls.stream().filter(c -> c.method().equals("GET")).findFirst().orElseThrow();

        assertThat(post.path()).isEqualTo("/folders");
        assertThat(post.operationId()).isEqualTo("createFolder");
        assertThat(post.resourceName()).isEqualTo("folders");
        assertThat(post.hasPathParams()).isFalse();
        assertThat(post.hasRequestBody()).isTrue();
        assertThat(post.pathParamNames()).isEmpty();
        assertThat(post.defaultQueryParams()).isEmpty();
        assertThat(post.isCreator()).isTrue();

        assertThat(get.path()).isEqualTo("/folders/{folderID}");
        assertThat(get.operationId()).isEqualTo("getFolder");
        assertThat(get.resourceName()).isEqualTo("folders");
        assertThat(get.hasPathParams()).isTrue();
        assertThat(get.pathParamNames()).containsExactly("folderID");
        assertThat(get.defaultQueryParams())
                .containsEntry("kbLanguage", "en-US")
                .containsEntry("$level", "0");
        assertThat(get.isConsumer()).isTrue();
    }

    @Test
    void extractResourceName_skipsTemplateSegments() {
        assertThat(ApiCallExtractor.extractResourceName("/folders")).isEqualTo("folders");
        assertThat(ApiCallExtractor.extractResourceName("/folders/{id}")).isEqualTo("folders");
        assertThat(ApiCallExtractor.extractResourceName("/a/{x}/b/{y}")).isEqualTo("b");
        assertThat(ApiCallExtractor.extractResourceName("/")).isEqualTo("resource");
    }

    @Test
    void pickExampleForQueryParam_preferenceOrder() {
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("example", "hello"))).isEqualTo("hello");
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("default", 42))).isEqualTo("42");
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("enum", List.of("a", "b")))).isEqualTo("a");
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("type", "integer", "minimum", 5))).isEqualTo("5");
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("type", "boolean"))).isEqualTo("true");
        assertThat(ApiCallExtractor.pickExampleForQueryParam(Map.of("type", "string"))).isEqualTo("test");
        assertNull(ApiCallExtractor.pickExampleForQueryParam(Map.of("type", "object")));
    }

    @Test
    void buildRequestBodyForOperation_resolvesRef() {
        Map<String, Object> spec = SequenceTestFixtures.minimalFolderSpec();
        List<ApiCallInfo> calls = extractor.extract(spec);
        ApiCallInfo post = calls.stream().filter(ApiCallInfo::isCreator).findFirst().orElseThrow();

        String json = extractor.buildRequestBodyForOperation(post.operation(), spec);

        assertThat(json).isEqualTo("{\"name\": \"mock_name\"}");
    }
}
