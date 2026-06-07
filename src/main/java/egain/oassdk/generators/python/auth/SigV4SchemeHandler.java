package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/**
 * AWS IAM SigV4 (x-amazon-apigateway-authtype: awsSigv4). Example of a custom auth type
 * supported by adding a single handler: authentication is enforced at the API Gateway
 * edge, so this strategy does not re-verify the signature — it surfaces the IAM caller
 * identity from the gateway request context for downstream use.
 */
public final class SigV4SchemeHandler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) {
        return def != null && "awsSigv4".equalsIgnoreCase(String.valueOf(def.get("x-amazon-apigateway-authtype")));
    }
    public String strategyClassName() { return "SigV4Auth"; }
    public boolean requiresRegistry() { return false; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        return "SigV4Auth()";
    }
    public String strategyClassSource() {
        return """


                    class SigV4Auth(AuthStrategy):
                        \"\"\"AWS IAM SigV4 (x-amazon-apigateway-authtype: awsSigv4).

                        Authentication is enforced at the API Gateway edge, so a request that
                        reaches the app is already authenticated. This strategy does NOT re-verify
                        the signature; it surfaces the IAM caller identity from the API Gateway
                        request context (available on the ASGI scope under "aws.event", e.g. via
                        Mangum) for downstream use.
                        \"\"\"

                        scheme = "awsSigv4"

                        async def __call__(self, request: Request) -> dict:
                            event = request.scope.get("aws.event") or {}
                            ctx = event.get("requestContext") or {}
                            identity = ctx.get("identity") or {}
                            return {
                                "auth_type": "sigv4",
                                "principal": identity.get("userArn") or identity.get("caller"),
                                "account_id": identity.get("accountId"),
                                "identity": identity,
                            }
                    """;
    }
}
