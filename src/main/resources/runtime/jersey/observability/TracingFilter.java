package __PACKAGE__.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import __INJECT_NS__.Singleton;
import __WS_NS__.container.ContainerRequestContext;
import __WS_NS__.container.ContainerRequestFilter;
import __WS_NS__.container.ContainerResponseContext;
import __WS_NS__.container.ContainerResponseFilter;
import __WS_NS__.ext.Provider;
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
