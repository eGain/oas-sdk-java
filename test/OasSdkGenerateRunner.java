import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.Collections;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs the eGain OAS SDK generateApplication for each YAML spec path.
 * Usage: OasSdkGenerateRunner &lt;baseOutputDir&gt; &lt;yamlPath1&gt; [yamlPath2 ...]
 * Or:   OasSdkGenerateRunner &lt;baseOutputDir&gt; &lt;comma-separated yaml paths&gt;
 *
 * Each spec is generated into baseOutputDir/&lt;specName&gt; with package egain.ws.oas.generated.&lt;specName&gt;.
 */
public final class OasSdkGenerateRunner {

    private static final String PACKAGE_PREFIX = "egain.ws.oas.generated.";
    private static final Pattern QUOTE = Pattern.compile("^\"|\"$");

    public static void main(final String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Usage: OasSdkGenerateRunner <baseOutputDir> <yamlPath1> [yamlPath2 ...]");
            System.exit(1);
        }
        String baseOutputDir = stripQuotes(args[0].trim());
        List<String> yamlPaths = parsePaths(args);
        if (yamlPaths.isEmpty()) {
            System.err.println("No YAML paths provided.");
            System.exit(1);
        }
        int failed = 0;
        for (String yamlPath : yamlPaths) {
            String path = stripQuotes(yamlPath.trim());
            if (path.isEmpty()) {
                continue;
            }
            File f = new File(path);
            if (!f.isFile()) {
                System.err.println("Skipping (not a file): " + path);
                failed++;
                continue;
            }
            // Normalize path for consistent behavior on Windows and Unix (avoids mixed separators)
            Path specPath = Paths.get(path).normalize().toAbsolutePath();
            String pathStr = specPath.toString();
            System.out.println("_______________ Generated: pathStr -> " + pathStr);
            String specName = deriveSpecName(pathStr);
            System.out.println("_______________ Generated: specName -> " + specName);
            String outputSubdir = baseOutputDir + "/" + specName;
            System.out.println("_______________ Generated: outputSubdir -> " + outputSubdir);
            String packageName = PACKAGE_PREFIX + specName;
            String folderName = "platform-api-interfaces";
            try {
                int index = pathStr.indexOf(folderName);
                if (index == -1)
                    continue;
                Path repoRoot = Paths.get(pathStr.substring(0, index + folderName.length())).normalize().toAbsolutePath();
                System.out.println("_______________ Generated: repoRoot -> " + repoRoot.toString());
                // Use published/ as search path so refs like ../../../models/v4/common.yaml resolve to published/models/v4/common.yaml
                Path publishedPath = repoRoot.resolve("published").normalize();
                System.out.println("_______________ Generated: publishedPath -> " + publishedPath.toString());
                String publishedDir = publishedPath.toAbsolutePath().toString();
                System.out.println("_______________ Generated: publishedDir -> " + publishedDir);
                if (!publishedPath.toFile().isDirectory()) {
                    publishedDir = repoRoot.toAbsolutePath().toString();
                }
                //System.out.println("_______________ Generated: publishedDir -> " + publishedDir);
                GeneratorConfig config = GeneratorConfig.builder()
                .searchPaths(Collections.singletonList(publishedDir))
                .build();
                OASSDK sdk = new OASSDK(config, null, null);
            
                sdk.loadSpec(pathStr);
                sdk.generateApplication("java", "jersey", packageName, outputSubdir);
                System.out.println("Generated: " + pathStr + " -> " + outputSubdir);
            } catch (OASSDKException e) {
                System.err.println("Failed for " + pathStr + ": " + e.getMessage());
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
        if (args.length == 2 && args[1].contains(",")) {
            for (String p : args[1].split(",")) {
                String t = stripQuotes(p.trim());
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        } else {
            for (int i = 1; i < args.length; i++) {
                String t = stripQuotes(args[i].trim());
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
     * Derives a short spec name from the YAML path for output subdir and package (e.g. main, department).
     */
    private static String deriveSpecName(final String path) {
        String name = new File(path).getName();
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
