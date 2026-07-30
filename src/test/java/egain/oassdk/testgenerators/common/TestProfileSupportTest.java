package egain.oassdk.testgenerators.common;

import egain.oassdk.config.TestConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestProfileSupportTest {

    @Test
    void smokeProfile_filtersToIntegrationFocusedTypes() {
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("testProfile", "smoke");
        config.setAdditionalProperties(props);

        List<String> filtered = TestProfileSupport.filterTestTypes(
                List.of("unit", "integration", "nfr", "security"), config);

        assertThat(filtered).containsExactly("integration");
    }

    @Test
    void aggregatorModules_reflectsGeneratedTypes() {
        assertThat(TestProfileSupport.aggregatorModules(List.of("unit", "integration", "postman")))
                .containsExactly("unit", "integration");
    }

    @Test
    void applyPlaywrightFlag_appendsWhenEnabled() {
        List<String> types = TestProfileSupport.applyPlaywrightFlag(
                List.of("unit"), TestConfig.builder().build());
        assertThat(types).containsExactly("unit", "playwright");
    }

    @Test
    void applyPlaywrightFlag_stripsWhenDisabled() {
        List<String> types = TestProfileSupport.applyPlaywrightFlag(
                List.of("unit", "playwright"),
                TestConfig.builder().playwrightTests(false).build());
        assertThat(types).containsExactly("unit");
    }

    @Test
    void smokeProfile_allowsPlaywright() {
        TestConfig config = new TestConfig();
        Map<String, Object> props = new HashMap<>();
        props.put("testProfile", "smoke");
        config.setAdditionalProperties(props);

        List<String> filtered = TestProfileSupport.filterTestTypes(
                List.of("unit", "playwright", "integration"), config);

        assertThat(filtered).containsExactly("playwright", "integration");
    }
}
