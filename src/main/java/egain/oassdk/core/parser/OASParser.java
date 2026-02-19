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
     * Sanitize file path and normalize to Unix style (forward slashes only).
     * All path processing in the parser uses Unix style for cross-platform consistency.
     */
    private String sanitizeFilePath(String filePath) {
        return PathUtils.toUnixPath(filePath != null ? filePath : "");
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
     * Normalize a path to a canonical string key (Unix style: forward slashes, absolute).
     * Ensures the same file gets the same key on all platforms (e.g. Windows vs Mac),
     * so loadedFiles lookups and merge logic work correctly.
     */
    private static String normalizePathKey(Path path) {
        return PathUtils.toUnixPath(path);
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

        // Load the base file into the cache (use normalized key for cross-platform consistency)
        Path basePath = Paths.get(sanitizeFilePath(baseFilePath)).normalize().toAbsolutePath();
        String baseFileKey = normalizePathKey(basePath);
        loadedFiles.put(baseFileKey, resolvedSpec);

        // Track references currently being resolved to detect circular references
        Set<String> resolvingRefs = new HashSet<>();
        // Track visited objects to prevent infinite recursion
        // Use IdentityHashMap-based set to avoid hashCode() issues with circular references
        Set<Object> visitedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        // Track which fragment(s) of each external file were referenced (e.g. /components/parameters/accept).
        // Key = file key (same as loadedFiles), value = set of JSON paths. Sentinel "/" = whole file referenced.
        Map<String, Set<String>> referencedFragmentsByFile = new HashMap<>();

        // Resolve all references recursively
        resolveReferencesRecursive(resolvedSpec, basePath.getParent(), baseFileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);

        // After all references are resolved, merge external schemas into the main spec
        // This allows generators to find and generate models from external files
        Map<String, Object> mainSpec = loadedFiles.get(baseFileKey);
        for (Map.Entry<String, Map<String, Object>> entry : loadedFiles.entrySet()) {
            if (!entry.getKey().equals(baseFileKey) && entry.getValue() != mainSpec) {
                Set<String> referencedFragments = referencedFragmentsByFile.get(entry.getKey());
                mergeExternalSchemasIntoMainSpec(entry.getKey(), entry.getValue(), mainSpec, referencedFragments);
            }
        }

        return resolvedSpec;
    }

    /** Sentinel in referencedFragmentsByFile meaning the entire file was referenced (e.g. ref without fragment). */
    private static final String REF_FRAGMENT_WHOLE_FILE = "/";

    /**
     * Recursively resolve $ref references in the specification
     * @param baseFileKey key of the main spec in loadedFiles (for registering resolved external schemas)
     */
    private void resolveReferencesRecursive(Object obj, Path baseDir, String currentFileKey, String baseFileKey,
                                            Map<String, Map<String, Object>> loadedFiles, Set<String> resolvingRefs, Set<Object> visitedObjects,
                                            Map<String, Set<String>> referencedFragmentsByFile) throws OASSDKException {
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
                    Object resolved = resolveReference(ref, baseDir, currentFileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);

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
                        // Preserve original ref path for internal refs so generators (e.g. XSD) can emit imports/type refs
                        if (ref != null && ref.startsWith("#/components/schemas/")) {
                            map.put("x-resolved-ref", ref);
                        } else if (isExternalFileRef && ref != null) {
                            // For external file refs, set x-resolved-ref so generators emit type refs and imports.
                            // Prefer schema name from fragment (e.g. common.yaml#/components/schemas/L10NString -> L10NString).
                            String schemaName = null;
                            if (ref.contains("#/components/schemas/")) {
                                String fragment = ref.substring(ref.indexOf("#/components/schemas/") + "#/components/schemas/".length());
                                schemaName = fragment.contains("/") ? fragment.substring(fragment.lastIndexOf("/") + 1) : fragment;
                            }
                            if (schemaName == null || schemaName.isEmpty()) {
                                schemaName = deriveSchemaNameFromRef(ref);
                            }
                            if (schemaName != null && !schemaName.isEmpty()) {
                                map.put("x-resolved-ref", "#/components/schemas/" + schemaName);
                            }
                        } else if (ref != null && ref.contains("#/components/schemas/")) {
                            // External ref with fragment (e.g. common.yaml#/components/schemas/L10NString) that did not
                            // match isExternalFileRef (ref does not end with .yaml). Set x-resolved-ref so generators
                            // can emit the correct type (e.g. for property-level allOf single-ref).
                            String schemaName = null;
                            String fragment = ref.substring(ref.indexOf("#/components/schemas/") + "#/components/schemas/".length());
                            schemaName = fragment.contains("/") ? fragment.substring(fragment.lastIndexOf("/") + 1) : fragment;
                            if (schemaName == null || schemaName.isEmpty()) {
                                schemaName = deriveSchemaNameFromRef(ref);
                            }
                            if (schemaName != null && !schemaName.isEmpty()) {
                                map.put("x-resolved-ref", "#/components/schemas/" + schemaName);
                            }
                        }
                        
                        // When we inline an external schema file, register it in the main spec's components/schemas
                        // so that the full schema (e.g. User from User.yaml) is available even if the main spec
                        // had a wrong or missing entry for that name (e.g. User pointing at Users.yaml).
                        if (isExternalFileRef && ref != null) {
                            addResolvedExternalSchemaToMainSpec(ref, resolvedCopy, loadedFiles, baseFileKey);
                        }

                        // Recursively resolve any references in the resolved content
                        // Note: We keep refKey in resolvingRefs to detect circular references in nested content
                        resolveReferencesRecursive(map, baseDir, currentFileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);
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
                resolveReferencesRecursive(value, baseDir, currentFileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);
            }
        } else if (obj instanceof List) {
            List<Object> list = Util.asObjectList(obj);
            // Mark list as visited
            visitedObjects.add(list);
            for (Object o : list) {
                resolveReferencesRecursive(o, baseDir, currentFileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);
            }
        }
    }

    /**
     * When an external file ref (e.g. User.yaml) is resolved and inlined, register that schema
     * in the main spec's components/schemas under the derived name. This ensures the full schema
     * is available to generators even when the only reference was nested (e.g. Users.user.items).
     */
    private void addResolvedExternalSchemaToMainSpec(String ref, Map<String, Object> resolvedCopy,
                                                     Map<String, Map<String, Object>> loadedFiles, String baseFileKey) {
        if (ref == null || resolvedCopy == null || loadedFiles == null || baseFileKey == null) {
            return;
        }
        // Only register if the resolved content looks like a schema definition (type or properties at top level)
        if (!resolvedCopy.containsKey("type") && !resolvedCopy.containsKey("properties")) {
            return;
        }
        String schemaName = deriveSchemaNameFromRef(ref);
        if (schemaName == null || schemaName.isEmpty()) {
            return;
        }
        Map<String, Object> mainSpec = loadedFiles.get(baseFileKey);
        if (mainSpec == null) {
            return;
        }
        // Use the actual map from the spec so we modify in place (Util.asStringObjectMap would copy)
        @SuppressWarnings("unchecked")
        Map<String, Object> mainComponents = (Map<String, Object>) mainSpec.get("components");
        if (mainComponents == null) {
            mainComponents = new HashMap<>();
            mainSpec.put("components", mainComponents);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mainSchemas = (Map<String, Object>) mainComponents.get("schemas");
        if (mainSchemas == null) {
            mainSchemas = new HashMap<>();
            mainComponents.put("schemas", mainSchemas);
        }
        mainSchemas.put(schemaName, new HashMap<>(resolvedCopy));
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
                // External file reference (use normalized key for cross-platform consistency)
                Path refPath;
                if (baseDir != null) {
                    refPath = baseDir.resolve(filePath).normalize();
                } else {
                    refPath = Paths.get(filePath).normalize();
                }
                String pathKey = refPath.isAbsolute() ? normalizePathKey(refPath) : refPath.toString().replace('\\', '/');
                return pathKey + "#" + jsonPath;
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
     * Derive schema name from a $ref or file path (e.g. "models/v4/Link.yaml" -> "Link", "DepartmentView.yaml" -> "DepartmentView").
     * Used so that x-resolved-ref and merged schema names match for external file refs.
     */
    private String deriveSchemaNameFromRef(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        String path = ref.contains("#") ? ref.split("#", 2)[0] : ref;
        path = path.replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (segment.isEmpty()) {
            return null;
        }
        if (segment.endsWith(".yaml")) {
            return segment.substring(0, segment.length() - 5);
        }
        if (segment.endsWith(".yml")) {
            return segment.substring(0, segment.length() - 4);
        }
        if (segment.endsWith(".json")) {
            return segment.substring(0, segment.length() - 5);
        }
        return segment;
    }

    /** Max depth for collectSchemaRefPaths to avoid stack overflow on deep or cyclic structures. */
    private static final int COLLECT_SCHEMA_REFS_MAX_DEPTH = 100;

    /**
     * Collect all JSON paths of $ref that point to #/components/schemas/... from the given object (transitively).
     * Used so that when we reference a fragment (e.g. requestBodies/X), we also record schema refs used inside it
     * and merge those schemas into the main spec before resolution continues.
     *
     * @param obj      root object to traverse (Map, List, or other)
     * @param outPaths set to add paths to (e.g. "/components/schemas/UserEditRequest"); paths use leading slash
     */
    private void collectSchemaRefPaths(Object obj, Set<String> outPaths) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectSchemaRefPaths(obj, outPaths, visited, 0);
    }

    private void collectSchemaRefPaths(Object obj, Set<String> outPaths, Set<Object> visited, int depth) {
        if (obj == null || depth > COLLECT_SCHEMA_REFS_MAX_DEPTH) {
            return;
        }
        if (visited.contains(obj)) {
            return;
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            visited.add(obj);
            if (map.containsKey("$ref")) {
                String ref = (String) map.get("$ref");
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    String path = ref.startsWith("#/") ? "/" + ref.substring(2) : (ref.startsWith("/") ? ref : "/" + ref);
                    outPaths.add(path);
                }
            }
            for (Object value : map.values()) {
                collectSchemaRefPaths(value, outPaths, visited, depth + 1);
            }
            return;
        }
        if (obj instanceof List) {
            visited.add(obj);
            for (Object item : (List<?>) obj) {
                collectSchemaRefPaths(item, outPaths, visited, depth + 1);
            }
        }
    }

    /**
     * Resolve a single $ref reference
     * @param baseFileKey key of the main spec in loadedFiles (for recursive resolution)
     */
    private Object resolveReference(String ref, Path baseDir, String currentFileKey, String baseFileKey, Map<String, Map<String, Object>> loadedFiles, Set<String> resolvingRefs, Set<Object> visitedObjects, Map<String, Set<String>> referencedFragmentsByFile) throws OASSDKException {
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

                // Load the external file if not already loaded (use normalized key for cross-platform consistency)
                String fileKey = normalizePathKey(refPath);
                Map<String, Object> externalSpec = loadedFiles.get(fileKey);
                if (externalSpec == null) {
                    // Parse the external file (always use Unix-style path)
                    externalSpec = parse(PathUtils.toUnixPath(refPath));
                    // Create a copy to avoid modifying the original
                    externalSpec = new HashMap<>(externalSpec);
                    loadedFiles.put(fileKey, externalSpec);
                    // Resolve references in the external file recursively
                    // Share the same resolving set to detect cross-file circular references
                    resolveReferencesRecursive(externalSpec, refPath.getParent(), fileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);
                }

                // Record which fragment of this file was referenced (for merge-only-referenced-fragments)
                String normalizedPath;
                if (jsonPath != null && !jsonPath.isEmpty() && !"/".equals(jsonPath)) {
                    normalizedPath = jsonPath.startsWith("/") ? jsonPath : "/" + jsonPath;
                } else {
                    normalizedPath = REF_FRAGMENT_WHOLE_FILE;
                }
                referencedFragmentsByFile.computeIfAbsent(fileKey, k -> new HashSet<>()).add(normalizedPath);

                // Resolve the JSON path in the external file
                if (jsonPath == null || jsonPath.isEmpty() || "/".equals(jsonPath)) {
                    return externalSpec;
                } else {
                    Object fragment = resolveJsonPath(externalSpec, jsonPath);
                    // Record transitive schema refs from this fragment so they get merged (e.g. requestBody -> schema)
                    Set<String> schemaRefPaths = new HashSet<>();
                    collectSchemaRefPaths(fragment, schemaRefPaths);
                    referencedFragmentsByFile.computeIfAbsent(fileKey, k -> new HashSet<>()).addAll(schemaRefPaths);
                    // Merge this file into main spec now so internal refs (e.g. #/components/schemas/UserEditRequest) resolve
                    Map<String, Object> mainSpec = loadedFiles.get(baseFileKey);
                    if (mainSpec != null) {
                        mergeExternalSchemasIntoMainSpec(fileKey, externalSpec, mainSpec, referencedFragmentsByFile.get(fileKey));
                    }
                    return fragment;
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

                // Load the external file if not already loaded (use normalized key for cross-platform consistency)
                String fileKey = normalizePathKey(refPath);
                Map<String, Object> externalSpec = loadedFiles.get(fileKey);
                if (externalSpec == null) {
                    externalSpec = parse(PathUtils.toUnixPath(refPath));
                    externalSpec = new HashMap<>(externalSpec);
                    loadedFiles.put(fileKey, externalSpec);
                    resolveReferencesRecursive(externalSpec, refPath.getParent(), fileKey, baseFileKey, loadedFiles, resolvingRefs, visitedObjects, referencedFragmentsByFile);
                }

                // Whole-file ref: record sentinel so merge keeps full-file behavior
                referencedFragmentsByFile.computeIfAbsent(fileKey, k -> new HashSet<>()).add(REF_FRAGMENT_WHOLE_FILE);
                
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
     * This allows generators to find and generate models from external files.
     * When referencedFragments is non-null and does not contain the whole-file sentinel,
     * only components whose paths were referenced are merged (e.g. only schema X if
     * "/components/schemas/X" was referenced).
     *
     * @param fileKey the file path key (e.g. from loadedFiles) used to derive schema name when title is absent
     * @param referencedFragments set of JSON paths that were referenced in this file (e.g. /components/parameters/accept).
     *                            Null or empty = merge all (backward compatible). Contains REF_FRAGMENT_WHOLE_FILE = merge all.
     */
    private void mergeExternalSchemasIntoMainSpec(String fileKey, Map<String, Object> externalSpec, Map<String, Object> mainSpec, Set<String> referencedFragments) {
        if (externalSpec == null || mainSpec == null) {
            return;
        }

        boolean mergeAll = referencedFragments == null || referencedFragments.isEmpty() || referencedFragments.contains(REF_FRAGMENT_WHOLE_FILE);
        // When file was referenced via a non-schema fragment (e.g. parameters, requestBodies, responses), merge all schemas
        // so that internal refs like #/components/schemas/UserView in the main spec can resolve (e.g. from common.yaml)
        if (!mergeAll && referencedFragments != null) {
            for (String p : referencedFragments) {
                if (p.startsWith("/components/parameters/") || p.startsWith("/components/requestBodies/") || p.startsWith("/components/responses/")) {
                    mergeAll = true;
                    break;
                }
            }
        }

        // Check if externalSpec is a schema definition file (not a full OpenAPI spec)
        // Schema definition files have type/properties directly, not wrapped in components/schemas
        if (externalSpec.containsKey("type") || externalSpec.containsKey("properties")) {
            // Only merge whole-file schema definition if the file was referenced as a whole
            if (!mergeAll) {
                return;
            }
            // This is a schema definition file - merge it as a schema.
            // Use stable name from file path (e.g. User.yaml -> "User", Users.yaml -> "Users")
            // so that the correct schema always wins (full User from User.yaml, not inlined placeholder).
            String schemaName = null;
            if (fileKey != null && !fileKey.isEmpty()) {
                schemaName = deriveSchemaNameFromRef(fileKey);
            }
            if (schemaName == null || schemaName.isEmpty()) {
                if (externalSpec.containsKey("title")) {
                    schemaName = (String) externalSpec.get("title");
                }
            }
            if (schemaName == null || schemaName.isEmpty()) {
                return;
            }
            
            // Use the actual map from the spec so we modify in place (Util.asStringObjectMap would copy)
            @SuppressWarnings("unchecked")
            Map<String, Object> mainComponents = (Map<String, Object>) mainSpec.get("components");
            if (mainComponents == null) {
                mainComponents = new HashMap<>();
                mainSpec.put("components", mainComponents);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mainSchemas = (Map<String, Object>) mainComponents.get("schemas");
            if (mainSchemas == null) {
                mainSchemas = new HashMap<>();
                mainComponents.put("schemas", mainSchemas);
            }
            
            // Overwrite with external schema definition so full schema from file wins
            // (e.g. User.yaml provides full User with many properties, not an inlined placeholder)
            mainSchemas.put(schemaName, new HashMap<>(externalSpec));
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

        // Use the actual map from the spec so we modify in place
        @SuppressWarnings("unchecked")
        Map<String, Object> mainComponents = (Map<String, Object>) mainSpec.get("components");
        if (mainComponents == null) {
            mainComponents = new HashMap<>();
            mainSpec.put("components", mainComponents);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mainSchemas = (Map<String, Object>) mainComponents.get("schemas");
        if (mainSchemas == null) {
            mainSchemas = new HashMap<>();
            mainComponents.put("schemas", mainSchemas);
        }

        // Derive schema name from this file (e.g. User.yaml -> "User") so we overwrite that name when
        // the external file is the canonical source (e.g. full User from User.yaml wins over a wrong main entry).
        String fileDerivedSchemaName = (fileKey != null && !fileKey.isEmpty()) ? deriveSchemaNameFromRef(fileKey) : null;

        // Merge external schemas into main schemas. When mergeAll is false, only merge schemas whose path was referenced.
        for (Map.Entry<String, Object> schemaEntry : externalSchemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            if (!mergeAll && (referencedFragments == null || !isSchemaPathReferenced(referencedFragments, schemaName))) {
                continue;
            }
            boolean overwrite = fileDerivedSchemaName != null && fileDerivedSchemaName.equals(schemaName);
            if (overwrite || !mainSchemas.containsKey(schemaName)) {
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
     * Check if a schema name was referenced (accepts both /components/schemas/Name and //components/schemas/Name).
     */
    private static boolean isSchemaPathReferenced(Set<String> referencedFragments, String schemaName) {
        if (referencedFragments == null) return false;
        String path = "/components/schemas/" + schemaName;
        return referencedFragments.contains(path) || referencedFragments.contains("/" + path);
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
