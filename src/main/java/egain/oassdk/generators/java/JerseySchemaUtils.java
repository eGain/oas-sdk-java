package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.util.*;

/**
 * Pure stateless utility methods for OpenAPI schema inspection and resolution.
 * All methods are static and operate on raw schema maps.
 */
public final class JerseySchemaUtils {

    /** Max depth when resolving allOf/oneOf/anyOf to prevent infinite recursion */
    public static final int MAX_COMPOSITION_RESOLVE_DEPTH = 10;

    /** Max recursion depth for mergeSchemaProperties to prevent StackOverflow on large specs. */
    public static final int MAX_MERGE_SCHEMA_DEPTH = 15;

    private JerseySchemaUtils() {
        // utility class - no instances
    }

    // ---------------------------------------------------------------------------
    //  Composition resolution
    // ---------------------------------------------------------------------------

    /**
     * Resolve allOf/oneOf/anyOf to a single effective schema for type/validation/XSD.
     * allOf: merged schema from all branches; oneOf/anyOf: first branch (Java has no union type).
     * Resolves $ref in sub-schemas when spec is non-null.
     *
     * @param schema the schema that may contain allOf, oneOf, or anyOf
     * @param spec   the full OpenAPI spec (nullable); when non-null, $ref in composition are resolved
     * @return effective schema map, or the original schema if no composition or depth exceeded
     */
    public static Map<String, Object> resolveCompositionToEffectiveSchema(Map<String, Object> schema, Map<String, Object> spec) {
        return resolveCompositionToEffectiveSchema(schema, spec, 0);
    }

