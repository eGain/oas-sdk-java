package egain.oassdk.connectors;

import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.logging.LoggerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Business Logic Connector for developers
 * <p>
 * This class provides a clean interface for developers to implement their business logic
 * while leveraging all the SDK components (validators, limit checkers, SLA enforcement, etc.)
 */
@Singleton
public class BusinessLogicConnector {

    private static final Logger logger = LoggerConfig.getLogger(BusinessLogicConnector.class);

    @Inject
    private APIValidator apiValidator;

    @Inject
    private RateLimiter rateLimiter;

    @Inject
    private StaticLimitChecker staticLimitChecker;

    @Inject
    private SLAMonitoringController slaMonitoringController;

    /**
     * Process business logic with full SDK integration
     *
     * @param request       The incoming request
     * @param businessLogic The business logic to execute
     * @return Processed response
     * @throws OASSDKException if processing fails
     */
    public Response processRequest(
            Map<String, Object> request,
            BusinessLogicFunction businessLogic) throws OASSDKException {

        try {
            // 1. Validate request using SDK validators
            validateRequest(request);

            // 2. Check rate limits using SDK rate limiter
            checkRateLimits();

            // 3. Check static limits using SDK static limit checker
            checkStaticLimits(request);

            // 4. Execute business logic
            Map<String, Object> result = businessLogic.execute(request);

            // 5. Validate response using SDK validators
            validateResponse(result);

            // 6. Update SLA metrics
            updateSLAMetrics(request, result);

            // 7. Return response
            return Response.ok(result).build();

        } catch (Exception e) {
            throw new OASSDKException("Failed to process request: " + e.getMessage(), e);
        }
    }

    /**
     * Process business logic with custom validation
     *
     * @param request         The incoming request
     * @param businessLogic   The business logic to execute
     * @param validationRules Custom validation rules
     * @return Processed response
     * @throws OASSDKException if processing fails
     */
    public Response processRequestWithCustomValidation(
            Map<String, Object> request,
            BusinessLogicFunction businessLogic,
            ValidationRules validationRules) throws OASSDKException {

        try {
            // 1. Apply custom validation rules
            applyCustomValidation(request, validationRules);

            // 2. Check rate limits
            checkRateLimits();

            // 3. Execute business logic
            Map<String, Object> result = businessLogic.execute(request);

            // 4. Apply custom response validation
            applyCustomResponseValidation(result, validationRules);

            // 5. Update SLA metrics
            updateSLAMetrics(request, result);

            // 6. Return response
            return Response.ok(result).build();

        } catch (Exception e) {
            throw new OASSDKException("Failed to process request with custom validation: " + e.getMessage(), e);
        }
    }

    /**
     * Process business logic with SLA enforcement
     *
     * @param request         The incoming request
     * @param businessLogic   The business logic to execute
     * @param slaRequirements SLA requirements to enforce
     * @return Processed response
     * @throws OASSDKException if processing fails
     */
    public Response processRequestWithSLAEnforcement(
            Map<String, Object> request,
            BusinessLogicFunction businessLogic,
            SLARequirements slaRequirements) throws OASSDKException {

        try {
            // 1. Check SLA requirements
            checkSLARequirements(slaRequirements);

            // 2. Execute business logic with SLA monitoring
            long startTime = System.currentTimeMillis();
            Map<String, Object> result = businessLogic.execute(request);
            long executionTime = System.currentTimeMillis() - startTime;

            // 3. Validate SLA compliance
            validateSLACompliance(executionTime, slaRequirements);

            // 4. Update SLA metrics
            updateSLAMetrics(request, result);

            // 5. Return response
            return Response.ok(result).build();

        } catch (Exception e) {
            throw new OASSDKException("Failed to process request with SLA enforcement: " + e.getMessage(), e);
        }
    }

