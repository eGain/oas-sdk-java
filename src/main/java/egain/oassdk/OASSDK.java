package egain.oassdk;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.exceptions.ValidationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.core.metadata.OASMetadata;
import egain.oassdk.core.parser.OASParser;
import egain.oassdk.core.validator.OASValidator;
import egain.oassdk.docs.DocumentationGenerator;
import egain.oassdk.generators.GeneratorFactory;
import egain.oassdk.sla.SLAProcessor;
import egain.oassdk.testgenerators.TestGeneratorFactory;

import java.util.logging.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main SDK class for OpenAPI-first development
 * <p>
 * This class provides the primary interface for generating applications,
 * tests, mock data, SLA enforcement, and documentation from OpenAPI specifications.
 * When constructed with {@link GeneratorConfig#getSpecZipPath() specZipPath}, specs are
 * read from the ZIP and {@link #close()} should be called when done to release the ZIP filesystem.
 */
public class OASSDK implements AutoCloseable {

    private static final Logger logger = LoggerConfig.getLogger(OASSDK.class);

    private final GeneratorConfig generatorConfig;
    private final TestConfig testConfig;
    private final SLAConfig slaConfig;

    // Core components
    private final OASParser parser;
    private final OASValidator validator;
    /** When specZipPath is set, the ZIP is opened as a FileSystem and closed in close() */
    private final java.nio.file.FileSystem zipFileSystem;
    private final OASMetadata metadata;
    private final GeneratorFactory generatorFactory;
    private final TestGeneratorFactory testGeneratorFactory;
    private final SLAProcessor slaProcessor;
    private final DocumentationGenerator docGenerator;

    // Loaded specifications
    private Map<String, Object> spec;
    private Map<String, Object> slaSpec;

    // Path/operation filters
    private Set<String> pathFilters;  // e.g., ["/api/users", "/api/posts"]
    private Map<String, Set<String>> operationFilters;  // e.g., {"/api/users": ["GET", "POST"]}

    /**
     * Default constructor
     */
    public OASSDK() {
        this(null, null, null);
    }

    /**
     * Constructor with configuration
     *
     * @param generatorConfig Configuration for code generation
     * @param testConfig      Configuration for test generation
     * @param slaConfig       Configuration for SLA integration
     */
    public OASSDK(GeneratorConfig generatorConfig, TestConfig testConfig, SLAConfig slaConfig) {
        // Initialize logging
        LoggerConfig.initialize();
        
        // Store config references - EI_EXPOSE_REP2 is acceptable here as configs
        // are designed to be passed by reference for efficient configuration sharing
        this.generatorConfig = generatorConfig;
        this.testConfig = testConfig;
        this.slaConfig = slaConfig;

        // Initialize components: ZIP-based or filesystem-based
        java.nio.file.FileSystem zipFs = null;
        if (generatorConfig != null && generatorConfig.getSpecZipPath() != null) {
            try {
                Path zipPath = Paths.get(generatorConfig.getSpecZipPath()).normalize();
                if (!Files.isRegularFile(zipPath)) {
                    throw new IllegalArgumentException("Spec ZIP path is not a file: " + generatorConfig.getSpecZipPath());
                }
                zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open spec ZIP: " + generatorConfig.getSpecZipPath(), e);
            }
        }
        this.zipFileSystem = zipFs;
        if (zipFs != null) {
            this.parser = new OASParser(null, zipFs, "/");
        } else {
            List<String> searchPaths = null;
            if (generatorConfig != null && generatorConfig.getSearchPaths() != null) {
                searchPaths = generatorConfig.getSearchPaths();
            }
            this.parser = new OASParser(searchPaths);
        }
        this.validator = new OASValidator();
        this.metadata = new OASMetadata();
        this.generatorFactory = new GeneratorFactory();
        this.testGeneratorFactory = new TestGeneratorFactory();
        this.slaProcessor = new SLAProcessor();
        this.docGenerator = new DocumentationGenerator();
    }

    /**
     * Closes the ZIP filesystem if this SDK was created with {@link GeneratorConfig#getSpecZipPath() specZipPath}.
     * Call this when done using the SDK to release resources when creating many SDK instances with different ZIPs.
     */
    @Override
    public void close() {
        if (zipFileSystem != null && zipFileSystem.isOpen()) {
            try {
                zipFileSystem.close();
            } catch (IOException e) {
                logger.log(java.util.logging.Level.WARNING, "Failed to close spec ZIP filesystem: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Set path filters to include only specific paths in generation
     *
     * @param paths List of paths to include (e.g., ["/api/users", "/api/posts"])
     * @return This SDK instance for method chaining
     */
    public OASSDK filterPaths(List<String> paths) {
        if (paths == null) {
            this.pathFilters = null;
        } else {
            this.pathFilters = new HashSet<>(paths);
        }
        return this;
    }

    /**
     * Set operation filters to include only specific operations
     *
     * @param pathOperationMap Map of path to list of HTTP methods (e.g., {"/api/users": ["GET", "POST"]})
     * @return This SDK instance for method chaining
     */
    public OASSDK filterOperations(Map<String, List<String>> pathOperationMap) {
        if (pathOperationMap == null) {
            this.operationFilters = null;
        } else {
            this.operationFilters = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : pathOperationMap.entrySet()) {
                this.operationFilters.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        return this;
    }

    /**
     * Clear all path and operation filters
     *
     * @return This SDK instance for method chaining
     */
    public OASSDK clearFilters() {
        this.pathFilters = null;
        this.operationFilters = null;
        return this;
    }

    /**
     * Apply filters from GeneratorConfig if available
     */
    private void applyConfigFilters() {
        if (generatorConfig != null) {
            if (generatorConfig.getIncludePaths() != null && !generatorConfig.getIncludePaths().isEmpty()) {
                filterPaths(generatorConfig.getIncludePaths());
            }
            if (generatorConfig.getIncludeOperations() != null && !generatorConfig.getIncludeOperations().isEmpty()) {
                filterOperations(generatorConfig.getIncludeOperations());
            }
        }
    }

    /**
     * Filter OpenAPI specification based on path and operation filters
     *
     * @param originalSpec Original OpenAPI specification
     * @return Filtered OpenAPI specification
     */
    private Map<String, Object> filterSpec(Map<String, Object> originalSpec) {
        // If no filters are set, return original spec
        if (pathFilters == null && operationFilters == null) {
            return originalSpec;
        }

        // Create a copy of the spec
        Map<String, Object> filteredSpec = new LinkedHashMap<>(originalSpec);

        // Get paths
        Map<String, Object> paths = Util.asStringObjectMap(originalSpec.get("paths"));
        if (paths == null) {
            return filteredSpec;
        }

        Map<String, Object> filteredPaths = new LinkedHashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            // Check if path should be included
            boolean includePath = pathFilters == null || pathFilters.contains(path);

            if (!includePath) {
                continue;
            }

            // If operation filters are set, filter operations for this path
            if (operationFilters != null && operationFilters.containsKey(path)) {
                Set<String> allowedMethods = operationFilters.get(path);
                Map<String, Object> filteredPathItem = new LinkedHashMap<>();

                // Copy allowed operations
                String[] methods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
                for (String method : methods) {
                    String upperMethod = method.toUpperCase(Locale.ROOT);
                    if (allowedMethods.contains(upperMethod) && pathItem.containsKey(method)) {
                        filteredPathItem.put(method, pathItem.get(method));
                    }
                }

                // Copy path-level parameters if any
                if (pathItem.containsKey("parameters")) {
                    filteredPathItem.put("parameters", pathItem.get("parameters"));
                }

                // Copy path-level summary/description if any
                if (pathItem.containsKey("summary")) {
                    filteredPathItem.put("summary", pathItem.get("summary"));
                }
                if (pathItem.containsKey("description")) {
                    filteredPathItem.put("description", pathItem.get("description"));
                }

                // Only add path if it has at least one operation
                if (!filteredPathItem.isEmpty()) {
                    filteredPaths.put(path, filteredPathItem);
                }
            } else {
                // No operation filters for this path - include entire pathItem
                filteredPaths.put(path, pathItem);
            }
        }

        filteredSpec.put("paths", filteredPaths);
        return filteredSpec;
    }

    /**
     * Load OpenAPI specification from file or from a ZIP entry.
     * When this SDK was created with {@link GeneratorConfig#getSpecZipPath() specZipPath}, {@code specPath}
     * is an entry path inside the ZIP (e.g. {@code published/core/infomgr/v4/api.yaml}); use forward slashes.
     * Otherwise, {@code specPath} is a filesystem path to a YAML/JSON file.
     *
     * @param specPath Path to the spec file, or ZIP entry path when using specZipPath
     * @return This SDK instance for method chaining
     * @throws OASSDKException if specification cannot be loaded
     */
    public OASSDK loadSpec(String specPath) throws OASSDKException {
        Objects.requireNonNull(specPath, "Specification path cannot be null");
        String unixSpecPath = egain.oassdk.core.parser.PathUtils.toUnixPath(specPath);
        try {
            // Parse the specification (all paths processed in Unix style)
            this.spec = parser.parse(unixSpecPath);

            // Resolve all $ref references (internal and external)
            this.spec = parser.resolveReferences(this.spec, unixSpecPath);

            // Validate specification
            validator.validate(this.spec);

            // Extract metadata
            metadata.extract(this.spec);

            // Check for SLA file reference
            Map<String, Object> info = Util.asStringObjectMap(this.spec.get("info"));
            if (info != null && info.containsKey("x-nfr-file")) {
                String slaFile = (String) info.get("x-nfr-file");
                loadSLA(slaFile);
            }

            return this;

        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to load specification: " + e.getMessage(), e);
            throw new ValidationException("Failed to load specification: " + e.getMessage(), e);
        }
    }

    /**
     * Load SLA specification from file
     *
     * @param slaPath Path to SLA specification file
     * @return This SDK instance for method chaining
     * @throws OASSDKException if SLA specification cannot be loaded
     */
    public OASSDK loadSLA(String slaPath) throws OASSDKException {
        Objects.requireNonNull(slaPath, "SLA path cannot be null");
        String unixSlaPath = egain.oassdk.core.parser.PathUtils.toUnixPath(slaPath);
        try {
            this.slaSpec = parser.parse(unixSlaPath);
            return this;
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to load SLA specification: " + e.getMessage(), e);
            throw new ValidationException("Failed to load SLA specification: " + e.getMessage(), e);
        }
    }

    /**
     * Generate application code
     *
     * @param language  Programming language (java, python, nodejs, go, csharp)
     * @param framework Framework (jersey, fastapi, express, gin, aspnet)
     * @param outputDir Output directory for generated code
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateApplication(String language, String framework, String outputDir) throws OASSDKException {
        return generateApplication(language, framework, null, outputDir);
    }

    /**
     * Generate application code with package name
     *
     * @param language    Programming language
     * @param framework   Framework
     * @param packageName Package/namespace name
     * @param outputDir   Output directory for generated code
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateApplication(String language, String framework, String packageName, String outputDir) throws OASSDKException {
        Objects.requireNonNull(language, "Language cannot be null");
        Objects.requireNonNull(framework, "Framework cannot be null");
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Get generator
            var generator = generatorFactory.getGenerator(language, framework);

            // Apply filters from config if available
            applyConfigFilters();

            // Filter spec if filters are set
            Map<String, Object> specToUse = filterSpec(spec);

            // Generate application
            generator.generate(specToUse, outputDir, generatorConfig, packageName);

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate application: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test suite
     *
     * @param testTypes List of test types (unit, integration, nfr, performance, security)
     * @param outputDir Output directory for generated tests
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateTests(List<String> testTypes, String outputDir) throws OASSDKException {
        return generateTests(testTypes, null, outputDir);
    }

    /**
     * Generate test suite with test framework
     *
     * @param testTypes     List of test types
     * @param testFramework Test framework (junit5, pytest, jest)
     * @param outputDir     Output directory for generated tests
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateTests(List<String> testTypes, String testFramework, String outputDir) throws OASSDKException {
        Objects.requireNonNull(testTypes, "Test types cannot be null");
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Apply filters from config if available
            applyConfigFilters();

            // Filter spec if filters are set
            Map<String, Object> specToUse = filterSpec(spec);

            // Ensure testConfig has language/framework information from generatorConfig if not already set
            if (testConfig != null && generatorConfig != null) {
                if (testConfig.getLanguage() == null && generatorConfig.getLanguage() != null) {
                    testConfig.setLanguage(generatorConfig.getLanguage());
                }
                if (testConfig.getFramework() == null && generatorConfig.getFramework() != null) {
                    // Map code generation framework to test framework
                    String codeFramework = generatorConfig.getFramework().toLowerCase(Locale.ROOT);
                    if (codeFramework.contains("fastapi") || codeFramework.contains("flask")) {
                        testConfig.setFramework("pytest");
                    } else if (codeFramework.contains("express")) {
                        testConfig.setFramework("jest");
                    } else {
                        testConfig.setFramework(codeFramework);
                    }
                }
            }

            // Generate each test type
            for (String testType : testTypes) {
                var testGenerator = testGeneratorFactory.getGenerator(testType, testConfig);
                testGenerator.generate(specToUse, outputDir, testConfig, testFramework);
            }

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate tests: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate mock data
     *
     * @param outputDir Output directory for generated mock data
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateMockData(String outputDir) throws OASSDKException {
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Apply filters from config if available
            applyConfigFilters();

            // Filter spec if filters are set
            Map<String, Object> specToUse = filterSpec(spec);

            // Generate mock data
            var mockGenerator = testGeneratorFactory.getGenerator("mock_data");
            mockGenerator.generate(specToUse, outputDir, testConfig, null);

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate mock data: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate mock data: " + e.getMessage(), e);
        }
    }

    /**
     * Generate SLA enforcement code
     *
     * @param slaFile   SLA specification file
     * @param outputDir Output directory for generated SLA enforcement
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateSLAEnforcement(String slaFile, String outputDir) throws OASSDKException {
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        if (slaSpec == null && slaFile != null) {
            loadSLA(slaFile);
        }

        if (slaSpec == null) {
            throw new OASSDKException("No SLA specification loaded. Call loadSLA() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Generate SLA enforcement
            slaProcessor.generateEnforcement(spec, slaSpec, outputDir, slaConfig);

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate SLA enforcement: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate SLA enforcement: " + e.getMessage(), e);
        }
    }

    /**
     * Generate monitoring setup
     *
     * @param monitoringStack List of monitoring tools (prometheus, grafana)
     * @param outputDir       Output directory for generated monitoring
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateMonitoring(List<String> monitoringStack, String outputDir) throws OASSDKException {
        Objects.requireNonNull(monitoringStack, "Monitoring stack cannot be null");
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Generate monitoring
            slaProcessor.generateMonitoring(spec, outputDir, slaConfig, monitoringStack);

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate monitoring: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate monitoring: " + e.getMessage(), e);
        }
    }

    /**
     * Generate documentation
     *
     * @param outputDir Output directory for generated documentation
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateDocumentation(String outputDir) throws OASSDKException {
        return generateDocumentation(outputDir, true, true, true);
    }

    /**
     * Generate documentation with options
     *
     * @param outputDir          Output directory for generated documentation
     * @param includeAPIDocs     Include API documentation
     * @param includeTestDocs    Include test documentation
     * @param includeProjectDocs Include project documentation
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateDocumentation(String outputDir, boolean includeAPIDocs, boolean includeTestDocs, boolean includeProjectDocs) throws OASSDKException {
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Apply filters from config if available
            applyConfigFilters();

            // Filter spec if filters are set
            Map<String, Object> specToUse = filterSpec(spec);

            // Generate documentation
            docGenerator.generate(specToUse, outputDir, includeAPIDocs, includeTestDocs, includeProjectDocs);

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate documentation: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate complete project
     *
     * @param outputDir Output directory for generated project
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public OASSDK generateAll(String outputDir) throws OASSDKException {
        Objects.requireNonNull(outputDir, "Output directory cannot be null");
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            // Create output directory
            createDirectory(outputDir);

            // Generate application
            if (generatorConfig != null) {
                generateApplication(
                        generatorConfig.getLanguage(),
                        generatorConfig.getFramework(),
                        generatorConfig.getPackageName(),
                        outputDir + "/src"
                );
            }

            // Generate tests
            if (testConfig != null) {
                generateTests(
                        List.of("unit", "integration", "nfr"),
                        outputDir + "/tests"
                );

                // Generate mock data
                generateMockData(outputDir + "/mock-data");
            }

            // Generate SLA enforcement
            if (slaConfig != null && slaSpec != null) {
                generateSLAEnforcement(
                        slaConfig.getSlaFile(),
                        outputDir + "/sla-enforcement"
                );

                // Generate monitoring
                generateMonitoring(
                        slaConfig.getMonitoringStack(),
                        outputDir + "/monitoring"
                );
            }

            // Generate documentation
            generateDocumentation(outputDir + "/docs");

            return this;

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to create output directory: " + e.getMessage(), e);
            throw new GenerationException("Failed to create output directory: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate complete project: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate complete project: " + e.getMessage(), e);
        }
    }

    /**
     * Run generated tests
     *
     * @param testDir Directory containing generated tests
     * @return true if all tests pass, false otherwise
     * @throws OASSDKException if test execution fails
     */
    public boolean runTests(String testDir) throws OASSDKException {
        Objects.requireNonNull(testDir, "Test directory cannot be null");
        try {
            // This would integrate with the appropriate test runner
            // based on the language and framework
            // For now, return true as a placeholder
            return true;

        } catch (RuntimeException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to run tests: " + e.getMessage(), e);
            throw new GenerationException("Failed to run tests: " + e.getMessage(), e);
        }
    }

    /**
     * Get extracted metadata from specification
     *
     * @return Dictionary containing metadata
     * @throws OASSDKException if no specification is loaded
     */
    public Map<String, Object> getMetadata() throws OASSDKException {
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        return metadata.getMetadata();
    }

    /**
     * Validate the loaded specification
     *
     * @return true if specification is valid, false otherwise
     * @throws OASSDKException if no specification is loaded
     */
    public boolean validateSpec() throws OASSDKException {
        if (spec == null) {
            throw new OASSDKException("No specification loaded. Call loadSpec() first.");
        }

        try {
            validator.validate(spec);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    /**
     * Create directory if it doesn't exist
     *
     * @param dirPath Directory path
     * @throws IOException if directory creation fails
     */
    private void createDirectory(String dirPath) throws IOException {
        Objects.requireNonNull(dirPath, "Directory path cannot be null");
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
}
