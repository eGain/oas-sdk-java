package egain.oassdk.core.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import egain.oassdk.Util;
import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parser for OpenAPI and SLA YAML files
 */
public class OASParser {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final PathResolver pathResolver;

    public OASParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.registerModule(new JavaTimeModule());
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.registerModule(new JavaTimeModule());
        this.pathResolver = new PathResolver();
    }

    /**
     * Constructor with search paths for external file references
     *
     * @param searchPaths List of paths to search for external references
     */
    public OASParser(List<String> searchPaths) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.registerModule(new JavaTimeModule());
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.registerModule(new JavaTimeModule());
        this.pathResolver = new PathResolver(searchPaths);
    }

    /**
     * Parse OpenAPI or SLA specification from file
     *
     * @param filePath Path to the YAML file
     * @return Parsed specification as Map
     * @throws OASSDKException if parsing fails
     */
    public Map<String, Object> parse(String filePath) throws OASSDKException {
        // Validate input
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new OASSDKException("File path cannot be null or empty");
        }

        // Sanitize file path
        String sanitizedPath = sanitizeFilePath(filePath);

        try {
            Path path = Paths.get(sanitizedPath);
            if (!Files.exists(path)) {
                throw new OASSDKException("File not found: " + filePath);
            }

            if (!Files.isRegularFile(path)) {
                throw new OASSDKException("Path is not a regular file: " + filePath);
            }

            // Validate file size before reading
            long fileSize = Files.size(path);
            if (fileSize > PathResolver.MAX_FILE_SIZE) {
                throw new OASSDKException("File too large: " + filePath +
                        " (" + fileSize + " bytes, max: " + PathResolver.MAX_FILE_SIZE + " bytes)");
            }

            String content = Files.readString(path);
            return parseContent(content, filePath);

        } catch (IOException e) {
            throw new OASSDKException("Failed to read file: " + filePath, e);
        }
    }

    /**
     * Sanitize file path to prevent path traversal attacks
     */
    private String sanitizeFilePath(String filePath) {
        // Remove any null bytes
        String sanitized = filePath.replace("\0", "");

        // Remove leading/trailing whitespace
        sanitized = sanitized.trim();

        // Replace backslashes with forward slashes for cross-platform compatibility
        sanitized = sanitized.replace('\\', '/');

        return sanitized;
    }

    /**
     * Parse OpenAPI or SLA specification from content
     *
     * @param content  YAML content as string
     * @param filePath Original file path for error reporting
     * @return Parsed specification as Map
     * @throws OASSDKException if parsing fails
     */
    public Map<String, Object> parseContent(String content, String filePath) throws OASSDKException {
        try {
            // Try YAML first
            if (filePath.toLowerCase(Locale.ROOT).endsWith(".yaml") || filePath.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                return Util.asStringObjectMap(yamlMapper.readValue(content, Map.class));
            } else if (filePath.toLowerCase(Locale.ROOT).endsWith(".json")) {
                return Util.asStringObjectMap(jsonMapper.readValue(content, Map.class));
            } else {
                // Try to detect format by content
                if (content.trim().startsWith("{")) {
                    return Util.asStringObjectMap(jsonMapper.readValue(content, Map.class));
                } else {
                    return Util.asStringObjectMap(yamlMapper.readValue(content, Map.class));
                }
            }
        } catch (Exception e) {
            throw new OASSDKException("Failed to parse specification file: " + filePath, e);
        }
    }

    /**
     * Validate that the parsed content is a valid OpenAPI specification
     *
     * @param spec Parsed specification
     * @return true if valid OpenAPI spec
     */
    public boolean isOpenAPISpec(Map<String, Object> spec) {
        return spec.containsKey("openapi") || spec.containsKey("swagger");
    }

    /**
     * Validate that the parsed content is a valid SLA specification
     *
     * @param spec Parsed specification
     * @return true if valid SLA spec
     */
    public boolean isSLASpec(Map<String, Object> spec) {
        return spec.containsKey("sla") || spec.containsKey("nfr") || spec.containsKey("non-functional-requirements");
    }

    /**
     * Get OpenAPI version from specification
     *
     * @param spec Parsed specification
     * @return OpenAPI version or null if not found
     */
    public String getOpenAPIVersion(Map<String, Object> spec) {
        if (spec.containsKey("openapi")) {
            Object value = spec.get("openapi");
            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Number) {
                return String.valueOf(value);
            }
        } else if (spec.containsKey("swagger")) {
            Object value = spec.get("swagger");
            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Number) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * Get API title from specification
     *
     * @param spec Parsed specification
     * @return API title or null if not found
     */
    public String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info != null && info.containsKey("title")) {
            return (String) info.get("title");
        }
        return null;
    }

    /**
     * Get API version from specification
     *
     * @param spec Parsed specification
     * @return API version or null if not found
     */
    public String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info != null && info.containsKey("version")) {
            return (String) info.get("version");
        }
        return null;
    }

    /**
     * Resolve all $ref references in the specification
     * This includes both internal references (#/components/...) and external file references
     *
     * @param spec         Parsed specification
     * @param baseFilePath Base file path for resolving external references
     * @return Specification with all references resolved
     * @throws OASSDKException if reference resolution fails
     */
    public Map<String, Object> resolveReferences(Map<String, Object> spec, String baseFilePath) throws OASSDKException {
        if (spec == null) {
            return spec;
        }

        // IMPORTANT: Use the original spec directly, not a copy
        // This ensures that when we modify maps during resolution, we're modifying the actual spec
        // If we use a copy, the modifications won't be reflected in the original
        Map<String, Object> resolvedSpec = spec;
        Map<String, Map<String, Object>> loadedFiles = new HashMap<>();

        // Load the base file into the cache
        Path basePath = Paths.get(baseFilePath);
        String baseFileKey = basePath.normalize().toString();
        loadedFiles.put(baseFileKey, resolvedSpec);

        // Track references currently being resolved to detect circular references
        Set<String> resolvingRefs = new HashSet<>();
        // Track visited objects to prevent infinite recursion
        // Use IdentityHashMap-based set to avoid hashCode() issues with circular references
        Set<Object> visitedObjects = Collections.newSetFromMap(new IdentityHashMap<>());

        // Resolve all references recursively
        resolveReferencesRecursive(resolvedSpec, basePath.getParent(), baseFileKey, loadedFiles, resolvingRefs, visitedObjects);

        // After all references are resolved, merge external schemas into the main spec
        // This allows generators to find and generate models from external files
        Map<String, Object> mainSpec = loadedFiles.get(baseFileKey);
        for (Map.Entry<String, Map<String, Object>> entry : loadedFiles.entrySet()) {
            if (!entry.getKey().equals(baseFileKey) && entry.getValue() != mainSpec) {
                mergeExternalSchemasIntoMainSpec(entry.getValue(), mainSpec);
            }
        }

        return resolvedSpec;
    }

    /**
     * Recursively resolve $ref references in the specification
     */
    private void resolveReferencesRecursive(Object obj, Path baseDir, String currentFileKey,
                                            Map<String, Map<String, Object>> loadedFiles, Set<String> resolvingRefs, Set<Object> visitedObjects) throws OASSDKException {
        if (obj == null) {
            return;
        }

        // Check if we've already visited this object to prevent infinite recursion
        if (visitedObjects.contains(obj)) {
            return;
        }

        if (obj instanceof Map) {
            // IMPORTANT: Cast directly to avoid creating a new map instance
            // Util.asStringObjectMap might create a new LinkedHashMap, which would break our reference
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map == null) {
                return;
            }

            // Check if this is a $ref
            // IMPORTANT: Only resolve if map has ONLY $ref (size == 1)
            // This ensures we don't try to resolve $refs that are part of a larger schema object
            // However, for external file references that should be fully replaced, we need to handle them
            if (map.containsKey("$ref")) {
                // Check if this is an external file reference that should be fully replaced
                String ref = (String) map.get("$ref");
                boolean isExternalFileRef = ref != null && !ref.startsWith("#") && 
                                           (ref.endsWith(".yaml") || ref.endsWith(".yml") || ref.endsWith(".json"));
                
                // If it's an external file reference, always resolve it (even if map.size() > 1)
                // because external file refs should replace the entire schema
                if (isExternalFileRef || map.size() == 1) {
                    // Create a unique key for this reference (file + path)
                    String refKey = createRefKey(ref, baseDir, currentFileKey);

                // Check for circular reference
                if (resolvingRefs.contains(refKey)) {
                    // Circular reference detected - leave the $ref as-is to break the cycle
                    return;
                }

                // Add to resolving set
                resolvingRefs.add(refKey);

                try {
                    Object resolved = resolveReference(ref, baseDir, currentFileKey, loadedFiles, resolvingRefs, visitedObjects);

                    // Replace the map with resolved content
                    if (resolved instanceof Map) {
                        Map<String, Object> resolvedMap = Util.asStringObjectMap(resolved);
                        if (resolvedMap == null) {
                            throw new OASSDKException("Resolved reference is null: " + ref);
                        }
                        // Create a copy to avoid modifying the original
                        Map<String, Object> resolvedCopy = new HashMap<>(resolvedMap);
                        
                        // IMPORTANT: For external file references, the resolved content is the schema definition itself
                        // (e.g., User.yaml contains type: object, properties: {...} directly)
                        // We need to replace the entire map content with this schema definition
                        
                        // Mark the original map as visited before replacing content
                        visitedObjects.add(map);
                        // Replace the map content completely - this ensures $ref is removed
                        map.clear();
                        map.putAll(resolvedCopy);
                        // IMPORTANT: Remove $ref key if it still exists after resolution
                        // This can happen if the resolved content itself contains a $ref
                        // but for external file references, the resolved content should be the schema itself
                        map.remove("$ref");
                        
                        
                        // Recursively resolve any references in the resolved content
                        // Note: We keep refKey in resolvingRefs to detect circular references in nested content
                        resolveReferencesRecursive(map, baseDir, currentFileKey, loadedFiles, resolvingRefs, visitedObjects);
                    } else {
                        // If resolved is not a map, this shouldn't happen for parameters
                        throw new OASSDKException("Resolved reference is not a Map: " + ref);
                    }
                    } finally {
                        // Remove from resolving set after processing
                        resolvingRefs.remove(refKey);
                    }
                    return;
                }
            }

            // Mark this map as visited before processing its entries
            visitedObjects.add(map);

            // Recursively process all values in the map, but skip if this map was already processed
            // to avoid infinite loops with circular references
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                // Skip processing $ref entries that are already being resolved (circular reference)
                if (value instanceof Map) {
                    Map<String, Object> valueMap = Util.asStringObjectMap(value);
                    if (valueMap != null && valueMap.containsKey("$ref") && valueMap.size() == 1) {
                        String nestedRef = (String) valueMap.get("$ref");
                        String nestedRefKey = createRefKey(nestedRef, baseDir, currentFileKey);
                        if (resolvingRefs.contains(nestedRefKey)) {
                            // Skip this nested reference as it's part of a circular reference
                            continue;
                        }
                    }
                }
                resolveReferencesRecursive(value, baseDir, currentFileKey, loadedFiles, resolvingRefs, visitedObjects);
            }
        } else if (obj instanceof List) {
            List<Object> list = Util.asObjectList(obj);
            // Mark list as visited
            visitedObjects.add(list);
            for (Object o : list) {
                resolveReferencesRecursive(o, baseDir, currentFileKey, loadedFiles, resolvingRefs, visitedObjects);
            }
        }
    }

    /**
     * Create a unique key for a reference to track circular references
     */
    private String createRefKey(String ref, Path baseDir, String currentFileKey) {
        if (ref == null || ref.isEmpty()) {
            return currentFileKey + "#";
        }

        if (ref.contains("#")) {
            String[] parts = ref.split("#", 2);
            String filePath = parts[0];
            String jsonPath = parts.length > 1 ? parts[1] : "";

            if (!filePath.isEmpty()) {
                // External file reference
                Path refPath;
                if (baseDir != null) {
                    refPath = baseDir.resolve(filePath).normalize();
                } else {
                    refPath = Paths.get(filePath).normalize();
                }
                return refPath + "#" + jsonPath;
            } else {
                // Internal reference
                return currentFileKey + "#" + jsonPath;
            }
        } else {
            // Internal reference without file part
            return currentFileKey + "#" + ref;
        }
    }

    /**
     * Resolve a single $ref reference
     */
    private Object resolveReference(String ref, Path baseDir, String currentFileKey, Map<String, Map<String, Object>> loadedFiles, Set<String> resolvingRefs, Set<Object> visitedObjects) throws OASSDKException {
        if (ref == null || ref.isEmpty()) {
            throw new OASSDKException("Empty $ref reference");
        }

        // Check if it's an external file reference
        if (ref.contains("#")) {
            String[] parts = ref.split("#", 2);
            String filePath = parts[0];
            String jsonPath = parts.length > 1 ? parts[1] : "";

            if (!filePath.isEmpty()) {
                // External file reference - use PathResolver for secure resolution
                Path refPath = pathResolver.resolveReference(filePath, baseDir);

                // Load the external file if not already loaded
                String fileKey = refPath.toString();
                Map<String, Object> externalSpec = loadedFiles.get(fileKey);
                if (externalSpec == null) {
                    // Parse the external file
                    externalSpec = parse(refPath.toString());
                    // Create a copy to avoid modifying the original
                    externalSpec = new HashMap<>(externalSpec);
                    loadedFiles.put(fileKey, externalSpec);
                    // Resolve references in the external file recursively
                    // Share the same resolving set to detect cross-file circular references
                    resolveReferencesRecursive(externalSpec, refPath.getParent(), fileKey, loadedFiles, resolvingRefs, visitedObjects);
                }

                // Resolve the JSON path in the external file
                if (jsonPath.isEmpty() || jsonPath.equals("/")) {
                    return externalSpec;
                } else {
                    return resolveJsonPath(externalSpec, jsonPath);
                }
            } else {
                // Internal reference (same file) - use currentFileKey to get the correct spec
                Map<String, Object> currentSpec = loadedFiles.get(currentFileKey);
                if (currentSpec == null) {
                    throw new OASSDKException("Current file not found in loaded files: " + currentFileKey);
                }
                String path = jsonPath.startsWith("/") ? jsonPath.substring(1) : jsonPath;
                return resolveJsonPath(currentSpec, path);
            }
        } else {
            // Could be an external file reference without #, or an internal JSON path reference
            // Check if it looks like a file path (contains .yaml or .yml or .json)
            if (ref.endsWith(".yaml") || ref.endsWith(".yml") || ref.endsWith(".json")) {
                // External file reference without JSON path - return the whole file
                // Use PathResolver for secure resolution
                Path refPath = pathResolver.resolveReference(ref, baseDir);

                // Load the external file if not already loaded
                String fileKey = refPath.toString();
                Map<String, Object> externalSpec = loadedFiles.get(fileKey);
                if (externalSpec == null) {
                    externalSpec = parse(refPath.toString());
                    externalSpec = new HashMap<>(externalSpec);
                    loadedFiles.put(fileKey, externalSpec);
                    resolveReferencesRecursive(externalSpec, refPath.getParent(), fileKey, loadedFiles, resolvingRefs, visitedObjects);
                }
                
                // IMPORTANT: If the external file is a schema definition (not a full OpenAPI spec),
                // it will have type/properties directly. Return it as-is.
                // If it's a full OpenAPI spec, we'd need to extract components/schemas, but
                // for schema definition files, the content is the schema itself.
                return externalSpec;
            } else {
                // Internal reference without file part - use currentFileKey
                Map<String, Object> currentSpec = loadedFiles.get(currentFileKey);
                if (currentSpec == null) {
                    throw new OASSDKException("Current file not found in loaded files: " + currentFileKey);
                }
                String jsonPath = ref.startsWith("/") ? ref.substring(1) : ref;
                return resolveJsonPath(currentSpec, jsonPath);
            }
        }
    }

    /**
     * Merge external schemas into the main spec's components/schemas section
     * This allows generators to find and generate models from external files
     */
    private void mergeExternalSchemasIntoMainSpec(Map<String, Object> externalSpec, Map<String, Object> mainSpec) {
        if (externalSpec == null || mainSpec == null) {
            return;
        }

        // Check if externalSpec is a schema definition file (not a full OpenAPI spec)
        // Schema definition files have type/properties directly, not wrapped in components/schemas
        if (externalSpec.containsKey("type") || externalSpec.containsKey("properties")) {
            // This is a schema definition file - merge it as a schema
            // Use the file name as the schema name, or try to find a title/name
            String schemaName = null;
            if (externalSpec.containsKey("title")) {
                schemaName = (String) externalSpec.get("title");
            } else {
                // Try to extract from file path if available
                // For now, we'll skip this as we don't have the file path here
                // The resolved $ref should already have the schema content
                return;
            }
            
            // Ensure main spec has components/schemas
            Map<String, Object> mainComponents = Util.asStringObjectMap(mainSpec.get("components"));
            if (mainComponents == null) {
                mainComponents = new HashMap<>();
                mainSpec.put("components", mainComponents);
            }
            
            Map<String, Object> mainSchemas = Util.asStringObjectMap(mainComponents.get("schemas"));
            if (mainSchemas == null) {
                mainSchemas = new HashMap<>();
                mainComponents.put("schemas", mainSchemas);
            }
            
            // Merge the schema definition if not already present
            if (schemaName != null && !mainSchemas.containsKey(schemaName)) {
                mainSchemas.put(schemaName, new HashMap<>(externalSpec));
            }
            return;
        }

        // Get components/schemas from external file
        Map<String, Object> externalComponents = Util.asStringObjectMap(externalSpec.get("components"));
        if (externalComponents == null) {
            return;
        }

        Map<String, Object> externalSchemas = Util.asStringObjectMap(externalComponents.get("schemas"));
        if (externalSchemas == null || externalSchemas.isEmpty()) {
            return;
        }

        // Ensure main spec has components/schemas
        Map<String, Object> mainComponents = Util.asStringObjectMap(mainSpec.get("components"));
        if (mainComponents == null) {
            mainComponents = new HashMap<>();
            mainSpec.put("components", mainComponents);
        }

        Map<String, Object> mainSchemas = Util.asStringObjectMap(mainComponents.get("schemas"));
        if (mainSchemas == null) {
            mainSchemas = new HashMap<>();
            mainComponents.put("schemas", mainSchemas);
        }

        // Merge external schemas into main schemas (only if not already present)
        for (Map.Entry<String, Object> schemaEntry : externalSchemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            if (!mainSchemas.containsKey(schemaName)) {
                // Create a copy to avoid modifying the original
                Object schemaValue = schemaEntry.getValue();
                if (schemaValue instanceof Map) {
                    Map<String, Object> schemaMap = Util.asStringObjectMap(schemaValue);
                    if (schemaMap != null) {
                        mainSchemas.put(schemaName, new HashMap<>(schemaMap));
                    } else {
                        mainSchemas.put(schemaName, schemaValue);
                    }
                } else {
                    mainSchemas.put(schemaName, schemaValue);
                }
            }
        }
    }

    /**
     * Resolve a JSON path (e.g., /components/parameters/SomeParameter)
     */
    private Object resolveJsonPath(Map<String, Object> root, String jsonPath) throws OASSDKException {
        if (jsonPath == null || jsonPath.isEmpty() || jsonPath.equals("/")) {
            return root;
        }

        // Remove leading slash
        String path = jsonPath.startsWith("/") ? jsonPath.substring(1) : jsonPath;
        String[] parts = path.split("/");

        Object current = root;
        for (String part : parts) {
            if (current instanceof Map) {
                Map<String, Object> currentMap = Util.asStringObjectMap(current);
                if (currentMap == null) {
                    throw new OASSDKException("Invalid reference path: " + jsonPath);
                }
                current = currentMap.get(part);
                if (current == null) {
                    throw new OASSDKException("Reference not found: " + jsonPath);
                }
            } else {
                throw new OASSDKException("Invalid reference path: " + jsonPath);
            }
        }

        return current;
    }
}
