package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/** HTTP bearer scheme (no API Gateway authtype): Authorization: Bearer <token>. */
public final class HttpBearerHandler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) {
        return def != null && "http".equalsIgnoreCase((String) def.get("type"))
                && "bearer".equalsIgnoreCase((String) def.get("scheme"))
                && !AuthSchemeHandler.hasApigatewayAuthtype(def);
    }
    public String strategyClassName() { return "HttpBearerAuth"; }
    public boolean requiresRegistry() { return false; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        return "HttpBearerAuth()";
    }
    public String strategyClassSource() {
        return """


                    class HttpBearerAuth(AuthStrategy):
                        \"\"\"HTTP bearer token (Authorization: Bearer <token>).

                        Placeholder: extracts the bearer token. Replace the body with real
                        verification (e.g. JWT decode) before production use.
                        \"\"\"

                        scheme = "bearer"

                        async def __call__(self, request: Request) -> dict:
                            header = request.headers.get("Authorization", "")
                            token = header[7:] if header[:7].lower() == "bearer " else None
                            if not token:
                                raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")
                            # TODO: verify the token.
                            return {"auth_type": "bearer", "token": token}
                    """;
    }
}
