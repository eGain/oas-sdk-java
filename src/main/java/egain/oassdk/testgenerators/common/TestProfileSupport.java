package egain.oassdk.testgenerators.common;

import egain.oassdk.config.TestConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves {@code testProfile} from {@link TestConfig} and filters generated test types.
 */
public final class TestProfileSupport {

    public static final String PROFILE_FULL = "full";
    public static final String PROFILE_SMOKE = "smoke";

    private static final Set<String> JAVA_MODULES = Set.of(
            "unit", "integration", "nfr", "performance", "security", "sequence-java");

    private TestProfileSupport() {
    }

    public static String profile(TestConfig config) {
        if (config == null || config.getAdditionalProperties() == null) {
            return PROFILE_FULL;
        }
        Object v = config.getAdditionalProperties().get("testProfile");
        if (v == null) {
            return PROFILE_FULL;
        }
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return PROFILE_SMOKE.equals(s) ? PROFILE_SMOKE : PROFILE_FULL;
    }

    public static boolean isSmoke(TestConfig config) {
        return PROFILE_SMOKE.equals(profile(config));
    }

    /**
     * When smoke profile is active, keep integration-focused suites only.
     */
    public static List<String> filterTestTypes(List<String> testTypes, TestConfig config) {
        if (!isSmoke(config) || testTypes == null) {
            return testTypes;
        }
        Set<String> allowed = Set.of("integration", "schemathesis", "postman", "sequence-java", "sequence");
        List<String> filtered = new ArrayList<>();
        for (String type : testTypes) {
            if (type != null && allowed.contains(type.toLowerCase(Locale.ROOT))) {
                filtered.add(type);
            }
        }
        return filtered.isEmpty() ? List.of("integration") : filtered;
    }

    public static List<String> aggregatorModules(List<String> testTypes) {
        Set<String> modules = new LinkedHashSet<>();
        if (testTypes == null) {
            return List.of("unit", "integration");
        }
        for (String type : testTypes) {
            if (type == null) {
                continue;
            }
            String normalized = type.toLowerCase(Locale.ROOT);
            if (JAVA_MODULES.contains(normalized)) {
                modules.add(normalized);
            }
        }
        if (modules.isEmpty()) {
            modules.add("integration");
        }
        return List.copyOf(modules);
    }
}
