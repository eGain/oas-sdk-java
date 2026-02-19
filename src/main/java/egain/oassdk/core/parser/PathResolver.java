package egain.oassdk.core.parser;

import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Secure path resolver for external file references
 * Provides path traversal protection and configurable search paths
 *
 * <p>This class is thread-safe. Multiple threads can safely use the same instance
 * concurrently. The recursion depth tracking is thread-local to prevent interference
 * between concurrent resolution operations.</p>
 *
 * <p>Security features:
 * <ul>
 *   <li>Path traversal protection - prevents directory escape attacks</li>
 *   <li>File size limits - maximum 100 MB per file</li>
 *   <li>File extension validation - only .yaml, .yml, .json allowed</li>
 *   <li>Resource limits - recursion depth and search depth limits</li>
 * </ul>
 * </p>
 */
public class PathResolver {

    private static final Logger logger = Logger.getLogger(PathResolver.class.getName());

    // Maximum file size (100 MB)
    public static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    // Maximum recursion depth for file resolution
    private static final int MAX_RECURSION_DEPTH = 50;

    // Allowed file extensions
    private static final String[] ALLOWED_EXTENSIONS = {".yaml", ".yml", ".json"};

    private final List<Path> searchPaths;
    // Thread-local recursion depth to ensure thread safety
    private final ThreadLocal<Integer> currentRecursionDepth = ThreadLocal.withInitial(() -> 0);

    /**
     * Constructor with search paths
     *
     * @param searchPaths List of paths to search for external references
     */
    public PathResolver(List<String> searchPaths) {
        this.searchPaths = new ArrayList<>();
        if (searchPaths != null) {
            for (String searchPath : searchPaths) {
                if (searchPath != null && !searchPath.trim().isEmpty()) {
                    Path path = Paths.get(searchPath).normalize();
                    if (Files.exists(path) && Files.isDirectory(path)) {
                        this.searchPaths.add(path);
                    }
                }
            }
        }

        // Add environment variable search path if set
        String envPath = System.getenv("OAS_SEARCH_PATH");
        if (envPath != null && !envPath.trim().isEmpty()) {
            Path envPathObj = Paths.get(envPath).normalize();
            if (Files.exists(envPathObj) && Files.isDirectory(envPathObj)) {
                this.searchPaths.add(envPathObj);
            }
        }

        // Add system property search path if set
        String sysPropPath = System.getProperty("oas.search.path");
        if (sysPropPath != null && !sysPropPath.trim().isEmpty()) {
            Path sysPropPathObj = Paths.get(sysPropPath).normalize();
            if (Files.exists(sysPropPathObj) && Files.isDirectory(sysPropPathObj)) {
                this.searchPaths.add(sysPropPathObj);
            }
        }
    }

    /**
     * Default constructor - no search paths
     */
    public PathResolver() {
        this(null);
    }

