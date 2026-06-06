package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/**
 * A pluggable handler for one family of OpenAPI security schemes.
 *
 * <p>Resolving an operation's auth = find the FIRST handler whose {@link #supports(Map)}
 * is true for the scheme's definition, then ask it for the {@code Depends(...)} expression,
 * the Python strategy class name, and (when used) the Python source to emit.
 */
public interface AuthSchemeHandler {
    /** Does this handler handle this OpenAPI securityScheme definition? Null-safe. */
    boolean supports(Map<String, Object> schemeDef);

    /** Python class used in Depends(...) (e.g. "ApiKeyAuth", "OAuth2Auth", "RegisteredAuth"). */
    String strategyClassName();

    /** Python source emitted into security.py for that class; "" if provided elsewhere. */
    String strategyClassSource();

    /** True if this handler needs the register_auth/RegisteredAuth machinery emitted. */
    boolean requiresRegistry();

    /** The Depends(...) inner expression for an operation using this scheme. */
    String dependsExpression(String schemeName, Map<String, Object> schemeDef, List<String> scopes);

    /** True when the scheme carries an AWS API Gateway custom authtype (e.g. awsSigv4). */
    static boolean hasApigatewayAuthtype(Map<String, Object> def) {
        return def != null && def.get("x-amazon-apigateway-authtype") != null;
    }
}
