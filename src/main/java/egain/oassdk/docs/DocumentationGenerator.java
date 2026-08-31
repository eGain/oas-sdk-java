package egain.oassdk.docs;

import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.docs.MarkdownGenerator.MarkdownConfig;
import egain.oassdk.docs.OpenAPISpecGenerator.OpenAPIConfig;
import egain.oassdk.docs.RedoclyConfigGenerator.RedoclyApiConfig;
import egain.oassdk.docs.RedoclyConfigGenerator.RedoclyThemeConfig;
import egain.oassdk.docs.SwaggerUIGenerator.SwaggerUIConfig;
import egain.oassdk.docs.TemplateGenerator.ProjectDocConfig;
import egain.oassdk.docs.TemplateGenerator.TestDocConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Documentation generator for Redocly and other documentation formats
 * <p>
 * This class now uses Redocly CLI and proper configuration instead of hardcoded HTML strings.
 * It generates professional, customizable documentation with themes, code samples, and proper API organization.
 */
public class DocumentationGenerator {

    private final RedoclyConfigGenerator redoclyConfigGenerator;
    private final SwaggerUIGenerator swaggerUIGenerator;
    private final MarkdownGenerator markdownGenerator;
    private final OpenAPISpecGenerator openAPISpecGenerator;
    private final TemplateGenerator templateGenerator;

    public DocumentationGenerator() {
        this.redoclyConfigGenerator = new RedoclyConfigGenerator();
        this.swaggerUIGenerator = new SwaggerUIGenerator();
        this.markdownGenerator = new MarkdownGenerator();
        this.openAPISpecGenerator = new OpenAPISpecGenerator();
        this.templateGenerator = new TemplateGenerator();
    }

