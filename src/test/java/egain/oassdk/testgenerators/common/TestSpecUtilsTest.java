package egain.oassdk.testgenerators.common;

import egain.oassdk.config.TestConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSpecUtilsTest {

    @Test
    void resolveBaseUrl_substitutesServerVariablesFromConfig() {
        Map<String, Object> spec = Map.of(
                "servers", List.of(Map.of(
                        "url", "https://${API_DOMAIN}/knowledge/contentmgr/v4",
                        "variables", Map.of("API_DOMAIN", Map.of("default", "example.com"))
                ))
        );
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("test.API_DOMAIN", "host.example");
        config.setAdditionalProperties(props);

        assertThat(TestSpecUtils.resolveBaseUrl(spec, config))
                .isEqualTo("https://host.example/knowledge/contentmgr/v4");
    }

    @Test
    void resolveBaseUrl_fallsBackToTestBaseUrlWhenPlaceholderRemains() {
        Map<String, Object> spec = Map.of(
                "servers", List.of(Map.of("url", "https://${API_DOMAIN}/v4"))
        );
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("test.baseUrl", "https://resolved.example/v4");
        config.setAdditionalProperties(props);

        assertThat(TestSpecUtils.resolveBaseUrl(spec, config))
                .isEqualTo("https://resolved.example/v4");
    }

    @Test
    void isEgainSpec_detectsVendorExtension() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("info", Map.of("x-vendor", "egain", "title", "API"));
        assertThat(TestSpecUtils.isEgainSpec(spec)).isTrue();
    }
}
