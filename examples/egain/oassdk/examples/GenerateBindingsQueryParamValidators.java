package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;

/**
 * Regenerates bindings {@code QueryParamValidators.txt} and {@code ValidationMapHelper.txt}
 * (modelsOnly) from {@code examples/bundle-openapi 3.yaml} for manual integration into the
 * bindings WS project as {@code .java} files.
 */
public final class GenerateBindingsQueryParamValidators {

    private static final String BINDINGS_PACKAGE = "com.egain.bindings.ws.model.xsds.common.v4";
    private static final String OUTPUT_DIR = "./target/generated-bindings-query-param-validators";

    private GenerateBindingsQueryParamValidators() {
    }

    public static void main(String[] args) throws OASSDKException {
        String specFile = "examples/bundle-openapi 3.yaml";
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .packageName(BINDINGS_PACKAGE)
                .outputDir(OUTPUT_DIR)
                .build();

        System.out.println("Generating bindings validation artifacts (modelsOnly)...");
        System.out.println("  spec:    " + specFile);
        System.out.println("  package: " + BINDINGS_PACKAGE);
        System.out.println("  output:  " + OUTPUT_DIR);

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specFile);
            sdk.generateApplication("java", "jersey", BINDINGS_PACKAGE, OUTPUT_DIR);
        }

        String base = OUTPUT_DIR + "/" + BINDINGS_PACKAGE.replace('.', '/');
        System.out.println("Done. Copy to bindings WS project:");
        System.out.println("  " + base + "/QueryParamValidators.txt -> QueryParamValidators.java");
        System.out.println("  " + base + "/ValidationMapHelper.txt -> ValidationMapHelper.java (if endpoint map changed)");
    }
}
