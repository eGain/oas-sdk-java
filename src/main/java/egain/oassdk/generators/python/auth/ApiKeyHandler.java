package egain.oassdk.generators.python.auth;

import java.util.List;
import java.util.Map;

/** apiKey scheme (no API Gateway authtype): key in header/query/cookie. */
public final class ApiKeyHandler implements AuthSchemeHandler {
    public boolean supports(Map<String, Object> def) {
        return def != null && "apiKey".equalsIgnoreCase((String) def.get("type"))
                && !AuthSchemeHandler.hasApigatewayAuthtype(def);
    }
    public String strategyClassName() { return "ApiKeyAuth"; }
    public boolean requiresRegistry() { return false; }
    public String dependsExpression(String schemeName, Map<String, Object> def, List<String> scopes) {
        String name = def != null && def.get("name") != null ? (String) def.get("name") : "X-API-Key";
        String location = def != null && def.get("in") != null ? (String) def.get("in") : "header";
        return "ApiKeyAuth(name=\"" + name + "\", location=\"" + location + "\")";
    }
    public String strategyClassSource() {
        return """


                    class ApiKeyAuth(AuthStrategy):
                        \"\"\"API key supplied in a request header, query parameter, or cookie.\"\"\"

                        scheme = "apiKey"

                        def __init__(self, name: str = "X-API-Key", location: str = "header") -> None:
                            self.name = name
                            self.location = location

                        async def __call__(self, request: Request) -> dict:
                            if self.location == "query":
                                key = request.query_params.get(self.name)
                            elif self.location == "cookie":
                                key = request.cookies.get(self.name)
                            else:
                                key = request.headers.get(self.name)
                            if not key:
                                raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing API key")
                            # TODO: validate the API key.
                            return {"auth_type": "apikey", "api_key": key}
                    """;
    }
}
