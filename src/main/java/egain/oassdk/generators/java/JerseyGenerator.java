package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Jersey (JAX-RS) code generator.
 * Acts as the top-level coordinator, delegating to specialised sub-generators.
 */
public class JerseyGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(JerseyGenerator.class);

    private GeneratorConfig config;
    private boolean isModelsOnly = false;
    private final Map<Object, String> inlinedSchemas = new IdentityHashMap<>();
    private final ThreadLocal<Map<String, Object>> currentSpecForResolution = new ThreadLocal<>();

    // -----------------------------------------------------------------------
    //  CodeGenerator / ConfigurableGenerator interface
    // -----------------------------------------------------------------------

    @Override public String getName()      { return "Jersey Generator"; }
    @Override public String getVersion()   { return "1.0.0"; }
    @Override public String getLanguage()  { return "java"; }
    @Override public String getFramework() { return "jersey"; }

    @Override public void setConfig(GeneratorConfig config) { this.config = config; }
    @Override public GeneratorConfig getConfig()            { return this.config; }

    // -----------------------------------------------------------------------
    //  Main generation entry-point
    // -----------------------------------------------------------------------

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            currentSpecForResolution.set(spec);
            this.isModelsOnly = config != null && config.isModelsOnly();

            if (!isModelsOnly) {
                createDirectoryStructure(outputDir, packageName);
            }

            JerseyGenerationContext ctx = new JerseyGenerationContext(spec, outputDir, config, packageName);
            JerseyTypeUtils typeUtils = new JerseyTypeUtils(ctx);
            JerseySchemaCollector schemaCollector = new JerseySchemaCollector(ctx);
            JerseyModelGenerator modelGenerator = new JerseyModelGenerator(ctx, typeUtils, schemaCollector);

            schemaCollector.collectInlinedSchemas(spec);
            this.inlinedSchemas.putAll(ctx.getInlinedSchemas());

            modelGenerator.generateModels(spec, outputDir, packageName);
            this.inlinedSchemas.putAll(ctx.getInlinedSchemas());

            if (config != null && config.isAuthorizationDataGenerationEnabled()) {
                new JerseyAuthorizationDataGenerator().generate(spec, outputDir, config);
            }

            new JerseyQueryParamValidatorGenerator(ctx).generate();

            JerseyValidationGenerator validationGenerator = new JerseyValidationGenerator(ctx);
            // Runtime support classes (RequestInfo, Validations) and fixed-package parameter
            // validators are always emitted, regardless of models-only mode.
            validationGenerator.generateSupportClasses();

            // The egain.framework.validation runtime framework (ValidatorAction, Validator,
            // ValidationError, builders, L10NResource) is likewise always emitted.
            new JerseyValidationFrameworkGenerator(ctx).generate();

            if (!isModelsOnly) {
                JerseyBuildGenerator buildGenerator = new JerseyBuildGenerator(ctx);
                buildGenerator.generateMainApplicationClass(spec, outputDir, packageName);

                new JerseyResourceGenerator(ctx, this::getJavaType).generate();
                // Standalone builds have no eGain platform on the classpath, so emit local stubs for
                // the authorization types (Actor/ActorType/OAuthScope) the resources reference.
                new JerseyAuthorizationFrameworkGenerator(ctx).generate();

                buildGenerator.generateServices(outputDir, packageName);
                buildGenerator.generateConfiguration(outputDir, packageName);
                buildGenerator.generateExceptionMappers(outputDir, packageName);
                buildGenerator.generateBuildFiles(spec, outputDir, packageName);

                new JerseyObservabilityGenerator(ctx).generate();
            }

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Jersey application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Jersey application: " + e.getMessage(), e);
        } finally {
            currentSpecForResolution.remove();
        }
    }

    // -----------------------------------------------------------------------
    //  Directory structure
    // -----------------------------------------------------------------------

    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String packagePath = packageName != null ? packageName.replace(".", "/") : "com/example/api";

        for (String dir : new String[]{
                outputDir + "/src/main/java/" + packagePath,
                outputDir + "/src/main/java/" + packagePath + "/resources",
                outputDir + "/src/main/java/" + packagePath + "/model",
                outputDir + "/src/main/java/" + packagePath + "/service",
                outputDir + "/src/main/java/" + packagePath + "/config",
                outputDir + "/src/main/java/" + packagePath + "/exception",
                outputDir + "/src/main/resources",
                outputDir + "/src/test/java/" + packagePath,
                outputDir + "/src/test/java/" + packagePath + "/resources",
                outputDir + "/src/test/java/" + packagePath + "/service"
        }) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    // -----------------------------------------------------------------------
    //  Type resolution delegate (passed as this::getJavaType to sub-generators)
    // -----------------------------------------------------------------------

    private String getJavaType(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }
        Map<String, Object> spec = currentSpecForResolution.get();
        JerseyGenerationContext tempCtx = new JerseyGenerationContext(spec, null, config, null);
        tempCtx.getInlinedSchemas().putAll(this.inlinedSchemas);
        return new JerseyTypeUtils(tempCtx).getJavaType(schema);
    }

}
