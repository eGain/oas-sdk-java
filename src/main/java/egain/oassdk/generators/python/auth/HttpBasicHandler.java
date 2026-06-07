package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/** HTTP basic scheme: Authorization: Basic <base64(user:pass)>. */
public final class HttpBasicHandler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) {
        return def != null && "http".equalsIgnoreCase((String) def.get("type"))
                && "basic".equalsIgnoreCase((String) def.get("scheme"));
    }
    public String strategyClassName() { return "HttpBasicAuth"; }
    public boolean requiresRegistry() { return false; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        return "HttpBasicAuth()";
    }
    public String strategyClassSource() {
        return """


                    class HttpBasicAuth(AuthStrategy):
                        \"\"\"HTTP basic credentials (Authorization: Basic <base64>).

                        Placeholder: extracts and decodes the credentials. Replace the body
                        with real verification before production use.
                        \"\"\"

                        scheme = "basic"

                        async def __call__(self, request: Request) -> dict:
                            import base64
                            header = request.headers.get("Authorization", "")
                            if header[:6].lower() != "basic ":
                                raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing basic credentials")
                            try:
                                decoded = base64.b64decode(header[6:]).decode("utf-8")
                                username, _, password = decoded.partition(":")
                            except Exception:
                                raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Malformed basic credentials")
                            # TODO: verify the credentials.
                            return {"auth_type": "basic", "username": username, "password": password}
                    """;
    }
}
