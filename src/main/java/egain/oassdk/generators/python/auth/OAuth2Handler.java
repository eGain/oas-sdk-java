package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/** OAuth2 / OpenID Connect scheme (no API Gateway authtype): bearer token + scopes. */
public final class OAuth2Handler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) {
        if (def == null || AuthSchemeHandler.hasApigatewayAuthtype(def)) return false;
        String type = (String) def.get("type");
        return "oauth2".equalsIgnoreCase(type) || "openIdConnect".equalsIgnoreCase(type);
    }
    public String strategyClassName() { return "OAuth2Auth"; }
    public boolean requiresRegistry() { return false; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        StringBuilder arg = new StringBuilder();
        if (scopes != null && !scopes.isEmpty()) {
            arg.append("required_scopes=[");
            for (int i = 0; i < scopes.size(); i++) {
                if (i > 0) arg.append(", ");
                arg.append("\"").append(scopes.get(i)).append("\"");
            }
            arg.append("]");
        }
        return "OAuth2Auth(" + arg + ")";
    }
    public String strategyClassSource() {
        return """


                    class OAuth2Auth(AuthStrategy):
                        \"\"\"OAuth2 / OpenID Connect bearer token.

                        Placeholder: extracts the bearer token and records required scopes.
                        Replace the body with real verification (e.g. JWT decode) and enforce
                        self.required_scopes before production use.
                        \"\"\"

                        scheme = "oauth2"

                        def __init__(self, required_scopes: Optional[List[str]] = None) -> None:
                            self.required_scopes = required_scopes or []

                        async def __call__(self, request: Request) -> dict:
                            header = request.headers.get("Authorization", "")
                            token = header[7:] if header[:7].lower() == "bearer " else None
                            # TODO: verify the token, populate real scopes, and enforce
                            # self.required_scopes (raise 401/403 as appropriate).
                            return {"auth_type": "oauth2", "token": token, "scopes": [],
                                    "required_scopes": self.required_scopes}
                    """;
    }
}
