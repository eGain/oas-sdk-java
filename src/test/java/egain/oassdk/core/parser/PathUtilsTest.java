package egain.oassdk.core.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that PathUtils normalizes all paths to Unix style (forward slashes).
 */
class PathUtilsTest {

    @Test
    void toUnixPathString_convertsBackslashesToForwardSlashes() {
        String windows = "E:\\Jenkins\\workspace\\egain_master\\api.yaml";
        String unix = PathUtils.toUnixPath(windows);
        assertNotNull(unix);
        assertFalse(unix.contains("\\"), "Path should contain no backslashes: " + unix);
        assertTrue(unix.contains("/"), "Path should use forward slashes: " + unix);
    }

    @Test
    void toUnixPathString_forwardSlashesUnchanged() {
        String unixInput = "/Users/test/platform-api-interfaces/published/core/api.yaml";
        String result = PathUtils.toUnixPath(unixInput);
        assertNotNull(result);
        assertEquals(unixInput, result);
        assertFalse(result.contains("\\"));
    }

    @Test
    void toUnixPathString_nullReturnsNull() {
        assertNull(PathUtils.toUnixPath((String) null));
    }

    @Test
    void toUnixPathString_emptyReturnsEmpty() {
        assertEquals("", PathUtils.toUnixPath(""));
        assertEquals("", PathUtils.toUnixPath("   "));
    }

    @Test
    void toUnixPathPath_producesForwardSlashes() {
        String withBackslashes = "E:\\a\\b\\c.yaml";
        String unix = PathUtils.toUnixPath(Paths.get(withBackslashes));
        assertNotNull(unix);
        assertFalse(unix.contains("\\"), "Path should contain no backslashes: " + unix);
        assertTrue(unix.contains("/"));
    }

    @Test
    void toUnixPathPath_nullReturnsNull() {
        assertNull(PathUtils.toUnixPath((java.nio.file.Path) null));
    }

    @Test
    void toUnixPathString_relativePath() {
        String relative = "models/v4/common.yaml";
        String result = PathUtils.toUnixPath(relative);
        assertNotNull(result);
        assertFalse(result.contains("\\"));
        assertTrue(result.contains("models") && result.contains("common.yaml"));
    }
}
