package egain.oassdk.generators.java;

import java.io.IOException;
import java.util.logging.Logger;

import egain.oassdk.core.logging.LoggerConfig;

/**
 * Copies the observability instrumentation classes (MetricsFilter, TracingFilter, MetricsEndpoint,
 * ObservabilityBootstrap) into the generated application when observability is enabled.
 *
 * <p>These classes are fixed and spec-independent, so they are stored verbatim under
 * {@code src/main/resources/runtime/jersey/observability} and copied with only the package and
 * javax/jakarta namespace placeholders substituted.
 */
class JerseyObservabilityGenerator {

    private static final Logger logger = LoggerConfig.getLogger(JerseyObservabilityGenerator.class);

    private static final String[] OBSERVABILITY_CLASSES = {
            "MetricsFilter", "TracingFilter", "MetricsEndpoint", "ObservabilityBootstrap",
    };

    private final JerseyGenerationContext ctx;

    JerseyObservabilityGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Entry point: generate observability classes if enabled.
     */
    void generate() throws IOException {
        generateObservability(ctx.outputDir, ctx.packageName, ctx.spec);
    }

    private void generateObservability(String outputDir, String packageName, java.util.Map<String, Object> spec) throws IOException {
        if (!ctx.isObservabilityEnabled()) {
            return;
        }

        String packagePath = packageName != null ? packageName : "com.example.api";
        String obsDir = outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/observability";

        String serviceName = ctx.config.getObservabilityConfig().getServiceName();
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = JerseyGenerationContext.getAPITitle(spec);
        }

        for (String className : OBSERVABILITY_CLASSES) {
            String content = JerseyGenerationContext
                    .readRuntimeResource("runtime/jersey/observability/" + className + ".java")
                    .replace("__WS_NS__", ctx.getWsNs())
                    .replace("__INJECT_NS__", ctx.injectNs)
                    .replace("__PACKAGE__", packagePath);
            JerseyGenerationContext.writeFile(obsDir + "/" + className + ".java", content);
        }

        logger.info("Generated observability instrumentation for service: " + serviceName);
    }
}
