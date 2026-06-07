package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/**
 * Catch-all (LAST in the registry): anything not matched by a built-in handler —
 * other custom API Gateway authtypes, mutualTLS, or unrecognized schemes. The
 * actual strategy implementation is supplied at runtime OUTSIDE the generated code
 * via register_auth(...); RegisteredAuth resolves it by scheme name at call time.
 */
public final class RegisteredAuthHandler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) { return true; }
    public String strategyClassName() { return "RegisteredAuth"; }
    public boolean requiresRegistry() { return true; }
    public String strategyClassSource() { return ""; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        return "RegisteredAuth(\"" + schemeName + "\")";
    }
}
