package __PACKAGE__.config;

import __WS_NS__.container.ContainerRequestContext;
import __WS_NS__.container.ContainerResponseContext;
import __WS_NS__.container.ContainerResponseFilter;
import __WS_NS__.ext.Provider;
import java.io.IOException;

// TODO: SECURITY - Restrict CORS origins before deploying to production.
// Replace "*" with specific allowed origins (e.g., "https://yourdomain.com").
@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "*");
    }
}
