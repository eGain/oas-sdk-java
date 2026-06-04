package __PACKAGE__.observability;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.core.instrument.Timer;
import __INJECT_NS__.Singleton;
import __WS_NS__.container.ContainerRequestContext;
import __WS_NS__.container.ContainerRequestFilter;
import __WS_NS__.container.ContainerResponseContext;
import __WS_NS__.container.ContainerResponseFilter;
import __WS_NS__.ext.Provider;
import java.io.IOException;
import java.time.Duration;

@Provider
@Singleton
public class MetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_TIME_PROPERTY = "metrics.startTime";
    private final PrometheusMeterRegistry registry;

    public MetricsFilter() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        Object startObj = requestContext.getProperty(START_TIME_PROPERTY);
        if (startObj instanceof Long startTime) {
            long duration = System.nanoTime() - startTime;
            String method = requestContext.getMethod();
            String path = requestContext.getUriInfo().getPath();
            String status = String.valueOf(responseContext.getStatus());

            Timer.builder("http.server.requests")
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(registry)
                    .record(Duration.ofNanos(duration));

            registry.counter("http.server.requests.count",
                    "method", method, "path", path, "status", status).increment();
        }
    }

    public PrometheusMeterRegistry getRegistry() {
        return registry;
    }
}
