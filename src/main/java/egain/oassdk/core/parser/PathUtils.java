package egain.oassdk.core.parser;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central utility for normalizing all file paths to Unix style (forward slashes).
 * All path processing in the SDK uses Unix-style paths so behavior is consistent
 * on Windows and Unix (e.g. loadedFiles keys, reference resolution, path traversal checks).
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Convert any path string to Unix style: forward slashes, no null bytes, trimmed.
     * If the string is an absolute path, normalizes via Path so that . and .. are resolved.
     *
     * @param path path string (may be Windows or Unix style)
     * @return Unix-style path string, or null if input is null
     */
    public static String toUnixPath(String path) {
        if (path == null) {
            return null;
        }
        String unix = path.replace("\0", "").trim().replace('\\', '/');
        if (unix.isEmpty()) {
            return unix;
        }
        Path p = Paths.get(unix).normalize();
        return (p.isAbsolute() ? p.toAbsolutePath() : p).toString().replace('\\', '/');
    }

    /**
     * Convert a Path to a Unix-style path string (absolute, normalized, forward slashes).
     *
     * @param path path (may have been created with Windows separators)
     * @return Unix-style path string, or null if input is null
     */
    public static String toUnixPath(Path path) {
        if (path == null) {
            return null;
        }
        return path.normalize().toAbsolutePath().toString().replace('\\', '/');
    }
}
