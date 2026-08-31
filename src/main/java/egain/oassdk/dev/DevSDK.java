package egain.oassdk.dev;

import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.metadata.OASMetadata;
import egain.oassdk.core.parser.OASParser;
import egain.oassdk.core.validator.OASValidator;
import egain.oassdk.dev.beans.BeanFactory;
import egain.oassdk.dev.docs.RedoclyGenerator;
import egain.oassdk.dev.limits.RateLimitChecker;
import egain.oassdk.dev.limits.StaticLimitChecker;
import egain.oassdk.dev.sla.SLAGatewayScripts;
import egain.oassdk.dev.validators.APIValidator;

import java.util.Map;

/**
 * Development SDK - Provides components for API development
 * <p>
 * This SDK provides validators, beans, limit checkers, SLA scripts, and documentation
 * for developers to build robust APIs with proper validation and monitoring.
 */
public class DevSDK {

    private final OASParser parser;
    private final OASValidator validator;
    private final OASMetadata metadata;
    private final APIValidator apiValidator;
    private final BeanFactory beanFactory;
    private final StaticLimitChecker staticLimitChecker;
    private final RateLimitChecker rateLimitChecker;
    private final SLAGatewayScripts slaGatewayScripts;
    private final RedoclyGenerator redoclyGenerator;

    // Loaded specifications
    private Map<String, Object> openApiSpec;
    private Map<String, Object> slaSpec;

    public DevSDK() {
        this.parser = new OASParser();
        this.validator = new OASValidator();
        this.metadata = new OASMetadata();
        this.apiValidator = new APIValidator();
        this.beanFactory = new BeanFactory();
        this.staticLimitChecker = new StaticLimitChecker();
        this.rateLimitChecker = new RateLimitChecker();
        this.slaGatewayScripts = new SLAGatewayScripts();
        this.redoclyGenerator = new RedoclyGenerator();
    }

    /**
     * Load OpenAPI specification
     *
     * @param specPath Path to OpenAPI specification file
     * @return This SDK instance for method chaining
     * @throws OASSDKException if specification cannot be loaded
     */
    public DevSDK loadOpenAPISpec(String specPath) throws OASSDKException {
        try {
            this.openApiSpec = parser.parse(specPath);
            validator.validate(this.openApiSpec);
            metadata.extract(this.openApiSpec);
            return this;
        } catch (OASSDKException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OASSDKException("Failed to load OpenAPI specification: " + e.getMessage(), e);
        }
    }

    /**
     * Load SLA specification
     *
     * @param slaPath Path to SLA specification file
     * @return This SDK instance for method chaining
     * @throws OASSDKException if SLA specification cannot be loaded
     */
    public DevSDK loadSLASpec(String slaPath) throws OASSDKException {
        try {
            this.slaSpec = parser.parse(slaPath);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to load SLA specification: " + e.getMessage(), e);
        }
    }

    /**
     * Generate API validators
     *
     * @param outputDir Output directory for validators
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateValidators(String outputDir) throws OASSDKException {
        if (openApiSpec == null) {
            throw new OASSDKException("No OpenAPI specification loaded. Call loadOpenAPISpec() first.");
        }

        try {
            apiValidator.generateValidators(openApiSpec, outputDir);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate validators: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Spring beans
     *
     * @param outputDir   Output directory for beans
     * @param packageName Package name for generated beans
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateBeans(String outputDir, String packageName) throws OASSDKException {
        if (openApiSpec == null) {
            throw new OASSDKException("No OpenAPI specification loaded. Call loadOpenAPISpec() first.");
        }

        try {
            beanFactory.generateBeans(openApiSpec, outputDir, packageName);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate beans: " + e.getMessage(), e);
        }
    }

    /**
     * Generate static limit checkers based on OpenAPI spec
     *
     * @param outputDir Output directory for static limit checkers
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateStaticLimitCheckers(String outputDir) throws OASSDKException {
        if (openApiSpec == null) {
            throw new OASSDKException("No OpenAPI specification loaded. Call loadOpenAPISpec() first.");
        }

        try {
            staticLimitChecker.generateStaticLimitCheckers(openApiSpec, outputDir);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate static limit checkers: " + e.getMessage(), e);
        }
    }

    /**
     * Generate rate limit checkers based on SLA spec
     *
     * @param outputDir Output directory for rate limit checkers
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateRateLimitCheckers(String outputDir) throws OASSDKException {
        if (slaSpec == null) {
            throw new OASSDKException("No SLA specification loaded. Call loadSLASpec() first.");
        }

        try {
            rateLimitChecker.generateRateLimitCheckers(slaSpec, outputDir);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate rate limit checkers: " + e.getMessage(), e);
        }
    }

    /**
     * Generate SLA scripts for API Gateway
     *
     * @param outputDir Output directory for SLA scripts
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateSLAGatewayScripts(String outputDir) throws OASSDKException {
        if (openApiSpec == null || slaSpec == null) {
            throw new OASSDKException("Both OpenAPI and SLA specifications must be loaded.");
        }

        try {
            slaGatewayScripts.generateScripts(openApiSpec, slaSpec, outputDir);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate SLA gateway scripts: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Redocly documentation
     *
     * @param outputDir Output directory for documentation
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateRedoclyDocumentation(String outputDir) throws OASSDKException {
        if (openApiSpec == null) {
            throw new OASSDKException("No OpenAPI specification loaded. Call loadOpenAPISpec() first.");
        }

        try {
            redoclyGenerator.generateDocumentation(openApiSpec, outputDir);
            return this;
        } catch (Exception e) {
            throw new OASSDKException("Failed to generate Redocly documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate all development components
     *
     * @param outputDir   Base output directory
     * @param packageName Package name for generated code
     * @return This SDK instance for method chaining
     * @throws OASSDKException if generation fails
     */
    public DevSDK generateAll(String outputDir, String packageName) throws OASSDKException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        generateValidators(outputDir + "/validators");
        generateBeans(outputDir + "/beans", packageName);
        generateStaticLimitCheckers(outputDir + "/static-limits");
        generateRateLimitCheckers(outputDir + "/rate-limits");
        generateSLAGatewayScripts(outputDir + "/sla-gateway");
        generateRedoclyDocumentation(outputDir + "/docs");
        return this;
    }

    /**
     * Get loaded OpenAPI specification
     *
     * @return OpenAPI specification
     */
    public Map<String, Object> getOpenAPISpec() {
        return openApiSpec != null ? new java.util.LinkedHashMap<>(openApiSpec) : null;
    }

    /**
     * Get loaded SLA specification
     *
     * @return SLA specification
     */
    public Map<String, Object> getSLASpec() {
        return slaSpec != null ? new java.util.LinkedHashMap<>(slaSpec) : null;
    }

    /**
     * Get extracted metadata
     *
     * @return Metadata from OpenAPI specification
     */
    public Map<String, Object> getMetadata() {
        return metadata.getMetadata();
    }
}
