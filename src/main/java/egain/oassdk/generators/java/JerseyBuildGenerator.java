package egain.oassdk.generators.java;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Generates build-related artifacts for the Jersey application:
 * <ul>
 *   <li>Main Application class (JAX-RS with Grizzly)</li>
 *   <li>ApiService stub</li>
 *   <li>CorsFilter configuration</li>
 *   <li>GenericExceptionMapper</li>
 *   <li>pom.xml and web.xml</li>
 * </ul>
 *
 * <p>The fixed boilerplate for each artifact is stored verbatim under
 * {@code src/main/resources/runtime/jersey} and copied with placeholder substitution
 * ({@code __PACKAGE__}, {@code __WS_NS__}, {@code __CLASS_NAME__}, etc.). The config-driven
 * fragments (javax/jakarta dependencies, observability blocks) live under {@code .../fragments}.
 */
class JerseyBuildGenerator {

    private final JerseyGenerationContext ctx;

    JerseyBuildGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Generate the main JAX-RS Application class with Grizzly server support.
     */
    public void generateMainApplicationClass(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String className = JerseyGenerationContext.getAPITitle(spec).replaceAll("[^a-zA-Z0-9]", "") + "Application";

        String content = JerseyGenerationContext.readRuntimeResource("runtime/jersey/Application.java")
                .replace("__OBSERVABILITY_REGISTRATION__", getObservabilityRegistration(packagePath))
                .replace("__CLASS_NAME__", className)
                .replace("__WS_NS__", ctx.getWsNs())
                .replace("__PACKAGE__", packagePath);

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/" + className + ".java", content);
    }

    /**
     * Returns observability class registration lines for the Application class constructor,
     * or an empty string if observability is not enabled.
     */
    public String getObservabilityRegistration(String packagePath) throws IOException {
        if (!ctx.isObservabilityEnabled()) {
            return "";
        }
        return JerseyGenerationContext
                .readRuntimeResource("runtime/jersey/fragments/app-observability-registration.txt")
                .replace("__PACKAGE__", packagePath);
    }

    /**
     * Generate ApiService stub class.
     */
    public void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String content = JerseyGenerationContext.readRuntimeResource("runtime/jersey/ApiService.java")
                .replace("__INJECT_NS__", ctx.injectNs)
                .replace("__PACKAGE__", packagePath);
        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/service/ApiService.java", content);
    }

    /**
     * Generate CorsFilter configuration class.
     */
    public void generateConfiguration(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String content = JerseyGenerationContext.readRuntimeResource("runtime/jersey/CorsFilter.java")
                .replace("__WS_NS__", ctx.getWsNs())
                .replace("__PACKAGE__", packagePath);
        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/config/CorsFilter.java", content);
    }

    /**
     * Generate GenericExceptionMapper class.
     */
    public void generateExceptionMappers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String content = JerseyGenerationContext.readRuntimeResource("runtime/jersey/GenericExceptionMapper.java")
                .replace("__WS_NS__", ctx.getWsNs())
                .replace("__PACKAGE__", packagePath);
        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/exception/GenericExceptionMapper.java", content);
    }

    /**
     * Orchestrate generation of pom.xml and web.xml.
     */
    public void generateBuildFiles(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        JerseyGenerationContext.writeFile(outputDir + "/pom.xml", generatePomXml(spec, packageName));
        JerseyGenerationContext.writeFile(outputDir + "/src/main/webapp/WEB-INF/web.xml", generateWebXml(packageName));
    }

    /**
     * Generate Maven pom.xml content.
     */
    public String generatePomXml(Map<String, Object> spec, String packageName) throws IOException {
        return JerseyGenerationContext.readRuntimeResource("runtime/jersey/pom.xml")
                .replace("__NAMESPACE_DEPS__", getNamespaceDependencies())
                .replace("__OBSERVABILITY_DEPS__", getObservabilityDependencies())
                .replace("__GROUP_ID__", packageName != null ? packageName : "com.example.api")
                .replace("__ARTIFACT_ID__", JerseyGenerationContext.getAPITitle(spec).toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "-"))
                .replace("__VERSION__", JerseyGenerationContext.getAPIVersion(spec))
                .replace("__NAME__", JerseyGenerationContext.getAPITitle(spec))
                .replace("__DESCRIPTION__", String.valueOf(JerseyGenerationContext.getAPIDescription(spec)));
    }

    /**
     * Returns the namespace-specific (javax/jakarta) Maven dependency XML block for the pom.
     */
    public String getNamespaceDependencies() throws IOException {
        return JerseyGenerationContext.readRuntimeResource(
                ctx.useJakarta ? "runtime/jersey/fragments/pom-deps-jakarta.xml"
                               : "runtime/jersey/fragments/pom-deps-javax.xml");
    }

    /**
     * Returns observability Maven dependencies XML block, or empty string if not enabled.
     */
    public String getObservabilityDependencies() throws IOException {
        if (!ctx.isObservabilityEnabled()) {
            return "";
        }
        return JerseyGenerationContext.readRuntimeResource("runtime/jersey/fragments/pom-deps-observability.xml");
    }

    /**
     * Generate web.xml content for servlet container deployment.
     */
    public String generateWebXml(String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String className = JerseyGenerationContext.getAPITitle(ctx.spec).replaceAll("[^a-zA-Z0-9]", "") + "Application";
        String resource = ctx.useJakarta ? "runtime/jersey/web-jakarta.xml" : "runtime/jersey/web-javax.xml";
        return JerseyGenerationContext.readRuntimeResource(resource)
                .replace("__WS_NS__", ctx.getWsNs())
                .replace("__CLASS_NAME__", className)
                .replace("__PACKAGE__", packagePath);
    }
}
