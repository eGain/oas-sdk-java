package egain.oassdk.generators.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Generates standalone (open-source) stubs for the proprietary eGain authorization types
 * referenced by the generated JAX-RS resources: {@code egain.framework.Actor} (the annotation),
 * {@code egain.framework.ActorType}, and {@code egain.framework.OAuthScope}.
 *
 * <p>In normal (non-standalone) builds these resolve against the eGain platform on the classpath,
 * so nothing is emitted. In standalone mode the platform is unavailable, so local copies are
 * generated — mirroring how {@link JerseyValidationFrameworkGenerator} handles the validation
 * framework — to honor {@code --standalone}'s "no proprietary eGain platform dependencies" contract.
 *
 * <p>The {@code OAuthScope} enum is populated from exactly the scope constants the generated
 * {@code @Actor} annotations reference (collected via
 * {@link JerseyResourceGenerator#collectOAuthScopes}), so the generated code compiles.
 */
class JerseyAuthorizationFrameworkGenerator {

    private final JerseyGenerationContext ctx;

    JerseyAuthorizationFrameworkGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Emit the authorization stubs into {@code egain/framework} — but only in standalone mode,
     * where the proprietary types are not on the classpath.
     */
    void generate() throws IOException {
        if (!ctx.isStandaloneMode()) {
            return;
        }
        if (ctx.outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String sourceRoot = ctx.outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/");
        String frameworkDir = sourceRoot + "egain/framework";
        Files.createDirectories(Paths.get(frameworkDir));

        generateActorType(frameworkDir, JerseyResourceGenerator.collectActorTypes());
        generateOAuthScope(frameworkDir, JerseyResourceGenerator.collectOAuthScopes(ctx.spec));
        generateActor(frameworkDir);
    }

    private void generateActorType(String dir, Set<String> actorTypes) throws IOException {
        StringBuilder constants = new StringBuilder();
        int i = 0;
        for (String type : actorTypes) {
            if (i++ > 0) {
                constants.append(",\n");
            }
            constants.append("    ").append(type);
        }
        if (constants.length() > 0) {
            constants.append(";");
        }
        String content = String.format("""
                package egain.framework;

                /**
                 * Standalone copy of the eGain actor-type enumeration referenced by generated @Actor
                 * annotations. Identifies the kind of authenticated principal an operation accepts.
                 */
                public enum ActorType
                {
                %s
                }
                """, constants);
        writeFile(dir + "/ActorType.java", content);
    }

    private void generateOAuthScope(String dir, Set<String> scopes) throws IOException {
        StringBuilder constants = new StringBuilder();
        int i = 0;
        for (String scope : scopes) {
            if (i++ > 0) {
                constants.append(",\n");
            }
            constants.append("    ").append(scope);
        }
        if (constants.length() > 0) {
            constants.append(";");
        }
        String content = String.format("""
                package egain.framework;

                /**
                 * Standalone copy of the eGain OAuth scope enumeration referenced by generated @Actor
                 * annotations. Constants are derived from the security scopes declared in the OpenAPI spec.
                 */
                public enum OAuthScope
                {
                %s
                }
                """, constants);
        writeFile(dir + "/OAuthScope.java", content);
    }

    private void generateActor(String dir) throws IOException {
        String content = """
                package egain.framework;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                /**
                 * Standalone copy of the eGain authorization annotation. Declares which actor types and
                 * OAuth scopes are permitted to invoke an operation. In an open-source build this is a
                 * documentation-level contract (no enforcement engine is bundled).
                 */
                @Target({ElementType.TYPE, ElementType.METHOD})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Actor
                {
                    ActorType[] type() default {};

                    OAuthScope[] scope() default {};
                }
                """;
        writeFile(dir + "/Actor.java", content);
    }

    private void writeFile(String filePath, String content) throws IOException {
        JerseyGenerationContext.writeFile(filePath, content);
    }
}
