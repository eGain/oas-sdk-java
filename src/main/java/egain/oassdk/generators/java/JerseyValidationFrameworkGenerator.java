package egain.oassdk.generators.java;

import java.io.IOException;

/**
 * Copies the {@code egain.framework.validation} runtime framework that the generated parameter
 * validators build on — the {@link ValidatorAction}-style SPI, the {@code Validator} engine, the
 * fluent builders, the {@code ValidationError} model, and the {@code L10NResource} data holder in
 * the {@code egain.framework.validation.data} subpackage.
 *
 * <p>These classes are fixed and spec-independent (they hardcode the {@code egain.framework.validation}
 * package), so they are stored verbatim under {@code src/main/resources/runtime/jersey} and copied
 * as-is rather than emitted from inline text blocks.
 *
 * <p>Like {@code RequestInfo} / {@code Validations}, they live in fixed packages that the generated
 * validators import directly, so they are emitted unconditionally (in both full and models-only
 * modes) by the orchestrator.
 */
class JerseyValidationFrameworkGenerator {

    private static final String RESOURCE_BASE = "runtime/jersey/";

    private static final String[] FRAMEWORK_CLASSES = {
            "egain/framework/validation/ValidatorAction.java",
            "egain/framework/validation/Validator.java",
            "egain/framework/validation/ValidationBuilder.java",
            "egain/framework/validation/ValidationError.java",
            "egain/framework/validation/ValidationErrorBuilder.java",
            "egain/framework/validation/ValidationErrorHelper.java",
            "egain/framework/validation/data/L10NResource.java",
    };

    private final JerseyGenerationContext ctx;

    JerseyValidationFrameworkGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Copy every framework class into the {@code egain/framework/validation} (and {@code .../data})
     * directories under the output source root.
     */
    void generate() throws IOException {
        if (ctx.outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String sourceRoot = ctx.outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/");
        for (String relativePath : FRAMEWORK_CLASSES) {
            String content = JerseyGenerationContext.readRuntimeResource(RESOURCE_BASE + relativePath);
            JerseyGenerationContext.writeFile(sourceRoot + relativePath, content);
        }
    }
}
