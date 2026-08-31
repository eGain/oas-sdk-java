package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.util.*;
import java.util.logging.Logger;

/**
 * Collects and catalogs schemas referenced across an OpenAPI spec.
 * Populates the shared inlined-schemas map ({@link JerseyGenerationContext#getInlinedSchemas()}) and provides methods
 * for discovering which component schemas are actually referenced.
 */
public final class JerseySchemaCollector {

    private static final Logger logger = egain.oassdk.core.logging.LoggerConfig.getLogger(JerseySchemaCollector.class);

    /** Max recursion depth for collectInlineSchemasFromSchemaProperties to prevent StackOverflow on very deep specs. */
    private static final int MAX_INLINE_SCHEMA_COLLECTION_DEPTH = 100;

    private final JerseyGenerationContext ctx;

    public JerseySchemaCollector(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    // ---------------------------------------------------------------------------
    //  Inline schema collection
    // ---------------------------------------------------------------------------

    /**
     * Collect in-lined schemas from response bodies and assign names to them.
     */
    public void collectInlinedSchemas(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return;
        }

        int schemaCounter = 1;

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    // Collect from responses
                    if (operation.containsKey("responses")) {
                        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
                        if (responses != null) {
                            for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                                Object responseObj = responseEntry.getValue();

                                Map<String, Object> response = Util.asStringObjectMap(responseObj);
                                if (response != null && response.containsKey("content")) {
                                    Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                    if (content != null) {
                                        for (Object mediaTypeObj : content.values()) {
                                            Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                            if (mediaType != null) {
                                                Object schemaObj = mediaType.get("schema");

                                                Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
                                                if (schema != null) {
                                                    // Check if it's an in-lined schema (not a $ref)
                                                    if (!schema.containsKey("$ref")) {
                                                        // Check if it's an object type with properties
                                                        String type = (String) schema.get("type");
                                                        if ("object".equals(type) && schema.containsKey("properties")) {
                                                            // Generate a name based on operation ID and response code
                                                            String operationId = (String) operation.get("operationId");
                                                            String modelName = generateInlinedSchemaName(operationId, schema, schemaCounter++);

                                                            // Store the mapping
                                                            ctx.getInlinedSchemas().put(schemaObj, modelName);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Collect inline object schemas from schema properties.
     * When referencedSchemaNames is non-null, only collects from those top-level schemas (avoids phantom InlineObject classes).
     */
    void collectInlinedSchemasFromProperties(Map<String, Object> schemas, Map<String, Object> spec, Set<String> referencedSchemaNames) {
        if (schemas == null) return;

        // Track visited schemas to prevent infinite recursion (using IdentityHashSet for identity-based comparison)
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        // Create a set of top-level schema objects for quick lookup
        Set<Object> topLevelSchemaObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object schemaObj : schemas.values()) {
            if (schemaObj instanceof Map) {
                topLevelSchemaObjects.add(schemaObj);
            }
        }

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            // When referenced set is provided, only collect inlines from referenced top-level schemas
            if (referencedSchemaNames != null && !referencedSchemaNames.contains(schemaEntry.getKey())) {
                continue;
            }
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Check properties in this schema (but don't register the schema itself as inline)
            collectInlineSchemasFromSchemaProperties(schema, null, spec, visited, topLevelSchemaObjects, false, 0);
        }
    }

    /**
     * Recursively collect inline object schemas from a schema's properties.
     */
    void collectInlineSchemasFromSchemaProperties(Map<String, Object> schema, String parentPropertyName, Map<String, Object> spec, Set<Object> visited, Set<Object> topLevelSchemaObjects) {
        collectInlineSchemasFromSchemaProperties(schema, parentPropertyName, spec, visited, topLevelSchemaObjects, false, 0);
    }

    /**
     * Recursively collect inline object schemas from a schema's properties.
     *
     * @param isInCompositionContext true if this schema is being processed as part of an allOf/oneOf/anyOf composition
     * @param depth current recursion depth (0 at top level)
     */
    void collectInlineSchemasFromSchemaProperties(Map<String, Object> schema, String parentPropertyName, Map<String, Object> spec, Set<Object> visited, Set<Object> topLevelSchemaObjects, boolean isInCompositionContext, int depth) {
        if (schema == null) return;
        if (depth > MAX_INLINE_SCHEMA_COLLECTION_DEPTH) {
            logger.fine("Recursion depth limit reached in collectInlineSchemasFromSchemaProperties: " + depth + " (no impact on SDK generation for already-visited schemas)");
            return;
        }

        // Prevent infinite recursion - check if we've already visited this schema
        if (visited.contains(schema)) {
            return;
        }
        visited.add(schema);

        // Handle $ref - resolve and check
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                        if (referencedSchema != null) {
                            // Don't recurse into top-level schemas - they're already being processed
                            if (!topLevelSchemaObjects.contains(referencedSchema)) {
                                if (visited.add(referencedSchema)) {
                                    collectInlineSchemasFromSchemaProperties(referencedSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, isInCompositionContext, depth + 1);
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        // Check allOf, oneOf, anyOf - process sub-schemas but mark them as being in composition context
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas != null) {
                for (Map<String, Object> subSchema : allOfSchemas) {
                    if (subSchema != null) {
                        collectInlineSchemasFromSchemaProperties(subSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, true, depth + 1);
                    }
                }
            }
        }

        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemasList = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            if (schemasList != null) {
                for (Map<String, Object> subSchema : schemasList) {
                    if (subSchema != null) {
                        collectInlineSchemasFromSchemaProperties(subSchema, parentPropertyName, spec, visited, topLevelSchemaObjects, true, depth + 1);
                    }
                }
            }
        }

        // Check if this is an inline object schema (not a top-level schema)
        // Skip if it's part of a composition (allOf/oneOf/anyOf) - those should be merged, not separate models
        String type = (String) schema.get("type");
        if ("object".equals(type) && schema.containsKey("properties") && !schema.containsKey("$ref") && !isInCompositionContext) {
            if (!topLevelSchemaObjects.contains(schema) && !ctx.getInlinedSchemas().containsKey(schema) && schema.containsKey("x-resolved-ref")) {
                String modelName = generateInlineSchemaNameFromProperty(schema, parentPropertyName);
                ctx.getInlinedSchemas().put(schema, modelName);
            }
        }

        // Recursively check properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            if (properties != null) {
                for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
                    String propertyName = propertyEntry.getKey();
                    Object propertyValue = propertyEntry.getValue();

                    Map<String, Object> propertySchema = Util.asStringObjectMap(propertyValue);
                    if (propertySchema != null) {
                        collectInlineSchemasFromSchemaProperties(propertySchema, propertyName, spec, visited, topLevelSchemaObjects, isInCompositionContext, depth + 1);
                    }
                }
            }
        }

        // Check array items
        if ("array".equals(type) && schema.containsKey("items")) {
            Object itemsObj = schema.get("items");
            Map<String, Object> itemsMap = Util.asStringObjectMap(itemsObj);
            if (itemsMap != null) {
                collectInlineSchemasFromSchemaProperties(itemsMap, null, spec, visited, topLevelSchemaObjects, isInCompositionContext, depth + 1);
            }
        }
    }

    // ---------------------------------------------------------------------------
    //  Inline schema naming
    // ---------------------------------------------------------------------------

    /**
     * Generate a name for an inline schema from a property.
     * Uses schema name (from resolved ref) or property name; optional title is not used.
     */
    String generateInlineSchemaNameFromProperty(Map<String, Object> schema, String propertyName) {
        // Use schema name from resolved ref when present
        if (schema.containsKey("x-resolved-ref")) {
            String resolvedRef = (String) schema.get("x-resolved-ref");
            if (resolvedRef != null && !resolvedRef.isEmpty()) {
                String[] parts = resolvedRef.split("/");
                String lastPart = parts[parts.length - 1];
                return JerseyNamingUtils.toJavaClassName(lastPart);
            }
        }

        // Use the property name (key in parent schema)
        if (propertyName != null && !propertyName.isEmpty()) {
            return JerseyNamingUtils.toJavaClassName(JerseyNamingUtils.capitalize(propertyName));
        }

        // Fallback to generic name
        return "InlineObject" + ctx.getInlinedSchemas().size();
    }

    /**
     * Generate a name for an in-lined schema.
     * Uses schema name (from resolved ref) first, then property-based or operation-based fallbacks; optional title is not used.
     */
    String generateInlinedSchemaName(String operationId, Map<String, Object> schema, int counter) {

        // Use schema name from resolved ref (component schema name) when present
        if (schema.containsKey("x-resolved-ref")) {
            String resolvedRef = (String) schema.get("x-resolved-ref");
            if (resolvedRef != null && !resolvedRef.isEmpty()) {
                // Extract the last part of the resolved ref as the model name
                String[] parts = resolvedRef.split("/");
                String lastPart = parts[parts.length - 1];
                return JerseyNamingUtils.toJavaClassName(lastPart);
            }
        }

        // Try to generate a meaningful name from the schema properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));

            // If we have a single property, use it
            if (properties!=null && properties.size() == 1) {
                String propName = properties.keySet().iterator().next();
                return JerseyNamingUtils.toJavaClassName(JerseyNamingUtils.capitalize(propName));
            }
        }

        // Fallback: use operation ID if available
        if (operationId != null && !operationId.isEmpty()) {
            return JerseyNamingUtils.toJavaClassName(JerseyNamingUtils.capitalize(operationId) + "Response");
        }

        // Last resort: generic name
        return "InlinedSchema" + counter;
    }

    // ---------------------------------------------------------------------------
    //  Composition-only schema detection
    // ---------------------------------------------------------------------------

    /**
     * Collect schemas that are only used via allOf/oneOf/anyOf in other schemas.
     */
    Set<String> collectCompositionOnlySchemas(Map<String, Object> schemas) {
        Set<String> compositionOnlySchemas = new HashSet<>();

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            boolean usedInComposition = false;

            for (Map.Entry<String, Object> otherEntry : schemas.entrySet()) {
                if (otherEntry.getKey().equals(schemaName)) continue;

                Map<String, Object> otherSchema = Util.asStringObjectMap(otherEntry.getValue());
                if (otherSchema == null) continue;

                // Check allOf
                if (otherSchema.containsKey("allOf")) {
                    List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(otherSchema.get("allOf"));
                    for (Map<String, Object> subSchema : allOfSchemas) {
                        if (JerseySchemaUtils.isSchemaReference(subSchema, schemaName)) {
                            usedInComposition = true;
                            break;
                        }
                    }
                }

                // Check oneOf
                if (otherSchema.containsKey("oneOf")) {
                    List<Map<String, Object>> oneOfSchemas = Util.asStringObjectMapList(otherSchema.get("oneOf"));
                    if (oneOfSchemas != null) {
                        for (Map<String, Object> subSchema : oneOfSchemas) {
                            if (JerseySchemaUtils.isSchemaReference(subSchema, schemaName)) {
                                usedInComposition = true;
                                break;
                            }
                        }
                    }
                }

                // Check anyOf
                if (otherSchema.containsKey("anyOf")) {
                    List<Map<String, Object>> anyOfSchemas = Util.asStringObjectMapList(otherSchema.get("anyOf"));
                    if (anyOfSchemas != null) {
                        for (Map<String, Object> subSchema : anyOfSchemas) {
                            if (JerseySchemaUtils.isSchemaReference(subSchema, schemaName)) {
                                usedInComposition = true;
                                break;
                            }
                        }
                    }
                }

                if (usedInComposition) break;
            }

            if (usedInComposition) {
                compositionOnlySchemas.add(schemaName);
            }
        }

        return compositionOnlySchemas;
    }

    // ---------------------------------------------------------------------------
    //  Referenced schema collection
    // ---------------------------------------------------------------------------

    /**
     * Collect all component schema names that are referenced from paths (responses, requestBody, parameters)
     * and from components (responses, requestBodies, parameters). Includes transitive refs.
     * Used to generate only referenced schemas and to restrict inline schema collection.
     */
    Set<String> collectAllReferencedSchemaNames(Map<String, Object> spec) {
        Set<String> referencedSchemas = new HashSet<>();
        Map<Object, Boolean> globalVisitedSchemas = new IdentityHashMap<>();
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));

        // Helper to collect from a single schema object with StackOverflow protection
        java.util.function.Consumer<Object> collectFromSchema = schemaObj -> {
            if (schemaObj == null) return;
            try {
                collectSchemasFromSchemaObject(schemaObj, referencedSchemas, spec, globalVisitedSchemas, 0);
            } catch (StackOverflowError e) {
                logger.warning("StackOverflow in collectAllReferencedSchemaNames, skipping deep recursion");
                if (schemaObj instanceof Map) {
                    Map<String, Object> m = Util.asStringObjectMap(schemaObj);
                    if (m != null && m.containsKey("$ref")) {
                        String ref = (String) m.get("$ref");
                        if (ref != null && ref.startsWith("#/components/schemas/")) {
                            referencedSchemas.add(ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                }
            }
            if (referencedSchemas.size() > 10000) {
                logger.warning("Schema collection limit reached in collectAllReferencedSchemaNames");
            }
        };

        // Components: schemas
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null) {
                for (Object schemaObj : schemas.values()) {
                    collectFromSchema.accept(schemaObj);
                }
            }
        }

        // Components: responses
        if (components != null) {
            Map<String, Object> responses = Util.asStringObjectMap(components.get("responses"));
            if (responses != null) {
                for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                    Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
                    if (response == null) continue;
                    Set<String> visitedResponseNames = new HashSet<>();
                    response = resolveResponseReference(response, components, visitedResponseNames);
                    if (response == null) continue;
                    if (response.containsKey("content")) {
                        Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                        if (content != null) {
                            for (Object mediaTypeObj : content.values()) {
                                Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                if (mediaType != null && mediaType.containsKey("schema")) {
                                    collectFromSchema.accept(mediaType.get("schema"));
                                }
                            }
                        }
                    }
                }
            }
            // Components: requestBodies
            Map<String, Object> requestBodies = Util.asStringObjectMap(components.get("requestBodies"));
            if (requestBodies != null) {
                for (Object rbObj : requestBodies.values()) {
                    Map<String, Object> rb = Util.asStringObjectMap(rbObj);
                    if (rb == null || !rb.containsKey("content")) continue;
                    Map<String, Object> content = Util.asStringObjectMap(rb.get("content"));
                    if (content != null) {
                        for (Object mediaTypeObj : content.values()) {
                            Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                            if (mediaType != null && mediaType.containsKey("schema")) {
                                collectFromSchema.accept(mediaType.get("schema"));
                            }
                        }
                    }
                }
            }
            // Components: parameters
            Map<String, Object> parameters = Util.asStringObjectMap(components.get("parameters"));
            if (parameters != null) {
                for (Object paramObj : parameters.values()) {
                    Map<String, Object> param = Util.asStringObjectMap(paramObj);
                    if (param != null && param.containsKey("schema")) {
                        collectFromSchema.accept(param.get("schema"));
                    }
                }
            }
        }

        // Paths: each operation's responses, requestBody, parameters
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
                if (pathItem == null) continue;
                // Path-level parameters
                if (pathItem.containsKey("parameters")) {
                    List<Map<String, Object>> params = Util.asStringObjectMapList(pathItem.get("parameters"));
                    if (params != null) {
                        for (Map<String, Object> param : params) {
                            if (param != null && param.containsKey("schema")) {
                                collectFromSchema.accept(param.get("schema"));
                            }
                        }
                    }
                }
                for (String method : new String[]{"get", "post", "put", "patch", "delete", "head", "options", "trace"}) {
                    if (!pathItem.containsKey(method)) continue;
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;
                    // Operation responses
                    if (operation.containsKey("responses")) {
                        Map<String, Object> operationResponses = Util.asStringObjectMap(operation.get("responses"));
                        if (operationResponses != null) {
                            for (Map.Entry<String, Object> responseEntry : operationResponses.entrySet()) {
                                Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
                                if (response == null) continue;
                                Set<String> visitedResponseNames = new HashSet<>();
                                response = resolveResponseReference(response, components, visitedResponseNames);
                                if (response == null) continue;
                                if (response.containsKey("content")) {
                                    Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                    if (content != null) {
                                        for (Object mediaTypeObj : content.values()) {
                                            Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                            if (mediaType != null && mediaType.containsKey("schema")) {
                                                collectFromSchema.accept(mediaType.get("schema"));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Operation requestBody
                    if (operation.containsKey("requestBody")) {
                        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
                        if (requestBody != null && requestBody.containsKey("content")) {
                            Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
                            if (content != null) {
                                for (Object mediaTypeObj : content.values()) {
                                    Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                    if (mediaType != null && mediaType.containsKey("schema")) {
                                        collectFromSchema.accept(mediaType.get("schema"));
                                    }
                                }
                            }
                        }
                    }
                    // Operation parameters
                    if (operation.containsKey("parameters")) {
                        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
                        if (params != null) {
                            for (Map<String, Object> param : params) {
                                if (param != null && param.containsKey("schema")) {
                                    collectFromSchema.accept(param.get("schema"));
                                }
                            }
                        }
                    }
                }
            }
        }

        return referencedSchemas;
    }

    // ---------------------------------------------------------------------------
    //  Schema object recursive collection
    // ---------------------------------------------------------------------------

    /**
     * Recursively collect schema names from a schema object.
     */
    void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec) {
        collectSchemasFromSchemaObject(schemaObj, referencedSchemas, spec, new IdentityHashMap<>());
    }

    /**
     * Recursively collect schema names from a schema object with cycle detection.
     */
    void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec, Map<Object, Boolean> visited) {
        collectSchemasFromSchemaObject(schemaObj, referencedSchemas, spec, visited, 0);
    }

    /**
     * Recursively collect schema names from a schema object with cycle detection and depth limit.
     */
    void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec, Map<Object, Boolean> visited, int depth) {
        if (depth > 15) {
            logger.warning("Recursion depth limit reached in collectSchemasFromSchemaObject: " + depth);
            return;
        }

        if (referencedSchemas.size() > 1500) {
            logger.warning("Schema collection limit reached in collectSchemasFromSchemaObject, stopping to prevent StackOverflow");
            return;
        }

        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null && schemas.size() > 500 && depth > 3) {
                logger.warning("Early bailout for very large spec at depth: " + depth);
                return;
            }
        }

        if (schemaObj == null) {
            return;
        }

        if (!(schemaObj instanceof Map<?, ?>)) {
            return;
        }

        // If this map is exactly a top-level components/schemas definition, register its name.
        // Must run before the visited short-circuit: the same instance may be reached first from
        // iterating all schemas (no x-resolved-ref on the root object) and later from property/item
        // traversal; a second visit hits visited and would otherwise skip x-resolved-ref handling.
        // Util.asStringObjectMap copies break identity checks later in this method.
        Map<String, Object> compsForReg = Util.asStringObjectMap(spec.get("components"));
        if (compsForReg != null) {
            Map<String, Object> schemasForReg = Util.asStringObjectMap(compsForReg.get("schemas"));
            if (schemasForReg != null) {
                for (Map.Entry<String, Object> e : schemasForReg.entrySet()) {
                    if (e.getValue() == schemaObj) {
                        referencedSchemas.add(e.getKey());
                        break;
                    }
                }
            }
        }

        // Cycle detection
        if (visited.containsKey(schemaObj)) {
            return;
        }
        visited.put(schemaObj, Boolean.TRUE);

        Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
        if (schema == null) {
            return;
        }

        // Check for x-resolved-ref (parser preserves this when $ref was resolved) - add schema name immediately
        String resolvedRefSchemaName = JerseySchemaUtils.getSchemaNameFromRef(schema);
        if (resolvedRefSchemaName != null) {
            if (!referencedSchemas.contains(resolvedRefSchemaName)) {
                referencedSchemas.add(resolvedRefSchemaName);
                components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(resolvedRefSchemaName) && depth < 15) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(resolvedRefSchemaName));
                        if (referencedSchema != null && !visited.containsKey(referencedSchema)) {
                            visited.put(referencedSchema, Boolean.TRUE);
                            if (referencedSchema.containsKey("type") && "array".equals(referencedSchema.get("type"))) {
                                Object items = referencedSchema.get("items");
                                if (items != null) {
                                    collectSchemasFromSchemaObject(items, referencedSchemas, spec, visited, depth + 1);
                                }
                            } else {
                                collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec, visited, depth + 1);
                            }
                        }
                    }
                }
            }
            return;
        }

        // Check for $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                if (!referencedSchemas.contains(schemaName)) {
                    referencedSchemas.add(schemaName);
                    components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null && schemas.containsKey(schemaName)) {
                            Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                            if (referencedSchema != null) {
                                if (visited.containsKey(referencedSchema)) {
                                    return;
                                }
                                visited.put(referencedSchema, Boolean.TRUE);

                                if (referencedSchema.containsKey("type") && "array".equals(referencedSchema.get("type"))) {
                                    Object items = referencedSchema.get("items");
                                    if (items != null && depth < 10) {
                                        collectSchemasFromSchemaObject(items, referencedSchemas, spec, visited, depth + 1);
                                    }
                                } else if (depth < 10) {
                                    collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec, visited, depth + 1);
                                }
                            }
                        }
                    }
                }
                return;
            }
            // External $ref
            if (ref != null && depth < 15) {
                String derivedName = JerseySchemaUtils.deriveSchemaNameFromExternalRef(ref);
                if (derivedName != null) {
                    components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null && schemas.containsKey(derivedName)) {
                            if (!referencedSchemas.contains(derivedName)) {
                                referencedSchemas.add(derivedName);
                                Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(derivedName));
                                if (referencedSchema != null && !visited.containsKey(referencedSchema)) {
                                    visited.put(referencedSchema, Boolean.TRUE);
                                    if (referencedSchema.containsKey("type") && "array".equals(referencedSchema.get("type"))) {
                                        Object items = referencedSchema.get("items");
                                        if (items != null) {
                                            collectSchemasFromSchemaObject(items, referencedSchemas, spec, visited, depth + 1);
                                        }
                                    } else {
                                        collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec, visited, depth + 1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        // Identity match: parser may resolve $ref in place
        if (depth < 15) {
            Map<String, Object> componentsForIdentity = Util.asStringObjectMap(spec.get("components"));
            if (componentsForIdentity != null) {
                Map<String, Object> schemasMap = Util.asStringObjectMap(componentsForIdentity.get("schemas"));
                if (schemasMap != null) {
                    for (Map.Entry<String, Object> schemaEntry : schemasMap.entrySet()) {
                        if (schemaEntry.getValue() == schemaObj) {
                            String schemaName = schemaEntry.getKey();
                            if (!referencedSchemas.contains(schemaName)) {
                                referencedSchemas.add(schemaName);
                                if (visited.containsKey(schema)) {
                                    return;
                                }
                                visited.put(schema, Boolean.TRUE);
                                if (schema.containsKey("type") && "array".equals(schema.get("type"))) {
                                    Object items = schema.get("items");
                                    if (items != null) {
                                        collectSchemasFromSchemaObject(items, referencedSchemas, spec, visited, depth + 1);
                                    }
                                } else {
                                    collectSchemasFromSchemaObject(schema, referencedSchemas, spec, visited, depth + 1);
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }

        // Resolved array type schema - find its name by matching structure
        if (!schema.containsKey("$ref") && schema.containsKey("type") && "array".equals(schema.get("type"))) {
            components = Util.asStringObjectMap(spec.get("components"));
            if (components != null) {
                Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                if (schemas != null) {
                    for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                        if (schemaEntry.getValue() == schemaObj) {
                            String schemaName = schemaEntry.getKey();
                            if (!referencedSchemas.contains(schemaName)) {
                                referencedSchemas.add(schemaName);
                                Object itemsObj = schema.get("items");
                                if (itemsObj != null) {
                                    visited.put(schema, Boolean.TRUE);
                                    collectSchemasFromSchemaObject(itemsObj, referencedSchemas, spec, visited, depth + 1);
                                }
                            }
                            return;
                        }
                    }
                    Object itemsObj = schema.get("items");
                    if (itemsObj != null) {
                        Map<String, Object> itemsMap = Util.asStringObjectMap(itemsObj);
                        String itemsRef = null;
                        if (itemsMap != null && itemsMap.containsKey("$ref")) {
                            itemsRef = (String) itemsMap.get("$ref");
                        }

                        if (itemsRef != null) {
                            for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                                Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                                if (candidateSchema != null &&
                                    candidateSchema.containsKey("type") &&
                                    "array".equals(candidateSchema.get("type"))) {
                                    Object candidateItems = candidateSchema.get("items");
                                    if (candidateItems != null) {
                                        Map<String, Object> candidateItemsMap = Util.asStringObjectMap(candidateItems);
                                        if (candidateItemsMap != null) {
                                            String candidateItemsRef = (String) candidateItemsMap.get("$ref");
                                            if (itemsRef.equals(candidateItemsRef)) {
                                                String schemaName = schemaEntry.getKey();
                                                if (!referencedSchemas.contains(schemaName)) {
                                                    referencedSchemas.add(schemaName);
                                                    visited.put(schema, Boolean.TRUE);
                                                    collectSchemasFromSchemaObject(itemsObj, referencedSchemas, spec, visited, depth + 1);
                                                }
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        visited.put(schema, Boolean.TRUE);
                        collectSchemasFromSchemaObject(itemsObj, referencedSchemas, spec, visited, depth + 1);
                        return;
                    } else {
                        return;
                    }
                }
            }
        }

        // Check allOf, oneOf, anyOf
        if (depth < 10) {
            for (String compositionType : new String[]{"allOf", "oneOf", "anyOf"}) {
                if (schema.containsKey(compositionType)) {
                    Object compObj = schema.get(compositionType);
                    if (compObj instanceof List<?> compositions) {
                        int compCount = 0;
                        int maxCompositions = 20;
                        for (Object compItem : compositions) {
                            if (compCount++ >= maxCompositions) {
                                logger.warning("Composition limit reached in collectSchemasFromSchemaObject at depth " + depth);
                                break;
                            }
                            collectSchemasFromSchemaObject(compItem, referencedSchemas, spec, visited, depth + 1);
                        }
                    }
                }
            }

            // Check items (for arrays)
            if (schema.containsKey("items")) {
                collectSchemasFromSchemaObject(schema.get("items"), referencedSchemas, spec, visited, depth + 1);
            }
        }

        // Check properties (for objects)
        if (schema.containsKey("properties") && depth < 10) {
            Object propsObj = schema.get("properties");
            if (propsObj instanceof Map<?, ?>) {
                Map<String, Object> properties = Util.asStringObjectMap(propsObj);
                int propertyCount = 0;
                int maxProperties = 100;
                for (Object propSchema : properties.values()) {
                    if (referencedSchemas.size() > 1500) {
                        logger.warning("Schema collection limit reached during property processing at depth " + depth);
                        break;
                    }
                    if (propertyCount++ >= maxProperties) {
                        logger.warning("Property limit reached in collectSchemasFromSchemaObject at depth " + depth);
                        break;
                    }
                    collectSchemasFromSchemaObject(propSchema, referencedSchemas, spec, visited, depth + 1);
                }
            }
        }
    }

    /**
     * Add schema to referenced set and recursively collect nested schemas.
     */
    void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec) {
        addSchemaAndCollectNested(schemaName, referencedSchemas, spec, new IdentityHashMap<>());
    }

    /**
     * Add schema to referenced set and recursively collect nested schemas with cycle detection.
     */
    void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec, Map<Object, Boolean> visited) {
        if (referencedSchemas.contains(schemaName)) {
            return;
        }

        referencedSchemas.add(schemaName);

        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null && schemas.containsKey(schemaName)) {
                Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                if (referencedSchema != null) {
                    visited.put(referencedSchema, Boolean.TRUE);
                    collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec, visited);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    //  Response-referenced schema collection
    // ---------------------------------------------------------------------------

    /**
     * Collect schemas that are referenced in components/responses and paths/responses.
     * This is needed to generate model classes for array types that are referenced in responses.
     */
    Set<String> collectSchemasReferencedInResponses(Map<String, Object> spec) {
        Set<String> referencedSchemas = new HashSet<>();

        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));

        // Build a cache/index of array schemas by their items $ref for efficient lookup
        Map<String, String> arraySchemaByItemsRef = new HashMap<>();
        Map<String, String> arraySchemaByItemsType = new HashMap<>();
        Map<String, Integer> arraySchemaCountByItemsType = new HashMap<>();

        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null) {
                for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                    Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                    if (candidateSchema != null &&
                        candidateSchema.containsKey("type") &&
                        "array".equals(candidateSchema.get("type"))) {
                        Object items = candidateSchema.get("items");
                        if (items != null) {
                            Map<String, Object> itemsMap = Util.asStringObjectMap(items);
                            if (itemsMap != null) {
                                String itemsRef = (String) itemsMap.get("$ref");
                                String itemsType = (String) itemsMap.get("type");
                                if (itemsRef != null) {
                                    arraySchemaByItemsRef.put(itemsRef, schemaEntry.getKey());
                                }
                                if (itemsType != null) {
                                    arraySchemaCountByItemsType.put(itemsType,
                                        arraySchemaCountByItemsType.getOrDefault(itemsType, 0) + 1);
                                    if (arraySchemaCountByItemsType.get(itemsType) == 1) {
                                        arraySchemaByItemsType.put(itemsType, schemaEntry.getKey());
                                    } else {
                                        arraySchemaByItemsType.remove(itemsType);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<Object, Boolean> globalVisitedSchemas = new IdentityHashMap<>();

        // Helper to process a response schema object for array type matching
        java.util.function.BiConsumer<Object, Map<String, Object>> processResponseSchema = (schemaObj, comps) -> {
            try {
                collectSchemasFromSchemaObject(schemaObj, referencedSchemas, spec, globalVisitedSchemas, 0);
            } catch (StackOverflowError e) {
                logger.warning("StackOverflow in collectSchemasFromSchemaObject, skipping deep recursion");
                if (schemaObj instanceof Map) {
                    Map<String, Object> sm = Util.asStringObjectMap(schemaObj);
                    if (sm != null && sm.containsKey("$ref")) {
                        String ref = (String) sm.get("$ref");
                        if (ref != null && ref.startsWith("#/components/schemas/")) {
                            referencedSchemas.add(ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                }
            }

            if (referencedSchemas.size() > 10000) {
                logger.warning("Schema collection limit reached, stopping to prevent StackOverflow");
                return;
            }

            // Also check if this schema object itself is an array type that needs to be added
            Map<String, Object> schemaMap = Util.asStringObjectMap(schemaObj);
            if (schemaMap != null) {
                if (schemaMap.containsKey("$ref")) {
                    String ref = (String) schemaMap.get("$ref");
                    if (ref != null && ref.startsWith("#/components/schemas/")) {
                        String refSchemaName = ref.substring(ref.lastIndexOf("/") + 1);
                        Map<String, Object> schemas = comps != null ?
                            Util.asStringObjectMap(comps.get("schemas")) : null;
                        if (schemas != null && schemas.containsKey(refSchemaName)) {
                            Map<String, Object> refSchema = Util.asStringObjectMap(schemas.get(refSchemaName));
                            if (refSchema != null && refSchema.containsKey("type") &&
                                "array".equals(refSchema.get("type"))) {
                                referencedSchemas.add(refSchemaName);
                            }
                        }
                    }
                } else if (!schemaMap.containsKey("$ref") &&
                           schemaMap.containsKey("type") &&
                           "array".equals(schemaMap.get("type"))) {
                    Map<String, Object> schemas = comps != null ?
                        Util.asStringObjectMap(comps.get("schemas")) : null;
                    if (schemas != null) {
                        boolean found = false;
                        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                            Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                            if (candidateSchema == schemaMap) {
                                String schemaName = schemaEntry.getKey();
                                if (!referencedSchemas.contains(schemaName)) {
                                    referencedSchemas.add(schemaName);
                                }
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            Object responseItems = schemaMap.get("items");
                            String responseItemsRef = null;
                            String responseItemsType = null;
                            if (responseItems != null) {
                                Map<String, Object> responseItemsMap = Util.asStringObjectMap(responseItems);
                                if (responseItemsMap != null) {
                                    responseItemsRef = (String) responseItemsMap.get("$ref");
                                    responseItemsType = (String) responseItemsMap.get("type");
                                }
                            }

                            if (responseItemsRef != null && arraySchemaByItemsRef.containsKey(responseItemsRef)) {
                                String schemaName = arraySchemaByItemsRef.get(responseItemsRef);
                                if (!referencedSchemas.contains(schemaName)) {
                                    referencedSchemas.add(schemaName);
                                }
                                found = true;
                            }

                            if (!found && responseItemsType != null && arraySchemaByItemsType.containsKey(responseItemsType)) {
                                String schemaName = arraySchemaByItemsType.get(responseItemsType);
                                if (schemaName != null && !referencedSchemas.contains(schemaName)) {
                                    referencedSchemas.add(schemaName);
                                }
                            }
                        }
                    }
                }
            }
        };

        // Collect from components/responses
        if (components != null) {
            Map<String, Object> responses = Util.asStringObjectMap(components.get("responses"));
            if (responses != null) {
                for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                    Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
                    if (response == null) continue;
                    Set<String> visitedResponseNames = new HashSet<>();
                    response = resolveResponseReference(response, components, visitedResponseNames);
                    if (response == null) continue;
                    if (response.containsKey("content")) {
                        Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                        if (content != null) {
                            for (Object mediaTypeObj : content.values()) {
                                Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                if (mediaType != null && mediaType.containsKey("schema")) {
                                    processResponseSchema.accept(mediaType.get("schema"), components);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Also collect from paths/responses
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
                if (pathItem == null) continue;

                for (String method : new String[]{"get", "post", "put", "patch", "delete", "head", "options", "trace"}) {
                    if (pathItem.containsKey(method)) {
                        Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                        if (operation != null && operation.containsKey("responses")) {
                            Map<String, Object> operationResponses = Util.asStringObjectMap(operation.get("responses"));
                            if (operationResponses != null) {
                                for (Map.Entry<String, Object> responseEntry : operationResponses.entrySet()) {
                                    Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
                                    if (response == null) continue;
                                    Set<String> visitedResponseNames = new HashSet<>();
                                    response = resolveResponseReference(response, components, visitedResponseNames);
                                    if (response == null) continue;
                                    if (response.containsKey("content")) {
                                        Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                        if (content != null) {
                                            for (Object mediaTypeObj : content.values()) {
                                                Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                                if (mediaType != null && mediaType.containsKey("schema")) {
                                                    processResponseSchema.accept(mediaType.get("schema"), components);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return referencedSchemas;
    }

    // ---------------------------------------------------------------------------
    //  Response reference resolution
    // ---------------------------------------------------------------------------

    /**
     * Resolve a response reference with cycle detection to prevent infinite recursion.
     */
    Map<String, Object> resolveResponseReference(Map<String, Object> response, Map<String, Object> components, Set<String> visitedResponseNames) {
        if (response == null || !response.containsKey("$ref")) {
            return response;
        }

        String ref = (String) response.get("$ref");
        if (ref == null || !ref.startsWith("#/components/responses/")) {
            return response;
        }

        String responseName = ref.substring(ref.lastIndexOf("/") + 1);

        if (visitedResponseNames.contains(responseName)) {
            return null;
        }

        visitedResponseNames.add(responseName);

        if (components != null) {
            Map<String, Object> componentResponses = Util.asStringObjectMap(components.get("responses"));
            if (componentResponses != null && componentResponses.containsKey(responseName)) {
                Map<String, Object> resolvedResponse = Util.asStringObjectMap(componentResponses.get(responseName));
                if (resolvedResponse != null && resolvedResponse.containsKey("$ref")) {
                    return resolveResponseReference(resolvedResponse, components, visitedResponseNames);
                }
                return resolvedResponse;
            }
        }

        return null;
    }
}
