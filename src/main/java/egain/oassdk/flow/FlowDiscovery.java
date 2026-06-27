package egain.oassdk.flow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class FlowDiscovery {
    private FlowDiscovery() {
    }

    public static List<Path> discover(Properties props, Set<String> includeOperations) throws IOException {
        String dir = props.getProperty("test.flows.dir", "src/test/flows");
        String manifest = props.getProperty("test.flows.manifest", "").trim();
        List<Path> candidates = manifest.isEmpty()
                ? discoverDirectory(Path.of(dir))
                : discoverManifest(Path.of(manifest));
        if (includeOperations == null || includeOperations.isEmpty()) {
            return candidates;
        }
        FlowParser parser = new FlowParser();
        List<Path> filtered = new ArrayList<>();
        for (Path flow : candidates) {
            String content = Files.readString(flow);
            FlowAst.FlowDefinition parsed = parser.parse(content);
            Set<String> ops = parsed.operations().isEmpty()
                    ? collectCallOps(parsed)
                    : Set.copyOf(parsed.operations());
            boolean hit = ops.stream().anyMatch(includeOperations::contains);
            if (hit) {
                filtered.add(flow);
            }
        }
        return filtered;
    }

    private static Set<String> collectCallOps(FlowAst.FlowDefinition parsed) {
        java.util.Set<String> ops = new java.util.LinkedHashSet<>();
        for (FlowAst.FlowStep step : parsed.steps()) {
            if (step instanceof FlowAst.CallStep call) {
                ops.add(call.operationId());
            }
        }
        return ops;
    }

    private static List<Path> discoverDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> flows = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".flow"))
                    .forEach(flows::add);
        }
        Collections.sort(flows);
        return flows;
    }

    private static List<Path> discoverManifest(Path manifest) throws IOException {
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        for (String line : Files.readAllLines(manifest)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            out.add(Path.of(trimmed));
        }
        Collections.sort(out);
        return out;
    }
}