    public static Map<String, Object> resolveCompositionToEffectiveSchema(Map<String, Object> schema, Map<String, Object> spec, int depth) {
        if (schema == null || depth > MAX_COMPOSITION_RESOLVE_DEPTH) {
            return schema;
        }
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas == null || allOfSchemas.isEmpty()) {
                return schema;
            }
            String singleRefSchemaName = null;
            int refCount = 0;
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Map<String, Object> sub : allOfSchemas) {
                if (sub == null) continue;
                String refName = getSchemaNameFromRef(sub);
                if (refName == null) {
                    String ref = (String) sub.get("x-resolved-ref");
                    if (ref == null) ref = (String) sub.get("$ref");
                    if (ref != null && ref.contains("components/schemas/")) {
                        refName = ref.substring(ref.lastIndexOf("/") + 1);
                    }
                }
                if (refName != null) {
                    refCount++;
                    if (singleRefSchemaName == null) singleRefSchemaName = refName;
                }
                Map<String, Object> resolved = resolveRefInSchema(sub, spec);
                resolved = resolveCompositionToEffectiveSchema(resolved, spec, depth + 1);
                if (resolved != null) {
                    mergeIntoEffectiveSchema(merged, resolved);
                }
            }
            if (refCount == 1 && singleRefSchemaName != null && !merged.isEmpty()) {
                merged.put("x-java-type-ref", singleRefSchemaName);
            }
            return merged.isEmpty() ? schema : merged;
        }
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            if (schemas != null && !schemas.isEmpty()) {
                Map<String, Object> first = schemas.get(0);
                if (first != null) {
                    first = resolveRefInSchema(first, spec);
                    return resolveCompositionToEffectiveSchema(first, spec, depth + 1);
                }
            }
            return schema;
        }
        return schema;
    }

    // ---------------------------------------------------------------------------
    //  $ref resolution
    // ---------------------------------------------------------------------------

    /** Resolve $ref in a schema using spec (components/schemas). Returns resolved map or original if no ref/spec. */
    public static Map<String, Object> resolveRefInSchema(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || spec == null || !schema.containsKey("$ref")) {
            return schema;
        }
        String ref = (String) schema.get("$ref");
        if (ref == null || !ref.contains("components/schemas/")) {
            return schema;
        }
        String schemaName = ref.contains("#/components/schemas/")
                ? ref.substring(ref.lastIndexOf("/") + 1)
                : (ref.contains("#") ? ref.substring(ref.indexOf("#/components/schemas/") + "#/components/schemas/".length()) : ref);
        if (schemaName.contains("/")) {
            schemaName = schemaName.substring(schemaName.lastIndexOf("/") + 1);
        }
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return schema;
        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null || !schemas.containsKey(schemaName)) return schema;
        Map<String, Object> resolved = Util.asStringObjectMap(schemas.get(schemaName));
        return resolved != null ? resolved : schema;
    }

    // ---------------------------------------------------------------------------
    //  Schema merging
    // ---------------------------------------------------------------------------

    /** Merge one schema into the effective merged map (type, format, pattern, constraints, properties, required). */
    public static void mergeIntoEffectiveSchema(Map<String, Object> merged, Map<String, Object> from) {
        if (from == null) return;
        for (String key : new String[]{"type", "format", "pattern", "minLength", "maxLength", "minItems", "maxItems", "enum", "writeOnly", "readOnly"}) {
            if (from.containsKey(key) && !merged.containsKey(key)) {
                merged.put(key, from.get(key));
            }
        }
        if (from.containsKey("properties")) {
            Map<String, Object> fromProps = Util.asStringObjectMap(from.get("properties"));
            if (fromProps != null && !fromProps.isEmpty()) {
                Map<String, Object> mergedProps = Util.asStringObjectMap(merged.get("properties"));
                if (mergedProps == null) {
                    mergedProps = new LinkedHashMap<>();
                    merged.put("properties", mergedProps);
                }
                mergedProps.putAll(fromProps);
            }
        }
        if (from.containsKey("required")) {
            List<String> fromReq = Util.asStringList(from.get("required"));
            if (fromReq != null && !fromReq.isEmpty()) {
                List<String> mergedReq = Util.asStringList(merged.get("required"));
                if (mergedReq == null) {
                    mergedReq = new ArrayList<>();
                    merged.put("required", mergedReq);
                }
                for (String r : fromReq) {
                    if (!mergedReq.contains(r)) mergedReq.add(r);
                }
            }
        }
        if (from.containsKey("items") && !merged.containsKey("items")) {
            merged.put("items", from.get("items"));
        }
    }

    /**
     * Merge two JSON-Schema property definition maps as sequential {@code allOf} branches would:
     * {@code earlier} is merged first, then {@code later}. The result favours {@code later} for type
     * and constraints, but preserves {@code readOnly}/{@code writeOnly} from {@code earlier} when
     * {@code later} omits those keys and the earlier flag is true.
     */
    public static Map<String, Object> mergePropertyDefinitionsForComposition(Map<String, Object> earlier,
                                                                        Map<String, Object> later) {
        if (later == null || later.isEmpty()) {
            return earlier != null ? new LinkedHashMap<>(earlier) : new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>(later);
        if (earlier != null) {
            List<Map<String, Object>> earlierAllOf = Util.asStringObjectMapList(earlier.get("allOf"));
            List<Map<String, Object>> laterAllOf = Util.asStringObjectMapList(later.get("allOf"));
            if (earlierAllOf != null && !earlierAllOf.isEmpty()
                    && laterAllOf != null && !laterAllOf.isEmpty()) {
                List<Map<String, Object>> combinedAllOf = new ArrayList<>(earlierAllOf.size() + laterAllOf.size());
                combinedAllOf.addAll(earlierAllOf);
                combinedAllOf.addAll(laterAllOf);
                out.put("allOf", combinedAllOf);
            }
            if (!later.containsKey("readOnly") && isSchemaFlagTrue(earlier, "readOnly")) {
                out.put("readOnly", true);
            }
            if (!later.containsKey("writeOnly") && isSchemaFlagTrue(earlier, "writeOnly")) {
                out.put("writeOnly", true);
            }
            Map<String, Object> laterProps = Util.asStringObjectMap(later.get("properties"));
            Map<String, Object> earlierProps = Util.asStringObjectMap(earlier.get("properties"));
            if (earlierProps != null && !earlierProps.isEmpty()) {
                Map<String, Object> mergedProps = new LinkedHashMap<>();
                if (laterProps != null) {
                    mergedProps.putAll(laterProps);
                }
                for (Map.Entry<String, Object> pe : earlierProps.entrySet()) {
                    String pk = pe.getKey();
                    Map<String, Object> eSub = Util.asStringObjectMap(pe.getValue());
                    if (!mergedProps.containsKey(pk)) {
                        mergedProps.put(pk, pe.getValue());
                    } else {
                        Map<String, Object> lSub = Util.asStringObjectMap(mergedProps.get(pk));
                        if (eSub != null && lSub != null) {
                            mergedProps.put(pk, mergePropertyDefinitionsForComposition(eSub, lSub));
                        } else {
                            mergedProps.put(pk, pe.getValue());
                        }
                    }
                }
                out.put("properties", mergedProps);
            }
        }
        return out;
    }

    /**
     * Merge a schema {@code properties} map into {@code allProperties}, deep-merging when a property
     * name already exists so readOnly/writeOnly overlays are not dropped.
     */
    public static void mergePropertiesIntoAll(Map<String, Object> allProperties,
                                               Map<String, Object> properties) {
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            String name = e.getKey();
            Object incomingObj = e.getValue();
            Map<String, Object> incoming = Util.asStringObjectMap(incomingObj);
            if (!allProperties.containsKey(name)) {
                allProperties.put(name, incomingObj);
                continue;
            }
            Object existingObj = allProperties.get(name);
            Map<String, Object> existing = Util.asStringObjectMap(existingObj);
            if (existing == null || incoming == null) {
                allProperties.put(name, incomingObj);
            } else {
                allProperties.put(name, mergePropertyDefinitionsForComposition(existing, incoming));
            }
        }
    }

    /**
     * Merge schema properties into the allProperties map.
     */
    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec) {
        mergeSchemaProperties(schema, allProperties, allRequired, spec, new IdentityHashMap<>(), 0);
    }

    /**
     * Merge schema properties with cycle detection to prevent infinite recursion.
     */
    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec,
                                       Map<Object, Boolean> visited) {
        mergeSchemaProperties(schema, allProperties, allRequired, spec, visited, 0);
    }

    /**
     * Merge schema properties with cycle detection and depth limit to prevent StackOverflow.
     */
    public static void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec,
                                       Map<Object, Boolean> visited, int depth) {
        if (schema == null) return;
        if (depth > MAX_MERGE_SCHEMA_DEPTH) {
            java.util.logging.Logger.getLogger(JerseySchemaUtils.class.getName())
                    .warning("Recursion depth limit reached in mergeSchemaProperties: " + depth);
            return;
        }

        // Cycle detection - prevent infinite recursion
        if (visited.containsKey(schema)) {
            return;
        }
        visited.put(schema, Boolean.TRUE);

        // Handle $ref
        // IMPORTANT: Check for properties FIRST, even if $ref exists.
        // When the parser resolves external file $refs, it replaces the map content,
        // so the schema should have properties directly. However, the $ref key might
        // still be present. If properties exist, use them and ignore $ref.
        if (schema.containsKey("properties")) {
            // Schema has properties - use them directly (even if $ref also exists)
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            mergePropertiesIntoAll(allProperties, properties);
            // Merge required fields
            if (schema.containsKey("required")) {
                List<String> required = Util.asStringList(schema.get("required"));
                if (required != null) {
                    for (String field : required) {
                        if (!allRequired.contains(field)) {
                            allRequired.add(field);
                        }
                    }
                }
            }
            // Properties found - return (unless there's allOf/oneOf/anyOf to handle)
            if (!schema.containsKey("allOf") && !schema.containsKey("oneOf") && !schema.containsKey("anyOf")) {
                return;
            }
        }

        // Only handle $ref if no properties were found
        if (schema.containsKey("$ref") && !schema.containsKey("properties")) {
            String ref = (String) schema.get("$ref");
            if (ref != null) {
                if (ref.startsWith("#/components/schemas/")) {
                    // Internal schema reference
                    String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null && schemas.containsKey(schemaName)) {
                            Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                            if (referencedSchema != null) {
                                mergeSchemaProperties(referencedSchema, allProperties, allRequired, spec, visited, depth + 1);
                            }
                        }
                    }
                } else if (ref.contains("#/components/schemas/")) {
                    // External file with schema path
                    String schemaPath = ref.substring(ref.indexOf("#/components/schemas/") + "#/components/schemas/".length());
                    String schemaName = schemaPath.contains("/") ? schemaPath.substring(schemaPath.lastIndexOf("/") + 1) : schemaPath;
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null && schemas.containsKey(schemaName)) {
                            Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                            if (referencedSchema != null) {
                                mergeSchemaProperties(referencedSchema, allProperties, allRequired, spec, visited, depth + 1);
                            }
                        }
                    }
                } else {
                    // External file reference without schema path (e.g., ../../../models/v4/User.yaml)
                    // The parser should have resolved this by replacing $ref with the file content.
                }
                return;
            }
        }

        // Merge direct properties (this handles schemas that were resolved and have properties)
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            mergePropertiesIntoAll(allProperties, properties);
            // Merge required fields
            if (schema.containsKey("required")) {
                List<String> required = Util.asStringList(schema.get("required"));
                if (required != null) {
                    for (String field : required) {
                        if (!allRequired.contains(field)) {
                            allRequired.add(field);
                        }
                    }
                }
            }
            // If schema has properties, we've merged them, so we can return
            // (unless it also has allOf/oneOf/anyOf which should be handled)
            if (!schema.containsKey("allOf") && !schema.containsKey("oneOf") && !schema.containsKey("anyOf")) {
                return;
            }
        }

        // Handle allOf
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas != null) {
                for (Map<String, Object> subSchema : allOfSchemas) {
                    if (subSchema != null) {
                        mergeSchemaProperties(subSchema, allProperties, allRequired, spec, visited, depth + 1);
                    }
                }
            }
            return;
        }

        // Handle oneOf/anyOf
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            if (schemas != null) {
                for (Map<String, Object> subSchema : schemas) {
                    if (subSchema != null) {
                        mergeSchemaProperties(subSchema, allProperties, allRequired, spec, visited, depth + 1);
                    }
                }
            }
            return;
        }

        // Merge direct properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            mergePropertiesIntoAll(allProperties, properties);
        }

        // Merge required fields
        if (schema.containsKey("required")) {
            List<String> required = Util.asStringList(schema.get("required"));
            if (required != null) {
                for (String field : required) {
                    if (!allRequired.contains(field)) {
                        allRequired.add(field);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    //  Schema flag / name helpers
    // ---------------------------------------------------------------------------

    /** Return true if the schema has the given key set to a truthy value (e.g. readOnly, writeOnly). */
    public static boolean isSchemaFlagTrue(Map<String, Object> schema, String key) {
        if (schema == null || !schema.containsKey(key)) return false;
        Object v = schema.get(key);
        if (v instanceof Boolean) return Boolean.TRUE.equals(v);
        if (v instanceof String) return "true".equalsIgnoreCase((String) v);
        return false;
    }

    /**
     * Extract component schema name from x-resolved-ref or $ref (e.g. "#/components/schemas/UserView" -> "UserView").
     */
    public static String getSchemaNameFromRef(Map<String, Object> schema) {
        if (schema == null) return null;
        String ref = (String) schema.get("x-resolved-ref");
        if (ref == null) ref = (String) schema.get("$ref");
        if (ref == null || !ref.contains("#/components/schemas/")) return null;
        return ref.substring(ref.lastIndexOf("/") + 1);
    }

    /**
     * Derive schema name from external file $ref (e.g. "./User.yaml" or "models/v3/User.yaml" -> "User").
     * Used when array items have unresolved external $ref so we can resolve to List&lt;User&gt; if that schema exists.
     */
    public static String deriveSchemaNameFromExternalRef(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        String path = ref.contains("#") ? ref.split("#", 2)[0] : ref;
        path = path.replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (segment.isEmpty()) return null;
        if (segment.endsWith(".yaml")) return segment.substring(0, segment.length() - 5);
        if (segment.endsWith(".yml")) return segment.substring(0, segment.length() - 4);
        if (segment.endsWith(".json")) return segment.substring(0, segment.length() - 5);
        return segment;
    }

    // ---------------------------------------------------------------------------
    //  Object-with-single-array-of-ref detection
    // ---------------------------------------------------------------------------

    /** Holder for (innerPropertyName, itemTypeName) when schema is object with single array of ref. */
    public static final class ObjectWithSingleArrayInfo {
        final String innerPropertyName;
        final String itemTypeName;

        ObjectWithSingleArrayInfo(String innerPropertyName, String itemTypeName) {
            this.innerPropertyName = innerPropertyName;
            this.itemTypeName = itemTypeName;
        }
    }

    /**
     * True if the schema is an object with exactly one property that is an array of a $ref.
     * Used to generate a wrapper inner class instead of flattening to List.
     */
    public static boolean isObjectWithSingleArrayOfRef(Map<String, Object> schema, Map<String, Object> spec) {
        return getObjectWithSingleArrayInfo(schema, spec) != null;
    }

    /**
     * If the schema is an object with exactly one property that is an array of a $ref,
     * return (innerPropertyName, itemTypeName) e.g. ("tagCategory", "TagCategory"). Otherwise null.
     */
    public static ObjectWithSingleArrayInfo getObjectWithSingleArrayInfo(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || spec == null) return null;
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.size() != 1) return null;
        Map.Entry<String, Object> single = properties.entrySet().iterator().next();
        Map<String, Object> nested = Util.asStringObjectMap(single.getValue());
        if (nested == null || !"array".equals(nested.get("type"))) return null;
        Object itemsObj = nested.get("items");
        if (itemsObj == null || !(itemsObj instanceof Map)) return null;
        Map<String, Object> items = Util.asStringObjectMap(itemsObj);
        if (items == null) return null;
        String itemSchemaName = getSchemaNameFromRef(items);
        if (itemSchemaName == null && items.containsKey("$ref")) {
            String ref = (String) items.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                itemSchemaName = ref.substring(ref.lastIndexOf("/") + 1);
            }
        }
        if (itemSchemaName == null && items.containsKey("$ref")) {
            String ref = (String) items.get("$ref");
            if (ref != null && (ref.endsWith(".yaml") || ref.endsWith(".yml") || ref.endsWith(".json"))) {
                String externalSchemaName = deriveSchemaNameFromExternalRef(ref);
                if (externalSchemaName != null) {
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    Map<String, Object> schemas = components != null ? Util.asStringObjectMap(components.get("schemas")) : null;
                    if (schemas != null && schemas.containsKey(externalSchemaName)) {
                        itemSchemaName = externalSchemaName;
                    }
                }
            }
        }
        if (itemSchemaName == null || itemSchemaName.isEmpty()) return null;
        return new ObjectWithSingleArrayInfo(single.getKey(), JerseyNamingUtils.toJavaClassName(itemSchemaName));
    }

    /**
     * Inner class name for an object-with-single-array property (e.g. accessTags -> AccessTags).
     */
    public static String getWrapperClassName(String propertyName) {
        return JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName(propertyName));
    }

    /**
     * If the schema is an object with exactly one property that is an array of a $ref,
     * return the Java type as List&lt;ResolvedRefType&gt;. Otherwise return null.
     */
    public static String getListTypeForObjectWithSingleArrayOfRef(Map<String, Object> schema, Map<String, Object> spec) {
        ObjectWithSingleArrayInfo info = getObjectWithSingleArrayInfo(schema, spec);
        return info == null ? null : "List<" + info.itemTypeName + ">";
    }

    /**
     * Check if a schema object references the given schema name.
     */
    public static boolean isSchemaReference(Map<String, Object> schema, String schemaName) {
        if (schema == null) return false;
        String ref = (String) schema.get("$ref");
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String refSchemaName = ref.substring(ref.lastIndexOf("/") + 1);
            return refSchemaName.equals(schemaName);
        }
        return false;
    }
}
