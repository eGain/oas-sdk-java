package egain.oassdk.testgenerators.common;

import java.nio.file.Path;

/**
 * Standard directory layout for generated Java test modules.
 */
public final class TestOutputLayout {

    public static final String TEST_SOURCE_ROOT = "src/test/java";
    public static final String SUPPORT_PACKAGE_SUFFIX = ".support";

    private TestOutputLayout() {
    }

    public static String testJavaDir(String moduleDir, String basePackage) {
        return moduleDir + "/" + TEST_SOURCE_ROOT + "/" + basePackage.replace('.', '/');
    }

    public static String supportJavaDir(String outputDir, String basePackage) {
        return outputDir + "/test-support/" + TEST_SOURCE_ROOT + "/"
                + (basePackage + SUPPORT_PACKAGE_SUFFIX).replace('.', '/');
    }

    public static String supportPackage(String basePackage) {
        return basePackage + SUPPORT_PACKAGE_SUFFIX;
    }

    public static Path supportRoot(String outputDir) {
        return Path.of(outputDir, "test-support", TEST_SOURCE_ROOT);
    }
}