    /**
     * Generate documentation
     *
     * @param spec               OpenAPI specification
     * @param outputDir          Output directory for generated documentation
     * @param includeAPIDocs     Include API documentation
     * @param includeTestDocs    Include test documentation
     * @param includeProjectDocs Include project documentation
     * @throws GenerationException if generation fails
     */
    public void generate(Map<String, Object> spec, String outputDir, boolean includeAPIDocs, boolean includeTestDocs, boolean includeProjectDocs) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDir));

            if (includeAPIDocs) {
                generateAPIDocumentation(spec, outputDir);
            }

            if (includeTestDocs) {
                generateTestDocumentation(spec, outputDir);
            }

            if (includeProjectDocs) {
                generateProjectDocumentation(spec, outputDir);
            }

            // Redocly configuration is now handled by RedoclyConfigGenerator

            // Generate build scripts
            generateBuildScripts(spec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate API documentation
     */
    private void generateAPIDocumentation(Map<String, Object> spec, String outputDir) throws IOException {
        // Generate Redocly documentation using CLI and proper configuration
        generateRedoclyDocumentation(spec, outputDir);

        // Generate Swagger UI documentation
        generateSwaggerUI(spec, outputDir);

        // Generate Markdown documentation
        generateMarkdownDocs(spec, outputDir);

        // Generate OpenAPI spec with examples
        generateEnhancedOpenAPISpec(spec, outputDir);
    }

    /**
     * Generate Redocly documentation using Redocly CLI
     * <p>
     * This method generates professional documentation using Redocly CLI instead of hardcoded HTML.
     * It creates proper configuration files and uses Redocly's build system for better maintainability.
     */
    private void generateRedoclyDocumentation(Map<String, Object> spec, String outputDir) throws IOException {
        // Create theme and API configuration
        RedoclyThemeConfig themeConfig = createDefaultThemeConfig();
        RedoclyApiConfig apiConfig = createDefaultApiConfig();

        // Generate Redocly configuration files
        try {
            redoclyConfigGenerator.generateRedoclyConfig(spec, outputDir, themeConfig, apiConfig);
        } catch (GenerationException e) {
            throw new IOException("Failed to generate Redocly configuration: " + e.getMessage(), e);
        }

        // Copy OpenAPI spec to output directory
        copyOpenAPISpec(spec, outputDir);

        // Generate documentation using Redocly CLI
        generateDocumentationWithRedoclyCLI(outputDir);

        // Generate additional documentation files
        generateDocumentationIndex(outputDir);
        generateDocumentationReadme(outputDir);
    }

    /**
     * Create default theme configuration
     */
    private RedoclyThemeConfig createDefaultThemeConfig() {
        RedoclyThemeConfig themeConfig = new RedoclyThemeConfig();
        themeConfig.setPrimaryColor("#32329f");
        themeConfig.setSecondaryColor("#6c757d");
        themeConfig.setTextColor("#333333");
        themeConfig.setBackgroundColor("#ffffff");
        themeConfig.setSidebarBackgroundColor("#f8f9fa");
        themeConfig.setSidebarTextColor("#495057");
        themeConfig.setSidebarActiveTextColor("#32329f");
        themeConfig.setCodeBackgroundColor("#f8f9fa");
        themeConfig.setCodeTextColor("#e83e8c");
        themeConfig.setFontFamily("Inter");
        themeConfig.setHeadingFontFamily("Inter");
        themeConfig.setCodeFontFamily("JetBrains Mono");
        return themeConfig;
    }

    /**
     * Create default API configuration
     */
    private RedoclyApiConfig createDefaultApiConfig() {
        RedoclyApiConfig apiConfig = new RedoclyApiConfig();
        apiConfig.setGenerateCodeSamples(true);
        apiConfig.setGenerateRequestSamples(true);
        apiConfig.setGenerateResponseSamples(true);
        apiConfig.setGenerateWebhooks(true);
        return apiConfig;
    }

    /**
     * Copy OpenAPI specification to output directory
     */
    private void copyOpenAPISpec(Map<String, Object> spec, String outputDir) throws IOException {
        // This would typically copy the OpenAPI spec file to the output directory
        // For now, we'll create a placeholder that indicates the spec should be copied
        String specPlaceholder = """
                # OpenAPI Specification
                #
                # This file should contain your OpenAPI specification.
                # Copy your openapi.yaml or openapi.json file to this location.
                #
                # The Redocly CLI will use this file to generate the documentation.
                """;
        Files.write(Paths.get(outputDir, "openapi.yaml"), specPlaceholder.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate documentation using Redocly CLI
     */
    private void generateDocumentationWithRedoclyCLI(String outputDir) throws IOException {
        // Generate build script that uses Redocly CLI
        String buildScript = """
                #!/bin/bash
                
                echo "Building documentation with Redocly CLI..."
                
                # Check if Redocly CLI is installed
                if ! command -v redocly &> /dev/null; then
                    echo "Installing Redocly CLI..."
                    npm install -g @redocly/cli@latest
                fi
                
                # Install dependencies
                if [ -f "package.json" ]; then
                    echo "Installing dependencies..."
                    npm install
                fi
                
                # Lint OpenAPI specification
                echo "Linting OpenAPI specification..."
                redocly lint openapi.yaml
                
                # Build documentation
                echo "Building documentation..."
                redocly build-docs openapi.yaml --output index.html --config redocly.yaml
                
                # Generate bundled specification
                echo "Generating bundled specification..."
                redocly bundle openapi.yaml --output bundled.yaml
                
                echo "Documentation built successfully!"
                echo "Open index.html in your browser to view the documentation."
                """;

        Files.write(Paths.get(outputDir, "build-docs.sh"), buildScript.getBytes(StandardCharsets.UTF_8));

        // Make script executable (Unix/Linux/Mac)
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "+x", Paths.get(outputDir, "build-docs.sh").toString());
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Ignore if chmod fails (e.g., on Windows)
        }
    }

    /**
     * Generate documentation index page
     */
    private void generateDocumentationIndex(String outputDir) throws IOException {
        String indexHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>API Documentation</title>
                    <link rel="stylesheet" href="custom.css">
                    <style>
                        body {
                            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            margin: 0;
                            padding: 0;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                        }
                        .container {
                            max-width: 1200px;
                            margin: 0 auto;
                            padding: 2rem;
                        }
                        .header {
                            text-align: center;
                            color: white;
                            margin-bottom: 3rem;
                        }
                        .header h1 {
                            font-size: 3rem;
                            margin-bottom: 1rem;
                            text-shadow: 0 2px 4px rgba(0,0,0,0.3);
                        }
                        .header p {
                            font-size: 1.2rem;
                            opacity: 0.9;
                        }
                        .cards {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                            gap: 2rem;
                            margin-bottom: 3rem;
                        }
                        .card {
                            background: white;
                            border-radius: 12px;
                            padding: 2rem;
                            box-shadow: 0 8px 32px rgba(0,0,0,0.1);
                            transition: transform 0.3s ease, box-shadow 0.3s ease;
                        }
                        .card:hover {
                            transform: translateY(-4px);
                            box-shadow: 0 12px 40px rgba(0,0,0,0.15);
                        }
                        .card h3 {
                            color: #32329f;
                            margin-bottom: 1rem;
                            font-size: 1.5rem;
                        }
                        .card p {
                            color: #666;
                            line-height: 1.6;
                            margin-bottom: 1.5rem;
                        }
                        .btn {
                            display: inline-block;
                            background: #32329f;
                            color: white;
                            padding: 0.75rem 1.5rem;
                            text-decoration: none;
                            border-radius: 6px;
                            font-weight: 500;
                            transition: background 0.3s ease;
                        }
                        .btn:hover {
                            background: #2a2a8a;
                        }
                        .btn-secondary {
                            background: #6c757d;
                        }
                        .btn-secondary:hover {
                            background: #5a6268;
                        }
                        .footer {
                            text-align: center;
                            color: white;
                            opacity: 0.8;
                            margin-top: 3rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>API Documentation</h1>
                            <p>Comprehensive API documentation generated by OAS SDK</p>
                        </div>
                
                        <div class="cards">
                            <div class="card">
                                <h3>📚 Interactive Documentation</h3>
                                <p>Explore the API with our interactive Redocly-powered documentation. Test endpoints, view examples, and understand the API structure.</p>
                                <a href="index.html" class="btn">View Documentation</a>
                            </div>
                
                            <div class="card">
                                <h3>🔧 API Reference</h3>
                                <p>Complete API reference with detailed endpoint descriptions, request/response schemas, and authentication requirements.</p>
                                <a href="index.html#tag/API" class="btn">API Reference</a>
                            </div>
                
                            <div class="card">
                                <h3>💻 Code Examples</h3>
                                <p>Ready-to-use code examples in multiple programming languages including cURL, JavaScript, Python, Java, and more.</p>
                                <a href="index.html#section/Code-Examples" class="btn">Code Examples</a>
                            </div>
                
                            <div class="card">
                                <h3>🚀 Getting Started</h3>
                                <p>Quick start guide to help you integrate with the API. Learn about authentication, rate limits, and best practices.</p>
                                <a href="index.html#section/Getting-Started" class="btn">Get Started</a>
                            </div>
                
                            <div class="card">
                                <h3>📋 OpenAPI Specification</h3>
                                <p>Download the complete OpenAPI specification in YAML or JSON format for use with other tools and SDKs.</p>
                                <a href="openapi.yaml" class="btn btn-secondary">Download YAML</a>
                            </div>
                
                            <div class="card">
                                <h3>🛠️ Development Tools</h3>
                                <p>Access development tools including Postman collections, test scripts, and monitoring configurations.</p>
                                <a href="README.md" class="btn btn-secondary">Development Guide</a>
                            </div>
                        </div>
                
                        <div class="footer">
                            <p>Generated by OAS SDK • Powered by Redocly</p>
                        </div>
                    </div>
                </body>
                </html>
                """;

        Files.write(Paths.get(outputDir, "index.html"), indexHtml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate documentation README
     */
    private void generateDocumentationReadme(String outputDir) throws IOException {
        String readme = """
                # API Documentation
                
                This directory contains the complete API documentation generated by the OAS SDK.
                
                ## Files Overview
                
                - `index.html` - Main documentation page with navigation
                - `openapi.yaml` - OpenAPI specification file
                - `redocly.yaml` - Redocly configuration file
                - `redocly.json` - Redocly JSON configuration
                - `custom.css` - Custom styling and themes
                - `theme/custom.css` - Additional theme customizations
                - `package.json` - Node.js dependencies for Redocly
                - `build-docs.sh` - Build script for documentation
                - `serve-docs.sh` - Development server script
                
                ## Quick Start
                
                ### View Documentation
                1. Open `index.html` in your web browser
                2. Navigate through the interactive documentation
                3. Test API endpoints using the built-in interface
                
                ### Build Documentation
                ```bash
                # Make build script executable
                chmod +x build-docs.sh
                ./build-docs.sh
                ```
                
                ### Serve Documentation (Development)
                ```bash
                # Make serve script executable
                chmod +x serve-docs.sh
                ./serve-docs.sh
                ```
                
                ### Using Redocly CLI Directly
                ```bash
                # Install Redocly CLI globally
                npm install -g @redocly/cli
                # Build documentation
                redocly build-docs openapi.yaml --output index.html
                # Serve documentation
                redocly preview-docs openapi.yaml --port 8081
                ```
                
                ## Customization
                
                ### Themes
                Edit `redocly.yaml` or `redocly.json` to customize:
                - Colors and typography
                - Layout and sidebar configuration
                - Code sample languages
                - API grouping and organization
                
                ### Styling
                Modify `custom.css` to add custom styles:
                - Custom fonts and typography
                - Brand colors and logos
                - Layout modifications
                - Interactive elements
                
                ## Features
                
                - **Interactive Documentation**: Test API endpoints directly from the browser
                - **Code Samples**: Auto-generated examples in multiple languages
                - **Responsive Design**: Works on desktop, tablet, and mobile devices
                - **Search**: Built-in search functionality
                - **Dark Mode**: Toggle between light and dark themes
                - **Export**: Download OpenAPI specifications
                - **Validation**: Built-in OpenAPI specification validation
                
                ## Troubleshooting
                
                ### Redocly CLI Not Found
                ```bash
                npm install -g @redocly/cli
                ```
                
                ### Build Errors
                1. Check that `openapi.yaml` is valid
                2. Run `redocly lint openapi.yaml` to identify issues
                3. Ensure all referenced files exist
                
                ### Styling Issues
                1. Clear browser cache
                2. Check `custom.css` for syntax errors
                3. Verify Redocly configuration files
                
                ## Support
                
                For issues with the documentation generation:
                - Check the OAS SDK documentation
                - Review Redocly documentation: https://redocly.com/docs
                - Open an issue in the OAS SDK repository
                
                ## License
                
                This documentation is generated by the OAS SDK and follows the same license terms.
                """;

        Files.write(Paths.get(outputDir, "README.md"), readme.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Swagger UI documentation using proper library integration
     */
    private void generateSwaggerUI(Map<String, Object> spec, String outputDir) throws IOException {
        try {
            // Create Swagger UI configuration
            SwaggerUIConfig config = createDefaultSwaggerUIConfig();

            // Generate Swagger UI documentation
            swaggerUIGenerator.generateSwaggerUI(spec, outputDir, config);

        } catch (GenerationException e) {
            throw new IOException("Failed to generate Swagger UI documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create default Swagger UI configuration
     */
    private SwaggerUIConfig createDefaultSwaggerUIConfig() {
        SwaggerUIConfig config = new SwaggerUIConfig();
        config.setSwaggerUIVersion("4.15.5");
        config.setDeepLinking(true);
        config.setLayout("StandaloneLayout");
        config.setTryItOutEnabled(true);
        config.setDocExpansion("list");
        config.setDefaultModelsExpandDepth(1);
        config.setDefaultModelExpandDepth(1);
        config.setDisplayRequestDuration(true);
        config.setFilter(false);
        config.setShowExtensions(false);
        config.setShowCommonExtensions(false);
        config.setTagsSorter("alpha");
        config.setOperationsSorter("alpha");
        config.setCustomCSS("""
                .swagger-ui .topbar { display: none; }
                .swagger-ui .info { margin: 20px 0; }
                .swagger-ui .scheme-container { background: #fafafa; padding: 20px; border-radius: 4px; }
                """);
        return config;
    }

    /**
     * Generate Markdown documentation using Flexmark library
     */
    private void generateMarkdownDocs(Map<String, Object> spec, String outputDir) throws IOException {
        try {
            // Create Markdown configuration
            MarkdownConfig config = createDefaultMarkdownConfig();

            // Generate Markdown documentation
            markdownGenerator.generateMarkdownDocs(spec, outputDir, config);

        } catch (GenerationException e) {
            throw new IOException("Failed to generate Markdown documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create default Markdown configuration
     */
    private MarkdownConfig createDefaultMarkdownConfig() {
        MarkdownConfig config = new MarkdownConfig();
        config.setIncludeFrontMatter(true);
        config.setIncludeTOC(true);
        config.setGenerateHTML(true);
        config.setIncludeTables(true);
        config.setIncludeCodeBlocks(true);
        config.setIncludeEmojis(true);
        config.setCustomCSS("""
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                h1, h2, h3, h4, h5, h6 { color: #333; }
                code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
                pre { background: #f4f4f4; padding: 15px; border-radius: 5px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                """);
        return config;
    }

    /**
     * Generate enhanced OpenAPI spec using proper OpenAPI libraries
     */
    private void generateEnhancedOpenAPISpec(Map<String, Object> spec, String outputDir) throws IOException {
        try {
            // Create OpenAPI configuration
            OpenAPIConfig config = createDefaultOpenAPIConfig();

            // Generate enhanced OpenAPI specification
            openAPISpecGenerator.generateEnhancedOpenAPISpec(spec, outputDir, config);

        } catch (GenerationException e) {
            throw new IOException("Failed to generate enhanced OpenAPI specification: " + e.getMessage(), e);
        }
    }

    /**
     * Create default OpenAPI configuration
     */
    private OpenAPIConfig createDefaultOpenAPIConfig() {
        OpenAPIConfig config = new OpenAPIConfig();
        config.setApiTitle("Generated API");
        config.setApiVersion("1.0.0");
        config.setApiDescription("API generated by OAS SDK");
        config.setContactName("API Team");
        config.setContactEmail("api@example.com");
        config.setContactUrl("https://example.com/contact");
        config.setLicenseName("MIT");
        config.setLicenseUrl("https://opensource.org/licenses/MIT");
        config.setTermsOfService("https://example.com/terms");
        config.setServerUrl("https://api.example.com/v1");
        config.setServerDescription("Production server");
        config.setExternalDocsUrl("https://docs.example.com");
        config.setAddExamples(true);
        config.setAddSecuritySchemes(true);
        config.setAddServerInfo(true);
        config.setAddTags(true);
        config.setAddExternalDocs(true);
        return config;
    }

    /**
     * Generate test documentation using template-based generation
     */
    private void generateTestDocumentation(Map<String, Object> spec, String outputDir) throws IOException {
        try {
            // Create test documentation configuration
            TestDocConfig config = createDefaultTestDocConfig();

            // Generate test documentation from template
            templateGenerator.generateTestDocumentation(spec, outputDir, config);

        } catch (GenerationException e) {
            throw new IOException("Failed to generate test documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create default test documentation configuration
     */
    private TestDocConfig createDefaultTestDocConfig() {
        TestDocConfig config = new TestDocConfig();
        config.setUnitTestsEnabled(true);
        config.setIntegrationTestsEnabled(true);
        config.setNfrTestsEnabled(true);
        config.setPerformanceTestsEnabled(true);
        config.setSecurityTestsEnabled(true);
        config.setTestDataLocation("src/test/resources/mock-data/");
        config.setPostmanCollection("API.postman_collection.json");
        config.setPostmanEnvironment("API-Environment.postman_environment.json");
        return config;
    }

    /**
     * Generate project documentation using template-based generation
     */
    private void generateProjectDocumentation(Map<String, Object> spec, String outputDir) throws IOException {
        try {
            // Create project documentation configuration
            ProjectDocConfig config = createDefaultProjectDocConfig();

            // Generate project documentation from template
            templateGenerator.generateProjectDocumentation(spec, outputDir, config);

        } catch (GenerationException e) {
            throw new IOException("Failed to generate project documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create default project documentation configuration
     */
    private ProjectDocConfig createDefaultProjectDocConfig() {
        ProjectDocConfig config = new ProjectDocConfig();
        config.setProjectName("Generated API Project");
        config.setProjectDescription("API project generated by OAS SDK");
        config.setJavaVersion("21");
        config.setMavenVersion("3.6");
        config.setDockerImage("api-service");
        config.setDockerPort("8080");
        config.setContactEmail("api@example.com");
        config.setLicense("MIT");
        config.setLicenseUrl("https://opensource.org/licenses/MIT");
        return config;
    }


    /**
     * Generate build scripts
     */
    private void generateBuildScripts(Map<String, Object> spec, String outputDir) throws IOException {
        // Generate serve script
        String serveScript = generateServeScript();
        Files.write(Paths.get(outputDir, "serve-docs.sh"), serveScript.getBytes(StandardCharsets.UTF_8));

        // Generate Windows batch files
        generateWindowsScripts(outputDir);
    }


    /**
     * Generate serve script
     */
    private String generateServeScript() {
        return """
                #!/bin/bash
                
                echo "Starting documentation server..."
                
                # Check if Redocly CLI is installed
                if ! command -v redocly &> /dev/null; then
                    echo "Installing Redocly CLI..."
                    npm install -g @redocly/cli@latest
                fi
                
                # Install dependencies
                if [ -f "package.json" ]; then
                    echo "Installing dependencies..."
                    npm install
                fi
                
                # Serve documentation
                echo "Starting development server..."
                redocly preview-docs openapi.yaml --port 8081 --config redocly.yaml
                
                echo "Documentation server started at http://localhost:8081"
                """;
    }

    /**
     * Generate Windows batch scripts
     */
    private void generateWindowsScripts(String outputDir) throws IOException {
        // Generate Windows build script
        String windowsBuildScript = """
                @echo off
                echo Building documentation with Redocly CLI...
                
                REM Check if Node.js is installed
                where node >nul 2>nul
                if %ERRORLEVEL% NEQ 0 (
                    echo Node.js is not installed. Please install Node.js first.
                    pause
                    exit /b 1
                )
                
                REM Check if Redocly CLI is installed
                redocly --version >nul 2>nul
                if %ERRORLEVEL% NEQ 0 (
                    echo Installing Redocly CLI...
                    npm install -g @redocly/cli@latest
                )
                
                REM Install dependencies
                if exist package.json (
                    echo Installing dependencies...
                    npm install
                )
                
                REM Lint OpenAPI specification
                echo Linting OpenAPI specification...
                redocly lint openapi.yaml
                
                REM Build documentation
                echo Building documentation...
                redocly build-docs openapi.yaml --output index.html --config redocly.yaml
                
                REM Generate bundled specification
                echo Generating bundled specification...
                redocly bundle openapi.yaml --output bundled.yaml
                
                echo Documentation built successfully!
                echo Open index.html in your browser to view the documentation.
                pause
                """;

        Files.write(Paths.get(outputDir, "build-docs.bat"), windowsBuildScript.getBytes(StandardCharsets.UTF_8));

        // Generate Windows serve script
        String windowsServeScript = """
                @echo off
                echo Starting documentation server...
                
                REM Check if Node.js is installed
                where node >nul 2>nul
                if %ERRORLEVEL% NEQ 0 (
                    echo Node.js is not installed. Please install Node.js first.
                    pause
                    exit /b 1
                )
                
                REM Check if Redocly CLI is installed
                redocly --version >nul 2>nul
                if %ERRORLEVEL% NEQ 0 (
                    echo Installing Redocly CLI...
                    npm install -g @redocly/cli@latest
                )
                
                REM Install dependencies
                if exist package.json (
                    echo Installing dependencies...
                    npm install
                )
                
                REM Serve documentation
                echo Starting development server...
                redocly preview-docs openapi.yaml --port 8081 --config redocly.yaml
                
                echo Documentation server started at http://localhost:8081
                pause
                """;

        Files.write(Paths.get(outputDir, "serve-docs.bat"), windowsServeScript.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * Helper methods
     */


}
