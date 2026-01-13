package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.IOException;
import java.util.logging.Logger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Jersey (JAX-RS) code generator
 */
public class JerseyGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(JerseyGenerator.class);

    private GeneratorConfig config;
    // Map to store in-lined schemas: schema object -> generated model name
    private final Map<Object, String> inlinedSchemas = new java.util.IdentityHashMap<>();

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            // Create directory structure
            createDirectoryStructure(outputDir, packageName);

            // Generate main application class
            generateMainApplicationClass(spec, outputDir, packageName);

            // Collect in-lined schemas from responses before generating models
            collectInlinedSchemas(spec);

            // Generate resources (controllers in Jersey terminology)
            generateResources(spec, outputDir, packageName);

            // Generate models (including in-lined schemas)
            generateModels(spec, outputDir, packageName);

            // Generate services
            generateServices(outputDir, packageName);

            // Generate configuration
            generateConfiguration(outputDir, packageName);

            // Generate exception mappers
            generateExceptionMappers(outputDir, packageName);

            // Generate build files
            generateBuildFiles(spec, outputDir, packageName);

            // Generate query parameter validators
            generateQueryParamValidators(spec, outputDir);

            // Generate Validation classes automatically
            generateValidationClasses(outputDir);

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Jersey application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Jersey application: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "Jersey Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public String getFramework() {
        return "jersey";
    }

    @Override
    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    @Override
    public GeneratorConfig getConfig() {
        return this.config;
    }

    /**
     * Create directory structure
     */
    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String packagePath = packageName != null ? packageName.replace(".", "/") : "com/example/api";

        String[] directories = {
                outputDir + "/src/main/java/" + packagePath,
                outputDir + "/src/main/java/" + packagePath + "/resources",
                outputDir + "/src/main/java/" + packagePath + "/model",
                outputDir + "/src/main/java/" + packagePath + "/service",
                outputDir + "/src/main/java/" + packagePath + "/config",
                outputDir + "/src/main/java/" + packagePath + "/exception",
                outputDir + "/src/main/java/egain/ws/oas/gen",  // Validation package directory
                outputDir + "/src/main/java/egain/ws/oas/validation",  // Validation package directory (lowercase)
                outputDir + "/src/main/resources",
                outputDir + "/src/test/java/" + packagePath,
                outputDir + "/src/test/java/" + packagePath + "/resources",
                outputDir + "/src/test/java/" + packagePath + "/service"
        };

        for (String dir : directories) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    /**
     * Generate main application class
     */
    private void generateMainApplicationClass(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String className = getAPITitle(spec).replaceAll("[^a-zA-Z0-9]", "") + "Application";

        String content = String.format("""
                package %s;
                
                import org.glassfish.grizzly.http.server.HttpServer;
                import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
                import org.glassfish.jersey.server.ResourceConfig;
                import jakarta.ws.rs.ApplicationPath;
                import jakarta.ws.rs.core.Application;
                import jakarta.ws.rs.ext.ContextResolver;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;
                import java.net.URI;
                import java.util.HashSet;
                import java.util.Set;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
                import org.glassfish.jersey.jackson.JacksonFeature;
                
                @ApplicationPath("/api")
                public class %s extends ResourceConfig {
                
                    public %s() {
                        // Register packages containing JAX-RS resources
                        packages("%s.resources");
                
                        // Register Jackson for JSON with JSR310 support
                        register(JacksonFeature.class);
                        register(ObjectMapperContextResolver.class);
                
                        // Register exception mappers
                        register(egain.oassdk.exception.GenericExceptionMapper.class);
                    }
                
                    @Provider
                    public static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
                        private final ObjectMapper objectMapper;
                
                        public ObjectMapperContextResolver() {
                            this.objectMapper = new ObjectMapper();
                            this.objectMapper.registerModule(new JavaTimeModule());
                        }
                
                        @Override
                        public ObjectMapper getContext(Class<?> type) {
                            return objectMapper;
                        }
                    }
                
                    public static HttpServer startServer() {
                        final String baseUri = "http://localhost:8080/";
                        final ResourceConfig config = new %s();
                        return GrizzlyHttpServerFactory.createHttpServer(URI.create(baseUri), config);
                    }
                
                    public static void main(String[] args) throws IOException {
                        final HttpServer server = startServer();
                        logger.info("Jersey app started with endpoints available at http://localhost:8080/api/");
                        logger.info("Hit Ctrl-C to stop it...");
                        System.in.read();
                        server.shutdown();
                    }
                }
                """, packagePath, className, className, packagePath, className);

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/" + className + ".java", content);
    }

    /**
     * Generate resources (REST endpoints)
     * Groups paths by parent path and generates one resource per parent path
     */
    private void generateResources(Map<String, Object> spec, String outputDir, String packageName) throws IOException, GenerationException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        String packagePath = packageName != null ? packageName : "com.example.api";

        // Group paths by parent path (first segment)
        Map<String, List<PathOperation>> pathGroups = new LinkedHashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Extract parent path (first segment)
            String parentPath = extractParentPath(path);

            // Get or create the list for this parent path
            List<PathOperation> operations = pathGroups.computeIfAbsent(parentPath, k -> new ArrayList<>());

            // Add all operations from this path to the parent path group
            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation != null) {
                        operations.add(new PathOperation(path, method, operation));
                    }
                }
            }
        }

        // Generate one resource per parent path
        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();

            try {
                generateResourceForParentPath(parentPath, operations, outputDir, packagePath, spec);
            } catch (GenerationException e) {
                throw new GenerationException("Failed to generate resource for path " + parentPath + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Extract parent path (first segment) from a full path
     * Examples:
     * "/users" -> "/users"
     * "/users/{id}" -> "/users"
     * "/users/{id}/posts" -> "/users"
     * "/api/v1/users" -> "/api"
     */
    private String extractParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }

        // Remove leading slash if present
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        // Find the first segment
        int firstSlash = normalizedPath.indexOf('/');
        if (firstSlash == -1) {
            // Single segment path
            return "/" + normalizedPath;
        }

        // Return first segment with leading slash
        return "/" + normalizedPath.substring(0, firstSlash);
    }

    /**
     * Extract API path with version from parent path, operations, and server URL
     * Looks for version patterns like /v1, /v2, /v4 in the server URL or paths
     * Examples:
     * parentPath="/prompts", server URL="/knowledge/contentmgr/v4"
     * -> "/knowledge/contentmgr/v4/prompts"
     * parentPath="/articles", no version -> "/articles"
     */
    private String extractApiPathWithVersion(String parentPath, List<PathOperation> operations, Map<String, Object> spec) {
        String serverBasePath = extractServerBasePath(spec);

        String normalizedParent = parentPath.startsWith("/") ? parentPath : "/" + parentPath;
        if (serverBasePath != null && !serverBasePath.isEmpty()) {
            // Server URL has a base path, combine it with parent path
            if (serverBasePath.endsWith("/")) {
                return serverBasePath + normalizedParent.substring(1);
            } else {
                return serverBasePath + normalizedParent;
            }
        }

        // Fallback: Look for version pattern in the first operation's path
        if (operations != null && !operations.isEmpty()) {
            String firstPath = operations.getFirst().path;
            if (firstPath != null) {
                // Check for version pattern like /v1, /v2, /v4, etc.
                java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("(/v\\d+)");
                java.util.regex.Matcher matcher = versionPattern.matcher(firstPath);

                if (matcher.find()) {
                    // Extract the path up to and including the version
                    int versionEnd = matcher.end();
                    String basePath = firstPath.substring(0, versionEnd);

                    // Find where the parent path starts in the full path
                    int parentIndex = firstPath.indexOf(normalizedParent, versionEnd);

                    if (parentIndex != -1) {
                        // Include the parent path in the result
                        int parentEnd = parentIndex + normalizedParent.length();
                        return firstPath.substring(0, parentEnd);
                    } else {
                        // Parent path not found after version, append it
                        if (basePath.endsWith("/")) {
                            return basePath + normalizedParent.substring(1);
                        } else {
                            return basePath + normalizedParent;
                        }
                    }
                }
            }
        }

        // No version found, return parent path as is
        return parentPath;
    }

    /**
     * Extract base path from server URL (path portion after domain)
     * Examples:
     * "<a href="https://api.example.com/knowledge/contentmgr/v4">...</a>" -> "/knowledge/contentmgr/v4"
     * "<a href="http://localhost:8080">...</a>" -> ""
     */
    private String extractServerBasePath(Map<String, Object> spec) {
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

    /**
     * Extract security information from operations and generate @Actor annotation
     */
    private String generateActorAnnotation(List<PathOperation> operations) {
        Set<String> actorTypes = new HashSet<>();
        Set<String> scopes = new HashSet<>();

        // Map security scheme names to ActorType enum values
        Map<String, String> securitySchemeToActorType = new LinkedHashMap<>();
        securitySchemeToActorType.put("oAuthUser", "USER");
        securitySchemeToActorType.put("oAuthCustomer", "CUSTOMER");
        securitySchemeToActorType.put("oAuthAnonymousCustomer", "ANONYMOUS_CUSTOMER");
        securitySchemeToActorType.put("oAuthClient", "CLIENT_APP");

        // Extract security information from all operations
        for (PathOperation pathOp : operations) {
            Map<String, Object> operation = pathOp.operation;
            if (operation != null && operation.containsKey("security")) {
                List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));
                if (securityList != null) {
                    for (Map<String, Object> securityMap : securityList) {
                        if (securityMap != null) {
                            for (Map.Entry<String, Object> entry : securityMap.entrySet()) {
                                String schemeName = entry.getKey();
                                String actorType = securitySchemeToActorType.get(schemeName);
                                if (actorType != null) {
                                    actorTypes.add(actorType);
                                }

                                // Extract scopes
                                if (entry.getValue() instanceof List<?> scopeList) {
                                    for (Object scope : scopeList) {
                                        if (scope instanceof String scopeStr) {
                                            // Remove ${SCOPE_PREFIX} if present and convert to enum format
                                            scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
                                            // Convert scope to enum format: knowledge.contentmgr.read -> KNOWLEDGE_CONTENTMGR_READ
                                            String enumScope = convertScopeToEnum(scopeStr);
                                            if (enumScope != null && !enumScope.isEmpty()) {
                                                scopes.add(enumScope);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // If no security found, return default @Actor
        if (actorTypes.isEmpty() && scopes.isEmpty()) {
            return "@Actor\n";
        }

        // Build @Actor annotation
        StringBuilder actorAnnotation = new StringBuilder("@Actor(type = { ");

        // Add actor types
        if (!actorTypes.isEmpty()) {
            List<String> sortedActorTypes = new ArrayList<>(actorTypes);
            java.util.Collections.sort(sortedActorTypes);
            for (int i = 0; i < sortedActorTypes.size(); i++) {
                if (i > 0) actorAnnotation.append(", ");
                actorAnnotation.append("ActorType.").append(sortedActorTypes.get(i));
            }
        } else {
            // Default to CLIENT_APP if no actor types found
            actorAnnotation.append("ActorType.CLIENT_APP");
        }

        actorAnnotation.append(" }, scope = {");

        // Add scopes
        if (!scopes.isEmpty()) {
            List<String> sortedScopes = new ArrayList<>(scopes);
            java.util.Collections.sort(sortedScopes);
            actorAnnotation.append("\n");
            for (int i = 0; i < sortedScopes.size(); i++) {
                if (i > 0) actorAnnotation.append(",\n");
                actorAnnotation.append("\t\tOAuthScope.").append(sortedScopes.get(i));
            }
            actorAnnotation.append("\n\t");
        }

        actorAnnotation.append("})\n");

        return actorAnnotation.toString();
    }

    /**
     * Convert scope string to enum format
     * Examples:
     * knowledge.contentmgr.read -> KNOWLEDGE_CONTENTMGR_READ
     * knowledge.portalmgr.manage -> KNOWLEDGE_PORTALMGR_MANAGE
     */
    private String convertScopeToEnum(String scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }

        // Replace dots and hyphens with underscores, convert to uppercase
        String enumScope = scope.replace(".", "_").replace("-", "_").toUpperCase(Locale.ROOT);

        // Remove any remaining special characters that aren't valid in enum names
        enumScope = enumScope.replaceAll("[^A-Z0-9_]", "");

        return enumScope;
    }

    /**
     * Generate resource for a parent path with all its operations
     */
    private void generateResourceForParentPath(String parentPath, List<PathOperation> operations,
                                               String outputDir, String packagePath, Map<String, Object> spec) throws IOException, GenerationException {
        String resourceName = generateResourceName(parentPath);

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".resources;\n\n");
        content.append("import jakarta.ws.rs.*;\n");
        content.append("import jakarta.ws.rs.core.MediaType;\n");
        content.append("import jakarta.ws.rs.core.Response;\n");
        content.append("import ").append(packagePath).append(".service.*;\n");
        content.append("import ").append(packagePath).append(".model.*;\n");
        content.append("import egain.framework.Actor;\n");
        content.append("import egain.framework.ActorType;\n");
        content.append("import egain.framework.OAuthScope;\n\n");

        // Extract API version from path and construct @Path
        String apiPath = extractApiPathWithVersion(parentPath, operations, spec);
        content.append("@Path(\"").append(apiPath).append("\")\n");
        content.append("@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
        content.append("@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");

        // Generate @Actor annotation with security information
        content.append(generateActorAnnotation(operations));

        content.append("public class ").append(resourceName).append(" {\n\n");

        // Generate methods for each operation
        for (PathOperation pathOp : operations) {
            String relativePath = getRelativePath(parentPath, pathOp.path);
            try {
                generateResourceMethod(pathOp.method, pathOp.operation, relativePath, content);
            } catch (GenerationException e) {
                throw new GenerationException("Failed to generate resource method for " + pathOp.method + " " + pathOp.path + ": " + e.getMessage(), e);
            }
        }

        content.append("}\n");

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/resources/" + resourceName + ".java", content.toString());
    }

    /**
     * Get relative path from parent path to full path
     * Examples:
     * parentPath="/users", fullPath="/users" -> ""
     * parentPath="/users", fullPath="/users/{id}" -> "/{id}"
     * parentPath="/users", fullPath="/users/{id}/posts" -> "/{id}/posts"
     */
    private String getRelativePath(String parentPath, String fullPath) {
        if (fullPath.equals(parentPath)) {
            return "";
        }

        // Remove leading slash from parent path for comparison
        String normalizedParent = parentPath.startsWith("/") ? parentPath.substring(1) : parentPath;
        String normalizedFull = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;

        if (normalizedFull.startsWith(normalizedParent + "/")) {
            return "/" + normalizedFull.substring(normalizedParent.length() + 1);
        }

        // Fallback: return the full path if parent doesn't match
        return fullPath;
    }

    /**
     * Helper class to store path and operation information
     */
    private static class PathOperation {
        String path;
        String method;
        Map<String, Object> operation;

        PathOperation(String path, String method, Map<String, Object> operation) {
            this.path = path;
            this.method = method;
            this.operation = operation;
        }
    }

    /**
     * Generate resource method
     */
    private void generateResourceMethod(String method, Map<String, Object> operation, String relativePath, StringBuilder content) throws GenerationException {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        // Get HTTP method annotation
        String httpMethod = method.toUpperCase(Locale.ROOT);

        // Add HTTP method annotation
        content.append("    @").append(httpMethod).append("\n");

        // Add path annotation if relative path is not empty
        if (relativePath != null && !relativePath.isEmpty()) {
            content.append("    @Path(\"").append(relativePath).append("\")\n");
        }

        // Extract parameters and generate method signature
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> parameterList = new ArrayList<>();

        // Handle request body for POST/PUT/PATCH
        if ((method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch"))) {
            Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
            if (requestBody != null) {
                // For simplicity, assume JSON request body
                parameterList.add("Object requestBody");
            }
        }

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                // Skip header parameters as they are handled by the framework
                if ("header".equals(in)) {
                    continue;
                }

                if (name != null && in != null && schema != null) {
                    // Validate and sanitize parameter name for Java
                    String sanitizedName = sanitizeParameterName(name);
                    if (sanitizedName == null) {
                        throw new GenerationException("The endpoint request param contains the invalid variable name: " + name +
                                " in path: " + relativePath + ". Parameter names must be valid Java identifiers.");
                    }

                    String javaType = getJavaType(schema);
                    String annotation = getParameterAnnotation(in);

                    // Build parameter string with parameter annotation (validation annotations removed)
                    String paramBuilder = annotation + "(\"" + name + "\") " +
                            javaType + " " + sanitizedName;

                    parameterList.add(paramBuilder);
                }
            }
        }

        // Generate method signature
        String methodName = operationId != null ? operationId : method;
        content.append("    public Response ").append(methodName).append("(");
        if (!parameterList.isEmpty()) {
            // Join parameters, handling multi-line annotations
            for (int i = 0; i < parameterList.size(); i++) {
                if (i > 0) {
                    content.append(",\n            ");
                } else {
                    content.append("\n            ");
                }
                content.append(parameterList.get(i));
            }
            content.append("\n        ");
        }
        content.append(") {\n");
        content.append("        // Implementation placeholder for ").append(summary != null ? summary : method).append("\n");
        content.append("        // Replace this with actual business logic implementation\n");
        content.append("        return Response.ok().build();\n");
        content.append("    }\n\n");
    }

    /**
     * Get parameter annotation based on parameter location
     */
    private String getParameterAnnotation(String in) {
        return switch (in.toLowerCase(Locale.ROOT)) {
            case "path" -> "@PathParam";
            case "header" -> "@HeaderParam";
            default -> "@QueryParam";
        };
    }

    /**
     * Collect in-lined schemas from response bodies and assign names to them
     */
    private void collectInlinedSchemas(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }

        int schemaCounter = 1;

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    // Collect from responses
                    if (operation.containsKey("responses")) {
                        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
                        if (responses != null) {
                            for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                                //String responseCode = responseEntry.getKey();
                                Object responseObj = responseEntry.getValue();

                                Map<String, Object> response = Util.asStringObjectMap(responseObj);
                                if (response != null && response.containsKey("content")) {
                                    Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                    if (content != null) {
                                        for (Object mediaTypeObj : content.values()) {
                                            Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                            if (mediaType != null) {
                                                Object schemaObj = mediaType.get("schema");

                                                Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
                                                if (schema != null) {
                                                    // Check if it's an in-lined schema (not a $ref)
                                                    if (!schema.containsKey("$ref")) {
                                                        // Check if it's an object type with properties
                                                        String type = (String) schema.get("type");
                                                        if ("object".equals(type) && schema.containsKey("properties")) {
                                                            // Generate a name based on operation ID and response code
                                                            String operationId = (String) operation.get("operationId");
                                                            String modelName = generateInlinedSchemaName(operationId, schema, schemaCounter++);

                                                            // Store the mapping
                                                            inlinedSchemas.put(schemaObj, modelName);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Collect inline object schemas from schema properties
     */
    private void collectInlinedSchemasFromProperties(Map<String, Object> schemas, Map<String, Object> spec) {
        if (schemas == null) return;

        // Track visited schemas to prevent infinite recursion (using IdentityHashSet for identity-based comparison)
        Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        // Create a set of top-level schema objects for quick lookup
        Set<Object> topLevelSchemaObjects = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Object schemaObj : schemas.values()) {
            if (schemaObj instanceof Map) {
                topLevelSchemaObjects.add(schemaObj);
            }
        }

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Check properties in this schema (but don't register the schema itself as inline)
            collectInlineSchemasFromSchemaProperties(schema, null, spec, visited, topLevelSchemaObjects);
        }
    }

    /**
     * Recursively collect inline object schemas from a schema's properties
     */
    private void collectInlineSchemasFromSchemaProperties(Map<String, Object> schema, String parentPropertyName, Map<String, Object> spec, Set<Object> visited, Set<Object> topLevelSchemaObjects) {
        collectInlineSchemasFromSchemaProperties(schema, parentPropertyName, spec, visited, topLevelSchemaObjects, false);
    }

    /**
     * Recursively collect inline object schemas from a schema's properties
     *
     * @param isInCompositionContext true if this schema is being processed as part of an allOf/oneOf/anyOf composition
     */
    private void collectInlineSchemasFromSchemaProperties(Map<String, Object> schema, String parentPropertyName, Map<String, Object> spec, Set<Object> visited, Set<Object> topLevelSchemaObjects, boolean isInCompositionContext) {
        if (schema == null) return;

        // Prevent infinite recursion - check if we've already visited this schema
        if (visited.contains(schema)) {
            return;
        }
        visited.add(schema);

        // Handle $ref - resolve and check
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                        if (referencedSchema != null) {
                            // Don't recurse into top-level schemas - they're already being processed
                            if (!topLevelSchemaObjects.contains(referencedSchema)) {
                                // Check if we've already visited this referenced schema to prevent cycles
                                // Add to visited BEFORE recursing to prevent infinite loops
                                if (visited.add(referencedSchema)) {
                                    // Only recurse if we successfully added (wasn't already visited)
                                    collectInlineSchemasFromSchemaProperties(referencedSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, isInCompositionContext);
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        // Check allOf, oneOf, anyOf - process sub-schemas but mark them as being in composition context
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas != null) {
                for (Map<String, Object> subSchema : allOfSchemas) {
                    if (subSchema != null) {
                        // Process sub-schemas in composition context - inline objects in allOf should not be separate models
                        collectInlineSchemasFromSchemaProperties(subSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, true);
                    }
                }
            }
        }

        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemasList = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            if (schemasList != null) {
                for (Map<String, Object> subSchema : schemasList) {
                    if (subSchema != null) {
                        // Process sub-schemas in composition context - inline objects in oneOf/anyOf should not be separate models
                        collectInlineSchemasFromSchemaProperties(subSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, true);
                    }
                }
            }
        }

        // Check if this is an inline object schema (not a top-level schema)
        // Skip if it's part of a composition (allOf/oneOf/anyOf) - those should be merged, not separate models
        String type = (String) schema.get("type");
        if ("object".equals(type) && schema.containsKey("properties") && !schema.containsKey("$ref") && !isInCompositionContext) {
            // Only register if it's not a top-level schema and not already registered
            if (!topLevelSchemaObjects.contains(schema) && !inlinedSchemas.containsKey(schema)) {
                // Generate a name for this inline schema
                String modelName = generateInlineSchemaNameFromProperty(schema, parentPropertyName);
                inlinedSchemas.put(schema, modelName);
            }
        }

        // Recursively check properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            if (properties != null) {
                for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
                    String propertyName = propertyEntry.getKey();
                    Object propertyValue = propertyEntry.getValue();

                    Map<String, Object> propertySchema = Util.asStringObjectMap(propertyValue);
                    if (propertySchema != null) {
                        collectInlineSchemasFromSchemaProperties(propertySchema, propertyName, spec, visited, topLevelSchemaObjects, isInCompositionContext);
                    }
                }
            }
        }

        // Check array items
        if ("array".equals(type) && schema.containsKey("items")) {
            Object itemsObj = schema.get("items");
            Map<String, Object> itemsMap = Util.asStringObjectMap(itemsObj);
            if (itemsMap != null) {
                collectInlineSchemasFromSchemaProperties(itemsMap, parentPropertyName, spec, visited, topLevelSchemaObjects, isInCompositionContext);
            }
        }
    }

    /**
     * Generate a name for an inline schema from a property
     */
    private String generateInlineSchemaNameFromProperty(Map<String, Object> schema, String propertyName) {
        // First, try to use the title if available
        if (schema.containsKey("title")) {
            String title = (String) schema.get("title");
            if (title != null && !title.isEmpty()) {
                return toJavaClassName(title);
            }
        }

        // Use the property name if available
        if (propertyName != null && !propertyName.isEmpty()) {
            return toJavaClassName(capitalize(propertyName));
        }

        // Fallback to generic name
        return "InlineObject" + inlinedSchemas.size();
    }

    /**
     * Generate a name for an in-lined schema
     */
    private String generateInlinedSchemaName(String operationId, Map<String, Object> schema, int counter) {
        // Try to generate a meaningful name from the schema properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));

            // Look for common patterns like "promptTemplates", "templates", etc.
            for (String propName : properties.keySet()) {
                // If property name suggests a collection (ends with 's' or plural forms), use it
                if (propName.endsWith("s") && propName.length() > 1) {
                    // Check if it's a plural (not just a word ending in 's')
                    // For "promptTemplates", we want "PromptTemplates" (without Response suffix)
                    String capitalized = capitalize(propName);
                    return toJavaClassName(capitalized);
                } else if (propName.endsWith("List") || propName.endsWith("Array")) {
                    String baseName = propName.substring(0, propName.length() - 4);
                    if (!baseName.isEmpty()) {
                        return toJavaClassName(capitalize(baseName));
                    }
                }
            }

            // If we have a single property, use it
            if (properties.size() == 1) {
                String propName = properties.keySet().iterator().next();
                return toJavaClassName(capitalize(propName));
            }
        }

        // Fallback: use operation ID if available
        if (operationId != null && !operationId.isEmpty()) {
            return toJavaClassName(capitalize(operationId) + "Response");
        }

        // Last resort: generic name
        return "InlinedSchema" + counter;
    }

    /**
     * Generate models
     * Generates all models from the schemas section, not just referenced ones
     * Skips schemas that are only used via allOf/oneOf/anyOf in other schemas
     */
    private void generateModels(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) return;

        String packagePath = packageName != null ? packageName : "com.example.api";

        // Collect schemas that are only used via allOf/oneOf/anyOf
        Set<String> compositionOnlySchemas = collectCompositionOnlySchemas(schemas);

        // Collect inline object schemas from schema properties
        collectInlinedSchemasFromProperties(schemas, spec);

        // Generate all models from schemas section
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Filter out error schemas
            if (isErrorSchema(schemaName)) {
                continue;
            }

            // Skip schemas that are only used via allOf/oneOf/anyOf AND are intermediate schemas
            // (i.e., they use allOf to extend another schema, making them intermediate, not base schemas)
            if (compositionOnlySchemas.contains(schemaName)) {
                // Check if this schema uses allOf to extend another schema (making it intermediate)
                boolean isIntermediateSchema = schema.containsKey("allOf");
                if (isIntermediateSchema) {
                    continue; // Skip intermediate schemas
                }
                // Keep base schemas even if they're used in composition
            }

            // Generate all schemas that have properties, allOf, oneOf, anyOf, or enum
            // Skip only simple primitive types without any constraints or structure
            boolean hasStructure = schema.containsKey("properties") ||
                    schema.containsKey("allOf") ||
                    schema.containsKey("oneOf") ||
                    schema.containsKey("anyOf") ||
                    schema.containsKey("enum");

            // If it's a simple type (string, integer, etc.) without structure, skip it
            if (!hasStructure && schema.containsKey("type")) {
                Object type = schema.get("type");
                if (type instanceof String && !"object".equals(type)) {
                    // Skip simple primitives without any structure
                    continue;
                }
            }

            // Convert schema name to valid Java class name
            String javaClassName = toJavaClassName(schemaName);

            generateModel(javaClassName, schema, outputDir, packagePath, spec);
        }

        // Generate models for in-lined schemas
        for (Map.Entry<Object, String> entry : inlinedSchemas.entrySet()) {
            Object schemaObj = entry.getKey();
            String modelName = entry.getValue();

            Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
            if (schema != null) {
                generateModel(modelName, schema, outputDir, packagePath, spec);
            }
        }

        // Generate JAXBBean interface
        generateJAXBBeanInterface(outputDir, packagePath);

        // Generate ObjectFactory for JAXB support
        generateObjectFactory(schemas, outputDir, packagePath);
    }

    /**
     * Generate JAXBBean interface
     */
    private void generateJAXBBeanInterface(String outputDir, String packagePath) throws IOException {
        String content = "package " + packagePath + ".model;\n\n" +
                "import java.util.List;\n\n" +
                "/**\n" +
                " * Interface for JAXB beans with dynamic attribute support.\n" +
                " * All model classes implementing this interface are JAXB-compatible.\n" +
                " */\n" +
                "public interface JAXBBean {\n" +
                "    /**\n" +
                "     * Get an attribute value by name.\n" +
                "     * @param name The attribute name\n" +
                "     * @return The attribute value, or null if not set\n" +
                "     */\n" +
                "    Object getAttribute(String name);\n\n" +
                "    /**\n" +
                "     * Check if an attribute is set.\n" +
                "     * @param name The attribute name\n" +
                "     * @return true if the attribute is set, false otherwise\n" +
                "     */\n" +
                "    boolean isSetAttribute(String name);\n\n" +
                "    /**\n" +
                "     * Get all attribute names.\n" +
                "     * @return A list of all attribute names\n" +
                "     */\n" +
                "    List<String> getAttributeNames();\n\n" +
                "    /**\n" +
                "     * Set an attribute value by name.\n" +
                "     * @param name The attribute name\n" +
                "     * @param value The attribute value\n" +
                "     */\n" +
                "    void setAttribute(String name, Object value);\n" +
                "}\n";

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/model/JAXBBean.java", content);
    }

    /**
     * Generate ObjectFactory class for JAXB support
     */
    private void generateObjectFactory(Map<String, Object> schemas, String outputDir, String packagePath) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".model;\n\n");
        content.append("import jakarta.xml.bind.JAXBElement;\n");
        content.append("import jakarta.xml.bind.annotation.XmlElementDecl;\n");
        content.append("import jakarta.xml.bind.annotation.XmlRegistry;\n");
        content.append("import javax.validation.constraints.*;\n");
        content.append("import javax.xml.namespace.QName;\n\n");

        content.append("@XmlRegistry\n");
        content.append("public class ObjectFactory {\n\n");

        content.append("    public ObjectFactory() {\n");
        content.append("    }\n\n");

        // Generate factory methods for each model class
        Set<String> generatedClasses = new HashSet<>();

        // Add all top-level schemas
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Skip error schemas and simple types
            if (isErrorSchema(schemaName)) {
                continue;
            }

            boolean hasStructure = schema.containsKey("properties") ||
                    schema.containsKey("allOf") ||
                    schema.containsKey("oneOf") ||
                    schema.containsKey("anyOf") ||
                    schema.containsKey("enum");

            if (!hasStructure && schema.containsKey("type")) {
                Object type = schema.get("type");
                if (type instanceof String && !"object".equals(type)) {
                    continue;
                }
            }

            String javaClassName = toJavaClassName(schemaName);
            if (!generatedClasses.contains(javaClassName)) {
                generateObjectFactoryMethod(content, javaClassName);
                generatedClasses.add(javaClassName);
            }
        }

        // Add inlined schemas
        for (String modelName : inlinedSchemas.values()) {
            if (!generatedClasses.contains(modelName)) {
                generateObjectFactoryMethod(content, modelName);
                generatedClasses.add(modelName);
            }
        }

        content.append("}\n");

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/model/ObjectFactory.java", content.toString());
    }

    /**
     * Generate factory method for a model class
     */
    private void generateObjectFactoryMethod(StringBuilder content, String className) {
        String elementName = className.substring(0, 1).toLowerCase(Locale.ROOT) + className.substring(1);
        String qnameConstant = "_" + className.toUpperCase(Locale.ROOT) + "_QNAME";
        content.append("    private static final QName ").append(qnameConstant).append(" = new QName(\"\", \"").append(elementName).append("\");\n\n");
        content.append("    @XmlElementDecl(name = \"").append(elementName).append("\")\n");
        content.append("    public JAXBElement<").append(className).append("> create").append(className).append("(").append(className).append(" value) {\n");
        content.append("        return new JAXBElement<").append(className).append(">(").append(qnameConstant).append(", ").append(className).append(".class, null, value);\n");
        content.append("    }\n\n");
    }

    /**
     * Collect schemas that are only used via allOf/oneOf/anyOf in other schemas
     */
    private Set<String> collectCompositionOnlySchemas(Map<String, Object> schemas) {
        Set<String> compositionOnlySchemas = new HashSet<>();

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Check if this schema is used in allOf/oneOf/anyOf of other schemas
            boolean usedInComposition = false;

            for (Map.Entry<String, Object> otherEntry : schemas.entrySet()) {
                if (otherEntry.getKey().equals(schemaName)) continue;

                Map<String, Object> otherSchema = Util.asStringObjectMap(otherEntry.getValue());
                if (otherSchema == null) continue;

                // Check allOf
                if (otherSchema.containsKey("allOf")) {
                    List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(otherSchema.get("allOf"));
                    for (Map<String, Object> subSchema : allOfSchemas) {
                        if (isSchemaReference(subSchema, schemaName)) {
                            usedInComposition = true;
                            break;
                        }
                    }
                }

                // Check oneOf
                if (otherSchema.containsKey("oneOf")) {
                    List<Map<String, Object>> oneOfSchemas = Util.asStringObjectMapList(otherSchema.get("oneOf"));
                    if (oneOfSchemas != null) {
                        for (Map<String, Object> subSchema : oneOfSchemas) {
                            if (isSchemaReference(subSchema, schemaName)) {
                                usedInComposition = true;
                                break;
                            }
                        }
                    }
                }

                // Check anyOf
                if (otherSchema.containsKey("anyOf")) {
                    List<Map<String, Object>> anyOfSchemas = Util.asStringObjectMapList(otherSchema.get("anyOf"));
                    if (anyOfSchemas != null) {
                        for (Map<String, Object> subSchema : anyOfSchemas) {
                            if (isSchemaReference(subSchema, schemaName)) {
                                usedInComposition = true;
                                break;
                            }
                        }
                    }
                }

                if (usedInComposition) break;
            }

            if (usedInComposition) {
                compositionOnlySchemas.add(schemaName);
            }
        }

        return compositionOnlySchemas;
    }

    /**
     * Check if a schema object references the given schema name
     */
    private boolean isSchemaReference(Map<String, Object> schema, String schemaName) {
        if (schema == null) return false;

        String ref = (String) schema.get("$ref");
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String refSchemaName = ref.substring(ref.lastIndexOf("/") + 1);
            return refSchemaName.equals(schemaName);
        }

        return false;
    }

    /**
     * Recursively collect schema names from a schema object
     */
    private void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec) {
        collectSchemasFromSchemaObject(schemaObj, referencedSchemas, spec, new java.util.IdentityHashMap<>());
    }

    /**
     * Recursively collect schema names from a schema object with cycle detection
     */
    private void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec, java.util.Map<Object, Boolean> visited) {
        if (schemaObj == null) {
            return;
        }

        if (!(schemaObj instanceof Map<?, ?>)) {
            return;
        }

        // Cycle detection
        if (visited.containsKey(schemaObj)) {
            return;
        }
        visited.put(schemaObj, Boolean.TRUE);

        Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
        if (schema == null) {
            return;
        }

        // Check for $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                addSchemaAndCollectNested(schemaName, referencedSchemas, spec);
            }
            return;
        }

        // Check allOf, oneOf, anyOf
        for (String compositionType : new String[]{"allOf", "oneOf", "anyOf"}) {
            if (schema.containsKey(compositionType)) {
                Object compObj = schema.get(compositionType);
                if (compObj instanceof List<?> compositions) {
                    for (Object compItem : compositions) {
                        collectSchemasFromSchemaObject(compItem, referencedSchemas, spec, visited);
                    }
                }
            }
        }

        // Check items (for arrays)
        if (schema.containsKey("items")) {
            collectSchemasFromSchemaObject(schema.get("items"), referencedSchemas, spec, visited);
        }

        // Check properties (for objects)
        if (schema.containsKey("properties")) {
            Object propsObj = schema.get("properties");
            if (propsObj instanceof Map<?, ?>) {
                Map<String, Object> properties = Util.asStringObjectMap(propsObj);
                for (Object propSchema : properties.values()) {
                    collectSchemasFromSchemaObject(propSchema, referencedSchemas, spec, visited);
                }
            }
        }
    }

    /**
     * Add schema to referenced set and recursively collect nested schemas
     */
    private void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (referencedSchemas.contains(schemaName)) {
            return;
        }

        referencedSchemas.add(schemaName);

        // Recursively collect schemas referenced by this schema
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null && schemas.containsKey(schemaName)) {
                Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec);
            }
        }
    }

    /**
     * Generate individual model
     */
    private void generateModel(String schemaName, Map<String, Object> schema, String outputDir, String packagePath, Map<String, Object> spec) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".model;\n\n");
        content.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        content.append("import javax.validation.constraints.*;\n");
        content.append("import jakarta.xml.bind.annotation.*;\n");
        content.append("import jakarta.xml.bind.JAXBElement;\n");
        content.append("import javax.xml.namespace.QName;\n");
        content.append("import java.io.Serializable;\n");
        content.append("import java.util.Objects;\n");
        content.append("import java.util.List;\n");
        content.append("import java.util.ArrayList;\n");
        content.append("import java.util.Map;\n");
        content.append("import java.util.HashMap;\n");
        content.append("import javax.xml.datatype.XMLGregorianCalendar;\n\n");

        // Add JAXB annotations
        content.append("@XmlRootElement(name = \"").append(schemaName).append("\")\n");
        content.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
        content.append("@XmlType(name = \"").append(schemaName).append("\", propOrder = {\n");

        // Generate propOrder for JAXB
        Map<String, Object> allProperties = new java.util.LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();

        // Extract properties first to get field names
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> subSchema : schemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else {
            mergeSchemaProperties(schema, allProperties, allRequired, spec);
        }

        List<String> fieldNames = new ArrayList<>(allProperties.keySet());
        if (!fieldNames.isEmpty()) {
            for (int i = 0; i < fieldNames.size(); i++) {
                content.append("    \"").append(toCamelCase(fieldNames.get(i))).append("\"");
                if (i < fieldNames.size() - 1) {
                    content.append(",\n");
                } else {
                    content.append("\n");
                }
            }
        }
        content.append("})\n");

        content.append("public class ").append(schemaName).append(" implements Serializable, JAXBBean {\n\n");

        // Add serialVersionUID for Serializable
        content.append("    private static final long serialVersionUID = 1L;\n\n");

        // Add field for dynamic attributes
        content.append("    private Map<String, Object> attributes = new HashMap<>();\n\n");

        // Generate fields
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

            content.append("    ");

            // Add JAXB @XmlElement annotation
            String javaFieldName = toCamelCase(fieldName);
            String fieldType = getJavaType(fieldSchema);

            // Handle arrays/lists with @XmlElementWrapper
            if (fieldType.startsWith("List<")) {
                content.append("@XmlElementWrapper(name = \"").append(fieldName).append("\")\n    ");
                content.append("@XmlElement()\n    ");
            } else {
                content.append("@XmlElement(name = \"").append(fieldName).append("\"");
                if (allRequired.contains(fieldName)) {
                    content.append(", required = true");
                }
                content.append(")\n    ");
            }

            // Add @JsonProperty annotation if field name differs from Java naming
            if (!fieldName.equals(javaFieldName)) {
                content.append("@JsonProperty(\"").append(fieldName).append("\")\n    ");
            }

            // Add validation annotations based on schema constraints
            String validationAnnotations = generateValidationAnnotations(fieldSchema, allRequired.contains(fieldName));
            if (!validationAnnotations.isEmpty()) {
                content.append(validationAnnotations);
            }

            // Add field type and name
            content.append("private ").append(fieldType).append(" ").append(javaFieldName).append(";\n\n");
        }

        // Generate default constructor
        content.append("    public ").append(schemaName).append("() {\n");
        content.append("    }\n\n");

        // Generate getters and setters
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
            String fieldType = getJavaType(fieldSchema);
            String javaFieldName = toCamelCase(fieldName);
            String capitalizedFieldName = capitalize(javaFieldName);

            // Getter
            content.append("    public ").append(fieldType).append(" get").append(capitalizedFieldName).append("() {\n");
            content.append("        return ").append(javaFieldName).append(";\n");
            content.append("    }\n\n");

            // Setter
            content.append("    public void set").append(capitalizedFieldName).append("(").append(fieldType).append(" ").append(javaFieldName).append(") {\n");
            content.append("        this.").append(javaFieldName).append(" = ").append(javaFieldName).append(";\n");
            content.append("    }\n\n");
        }

        // Generate equals method
        content.append("    @Override\n");
        content.append("    public boolean equals(Object o) {\n");
        content.append("        if (this == o) return true;\n");
        content.append("        if (o == null || getClass() != o.getClass()) return false;\n");
        content.append("        ").append(schemaName).append(" that = (").append(schemaName).append(") o;\n");
        content.append("        return ");

        if (!fieldNames.isEmpty()) {
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) content.append("                ");
                content.append("Objects.equals(").append(toCamelCase(fieldNames.get(i))).append(", that.").append(toCamelCase(fieldNames.get(i))).append(")");
                if (i < fieldNames.size() - 1) {
                    content.append(" &&\n");
                } else {
                    content.append(";\n");
                }
            }
        } else {
            content.append("true;\n");
        }
        content.append("    }\n\n");

        // Generate hashCode method
        content.append("    @Override\n");
        content.append("    public int hashCode() {\n");
        content.append("        return Objects.hash(");
        if (!fieldNames.isEmpty()) {
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) content.append(", ");
                content.append(toCamelCase(fieldNames.get(i)));
            }
        }
        content.append(");\n");
        content.append("    }\n\n");

        // Generate toString method
        content.append("    @Override\n");
        content.append("    public String toString() {\n");
        content.append("        return \"").append(schemaName).append("{\" +\n");

        if (!fieldNames.isEmpty()) {
            for (int i = 0; i < fieldNames.size(); i++) {
                String fieldName = fieldNames.get(i);
                String javaFieldName = toCamelCase(fieldName);
                content.append("                \"").append(javaFieldName).append("=\" + ").append(javaFieldName);
                if (i < fieldNames.size() - 1) {
                    content.append(" + \", \" +\n");
                } else {
                    content.append(" +\n");
                }
            }
        }

        content.append("                '}';\n");
        content.append("    }\n\n");

        // Generate JAXBBean interface methods
        content.append("    @Override\n");
        content.append("    public Object getAttribute(String name) {\n");
        // First check attributes map, then check fields
        if (!fieldNames.isEmpty()) {
            content.append("        if (attributes.containsKey(name)) {\n");
            content.append("            return attributes.get(name);\n");
            content.append("        }\n");
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                String javaFieldName = toCamelCase(fieldName);
                content.append("            case \"").append(fieldName).append("\":\n");
                content.append("                return ").append(javaFieldName).append(";\n");
            }
            content.append("            default:\n");
            content.append("                return null;\n");
            content.append("        }\n");
        } else {
            content.append("        return attributes.get(name);\n");
        }
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public boolean isSetAttribute(String name) {\n");
        // Check attributes map first
        content.append("        if (attributes.containsKey(name)) {\n");
        content.append("            return true;\n");
        content.append("        }\n");
        // Then check if it's a field and if it's set (not null)
        if (!fieldNames.isEmpty()) {
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                String javaFieldName = toCamelCase(fieldName);
                content.append("            case \"").append(fieldName).append("\":\n");
                content.append("                return ").append(javaFieldName).append(" != null;\n");
            }
            content.append("            default:\n");
            content.append("                return false;\n");
            content.append("        }\n");
        } else {
            content.append("        return false;\n");
        }
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public List<String> getAttributeNames() {\n");
        content.append("        List<String> allNames = new ArrayList<>(attributes.keySet());\n");
        // Add field names to the list
        if (!fieldNames.isEmpty()) {
            for (String fieldName : fieldNames) {
                content.append("        if (!allNames.contains(\"").append(fieldName).append("\")) {\n");
                content.append("            allNames.add(\"").append(fieldName).append("\");\n");
                content.append("        }\n");
            }
        }
        content.append("        return allNames;\n");
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public void setAttribute(String name, Object value) {\n");
        // Check if it's a known field and use the setter, otherwise store in attributes map
        if (!fieldNames.isEmpty()) {
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                String javaFieldName = toCamelCase(fieldName);
                String capitalizedFieldName = capitalize(javaFieldName);
                Map<String, Object> fieldSchema = Util.asStringObjectMap(allProperties.get(fieldName));
                String fieldType = fieldSchema != null ? getJavaType(fieldSchema) : "Object";
                content.append("            case \"").append(fieldName).append("\":\n");
                content.append("                set").append(capitalizedFieldName).append("((").append(fieldType).append(") value);\n");
                content.append("                return;\n");
            }
            content.append("            default:\n");
            content.append("                attributes.put(name, value);\n");
            content.append("                break;\n");
            content.append("        }\n");
        } else {
            content.append("        attributes.put(name, value);\n");
        }
        content.append("    }\n");
        content.append("}\n");

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/model/" + schemaName + ".java", content.toString());
    }

    /**
     * Generate services
     */
    private void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String serviceContent = String.format("""
                package %s.service;
                
                import jakarta.inject.Singleton;
                
                @Singleton
                public class ApiService {
                
                    // Business logic implementation placeholder
                    // This service should contain the core business logic for the API
                    // Implement methods that correspond to the operations defined in the OpenAPI specification
                
                }
                """, packagePath);

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/service/ApiService.java", serviceContent);
    }

    /**
     * Generate configuration
     */
    private void generateConfiguration(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String configContent = String.format("""
                package %s.config;
                
                import jakarta.ws.rs.container.ContainerRequestContext;
                import jakarta.ws.rs.container.ContainerResponseContext;
                import jakarta.ws.rs.container.ContainerResponseFilter;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;
                
                @Provider
                public class CorsFilter implements ContainerResponseFilter {
                
                    @Override
                    public void filter(ContainerRequestContext requestContext,
                                       ContainerResponseContext responseContext) throws IOException {
                        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
                        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                        responseContext.getHeaders().add("Access-Control-Allow-Headers", "*");
                    }
                }
                """, packagePath);

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/config/CorsFilter.java", configContent);
    }

    /**
     * Generate exception mappers
     */
    private void generateExceptionMappers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String exceptionContent = String.format("""
                package %s.exception;
                
                import jakarta.ws.rs.core.Response;
                import jakarta.ws.rs.ext.ExceptionMapper;
                import jakarta.ws.rs.ext.Provider;
                
                @Provider
                public class GenericExceptionMapper implements ExceptionMapper<Exception> {
                
                    @Override
                    public Response toResponse(Exception exception) {
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity("An error occurred: " + exception.getMessage())
                                .build();
                    }
                }
                """, packagePath);

        writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/exception/GenericExceptionMapper.java", exceptionContent);
    }

    /**
     * Generate build files
     */
    private void generateBuildFiles(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        // Generate pom.xml
        String pomContent = generatePomXml(spec, packageName);
        writeFile(outputDir + "/pom.xml", pomContent);

        // Generate web.xml for servlet container deployment
        String webXmlContent = generateWebXml(packageName);
        writeFile(outputDir + "/src/main/webapp/WEB-INF/web.xml", webXmlContent);
    }

    /**
     * Generate pom.xml
     */
    private String generatePomXml(Map<String, Object> spec, String packageName) {
        return String.format("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                        
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                            <packaging>war</packaging>
                        
                            <name>%s</name>
                            <description>%s</description>
                        
                            <properties>
                                <java.version>21</java.version>
                                <jersey.version>3.1.3</jersey.version>
                                <jackson.version>2.15.2</jackson.version>
                                <maven.compiler.source>21</maven.compiler.source>
                                <maven.compiler.target>21</maven.compiler.target>
                                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            </properties>
                        
                            <dependencies>
                                <!-- Jersey -->
                                <dependency>
                                    <groupId>org.glassfish.jersey.core</groupId>
                                    <artifactId>jersey-server</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.containers</groupId>
                                    <artifactId>jersey-container-servlet</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.containers</groupId>
                                    <artifactId>jersey-container-grizzly2-http</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.inject</groupId>
                                    <artifactId>jersey-hk2</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.media</groupId>
                                    <artifactId>jersey-media-json-jackson</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.media</groupId>
                                    <artifactId>jersey-media-jaxb</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                        
                                <!-- JAXB -->
                                <dependency>
                                    <groupId>jakarta.xml.bind</groupId>
                                    <artifactId>jakarta.xml.bind-api</artifactId>
                                    <version>4.0.0</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jaxb</groupId>
                                    <artifactId>jaxb-runtime</artifactId>
                                    <version>4.0.2</version>
                                </dependency>
                        
                                <!-- Jackson -->
                                <dependency>
                                    <groupId>com.fasterxml.jackson.core</groupId>
                                    <artifactId>jackson-databind</artifactId>
                                    <version>${jackson.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>com.fasterxml.jackson.datatype</groupId>
                                    <artifactId>jackson-datatype-jsr310</artifactId>
                                    <version>${jackson.version}</version>
                                </dependency>
                        
                                <!-- Jakarta APIs -->
                                <dependency>
                                    <groupId>jakarta.ws.rs</groupId>
                                    <artifactId>jakarta.ws.rs-api</artifactId>
                                    <version>3.1.0</version>
                                </dependency>
                                <dependency>
                                    <groupId>jakarta.servlet</groupId>
                                    <artifactId>jakarta.servlet-api</artifactId>
                                    <version>6.0.0</version>
                                    <scope>provided</scope>
                                </dependency>
                                <dependency>
                                    <groupId>jakarta.validation</groupId>
                                    <artifactId>jakarta.validation-api</artifactId>
                                    <version>3.0.2</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.hibernate.validator</groupId>
                                    <artifactId>hibernate-validator</artifactId>
                                    <version>8.0.1.Final</version>
                                </dependency>
                            </dependencies>
                        
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-compiler-plugin</artifactId>
                                        <version>3.11.0</version>
                                        <configuration>
                                            <source>21</source>
                                            <target>21</target>
                                        </configuration>
                                    </plugin>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-war-plugin</artifactId>
                                        <version>3.3.2</version>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                packageName != null ? packageName : "com.example.api",
                getAPITitle(spec).toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "-"),
                getAPIVersion(spec),
                getAPITitle(spec),
                getAPIDescription(spec));
    }

    /**
     * Generate web.xml
     */
    private String generateWebXml(String packageName) {
        String packagePath = packageName != null ? packageName : "com.example.api";
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                         https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                         version="6.0">
                    <servlet>
                        <servlet-name>Jersey Servlet</servlet-name>
                        <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
                        <init-param>
                            <param-name>jakarta.ws.rs.Application</param-name>
                            <param-value>%s.%sApplication</param-value>
                        </init-param>
                        <load-on-startup>1</load-on-startup>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>Jersey Servlet</servlet-name>
                        <url-pattern>/api/*</url-pattern>
                    </servlet-mapping>
                </web-app>
                """, packagePath, getAPITitle(null).replaceAll("[^a-zA-Z0-9]", ""));
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        if (spec == null) return "API";
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("description") : "Generated API";
    }

    private String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("version") : "1.0.0";
    }

    private String generateResourceName(String path) {
        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Resource";
    }

    private void writeFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content);
    }

    /**
     * Merge schema properties into the allProperties map
     */
    private void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec) {
        mergeSchemaProperties(schema, allProperties, allRequired, spec, new java.util.IdentityHashMap<>());
    }

    /**
     * Merge schema properties with cycle detection to prevent infinite recursion
     */
    private void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec,
                                       java.util.Map<Object, Boolean> visited) {
        if (schema == null) return;

        // Cycle detection - prevent infinite recursion
        if (visited.containsKey(schema)) {
            return;
        }
        visited.put(schema, Boolean.TRUE);

        // Handle $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                        if (referencedSchema != null) {
                            mergeSchemaProperties(referencedSchema, allProperties, allRequired, spec, visited);
                        }
                    }
                }
            }
            return;
        }

        // Handle allOf
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas != null) {
                for (Map<String, Object> subSchema : allOfSchemas) {
                    if (subSchema != null) {
                        mergeSchemaProperties(subSchema, allProperties, allRequired, spec, visited);
                    }
                }
            }
            return;
        }

        // Handle oneOf/anyOf
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            if (schemas != null) {
                for (Map<String, Object> subSchema : schemas) {
                    if (subSchema != null) {
                        mergeSchemaProperties(subSchema, allProperties, allRequired, spec, visited);
                    }
                }
            }
            return;
        }

        // Merge direct properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            if (properties != null) {
                allProperties.putAll(properties);
            }
        }

        // Merge required fields
        if (schema.containsKey("required")) {
            List<String> required = Util.asStringList(schema.get("required"));
            if (required != null) {
                for (String field : required) {
                    if (!allRequired.contains(field)) {
                        allRequired.add(field);
                    }
                }
            }
        }
    }

    /**
     * Generate validation annotations based on OpenAPI schema constraints
     */
    private String generateValidationAnnotations(Map<String, Object> schema, boolean isRequired) {
        if (schema == null) {
            return isRequired ? "@NotNull\n    " : "";
        }

        // Handle $ref - resolve the reference to get actual schema
        if (schema.containsKey("$ref")) {
            // Note: We can't resolve $ref here as we don't have access to the full spec
            // The schema should already be resolved before calling this method
            return isRequired ? "@NotNull\n    " : "";
        }

        StringBuilder annotations = new StringBuilder();

        // Add @NotNull for required fields
        if (isRequired) {
            annotations.append("@NotNull\n    ");
        }

        String type = (String) schema.get("type");
        if (type == null) {
            return annotations.toString();
        }

        // Handle string validations
        switch (type) {
            case "string" -> {
                String format = (String) schema.get("format");

                // Email format
                if ("email".equals(format)) {
                    annotations.append("@Email\n    ");
                }

                // Enum validation - must be checked before pattern to avoid conflicts
                List<?> enumValues = schema.get("enum") instanceof List<?> list ? list : null;
                boolean hasEnum = enumValues != null && !enumValues.isEmpty();

                if (hasEnum && enumValues != null) {
                    // Build regex pattern that matches any of the enum values
                    StringBuilder enumPattern = new StringBuilder("^(");
                    for (int i = 0; i < enumValues.size(); i++) {
                        if (i > 0) {
                            enumPattern.append("|");
                        }
                        // Escape special regex characters in enum values
                        String enumValue = enumValues.get(i).toString();
                        enumValue = enumValue.replace("\\", "\\\\")
                                .replace("^", "\\^")
                                .replace("$", "\\$")
                                .replace(".", "\\.")
                                .replace("|", "\\|")
                                .replace("?", "\\?")
                                .replace("*", "\\*")
                                .replace("+", "\\+")
                                .replace("(", "\\(")
                                .replace(")", "\\)")
                                .replace("[", "\\[")
                                .replace("]", "\\]")
                                .replace("{", "\\{")
                                .replace("}", "\\}")
                                .replace("\"", "\\\"");
                        enumPattern.append(enumValue);
                    }
                    enumPattern.append(")$");
                    annotations.append("@Pattern(regexp = \"").append(enumPattern).append("\")\n    ");
                }

                // Pattern validation (only if enum is not present, as enum takes precedence)
                if (!hasEnum) {
                    Object patternObj = schema.get("pattern");
                    if (patternObj != null) {
                        String pattern = patternObj.toString();
                        // Escape backslashes and quotes in pattern for Java string
                        pattern = pattern.replace("\\", "\\\\").replace("\"", "\\\"");
                        annotations.append("@Pattern(regexp = \"").append(pattern).append("\")\n    ");
                    }
                }

                // Min and max length together (preferred)
                Object minLengthObj = schema.get("minLength");
                Object maxLengthObj = schema.get("maxLength");
                if (minLengthObj != null && maxLengthObj != null) {
                    int minLength = getIntValue(minLengthObj);
                    int maxLength = getIntValue(maxLengthObj);
                    annotations.append("@Size(min = ").append(minLength).append(", max = ").append(maxLength).append(")\n    ");
                } else {
                    // Min length only
                    if (minLengthObj != null) {
                        int minLength = getIntValue(minLengthObj);
                        if (minLength > 0) {
                            annotations.append("@Size(min = ").append(minLength).append(")\n    ");
                        }
                    }

                    // Max length only
                    if (maxLengthObj != null) {
                        int maxLength = getIntValue(maxLengthObj);
                        annotations.append("@Size(max = ").append(maxLength).append(")\n    ");
                    }
                }
            }

            // Handle integer validations
            case "integer" -> {
                // Minimum
                Object minimumObj = schema.get("minimum");
                if (minimumObj != null) {
                    long minimum = getLongValue(minimumObj);
                    boolean exclusiveMinimum = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                    if (exclusiveMinimum) {
                        annotations.append("@Min(value = ").append(minimum + 1).append(")\n    ");
                    } else {
                        annotations.append("@Min(value = ").append(minimum).append(")\n    ");
                    }
                }

                // Maximum
                Object maximumObj = schema.get("maximum");
                if (maximumObj != null) {
                    long maximum = getLongValue(maximumObj);
                    boolean exclusiveMaximum = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                    if (exclusiveMaximum) {
                        annotations.append("@Max(value = ").append(maximum - 1).append(")\n    ");
                    } else {
                        annotations.append("@Max(value = ").append(maximum).append(")\n    ");
                    }
                }

                // Positive/negative constraints
                if (minimumObj != null) {
                    long minimum = getLongValue(minimumObj);
                    if (minimum > 0) {
                        annotations.append("@Positive\n    ");
                    } else if (minimum == 0) {
                        annotations.append("@PositiveOrZero\n    ");
                    }
                }
                if (maximumObj != null) {
                    long maximum = getLongValue(maximumObj);
                    if (maximum < 0) {
                        annotations.append("@Negative\n    ");
                    } else if (maximum == 0) {
                        annotations.append("@NegativeOrZero\n    ");
                    }
                }
            }


            // Handle number validations
            case "number" -> {
                // Minimum
                Object minimumObj = schema.get("minimum");
                if (minimumObj != null) {
                    double minimum = getDoubleValue(minimumObj);
                    boolean exclusiveMinimum = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                    String minValue = String.format("%.10f", exclusiveMinimum ? minimum + Double.MIN_VALUE : minimum);
                    annotations.append("@DecimalMin(value = \"").append(minValue).append("\")\n    ");
                }

                // Maximum
                Object maximumObj = schema.get("maximum");
                if (maximumObj != null) {
                    double maximum = getDoubleValue(maximumObj);
                    boolean exclusiveMaximum = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                    String maxValue = String.format("%.10f", exclusiveMaximum ? maximum - Double.MIN_VALUE : maximum);
                    annotations.append("@DecimalMax(value = \"").append(maxValue).append("\")\n    ");
                }

                // Positive/negative constraints
                if (minimumObj != null) {
                    double minimum = getDoubleValue(minimumObj);
                    if (minimum > 0) {
                        annotations.append("@Positive\n    ");
                    } else if (minimum == 0) {
                        annotations.append("@PositiveOrZero\n    ");
                    }
                }
                if (maximumObj != null) {
                    double maximum = getDoubleValue(maximumObj);
                    if (maximum < 0) {
                        annotations.append("@Negative\n    ");
                    } else if (maximum == 0) {
                        annotations.append("@NegativeOrZero\n    ");
                    }
                }
            }


            // Handle array validations
            case "array" -> {
                // Min and max items together (preferred)
                Object minItemsObj = schema.get("minItems");
                Object maxItemsObj = schema.get("maxItems");
                if (minItemsObj != null && maxItemsObj != null) {
                    int minItems = getIntValue(minItemsObj);
                    int maxItems = getIntValue(maxItemsObj);
                    annotations.append("@Size(min = ").append(minItems).append(", max = ").append(maxItems).append(")\n    ");
                } else {
                    // Min items only
                    if (minItemsObj != null) {
                        int minItems = getIntValue(minItemsObj);
                        if (minItems > 0) {
                            annotations.append("@Size(min = ").append(minItems).append(")\n    ");
                        }
                    }

                    // Max items only
                    if (maxItemsObj != null) {
                        int maxItems = getIntValue(maxItemsObj);
                        annotations.append("@Size(max = ").append(maxItems).append(")\n    ");
                    }
                }
            }
        }

        return annotations.toString();
    }

    /**
     * Helper method to safely get int value from Object
     */
    private int getIntValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Helper method to safely get long value from Object
     */
    private long getLongValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Helper method to safely get double value from Object
     */
    private double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Convert OpenAPI type to Java type
     */
    // Thread-local visited set for getJavaType to prevent infinite recursion
    private final ThreadLocal<java.util.Set<Object>> getJavaTypeVisited = ThreadLocal.withInitial(() -> 
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    private String getJavaType(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }

        // Cycle detection for getJavaType
        java.util.Set<Object> visited = getJavaTypeVisited.get();
        if (visited.contains(schema)) {
            return "Object"; // Return default to break cycle
        }
        visited.add(schema);
        try {
            return getJavaTypeInternal(schema);
        } finally {
            visited.remove(schema);
        }
    }

    private String getJavaTypeInternal(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }

        // Check for $ref first (before type check, as $ref schemas may not have explicit type)
        String ref = (String) schema.get("$ref");
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
            return toJavaClassName(schemaRef);
        }

        String type = (String) schema.get("type");
        String format = (String) schema.get("format");

        switch (type) {
            case "string" -> {
                if ("date".equals(format)) {
                    return "XMLGregorianCalendar";
                } else if ("date-time".equals(format)) {
                    return "XMLGregorianCalendar";
                } else {
                    return "String";
                }
            }
            case "integer" -> {
                if ("int64".equals(format)) {
                    return "Long";
                } else {
                    return "Integer";
                }
            }
            case "number" -> {
                if ("float".equals(format)) {
                    return "Float";
                } else {
                    return "Double";
                }
            }
            case "boolean" -> {
                return "Boolean";
            }
            case "array" -> {
                if (schema.containsKey("items")) {
                    Object itemsObj = schema.get("items");
                    String itemType = null;

                    // Handle items as Map
                    if (itemsObj instanceof Map) {
                        Map<String, Object> items = Util.asStringObjectMap(itemsObj);

                        // First priority: Check for $ref in items
                        if (items.containsKey("$ref")) {
                            String itemsRef = (String) items.get("$ref");
                            if (itemsRef != null && itemsRef.startsWith("#/components/schemas/")) {
                                String schemaRef = itemsRef.substring(itemsRef.lastIndexOf("/") + 1);
                                itemType = toJavaClassName(schemaRef);
                            }
                        }

                        // Second priority: Use getJavaTypeInternal to resolve (it also checks for $ref)
                        if (itemType == null) {
                            itemType = getJavaTypeInternal(items);
                        }

                        // Fallback: If still Object but items has $ref, resolve it manually
                        if ("Object".equals(itemType) && items.containsKey("$ref")) {
                            String itemsRef = (String) items.get("$ref");
                            if (itemsRef != null && itemsRef.startsWith("#/components/schemas/")) {
                                String schemaRef = itemsRef.substring(itemsRef.lastIndexOf("/") + 1);
                                itemType = toJavaClassName(schemaRef);
                            }
                        }
                    } else {
                        // If items is not a Map, try to get type from it directly
                        itemType = getJavaTypeInternal(schema);
                        if (itemType != null && itemType.startsWith("List<")) {
                            // Already a List type, extract inner type
                            itemType = itemType.substring(5, itemType.length() - 1);
                        }
                    }

                    // Return the resolved type or default to Object
                    return "List<" + (itemType != null ? itemType : "Object") + ">";
                } else {
                    return "List<Object>";
                }
            }
            case "object" -> {
                // Check if this is an in-lined schema
                if (inlinedSchemas.containsKey(schema)) {
                    return inlinedSchemas.get(schema);
                }
                return "Object";
            }
            case null, default -> {
                return "Object";
            }
        }
    }

    /**
     * Convert snake_case to camelCase
     */
    private String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i]));
        }
        return result.toString();
    }

    /**
     * Capitalize first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    /**
     * Check if schema is an error schema
     */
    private boolean isErrorSchema(String schemaName) {
        if (schemaName == null) {
            return false;
        }

        String lowerName = schemaName.toLowerCase(Locale.ROOT);
        return lowerName.contains("error") ||
                lowerName.contains("exception") ||
                lowerName.contains("fault");
    }

    /**
     * Sanitize parameter name to be a valid Java identifier
     */
    private String sanitizeParameterName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        if (isValidJavaIdentifier(name)) {
            return name;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (i == 0) {
                if (Character.isLetter(c) || c == '_' || c == '$') {
                    result.append(c);
                } else if (Character.isDigit(c)) {
                    result.append('_').append(c);
                } else {
                    capitalizeNext = true;
                }
            } else {
                if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                    if (capitalizeNext) {
                        result.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        result.append(c);
                    }
                } else if (c == '-' || c == '.' || c == ' ') {
                    capitalizeNext = true;
                }
            }
        }

        String sanitized = result.toString();

        if (sanitized.isEmpty() || !isValidJavaIdentifier(sanitized)) {
            return null;
        }

        return sanitized;
    }

    /**
     * Check if a string is a valid Java identifier
     */
    private boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }

        return !isJavaKeyword(name);
    }

    /**
     * Check if a string is a Java keyword
     */
    private boolean isJavaKeyword(String name) {
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "package",
                "private", "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws", "transient",
                "try", "void", "volatile", "while", "true", "false", "null"
        };

        for (String keyword : keywords) {
            if (keyword.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert schema name to valid Java class name
     */
    private String toJavaClassName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "Unknown";
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : schemaName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else if (c == '-' || c == '_' || c == ' ' || c == '.') {
                capitalizeNext = true;
            }
        }

        if (result.isEmpty() || !Character.isLetter(result.charAt(0))) {
            return "Schema" + result;
        }

        return result.toString();
    }

    /**
     * Generate query parameter validators (QueryParamValidators.java and ValidationMapHelper.java)
     */
    private void generateQueryParamValidators(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        // Extract server base path for full path construction
        String serverBasePath = extractServerBasePath(spec);

        // Collect all endpoint validators
        List<EndpointValidator> validators = new ArrayList<>();
        Map<String, Integer> methodNameCounters = new LinkedHashMap<>();

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Build full path with server base path
            String fullPath = buildFullPath(serverBasePath, path);

            // Process each HTTP method
            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    // Extract parameters
                    List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
                    if (params == null) {
                        params = new ArrayList<>();
                    }

                    // Get operation ID for method name
                    String operationId = (String) operation.get("operationId");
                    String methodName = generateValidatorMethodName(operationId, method, path, methodNameCounters);

                    // Generate validator method content
                    String validatorMethod = generateValidatorMethod(methodName, params);

                    validators.add(new EndpointValidator(fullPath, method.toUpperCase(Locale.ROOT), methodName, validatorMethod));
                }
            }
        }

        // Generate QueryParamValidators.java
        generateQueryParamValidatorsFile(outputDir, validators);

        // Generate ValidationMapHelper.java
        generateValidationMapHelperFile(outputDir, validators);
    }

    /**
     * Build full path from server base path and relative path
     */
    private String buildFullPath(String serverBasePath, String relativePath) {
        String normalizedRelative = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        if (serverBasePath != null && !serverBasePath.isEmpty()) {
            if (serverBasePath.endsWith("/")) {
                return serverBasePath + normalizedRelative.substring(1);
            } else {
                return serverBasePath + normalizedRelative;
            }
        }
        return normalizedRelative;
    }

    /**
     * Generate validator method name from operation ID, HTTP method, and path
     */
    private String generateValidatorMethodName(String operationId, String method, String path, Map<String, Integer> counters) {
        String baseName;

        if (operationId != null && !operationId.isEmpty()) {
            // Use operation ID as base - keep original case (camelCase)
            baseName = operationId;
        } else {
            // Generate from path and method
            String pathPart = path.replaceAll("[^a-zA-Z0-9]", "");
            // Convert to camelCase: first letter lowercase, rest as-is
            if (pathPart.isEmpty()) {
                baseName = method.toLowerCase(Locale.ROOT);
            } else {
                baseName = method.toLowerCase(Locale.ROOT) + pathPart.substring(0, 1).toUpperCase(Locale.ROOT) +
                        (pathPart.length() > 1 ? pathPart.substring(1) : "");
            }
        }

        // Handle duplicates
        String key = baseName + method.toUpperCase(Locale.ROOT);
        int count = counters.getOrDefault(key, 0);
        counters.put(key, count + 1);

        if (count > 0) {
            baseName = baseName + "_" + count;
        }

        return baseName + method.toUpperCase(Locale.ROOT) + "Parameter_" + (count + 1);
    }

    /**
     * Generate validator method content
     */
    private String generateValidatorMethod(String methodName, List<Map<String, Object>> params) {
        // Reset counter for this method
        argCounter = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("  public static ValidationBuilder<RequestInfo> ").append(methodName).append("() {\n");
        sb.append("    ValidationBuilder<RequestInfo> v = new ValidationBuilder<>();\n");

        List<String> allowedParams = new ArrayList<>();

        // Process parameters
        for (Map<String, Object> param : params) {
            String name = (String) param.get("name");
            String in = (String) param.get("in");
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            boolean isRequired = param.containsKey("required") ? (Boolean) param.get("required") : false;

            if (name == null || in == null || schema == null) continue;

            // Skip header parameters
            if ("header".equals(in)) {
                continue;
            }

            // Add to allowed parameters list for query params
            if ("query".equals(in)) {
                allowedParams.add(name);
            }

            // Generate validators based on schema
            generateParameterValidators(sb, name, in, schema, isRequired);
        }

        // Add allowed parameters validator
        if (!allowedParams.isEmpty()) {
            sb.append("    List<String> allowedParameters = List.of(");
            for (int i = 0; i < allowedParams.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(allowedParams.get(i)).append("\"");
            }
            sb.append(");\n");
            sb.append("    v.add(new AllowedParameterValidator(allowedParameters));\n");
        } else {
            sb.append("    v.add(new AllowedParameterValidator(Collections.emptyList()));\n");
        }

        sb.append("    return v;\n");
        sb.append("  }\n");

        return sb.toString();
    }

    /**
     * Generate validators for a single parameter
     */
    private void generateParameterValidators(StringBuilder sb, String paramName, String paramType, Map<String, Object> schema, boolean isRequired) {
        String errorPrefix = "path".equals(paramType) ? "PATH_PARAM" : "QUERY_PARAM";

        // Required validator
        if (isRequired) {
            String errorCode = "path".equals(paramType)
                    ? "L10N_INVALID_VALUE_FOR_PATH_PARAM_REQUIRED"
                    : "I18N_REQUIRED_QUERY_PARAM_MISSING";
            sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName).append("\");\n");
            sb.append("    v.add(new IsRequiredValidator(\"").append(paramName).append("\", \"").append(paramName)
                    .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                    .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
        }

        String type = (String) schema.get("type");
        if (type == null) return;

        // String validations
        if ("string".equals(type)) {
            // Max length
            if (schema.containsKey("maxLength")) {
                Object maxLength = schema.get("maxLength");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_MORE_THAN_MAX_LEN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(maxLength).append("\");\n");
                sb.append("    v.add(new MaxLengthValidator(\"").append(paramName).append("\", \"").append(maxLength)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Min length
            if (schema.containsKey("minLength")) {
                Object minLength = schema.get("minLength");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_LESS_THAN_MIN_LEN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(minLength).append("\");\n");
                sb.append("    v.add(new MinLengthValidator(\"").append(paramName).append("\", \"").append(minLength)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Pattern
            if (schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_PATTERN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(escapeJavaString(pattern)).append("\");\n");
                sb.append("    v.add(new PatternValidator(\"").append(paramName).append("\", \"").append(escapeJavaString(pattern))
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Enum
            List<?> enumValues = schema.get("enum") instanceof List<?> list ? list : null;
            if (enumValues != null && !enumValues.isEmpty()) {
                boolean isArray = schema.containsKey("x-array") || paramName.contains("[");
                StringBuilder enumStr = new StringBuilder();
                for (int i = 0; i < enumValues.size(); i++) {
                    if (i > 0) enumStr.append(",");
                    enumStr.append(enumValues.get(i).toString());
                }
                sb.append("    v.add(new EnumValidator(\"").append(paramName).append("\", \"").append(enumStr)
                        .append("\", \"L10N_INVALID_VALUE_FOR_ENUM_ATTRIBUTE\", Collections.emptyList(), Collections.emptyList(), \"")
                        .append(paramType).append("\" ,").append(isArray).append("));\n");
            }
        }

        // Number validations
        if ("integer".equals(type) || "number".equals(type)) {
            // Maximum
            if (schema.containsKey("maximum")) {
                Object maximum = schema.get("maximum");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_MORE_THAN_MAX";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(maximum).append("\");\n");
                sb.append("    v.add(new NumericMaxValidator(\"").append(paramName).append("\", \"").append(maximum)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Minimum
            if (schema.containsKey("minimum")) {
                Object minimum = schema.get("minimum");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_LESS_THAN_MIN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(minimum).append("\");\n");
                sb.append("    v.add(new NumericMinValidator(\"").append(paramName).append("\", \"").append(minimum)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }
        }

        // Array validations
        if ("array".equals(type)) {
            // Max items
            if (schema.containsKey("maxItems")) {
                Object maxItems = schema.get("maxItems");
                sb.append("    v.add(new ArrayMaxItemsValidators(\"").append(paramName).append("\", ")
                        .append(maxItems).append(", \"L10N_INVALID_VALUE_FOR_ARRAY_MAX_ITEMS\", Collections.emptyList(), Collections.emptyList(), \"")
                        .append(paramType).append("\",false));\n");
            }

            // Min items
            if (schema.containsKey("minItems")) {
                Object minItems = schema.get("minItems");
                sb.append("    v.add(new ArrayMinItemsValidator(\"").append(paramName).append("\", ")
                        .append(minItems).append(", \"L10N_INVALID_VALUE_FOR_ARRAY_MIN_ITEMS\", Collections.emptyList(), Collections.emptyList(), \"")
                        .append(paramType).append("\",false));\n");
            }
        }

        // Boolean validator
        if ("boolean".equals(type)) {
            sb.append("    v.add(new BooleanValidator(\"").append(paramName)
                    .append("\", \"L10N_INVALID_VALUE_FOR_BOOLEAN\", Collections.emptyList(), Collections.emptyList(), \"")
                    .append(paramType).append("\",false));\n");
        }
    }

    // Counter for argument lists in validator methods
    private int argCounter = 0;

    private int getNextArgCounter() {
        return ++argCounter;
    }

    private int getCurrentArgCounter() {
        return argCounter;
    }

    /**
     * Escape Java string for use in generated code
     */
    private String escapeJavaString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generate QueryParamValidators.java file
     */
    private void generateQueryParamValidatorsFile(String outputDir, List<EndpointValidator> validators) throws IOException {
        // Use the package from the old tool: egain.ws.oas.gen
        String validatorPackage = "egain.ws.oas.gen";

        StringBuilder content = new StringBuilder();
        content.append("package ").append(validatorPackage).append(";\n\n");
        content.append("import com.egain.platform.framework.validation.ValidationBuilder;\n");
        content.append("import egain.ws.oas.RequestInfo;\n");
        content.append("import egain.ws.oas.validation.AllowedParameterValidator;\n");
        content.append("import egain.ws.oas.validation.ArrayMaxItemsValidators;\n");
        content.append("import egain.ws.oas.validation.ArrayMinItemsValidator;\n");
        content.append("import egain.ws.oas.validation.BooleanValidator;\n");
        content.append("import egain.ws.oas.validation.EnumValidator;\n");
        content.append("import egain.ws.oas.validation.FormatValidator;\n");
        content.append("import egain.ws.oas.validation.IsRequiredValidator;\n");
        content.append("import egain.ws.oas.validation.MaxLengthValidator;\n");
        content.append("import egain.ws.oas.validation.MinLengthValidator;\n");
        content.append("import egain.ws.oas.validation.NumericMaxValidator;\n");
        content.append("import egain.ws.oas.validation.NumericMinValidator;\n");
        content.append("import egain.ws.oas.validation.PatternValidator;\n");
        content.append("import java.lang.String;\n");
        content.append("import java.util.Collections;\n");
        content.append("import java.util.List;\n\n");
        content.append("public class QueryParamValidators {\n");

        // Generate all validator methods
        for (EndpointValidator validator : validators) {
            content.append(validator.validatorMethod).append("\n");
        }

        content.append("}\n");

        // Write to proper package directory under src/main/java
        String validatorPackagePath = "egain/ws/oas/gen";
        writeFile(outputDir + "/src/main/java/" + validatorPackagePath + "/QueryParamValidators.java", content.toString());
    }

    /**
     * Generate ValidationMapHelper.java file
     */
    private void generateValidationMapHelperFile(String outputDir, List<EndpointValidator> validators) throws IOException {
        // Use the package from the old tool: egain.ws.oas.gen
        String validatorPackage = "egain.ws.oas.gen";

        StringBuilder content = new StringBuilder();
        content.append("package ").append(validatorPackage).append(";\n\n");
        content.append("import com.egain.platform.framework.validation.ValidationBuilder;\n");
        content.append("import egain.ws.oas.RequestInfo;\n");
        content.append("import egain.ws.oas.Validations.ParameterValidatorMapKey;\n");
        content.append("import java.util.Map;\n");
        content.append("import java.util.function.Supplier;\n\n");
        content.append("public class ValidationMapHelper {\n");
        content.append("  public static final Map<ParameterValidatorMapKey, Supplier<ValidationBuilder<RequestInfo>>> validationsListMap = Map.<ParameterValidatorMapKey, Supplier<ValidationBuilder<RequestInfo>>> ofEntries(\n");

        // Generate map entries
        for (int i = 0; i < validators.size(); i++) {
            EndpointValidator validator = validators.get(i);
            content.append("    Map.entry(new ParameterValidatorMapKey(\"").append(validator.path)
                    .append("\", \"").append(validator.httpMethod).append("\"), QueryParamValidators::")
                    .append(validator.methodName);
            if (i < validators.size() - 1) {
                content.append(") ,\n");
            } else {
                content.append(")\n");
            }
        }

        content.append("  );\n\n");
        content.append("  /**\n");
        content.append("   * Validate request parameters for a given path and HTTP method\n");
        content.append("   * This method can be called from resources or at the beginning of business logic\n");
        content.append("   * \n");
        content.append("   * @param path The request path\n");
        content.append("   * @param httpMethod The HTTP method (GET, POST, PUT, DELETE, PATCH)\n");
        content.append("   * @param requestInfo The RequestInfo object containing path and query parameters\n");
        content.append("   * @return ValidationError if validation fails, null if validation passes\n");
        content.append("   */\n");
        content.append("  public static com.egain.platform.framework.validation.ValidationError validate(\n");
        content.append("      String path, String httpMethod, egain.ws.oas.RequestInfo requestInfo) {\n");
        content.append("    ParameterValidatorMapKey key = new ParameterValidatorMapKey(path, httpMethod);\n");
        content.append("    Supplier<ValidationBuilder<RequestInfo>> supplier = validationsListMap.get(key);\n");
        content.append("    if (supplier != null) {\n");
        content.append("      ValidationBuilder<RequestInfo> validationBuilder = supplier.get();\n");
        content.append("      return validationBuilder.validate(requestInfo);\n");
        content.append("    }\n");
        content.append("    return null;\n");
        content.append("  }\n");
        content.append("}\n");

        // Write to proper package directory under src/main/java
        String validatorPackagePath = "egain/ws/oas/gen";
        writeFile(outputDir + "/src/main/java/" + validatorPackagePath + "/ValidationMapHelper.java", content.toString());
    }

    /**
     * Generate Validation classes automatically in the output directory
     * Creates all required validator classes in the egain.ws.oas.validation package
     */
    private void generateValidationClasses(String outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String validationPackage = "egain.ws.oas.validation";
        String validationDir = outputDir + "/src/main/java/egain/ws/oas/validation";

        // Ensure target directory exists
        Files.createDirectories(Paths.get(validationDir));

        // Generate all validator classes
        generateIsRequiredValidator(validationDir, validationPackage);
        generatePatternValidator(validationDir, validationPackage);
        generateMaxLengthValidator(validationDir, validationPackage);
        generateMinLengthValidator(validationDir, validationPackage);
        generateNumericMaxValidator(validationDir, validationPackage);
        generateNumericMinValidator(validationDir, validationPackage);
        generateNumericMultipleOfValidator(validationDir, validationPackage);
        generateEnumValidator(validationDir, validationPackage);
        generateBooleanValidator(validationDir, validationPackage);
        generateFormatValidator(validationDir, validationPackage);
        generateAllowedParameterValidator(validationDir, validationPackage);
        generateArrayMaxItemsValidators(validationDir, validationPackage);
        generateArrayMinItemsValidator(validationDir, validationPackage);
        generateArrayUniqueItemsValidators(validationDir, validationPackage);
        generateArraySimpleStyleValidator(validationDir, validationPackage);
        generateIsAllowEmptyValueValidator(validationDir, validationPackage);
        generateIsAllowReservedValidator(validationDir, validationPackage);
    }

    /**
     * Generate IsRequiredValidator class
     */
    private void generateIsRequiredValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                
                public class IsRequiredValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                
                    private final String requiredParameter;
                
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public IsRequiredValidator(String parameterName, String requiredParameter, String l10nKey, List<String> arguments,
                        List<String> localizedArguments,
                        String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.requiredParameter = requiredParameter;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        if (nameSpace.equalsIgnoreCase("path") && !val.pathParameters().containsKey(requiredParameter))
                        {
                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                        }
                        if (nameSpace.equalsIgnoreCase("query") && !val.queryParameters().containsKey(requiredParameter))
                        {
                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsRequiredValidator.java", content);
    }

    /**
     * Generate PatternValidator class
     */
    private void generatePatternValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class PatternValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String val;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public PatternValidator(String parameterName, String val, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.val = val;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    if (!Validations.matchesPattern.apply(item, this.val))
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                if (!Validations.matchesPattern.apply(input, this.val))
                                {
                                    return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/PatternValidator.java", content);
    }

    /**
     * Generate MaxLengthValidator class
     */
    private void generateMaxLengthValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class MaxLengthValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String maxLength;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public MaxLengthValidator(String parameterName, String maxLength, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.maxLength = maxLength;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                int maxLen = Integer.parseInt(maxLength);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (item.length() > maxLen)
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (input.length() > maxLen)
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid maxLength value, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/MaxLengthValidator.java", content);
    }

    /**
     * Generate MinLengthValidator class
     */
    private void generateMinLengthValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class MinLengthValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String minLength;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public MinLengthValidator(String parameterName, String minLength, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.minLength = minLength;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                int minLen = Integer.parseInt(minLength);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (item.length() < minLen)
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (input.length() < minLen)
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid minLength value, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/MinLengthValidator.java", content);
    }

    /**
     * Generate NumericMaxValidator class
     */
    private void generateNumericMaxValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class NumericMaxValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String maximum;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public NumericMaxValidator(String parameterName, String maximum, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.maximum = maximum;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                double maxVal = Double.parseDouble(maximum);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        double val = Double.parseDouble(item.trim());
                                        if (val > maxVal)
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    double val = Double.parseDouble(input);
                                    if (val > maxVal)
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid number format, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/NumericMaxValidator.java", content);
    }

    /**
     * Generate NumericMinValidator class
     */
    private void generateNumericMinValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class NumericMinValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String minimum;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public NumericMinValidator(String parameterName, String minimum, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.minimum = minimum;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                double minVal = Double.parseDouble(minimum);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        double val = Double.parseDouble(item.trim());
                                        if (val < minVal)
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    double val = Double.parseDouble(input);
                                    if (val < minVal)
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid number format, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/NumericMinValidator.java", content);
    }

    /**
     * Generate NumericMultipleOfValidator class
     */
    private void generateNumericMultipleOfValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class NumericMultipleOfValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String multipleOf;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public NumericMultipleOfValidator(String parameterName, String multipleOf, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.multipleOf = multipleOf;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                double multiple = Double.parseDouble(multipleOf);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        double val = Double.parseDouble(item.trim());
                                        if (Math.abs(val %% multiple) > 0.0001)
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    double val = Double.parseDouble(input);
                                    if (Math.abs(val %% multiple) > 0.0001)
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid number format, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        // Replace %% with % for modulo operation
        content = content.replace("%%", "%");
        writeFile(outputDir + "/NumericMultipleOfValidator.java", content);
    }

    /**
     * Generate EnumValidator class
     */
    private void generateEnumValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class EnumValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String enumValues;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public EnumValidator(String parameterName, String enumValues, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.enumValues = enumValues;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            List<String> allowedValues = Arrays.asList(enumValues.split(","));
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    if (!allowedValues.contains(item.trim()))
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                if (!allowedValues.contains(input.trim()))
                                {
                                    return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/EnumValidator.java", content);
    }

    /**
     * Generate BooleanValidator class
     */
    private void generateBooleanValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class BooleanValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public BooleanValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    String trimmed = item.trim().toLowerCase(Locale.ROOT);
                                    if (!"true".equals(trimmed) && !"false".equals(trimmed))
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                String trimmed = input.trim().toLowerCase(Locale.ROOT);
                                if (!"true".equals(trimmed) && !"false".equals(trimmed))
                                {
                                    return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/BooleanValidator.java", content);
    }

    /**
     * Generate FormatValidator class
     */
    private void generateFormatValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                import java.util.regex.Pattern;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class FormatValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String format;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$");
                    private static final Pattern URI_PATTERN = Pattern.compile("^https?://.*");
                    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                
                    public FormatValidator(String parameterName, String format, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.format = format;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            Pattern pattern = getPatternForFormat(format);
                            if (pattern != null)
                            {
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (!pattern.matcher(item.trim()).matches())
                                        {
                                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (!pattern.matcher(input.trim()).matches())
                                    {
                                        return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                        }
                        return null;
                    }
                
                    private Pattern getPatternForFormat(String format)
                    {
                        if ("email".equalsIgnoreCase(format))
                        {
                            return EMAIL_PATTERN;
                        }
                        else if ("uri".equalsIgnoreCase(format) || "url".equalsIgnoreCase(format))
                        {
                            return URI_PATTERN;
                        }
                        else if ("uuid".equalsIgnoreCase(format))
                        {
                            return UUID_PATTERN;
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/FormatValidator.java", content);
    }

    /**
     * Generate AllowedParameterValidator class
     */
    private void generateAllowedParameterValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                
                public class AllowedParameterValidator implements ValidatorAction<RequestInfo>
                {
                    private final List<String> allowedParameters;
                
                    public AllowedParameterValidator(List<String> allowedParameters)
                    {
                        this.allowedParameters = allowedParameters;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        for (String param : val.queryParameters().keySet())
                        {
                            if (!allowedParameters.contains(param))
                            {
                                return ValidationErrorHelper.createValidationError("L10N_INVALID_QUERY_PARAMETER",
                                    List.of(param), List.of());
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/AllowedParameterValidator.java", content);
    }

    /**
     * Generate ArrayMaxItemsValidators class
     */
    private void generateArrayMaxItemsValidators(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class ArrayMaxItemsValidators implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final int maxItems;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public ArrayMaxItemsValidators(String parameterName, int maxItems, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.maxItems = maxItems;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            String[] items = input.split(",");
                            if (items.length > maxItems)
                            {
                                return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArrayMaxItemsValidators.java", content);
    }

    /**
     * Generate ArrayMinItemsValidator class
     */
    private void generateArrayMinItemsValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class ArrayMinItemsValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final int minItems;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public ArrayMinItemsValidator(String parameterName, int minItems, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.minItems = minItems;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            String[] items = input.split(",");
                            if (items.length < minItems)
                            {
                                return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArrayMinItemsValidator.java", content);
    }

    /**
     * Generate ArrayUniqueItemsValidators class
     */
    private void generateArrayUniqueItemsValidators(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.HashSet;
                import java.util.List;
                import java.util.Set;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class ArrayUniqueItemsValidators implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public ArrayUniqueItemsValidators(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            String[] items = input.split(",");
                            Set<String> seen = new HashSet<>();
                            for (String item : items)
                            {
                                String trimmed = item.trim();
                                if (seen.contains(trimmed))
                                {
                                    return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                                }
                                seen.add(trimmed);
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArrayUniqueItemsValidators.java", content);
    }

    /**
     * Generate ArraySimpleStyleValidator class
     */
    private void generateArraySimpleStyleValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                
                public class ArraySimpleStyleValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public ArraySimpleStyleValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        // Simple style validation - currently a placeholder
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArraySimpleStyleValidator.java", content);
    }

    /**
     * Generate IsAllowEmptyValueValidator class
     */
    private void generateIsAllowEmptyValueValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;
                
                public class IsAllowEmptyValueValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public IsAllowEmptyValueValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null && input.trim().isEmpty())
                        {
                            return ValidationErrorHelper.createValidationError(l10nKey, arguments, localizedArgs);
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsAllowEmptyValueValidator.java", content);
    }

    /**
     * Generate IsAllowReservedValidator class
     */
    private void generateIsAllowReservedValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;
                
                import java.util.ArrayList;
                import java.util.List;
                
                import com.egain.platform.framework.validation.ValidationError;
                import com.egain.platform.framework.validation.ValidationErrorHelper;
                import com.egain.platform.framework.validation.ValidatorAction;
                
                import egain.ws.oas.RequestInfo;
                
                public class IsAllowReservedValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;
                
                    public IsAllowReservedValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }
                
                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        // Reserved character validation - currently a placeholder
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsAllowReservedValidator.java", content);
    }

    /**
     * Helper class to store endpoint validator information
     */
    private static class EndpointValidator {
        String path;
        String httpMethod;
        String methodName;
        String validatorMethod;

        EndpointValidator(String path, String httpMethod, String methodName, String validatorMethod) {
            this.path = path;
            this.httpMethod = httpMethod;
            this.methodName = methodName;
            this.validatorMethod = validatorMethod;
        }
    }
}


