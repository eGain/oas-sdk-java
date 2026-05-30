package egain.oassdk.generators.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import egain.oassdk.core.logging.LoggerConfig;

/**
 * Generates observability instrumentation classes (MetricsFilter, TracingFilter,
 * MetricsEndpoint, ObservabilityBootstrap) when observability is enabled.
 * Extracted from JerseyGenerator to keep that class focused on orchestration.
 */
class JerseyObservabilityGenerator {

    private static final Logger logger = LoggerConfig.getLogger(JerseyObservabilityGenerator.class);

    private final JerseyGenerationContext ctx;

    JerseyObservabilityGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Entry point: generate observability classes if enabled.
     */
    void generate() throws IOException {
        generateObservability(ctx.outputDir, ctx.packageName, ctx.spec);
    }

    private void generateObservability(String outputDir, String packageName, java.util.Map<String, Object> spec) throws IOException {
        if (!ctx.isObservabilityEnabled()) {
            return;
        }

        String packagePath = packageName != null ? packageName : "com.example.api";
        String obsPackage = packagePath + ".observability";
        String obsDir = outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/observability";
        Files.createDirectories(Paths.get(obsDir));

        String serviceName = ctx.config.getObservabilityConfig().getServiceName();
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = JerseyGenerationContext.getAPITitle(spec);
        }

        // --- MetricsFilter.java ---
        String metricsFilter = String.format("""
                package %s;

                import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
                import io.micrometer.prometheusmetrics.PrometheusConfig;
                import io.micrometer.core.instrument.Timer;
                import %s.Singleton;
                import %s.container.ContainerRequestContext;
                import %s.container.ContainerRequestFilter;
                import %s.container.ContainerResponseContext;
                import %s.container.ContainerResponseFilter;
                import %s.ext.Provider;
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
                """, obsPackage, ctx.injectNs, ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs());
        writeFile(obsDir + "/MetricsFilter.java", metricsFilter);

        // --- TracingFilter.java ---
        String tracingFilter = String.format("""
                package %s;

                import io.opentelemetry.api.GlobalOpenTelemetry;
                import io.opentelemetry.api.trace.Span;
                import io.opentelemetry.api.trace.SpanKind;
                import io.opentelemetry.api.trace.StatusCode;
                import io.opentelemetry.api.trace.Tracer;
                import io.opentelemetry.context.Context;
                import io.opentelemetry.context.Scope;
                import io.opentelemetry.context.propagation.TextMapGetter;
                import %s.Singleton;
                import %s.container.ContainerRequestContext;
                import %s.container.ContainerRequestFilter;
                import %s.container.ContainerResponseContext;
                import %s.container.ContainerResponseFilter;
                import %s.ext.Provider;
                import java.io.IOException;
                import java.util.Collections;

                @Provider
                @Singleton
                public class TracingFilter implements ContainerRequestFilter, ContainerResponseFilter {

                    private static final String SPAN_PROPERTY = "tracing.span";
                    private static final String SCOPE_PROPERTY = "tracing.scope";

                    private final Tracer tracer;

                    private static final TextMapGetter<ContainerRequestContext> GETTER = new TextMapGetter<>() {
                        @Override
                        public Iterable<String> keys(ContainerRequestContext carrier) {
                            return carrier.getHeaders().keySet();
                        }

                        @Override
                        public String get(ContainerRequestContext carrier, String key) {
                            return carrier.getHeaderString(key);
                        }
                    };

                    public TracingFilter() {
                        this.tracer = GlobalOpenTelemetry.getTracer("jersey-server");
                    }

                    @Override
                    public void filter(ContainerRequestContext requestContext) throws IOException {
                        Context extractedContext = GlobalOpenTelemetry.getPropagators()
                                .getTextMapPropagator()
                                .extract(Context.current(), requestContext, GETTER);

                        Span span = tracer.spanBuilder(requestContext.getMethod() + " " + requestContext.getUriInfo().getPath())
                                .setParent(extractedContext)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("http.method", requestContext.getMethod())
                                .setAttribute("http.url", requestContext.getUriInfo().getRequestUri().toString())
                                .startSpan();

                        Scope scope = span.makeCurrent();
                        requestContext.setProperty(SPAN_PROPERTY, span);
                        requestContext.setProperty(SCOPE_PROPERTY, scope);
                    }

                    @Override
                    public void filter(ContainerRequestContext requestContext,
                                       ContainerResponseContext responseContext) throws IOException {
                        Scope scope = (Scope) requestContext.getProperty(SCOPE_PROPERTY);
                        Span span = (Span) requestContext.getProperty(SPAN_PROPERTY);
                        if (span != null) {
                            span.setAttribute("http.status_code", responseContext.getStatus());
                            if (responseContext.getStatus() >= 500) {
                                span.setStatus(StatusCode.ERROR, "HTTP " + responseContext.getStatus());
                            }
                            span.end();
                        }
                        if (scope != null) {
                            scope.close();
                        }
                    }
                }
                """, obsPackage, ctx.injectNs, ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs());
        writeFile(obsDir + "/TracingFilter.java", tracingFilter);

        // --- MetricsEndpoint.java ---
        String metricsEndpoint = String.format("""
                package %s;

                import %s.Inject;
                import %s.Singleton;
                import %s.GET;
                import %s.Path;
                import %s.Produces;

                @Path("/metrics")
                @Singleton
                public class MetricsEndpoint {

                    @Inject
                    private MetricsFilter metricsFilter;

                    @GET
                    @Produces("text/plain")
                    public String scrape() {
                        return metricsFilter.getRegistry().scrape();
                    }
                }
                """, obsPackage, ctx.injectNs, ctx.injectNs, ctx.getWsNs(), ctx.getWsNs(), ctx.getWsNs());
        writeFile(obsDir + "/MetricsEndpoint.java", metricsEndpoint);

        // --- ObservabilityBootstrap.java ---
        String bootstrap = String.format("""
                package %s;

                import io.opentelemetry.api.GlobalOpenTelemetry;
                import io.opentelemetry.api.common.Attributes;
                import io.opentelemetry.context.propagation.ContextPropagators;
                import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
                import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
                import io.opentelemetry.sdk.OpenTelemetrySdk;
                import io.opentelemetry.sdk.resources.Resource;
                import io.opentelemetry.sdk.trace.SdkTracerProvider;
                import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
                import io.opentelemetry.semconv.ServiceAttributes;

                /**
                 * Bootstraps OpenTelemetry SDK with OTLP exporter and W3C trace context propagation.
                 * Call {@link #initialize(String)} at application startup.
                 */
                public final class ObservabilityBootstrap {

                    private ObservabilityBootstrap() {
                        // utility class
                    }

                    /**
                     * Configure and register the global OpenTelemetry SDK.
                     *
                     * @param serviceName the logical service name (appears in traces)
                     */
                    public static void initialize(String serviceName) {
                        Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName)));

                        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                                .build();

                        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                                .setResource(resource)
                                .build();

                        OpenTelemetrySdk.builder()
                                .setTracerProvider(tracerProvider)
                                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                                .buildAndRegisterGlobal();
                    }
                }
                """, obsPackage);
        writeFile(obsDir + "/ObservabilityBootstrap.java", bootstrap);

        logger.info("Generated observability instrumentation for service: " + serviceName);
    }

    private void writeFile(String filePath, String content) throws IOException {
        JerseyGenerationContext.writeFile(filePath, content);
    }
}
