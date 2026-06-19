package egain.oassdk.generators.nodejs;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.File;
import java.util.logging.Logger;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Express.js code generator with full feature parity.
 *
 * <p><b>Architecture note:</b> {@link #generate} already orchestrates route, model, middleware,
 * validator, and build emission via private methods (~970 lines). Future refactors should
 * extract {@code ExpressRouteGenerator}, {@code ExpressModelGenerator}, and
 * {@code ExpressBuildGenerator} sub-classes mirroring the Jersey generator layout to reduce
 * bridge-node coupling reported by the codebase graph.
 */
public class ExpressGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(ExpressGenerator.class);

    private GeneratorConfig config;
    private final Map<Object, String> inlinedSchemas = new IdentityHashMap<>();

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        this.config = config;

        try {
            // Create directory structure
            createDirectoryStructure(outputDir, packageName);

            // Collect inline schemas before generating models
            collectInlinedSchemas(spec);

            // Generate routes
            generateRoutes(spec, outputDir, packageName);

            // Generate models
            generateModels(spec, outputDir, packageName);

            // Generate middleware (including security and observability)
            generateMiddleware(spec, outputDir, packageName);

            // Generate validators
            generateValidators(spec, outputDir, packageName);

            // Generate main app file
            generateApp(spec, outputDir, packageName);

            // Generate package.json
            generatePackageJson(spec, outputDir, packageName);

            // Generate README
            generateReadme(spec, outputDir, packageName);

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Express.js code: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Express.js code: " + e.getMessage(), e);
        }
    }

    /**
     * Create directory structure for Express app
     */
    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        boolean routesB = new File(basePath + "/routes").mkdirs();
        boolean modelsB = new File(basePath + "/models").mkdirs();
        boolean middleWareB = new File(basePath + "/middleware").mkdirs();
        boolean validatorsB = new File(basePath + "/validators").mkdirs();
        boolean servicesB = new File(basePath + "/services").mkdirs();

        logger.fine("Directory creation results - routes: " + routesB + ", models: " + modelsB + 
                ", middleware: " + middleWareB + ", validators: " + validatorsB + ", services: " + servicesB);
    }

    /**
     * Collect inline schemas from responses
     */
    private void collectInlinedSchemas(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    collectInlinedSchemasFromOperation(operation);
                }
            }
        }
    }

    /**
     * Collect inline schemas from a single operation
     */
    private void collectInlinedSchemasFromOperation(Map<String, Object> operation) {
        if (operation == null) return;

        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) return;

        for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
            Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
            if (response == null) continue;

            Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
            if (content == null) continue;

            for (Map.Entry<String, Object> contentEntry : content.entrySet()) {
                Map<String, Object> mediaType = Util.asStringObjectMap(contentEntry.getValue());
                if (mediaType == null) continue;

                Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
                if (isInlineSchema(schema)) {
                    String schemaName = generateInlineSchemaName(operation, responseEntry.getKey());
                    inlinedSchemas.put(schema, schemaName);
                }
            }
        }
    }

    /**
     * Check if schema is inline (not a reference)
     */
    private boolean isInlineSchema(Map<String, Object> schema) {
        return schema != null && !schema.containsKey("$ref") && schema.containsKey("type");
    }

    /**
     * Generate name for inline schema
     */
    private String generateInlineSchemaName(Map<String, Object> operation, String responseCode) {
        String operationId = (String) operation.get("operationId");
        if (operationId != null) {
            return toPascalCase(operationId) + "Response" + responseCode;
        }
        return "InlineResponse" + responseCode;
    }

    /**
     * Generate Express routes
     */
    private void generateRoutes(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        // Extract server base path
        String serverBasePath = extractServerBasePath(spec);

        // Group paths by parent path
        Map<String, List<PathOperation>> groupedPaths = new LinkedHashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String parentPath = getParentPath(path);
            groupedPaths.computeIfAbsent(parentPath, k -> new ArrayList<>());

            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    groupedPaths.get(parentPath).add(new PathOperation(path, method, operation));
                }
            }
        }

        // Generate route file for each parent path
        for (Map.Entry<String, List<PathOperation>> entry : groupedPaths.entrySet()) {
            generateRouteFile(entry.getKey(), entry.getValue(), outputDir, packageName, serverBasePath);
        }
    }

    /**
     * Extract server base path from OpenAPI spec
     */
    private String extractServerBasePath(Map<String, Object> spec) {
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> firstServer = servers.getFirst();
            String url = (String) firstServer.get("url");
            if (url != null) {
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                } catch (URISyntaxException e) {
                    return "";
                }
            }
        }
        return "";
    }

    /**
     * Build full path from server base path and relative path
     */
    private String buildFullPath(String serverBasePath, String relativePath) {
        String s = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        if (serverBasePath != null && !serverBasePath.isEmpty()) {
            String normalizedRelative = s;
            if (serverBasePath.endsWith("/")) {
                return serverBasePath + normalizedRelative.substring(1);
            } else {
                return serverBasePath + normalizedRelative;
            }
        }
        return s;
    }

    /**
     * Get parent path from full path
     */
    private String getParentPath(String path) {
        if (path == null || path.isEmpty()) return "/";

        String[] parts = path.split("/");
        if (parts.length <= 2) return "/";

        return "/" + parts[1];
    }

    /**
     * Get relative path from parent
     */
    private String getRelativePath(String parentPath, String fullPath) {
        if (fullPath.startsWith(parentPath)) {
            String relative = fullPath.substring(parentPath.length());
            return relative.isEmpty() ? "" : relative;
        }
        return fullPath;
    }

    /**
     * Generate route file for a parent path
     */
    private void generateRouteFile(String parentPath, List<PathOperation> operations,
                                   String outputDir, String packageName, String serverBasePath) throws IOException {
        String routeName = generateRouteName(parentPath);
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        StringBuilder content = new StringBuilder();
        content.append("const express = require('express');\n");
        content.append("const router = express.Router();\n");

        // Add security middleware import if needed
        if (hasSecurityRequirements(operations)) {
            content.append("const { authenticate, checkScopes } = require('../middleware/auth');\n");
        }

        // Add validator imports if needed
        content.append("const { validate, validators } = require('../middleware/validators');\n");
        content.append("\n");

        // Generate route handlers
        for (PathOperation pathOp : operations) {
            String fullOperationPath = serverBasePath.isEmpty() ? pathOp.path : buildFullPath(serverBasePath, pathOp.path);
            String relativePath = getRelativePath(parentPath, fullOperationPath);
            generateRouteHandler(pathOp.method, pathOp.operation, relativePath, content);
        }

        content.append("\nmodule.exports = router;\n");

        writeFile(basePath + "/routes/" + routeName + ".js", content.toString());
    }

    /**
     * Generate route handler
     */
    private void generateRouteHandler(String method, Map<String, Object> operation,
                                      String relativePath, StringBuilder content) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String functionName = operationId != null ? toCamelCase(operationId) : method + "Handler";

        // Convert path params to Express format
        String expressPath = relativePath.isEmpty() ? "/" : convertToExpressPath(relativePath);

        // Extract parameters
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> queryParams = new ArrayList<>();

        if (params != null) {
            for (Map<String, Object> param : params) {
                String in = (String) param.get("in");
                if ("query".equals(in)) {
                    queryParams.add((String) param.get("name"));
                }
            }
        }

        // Extract security info
        SecurityInfo securityInfo = extractSecurityInfo(operation);

        // Start route definition
        content.append("/**\n");
        if (summary != null) {
            content.append(" * ").append(summary).append("\n");
        }
        content.append(" */\n");
        content.append("router.").append(method.toLowerCase(Locale.ROOT)).append("('").append(expressPath).append("'");

        // Add security middleware
        if (securityInfo != null && securityInfo.hasRequirements) {
            if (!securityInfo.scopes.isEmpty()) {
                content.append(", authenticate, checkScopes([");
                for (int i = 0; i < securityInfo.scopes.size(); i++) {
                    if (i > 0) content.append(", ");
                    content.append("'").append(securityInfo.scopes.get(i)).append("'");
                }
                content.append("])");
            } else {
                content.append(", authenticate");
            }
        }

        // Add validation middleware if there are query params
        if (!queryParams.isEmpty()) {
            content.append(", validate(validators.").append(functionName).append(")");
        }

        content.append(", async (req, res) => {\n");
        content.append("  try {\n");
        content.append("    // Implementation placeholder\n");
        content.append("    // Replace with actual business logic\n");
        content.append("    res.status(501).json({ message: 'Not implemented' });\n");
        content.append("  } catch (error) {\n");
        content.append("    console.error(error);\n");
        content.append("    res.status(500).json({ error: 'Internal server error' });\n");
        content.append("  }\n");
        content.append("});\n\n");
    }

    /**
     * Convert OpenAPI path to Express path format
     */
    private String convertToExpressPath(String path) {
        return path.replaceAll("\\{([^}]+)}", ":$1");
    }

    /**
     * Check if operations have security requirements
     */
    private boolean hasSecurityRequirements(List<PathOperation> operations) {
        for (PathOperation pathOp : operations) {
            if (pathOp.operation != null && pathOp.operation.containsKey("security")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract security information from operation
     */
    private SecurityInfo extractSecurityInfo(Map<String, Object> operation) {
        if (operation == null || !operation.containsKey("security")) {
            return null;
        }

        SecurityInfo info = new SecurityInfo(true);
        List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));

        if (securityList != null) {
            for (Map<String, Object> securityMap : securityList) {
                if (securityMap != null) {
                    for (Map.Entry<String, Object> entry : securityMap.entrySet()) {
                        if (entry.getValue() instanceof List<?> scopeList) {
                            for (Object scope : scopeList) {
                                if (scope instanceof String scopeStr) {
                                    scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
                                    if (!scopeStr.isEmpty()) {
                                        info.scopes.add(scopeStr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return info;
    }

    /**
     * Generate validators
     */
    private void generateValidators(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        StringBuilder content = new StringBuilder();
        content.append("const { query, param, body, validationResult } = require('express-validator');\n\n");
        content.append("// Validation middleware\n");
        content.append("const validate = (validations) => {\n");
        content.append("  return async (req, res, next) => {\n");
        content.append("    await Promise.all(validations.map(validation => validation.run(req)));\n\n");
        content.append("    const errors = validationResult(req);\n");
        content.append("    if (errors.isEmpty()) {\n");
        content.append("      return next();\n");
        content.append("    }\n\n");
        content.append("    res.status(400).json({ errors: errors.array() });\n");
        content.append("  };\n");
        content.append("};\n\n");
        content.append("// Validators for each endpoint\n");
        content.append("const validators = {\n");

        // Generate validators for each operation
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            boolean first = true;
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
                if (pathItem == null) continue;

                String[] methods = {"get", "post", "put", "delete", "patch"};
                for (String method : methods) {
                    if (pathItem.containsKey(method)) {
                        Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                        String operationId = (String) operation.get("operationId");
                        String functionName = operationId != null ? toCamelCase(operationId) : method + "Handler";

                        if (!first) content.append(",\n");
                        first = false;

                        content.append("  ").append(functionName).append(": ");
                        generateValidatorFunction(operation, content);
                    }
                }
            }
        }

        content.append("\n};\n\n");
        content.append("module.exports = { validate, validators };\n");

        writeFile(basePath + "/middleware/validators.js", content.toString());
    }

    /**
     * Generate validator function for an operation
     */
    private void generateValidatorFunction(Map<String, Object> operation, StringBuilder content) {
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));

        content.append("[\n");

        if (params != null) {
            boolean first = true;
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
                boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;

                if (name == null || in == null || schema == null) continue;
                if ("header".equals(in)) continue; // Skip headers

                if (!first) content.append(",\n");
                first = false;

                String validatorType = "query".equals(in) ? "query" : "param";
                content.append("    ").append(validatorType).append("('").append(name).append("')");

                // Add validations based on schema
                String type = (String) schema.get("type");

                if (required) {
                    content.append(".notEmpty().withMessage('").append(name).append(" is required')");
                }

                if ("string".equals(type)) {
                    if (schema.containsKey("minLength")) {
                        content.append(".isLength({ min: ").append(schema.get("minLength")).append(" })");
                    }
                    if (schema.containsKey("maxLength")) {
                        content.append(".isLength({ max: ").append(schema.get("maxLength")).append(" })");
                    }
                    if (schema.containsKey("pattern")) {
                        String pattern = (String) schema.get("pattern");
                        content.append(".matches(/").append(pattern.replace("/", "\\/")).append("/)");
                    }
                } else if ("integer".equals(type)) {
                    content.append(".isInt()");
                    if (schema.containsKey("minimum")) {
                        content.append(".isInt({ min: ").append(schema.get("minimum")).append(" })");
                    }
                    if (schema.containsKey("maximum")) {
                        content.append(".isInt({ max: ").append(schema.get("maximum")).append(" })");
                    }
                } else if ("number".equals(type)) {
                    content.append(".isFloat()");
                    if (schema.containsKey("minimum")) {
                        content.append(".isFloat({ min: ").append(schema.get("minimum")).append(" })");
                    }
                    if (schema.containsKey("maximum")) {
                        content.append(".isFloat({ max: ").append(schema.get("maximum")).append(" })");
                    }
                }
            }
        }

        content.append("\n  ]");
    }

    /**
     * Generate models
     */
    private void generateModels(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) return;

        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        // Generate model file for each schema
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            generateModelFile(schemaName, schema, basePath);
        }

        // Generate inline schemas
        for (Map.Entry<Object, String> entry : inlinedSchemas.entrySet()) {
            Map<String, Object> schema = Util.asStringObjectMap(entry.getKey());
            String schemaName = entry.getValue();
            generateModelFile(schemaName, schema, basePath);
        }
    }

    /**
     * Generate model file
     */
    private void generateModelFile(String schemaName, Map<String, Object> schema, String basePath) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("/**\n");
        content.append(" * ").append(schemaName).append(" model\n");
        content.append(" */\n");
        content.append("class ").append(schemaName).append(" {\n");
        content.append("  constructor(data = {}) {\n");

        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties != null) {
            for (String propName : properties.keySet()) {
                content.append("    this.").append(propName).append(" = data.").append(propName).append(";\n");
            }
        }

        content.append("  }\n\n");
        content.append("  toJSON() {\n");
        content.append("    return {\n");

        if (properties != null) {
            int i = 0;
            for (String propName : properties.keySet()) {
                if (i > 0) content.append(",\n");
                content.append("      ").append(propName).append(": this.").append(propName);
                i++;
            }
        }

        content.append("\n    };\n");
        content.append("  }\n");
        content.append("}\n\n");
        content.append("module.exports = ").append(schemaName).append(";\n");

        writeFile(basePath + "/models/" + schemaName + ".js", content.toString());
    }

    /**
     * Generate middleware
     */
    private void generateMiddleware(String outputDir, String packageName) throws IOException {
        generateMiddleware(null, outputDir, packageName);
    }

    private void generateMiddleware(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        // Determine service name for observability
        String obsServiceName = "express-api";
        if (spec != null) {
            Map<String, Object> obsInfo = Util.asStringObjectMap(spec.get("info"));
            if (obsInfo != null && obsInfo.get("title") != null) {
                obsServiceName = toKebabCase((String) obsInfo.get("title"));
            }
        }

        // Generate observability middleware
        String observabilityContent = "// OpenTelemetry instrumentation - must be loaded before any other modules\n" +
                "const { NodeSDK } = require('@opentelemetry/sdk-node');\n" +
                "const { getNodeAutoInstrumentations } = require('@opentelemetry/auto-instrumentations-node');\n" +
                "\n" +
                "const sdk = new NodeSDK({\n" +
                "    serviceName: '" + obsServiceName + "',\n" +
                "    instrumentations: [getNodeAutoInstrumentations()]\n" +
                "});\n" +
                "\n" +
                "sdk.start();\n" +
                "console.log('OpenTelemetry tracing initialized');\n" +
                "\n" +
                "module.exports = { sdk };\n";

        writeFile(basePath + "/middleware/observability.js", observabilityContent);

        // Generate auth middleware
        String authContent = """
                const jwt = require('jsonwebtoken');
                
                /**
                 * Authentication middleware
                 */
                const authenticate = (req, res, next) => {
                  const authHeader = req.headers.authorization;
                 \s
                  if (!authHeader) {
                    return res.status(401).json({ error: 'No authorization header' });
                  }
                
                  const token = authHeader.replace('Bearer ', '');
                 \s
                  try {
                    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'your-secret-key');
                    req.user = decoded;
                    next();
                  } catch (error) {
                    return res.status(401).json({ error: 'Invalid token' });
                  }
                };
                
                /**
                 * Authorization middleware - check scopes
                 */
                const checkScopes = (requiredScopes) => {
                  return (req, res, next) => {
                    const userScopes = req.user?.scopes || [];
                   \s
                    const hasAllScopes = requiredScopes.every(scope => userScopes.includes(scope));
                   \s
                    if (!hasAllScopes) {
                      return res.status(403).json({ error: 'Insufficient permissions' });
                    }
                   \s
                    next();
                  };
                };
                
                module.exports = { authenticate, checkScopes };
                """;

        writeFile(basePath + "/middleware/auth.js", authContent);
    }

    /**
     * Generate main app file
     */
    private void generateApp(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        // Extract server base path
        String serverBasePath = extractServerBasePath(spec);

        // Determine service name from spec
        Map<String, Object> appInfo = Util.asStringObjectMap(spec.get("info"));
        String serviceName = appInfo != null && appInfo.get("title") != null
                ? toKebabCase((String) appInfo.get("title"))
                : "express-api";

        StringBuilder content = new StringBuilder();
        content.append("// Initialize OpenTelemetry (must be first)\n");
        content.append("require('./middleware/observability');\n\n");
        content.append("const express = require('express');\n");
        content.append("const cors = require('cors');\n");
        content.append("require('dotenv').config();\n\n");
        content.append("const app = express();\n\n");
        content.append("// Middleware\n");
        content.append("app.use(cors());\n");
        content.append("app.use(express.json());\n");
        content.append("app.use(express.urlencoded({ extended: true }));\n\n");
        content.append("// Observability: Prometheus metrics\n");
        content.append("const promBundle = require('express-prom-bundle');\n");
        content.append("const metricsMiddleware = promBundle({\n");
        content.append("    includeMethod: true,\n");
        content.append("    includePath: true,\n");
        content.append("    includeStatusCode: true,\n");
        content.append("    promClient: { collectDefaultMetrics: {} }\n");
        content.append("});\n");
        content.append("app.use(metricsMiddleware);\n\n");
        content.append("// Routes\n");

        // Import and register routes
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Set<String> parentPaths = new LinkedHashSet<>();
            for (String path : paths.keySet()) {
                parentPaths.add(getParentPath(path));
            }

            for (String parentPath : parentPaths) {
                String routeName = generateRouteName(parentPath);
                content.append("const ").append(toCamelCase(routeName)).append("Routes = require('./routes/")
                        .append(routeName).append("');\n");
            }

            content.append("\n");

            for (String parentPath : parentPaths) {
                String routeName = generateRouteName(parentPath);
                String fullPath = serverBasePath.isEmpty() ? parentPath : buildFullPath(serverBasePath, parentPath);
                content.append("app.use('").append(fullPath).append("', ").append(toCamelCase(routeName))
                        .append("Routes);\n");
            }
        }

        content.append("\n// Health check\n");
        content.append("app.get('/health', (req, res) => {\n");
        content.append("  res.json({ status: 'ok' });\n");
        content.append("});\n\n");
        content.append("// Error handling\n");
        content.append("app.use((err, req, res, next) => {\n");
        content.append("  console.error(err.stack);\n");
        content.append("  res.status(500).json({ error: 'Something went wrong!' });\n");
        content.append("});\n\n");
        content.append("const PORT = process.env.PORT || 3000;\n");
        content.append("app.listen(PORT, () => {\n");
        content.append("  console.log(`Server running on port ${PORT}`);\n");
        content.append("});\n\n");
        content.append("module.exports = app;\n");

        writeFile(basePath + "/app.js", content.toString());
    }

    /**
     * Generate package.json
     */
    private void generatePackageJson(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String title = info != null ? (String) info.get("title") : "API";
        String version = info != null ? (String) info.get("version") : "1.0.0";
        String description = info != null ? (String) info.get("description") : "Generated Express.js API";
        if (description == null) {
            description = "Generated Express.js API";
        }

        String content = "{\n" +
                "  \"name\": \"" + toKebabCase(title) + "\",\n" +
                "  \"version\": \"" + version + "\",\n" +
                "  \"description\": \"" + description.replace("\"", "\\\"") + "\",\n" +
                "  \"main\": \"app.js\",\n" +
                "  \"scripts\": {\n" +
                "    \"start\": \"node app.js\",\n" +
                "    \"dev\": \"nodemon app.js\",\n" +
                "    \"test\": \"jest\"\n" +
                "  },\n" +
                "  \"dependencies\": {\n" +
                "    \"express\": \"^4.18.2\",\n" +
                "    \"express-validator\": \"^7.0.1\",\n" +
                "    \"cors\": \"^2.8.5\",\n" +
                "    \"dotenv\": \"^16.0.3\",\n" +
                "    \"jsonwebtoken\": \"^9.0.0\",\n" +
                "    \"@opentelemetry/sdk-node\": \"^0.48.0\",\n" +
                "    \"@opentelemetry/auto-instrumentations-node\": \"^0.43.0\",\n" +
                "    \"@opentelemetry/exporter-prometheus\": \"^0.48.0\",\n" +
                "    \"express-prom-bundle\": \"^7.0.0\",\n" +
                "    \"prom-client\": \"^15.1.0\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"nodemon\": \"^2.0.22\",\n" +
                "    \"jest\": \"^29.5.0\",\n" +
                "    \"supertest\": \"^6.3.3\"\n" +
                "  },\n" +
                "  \"engines\": {\n" +
                "    \"node\": \">=14.0.0\"\n" +
                "  }\n" +
                "}\n";

        writeFile(basePath + "/package.json", content);
    }

    /**
     * Generate README
     */
    private void generateReadme(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String basePath = outputDir + "/" + (packageName != null ? packageName.replace(".", "/") : "api");

        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String title = info != null ? (String) info.get("title") : "API";
        String description = info != null ? (String) info.get("description") : "Generated Express.js API";

        String content = "# " + title + "\n\n" +
                description + "\n\n" +
                "## Installation\n\n" +
                "```bash\n" +
                "npm install\n" +
                "```\n\n" +
                "## Configuration\n\n" +
                "Create a `.env` file with:\n\n" +
                "```\n" +
                "PORT=3000\n" +
                "JWT_SECRET=your-secret-key\n" +
                "```\n\n" +
                "## Running\n\n" +
                "### Development\n" +
                "```bash\n" +
                "npm run dev\n" +
                "```\n\n" +
                "### Production\n" +
                "```bash\n" +
                "npm start\n" +
                "```\n\n" +
                "## Testing\n\n" +
                "```bash\n" +
                "npm test\n" +
                "```\n\n" +
                "## Project Structure\n\n" +
                "```\n" +
                ".\n" +
                "├── app.js           # Main application file\n" +
                "├── routes/          # Route handlers\n" +
                "├── models/          # Data models\n" +
                "├── middleware/      # Custom middleware (auth, validation)\n" +
                "├── validators/      # Request validators\n" +
                "├── services/        # Business logic\n" +
                "└── package.json     # Dependencies\n" +
                "```\n\n" +
                "## API Documentation\n\n" +
                "The API follows the OpenAPI 3.0 specification.\n\n" +
                "## Features\n\n" +
                "- ✅ RESTful API endpoints\n" +
                "- ✅ Request validation\n" +
                "- ✅ JWT authentication\n" +
                "- ✅ Scope-based authorization\n" +
                "- ✅ Error handling\n" +
                "- ✅ CORS support\n";

        writeFile(basePath + "/README.md", content);
    }

    /**
     * Generate route name from path
     */
    private String generateRouteName(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "index";
        }

        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.isEmpty() ? "index" : name;
    }

    /**
     * Convert string to camelCase
     */
    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] parts = input.split("[_\\-\\s]+");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));

        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0)));
                result.append(parts[i].substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return result.toString();
    }

    /**
     * Convert string to PascalCase
     */
    private String toPascalCase(String input) {
        String camel = toCamelCase(input);
        if (camel.isEmpty()) return camel;
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    /**
     * Convert string to kebab-case
     */
    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[_\\s]+", "-")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Write content to file
     */
    private void writeFile(String filePath, String content) throws IOException {
        File file = new File(filePath);
        boolean result = file.getParentFile().mkdirs();

        logger.fine("Directory creation result: " + result);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    /**
     * Helper class for path operations
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
     * Helper class for security information
     */
    private static class SecurityInfo {
        boolean hasRequirements;
        List<String> scopes = new ArrayList<>();

        SecurityInfo(boolean hasRequirements) {
            this.hasRequirements = hasRequirements;
        }
    }

    @Override
    public String getName() {
        return "Express.js Generator";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public String getLanguage() {
        return "nodejs";
    }

    @Override
    public String getFramework() {
        return "express";
    }

    @Override
    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    @Override
    public GeneratorConfig getConfig() {
        return this.config;
    }
}
