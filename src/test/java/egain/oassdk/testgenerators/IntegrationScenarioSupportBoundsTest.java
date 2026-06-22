package egain.oassdk.testgenerators;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationScenarioSupportBoundsTest {

    @Test
    void getParameterExample_clampsPagesizeToMaximum() {
        Map<String, Object> param = Map.of(
                "name", "$pagesize",
                "in", "query",
                "example", 123,
                "schema", Map.of("type", "integer", "maximum", 75, "minimum", 1)
        );
        assertThat(IntegrationScenarioSupport.getParameterExample(param)).isEqualTo("75");
    }

    @Test
    void getParameterExample_usesMinimumWhenNoExample() {
        Map<String, Object> param = Map.of(
                "name", "$pagenum",
                "in", "query",
                "schema", Map.of("type", "integer", "minimum", 1)
        );
        assertThat(IntegrationScenarioSupport.getParameterExample(param)).isEqualTo("1");
    }

    @Test
    void buildSuccessQueryParams_omitsOrderWithoutSort() {
        var params = IntegrationScenarioSupport.buildSuccessQueryParams(
                java.util.List.of(
                        Map.of("name", "$order", "in", "query", "schema", Map.of("type", "string")),
                        Map.of("name", "departmentId", "in", "query", "schema", Map.of("type", "string"))
                ),
                Map.of()
        );
        assertThat(params).doesNotContainKey("$order");
        assertThat(params).containsKey("departmentId");
    }
}
