package egain.oassdk.cli;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.generators.GeneratorFactory;
import egain.oassdk.testgenerators.schemathesis.SchemathesisTestGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line interface for OAS SDK
 */
@Command(name = "oas-sdk", mixinStandardHelpOptions = true, version = "2.1-SNAPSHOT",
        description = "OpenAPI Specification SDK (OAS-SDK) for Java")
public class OASSDKCLI implements Callable<Integer> {

    private static final Logger logger = Logger.getLogger(OASSDKCLI.class.getName());

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "generate", description = "Generate application from OpenAPI specification")
    public static class GenerateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Option(names = {"-l", "--language"}, defaultValue = "java",
                description = "Programming language")
        private String language;

        @Option(names = {"-f", "--framework"}, defaultValue = "jersey",
                description = "Framework")
        private String framework;

        @Option(names = {"-p", "--package"}, description = "Package name")
        private String packageName;

        @Option(names = {"-o", "--output"}, defaultValue = "./generated",
                description = "Output directory")
        private String output;

        @Option(names = {"-s", "--search-path"}, split = ",",
                description = "Path(s) to search for external $ref (e.g. published root). Comma-separated or repeated.")
        private List<String> searchPaths;

        @Option(names = {"--spec-zip"}, description = "Path to ZIP file containing specs; specPath is then an entry path inside the ZIP")
        private String specZipPath;

        @Option(names = {"--authorization-data"}, description = "Generate Java classes from x-egain-authorization-data on component schemas")
        private boolean authorizationData;

        @Option(names = {"--jakarta"}, description = "Use Jakarta EE namespace (jakarta.*) instead of Java EE namespace (javax.*) in generated code")
        private boolean useJakartaNamespace;

        @Option(names = {"--standalone"},
                description = "Generate open-source-friendly Java code without proprietary eGain platform dependencies "
                        + "(JAXBBean, ValidationBuilder, ValidationError, etc.). Models implement only Serializable.")
        private boolean standaloneMode;

        @Override
        public Integer call() {
            try {
                // Build configuration from CLI params
                GeneratorConfig.Builder configBuilder = GeneratorConfig.builder()
                        .language(language)
                        .framework(framework)
                        .packageName(packageName)
                        .outputDir(output)
                        .searchPaths(searchPaths != null && !searchPaths.isEmpty() ? searchPaths : null)
                        .authorizationDataGenerationEnabled(authorizationData)
                        .useJakartaNamespace(useJakartaNamespace);
                if (specZipPath != null && !specZipPath.isEmpty()) {
                    configBuilder.specZipPath(specZipPath);
                }
                if (standaloneMode) {
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("standaloneMode", "true");
                    configBuilder.additionalProperties(extra);
                }
                GeneratorConfig generatorConfig = configBuilder.build();

                // Initialize SDK
                try (OASSDK sdk = new OASSDK(generatorConfig, null, null)) {
                    // Load specification (filesystem path or ZIP entry path when specZipPath is set)
                    sdk.loadSpec(specPath);

                    // Generate application
                    sdk.generateApplication(language, framework, packageName, output);

                    logger.info("✅ Application generated successfully in " + output);
                    return 0;
                }

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    @Command(name = "tests", description = "Generate test suite from OpenAPI specification")
    public static class TestsCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Option(names = {"-o", "--output"}, defaultValue = "./generated/tests",
                description = "Output directory")
        private String output;

        @Option(names = {"-t", "--types"}, split = ",",
                defaultValue = "unit,integration",
                description = "Test types")
        private List<String> types;

        @Option(names = {"--framework"}, description = "Test framework")
        private String framework;

        @Option(names = {"--url", "--base-url"}, description = "Base URL for schemathesis.properties when generating schemathesis tests")
        private String baseUrl;

        @Option(names = {"--run"}, description = "After generation, run ./run-schemathesis.sh when types include schemathesis (requires bash and st on PATH)")
        private boolean runSchemathesis;

        @Override
        public Integer call() {
            try {
                Map<String, Object> extra = new HashMap<>();
                if (baseUrl != null && !baseUrl.isBlank()) {
                    extra.put("schemathesis.baseUrl", baseUrl.trim());
                }
                TestConfig testConfig = TestConfig.builder()
                        .testFramework(framework)
                        .additionalProperties(extra.isEmpty() ? null : extra)
                        .build();
                try (OASSDK sdk = new OASSDK(null, testConfig, null)) {
                    // Load specification
                    sdk.loadSpec(specPath);

                    // Generate tests
                    sdk.generateTests(types, framework, output);

                    if (runSchemathesis && types != null
                            && types.stream().anyMatch(t -> "schemathesis".equalsIgnoreCase(t))) {
                        runSchemathesisScript(output, testConfig);
                    }

                    logger.info("✅ Tests generated successfully in " + output);
                    return 0;
                }

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }

        private static void runSchemathesisScript(String output, TestConfig testConfig) throws OASSDKException, IOException, InterruptedException {
            Path bundle = SchemathesisTestGenerator.resolveBundleDirectory(output, testConfig);
            Path script = bundle.resolve("run-schemathesis.sh");
            if (!Files.isRegularFile(script)) {
                throw new OASSDKException("Expected Schemathesis script at " + script + ". Generate with -t schemathesis.");
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            List<String> command = os.contains("win")
                    ? List.of("bash.exe", "./run-schemathesis.sh")
                    : List.of("bash", "./run-schemathesis.sh");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(bundle.toFile());
            pb.inheritIO();
            Process process = pb.start();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new OASSDKException("Schemathesis run exited with code " + exit);
            }
        }
    }

    @Command(name = "mockdata", description = "Generate mock data from OpenAPI specification")
    public static class MockDataCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Option(names = {"-o", "--output"}, defaultValue = "./generated/mock-data",
                description = "Output directory")
        private String output;

        @Override
        public Integer call() {
            try (OASSDK sdk = new OASSDK()) {
                // Load specification
                sdk.loadSpec(specPath);

                // Generate mock data
                sdk.generateMockData(output);

                logger.info("✅ Mock data generated successfully in " + output);
                return 0;

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    @Command(name = "sla", description = "Generate SLA enforcement from OpenAPI specification and SLA file")
    public static class SLACommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Parameters(index = "1", description = "Path to SLA specification file")
        private String slaPath;

        @Option(names = {"-o", "--output"}, defaultValue = "./generated/sla-enforcement",
                description = "Output directory")
        private String output;

        @Override
        public Integer call() {
            try {
                // Initialize SDK
                SLAConfig slaConfig = SLAConfig.builder()
                        .slaFile(slaPath)
                        .build();
                try (OASSDK sdk = new OASSDK(null, null, slaConfig)) {
                    // Load specification
                    sdk.loadSpec(specPath);

                    // Generate SLA enforcement
                    sdk.generateSLAEnforcement(slaPath, output);

                    logger.info("✅ SLA enforcement generated successfully in " + output);
                    return 0;
                }

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    @Command(name = "all", description = "Generate complete project from OpenAPI specification (application, unit/integration/NFR tests, mock data, docs)")
    public static class AllCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Option(names = {"-o", "--output"}, defaultValue = "./generated",
                description = "Output directory")
        private String output;

        @Option(names = {"-p", "--package"}, description = "Package name for generated code")
        private String packageName;

        @Option(names = {"-l", "--language"}, defaultValue = "java",
                description = "Programming language")
        private String language;

        @Option(names = {"-f", "--framework"}, defaultValue = "jersey",
                description = "Framework")
        private String framework;

        @Option(names = {"-s", "--search-path"}, split = ",",
                description = "Path(s) to search for external $ref (e.g. published root). Comma-separated or repeated.")
        private List<String> searchPaths;

        @Option(names = {"--jakarta"}, description = "Use Jakarta EE namespace (jakarta.*) instead of Java EE namespace (javax.*) in generated code")
        private boolean useJakartaNamespace;

        @Option(names = {"--standalone"},
                description = "Generate open-source-friendly Java code without proprietary eGain platform dependencies "
                        + "(JAXBBean, ValidationBuilder, ValidationError, etc.). Models implement only Serializable.")
        private boolean standaloneMode;

        @Override
        public Integer call() {
            try {
                GeneratorConfig.Builder configBuilder = GeneratorConfig.builder()
                        .language(language)
                        .framework(framework)
                        .packageName(packageName)
                        .outputDir(output)
                        .searchPaths(searchPaths != null && !searchPaths.isEmpty() ? searchPaths : null)
                        .useJakartaNamespace(useJakartaNamespace);
                if (standaloneMode) {
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("standaloneMode", "true");
                    configBuilder.additionalProperties(extra);
                }
                GeneratorConfig generatorConfig = configBuilder.build();
                TestConfig testConfig = TestConfig.builder().build();
                SLAConfig slaConfig = null;

                try (OASSDK sdk = new OASSDK(generatorConfig, testConfig, slaConfig)) {
                    sdk.loadSpec(specPath);

                    sdk.generateAll(output);

                    logger.info("✅ Complete project generated successfully in " + output);
                    return 0;
                }

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    @Command(name = "validate", description = "Validate OpenAPI specification")
    public static class ValidateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Override
        public Integer call() {
            try (OASSDK sdk = new OASSDK()) {
                // Load specification
                sdk.loadSpec(specPath);

                // Validate
                if (sdk.validateSpec()) {
                    logger.info("✅ Specification is valid");
                    return 0;
                } else {
                    logger.warning("❌ Specification is invalid");
                    return 1;
                }

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    @Command(name = "generators", description = "List supported code generators")
    public static class GeneratorsCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            GeneratorFactory factory = new GeneratorFactory();
            logger.info("Implemented generators:");
            for (String combo : factory.getImplementedCombinations()) {
                logger.info("  " + combo);
            }
            String[] stubs = factory.getStubCombinations();
            if (stubs.length > 0) {
                logger.info("\nStub generators (registered, not yet implemented):");
                for (String combo : stubs) {
                    logger.info("  " + combo + " (stub)");
                }
            }
            return 0;
        }
    }

    @Command(name = "info", description = "Show information about OpenAPI specification")
    public static class InfoCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Override
        public Integer call() {
            try (OASSDK sdk = new OASSDK()) {
                // Load specification
                sdk.loadSpec(specPath);

                // Get metadata
                var metadata = sdk.getMetadata();

                // Display information
                logger.info("📋 API Information:");
                var apiInfo = egain.oassdk.Util.asStringObjectMap(metadata.get("api_info"));
                logger.info("  Title: " + apiInfo.get("title"));
                logger.info("  Version: " + apiInfo.get("version"));
                logger.info("  Description: " + apiInfo.get("description"));

                logger.info("\n📊 Statistics:");
                var stats = egain.oassdk.Util.asStringObjectMap(metadata.get("statistics"));
                logger.info("  Endpoints: " + stats.get("endpoint_count"));
                logger.info("  Models: " + stats.get("model_count"));
                logger.info("  Paths: " + stats.get("path_count"));

                logger.info("\n🔗 Endpoints:");
                var endpoints = egain.oassdk.Util.asStringObjectMapList(metadata.get("endpoints"));
                for (var endpoint : endpoints) {
                    logger.info("  " + endpoint.get("method") + " " + endpoint.get("path"));
                }

                return 0;

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OASSDKCLI())
                .addSubcommand("generate", new GenerateCommand())
                .addSubcommand("tests", new TestsCommand())
                .addSubcommand("mockdata", new MockDataCommand())
                .addSubcommand("sla", new SLACommand())
                .addSubcommand("all", new AllCommand())
                .addSubcommand("validate", new ValidateCommand())
                .addSubcommand("generators", new GeneratorsCommand())
                .addSubcommand("info", new InfoCommand())
                .execute(args);
        System.exit(exitCode);
    }
}
