package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared state holder for all Jersey sub-generators.
 * Encapsulates the spec, output directory, config, package name, and common utility methods
 * that are used across multiple generation phases.
 */
public final class JerseyGenerationContext {

    final Map<String, Object> spec;
    final String outputDir;
    final GeneratorConfig config;
    final String packageName;
    final boolean modelsOnly;
    final boolean useJakarta;
    final Map<Object, String> inlinedSchemas = new IdentityHashMap<>();

    /** javax/jakarta namespace prefix — "javax" or "jakarta" depending on config. */
    final String wsNs;          // "javax.ws.rs" or "jakarta.ws.rs"
    final String validationNs;  // "javax.validation" or "jakarta.validation"
    final String xmlBindNs;     // "javax.xml.bind" or "jakarta.xml.bind"
    final String injectNs;      // "javax.inject" or "jakarta.inject"
    final String servletNs;     // "javax.servlet" or "jakarta.servlet"

    public JerseyGenerationContext(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) {
        this.spec = spec;
        this.outputDir = outputDir;
        this.config = config;
        this.packageName = packageName;
        this.modelsOnly = config != null && config.isModelsOnly();
        this.useJakarta = config != null && config.isUseJakartaNamespace();

        String base = useJakarta ? "jakarta" : "javax";
        this.wsNs = base + ".ws.rs";
        this.validationNs = base + ".validation";
        this.xmlBindNs = base + ".xml.bind";
        this.injectNs = base + ".inject";
        this.servletNs = base + ".servlet";
    }

    /**
     * Mutable map of inlined anonymous schema objects to generated simple class names.
     */
    public Map<Object, String> getInlinedSchemas() {
        return inlinedSchemas;
    }

    public String getXmlBindNs() {
        return xmlBindNs;
    }

    public String getWsNs() {
        return wsNs;
    }

    /**
     * Read a fixed, spec-independent runtime class from the classpath (UTF-8). These classes are
     * stored verbatim under {@code src/main/resources/runtime/jersey} and copied into the generated
     * output as-is, rather than emitted from inline text blocks.
     */
    static String readRuntimeResource(String resourcePath) throws IOException {
        try (InputStream in = JerseyGenerationContext.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing runtime resource on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Write content to a file, creating parent directories as needed.
     */
    public static void writeFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content);
    }

    /**
     * Extract API title from spec info block.
     */
    static String getAPITitle(Map<String, Object> spec) {
        if (spec == null) return "API";
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    /**
     * Extract API description from spec info block.
     */
    static String getAPIDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("description") : "Generated API";
    }

    /**
     * Extract API version from spec info block.
     */
    static String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("version") : "1.0.0";
    }

    /**
     * Returns the configured package name, or a default if none is set.
     */
    String getPackageOrDefault() {
        return packageName != null ? packageName : "com.example.api";
    }

    /**
     * Returns the package name as a file-system path (dots replaced with slashes).
     */
    String getPackagePath() {
        return getPackageOrDefault().replace(".", "/");
    }

    /**
     * Returns true when generating for standalone (open-source) mode, where proprietary
     * eGain platform classes (JAXBBean, Actor, CallerContext, etc.) are not available and
     * must be emitted as local stubs instead. Controlled by the "standaloneMode" additional
     * property in GeneratorConfig.
     */
    boolean isStandaloneMode() {
        return config != null && config.getAdditionalProperties() != null
                && "true".equals(String.valueOf(config.getAdditionalProperties().get("standaloneMode")));
    }

    /**
     * Helper to check if observability generation is enabled.
     */
    boolean isObservabilityEnabled() {
        return config != null && config.getObservabilityConfig() != null && config.getObservabilityConfig().isEnabled();
    }

    /**
     * Extract base path from server URL (path portion after domain).
     * Examples:
     * "https://api.example.com/knowledge/contentmgr/v4" -> "/knowledge/contentmgr/v4"
     * "http://localhost:8080" -> ""
     */
    static String extractServerBasePath(Map<String, Object> spec) {
        if (spec == null || !spec.containsKey("servers")) {
            return null;
        }

        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        // Use the first server URL
        Map<String, Object> firstServer = servers.getFirst();
        if (firstServer == null) {
            return null;
        }

        String serverUrl = (String) firstServer.get("url");
        if (serverUrl == null || serverUrl.isEmpty()) {
            return null;
        }

        // Check if URL contains template variables (e.g., ${API_DOMAIN})
        boolean hasTemplateVars = serverUrl.contains("${") || serverUrl.contains("{{");

        if (hasTemplateVars) {
            // For URLs with template variables, extract path manually
            // Pattern: https://${VAR}/path or https://{{VAR}}/path
            // Extract everything after the third slash
            int thirdSlash = -1;
            int slashCount = 0;
            for (int i = 0; i < serverUrl.length(); i++) {
                if (serverUrl.charAt(i) == '/') {
                    slashCount++;
                    if (slashCount == 3) {
                        thirdSlash = i;
                        break;
                    }
                }
            }
            if (thirdSlash >= 0 && thirdSlash < serverUrl.length() - 1) {
                String path = serverUrl.substring(thirdSlash);
                // Remove trailing slash if present
                if (path.endsWith("/") && path.length() > 1) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            }
            return null;
        }

        try {
            URI uri = URI.create(serverUrl);   // validates syntax
            java.net.URL url = uri.toURL();
            String path = url.getPath();

            // Remove trailing slash if present
            if (path != null && path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }

            return path != null && !path.isEmpty() ? path : null;
        } catch (java.net.MalformedURLException | IllegalArgumentException e) {
            // If URL parsing fails, try to extract path manually
            // Look for path after the domain (after third slash)
            int thirdSlash = -1;
            int slashCount = 0;
            for (int i = 0; i < serverUrl.length(); i++) {
                if (serverUrl.charAt(i) == '/') {
                    slashCount++;
                    if (slashCount == 3) {
                        thirdSlash = i;
                        break;
                    }
                }
            }

            if (thirdSlash != -1 && thirdSlash < serverUrl.length() - 1) {
                String path = serverUrl.substring(thirdSlash);
                if (path.endsWith("/") && path.length() > 1) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            }

            return null;
        }
    }
}
