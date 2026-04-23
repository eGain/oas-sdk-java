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
    void idVariableName_snakeCasesCamelAndUpper() {
        assertThat(ApiCallExtractor.idVariableName("folderID")).isEqualTo("folder_id");
        assertThat(ApiCallExtractor.idVariableName("orderId")).isEqualTo("order_id");
        assertThat(ApiCallExtractor.idVariableName("UserID")).isEqualTo("user_id");
        assertThat(ApiCallExtractor.idVariableName("user_id")).isEqualTo("user_id");
        assertThat(ApiCallExtractor.idVariableName("ABCId")).isEqualTo("abc_id");
        assertThat(ApiCallExtractor.idVariableName("id")).isEqualTo("id");
        assertThat(ApiCallExtractor.idVariableName("")).isEqualTo("resource_id");
        assertThat(ApiCallExtractor.idVariableName(null)).isEqualTo("resource_id");
    }

    @Test
    void findProducerForParam_exactPrefixMatch() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        ApiCallInfo items = calls.stream()
                .filter(c -> c.path().equals("/orders/{orderId}/items") && c.method().equals("POST"))
                .findFirst().orElseThrow();

        ApiCallInfo producer = ApiCallExtractor.findProducerForParam(items, "orderId", calls);

        assertThat(producer).isNotNull();
        assertThat(producer.path()).isEqualTo("/orders");
        assertThat(producer.method()).isEqualTo("POST");
    }

    @Test
    void findProducerForParam_recursivelyResolvesNestedSubResource() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        ApiCallInfo itemGet = calls.stream()
                .filter(c -> c.path().equals("/orders/{orderId}/items/{itemId}") && c.method().equals("GET"))
                .findFirst().orElseThrow();

        ApiCallInfo orderIdProducer = ApiCallExtractor.findProducerForParam(itemGet, "orderId", calls);
        ApiCallInfo itemIdProducer = ApiCallExtractor.findProducerForParam(itemGet, "itemId", calls);

        assertThat(orderIdProducer.path()).isEqualTo("/orders");
        assertThat(itemIdProducer.path()).isEqualTo("/orders/{orderId}/items");
        assertThat(itemIdProducer.method()).isEqualTo("POST");
    }

    @Test
    void findProducerForParam_nameStemFallback() {
        // POST at /teams is the only creator; consumer path doesn't share a literal prefix
        // with /teams, so only the name-stem fallback can resolve `teamId`.
        Map<String, Object> spec = Map.of("paths", Map.of(
                "/teams", Map.of("post", Map.of("operationId", "createTeam")),
                "/reports/{teamId}", Map.of("get", Map.of("operationId", "getReport"))));
        List<ApiCallInfo> calls = extractor.extract(spec);
        ApiCallInfo report = calls.stream()
                .filter(c -> c.path().equals("/reports/{teamId}"))
                .findFirst().orElseThrow();

        ApiCallInfo producer = ApiCallExtractor.findProducerForParam(report, "teamId", calls);

        assertThat(producer).isNotNull();
        assertThat(producer.path()).isEqualTo("/teams");
    }

    @Test
    void findProducerForParam_returnsNullWhenUnresolvable() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.unresolvedParamSpec());
        ApiCallInfo orphan = calls.stream()
                .filter(c -> c.path().equals("/orphans/{parentId}/children"))
                .findFirst().orElseThrow();

        ApiCallInfo producer = ApiCallExtractor.findProducerForParam(orphan, "parentId", calls);

        assertThat(producer).isNull();
    }

    @Test
    void apiCallInfo_isSubResourceCreator() {
        List<ApiCallInfo> calls = extractor.extract(SequenceTestFixtures.orderWithItemsSpec());
        ApiCallInfo topLevel = calls.stream()
                .filter(c -> c.path().equals("/orders") && c.method().equals("POST"))
                .findFirst().orElseThrow();
        ApiCallInfo subResource = calls.stream()
                .filter(c -> c.path().equals("/orders/{orderId}/items") && c.method().equals("POST"))
                .findFirst().orElseThrow();
        ApiCallInfo nonPost = calls.stream()
                .filter(c -> c.method().equals("GET"))
                .findFirst().orElseThrow();

        assertThat(topLevel.isCreator()).isTrue();
        assertThat(topLevel.isSubResourceCreator()).isFalse();
        assertThat(subResource.isCreator()).isFalse();
        assertThat(subResource.isSubResourceCreator()).isTrue();
        assertThat(nonPost.isCreator()).isFalse();
        assertThat(nonPost.isSubResourceCreator()).isFalse();
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
