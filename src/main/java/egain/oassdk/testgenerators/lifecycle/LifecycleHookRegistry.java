package egain.oassdk.testgenerators.lifecycle;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps OpenAPI operationIds to cross-spec lifecycle hooks (v20 async poll, internal KB verify, etc.).
 * Built-in defaults cover eGain Content Manager folder operations; extensions via
 * {@code x-oas-sdk-lifecycle} on operations override defaults.
 */
public final class LifecycleHookRegistry {

    public enum Hook {
        V20_ASYNC_TASK_POLL,
        V20_INTERNAL_KB_VERIFY,
        V20_SYNC_DELETE_CLEANUP,
        IF_MATCH_EDIT
    }

    private final Map<String, Map<Hook, Boolean>> operationHooks = new LinkedHashMap<>();

    public LifecycleHookRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        enable("createFolder", Hook.V20_INTERNAL_KB_VERIFY);
        enable("editFolder", Hook.V20_INTERNAL_KB_VERIFY, Hook.IF_MATCH_EDIT);
        enable("deleteFolder", Hook.V20_ASYNC_TASK_POLL, Hook.V20_INTERNAL_KB_VERIFY);
    }

    public void registerFromSpec(Map<String, Object> spec) {
        if (spec == null) {
            return;
        }
        Object pathsObj = spec.get("paths");
        if (!(pathsObj instanceof Map<?, ?> paths)) {
            return;
        }
        for (Object pathItemObj : paths.values()) {
            if (!(pathItemObj instanceof Map<?, ?> pathItem)) {
                continue;
            }
            for (String method : new String[]{"get", "post", "put", "patch", "delete"}) {
                Object opObj = pathItem.get(method);
                if (!(opObj instanceof Map<?, ?> operation)) {
                    continue;
                }
                Object opIdObj = operation.get("operationId");
                if (opIdObj == null) {
                    continue;
                }
                String operationId = opIdObj.toString();
                Object extObj = operation.get("x-oas-sdk-lifecycle");
                if (!(extObj instanceof Map<?, ?> ext)) {
                    continue;
                }
                applyExtension(operationId, ext);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyExtension(String operationId, Map<?, ?> ext) {
        for (Map.Entry<?, ?> e : ext.entrySet()) {
            String key = Objects.toString(e.getKey(), "").toLowerCase(Locale.ROOT);
            boolean on = Boolean.TRUE.equals(e.getValue()) || "true".equalsIgnoreCase(Objects.toString(e.getValue(), ""));
            if (!on) {
                continue;
            }
            switch (key) {
                case "asyncpoll", "async_poll", "v20-async-task" -> enable(operationId, Hook.V20_ASYNC_TASK_POLL);
                case "verify", "verifyremoval", "v20-internal-kb-folder" ->
                        enable(operationId, Hook.V20_INTERNAL_KB_VERIFY);
                case "cleanup", "v20-sync-delete" -> enable(operationId, Hook.V20_SYNC_DELETE_CLEANUP);
                case "ifmatch", "if_match" -> enable(operationId, Hook.IF_MATCH_EDIT);
                default -> { /* ignore unknown keys */ }
            }
        }
    }

    public void enable(String operationId, Hook... hooks) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        Map<Hook, Boolean> map = operationHooks.computeIfAbsent(operationId, k -> new LinkedHashMap<>());
        for (Hook h : hooks) {
            map.put(h, true);
        }
    }

    public boolean hasHook(String operationId, Hook hook) {
        if (operationId == null || hook == null) {
            return false;
        }
        Map<Hook, Boolean> map = operationHooks.get(operationId);
        return map != null && Boolean.TRUE.equals(map.get(hook));
    }
}
