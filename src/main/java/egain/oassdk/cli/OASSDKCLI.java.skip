package egain.oassdk.cli;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line interface for OAS SDK
 */
@Command(name = "oas-sdk", mixinStandardHelpOptions = true, version = "1.0.0",
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

        @Option(names = {"-c", "--config"}, description = "Configuration file")
        private String config;

        @Option(names = {"-s", "--search-path"}, split = ",",
                description = "Path(s) to search for external $ref (e.g. published root). Comma-separated or repeated.")
        private List<String> searchPaths;

        @Override
        public Integer call() {
            try {
                // Build configuration from CLI params (config file loading not yet implemented)
                GeneratorConfig generatorConfig = GeneratorConfig.builder()
                        .language(language)
                        .framework(framework)
                        .packageName(packageName)
                        .outputDir(output)
                        .searchPaths(searchPaths != null && !searchPaths.isEmpty() ? searchPaths : null)
                        .build();

                // Initialize SDK
                OASSDK sdk = new OASSDK(generatorConfig, null, null);

                // Load specification
                sdk.loadSpec(specPath);

                // Generate application
                sdk.generateApplication(language, framework, packageName, output);

                logger.info("✅ Application generated successfully in " + output);
                return 0;

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

        @Override
        public Integer call() {
            try {
                // Initialize SDK
                TestConfig testConfig = TestConfig.builder()
                        .testFramework(framework)
                        .build();
                OASSDK sdk = new OASSDK(null, testConfig, null);

                // Load specification
                sdk.loadSpec(specPath);

                // Generate tests
                sdk.generateTests(types, framework, output);

                logger.info("✅ Tests generated successfully in " + output);
                return 0;

            } catch (OASSDKException e) {
                logger.log(Level.SEVERE, "❌ Error: " + e.getMessage(), e);
                return 1;
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
            try {
                // Initialize SDK
                OASSDK sdk = new OASSDK();

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
                OASSDK sdk = new OASSDK(null, null, slaConfig);

                // Load specification
                sdk.loadSpec(specPath);

                // Generate SLA enforcement
                sdk.generateSLAEnforcement(slaPath, output);

                logger.info("✅ SLA enforcement generated successfully in " + output);
                return 0;

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

        @Option(names = {"-c", "--config"}, description = "Configuration file")
        private String config;

        @Override
        public Integer call() {
            try {
                GeneratorConfig generatorConfig;
                TestConfig testConfig = TestConfig.builder().build();
                SLAConfig slaConfig = null;

                if (config != null) {
                    // Configuration loading from file is not yet implemented
                    generatorConfig = GeneratorConfig.builder().build();
                    slaConfig = SLAConfig.builder().build();
                } else {
                    generatorConfig = GeneratorConfig.builder()
                            .language(language)
                            .framework(framework)
                            .packageName(packageName)
                            .outputDir(output)
                            .searchPaths(searchPaths != null && !searchPaths.isEmpty() ? searchPaths : null)
                            .build();
                }

                OASSDK sdk = new OASSDK(generatorConfig, testConfig, slaConfig);

                sdk.loadSpec(specPath);

                sdk.generateAll(output);

                logger.info("✅ Complete project generated successfully in " + output);
                return 0;

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
            try {
                // Initialize SDK
                OASSDK sdk = new OASSDK();

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

    @Command(name = "info", description = "Show information about OpenAPI specification")
    public static class InfoCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to OpenAPI specification file")
        private String specPath;

        @Override
        public Integer call() {
            try {
                // Initialize SDK
                OASSDK sdk = new OASSDK();

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
                .addSubcommand("info", new InfoCommand())
                .execute(args);
        System.exit(exitCode);
    }
}
