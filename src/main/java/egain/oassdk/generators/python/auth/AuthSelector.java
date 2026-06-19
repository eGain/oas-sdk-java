package egain.oassdk.generators.python.auth;

import egain.oassdk.Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves OpenAPI operation {@code security} requirements to {@link AuthWiring} and handlers.
 */
public final class AuthSelector {

    private final Map<String, Map<String, Object>> securitySchemes;

    public AuthSelector(Map<String, Map<String, Object>> securitySchemes) {
        this.securitySchemes = securitySchemes != null ? securitySchemes : Map.of();
    }

    public AuthWiring authWiringFor(Map<String, Object> operation) {
        AuthSelection sel = selectAuth(operation);
        return sel == null
                ? null
                : new AuthWiring(
                        sel.handler.dependsExpression(sel.schemeName, sel.schemeDef, sel.scopes),
                        sel.handler.strategyClassName());
    }

    public Set<AuthSchemeHandler> usedHandlers(Map<String, Object> spec) {
        Set<AuthSchemeHandler> handlers = new LinkedHashSet<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return handlers;
        }
        for (Object pathItemObj : paths.values()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathItemObj);
            if (pathItem == null) {
                continue;
            }
            for (String method : egain.oassdk.core.Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    AuthSelection sel = selectAuth(Util.asStringObjectMap(pathItem.get(method)));
                    if (sel != null) {
                        handlers.add(sel.handler);
                    }
                }
            }
        }
        return handlers;
    }

    private AuthSelection selectAuth(Map<String, Object> operation) {
        if (operation == null || !operation.containsKey("security")) {
            return null;
        }
        List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));
        if (securityList == null) {
            return null;
        }
        for (Map<String, Object> req : securityList) {
            if (req == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : req.entrySet()) {
                String schemeName = entry.getKey();
                Map<String, Object> schemeDef = securitySchemes.get(schemeName);
                List<String> scopes = new ArrayList<>();
                if (entry.getValue() instanceof List<?> scopeList) {
                    for (Object scope : scopeList) {
                        if (scope instanceof String s) {
                            s = s.replace("${SCOPE_PREFIX}", "");
                            if (!s.isEmpty()) {
                                scopes.add(s);
                            }
                        }
                    }
                }
                for (AuthSchemeHandler handler : AuthHandlers.ALL) {
                    if (handler.supports(schemeDef)) {
                        return new AuthSelection(handler, schemeName, schemeDef, scopes);
                    }
                }
            }
        }
        return null;
    }

    private record AuthSelection(
            AuthSchemeHandler handler,
            String schemeName,
            Map<String, Object> schemeDef,
            List<String> scopes) {
    }
}