    /**
     * Process business logic with monitoring
     *
     * @param request          The incoming request
     * @param businessLogic    The business logic to execute
     * @param monitoringConfig Monitoring configuration
     * @return Processed response
     * @throws OASSDKException if processing fails
     */
    public Response processRequestWithMonitoring(
            Map<String, Object> request,
            BusinessLogicFunction businessLogic,
            MonitoringConfig monitoringConfig) throws OASSDKException {

        try {
            // 1. Start monitoring
            startMonitoring(request, monitoringConfig);

            // 2. Execute business logic
            Map<String, Object> result = businessLogic.execute(request);

            // 3. Stop monitoring and collect metrics
            stopMonitoring(request, result, monitoringConfig);

            // 4. Return response
            return Response.ok(result).build();

        } catch (Exception e) {
            throw new OASSDKException("Failed to process request with monitoring: " + e.getMessage(), e);
        }
    }

    /**
     * Validate request using SDK validators
     */
    private void validateRequest(Map<String, Object> request) throws OASSDKException {
        // Use SDK validators to validate request
        if (apiValidator != null) {
            // Validate request structure, format, etc.
            boolean isValid = apiValidator.validateRequest(request);
            if (!isValid) {
                throw new OASSDKException("Request validation failed");
            }
        }
    }

    /**
     * Check rate limits using SDK rate limiter
     */
    private void checkRateLimits() throws OASSDKException {
        if (rateLimiter != null) {
            String clientKey = extractClientKey();
            if (!rateLimiter.isAllowed(clientKey)) {
                throw new OASSDKException("Rate limit exceeded");
            }
        }
    }

    /**
     * Check static limits using SDK static limit checker
     */
    private void checkStaticLimits(Map<String, Object> request) throws OASSDKException {
        if (staticLimitChecker != null) {
            // Check static limits (request size, field lengths, etc.)
            boolean withinLimits = staticLimitChecker.checkLimits(request);
            if (!withinLimits) {
                throw new OASSDKException("Request exceeds static limits");
            }
        }
    }

    /**
     * Validate response using SDK validators
     */
    private void validateResponse(Map<String, Object> response) throws OASSDKException {
        if (apiValidator != null) {
            // Validate response structure, format, etc.
            boolean isValid = apiValidator.validateResponse(response);
            if (!isValid) {
                throw new OASSDKException("Response validation failed");
            }
        }
    }

