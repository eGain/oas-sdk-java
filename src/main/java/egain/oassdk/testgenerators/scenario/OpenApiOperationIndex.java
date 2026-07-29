package egain.oassdk.testgenerators.scenario;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Compact index of OpenAPI operations used for LLM prompting and step validation.
 */
public final class OpenApiOperationIndex {

    public record OperationRef(String method, String path, String operationId, String summary,
                               List<String> requiredParams) {
    }

    private final List<OperationRef> operations;
    private final Map<String, OperationRef> byOperationId;
    private final Map<String, OperationRef> byMethodPath;

    private OpenApiOperationIndex(List<OperationRef> operations,
                                  Map<String, OperationRef> byOperationId,
                                  Map<String, OperationRef> byMethodPath) {
        this.operations = List.copyOf(operations);
        this.byOperationId = Map.copyOf(byOperationId);
        this.byMethodPath = Map.copyOf(byMethodPath);
    }

    public static OpenApiOperationIndex fromSpec(Map<String, Object> spec) {
        List<OperationRef> ops = new ArrayList<>();
        Map<String, OperationRef> byId = new LinkedHashMap<>();
        Map<String, OperationRef> byMp = new LinkedHashMap<>();

        Map<String, Object> paths = Util.asStringObjectMap(spec != null ? spec.get("paths") : null);
        if (paths == null) {
            return new OpenApiOperationIndex(ops, byId, byMp);
        }

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                String methodKey = methodEntry.getKey();
                if (methodKey == null) {
                    continue;
                }
                String method = methodKey.toLowerCase(Locale.ROOT);
                if (!isHttpMethod(method)) {
                    continue;
                }
                Map<String, Object> operation = Util.asStringObjectMap(methodEntry.getValue());
                if (operation == null) {
                    continue;
                }
                String operationId = operation.get("operationId") != null
                        ? operation.get("operationId").toString()
                        : null;
                String summary = operation.get("summary") != null
                        ? operation.get("summary").toString()
                        : (operation.get("description") != null ? operation.get("description").toString() : "");
                List<String> required = extractRequiredParams(operation);
                OperationRef ref = new OperationRef(method.toUpperCase(Locale.ROOT), path, operationId, summary, required);
                ops.add(ref);
                byMp.put(methodPathKey(ref.method(), ref.path()), ref);
                if (operationId != null && !operationId.isBlank()) {
                    byId.put(operationId, ref);
                }
            }
        }
        return new OpenApiOperationIndex(ops, byId, byMp);
    }

    public List<OperationRef> getOperations() {
        return operations;
    }

    public String toPromptIndex() {
        StringBuilder sb = new StringBuilder();
        for (OperationRef op : operations) {
            sb.append("- ").append(op.method()).append(' ').append(op.path());
            if (op.operationId() != null && !op.operationId().isBlank()) {
                sb.append(" (operationId=").append(op.operationId()).append(')');
            }
            if (op.summary() != null && !op.summary().isBlank()) {
                sb.append(" — ").append(op.summary());
            }
            if (op.requiredParams() != null && !op.requiredParams().isEmpty()) {
                sb.append(" [required: ").append(String.join(", ", op.requiredParams())).append(']');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Resolve and normalize a step against the OpenAPI index.
     *
     * @return resolved step copy
     * @throws OASSDKException if the step cannot be mapped and fail-fast is requested by caller
     */
    public GeneratedScenario.ScenarioStep resolveStep(GeneratedScenario.ScenarioStep step, boolean failOnUnmapped)
            throws OASSDKException {
        Objects.requireNonNull(step, "step");
        OperationRef ref = null;
        if (step.getOperationId() != null && !step.getOperationId().isBlank()) {
            ref = byOperationId.get(step.getOperationId());
        }
        if (ref == null && step.getMethod() != null && step.getPath() != null) {
            ref = byMethodPath.get(methodPathKey(step.getMethod(), step.getPath()));
            if (ref == null) {
                // try path template match ignoring trailing slash
                String normalizedPath = normalizePath(step.getPath());
                for (OperationRef candidate : operations) {
                    if (candidate.method().equalsIgnoreCase(step.getMethod())
                            && normalizePath(candidate.path()).equals(normalizedPath)) {
                        ref = candidate;
                        break;
                    }
                }
            }
        }
        if (ref == null) {
            String detail = "Unmapped scenario step: method=" + step.getMethod()
                    + " path=" + step.getPath()
                    + " operationId=" + step.getOperationId();
            if (failOnUnmapped) {
                throw new OASSDKException(detail);
            }
            return step;
        }

        GeneratedScenario.ScenarioStep resolved = new GeneratedScenario.ScenarioStep();
        resolved.setMethod(ref.method());
        resolved.setPath(ref.path());
        resolved.setOperationId(ref.operationId());
        resolved.setHeaders(step.getHeaders());
        resolved.setQuery(step.getQuery());
        resolved.setBody(step.getBody());
        resolved.setExpectedStatus(step.getExpectedStatus() > 0 ? step.getExpectedStatus() : 200);
        return resolved;
    }

    public boolean containsMethodPath(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        if (byMethodPath.containsKey(methodPathKey(method, path))) {
            return true;
        }
        String normalized = normalizePath(path);
        for (OperationRef op : operations) {
            if (op.method().equalsIgnoreCase(method) && normalizePath(op.path()).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractRequiredParams(Map<String, Object> operation) {
        List<String> required = new ArrayList<>();
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        if (params == null) {
            return required;
        }
        for (Map<String, Object> p : params) {
            if (p == null) {
                continue;
            }
            Object req = p.get("required");
            if (Boolean.TRUE.equals(req) || "true".equalsIgnoreCase(String.valueOf(req))) {
                Object name = p.get("name");
                if (name != null) {
                    required.add(name.toString());
                }
            }
        }
        return required;
    }

    private static boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "patch", "delete", "head", "options", "trace" -> true;
            default -> false;
        };
    }

    private static String methodPathKey(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + normalizePath(path);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
