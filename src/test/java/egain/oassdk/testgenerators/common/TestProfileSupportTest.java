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
                List.of("contract", "integration", "nfr", "security"), config);

        assertThat(filtered).containsExactly("integration");
    }

    @Test
    void aggregatorModules_reflectsGeneratedTypes() {
        assertThat(TestProfileSupport.aggregatorModules(List.of("contract", "integration", "postman")))
                .containsExactly("contract", "integration");
    }
}
