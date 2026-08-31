package egain.oassdk.docs;

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;

import java.io.IOException;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Generates Markdown documentation using Flexmark library
 * <p>
 * This class replaces hardcoded Markdown strings with proper Markdown generation
 * using the Flexmark library for reliable, feature-rich Markdown processing.
 */
public class MarkdownGenerator {
    
    private static final Logger logger = LoggerConfig.getLogger(MarkdownGenerator.class);

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownGenerator() {
        // Configure Flexmark with extensions
        MutableDataSet options = new MutableDataSet();

        // Add extensions
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AnchorLinkExtension.create(),
                AutolinkExtension.create(),
                EmojiExtension.create(),
                TocExtension.create()
        ));

        // Configure table options
        options.set(TablesExtension.COLUMN_SPANS, false)
                .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
                .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
                .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true);

        // Configure TOC options
        options.set(TocExtension.TITLE, "Table of Contents")
                .set(TocExtension.DIV_CLASS, "toc")
                .set(TocExtension.LIST_CLASS, "toc-list");

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Generate Markdown documentation
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory for Markdown files
     * @param config    Markdown generation configuration
     * @throws GenerationException if generation fails
     */
    public void generateMarkdownDocs(Map<String, Object> spec, String outputDir, MarkdownConfig config) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate main API documentation
            generateAPIDocumentation(spec, outputDir, config);

            // Generate test documentation
            generateTestDocumentation(spec, outputDir, config);

            // Generate project documentation
            generateProjectDocumentation(spec, outputDir, config);

            // Generate HTML versions
            if (config.isGenerateHTML()) {
                generateHTMLVersions(outputDir);
            }

        } catch (Exception e) {
            throw new GenerationException("Failed to generate Markdown documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate main API documentation
     */
    private void generateAPIDocumentation(Map<String, Object> spec, String outputDir, MarkdownConfig config) throws IOException {
        StringBuilder markdown = new StringBuilder();

        // Front matter
        if (config.isIncludeFrontMatter()) {
            markdown.append("---\n");
            markdown.append("title: ").append(getAPITitle(spec)).append("\n");
            markdown.append("version: ").append(getAPIVersion(spec)).append("\n");
            markdown.append("description: ").append(getAPIDescription(spec)).append("\n");
            markdown.append("generated: ").append(new Date()).append("\n");
            markdown.append("---\n\n");
        }

        // API Overview
        markdown.append("# ").append(getAPITitle(spec)).append(" API Documentation\n\n");
        markdown.append("**Version:** ").append(getAPIVersion(spec)).append("\n\n");
        markdown.append(getAPIDescription(spec)).append("\n\n");

        // Table of Contents
        if (config.isIncludeTOC()) {
            markdown.append("## Table of Contents\n\n");
            markdown.append("- [Overview](#overview)\n");
            markdown.append("- [Authentication](#authentication)\n");
            markdown.append("- [Endpoints](#endpoints)\n");
            markdown.append("- [Models](#models)\n");
            markdown.append("- [Error Codes](#error-codes)\n");
            markdown.append("- [Examples](#examples)\n\n");
        }

        // Authentication
        markdown.append("## Authentication\n\n");
        generateAuthenticationSection(spec, markdown, config);

        // Endpoints
        markdown.append("## Endpoints\n\n");
        generateEndpointsSection(spec, markdown, config);

        // Models
        markdown.append("## Models\n\n");
        generateModelsSection(spec, markdown, config);

        // Error Codes
        markdown.append("## Error Codes\n\n");
        generateErrorCodesSection(spec, markdown, config);

        // Examples
        markdown.append("## Examples\n\n");
        generateExamplesSection(spec, markdown, config);

        // Write to file
        Files.write(Paths.get(outputDir, "API_DOCUMENTATION.md"), markdown.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate test documentation
     */
    private void generateTestDocumentation(Map<String, Object> spec, String outputDir, MarkdownConfig config) throws IOException {

        String markdown = "# Test Documentation\n\n" +
                "This document describes the testing strategy and implementation for the " + getAPITitle(spec) + " API.\n\n" +

                // Test Types Table
                "## Test Types\n\n" +
                "| Test Type | Purpose | Location | Framework | Coverage |\n" +
                "|-----------|---------|----------|-----------|----------|\n" +
                "| Unit Tests | Test individual components in isolation | `src/test/java/` | JUnit 5 | Controllers, Services, Models |\n" +
                "| Integration Tests | Test component interactions | `src/test/java/integration/` | Jersey Test | API endpoints, Database interactions |\n" +
                "| NFR Tests | Test non-functional requirements | `src/test/java/nfr/` | Custom + JUnit 5 | Performance, Scalability, Reliability |\n" +
                "| Performance Tests | Test system performance under load | `src/test/java/performance/` | JMeter, Gatling | Response time, Throughput, Resource usage |\n" +
                "| Security Tests | Test security vulnerabilities | `src/test/java/security/` | OWASP ZAP, Custom | Authentication, Authorization, Input validation |\n" +
                "| Schemathesis | Contract testing vs live API | `schemathesis/` (generated bundle) | Schemathesis (`st`) | OpenAPI conformance, coverage |\n\n" +

                // Running Tests
                "## Running Tests\n\n" +
                "```bash\n" +
                "# Run all tests\n" +
                "mvn test\n" +
                "# Run specific test type\n" +
                "mvn test -Dtest=*UnitTest\n" +
                "mvn test -Dtest=*IntegrationTest\n" +
                "mvn test -Dtest=*NFRTest\n" +
                "mvn test -Dtest=*PerformanceTest\n" +
                "mvn test -Dtest=*SecurityTest\n" +
                "```\n\n" +

                // Test Data
                "## Test Data\n\n" +
                "Test data is generated automatically based on the OpenAPI specification.\n" +
                "Mock data files are located in `src/test/resources/mock-data/`.\n\n" +

                // Postman Collection
                "## Postman Collection\n\n" +
                "A Postman collection is generated for manual testing:\n" +
                "- Collection file: `API.postman_collection.json`\n" +
                "- Environment file: `API-Environment.postman_environment.json`\n" +
                "- Run with Newman: `newman run API.postman_collection.json -e API-Environment.postman_environment.json`\n\n" +

                "## Schemathesis\n\n" +
                "When generated via the OAS SDK (`schemathesis` test type), the bundle includes `openapi.yaml`, `schemathesis.properties`, `run-schemathesis.sh`, and `README-schemathesis.md`. Install `st` (`pip install schemathesis`), configure `schemathesis.properties`, then run `./run-schemathesis.sh` from the bundle directory.\n\n";

        Files.write(Paths.get(outputDir, "TEST_DOCUMENTATION.md"), markdown.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate project documentation
     */
    private void generateProjectDocumentation(Map<String, Object> spec, String outputDir, MarkdownConfig config) throws IOException {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# ").append(getAPITitle(spec)).append(" Project Documentation\n\n");

        // Project Overview
        markdown.append("## Project Overview\n\n");
        markdown.append("This project is generated from an OpenAPI specification and includes:\n");
        markdown.append("- REST API implementation\n");
        markdown.append("- Comprehensive test suite\n");
        markdown.append("- SLA enforcement\n");
        markdown.append("- Monitoring and observability\n");
        markdown.append("- Documentation\n\n");

        // Architecture Diagram
        markdown.append("## Architecture\n\n");
        markdown.append("```mermaid\n");
        markdown.append("graph TB\n");
        markdown.append("    A[API Gateway] --> B[Load Balancer]\n");
        markdown.append("    B --> C[API Service]\n");
        markdown.append("    C --> D[Database]\n");
        markdown.append("    A --> E[Rate Limiting]\n");
        markdown.append("    C --> F[Monitoring]\n");
        markdown.append("```\n\n");

        // Getting Started
        markdown.append("## Getting Started\n\n");
        markdown.append("### Prerequisites\n");
        markdown.append("- Java 21 or higher\n");
        markdown.append("- Maven 3.6 or higher\n");
        markdown.append("- Docker (optional)\n\n");

        markdown.append("### Installation\n");
        markdown.append("1. Clone the repository\n");
        markdown.append("2. Run `mvn clean install`\n");
        markdown.append("3. Start the application: `mvn jersey:run`\n\n");

        // API Endpoints
        markdown.append("## API Endpoints\n\n");
        generateEndpointsList(spec, markdown);

        Files.write(Paths.get(outputDir, "PROJECT_DOCUMENTATION.md"), markdown.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate HTML versions of Markdown files
     */
    private void generateHTMLVersions(String outputDir) throws IOException {
        // Process all Markdown files
        Files.walk(Paths.get(outputDir))
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(mdFile -> {
                    try {
                        String markdown = Files.readString(mdFile);
                        Node document = parser.parse(markdown);
                        String html = renderer.render(document);

                        // Add HTML wrapper using StringBuilder to avoid String.format issues with % characters
                        Path mdFileName = mdFile.getFileName();
                        String pageTitle = mdFileName != null ? mdFileName.toString().replace(".md", "") : "doc";
                        String fullHtml = "<!DOCTYPE html>\n" +
                                "<html lang=\"en\">\n" +
                                "<head>\n" +
                                "    <meta charset=\"UTF-8\">\n" +
                                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                                "    <title>" + pageTitle + "</title>\n" +
                                "    <style>\n" +
                                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; }\n" +
                                "        h1, h2, h3, h4, h5, h6 { color: #333; }\n" +
                                "        code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }\n" +
                                "        pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }\n" +
                                "        table { border-collapse: collapse; width: 100%; }\n" +
                                "        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n" +
                                "        th { background-color: #f2f2f2; }\n" +
                                "    </style>\n" +
                                "</head>\n" +
                                "<body>\n" +
                                "    " + html + "\n" +
                                "</body>\n" +
                                "</html>\n";

                        String htmlFileName = mdFile.toString().replace(".md", ".html");
                        Files.write(Paths.get(htmlFileName), fullHtml.getBytes(StandardCharsets.UTF_8));

                    } catch (IOException e) {
                        logger.log(java.util.logging.Level.WARNING, "Error converting " + mdFile + " to HTML: " + e.getMessage(), e);
                    }
                });
    }

    /**
     * Generate authentication section
     */
    private void generateAuthenticationSection(Map<String, Object> spec, StringBuilder markdown, MarkdownConfig config) {
        markdown.append("This API uses the following authentication methods:\n\n");

        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null && components.containsKey("securitySchemes")) {
            Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));

            for (Map.Entry<String, Object> scheme : securitySchemes.entrySet()) {
                Map<String, Object> schemeDetails = Util.asStringObjectMap(scheme.getValue());
                markdown.append("### ").append(scheme.getKey()).append("\n\n");
                markdown.append("**Type:** ").append(schemeDetails.get("type")).append("\n\n");
                if (schemeDetails.containsKey("description")) {
                    markdown.append(schemeDetails.get("description")).append("\n\n");
                }
            }
        } else {
            markdown.append("No authentication required\n\n");
        }
    }

    /**
     * Generate endpoints section
     */
    private void generateEndpointsSection(Map<String, Object> spec, StringBuilder markdown, MarkdownConfig config) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Object pathValue = pathEntry.getValue();
            if (!(pathValue instanceof Map)) continue;
            Map<String, Object> pathItem = Util.asStringObjectMap(pathValue);

            if (pathItem == null) continue;

            markdown.append("### ").append(path).append("\n\n");

            for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                String method = methodEntry.getKey();
                if (method.startsWith("x-")) continue;

                Object opValue = methodEntry.getValue();
                if (!(opValue instanceof Map)) continue;
                Map<String, Object> operation = Util.asStringObjectMap(opValue);
                if (operation == null) continue;

                markdown.append("#### ").append(method.toUpperCase(Locale.ROOT)).append("\n\n");

                if (operation.containsKey("summary")) {
                    markdown.append("**Summary:** ").append(operation.get("summary")).append("\n\n");
                }

                if (operation.containsKey("description")) {
                    markdown.append(operation.get("description")).append("\n\n");
                }

                // Parameters
                if (operation.containsKey("parameters")) {
                    markdown.append("**Parameters:**\n\n");
                    // Add parameter table here
                }

                // Responses
                if (operation.containsKey("responses")) {
                    markdown.append("**Responses:**\n\n");
                    // Add response table here
                }

                markdown.append("---\n\n");
            }
        }
    }

    /**
     * Generate models section
     */
    private void generateModelsSection(Map<String, Object> spec, StringBuilder markdown, MarkdownConfig config) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null || !components.containsKey("schemas")) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            markdown.append("#### ").append(schemaName).append("\n\n");
            if (schema.containsKey("description")) {
                markdown.append(schema.get("description")).append("\n\n");
            }
        }
    }

    /**
     * Generate error codes section
     */
    private void generateErrorCodesSection(Map<String, Object> spec, StringBuilder markdown, MarkdownConfig config) {
        markdown.append("| Code | Description |\n");
        markdown.append("|------|-------------|\n");
        markdown.append("| 400 | Bad Request |\n");
        markdown.append("| 401 | Unauthorized |\n");
        markdown.append("| 403 | Forbidden |\n");
        markdown.append("| 404 | Not Found |\n");
        markdown.append("| 500 | Internal Server Error |\n\n");
    }

    /**
     * Generate examples section
     */
    private void generateExamplesSection(Map<String, Object> spec, StringBuilder markdown, MarkdownConfig config) {
        markdown.append("### cURL Example\n\n");
        markdown.append("```bash\n");
        markdown.append("curl -X GET \"https://api.example.com/users\" \\\n");
        markdown.append("  -H \"Accept: application/json\" \\\n");
        markdown.append("  -H \"Authorization: Bearer YOUR_TOKEN\"\n");
        markdown.append("```\n\n");

        markdown.append("### JavaScript Example\n\n");
        markdown.append("```javascript\n");
        markdown.append("fetch('https://api.example.com/users', {\n");
        markdown.append("  method: 'GET',\n");
        markdown.append("  headers: {\n");
        markdown.append("    'Accept': 'application/json',\n");
        markdown.append("    'Authorization': 'Bearer YOUR_TOKEN'\n");
        markdown.append("  }\n");
        markdown.append("})\n");
        markdown.append(".then(response => response.json())\n");
        markdown.append(".then(data => console.log(data));\n");
        markdown.append("```\n\n");
    }

    /**
     * Generate endpoints list
     */
    private void generateEndpointsList(Map<String, Object> spec, StringBuilder markdown) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (String path : paths.keySet()) {
            markdown.append("- ").append(path).append("\n");
        }
        markdown.append("\n");
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("version") : "1.0.0";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("description") : "Generated API";
    }

    /**
     * Markdown configuration class
     */
    public static class MarkdownConfig {
        private boolean includeFrontMatter = true;
        private boolean includeTOC = true;
        private boolean generateHTML = true;
        private boolean includeTables = true;
        private boolean includeCodeBlocks = true;
        private boolean includeEmojis = true;
        private String customCSS = "";

        // Getters and setters
        public boolean isIncludeFrontMatter() {
            return includeFrontMatter;
        }

        public void setIncludeFrontMatter(boolean includeFrontMatter) {
            this.includeFrontMatter = includeFrontMatter;
        }

        public boolean isIncludeTOC() {
            return includeTOC;
        }

        public void setIncludeTOC(boolean includeTOC) {
            this.includeTOC = includeTOC;
        }

        public boolean isGenerateHTML() {
            return generateHTML;
        }

        public void setGenerateHTML(boolean generateHTML) {
            this.generateHTML = generateHTML;
        }

        public boolean isIncludeTables() {
            return includeTables;
        }

        public void setIncludeTables(boolean includeTables) {
            this.includeTables = includeTables;
        }

        public boolean isIncludeCodeBlocks() {
            return includeCodeBlocks;
        }

        public void setIncludeCodeBlocks(boolean includeCodeBlocks) {
            this.includeCodeBlocks = includeCodeBlocks;
        }

        public boolean isIncludeEmojis() {
            return includeEmojis;
        }

        public void setIncludeEmojis(boolean includeEmojis) {
            this.includeEmojis = includeEmojis;
        }

        public String getCustomCSS() {
            return customCSS;
        }

        public void setCustomCSS(String customCSS) {
            this.customCSS = customCSS;
        }
    }
}
