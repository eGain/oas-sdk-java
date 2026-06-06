package egain.oassdk.generators.python.auth;

import java.util.List;

/**
 * The auth handler registry, in resolution order. The first handler whose
 * supports() is true for a scheme wins; RegisteredAuthHandler is the catch-all last.
 *
 * To support a new custom auth type, add an AuthSchemeHandler implementation to this
 * package and a single entry here (before RegisteredAuthHandler). No other code changes
 * are required.
 */
public final class AuthHandlers {

    private AuthHandlers() {}

    public static final List<AuthSchemeHandler> ALL = List.of(
            new ApiKeyHandler(),
            new HttpBearerHandler(),
            new HttpBasicHandler(),
            new OAuth2Handler(),
            new SigV4SchemeHandler(),
            new RegisteredAuthHandler());
}
