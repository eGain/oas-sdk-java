package __PACKAGE__;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import __WS_NS__.ext.ContextResolver;
import __WS_NS__.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.glassfish.jersey.jackson.JacksonFeature;
import java.util.logging.Logger;

public class __CLASS_NAME__ extends ResourceConfig {

    private static final Logger logger = Logger.getLogger(__CLASS_NAME__.class.getName());

    public __CLASS_NAME__() {
        // Register packages containing JAX-RS resources
        packages("__PACKAGE__.resources");

        // Register Jackson for JSON with JSR310 support
        register(JacksonFeature.class);
        register(ObjectMapperContextResolver.class);

        // Register exception mappers
        register(__PACKAGE__.exception.GenericExceptionMapper.class);
__OBSERVABILITY_REGISTRATION__
    }

    @Provider
    public static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
        private final ObjectMapper objectMapper;

        public ObjectMapperContextResolver() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public ObjectMapper getContext(Class<?> type) {
            return objectMapper;
        }
    }

    public static HttpServer startServer() {
        final String baseUri = "http://localhost:8080/";
        final ResourceConfig config = new __CLASS_NAME__();
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(baseUri), config);
    }

    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        logger.info("Jersey app started with endpoints available at http://localhost:8080/api/");
        logger.info("Hit Ctrl-C to stop it...");
        System.in.read();
        server.shutdown();
    }
}
