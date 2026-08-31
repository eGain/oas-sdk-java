package egain.oassdk.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Generates Swagger UI documentation using proper Swagger UI library integration
 * <p>
 * This class replaces hardcoded HTML strings with proper Swagger UI configuration
 * and uses the Swagger UI WebJar for reliable, maintainable documentation.
 */
public class SwaggerUIGenerator {

    private final ObjectMapper objectMapper;

    public SwaggerUIGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Generate Swagger UI documentation
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory for Swagger UI files
     * @param config    Swagger UI configuration
     * @throws GenerationException if generation fails
     */
    public void generateSwaggerUI(Map<String, Object> spec, String outputDir, SwaggerUIConfig config) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate Swagger UI HTML with proper configuration
            generateSwaggerUIHTML(spec, outputDir, config);

            // Generate Swagger UI configuration
            generateSwaggerUIConfig(outputDir, config);

            // Generate package.json for Swagger UI
            generatePackageJson(outputDir);

            // Generate build scripts
            generateBuildScripts(outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate Swagger UI documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Swagger UI HTML with proper library integration
     */
    private void generateSwaggerUIHTML(Map<String, Object> spec, String outputDir, SwaggerUIConfig config) throws IOException {
        String apiTitle = getAPITitle(spec);

        String swaggerUI = String.format("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>%s - Swagger UI</title>
                            <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@%s/swagger-ui.css" />
                            <link rel="icon" type="image/png" href="https://unpkg.com/swagger-ui-dist@%s/favicon-32x32.png" sizes="32x32" />
                            <link rel="icon" type="image/png" href="https://unpkg.com/swagger-ui-dist@%s/favicon-16x16.png" sizes="16x16" />
                            <style>
                                %s
                            </style>
                        </head>
                        <body>
                            <div id="swagger-ui"></div>
                            <script src="https://unpkg.com/swagger-ui-dist@%s/swagger-ui-bundle.js"></script>
                            <script src="https://unpkg.com/swagger-ui-dist@%s/swagger-ui-standalone-preset.js"></script>
                            <script>
                                window.onload = function() {
                                    const ui = SwaggerUIBundle({
                                        url: 'openapi.yaml',
                                        dom_id: '#swagger-ui',
                                        deepLinking: %s,
                                        presets: [
                                            SwaggerUIBundle.presets.apis,
                                            SwaggerUIStandalonePreset
                                        ],
                                        plugins: [
                                            SwaggerUIBundle.plugins.DownloadUrl
                                        ],
                                        layout: "%s",
                                        validatorUrl: %s,
                                        tryItOutEnabled: %s,
                                        requestInterceptor: %s,
                                        responseInterceptor: %s,
                                        onComplete: %s,
                                        docExpansion: "%s",
                                        defaultModelsExpandDepth: %d,
                                        defaultModelExpandDepth: %d,
                                        displayRequestDuration: %s,
                                        filter: %s,
                                        showExtensions: %s,
                                        showCommonExtensions: %s,
                                        supportedSubmitMethods: %s,
                                        tagsSorter: "%s",
                                        operationsSorter: "%s",
                                        onFailure: function(data) {
                                            console.error("Swagger UI failed to load:", data);
                                        }
                                    });
                                };
                            </script>
                        </body>
                        </html>
                        """,
                apiTitle,
                config.getSwaggerUIVersion(),
                config.getSwaggerUIVersion(),
                config.getSwaggerUIVersion(),
                config.getCustomCSS(),
                config.getSwaggerUIVersion(),
                config.getSwaggerUIVersion(),
                config.isDeepLinking(),
                config.getLayout(),
                config.getValidatorUrl() != null ? "'" + config.getValidatorUrl() + "'" : "null",
                config.isTryItOutEnabled(),
                config.getRequestInterceptor() != null ? config.getRequestInterceptor() : "null",
                config.getResponseInterceptor() != null ? config.getResponseInterceptor() : "null",
                config.getOnComplete() != null ? config.getOnComplete() : "null",
                config.getDocExpansion(),
                config.getDefaultModelsExpandDepth(),
                config.getDefaultModelExpandDepth(),
                config.isDisplayRequestDuration(),
                config.isFilter() ? "true" : "false",
                config.isShowExtensions(),
                config.isShowCommonExtensions(),
                config.getSupportedSubmitMethods(),
                config.getTagsSorter(),
                config.getOperationsSorter()
        );

        Files.write(Paths.get(outputDir, "swagger-ui.html"), swaggerUI.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Swagger UI configuration file
     */
    private void generateSwaggerUIConfig(String outputDir, SwaggerUIConfig config) throws IOException {
        ObjectNode swaggerConfig = objectMapper.createObjectNode();

        // Basic configuration
        swaggerConfig.put("url", "openapi.yaml");
        swaggerConfig.put("deepLinking", config.isDeepLinking());
        swaggerConfig.put("layout", config.getLayout());
        swaggerConfig.put("validatorUrl", config.getValidatorUrl());
        swaggerConfig.put("tryItOutEnabled", config.isTryItOutEnabled());
        swaggerConfig.put("docExpansion", config.getDocExpansion());
        swaggerConfig.put("defaultModelsExpandDepth", config.getDefaultModelsExpandDepth());
        swaggerConfig.put("defaultModelExpandDepth", config.getDefaultModelExpandDepth());
        swaggerConfig.put("displayRequestDuration", config.isDisplayRequestDuration());
        swaggerConfig.put("filter", config.isFilter());
        swaggerConfig.put("showExtensions", config.isShowExtensions());
        swaggerConfig.put("showCommonExtensions", config.isShowCommonExtensions());
        swaggerConfig.put("tagsSorter", config.getTagsSorter());
        swaggerConfig.put("operationsSorter", config.getOperationsSorter());

        // Supported submit methods
        com.fasterxml.jackson.databind.node.ArrayNode supportedMethods = objectMapper.createArrayNode();
        for (String method : config.getSupportedSubmitMethodsArray()) {
            supportedMethods.add(method);
        }
        swaggerConfig.set("supportedSubmitMethods", supportedMethods);

        // Custom CSS
        if (config.getCustomCSS() != null && !config.getCustomCSS().isEmpty()) {
            swaggerConfig.put("customCSS", config.getCustomCSS());
        }

        Files.write(Paths.get(outputDir, "swagger-ui-config.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(swaggerConfig));
    }

    /**
     * Generate package.json for Swagger UI
     */
    private void generatePackageJson(String outputDir) throws IOException {
        String packageJson = """
                {
                  "name": "swagger-ui-documentation",
                  "version": "1.0.0",
                  "description": "Swagger UI documentation generated by OAS SDK",
                  "main": "swagger-ui.html",
                  "scripts": {
                    "serve": "http-server . -p 8082 -o swagger-ui.html",
                    "build": "echo 'Swagger UI documentation is ready!'"
                  },
                  "dependencies": {
                    "http-server": "^14.1.1"
                  },
                  "devDependencies": {
                    "swagger-ui-dist": "^4.15.5"
                  },
                  "keywords": [
                    "swagger",
                    "openapi",
                    "api",
                    "documentation",
                    "oas-sdk"
                  ],
                  "author": "OAS SDK Team",
                  "license": "MIT"
                }
                """;

        Files.write(Paths.get(outputDir, "package.json"), packageJson.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate build scripts
     */
    private void generateBuildScripts(String outputDir) throws IOException {
        // Unix/Linux/Mac build script
        String buildScript = """
                #!/bin/bash
                
                echo "Building Swagger UI documentation..."
                
                # Install dependencies
                if [ -f "package.json" ]; then
                    echo "Installing dependencies..."
                    npm install
                fi
                
                echo "Swagger UI documentation is ready!"
                echo "Open swagger-ui.html in your browser to view the documentation."
                """;

        Files.write(Paths.get(outputDir, "build-swagger-ui.sh"), buildScript.getBytes(StandardCharsets.UTF_8));

        // Make script executable
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "+x", Paths.get(outputDir, "build-swagger-ui.sh").toString());
            Process process = pb.start();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Ignore if chmod fails (e.g., on Windows)
        }

        // Windows build script
        String windowsBuildScript = """
                @echo off
                echo Building Swagger UI documentation...
                
                REM Install dependencies
                if exist package.json (
                    echo Installing dependencies...
                    npm install
                )
                
                echo Swagger UI documentation is ready!
                echo Open swagger-ui.html in your browser to view the documentation.
                pause
                """;

        Files.write(Paths.get(outputDir, "build-swagger-ui.bat"), windowsBuildScript.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = egain.oassdk.Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }


    /**
     * Swagger UI configuration class
     */
    public static class SwaggerUIConfig {
        private String swaggerUIVersion = "4.15.5";
        private boolean deepLinking = true;
        private String layout = "StandaloneLayout";
        private String validatorUrl = null;
        private boolean tryItOutEnabled = true;
        private String docExpansion = "list";
        private int defaultModelsExpandDepth = 1;
        private int defaultModelExpandDepth = 1;
        private boolean displayRequestDuration = true;
        private boolean filter = false;
        private boolean showExtensions = false;
        private boolean showCommonExtensions = false;
        private String[] supportedSubmitMethods = {"get", "post", "put", "delete", "patch"};
        private String tagsSorter = "alpha";
        private String operationsSorter = "alpha";
        private String customCSS = "";
        private String requestInterceptor = null;
        private String responseInterceptor = null;
        private String onComplete = null;

        // Getters and setters
        public String getSwaggerUIVersion() {
            return swaggerUIVersion;
        }

        public void setSwaggerUIVersion(String swaggerUIVersion) {
            this.swaggerUIVersion = swaggerUIVersion;
        }

        public boolean isDeepLinking() {
            return deepLinking;
        }

        public void setDeepLinking(boolean deepLinking) {
            this.deepLinking = deepLinking;
        }

        public String getLayout() {
            return layout;
        }

        public void setLayout(String layout) {
            this.layout = layout;
        }

        public String getValidatorUrl() {
            return validatorUrl;
        }

        public void setValidatorUrl(String validatorUrl) {
            this.validatorUrl = validatorUrl;
        }

        public boolean isTryItOutEnabled() {
            return tryItOutEnabled;
        }

        public void setTryItOutEnabled(boolean tryItOutEnabled) {
            this.tryItOutEnabled = tryItOutEnabled;
        }

        public String getDocExpansion() {
            return docExpansion;
        }

        public void setDocExpansion(String docExpansion) {
            this.docExpansion = docExpansion;
        }

        public int getDefaultModelsExpandDepth() {
            return defaultModelsExpandDepth;
        }

        public void setDefaultModelsExpandDepth(int defaultModelsExpandDepth) {
            this.defaultModelsExpandDepth = defaultModelsExpandDepth;
        }

        public int getDefaultModelExpandDepth() {
            return defaultModelExpandDepth;
        }

        public void setDefaultModelExpandDepth(int defaultModelExpandDepth) {
            this.defaultModelExpandDepth = defaultModelExpandDepth;
        }

        public boolean isDisplayRequestDuration() {
            return displayRequestDuration;
        }

        public void setDisplayRequestDuration(boolean displayRequestDuration) {
            this.displayRequestDuration = displayRequestDuration;
        }

        public boolean isFilter() {
            return filter;
        }

        public void setFilter(boolean filter) {
            this.filter = filter;
        }

        public boolean isShowExtensions() {
            return showExtensions;
        }

        public void setShowExtensions(boolean showExtensions) {
            this.showExtensions = showExtensions;
        }

        public boolean isShowCommonExtensions() {
            return showCommonExtensions;
        }

        public void setShowCommonExtensions(boolean showCommonExtensions) {
            this.showCommonExtensions = showCommonExtensions;
        }

        public String[] getSupportedSubmitMethodsArray() {
            return supportedSubmitMethods != null ? supportedSubmitMethods.clone() : new String[0];
        }

        public void setSupportedSubmitMethods(String[] supportedSubmitMethods) {
            this.supportedSubmitMethods = supportedSubmitMethods != null ? supportedSubmitMethods.clone() : null;
        }

        public String getSupportedSubmitMethods() {
            return "[" + String.join(",", supportedSubmitMethods) + "]";
        }

        public String getTagsSorter() {
            return tagsSorter;
        }

        public void setTagsSorter(String tagsSorter) {
            this.tagsSorter = tagsSorter;
        }

        public String getOperationsSorter() {
            return operationsSorter;
        }

        public void setOperationsSorter(String operationsSorter) {
            this.operationsSorter = operationsSorter;
        }

        public String getCustomCSS() {
            return customCSS;
        }

        public void setCustomCSS(String customCSS) {
            this.customCSS = customCSS;
        }

        public String getRequestInterceptor() {
            return requestInterceptor;
        }

        public void setRequestInterceptor(String requestInterceptor) {
            this.requestInterceptor = requestInterceptor;
        }

        public String getResponseInterceptor() {
            return responseInterceptor;
        }

        public void setResponseInterceptor(String responseInterceptor) {
            this.responseInterceptor = responseInterceptor;
        }

        public String getOnComplete() {
            return onComplete;
        }

        public void setOnComplete(String onComplete) {
            this.onComplete = onComplete;
        }
    }
}
