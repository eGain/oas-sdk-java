package egain.oassdk.sla;

import egain.oassdk.Util;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;

import java.io.IOException;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * SLA processor for generating API Gateway scripts and SLA enforcement
 */
public class SLAProcessor {

    private static final Logger logger = LoggerConfig.getLogger(SLAProcessor.class);

    /**
     * Generate SLA enforcement code
     *
     * @param spec      OpenAPI specification
     * @param slaSpec   SLA specification
     * @param outputDir Output directory
     * @param config    SLA configuration
     * @throws GenerationException if generation fails
     */
    public void generateEnforcement(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir, SLAConfig config) throws GenerationException {
        
        if (spec == null) {
            throw new GenerationException("Specification cannot be null");
        }
        if (outputDir == null) {
            throw new GenerationException("Output directory cannot be null");
        }
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDir));

            // Generate API Gateway policies
            generateAPIGatewayPolicies(spec, slaSpec, outputDir);

            // Generate SLA validation code
            generateSLAValidationCode(spec, slaSpec, outputDir);

            // Generate monitoring configuration
            generateMonitoringConfig(spec, slaSpec, outputDir);

            // Generate SLA test suite
            generateSLATestSuite(spec, slaSpec, outputDir);

            // Generate Docker configuration
            generateDockerConfig(spec, slaSpec, outputDir);

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate SLA enforcement: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate SLA enforcement: " + e.getMessage(), e);
        }
    }

    /**
     * Generate monitoring setup
     *
     * @param spec            OpenAPI specification
     * @param outputDir       Output directory
     * @param config          SLA configuration
     * @param monitoringStack List of monitoring tools
     * @throws GenerationException if generation fails
     */
    public void generateMonitoring(Map<String, Object> spec, String outputDir, SLAConfig config, List<String> monitoringStack) throws GenerationException {
        if (outputDir == null) {
            throw new GenerationException("Output directory cannot be null");
        }
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDir));

            // Generate Prometheus configuration
            if (monitoringStack.contains("prometheus")) {
                generatePrometheusConfig(spec, outputDir);
            }

            // Generate Grafana dashboards
            if (monitoringStack.contains("grafana")) {
                generateGrafanaDashboards(spec, outputDir);
            }

            // Generate monitoring scripts
            generateMonitoringScripts(spec, outputDir, monitoringStack);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate monitoring: " + e.getMessage(), e);
        }
    }

    /**
     * Generate API Gateway policies
     */
    private void generateAPIGatewayPolicies(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir) throws IOException {
        // Generate AWS API Gateway policy
        String awsPolicy = generateAWSPolicy(spec, slaSpec);
        Files.write(Paths.get(outputDir, "ApiGatewayPolicy.json"), awsPolicy.getBytes(StandardCharsets.UTF_8));

        // Generate Kong configuration
        String kongConfig = generateKongConfig(spec, slaSpec);
        Files.write(Paths.get(outputDir, "kong.yml"), kongConfig.getBytes(StandardCharsets.UTF_8));

        // Generate NGINX configuration
        String nginxConfig = generateNginxConfig(spec, slaSpec);
        Files.write(Paths.get(outputDir, "nginx.conf"), nginxConfig.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate AWS API Gateway policy restricted to actual API paths
     */
    private String generateAWSPolicy(Map<String, Object> spec, Map<String, Object> slaSpec) {
        List<String> apiPaths = getAPIPaths(spec);

        StringBuilder statements = new StringBuilder();
        for (int i = 0; i < apiPaths.size(); i++) {
            String path = apiPaths.get(i);
            // Collect HTTP methods defined for this path
            Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
            Map<String, Object> pathItem = paths != null ? Util.asStringObjectMap(paths.get(path)) : null;
            List<String> methods = new ArrayList<>();
            if (pathItem != null) {
                for (String method : Constants.HTTP_METHODS) {
                    if (pathItem.containsKey(method)) {
                        methods.add(method.toUpperCase(Locale.ROOT));
                    }
                }
            }
            if (methods.isEmpty()) {
                methods.add("GET");
            }
            String methodPattern = String.join(",", methods);
            // Convert OAS path params {id} to wildcard *
            String arnPath = path.replaceAll("\\{[^}]+}", "*");
            if (i > 0) {
                statements.append(",\n");
            }
            statements.append(String.format("""
                            {
                                "Effect": "Allow",
                                "Action": "execute-api:Invoke",
                                "Resource": "arn:aws:execute-api:${region}:${account}:${apiId}/%s%s"
                            }""", methodPattern.contains(",") ? "*/" : methods.get(0) + "/", arnPath));
        }

        // Fallback if no paths found
        if (apiPaths.isEmpty()) {
            statements.append("""
                            {
                                "Effect": "Allow",
                                "Action": "execute-api:Invoke",
                                "Resource": "arn:aws:execute-api:${region}:${account}:${apiId}/*/GET/*"
                            }""");
        }

        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                %s
                    ]
                }
                """, statements);
    }

    /**
     * Generate Kong configuration
     */
    private String generateKongConfig(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                _format_version: "3.0"
                
                services:
                - name: api-service
                  url: http://api-service:8080
                  routes:
                  - name: api-route
                    paths:
                    - /
                  plugins:
                  - name: rate-limiting
                    config:
                      minute: 1000
                      hour: 10000
                  - name: response-transformer
                    config:
                      add:
                        headers:
                        - "X-Response-Time:$(latency_ms)"
                """;
    }

    /**
     * Generate NGINX configuration
     */
    private String generateNginxConfig(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                upstream api_backend {
                    server api-service:8080;
                }
                
                server {
                    listen 80;
                    server_name localhost;
                
                    location / {
                        proxy_pass http://api_backend;
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;
                
                        # Rate limiting
                        limit_req zone=api burst=20 nodelay;
                    }
                
                    # Rate limiting zone
                    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
                }
                """;
    }

    /**
     * Generate SLA validation code
     */
    private void generateSLAValidationCode(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir) throws IOException {
        // Generate Java SLA validator
        String javaValidator = generateJavaSLAValidator(spec, slaSpec);
        Files.write(Paths.get(outputDir, "SLAValidator.java"), javaValidator.getBytes(StandardCharsets.UTF_8));

        // Generate SLA monitoring controller
        String monitoringController = generateSLAMonitoringController(spec, slaSpec);
        Files.write(Paths.get(outputDir, "SLAMonitoringController.java"), monitoringController.getBytes(StandardCharsets.UTF_8));

        // Generate SLA configuration
        String slaConfig = generateSLAConfig(spec, slaSpec);
        Files.write(Paths.get(outputDir, "SLAConfig.java"), slaConfig.getBytes(StandardCharsets.UTF_8));

        // Generate Correlation ID filter
        String correlationIdFilter = generateCorrelationIdFilter();
        Files.write(Paths.get(outputDir, "CorrelationIdFilter.java"), correlationIdFilter.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Java SLA validator
     */
    private String generateJavaSLAValidator(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                package com.example.sla;

                import jakarta.inject.Singleton;
                import jakarta.ws.rs.container.ContainerRequestContext;
                import jakarta.ws.rs.container.ContainerRequestFilter;
                import jakarta.ws.rs.ext.Provider;
                import jakarta.ws.rs.core.Response;
                import java.io.IOException;
                import java.util.concurrent.ConcurrentHashMap;
                import java.util.concurrent.atomic.AtomicLong;
                import java.util.concurrent.atomic.AtomicReference;

                @Provider
                @Singleton
                public class SLAValidator implements ContainerRequestFilter {

                    private static final long WINDOW_MS = 60_000L;
                    private static final long MAX_REQUESTS_PER_WINDOW = 1000L;

                    /**
                     * Sliding window state holding the window start time and
                     * the request count within that window. Immutable so it can
                     * be swapped atomically via AtomicReference.
                     */
                    private static final class WindowState {
                        final long windowStart;
                        final long count;

                        WindowState(long windowStart, long count) {
                            this.windowStart = windowStart;
                            this.count = count;
                        }
                    }

                    private final ConcurrentHashMap<String, AtomicReference<WindowState>> windows =
                            new ConcurrentHashMap<>();

                    @Override
                    public void filter(ContainerRequestContext requestContext) throws IOException {
                        String endpoint = requestContext.getUriInfo().getPath();
                        String method = requestContext.getMethod();
                        String key = method + ":" + endpoint;

                        // Check rate limiting
                        if (!checkRateLimit(key)) {
                            requestContext.abortWith(
                                Response.status(Response.Status.TOO_MANY_REQUESTS)
                                    .entity("Rate limit exceeded")
                                    .build()
                            );
                            return;
                        }

                        // Check SLA requirements
                        if (!checkSLARequirements(requestContext)) {
                            requestContext.abortWith(
                                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                                    .entity("SLA requirements not met")
                                    .build()
                            );
                        }
                    }

                    private boolean checkRateLimit(String key) {
                        long now = System.currentTimeMillis();
                        AtomicReference<WindowState> ref = windows.computeIfAbsent(key,
                                k -> new AtomicReference<>(new WindowState(now, 0L)));

                        // CAS loop: atomically increment count or reset window
                        while (true) {
                            WindowState current = ref.get();
                            WindowState next;
                            if (now - current.windowStart >= WINDOW_MS) {
                                // Window expired -- start a new window with count = 1
                                next = new WindowState(now, 1L);
                            } else {
                                // Same window -- increment count
                                next = new WindowState(current.windowStart, current.count + 1);
                            }
                            if (ref.compareAndSet(current, next)) {
                                return next.count <= MAX_REQUESTS_PER_WINDOW;
                            }
                            // CAS failed, another thread updated; retry
                        }
                    }

                    private boolean checkSLARequirements(ContainerRequestContext requestContext) {
                        // Implement SLA validation logic
                        // Check response time, availability, etc.
                        return true;
                    }
                }
                """;
    }

    /**
     * Generate SLA monitoring controller
     */
    private String generateSLAMonitoringController(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                package com.example.sla;

                import jakarta.ws.rs.*;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import jakarta.inject.Singleton;
                import java.util.HashMap;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;
                import java.util.concurrent.atomic.AtomicLong;

                @Path("/sla")
                @Produces(MediaType.APPLICATION_JSON)
                @Singleton
                public class SLAMonitoringController {

                    private final long startTimeMillis = System.currentTimeMillis();

                    // Real counters for instrumentation
                    private final AtomicLong totalRequests = new AtomicLong(0);
                    private final AtomicLong errorCount = new AtomicLong(0);
                    private final AtomicLong totalResponseTimeNanos = new AtomicLong(0);

                    // Per-endpoint tracking
                    private final ConcurrentHashMap<String, AtomicLong> endpointRequests =
                            new ConcurrentHashMap<>();
                    private final ConcurrentHashMap<String, AtomicLong> endpointResponseTimeNanos =
                            new ConcurrentHashMap<>();

                    /**
                     * Record a completed request for metrics tracking.
                     *
                     * @param responseTimeMs response time in milliseconds
                     * @param isError        true if the response was an error (4xx/5xx)
                     */
                    public void recordRequest(long responseTimeMs, boolean isError) {
                        totalRequests.incrementAndGet();
                        totalResponseTimeNanos.addAndGet(responseTimeMs * 1_000_000L);
                        if (isError) {
                            errorCount.incrementAndGet();
                        }
                    }

                    /**
                     * Record a completed request with endpoint detail.
                     *
                     * @param endpoint       the request path
                     * @param responseTimeMs response time in milliseconds
                     * @param isError        true if the response was an error
                     */
                    public void recordRequest(String endpoint, long responseTimeMs, boolean isError) {
                        recordRequest(responseTimeMs, isError);
                        endpointRequests.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
                        endpointResponseTimeNanos.computeIfAbsent(endpoint, k -> new AtomicLong(0))
                                .addAndGet(responseTimeMs * 1_000_000L);
                    }

                    @GET
                    @Path("/status")
                    public Response getSLAStatus() {
                        Map<String, Object> status = new HashMap<>();
                        status.put("status", "healthy");
                        status.put("timestamp", System.currentTimeMillis());
                        status.put("uptime", getUptime());
                        status.put("responseTime", getAverageResponseTime());
                        status.put("errorRate", getErrorRate());
                        status.put("totalRequests", totalRequests.get());

                        return Response.ok(status).build();
                    }

                    @GET
                    @Path("/metrics")
                    public Response getMetrics() {
                        Map<String, Object> metrics = new HashMap<>();
                        metrics.put("requestsPerSecond", getRequestsPerSecond());
                        metrics.put("averageResponseTime", getAverageResponseTime());
                        metrics.put("errorRate", getErrorRate());
                        metrics.put("availability", getAvailability());
                        metrics.put("totalRequests", totalRequests.get());
                        metrics.put("totalErrors", errorCount.get());
                        metrics.put("endpointRequests", new HashMap<>(endpointRequests));

                        return Response.ok(metrics).build();
                    }

                    private long getUptime() {
                        return System.currentTimeMillis() - startTimeMillis;
                    }

                    private double getAverageResponseTime() {
                        long count = totalRequests.get();
                        if (count == 0) {
                            return 0.0;
                        }
                        return (totalResponseTimeNanos.get() / 1_000_000.0) / count;
                    }

                    private double getErrorRate() {
                        long count = totalRequests.get();
                        if (count == 0) {
                            return 0.0;
                        }
                        return (double) errorCount.get() / count;
                    }

                    private double getRequestsPerSecond() {
                        long uptimeSeconds = getUptime() / 1000;
                        if (uptimeSeconds == 0) {
                            return 0.0;
                        }
                        return (double) totalRequests.get() / uptimeSeconds;
                    }

                    private double getAvailability() {
                        long count = totalRequests.get();
                        if (count == 0) {
                            return 100.0;
                        }
                        return (1.0 - (double) errorCount.get() / count) * 100.0;
                    }
                }
                """;
    }

    /**
     * Generate SLA configuration
     */
    private String generateSLAConfig(Map<String, Object> spec, Map<String, Object> slaSpec) {
        // Derive thresholds from x-sla extensions if present, otherwise use defaults
        int responseTimeMs = 2000;
        int rateLimit = 1000;
        double errorRate = 0.01;
        double availability = 99.9;

        if (spec != null) {
            Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
            if (info != null) {
                Object rtVal = info.get("x-sla-response-time");
                if (rtVal instanceof Number) {
                    responseTimeMs = ((Number) rtVal).intValue();
                }
                Object rlVal = info.get("x-sla-rate-limit");
                if (rlVal instanceof Number) {
                    rateLimit = ((Number) rlVal).intValue();
                }
                Object erVal = info.get("x-sla-error-rate");
                if (erVal instanceof Number) {
                    errorRate = ((Number) erVal).doubleValue();
                }
                Object avVal = info.get("x-sla-availability");
                if (avVal instanceof Number) {
                    availability = ((Number) avVal).doubleValue();
                }
            }
        }

        return String.format("""
                package com.example.sla;

                import jakarta.inject.Singleton;

                @Singleton
                public class SLAConfig {

                    private int maxRequestsPerMinute = %d;
                    private int maxResponseTimeMs = %d;
                    private double maxErrorRate = %s;
                    private double minAvailability = %s;

                    // Getters and setters
                    public int getMaxRequestsPerMinute() {
                        return maxRequestsPerMinute;
                    }

                    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
                        this.maxRequestsPerMinute = maxRequestsPerMinute;
                    }

                    public int getMaxResponseTimeMs() {
                        return maxResponseTimeMs;
                    }

                    public void setMaxResponseTimeMs(int maxResponseTimeMs) {
                        this.maxResponseTimeMs = maxResponseTimeMs;
                    }

                    public double getMaxErrorRate() {
                        return maxErrorRate;
                    }

                    public void setMaxErrorRate(double maxErrorRate) {
                        this.maxErrorRate = maxErrorRate;
                    }

                    public double getMinAvailability() {
                        return minAvailability;
                    }

                    public void setMinAvailability(double minAvailability) {
                        this.minAvailability = minAvailability;
                    }
                }
                """, rateLimit, responseTimeMs, errorRate, availability);
    }

    /**
     * Generate monitoring configuration
     */
    private void generateMonitoringConfig(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir) throws IOException {
        // Generate Prometheus configuration
        String prometheusConfig = generatePrometheusConfig(spec, slaSpec);
        Files.write(Paths.get(outputDir, "prometheus.yml"), prometheusConfig.getBytes(StandardCharsets.UTF_8));

        // Generate Grafana dashboard
        String grafanaDashboard = generateGrafanaDashboard(spec, slaSpec);
        Files.write(Paths.get(outputDir, "grafana-dashboard.json"), grafanaDashboard.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Prometheus configuration
     */
    private String generatePrometheusConfig(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                global:
                  scrape_interval: 15s
                
                scrape_configs:
                  - job_name: 'api-service'
                    static_configs:
                      - targets: ['api-service:8080']
                    metrics_path: '/metrics'
                    scrape_interval: 5s
                """;
    }

    /**
     * Generate Grafana dashboard
     */
    private String generateGrafanaDashboard(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                {
                  "dashboard": {
                    "title": "API SLA Dashboard",
                    "panels": [
                      {
                        "title": "Response Time (p95)",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))"
                          }
                        ]
                      },
                      {
                        "title": "Response Time Percentiles (p50 / p75 / p99)",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))",
                            "legendFormat": "p50"
                          },
                          {
                            "expr": "histogram_quantile(0.75, rate(http_server_requests_seconds_bucket[5m]))",
                            "legendFormat": "p75"
                          },
                          {
                            "expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))",
                            "legendFormat": "p99"
                          }
                        ]
                      },
                      {
                        "title": "Request Rate",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "rate(http_server_requests_seconds_count[5m])"
                          }
                        ]
                      },
                      {
                        "title": "Per-Endpoint Request Rate",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "sum by (uri) (rate(http_server_requests_seconds_count[5m]))",
                            "legendFormat": "{{uri}}"
                          }
                        ]
                      },
                      {
                        "title": "Error Rate",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "rate(http_server_requests_seconds_count{status=~\\"5..\\"}[5m])"
                          }
                        ]
                      },
                      {
                        "title": "SLA Compliance",
                        "type": "gauge",
                        "targets": [
                          {
                            "expr": "(1 - (sum(rate(http_server_requests_seconds_count{status=~\\"5..\\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m])))) * 100"
                          }
                        ],
                        "fieldConfig": {
                          "defaults": {
                            "min": 0,
                            "max": 100,
                            "thresholds": {
                              "steps": [
                                { "value": 0, "color": "red" },
                                { "value": 95, "color": "yellow" },
                                { "value": 99, "color": "green" }
                              ]
                            }
                          }
                        }
                      },
                      {
                        "title": "Availability (%)",
                        "type": "gauge",
                        "targets": [
                          {
                            "expr": "(sum(rate(http_server_requests_seconds_count{status!~\\"5..\\"}[24h])) / sum(rate(http_server_requests_seconds_count[24h]))) * 100"
                          }
                        ],
                        "fieldConfig": {
                          "defaults": {
                            "min": 0,
                            "max": 100,
                            "thresholds": {
                              "steps": [
                                { "value": 0, "color": "red" },
                                { "value": 99, "color": "yellow" },
                                { "value": 99.9, "color": "green" }
                              ]
                            }
                          }
                        }
                      }
                    ]
                  }
                }
                """;
    }

    /**
     * Generate SLA test suite
     */
    private void generateSLATestSuite(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir) throws IOException {
        String testSuite = generateSLATestSuiteCode(spec, slaSpec);
        Files.write(Paths.get(outputDir, "SLATestSuite.java"), testSuite.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate SLA test suite code
     */
    private String generateSLATestSuiteCode(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                package com.example.sla;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                public class SLATestSuite {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    // SLA configuration constants
                    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
                    private static final int MAX_RESPONSE_TIME_MS = 2000;
                    private static final double MAX_ERROR_RATE = 0.01;
                    private static final double MIN_AVAILABILITY = 99.9;
                
                    @Test
                    public void testResponseTimeSLA() {
                        long startTime = System.currentTimeMillis();
                        Response response = target.path("health").request().get();
                        long responseTime = System.currentTimeMillis() - startTime;
                
                        assertTrue(responseTime < MAX_RESPONSE_TIME_MS, "Response time should be less than " + MAX_RESPONSE_TIME_MS + "ms");
                        assertEquals(200, response.getStatus());
                        response.close();
                    }
                
                    @Test
                    public void testRateLimitingSLA() {
                        // Test rate limiting
                        for (int i = 0; i < MAX_REQUESTS_PER_MINUTE + 1; i++) {
                            Response response = target.path("test").request().get();
                            if (i >= MAX_REQUESTS_PER_MINUTE) {
                                assertEquals(429, response.getStatus());
                            }
                            response.close();
                        }
                    }
                
                    @Test
                    public void testAvailabilitySLA() {
                        // Test availability
                        int successCount = 0;
                        int totalRequests = 100;
                
                        for (int i = 0; i < totalRequests; i++) {
                            Response response = target.path("health").request().get();
                            if (response.getStatus() == 200) {
                                successCount++;
                            }
                            response.close();
                        }
                
                        double availability = (double) successCount / totalRequests * 100;
                        assertTrue(availability >= MIN_AVAILABILITY, "Availability should be at least " + MIN_AVAILABILITY + "%");
                    }
                }
                """;
    }

    /**
     * Generate Docker configuration
     */
    private void generateDockerConfig(Map<String, Object> spec, Map<String, Object> slaSpec, String outputDir) throws IOException {
        // Generate Dockerfile
        String dockerfile = generateDockerfile(spec, slaSpec);
        Files.write(Paths.get(outputDir, "Dockerfile"), dockerfile.getBytes(StandardCharsets.UTF_8));

        // Generate docker-compose.yml
        String dockerCompose = generateDockerCompose(spec, slaSpec);
        Files.write(Paths.get(outputDir, "docker-compose.yml"), dockerCompose.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Dockerfile
     */
    private String generateDockerfile(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                FROM eclipse-temurin:21-jre
                
                WORKDIR /app
                
                COPY target/*.jar app.jar
                
                EXPOSE 8080
                
                HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \\
                  CMD curl -f http://localhost:8080/sla/status || exit 1
                
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;
    }

    /**
     * Generate docker-compose.yml
     */
    private String generateDockerCompose(Map<String, Object> spec, Map<String, Object> slaSpec) {
        return """
                version: '3.8'
                
                services:
                  api-service:
                    build: .
                    ports:
                      - "8080:8080"
                    environment:
                      - JAVA_OPTS=-Xmx512m
                    healthcheck:
                      test: ["CMD", "curl", "-f", "http://localhost:8080/sla/status"]
                      interval: 30s
                      timeout: 10s
                      retries: 3
                
                  prometheus:
                    image: prom/prometheus:latest
                    ports:
                      - "9090:9090"
                    volumes:
                      - ./prometheus.yml:/etc/prometheus/prometheus.yml
                
                  grafana:
                    image: grafana/grafana:latest
                    ports:
                      - "3000:3000"
                    environment:
                      - GF_SECURITY_ADMIN_PASSWORD=admin
                    volumes:
                      - ./grafana-dashboard.json:/var/lib/grafana/dashboards/api-dashboard.json
                """;
    }

    /**
     * Generate Prometheus configuration
     */
    private void generatePrometheusConfig(Map<String, Object> spec, String outputDir) throws IOException {
        String prometheusConfig = generatePrometheusConfig(spec, (Map<String, Object>) null);
        Files.write(Paths.get(outputDir, "prometheus.yml"), prometheusConfig.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Grafana dashboards
     */
    private void generateGrafanaDashboards(Map<String, Object> spec, String outputDir) throws IOException {
        String grafanaDashboard = generateGrafanaDashboard(spec, null);
        Files.write(Paths.get(outputDir, "grafana-dashboard.json"), grafanaDashboard.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate monitoring scripts
     */
    private void generateMonitoringScripts(Map<String, Object> spec, String outputDir, List<String> monitoringStack) throws IOException {
        // Generate monitoring startup script
        String startupScript = generateMonitoringStartupScript(monitoringStack);
        Files.write(Paths.get(outputDir, "start-monitoring.sh"), startupScript.getBytes(StandardCharsets.UTF_8));

        // Generate monitoring stop script
        String stopScript = generateMonitoringStopScript(monitoringStack);
        Files.write(Paths.get(outputDir, "stop-monitoring.sh"), stopScript.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate monitoring startup script
     */
    private String generateMonitoringStartupScript(List<String> monitoringStack) {
        StringBuilder script = new StringBuilder("#!/bin/bash\n\n");
        script.append("echo 'Starting monitoring stack...'\n\n");

        if (monitoringStack.contains("prometheus")) {
            script.append("echo 'Starting Prometheus...'\n");
            script.append("docker run -d --name prometheus -p 9090:9090 -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus:latest\n\n");
        }

        if (monitoringStack.contains("grafana")) {
            script.append("echo 'Starting Grafana...'\n");
            script.append("docker run -d --name grafana -p 3000:3000 -e GF_SECURITY_ADMIN_PASSWORD=admin grafana/grafana:latest\n\n");
        }

        script.append("echo 'Monitoring stack started!'\n");
        script.append("echo 'Prometheus: http://localhost:9090'\n");
        script.append("echo 'Grafana: http://localhost:3000 (admin/admin)'\n");

        return script.toString();
    }

    /**
     * Generate monitoring stop script
     */
    private String generateMonitoringStopScript(List<String> monitoringStack) {
        StringBuilder script = new StringBuilder("#!/bin/bash\n\n");
        script.append("echo 'Stopping monitoring stack...'\n\n");

        if (monitoringStack.contains("prometheus")) {
            script.append("docker stop prometheus\n");
            script.append("docker rm prometheus\n\n");
        }

        if (monitoringStack.contains("grafana")) {
            script.append("docker stop grafana\n");
            script.append("docker rm grafana\n\n");
        }

        script.append("echo 'Monitoring stack stopped!'\n");

        return script.toString();
    }

    /**
     * Generate CorrelationIdFilter for trace-id propagation
     */
    private String generateCorrelationIdFilter() {
        return """
                package com.example.sla;

                import jakarta.inject.Singleton;
                import jakarta.ws.rs.container.ContainerRequestContext;
                import jakarta.ws.rs.container.ContainerRequestFilter;
                import jakarta.ws.rs.container.ContainerResponseContext;
                import jakarta.ws.rs.container.ContainerResponseFilter;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;
                import java.util.UUID;

                /**
                 * JAX-RS filter that extracts or generates a correlation / trace ID
                 * and propagates it on the response so callers can correlate
                 * requests across services.
                 */
                @Provider
                @Singleton
                public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

                    private static final String TRACE_ID_HEADER = "X-Trace-Id";
                    private static final String PROPERTY_KEY = "sla.traceId";

                    @Override
                    public void filter(ContainerRequestContext requestContext) throws IOException {
                        String traceId = requestContext.getHeaderString(TRACE_ID_HEADER);
                        if (traceId == null || traceId.isBlank()) {
                            traceId = UUID.randomUUID().toString();
                        }
                        // Store on the request so the response filter can retrieve it
                        requestContext.setProperty(PROPERTY_KEY, traceId);
                    }

                    @Override
                    public void filter(ContainerRequestContext requestContext,
                                       ContainerResponseContext responseContext) throws IOException {
                        Object traceId = requestContext.getProperty(PROPERTY_KEY);
                        if (traceId != null) {
                            responseContext.getHeaders().putSingle(TRACE_ID_HEADER, traceId.toString());
                        }
                    }
                }
                """;
    }

    /**
     * Extract all API paths defined in the OpenAPI spec.
     */
    private List<String> getAPIPaths(Map<String, Object> spec) {
        List<String> paths = new ArrayList<>();
        if (spec != null) {
            Map<String, Object> pathsMap = Util.asStringObjectMap(spec.get("paths"));
            if (pathsMap != null) {
                paths.addAll(pathsMap.keySet());
            }
        }
        return paths;
    }
}