    /**
     * Resolve a file reference with path traversal protection
     *
     * @param filePath File path from $ref
     * @param baseDir  Base directory for relative paths
     * @return Resolved and validated path
     * @throws OASSDKException if path cannot be resolved or is invalid
     */
    public Path resolveReference(String filePath, Path baseDir) throws OASSDKException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new OASSDKException("File path cannot be null or empty");
        }

        // Sanitize file path first (normalize backslashes, etc.)
        String sanitizedPath = sanitizePath(filePath);

        // Validate file extension after sanitization
        validateFileExtension(sanitizedPath);

        // Resolve path relative to base directory
        Path resolvedPath;
        if (baseDir != null) {
            resolvedPath = baseDir.resolve(sanitizedPath).normalize();
        } else {
            resolvedPath = Paths.get(sanitizedPath).normalize();
        }

        // Check if file exists
        if (Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath)) {
            // Validate path traversal protection
            // If validation fails, continue to search paths instead of throwing immediately
            try {
                validatePathTraversal(baseDir, resolvedPath);
                // Validate file size
                validateFileSize(resolvedPath);
                return resolvedPath;
            } catch (OASSDKException e) {
                // Path traversal detected - continue to search paths
                // Don't throw here, let search paths be tried
            }
        }

        // Try search paths
        for (Path searchPath : searchPaths) {
            Path candidatePath = searchPath.resolve(sanitizedPath).normalize();
            if (Files.exists(candidatePath) && Files.isRegularFile(candidatePath)) {
                // Validate path traversal protection
                try {
                    validatePathTraversal(searchPath, candidatePath);
                    // Validate file size
                    validateFileSize(candidatePath);
                    return candidatePath;
                } catch (OASSDKException e) {
                    // Path traversal detected for this search path, try next
                    continue;
                }
            }
        }

        // When path escapes base dir (e.g. ../../../models/v4/Link.yaml), try path under search root with leading ../ stripped
        if (sanitizedPath.contains("../") || sanitizedPath.contains("..\\")) {
            String stripped = sanitizedPath.replace('\\', '/');
            while (stripped.startsWith("../")) {
                stripped = stripped.substring(3);
            }
            if (!stripped.isEmpty()) {
                for (Path searchPath : searchPaths) {
                    Path candidatePath = searchPath.resolve(stripped).normalize();
                    if (Files.exists(candidatePath) && Files.isRegularFile(candidatePath)) {
                        try {
                            validatePathTraversal(searchPath, candidatePath);
                            validateFileSize(candidatePath);
                            return candidatePath;
                        } catch (OASSDKException e) {
                            continue;
                        }
                    }
                }
            }
        }

        // If path contains ../ and wasn't found, try extracting just the filename
        // This handles cases like ../../../models/v4/User.yaml where we want to find User.yaml in search paths
        if (sanitizedPath.contains("../") || sanitizedPath.contains("..\\")) {
            String fileName = Paths.get(sanitizedPath).getFileName().toString();
            if (fileName != null && !fileName.isEmpty()) {
                for (Path searchPath : searchPaths) {
                    Path candidatePath = searchPath.resolve(fileName).normalize();
                    if (Files.exists(candidatePath) && Files.isRegularFile(candidatePath)) {
                        try {
                            validatePathTraversal(searchPath, candidatePath);
                            validateFileSize(candidatePath);
                            return candidatePath;
                        } catch (OASSDKException e) {
                            // Path traversal detected, try next
                            continue;
                        }
                    }
                }
            }
        }

        // If not found in search paths, try recursive search (with depth limit)
        // Note: currentRecursionDepth tracks external file resolution depth, not recursive search depth
        if (currentRecursionDepth.get() < MAX_RECURSION_DEPTH) {
            for (Path searchPath : searchPaths) {
                try {
                    // First try with full path (e.g. models/v4/User.yaml)
                    Path foundPath = findFileRecursively(sanitizedPath, searchPath);
                    // If not found, try with just the filename (e.g. ./UserView.yaml -> UserView.yaml)
                    if (foundPath == null) {
                        String fileName = Paths.get(sanitizedPath).getFileName().toString();
                        if (fileName != null && !fileName.isEmpty() && !fileName.equals(sanitizedPath)) {
                            foundPath = findFileRecursively(fileName, searchPath);
                        }
                    }
                    if (foundPath != null) {
                        try {
                            validatePathTraversal(searchPath, foundPath);
                            validateFileSize(foundPath);
                            return foundPath;
                        } catch (OASSDKException e) {
                            // Path traversal detected, try next search path
                            continue;
                        }
                    }
                } catch (IOException e) {
                    // Continue to next search path
                }
            }
        }

        throw new OASSDKException("Referenced file not found: " + filePath +
                " (searched in base directory and " + searchPaths.size() + " search paths)");
    }

    /**
     * Find file recursively in a directory (with depth limit)
     *
     * <p>Note: This method uses Files.walk() which has its own depth limit (10 levels).
     * The currentRecursionDepth tracks external file resolution depth, not the recursive
     * search depth within a single directory tree.</p>
     */
    private Path findFileRecursively(String fileName, Path searchBase) throws IOException {
        int depth = currentRecursionDepth.get();
        if (depth >= MAX_RECURSION_DEPTH) {
            return null;
        }

        currentRecursionDepth.set(depth + 1);
        try {
            if (!Files.exists(searchBase) || !Files.isDirectory(searchBase)) {
                return null;
            }

            // Files.walk() has its own depth limit (10 levels) for recursive search
            try (var stream = Files.walk(searchBase, 10)) {
                return stream
                        .filter(p -> {
                            java.nio.file.Path fileNamePath = p.getFileName();
                            return Files.isRegularFile(p) && fileNamePath != null && fileNamePath.toString().equals(fileName);
                        })
                        .findFirst()
                        .orElse(null);
            }
        } finally {
            currentRecursionDepth.set(depth);
        }
    }

    /**
     * Normalize path to a canonical string (forward slashes) for cross-platform comparison.
     * On Windows, Path.startsWith() can be inconsistent when paths use different separators;
     * comparing canonical strings ensures validation works the same on all platforms.
     */
    private static String toCanonicalPathString(Path path) {
        if (path == null) {
            return "";
        }
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    /**
     * Validate that resolved path doesn't escape base directory (path traversal protection)
     *
     * <p>Logs path traversal attempts at FINE (debug) level when resolution continues via search paths.</p>
     * <p>Uses canonical path strings (forward slashes) so validation behaves consistently on Windows and Unix.</p>
     */
    private void validatePathTraversal(Path baseDir, Path resolvedPath) throws OASSDKException {
        if (baseDir == null) {
            // If no base directory, just check that path is absolute and exists
            if (!resolvedPath.isAbsolute()) {
                logger.fine("Path traversal attempt detected: relative path without base directory - " + resolvedPath);
                throw new OASSDKException("Path traversal detected: relative path without base directory");
            }
            return;
        }

        try {
            String canonicalBase = toCanonicalPathString(baseDir);
            String canonicalResolved = toCanonicalPathString(resolvedPath);

            // Ensure resolved path is within base directory (string comparison for cross-platform consistency)
            if (!canonicalResolved.startsWith(canonicalBase)) {
                // Log security event for monitoring
                logger.fine("Path traversal attempt detected: " + resolvedPath +
                        " escapes base directory " + baseDir);
                throw new OASSDKException("Path traversal detected: " + resolvedPath +
                        " escapes base directory " + baseDir);
            }
            // Also require a path separator after the base so "E:/a" does not allow "E:/ab"
            if (canonicalResolved.length() > canonicalBase.length()
                    && canonicalResolved.charAt(canonicalBase.length()) != '/') {
                logger.fine("Path traversal attempt detected: " + resolvedPath +
                        " escapes base directory " + baseDir);
                throw new OASSDKException("Path traversal detected: " + resolvedPath +
                        " escapes base directory " + baseDir);
            }
        } catch (Exception e) {
            if (e instanceof OASSDKException) {
                throw e;
            }
            throw new OASSDKException("Failed to validate path: " + resolvedPath, e);
        }
    }

    /**
     * Validate file size
     */
    private void validateFileSize(Path filePath) throws OASSDKException {
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                throw new OASSDKException("File too large: " + filePath +
                        " (" + fileSize + " bytes, max: " + MAX_FILE_SIZE + " bytes)");
            }
        } catch (IOException e) {
            throw new OASSDKException("Failed to check file size: " + filePath, e);
        }
    }

    /**
     * Validate file extension
     */
    private void validateFileExtension(String filePath) throws OASSDKException {
        String lowerPath = filePath.toLowerCase(Locale.ROOT);
        boolean valid = false;
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new OASSDKException("Invalid file extension: " + filePath +
                    " (allowed: .yaml, .yml, .json)");
        }
    }

    /**
     * Sanitize file path to prevent path traversal attacks
     */
    private String sanitizePath(String filePath) {
        // Remove any null bytes
        String sanitized = filePath.replace("\0", "");

        // Remove leading/trailing whitespace
        sanitized = sanitized.trim();

        // Replace backslashes with forward slashes for cross-platform compatibility
        sanitized = sanitized.replace('\\', '/');

        // Note: Path traversal protection is handled by validatePathTraversal() method
        // which ensures resolved paths don't escape the base directory. We don't remove
        // "../" sequences here to allow legitimate relative paths to work correctly.

        return sanitized;
    }

    /**
     * Get current recursion depth for the current thread
     *
     * @return Current recursion depth (thread-local)
     */
    public int getCurrentRecursionDepth() {
        return currentRecursionDepth.get();
    }

    /**
     * Reset recursion depth counter for the current thread
     */
    public void resetRecursionDepth() {
        this.currentRecursionDepth.set(0);
    }

    /**
     * Clean up thread-local resources
     * Should be called when done with the PathResolver instance to prevent memory leaks
     */
    public void cleanup() {
        this.currentRecursionDepth.remove();
    }
}

