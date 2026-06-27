package egain.oassdk.flow;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class OpenApiOperationCatalog {
    private final Map<String, OperationMeta> operations;

    private OpenApiOperationCatalog(Map<String, OperationMeta> operations) {
        this.operations = operations;
    }

    public static OpenApiOperationCatalog fromSpec(Map<String, Object> spec) {
        Map<String, OperationMeta> map = new HashMap<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return new OpenApiOperationCatalog(map);
        }
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            String path = entry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(entry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                Map<String, Object> op = Util.asStringObjectMap(pathItem.get(method));
                if (op == null) {
                    continue;
                }
                Object opIdObj = op.get("operationId");
                if (opIdObj == null || opIdObj.toString().isBlank()) {
                    continue;
                }
                String opId = opIdObj.toString();
                boolean hasBody = op.containsKey("requestBody");
                Set<String> pathParams = new HashSet<>();
                java.util.List<Map<String, Object>> params = Util.asStringObjectMapList(op.get("parameters"));
                if (params != null) {
                    for (Map<String, Object> param : params) {
                        if ("path".equals(param.get("in")) && param.get("name") != null) {
                            pathParams.add(param.get("name").toString());
                        }
                    }
                }
                map.put(opId, new OperationMeta(opId, method.toUpperCase(), path, hasBody, pathParams));
            }
        }
        return new OpenApiOperationCatalog(map);
    }

    public boolean hasOperation(String operationId) {
        return operations.containsKey(operationId);
    }

    public OperationMeta operation(String operationId) {
        return operations.get(operationId);
    }

    public Set<String> operationIds() {
        return Set.copyOf(operations.keySet());
    }

    public record OperationMeta(String operationId, String method, String path, boolean hasBody, Set<String> pathParameters) {
    }
}
