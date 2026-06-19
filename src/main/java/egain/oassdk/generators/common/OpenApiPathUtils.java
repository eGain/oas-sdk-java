package egain.oassdk.generators.common;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Language-neutral OpenAPI path and server URL helpers shared by code generators.
 */
public final class OpenApiPathUtils {

    private OpenApiPathUtils() {
    }

    public static String extractParentPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        int firstSlash = normalizedPath.indexOf('/');
        if (firstSlash == -1) {
            return "/" + normalizedPath;
        }
        return "/" + normalizedPath.substring(0, firstSlash);
    }

    public static String getRelativePath(String parentPath, String fullPath) {
        if (parentPath == null || parentPath.isEmpty() || "/".equals(parentPath)) {
            return fullPath != null ? fullPath : "";
        }
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }
        String normalizedParent = parentPath.startsWith("/") ? parentPath.substring(1) : parentPath;
        String normalizedFull = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        if (normalizedFull.equals(normalizedParent)) {
            return "";
        }
        if (normalizedFull.startsWith(normalizedParent + "/")) {
            return normalizedFull.substring(normalizedParent.length());
        }
        return fullPath;
    }

    public static String extractServerBasePath(Map<String, Object> spec) {
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> firstServer = servers.getFirst();
            String url = (String) firstServer.get("url");
            if (url != null && !url.isEmpty()) {
                String path;
                int schemeIdx = url.indexOf("://");
                if (schemeIdx >= 0) {
                    int authorityStart = schemeIdx + 3;
                    int pathStart = url.indexOf('/', authorityStart);
                    path = (pathStart >= 0) ? url.substring(pathStart) : "";
                } else {
                    path = url.startsWith("/") ? url : "/" + url;
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return (path.isEmpty() || path.equals("/")) ? "" : path;
            }
        }
        return "";
    }

    public static String buildFullPath(String serverBasePath, String relativePath) {
        if (serverBasePath == null || serverBasePath.isEmpty()) {
            return relativePath;
        }
        String normalizedBase = serverBasePath.endsWith("/")
                ? serverBasePath.substring(0, serverBasePath.length() - 1)
                : serverBasePath;
        String normalizedRelative = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return normalizedBase + normalizedRelative;
    }

    /**
     * Group path operations by parent path for router/blueprint generation.
     */
    public static Map<String, List<PathOperation>> groupOperationsByParentPath(Map<String, Object> spec) {
        Map<String, List<PathOperation>> pathGroups = new LinkedHashMap<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return pathGroups;
        }

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }

            String parentPath = extractParentPath(path);
            List<PathOperation> operations = pathGroups.computeIfAbsent(parentPath, k -> new ArrayList<>());

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation != null) {
                        operations.add(new PathOperation(path, method, operation));
                    }
                }
            }
        }
        return pathGroups;
    }

    public static String getApiTitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    public static String getApiDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("description") : "Generated API";
    }

    public static String getApiVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String version = info != null ? (String) info.get("version") : null;
        return (version != null && !version.isBlank()) ? version : "1.0.0";
    }
}
