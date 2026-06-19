package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.generators.common.OpenApiPathUtils;
import egain.oassdk.generators.common.PathOperation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates Flask blueprints and route handlers from OpenAPI path operations.
 */
public final class FlaskBlueprintGenerator {

    private final PythonGenerationContext ctx;

    public FlaskBlueprintGenerator(PythonGenerationContext ctx) {
        this.ctx = ctx;
    }

    public void generateBlueprints(String outputDir, String packageName) throws IOException {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }

        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String serverBasePath = OpenApiPathUtils.extractServerBasePath(spec);
        Map<String, List<PathOperation>> pathGroups = OpenApiPathUtils.groupOperationsByParentPath(spec);

        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();
            String fullParentPath = OpenApiPathUtils.buildFullPath(serverBasePath, parentPath);
            generateBlueprintForParentPath(fullParentPath, operations, outputDir, packagePath, packageName, serverBasePath);
        }
    }

    void generateBlueprintForParentPath(String parentPath, List<PathOperation> operations,
                                        String outputDir, String packagePath, String packageName,
                                        String serverBasePath) throws IOException {
        String blueprintName = PythonNamingUtils.generateBlueprintName(parentPath);
        String pkg = packageName != null ? packageName : "api";

        StringBuilder content = new StringBuilder();
        content.append("from flask import Blueprint, request, jsonify\n");
        content.append("from typing import Optional, List\n");
        content.append("from functools import wraps\n");
        content.append("from ").append(pkg).append(".models import *\n");
        content.append("from ").append(pkg).append(".services.api_service import api_service\n");

        if (hasSecurityRequirements(operations)) {
            content.append("from ").append(pkg).append(".security import require_auth, check_scopes\n");
        }
        content.append("\n");

        content.append(blueprintName.toLowerCase()).append("_bp = Blueprint('")
                .append(blueprintName.toLowerCase()).append("', __name__, url_prefix='")
                .append(parentPath).append("')\n\n");

        for (PathOperation pathOp : operations) {
            String fullOperationPath = serverBasePath.isEmpty()
                    ? pathOp.path()
                    : OpenApiPathUtils.buildFullPath(serverBasePath, pathOp.path());
            String relativePath = getRelativePath(parentPath, fullOperationPath);
            generateRouteHandler(pathOp.method(), pathOp.operation(), relativePath, blueprintName, content);
        }

        PythonNamingUtils.writeFile(
                outputDir + "/" + packagePath + "/blueprints/" + blueprintName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Flask relative paths use "/" when the operation path equals the parent prefix.
     */
    private String getRelativePath(String parentPath, String fullPath) {
        if (fullPath.equals(parentPath)) {
            return "/";
        }
        String normalizedParent = parentPath.startsWith("/") ? parentPath.substring(1) : parentPath;
        String normalizedFull = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        if (normalizedFull.startsWith(normalizedParent + "/")) {
            return "/" + normalizedFull.substring(normalizedParent.length() + 1);
        }
        return fullPath;
    }

    void generateRouteHandler(String method, Map<String, Object> operation, String relativePath,
                              String blueprintName, StringBuilder content) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        String functionName = operationId != null ? PythonNamingUtils.toSnakeCase(operationId) : method + "_handler";
        String flaskPath = convertToFlaskPath(relativePath);

        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> pathParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        List<String> headerParams = new ArrayList<>();

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                if (name != null && in != null) {
                    switch (in.toLowerCase(Locale.ROOT)) {
                        case "path" -> pathParams.add(name);
                        case "query" -> queryParams.add(name);
                        case "header" -> headerParams.add(name);
                        default -> { }
                    }
                }
            }
        }

        SecurityInfo securityInfo = extractSecurityInfo(operation);

        content.append("@").append(blueprintName.toLowerCase()).append("_bp.route('")
                .append(flaskPath).append("', methods=['").append(method.toUpperCase(Locale.ROOT)).append("'])\n");

        if (securityInfo != null && securityInfo.hasRequirements) {
            if (!securityInfo.scopes.isEmpty()) {
                content.append("@require_auth(scopes=[");
                for (int i = 0; i < securityInfo.scopes.size(); i++) {
                    if (i > 0) {
                        content.append(", ");
                    }
                    content.append("\"").append(securityInfo.scopes.get(i)).append("\"");
                }
                content.append("])\n");
            } else {
                content.append("@require_auth()\n");
            }
        }

        content.append("def ").append(functionName).append("(");
        if (!pathParams.isEmpty()) {
            content.append(String.join(", ", pathParams));
        }
        content.append("):\n");
        content.append("    \"\"\"\n");
        if (summary != null) {
            content.append("    ").append(summary).append("\n");
        }
        content.append("    \"\"\"\n");

        if (!queryParams.isEmpty()) {
            for (String queryParam : queryParams) {
                Map<String, Object> paramDef = findParameterDefinition(params, queryParam, "query");
                if (paramDef != null) {
                    Map<String, Object> schema = Util.asStringObjectMap(paramDef.get("schema"));
                    boolean required = paramDef.containsKey("required") ? (Boolean) paramDef.get("required") : false;
                    String varName = PythonNamingUtils.toSnakeCase(queryParam);
                    content.append("    ").append(varName).append(" = request.args.get('").append(queryParam).append("')\n");
                    if (schema != null) {
                        generateFlaskParameterValidation(content, varName, queryParam, schema, required);
                    }
                }
            }
        }

        if (!headerParams.isEmpty()) {
            for (String headerParam : headerParams) {
                content.append("    ").append(PythonNamingUtils.toSnakeCase(headerParam))
                        .append(" = request.headers.get('").append(headerParam).append("')\n");
            }
        }

        if ("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
            content.append("    data = request.get_json()\n");
        }

        content.append("\n");
        content.append("    # Implementation placeholder\n");
        content.append("    # Replace this with actual business logic implementation\n");
        content.append("    return jsonify({'message': 'Not implemented'}), 501\n\n");
    }

    private String convertToFlaskPath(String path) {
        return path.replaceAll("\\{([^}]+)\\}", "<$1>");
    }

    private Map<String, Object> findParameterDefinition(List<Map<String, Object>> params, String name, String in) {
        if (params == null) {
            return null;
        }
        for (Map<String, Object> param : params) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");
            if (name.equals(paramName) && in.equals(paramIn)) {
                return param;
            }
        }
        return null;
    }

    private void generateFlaskParameterValidation(StringBuilder content, String varName, String originalName,
                                                  Map<String, Object> schema, boolean required) {
        String type = (String) schema.get("type");

        if (required) {
            content.append("    if ").append(varName).append(" is None:\n");
            content.append("        return jsonify({'error': 'Missing required parameter: ")
                    .append(originalName).append("'}), 400\n");
        }

        if ("string".equals(type)) {
            if (schema.containsKey("minLength")) {
                int minLength = ((Number) schema.get("minLength")).intValue();
                content.append("    if ").append(varName).append(" is not None and len(")
                        .append(varName).append(") < ").append(minLength).append(":\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName)
                        .append(" must be at least ").append(minLength).append(" characters'}), 400\n");
            }
            if (schema.containsKey("maxLength")) {
                int maxLength = ((Number) schema.get("maxLength")).intValue();
                content.append("    if ").append(varName).append(" is not None and len(")
                        .append(varName).append(") > ").append(maxLength).append(":\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName)
                        .append(" must be at most ").append(maxLength).append(" characters'}), 400\n");
            }
            if (schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                content.append("    import re\n");
                content.append("    if ").append(varName).append(" is not None and not re.match(r'")
                        .append(pattern.replace("'", "\\'")).append("', ").append(varName).append("):\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName)
                        .append(" does not match required pattern'}), 400\n");
            }
        } else if ("integer".equals(type) || "number".equals(type)) {
            content.append("    if ").append(varName).append(" is not None:\n");
            content.append("        try:\n");
            if ("integer".equals(type)) {
                content.append("            ").append(varName).append(" = int(").append(varName).append(")\n");
            } else {
                content.append("            ").append(varName).append(" = float(").append(varName).append(")\n");
            }
            content.append("        except ValueError:\n");
            content.append("            return jsonify({'error': 'Parameter ").append(originalName)
                    .append(" must be a valid ").append(type).append("'}), 400\n");

            if (schema.containsKey("minimum")) {
                Number minimum = (Number) schema.get("minimum");
                content.append("        if ").append(varName).append(" < ").append(minimum).append(":\n");
                content.append("            return jsonify({'error': 'Parameter ").append(originalName)
                        .append(" must be >= ").append(minimum).append("'}), 400\n");
            }
            if (schema.containsKey("maximum")) {
                Number maximum = (Number) schema.get("maximum");
                content.append("        if ").append(varName).append(" > ").append(maximum).append(":\n");
                content.append("            return jsonify({'error': 'Parameter ").append(originalName)
                        .append(" must be <= ").append(maximum).append("'}), 400\n");
            }
        }
    }

    private boolean hasSecurityRequirements(List<PathOperation> operations) {
        for (PathOperation pathOp : operations) {
            if (pathOp.operation() != null && pathOp.operation().containsKey("security")) {
                return true;
            }
        }
        return false;
    }

    private static final class SecurityInfo {
        final boolean hasRequirements;
        final List<String> scopes = new ArrayList<>();

        SecurityInfo(boolean hasRequirements) {
            this.hasRequirements = hasRequirements;
        }
    }

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
}
