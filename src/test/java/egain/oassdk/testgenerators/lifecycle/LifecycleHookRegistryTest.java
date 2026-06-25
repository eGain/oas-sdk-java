package egain.oassdk.testgenerators.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleHookRegistryTest {

    @Test
    void defaults_enableFolderLifecycleHooks() {
        LifecycleHookRegistry registry = new LifecycleHookRegistry();
        assertThat(registry.hasHook("createFolder", LifecycleHookRegistry.Hook.V20_INTERNAL_KB_VERIFY)).isTrue();
        assertThat(registry.hasHook("editFolder", LifecycleHookRegistry.Hook.IF_MATCH_EDIT)).isTrue();
        assertThat(registry.hasHook("deleteFolder", LifecycleHookRegistry.Hook.V20_ASYNC_TASK_POLL)).isTrue();
    }

    @Test
    void registerFromSpec_readsXoasSdkLifecycleExtension() {
        Map<String, Object> spec = new LinkedHashMap<>();
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> pathItem = new LinkedHashMap<>();
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "customOp");
        operation.put("x-oas-sdk-lifecycle", Map.of("asyncPoll", true, "verify", true));
        pathItem.put("post", operation);
        paths.put("/items", pathItem);
        spec.put("paths", paths);

        LifecycleHookRegistry registry = new LifecycleHookRegistry();
        registry.registerFromSpec(spec);

        assertThat(registry.hasHook("customOp", LifecycleHookRegistry.Hook.V20_ASYNC_TASK_POLL)).isTrue();
        assertThat(registry.hasHook("customOp", LifecycleHookRegistry.Hook.V20_INTERNAL_KB_VERIFY)).isTrue();
    }
}
