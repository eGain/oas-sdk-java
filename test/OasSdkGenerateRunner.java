import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.parser.PathUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs the eGain OAS SDK generateApplication for each spec entry path inside a ZIP file.
 * Usage: OasSdkGenerateRunner &lt;baseOutputDir&gt; &lt;zipPath&gt; &lt;entryPath1&gt; [entryPath2 ...]
 * Or:   OasSdkGenerateRunner &lt;baseOutputDir&gt; &lt;zipPath&gt; &lt;comma-separated entry paths&gt;
 *
 * Entry paths are paths inside the ZIP (e.g. published/core/infomgr/v4/api.yaml); use forward slashes.
 * Each spec is generated into baseOutputDir/&lt;specName&gt; with package egain.ws.oas.generated.&lt;specName&gt;.
 *
 * For testing: use test/lib/platform-api-interfaces.zip and published/core/infomgr/v4/api.yaml
 */
public final class OasSdkGenerateRunner {

    private static final String PACKAGE_PREFIX = "egain.ws.oas.generated.";
    private static final Pattern QUOTE = Pattern.compile("^\"|\"$");

    public static void main(final String[] args) {
        if (args == null || args.length < 3) {
            System.err.println("Usage: OasSdkGenerateRunner <baseOutputDir> <zipPath> <entryPath1> [entryPath2 ...]");
            System.err.println("Example: OasSdkGenerateRunner ./generated test/lib/platform-api-interfaces.zip published/core/infomgr/v4/api.yaml");
            System.exit(1);
        }
        String baseOutputDir = stripQuotes(args[0].trim());
        String zipPath = stripQuotes(args[1].trim());
        List<String> entryPaths = parsePaths(args);
        if (entryPaths.isEmpty()) {
            System.err.println("No entry paths provided.");
            System.exit(1);
        }
        Path zipFile = Paths.get(zipPath).normalize();
        if (!Files.isRegularFile(zipFile)) {
            System.err.println("ZIP file not found or not a file: " + zipPath);
            System.exit(1);
        }
        int failed = 0;
        for (String entryPath : entryPaths) {
            String path = stripQuotes(entryPath.trim()).replace('\\', '/');
            if (path.isEmpty()) {
                continue;
            }
            String specName = deriveSpecNameFromEntryPath(path);
            String outputSubdir = PathUtils.toUnixPath(baseOutputDir) + "/" + specName;
            String packageName = PACKAGE_PREFIX + specName;
            try {
                GeneratorConfig config = GeneratorConfig.builder()
                        .specZipPath(zipFile.toString())
                        .build();
                try (OASSDK sdk = new OASSDK(config, null, null)) {
                    sdk.loadSpec(path);
                    sdk.generateApplication("java", "jersey", packageName, outputSubdir);
                }
                System.out.println("Generated: " + path + " -> " + outputSubdir);
            } catch (OASSDKException e) {
                System.err.println("Failed for " + path + ": " + e.getMessage());
                e.printStackTrace();
                failed++;
            } catch (Exception e) {
                System.err.println("Failed for " + path + ": " + e.getMessage());
                e.printStackTrace();
                failed++;
            }
        }
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static List<String> parsePaths(final String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 3 && args[2].contains(",")) {
            for (String p : args[2].split(",")) {
                String t = stripQuotes(p.trim()).replace('\\', '/');
                if (t.startsWith("/")) {
                    t = t.substring(1);
                }
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        } else {
            for (int i = 2; i < args.length; i++) {
                String t = stripQuotes(args[i].trim()).replace('\\', '/');
                if (t.startsWith("/")) {
                    t = t.substring(1);
                }
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static String stripQuotes(final String s) {
        if (s == null) {
            return "";
        }
        return QUOTE.matcher(s).replaceAll("");
    }

    /**
     * Derives a short spec name from the ZIP entry path (e.g. published/core/infomgr/v4/api.yaml -> api).
     */
    private static String deriveSpecNameFromEntryPath(final String entryPath) {
        String path = entryPath.replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (name.endsWith(".yaml")) {
            name = name.substring(0, name.length() - 5);
        } else if (name.endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.equals("bundle-openapi")) {
            return "main";
        }
        if (name.startsWith("bundle-openapi-")) {
            return name.substring("bundle-openapi-".length()).replace("-", "_");
        }
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
