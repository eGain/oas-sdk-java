package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Generates JAX-RS resource (controller) classes from OpenAPI path definitions.
 * Each resource groups operations by their first path segment (parent path).
 */
class JerseyResourceGenerator {

    private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern.compile("(/v\\d+)");

    static final String X_EGAIN_RESOURCE_CLASS_NAME = "x-egain-resource-class-name";

    private final JerseyGenerationContext ctx;
    /**
     * Function to resolve an OpenAPI schema map to a Java type string.
     * Provided by JerseyGenerator until JerseyTypeUtils is extracted.
     */
    private final Function<Map<String, Object>, String> javaTypeResolver;

    JerseyResourceGenerator(JerseyGenerationContext ctx, Function<Map<String, Object>, String> javaTypeResolver) {
        this.ctx = ctx;
        this.javaTypeResolver = javaTypeResolver;
    }

    /**
     * Generate resource classes for all operations in the spec.
     */
    void generate() throws IOException, GenerationException {
        generateResources(ctx.spec, ctx.outputDir, ctx.packageName);
    }

    /**
     * Group paths by parent and generate one resource class per parent path.
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
     * Extract parent path (first segment) from a full path.
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
     * Extract API path with version from parent path, operations, and server URL.
     * Looks for version patterns like /v1, /v2, /v4 in the server URL or paths.
     * Examples:
     * parentPath="/prompts", server URL="/knowledge/contentmgr/v4"
     * -> "/knowledge/contentmgr/v4/prompts"
     * parentPath="/articles", no version -> "/articles"
     */
    private String extractApiPathWithVersion(String parentPath, List<PathOperation> operations, Map<String, Object> spec) {
        String serverBasePath = JerseyGenerationContext.extractServerBasePath(spec);

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
                java.util.regex.Matcher matcher = VERSION_PATTERN.matcher(firstPath);

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

    private static Map<String, String> securitySchemeToActorTypeMap() {
        Map<String, String> securitySchemeToActorType = new LinkedHashMap<>();
        securitySchemeToActorType.put("oAuthUser", "USER");
        securitySchemeToActorType.put("oAuthCustomer", "CUSTOMER");
        securitySchemeToActorType.put("oAuthAnonymousCustomer", "ANONYMOUS_CUSTOMER");
        securitySchemeToActorType.put("oAuthClient", "CLIENT_APP");
        securitySchemeToActorType.put("oAuthOnBehalfOfUser", "CLIENT_ON_BEHALF_OF_USER");
        securitySchemeToActorType.put("oAuthOnBehalfOfCustomer", "CLIENT_ON_BEHALF_OF_CUSTOMER");
        return securitySchemeToActorType;
    }

    /**
     * Extract security information from a single operation and generate {@code @Actor} annotation.
     */
    private String generateActorAnnotationForOperation(Map<String, Object> operation) {
        Set<String> actorTypes = new HashSet<>();
        Set<String> scopes = new HashSet<>();
        Map<String, String> securitySchemeToActorType = securitySchemeToActorTypeMap();

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

                            if (entry.getValue() instanceof List<?> scopeList) {
                                for (Object scope : scopeList) {
                                    if (scope instanceof String scopeStr) {
                                        scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
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

        if (actorTypes.isEmpty() && scopes.isEmpty()) {
            return "    @Actor\n";
        }

        StringBuilder actorAnnotation = new StringBuilder("    @Actor(type = { ");

        if (!actorTypes.isEmpty()) {
            List<String> sortedActorTypes = new ArrayList<>(actorTypes);
            Collections.sort(sortedActorTypes);
            for (int i = 0; i < sortedActorTypes.size(); i++) {
                if (i > 0) actorAnnotation.append(", ");
                actorAnnotation.append("ActorType.").append(sortedActorTypes.get(i));
            }
        } else {
            actorAnnotation.append("ActorType.CLIENT_APP");
        }

        actorAnnotation.append(" }, scope = {");

        if (!scopes.isEmpty()) {
            List<String> sortedScopes = new ArrayList<>(scopes);
            Collections.sort(sortedScopes);
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
     * Convert scope string to enum format.
     * Examples:
     * knowledge.contentmgr.read -> KNOWLEDGE_CONTENTMGR_READ
     * knowledge.contentmgr.onbehalfof.read -> KNOWLEDGE_CONTENTMGR_CLIENT_ON_BEHALF_OF_READ
     */
    static String convertScopeToEnum(String scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }

        String normalized = scope.replace("${SCOPE_PREFIX}", "");
        String[] parts = normalized.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if ("onbehalfof".equalsIgnoreCase(parts[i])) {
                parts[i] = "client_on_behalf_of";
            }
        }
        normalized = String.join(".", parts);

        String enumScope = normalized.replace(".", "_").replace("-", "_").toUpperCase(Locale.ROOT);
        enumScope = enumScope.replaceAll("[^A-Z0-9_]", "");

        return enumScope;
    }

    /**
     * Collect the complete set of {@code OAuthScope} enum constant names referenced by any
     * operation's security requirements across the spec. Used by the standalone-mode framework
     * generator to emit an {@code OAuthScope} enum containing exactly the constants the generated
     * {@code @Actor} annotations reference (so the generated code compiles).
     */
    static Set<String> collectOAuthScopes(Map<String, Object> spec) {
        Set<String> scopes = new TreeSet<>();
        if (spec == null) {
            return scopes;
        }
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return scopes;
        }
        String[] methods = {"get", "post", "put", "delete", "patch"};
        for (Object pathItemObj : paths.values()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathItemObj);
            if (pathItem == null) {
                continue;
            }
            for (String method : methods) {
                collectScopesFromOperation(Util.asStringObjectMap(pathItem.get(method)), scopes);
            }
        }
        return scopes;
    }

    /**
     * Mirror of the scope-extraction logic in {@link #generateActorAnnotationForOperation} so the
     * collected enum constants stay identical to what the annotations emit.
     */
    private static void collectScopesFromOperation(Map<String, Object> operation, Set<String> scopes) {
        if (operation == null || !operation.containsKey("security")) {
            return;
        }
        List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));
        if (securityList == null) {
            return;
        }
        for (Map<String, Object> securityMap : securityList) {
            if (securityMap == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : securityMap.entrySet()) {
                if (entry.getValue() instanceof List<?> scopeList) {
                    for (Object scope : scopeList) {
                        if (scope instanceof String scopeStr) {
                            scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
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

    /**
     * The complete set of {@code ActorType} enum constant names the generated {@code @Actor}
     * annotations can reference (the distinct values of the security-scheme → actor-type mapping,
     * which already includes the {@code CLIENT_APP} default).
     */
    static Set<String> collectActorTypes() {
        return new TreeSet<>(securitySchemeToActorTypeMap().values());
    }

    private void appendClassLevelMediaAnnotations(StringBuilder content, List<PathOperation> operations) {
        boolean jsonOnlyConfig = ctx.config != null && ctx.config.isJsonOnlyResourceMediaTypes();
        Set<String> collected = new LinkedHashSet<>();
        for (PathOperation po : operations) {
            collectMediaTypesFromOperation(po.operation, ctx.spec, collected);
        }

        boolean hasXml = collected.stream().anyMatch(JerseyResourceGenerator::mediaTypeImpliesXml);

        if (jsonOnlyConfig) {
            content.append("@Produces(MediaType.APPLICATION_JSON)\n");
            content.append("@Consumes(MediaType.APPLICATION_JSON)\n");
            return;
        }

        if (collected.isEmpty()) {
            content.append("@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
            content.append("@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
            return;
        }

        if (hasXml) {
            content.append("@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
            content.append("@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
        } else {
            content.append("@Produces(MediaType.APPLICATION_JSON)\n");
            content.append("@Consumes(MediaType.APPLICATION_JSON)\n");
        }
    }

    private static boolean mediaTypeImpliesXml(String mediaType) {
        return mediaType != null && mediaType.toLowerCase(Locale.ROOT).contains("xml");
    }

    private void collectMediaTypesFromOperation(Map<String, Object> operation, Map<String, Object> spec, Set<String> out) {
        if (operation == null) {
            return;
        }
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody != null) {
            extractContentMediaTypeKeys(requestBody, out);
        }
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses != null) {
            for (Object respObj : responses.values()) {
                Map<String, Object> resp = Util.asStringObjectMap(respObj);
                if (resp == null) {
                    continue;
                }
                if (resp.containsKey("$ref") && spec != null) {
                    resp = resolveResponseRef(resp, spec);
                }
                extractContentMediaTypeKeys(resp, out);
            }
        }
    }

    private static void extractContentMediaTypeKeys(Map<String, Object> holder, Set<String> out) {
        Map<String, Object> content = Util.asStringObjectMap(holder.get("content"));
        if (content != null) {
            out.addAll(content.keySet());
        }
    }

    private static Map<String, Object> resolveResponseRef(Map<String, Object> resp, Map<String, Object> spec) {
        String ref = (String) resp.get("$ref");
        if (ref == null || !ref.startsWith("#/components/responses/")) {
            return resp;
        }
        String name = ref.substring("#/components/responses/".length());
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return resp;
        }
        Map<String, Object> responses = Util.asStringObjectMap(components.get("responses"));
        if (responses == null) {
            return resp;
        }
        Map<String, Object> resolved = Util.asStringObjectMap(responses.get(name));
        return resolved != null ? resolved : resp;
    }

    /**
     * Generate resource for a parent path with all its operations.
     */
    private void generateResourceForParentPath(String parentPath, List<PathOperation> operations,
                                               String outputDir, String packagePath, Map<String, Object> spec) throws IOException, GenerationException {
        String resourceName = generateResourceName(parentPath, spec);

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".resources;\n\n");
        content.append("import ").append(ctx.getWsNs()).append(".*;\n");
        content.append("import ").append(ctx.getWsNs()).append(".core.MediaType;\n");
        content.append("import ").append(ctx.getWsNs()).append(".core.Response;\n");
        content.append("import ").append(packagePath).append(".service.*;\n");
        content.append("import ").append(packagePath).append(".model.*;\n");
        content.append("import egain.framework.Actor;\n");
        content.append("import egain.framework.ActorType;\n");
        content.append("import egain.framework.OAuthScope;\n");

        boolean needsListImport = false;
        StringBuilder body = new StringBuilder();
        for (PathOperation pathOp : operations) {
            String relativePath = getRelativePath(parentPath, pathOp.path);
            try {
                if (generateResourceMethod(pathOp.method, pathOp.operation, relativePath, body)) {
                    needsListImport = true;
                }
            } catch (GenerationException e) {
                throw new GenerationException("Failed to generate resource method for " + pathOp.method + " " + pathOp.path + ": " + e.getMessage(), e);
            }
        }

        if (needsListImport) {
            content.append("import java.util.List;\n");
        }
        content.append("\n");

        // Extract API version from path and construct @Path
        String apiPath = extractApiPathWithVersion(parentPath, operations, spec);
        content.append("@Path(\"").append(apiPath).append("\")\n");
        appendClassLevelMediaAnnotations(content, operations);

        content.append("public class ").append(resourceName).append(" {\n\n");
        content.append(body);
        content.append("}\n");

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/resources/" + resourceName + ".java", content.toString());
    }

    /**
     * Get relative path from parent path to full path.
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
     * Generate resource method.
     *
     * @return true if the signature uses {@link List} and {@code import java.util.List} is required
     */
    private boolean generateResourceMethod(String method, Map<String, Object> operation, String relativePath, StringBuilder content) throws GenerationException {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        String httpMethod = method.toUpperCase(Locale.ROOT);

        content.append("    @").append(httpMethod).append("\n");

        if (relativePath != null && !relativePath.isEmpty()) {
            content.append("    @Path(\"").append(relativePath).append("\")\n");
        }

        boolean hasRequestBody = false;
        if (method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch")) {
            Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
            hasRequestBody = requestBody != null;
        }
        if ((method.equalsIgnoreCase("get") || method.equalsIgnoreCase("delete") || method.equalsIgnoreCase("head")) && !hasRequestBody) {
            content.append("    @Consumes\n");
        }

        content.append(generateActorAnnotationForOperation(operation));

        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> parameterList = new ArrayList<>();
        boolean needsList = false;

        if (hasRequestBody) {
            parameterList.add("Object requestBody");
        }

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                if ("header".equals(in)) {
                    continue;
                }

                if (name != null && in != null && schema != null) {
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName == null) {
                        throw new GenerationException("The endpoint request param contains the invalid variable name: " + name +
                                " in path: " + relativePath + ". Parameter names must be valid Java identifiers.");
                    }

                    String javaType = javaTypeResolver.apply(schema);
                    if (javaType != null && javaType.contains("List<")) {
                        needsList = true;
                    }
                    String annotation = getParameterAnnotation(in);

                    String paramBuilder = annotation + "(\"" + name + "\") " +
                            javaType + " " + sanitizedName;

                    parameterList.add(paramBuilder);
                }
            }
        }

        String methodName = (operationId != null && !operationId.isEmpty()) ? JerseyNamingUtils.toJavaMethodName(operationId) : method;
        content.append("    public Response ").append(methodName).append("(");
        if (!parameterList.isEmpty()) {
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

        return needsList;
    }

    /**
     * Get parameter annotation based on parameter location.
     */
    private String getParameterAnnotation(String in) {
        return switch (in.toLowerCase(Locale.ROOT)) {
            case "path" -> "@PathParam";
            case "header" -> "@HeaderParam";
            default -> "@QueryParam";
        };
    }

    private String findResourceClassNameExtension(String parentPath, Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return null;
        }
        String norm = parentPath.startsWith("/") ? parentPath : "/" + parentPath;
        for (Map.Entry<String, Object> e : paths.entrySet()) {
            String p = e.getKey();
            if (!p.equals(norm) && !p.startsWith(norm + "/")) {
                continue;
            }
            Map<String, Object> item = Util.asStringObjectMap(e.getValue());
            if (item == null) {
                continue;
            }
            Object ext = item.get(X_EGAIN_RESOURCE_CLASS_NAME);
            if (ext instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }

    /**
     * Generate resource class name from parent path, optional {@value #X_EGAIN_RESOURCE_CLASS_NAME}, and simple plural heuristic.
     */
    private String generateResourceName(String parentPath, Map<String, Object> spec) {
        String fromExtension = findResourceClassNameExtension(parentPath, spec);
        if (fromExtension != null && !fromExtension.isEmpty()) {
            String simple = fromExtension.replaceAll("[^a-zA-Z0-9_]", "");
            if (!simple.isEmpty()) {
                return simple.substring(0, 1).toUpperCase(Locale.ROOT) + simple.substring(1);
            }
        }

        String name = parentPath != null ? parentPath.replaceAll("[^a-zA-Z0-9]", "") : "";
        if (name.isEmpty()) {
            return "RootResource";
        }
        String base = singularizeResourceSegment(name.toLowerCase(Locale.ROOT));
        return base.substring(0, 1).toUpperCase(Locale.ROOT) + base.substring(1) + "Resource";
    }

    /**
     * Simple plural-to-singular for resource segment (e.g. folders -> folder). Conservative: only strips trailing {@code s} when length &gt; 4 and not {@code ...ss}.
     */
    private static String singularizeResourceSegment(String segment) {
        if (segment.length() > 4 && segment.endsWith("s") && !segment.endsWith("ss")) {
            return segment.substring(0, segment.length() - 1);
        }
        return segment;
    }

    /**
     * Helper class to store path and operation information.
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
}
