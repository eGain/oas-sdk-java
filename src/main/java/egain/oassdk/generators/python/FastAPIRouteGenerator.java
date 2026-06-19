package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.generators.common.OpenApiPathUtils;
import egain.oassdk.generators.common.PathOperation;
import egain.oassdk.generators.python.auth.AuthSchemeHandler;
import egain.oassdk.generators.python.auth.AuthSelector;
import egain.oassdk.generators.python.auth.AuthWiring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates FastAPI routers, route handlers, and security wiring from OpenAPI path operations.
 */
public final class FastAPIRouteGenerator {

    private final PythonGenerationContext ctx;

    public FastAPIRouteGenerator(PythonGenerationContext ctx) {
        this.ctx = ctx;
    }

    public Map<String, Map<String, Object>> collectSecuritySchemes(Map<String, Object> spec) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (spec == null) {
            return result;
        }
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return result;
        }
        Map<String, Object> schemes = Util.asStringObjectMap(components.get("securitySchemes"));
        if (schemes == null) {
            return result;
        }
        for (Map.Entry<String, Object> e : schemes.entrySet()) {
            Map<String, Object> scheme = Util.asStringObjectMap(e.getValue());
            if (scheme != null) {
                result.put(e.getKey(), scheme);
            }
        }
        return result;
    }

    public void generateRouters(String outputDir, String packageName) throws IOException {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }

        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String serverBasePath = OpenApiPathUtils.extractServerBasePath(spec);
        Map<String, List<PathOperation>> pathGroups = OpenApiPathUtils.groupOperationsByParentPath(spec);

        List<String> routerModules = new ArrayList<>();
        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();
            String fullParentPath = OpenApiPathUtils.buildFullPath(serverBasePath, parentPath);
            generateRouterForParentPath(fullParentPath, operations, outputDir, packagePath, packageName, serverBasePath);
            routerModules.add(PythonNamingUtils.generateRouterName(fullParentPath).toLowerCase());
        }

        StringBuilder routersInit = new StringBuilder();
        for (String mod : routerModules) {
            routersInit.append("from .").append(mod).append(" import ").append(mod).append("_router\n");
        }
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/routers/__init__.py", routersInit.toString());
    }

    void generateRouterForParentPath(String parentPath, List<PathOperation> operations,
                                     String outputDir, String packagePath, String packageName,
                                     String serverBasePath) throws IOException {
        String routerName = PythonNamingUtils.generateRouterName(parentPath);
        String pkg = packageName != null ? packageName : "api";

        StringBuilder content = new StringBuilder();
        content.append("from fastapi import APIRouter, Query, Path, Header, Cookie, Body, Depends, HTTPException, status\n");
        content.append("from typing import Optional, List\n");
        content.append("from datetime import date, datetime\n");
        content.append("from ").append(pkg).append(".models import *\n");
        content.append("from ").append(pkg).append(".services import api_service\n");

        Set<String> strategyClasses = new LinkedHashSet<>();
        for (PathOperation op : operations) {
            AuthWiring w = authSelector().authWiringFor(op.operation());
            if (w != null) {
                strategyClasses.add(w.strategyClass());
            }
        }
        if (!strategyClasses.isEmpty()) {
            content.append("from ").append(pkg).append(".security import ")
                    .append(String.join(", ", strategyClasses)).append("\n");
        }
        content.append("\n");

        content.append(routerName.toLowerCase()).append("_router = APIRouter(prefix=\"")
                .append(parentPath).append("\")\n\n");

        for (PathOperation pathOp : operations) {
            String fullOperationPath = serverBasePath.isEmpty()
                    ? pathOp.path()
                    : OpenApiPathUtils.buildFullPath(serverBasePath, pathOp.path());
            String relativePath = OpenApiPathUtils.getRelativePath(parentPath, fullOperationPath);
            generateRouteHandler(pathOp.method(), pathOp.operation(), relativePath, routerName, content);
        }

        PythonNamingUtils.writeFile(
                outputDir + "/" + packagePath + "/routers/" + routerName.toLowerCase() + ".py", content.toString());
    }

    void generateRouteHandler(String method, Map<String, Object> operation, String relativePath,
                              String routerName, StringBuilder content) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        String functionName = operationId != null ? PythonNamingUtils.toSnakeCase(operationId) : method;
        String pathParam = relativePath.isEmpty() ? "\"\"" : "\"" + relativePath + "\"";

        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> parameterList = new ArrayList<>();

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
                boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;

                if (name != null && in != null && schema != null) {
                    String pythonName = PythonNamingUtils.toPyIdentifier(name);
                    boolean aliasNeeded = !pythonName.equals(name);
                    String pythonType = PythonTypeUtils.getPythonType(schema);

                    switch (in) {
                        case "query" -> parameterList.add(
                                buildQueryParameterWithValidation(pythonName, name, pythonType, schema, required));
                        case "path" -> {
                            if (PythonNamingUtils.isValidPyIdentifier(name)) {
                                parameterList.add(name + ": " + pythonType + " = Path(...)");
                            } else {
                                parameterList.add(pythonName + ": " + pythonType + " = Path(..., alias=\"" + name + "\")");
                            }
                        }
                        case "header" -> {
                            String defaultValue = required ? "..." : "None";
                            String optionalType = required ? pythonType : "Optional[" + pythonType + "]";
                            String aliasArg = aliasNeeded ? ", alias=\"" + name + "\"" : "";
                            parameterList.add(pythonName + ": " + optionalType + " = Header(" + defaultValue + aliasArg + ")");
                        }
                        case "cookie" -> {
                            String defaultValue = required ? "..." : "None";
                            String optionalType = required ? pythonType : "Optional[" + pythonType + "]";
                            String aliasArg = aliasNeeded ? ", alias=\"" + name + "\"" : "";
                            parameterList.add(pythonName + ": " + optionalType + " = Cookie(" + defaultValue + aliasArg + ")");
                        }
                        default -> {
                            String paramAnnotation = getParameterAnnotation(in, name);
                            parameterList.add(pythonName + ": " + pythonType + " = " + paramAnnotation);
                        }
                    }
                }
            }
        }

        AuthWiring auth = authSelector().authWiringFor(operation);
        if (auth != null) {
            parameterList.add("principal: dict = Depends(" + auth.dependsExpr() + ")");
        }

        content.append("@").append(routerName.toLowerCase()).append("_router.")
                .append(method.toLowerCase()).append("(").append(pathParam).append(")\n");
        content.append("async def ").append(functionName).append("(");
        if (!parameterList.isEmpty()) {
            content.append(String.join(", ", parameterList));
        }
        content.append("):\n");
        content.append("    \"\"\"\n");
        if (summary != null) {
            content.append("    ").append(summary).append("\n");
        }
        content.append("    \"\"\"\n");
        content.append("    # Implementation placeholder\n");
        content.append("    # Replace this with actual business logic implementation\n");
        content.append("    return {\"message\": \"Not implemented\"}\n\n");
    }

    public boolean anyOperationHasSecurity(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return false;
        }
        for (Object pathItemObj : paths.values()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathItemObj);
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)
                        && authSelector().authWiringFor(Util.asStringObjectMap(pathItem.get(method))) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public void generateSecurity(String outputDir, String packageName, Map<String, Object> spec) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        Set<AuthSchemeHandler> used = authSelector().usedHandlers(spec);

        StringBuilder c = new StringBuilder();
        c.append("""
                from abc import ABC, abstractmethod
                from typing import Dict, List, Optional
                from fastapi import Request, HTTPException, status


                class AuthStrategy(ABC):
                    \"\"\"Pluggable authentication strategy bound to an OpenAPI security scheme.

                    Each strategy is a FastAPI dependency: an instance is used as
                    ``Depends(strategy)``. It returns a principal dict, or raises HTTPException.
                    Add support for a new scheme by implementing this interface.
                    \"\"\"

                    # OpenAPI scheme/authtype this strategy implements.
                    scheme: str = "unknown"

                    @abstractmethod
                    async def __call__(self, request: Request) -> dict:
                        ...
                """);

        for (AuthSchemeHandler handler : used) {
            String source = handler.strategyClassSource();
            if (source != null && !source.isEmpty()) {
                c.append(source);
            }
        }

        boolean needsRegistry = used.stream().anyMatch(AuthSchemeHandler::requiresRegistry);
        if (needsRegistry) {
            c.append("""


                    _auth_registry: Dict[str, AuthStrategy] = {}


                    def register_auth(scheme: str, strategy: AuthStrategy) -> None:
                        \"\"\"Register the runtime strategy for a custom security scheme.

                        Call this at app startup for each custom scheme (e.g. awsSigv4,
                        mutualTLS) before the first request is served.
                        \"\"\"
                        _auth_registry[scheme] = strategy


                    class RegisteredAuth(AuthStrategy):
                        \"\"\"Resolves a strategy registered at runtime by its OpenAPI scheme name.

                        Used for custom schemes whose implementation is provided outside the
                        generated code (via register_auth). Raises 501 if none is registered.
                        \"\"\"

                        def __init__(self, scheme: str) -> None:
                            self.scheme = scheme

                        async def __call__(self, request: Request) -> dict:
                            strategy = _auth_registry.get(self.scheme)
                            if strategy is None:
                                raise HTTPException(
                                    status.HTTP_501_NOT_IMPLEMENTED,
                                    f"No auth strategy registered for scheme '{self.scheme}'. "
                                    f"Call register_auth('{self.scheme}', <strategy>) at startup.",
                                )
                            return await strategy(request)
                    """);
        }

        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/security.py", c.toString());
    }

    private AuthSelector authSelector() {
        return new AuthSelector(ctx.getSecuritySchemes());
    }

    private String getParameterAnnotation(String in, String name) {
        return switch (in.toLowerCase()) {
            case "path" -> "Path(..., alias=\"" + name + "\")";
            case "query" -> "Query(None, alias=\"" + name + "\")";
            case "header" -> "Header(None, alias=\"" + name + "\")";
            case "cookie" -> "Cookie(None, alias=\"" + name + "\")";
            default -> "Query(None, alias=\"" + name + "\")";
        };
    }

    private String buildQueryParameterWithValidation(String pythonName, String wireName, String pythonType,
                                                     Map<String, Object> schema, boolean required) {
        if (("date".equals(pythonType) || "datetime".equals(pythonType)) && schema.containsKey("pattern")) {
            pythonType = "str";
        }

        StringBuilder paramDef = new StringBuilder();
        String actualType = required ? pythonType : "Optional[" + pythonType + "]";
        paramDef.append(pythonName).append(": ").append(actualType).append(" = Query(");

        if (required) {
            paramDef.append("...");
        } else if (schema.containsKey("default")) {
            paramDef.append(PythonNamingUtils.pyLiteral(schema.get("default")));
        } else {
            paramDef.append("None");
        }

        if (wireName != null && !wireName.equals(pythonName)) {
            paramDef.append(", alias=\"").append(wireName).append("\"");
        }

        String type = (String) schema.get("type");
        if ("string".equals(type)) {
            if (schema.containsKey("minLength")) {
                paramDef.append(", min_length=").append(schema.get("minLength"));
            }
            if (schema.containsKey("maxLength")) {
                paramDef.append(", max_length=").append(schema.get("maxLength"));
            }
            if (schema.containsKey("pattern")) {
                paramDef.append(", pattern=").append(PythonNamingUtils.pyRegex((String) schema.get("pattern")));
            }
        } else if ("integer".equals(type) || "number".equals(type)) {
            if (schema.containsKey("minimum")) {
                paramDef.append(", ge=").append(schema.get("minimum"));
            }
            if (schema.containsKey("maximum")) {
                paramDef.append(", le=").append(schema.get("maximum"));
            }
            if (schema.containsKey("exclusiveMinimum")) {
                Object exMin = schema.get("exclusiveMinimum");
                if (exMin instanceof Boolean && (Boolean) exMin) {
                    if (schema.containsKey("minimum")) {
                        paramDef.append(", gt=").append(schema.get("minimum"));
                    }
                } else {
                    paramDef.append(", gt=").append(exMin);
                }
            }
            if (schema.containsKey("exclusiveMaximum")) {
                Object exMax = schema.get("exclusiveMaximum");
                if (exMax instanceof Boolean && (Boolean) exMax) {
                    if (schema.containsKey("maximum")) {
                        paramDef.append(", lt=").append(schema.get("maximum"));
                    }
                } else {
                    paramDef.append(", lt=").append(exMax);
                }
            }
        } else if ("array".equals(type)) {
            if (schema.containsKey("minItems")) {
                paramDef.append(", min_length=").append(schema.get("minItems"));
            }
            if (schema.containsKey("maxItems")) {
                paramDef.append(", max_length=").append(schema.get("maxItems"));
            }
        }

        if (schema.containsKey("description")) {
            paramDef.append(", description=").append(PythonNamingUtils.pyStr((String) schema.get("description")));
        }

        paramDef.append(")");
        return paramDef.toString();
    }
}