    /**
     * Update SLA metrics
     */
    private void updateSLAMetrics(Map<String, Object> request, Map<String, Object> response) {
        if (slaMonitoringController != null) {
            // Update SLA metrics (response time, success rate, etc.)
            try {
                slaMonitoringController.updateMetrics(request, response);
            } catch (Exception e) {
                // Log error but don't fail the request
                logger.log(java.util.logging.Level.WARNING, "Failed to update SLA metrics: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Apply custom validation rules
     */
    private void applyCustomValidation(Map<String, Object> request, ValidationRules validationRules) throws OASSDKException {
        // Apply custom validation rules
        for (ValidationRule rule : validationRules.getRules()) {
            if (!rule.validate(request)) {
                throw new OASSDKException("Validation failed: " + rule.getErrorMessage());
            }
        }
    }

    /**
     * Apply custom response validation
     */
    private void applyCustomResponseValidation(Map<String, Object> response, ValidationRules validationRules) throws OASSDKException {
        // Apply custom response validation rules
        for (ValidationRule rule : validationRules.getResponseRules()) {
            if (!rule.validate(response)) {
                throw new OASSDKException("Response validation failed: " + rule.getErrorMessage());
            }
        }
    }

    /**
     * Check SLA requirements
     */
    private void checkSLARequirements(SLARequirements slaRequirements) throws OASSDKException {
        // Check if request meets SLA requirements
        if (slaRequirements.getMaxResponseTime() > 0) {
            // Check if we can meet response time requirement
            // Verify system capacity and current load
            if (slaMonitoringController != null && !slaMonitoringController.isSLACompliant()) {
                throw new OASSDKException("System currently cannot meet SLA response time requirements");
            }
        }

        if (slaRequirements.getMaxConcurrentRequests() > 0) {
            // Check current concurrent request count
            if (slaMonitoringController != null) {
                Map<String, Object> metrics = slaMonitoringController.getMetrics();
                if (metrics != null && metrics.containsKey("currentRequests")) {
                    int currentRequests = (Integer) metrics.get("currentRequests");
                    if (currentRequests >= slaRequirements.getMaxConcurrentRequests()) {
                        throw new OASSDKException("Max concurrent requests exceeded");
                    }
                }
            }
        }
    }

    /**
     * Validate SLA compliance
     */
    private void validateSLACompliance(long executionTime, SLARequirements slaRequirements) throws OASSDKException {
        if (slaRequirements.getMaxResponseTime() > 0 && executionTime > slaRequirements.getMaxResponseTime()) {
            throw new OASSDKException("SLA violation: Response time exceeded");
        }
    }

    /**
     * Start monitoring
     */
    private void startMonitoring(Map<String, Object> request, MonitoringConfig monitoringConfig) {
        // Start monitoring based on configuration
        if (monitoringConfig.isEnablePerformanceMonitoring() || monitoringConfig.isEnableErrorMonitoring()) {
            // Log monitoring start
            logger.info("Starting monitoring for request: " +
                    (request != null ? request.getClass().getName() : "null"));
        }
    }

    /**
     * Stop monitoring and collect metrics
     */
    private void stopMonitoring(Map<String, Object> request, Map<String, Object> response, MonitoringConfig monitoringConfig) {
        // Stop monitoring and collect metrics
        if (monitoringConfig.isEnablePerformanceMonitoring() || monitoringConfig.isEnableErrorMonitoring()) {
            // Update SLA metrics if monitoring was enabled
            if (slaMonitoringController != null) {
                try {
                    slaMonitoringController.updateMetrics(request, response);
                } catch (Exception e) {
                    logger.log(java.util.logging.Level.WARNING, "Failed to update monitoring metrics: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Extract client key from request
     */
    private String extractClientKey() {
        // Extract client key (IP, API key, user ID, etc.)
        return "default-client";
    }

    /**
     * Business Logic Function interface
     */
    @FunctionalInterface
    public interface BusinessLogicFunction {
        Map<String, Object> execute(Map<String, Object> request) throws OASSDKException;
    }

    /**
     * Validation Rules class
     */
    public static class ValidationRules {
        private final List<ValidationRule> rules = new ArrayList<>();
        private final List<ValidationRule> responseRules = new ArrayList<>();

        public void addRule(ValidationRule rule) {
            rules.add(rule);
        }

        public void addResponseRule(ValidationRule rule) {
            responseRules.add(rule);
        }

        public List<ValidationRule> getRules() {
            return new ArrayList<>(rules);
        }

        public List<ValidationRule> getResponseRules() {
            return new ArrayList<>(responseRules);
        }
    }

    /**
     * Validation Rule interface
     */
    @FunctionalInterface
    public interface ValidationRule {
        boolean validate(Map<String, Object> data);

        default String getErrorMessage() {
            return "Validation failed";
        }
    }

    /**
     * SLA Requirements class
     */
    public static class SLARequirements {
        private long maxResponseTime = 0;
        private int maxConcurrentRequests = 0;
        private double maxErrorRate = 0.0;
        private double minAvailability = 0.0;

        public long getMaxResponseTime() {
            return maxResponseTime;
        }

        public void setMaxResponseTime(long maxResponseTime) {
            this.maxResponseTime = maxResponseTime;
        }

        public int getMaxConcurrentRequests() {
            return maxConcurrentRequests;
        }

        public void setMaxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
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

    /**
     * Monitoring Configuration class
     */
    public static class MonitoringConfig {
        private boolean enablePerformanceMonitoring = true;
        private boolean enableErrorMonitoring = true;
        private boolean enableSlaMonitoring = true;

        public boolean isEnablePerformanceMonitoring() {
            return enablePerformanceMonitoring;
        }

        public void setEnablePerformanceMonitoring(boolean enablePerformanceMonitoring) {
            this.enablePerformanceMonitoring = enablePerformanceMonitoring;
        }

        public boolean isEnableErrorMonitoring() {
            return enableErrorMonitoring;
        }

        public void setEnableErrorMonitoring(boolean enableErrorMonitoring) {
            this.enableErrorMonitoring = enableErrorMonitoring;
        }

        public boolean isEnableSlaMonitoring() {
            return enableSlaMonitoring;
        }

        public void setEnableSlaMonitoring(boolean enableSlaMonitoring) {
            this.enableSlaMonitoring = enableSlaMonitoring;
        }
    }
}
