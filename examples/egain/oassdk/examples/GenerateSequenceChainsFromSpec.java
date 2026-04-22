package egain.oassdk.examples;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.parser.OASParser;
import egain.oassdk.testgenerators.sequence.SequenceChainTestGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads an OpenAPI YAML from disk, resolves external {@code $ref}s using a
 * search root, and emits a pytest bundle of enumerated API-call chains via
 * {@link SequenceChainTestGenerator}.
 * <p>
 * Run from the repo root. Prefer environment variables so shells do not split
 * paths on spaces: {@code SEQUENCE_SPEC_PATH},
 * {@code SEQUENCE_SEARCH_ROOT}, {@code SEQUENCE_OUTPUT_DIR}, optional
 * {@code SEQUENCE_BASE_URL}; then run {@code mvn -q exec:java} (see the
 * {@code exec-maven-plugin} {@code mainClass} in {@code pom.xml}).
 * <p>
 * Or positional program arguments:
 * {@code specPath searchRoot outputDir [baseUrl]}. If {@code baseUrl} is
 * omitted, the generator falls back to the spec's first
 * {@code servers[].url} or {@code http://localhost:8080}.
 */
public final class GenerateSequenceChainsFromSpec {

    private GenerateSequenceChainsFromSpec() {
    }

    public static void main(String[] args) {
        String specPath;
        String searchRoot;
        String outputDir;
        String baseUrl;
        if (args.length >= 3) {
            specPath = Objects.requireNonNull(args[0], "specPath").trim();
            searchRoot = Objects.requireNonNull(args[1], "searchRoot").trim();
            outputDir = Objects.requireNonNull(args[2], "outputDir").trim();
            baseUrl = args.length >= 4 && !args[3].isBlank() ? args[3].trim() : null;
        } else if (args.length == 0) {
            specPath = trimOrNull(System.getenv("SEQUENCE_SPEC_PATH"));
            searchRoot = trimOrNull(System.getenv("SEQUENCE_SEARCH_ROOT"));
            outputDir = trimOrNull(System.getenv("SEQUENCE_OUTPUT_DIR"));
            baseUrl = trimOrNull(System.getenv("SEQUENCE_BASE_URL"));
            if (specPath == null || searchRoot == null || outputDir == null) {
                printUsage();
                System.exit(1);
                return;
            }
        } else {
            printUsage();
            System.exit(1);
            return;
        }

        try {
            run(specPath, searchRoot, outputDir, baseUrl);
            System.out.println("Sequence chain generation finished: " + outputDir);
        } catch (OASSDKException e) {
            System.err.println(e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static void run(String specPath, String searchRoot, String outputDir, String baseUrl)
            throws OASSDKException, IOException {
        Path spec = Paths.get(specPath).normalize();
        if (!Files.isRegularFile(spec)) {
            throw new OASSDKException("Spec file not found: " + spec);
        }

        OASParser parser = new OASParser(List.of(searchRoot));
        String specKey = spec.toAbsolutePath().toString();
        if (File.separatorChar == '\\') {
            specKey = specKey.replace('\\', '/');
        }
        Map<String, Object> map = parser.parse(specKey);
        map = parser.resolveReferences(map, specKey);

        Files.createDirectories(Paths.get(outputDir));
        TestConfig.Builder tc = TestConfig.builder().language("python").framework("pytest");
        if (baseUrl != null) {
            tc.additionalProperties(Map.of("sequence.baseUrl", baseUrl));
        }
        new SequenceChainTestGenerator().generate(map, outputDir, tc.build());
    }

    private static void printUsage() {
        System.err.println("Usage: GenerateSequenceChainsFromSpec <specPath> <searchRoot> <outputDir> [baseUrl]");
        System.err.println("Or set env SEQUENCE_SPEC_PATH, SEQUENCE_SEARCH_ROOT, SEQUENCE_OUTPUT_DIR,");
        System.err.println("optional SEQUENCE_BASE_URL and run with no args.");
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
