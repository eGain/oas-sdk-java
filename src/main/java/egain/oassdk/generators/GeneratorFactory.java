package egain.oassdk.generators;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.generators.csharp.ASPNETGenerator;
import egain.oassdk.generators.go.GinGenerator;
import egain.oassdk.generators.java.JerseyGenerator;
import egain.oassdk.generators.nodejs.ExpressGenerator;
import egain.oassdk.generators.python.FastAPIGenerator;
import egain.oassdk.generators.python.FlaskGenerator;

import java.util.Locale;


/**
 * Factory for creating code generators based on language and framework
 */
public class GeneratorFactory {

    /**
     * Get generator for specific language and framework
     *
     * @param language  Programming language
     * @param framework Framework
     * @return Code generator instance
     * @throws IllegalArgumentException if language/framework combination is not supported
     */
    public CodeGenerator getGenerator(String language, String framework) {
        String key = language.toLowerCase(Locale.ROOT) + "-" + framework.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "java-jersey", "java-jax-rs", "java-jaxrs" -> new JerseyGenerator();
            case "python-fastapi" -> new FastAPIGenerator();
            case "python-flask" -> new FlaskGenerator();
            case "nodejs-express", "javascript-express" -> new ExpressGenerator();
            case "go-gin" -> new GinGenerator();
            case "csharp-aspnet", "csharp-asp.net" -> new ASPNETGenerator();
            default ->
                    throw new IllegalArgumentException("Unsupported language/framework combination: " + language + "/" + framework);
        };
    }

    /**
     * Get generator with configuration
     *
     * @param language  Programming language
     * @param framework Framework
     * @param config    Generator configuration
     * @return Code generator instance
     */
    public CodeGenerator getGenerator(String language, String framework, GeneratorConfig config) {
        CodeGenerator generator = getGenerator(language, framework);
        if (generator instanceof ConfigurableGenerator) {
            ((ConfigurableGenerator) generator).setConfig(config);
        }
        return generator;
    }

    /**
     * Get list of supported language/framework combinations
     *
     * @return Array of supported combinations
     */
    public String[] getSupportedCombinations() {
        return getSupportedCombinations(true);
    }

    /**
     * Get supported combinations, optionally excluding stub generators.
     *
     * @param includeStubs when {@code false}, only {@link CodeGenerator#isImplemented()} generators are returned
     * @return Array of language-framework keys (e.g. {@code java-jersey})
     */
    public String[] getSupportedCombinations(boolean includeStubs) {
        String[] all = new String[]{
                "java-jersey",
                "python-fastapi",
                "python-flask",
                "nodejs-express",
                "go-gin",
                "csharp-aspnet"
        };
        if (includeStubs) {
            return all;
        }
        return java.util.Arrays.stream(all)
                .filter(key -> {
                    String[] parts = key.split("-", 2);
                    CodeGenerator g = getGenerator(parts[0], parts[1]);
                    return g.isImplemented();
                })
                .toArray(String[]::new);
    }

    /** Fully implemented generator combinations. */
    public String[] getImplementedCombinations() {
        return getSupportedCombinations(false);
    }

    /** Registered stub generators (not yet implemented). */
    public String[] getStubCombinations() {
        return java.util.Arrays.stream(getSupportedCombinations(true))
                .filter(key -> {
                    String[] parts = key.split("-", 2);
                    return !getGenerator(parts[0], parts[1]).isImplemented();
                })
                .toArray(String[]::new);
    }

    /**
     * Fail fast when the selected generator is a registered stub.
     *
     * @throws IllegalArgumentException when {@code generator.isImplemented()} is false
     */
    public void ensureImplemented(CodeGenerator generator, String language, String framework) {
        if (!generator.isImplemented()) {
            throw new IllegalArgumentException(
                    language.toLowerCase(Locale.ROOT) + "-" + framework.toLowerCase(Locale.ROOT)
                            + " is registered but not yet implemented. Available: "
                            + String.join(", ", getImplementedCombinations()));
        }
    }

    /**
     * Check if language/framework combination is supported
     *
     * @param language  Programming language
     * @param framework Framework
     * @return true if supported
     */
    public boolean isSupported(String language, String framework) {
        try {
            getGenerator(language, framework);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
