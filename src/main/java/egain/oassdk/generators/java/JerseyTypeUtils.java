package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.util.*;

/**
 * Type-resolution and validation-annotation utilities for the Jersey generator.
 * Encapsulates the logic for mapping OpenAPI schemas to Java types.
 */
public final class JerseyTypeUtils {

    private final JerseyGenerationContext ctx;

    /** Visited set for getJavaType to prevent infinite recursion (identity-based cycle detection). */
    private final Set<Object> javaTypeVisited = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Name-based visited set as a secondary guard against cycles through $ref resolution. */
    private final Set<String> javaTypeVisitedNames = new HashSet<>();

    /** Types that do not require a model import (primitives, java/javax types, current class). */
    private static final Set<String> MODEL_IMPORT_EXCLUDES = Set.of(
        "Object", "String", "Integer", "Boolean", "Long", "Double", "Float",
        "List", "Map", "XMLGregorianCalendar", "JAXBElement", "QName", "int", "boolean", "long", "double", "float");

    public JerseyTypeUtils(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    // ---------------------------------------------------------------------------
    //  Main type resolution
    // ---------------------------------------------------------------------------

    /**
     * Convert OpenAPI type to Java type.
     */
    public String getJavaType(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }

        // Cycle detection: identity-based for same object reference
        if (javaTypeVisited.contains(schema)) {
            return "Object"; // Return default to break cycle
        }

        // Secondary name-based cycle detection for schemas resolved through $ref
        String schemaRef = schema.containsKey("$ref") ? String.valueOf(schema.get("$ref")) : null;
        if (schemaRef != null && javaTypeVisitedNames.contains(schemaRef)) {
            return "Object";
        }

        javaTypeVisited.add(schema);
        if (schemaRef != null) {
            javaTypeVisitedNames.add(schemaRef);
        }
        try {
            return getJavaTypeInternal(schema);
        } finally {
            javaTypeVisited.remove(schema);
            if (schemaRef != null) {
                javaTypeVisitedNames.remove(schemaRef);
            }
        }
    }

    private String getJavaTypeInternal(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }

        String componentSchemaName = resolveNamedComponentType(schema);
        if (componentSchemaName != null) {
            return JerseyNamingUtils.toJavaClassName(componentSchemaName);
        }

        // When allOf has exactly one ref branch (e.g. L10NString + enum constraints), use that ref's type
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            if (allOfSchemas != null) {
                int refCount = 0;
                String singleRefSchemaName = null;
                for (Map<String, Object> sub : allOfSchemas) {
                    if (sub == null) continue;
                    String name = JerseySchemaUtils.getSchemaNameFromRef(sub);
                    if (name == null) {
                        // Fallback: extract from $ref/x-resolved-ref when ref contains "components/schemas/"
                        String ref = (String) sub.get("x-resolved-ref");
                        if (ref == null) ref = (String) sub.get("$ref");
                        if (ref != null && ref.contains("components/schemas/")) {
                            name = ref.substring(ref.lastIndexOf("/") + 1);
                        }
                    }
                    if (name != null) {
                        refCount++;
                        if (singleRefSchemaName == null) {
                            singleRefSchemaName = name;
                        }
                    }
                }
                if (refCount == 1 && singleRefSchemaName != null
                        && !JerseySchemaUtils.allOfHasPropertyOverlayBranches(allOfSchemas)) {
                    return JerseyNamingUtils.toJavaClassName(singleRefSchemaName);
                }
            }
        }

        // Parser-inlined internal $ref: body is oneOf/anyOf but x-resolved-ref (or $ref) names the component.
        // Use that model type instead of collapsing to the first branch (which would become Object).
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            String namedUnionSchema = JerseySchemaUtils.getSchemaNameFromRef(schema);
            if (namedUnionSchema != null) {
                return JerseyNamingUtils.toJavaClassName(namedUnionSchema);
            }
        }

        // Resolve property-level allOf/oneOf/anyOf to effective schema for type resolution
        if (schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, ctx.spec);
            if (effective != null && effective != schema) {
                schema = effective;
            }
            if (schema != null && schema.containsKey("x-java-type-ref")) {
                String refName = (String) schema.get("x-java-type-ref");
                if (refName != null && !refName.isEmpty()) {
                    return JerseyNamingUtils.toJavaClassName(refName);
                }
            }
        }

        // Check for $ref first (before type check, as $ref schemas may not have explicit type)
        String ref = (String) schema.get("$ref");
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
            return JerseyNamingUtils.toJavaClassName(schemaRef);
        }

        // x-resolved-ref (parser may set when $ref was resolved, e.g. from external file merge)
        String refSchemaName = JerseySchemaUtils.getSchemaNameFromRef(schema);
        if (refSchemaName != null) {
            return JerseyNamingUtils.toJavaClassName(refSchemaName);
        }

        // External file $ref (e.g. ./UserDirectReports.yaml) - derive name and use if schema exists in spec
        if (ref != null && (ref.endsWith(".yaml") || ref.endsWith(".yml") || ref.endsWith(".json"))) {
            String externalSchemaName = JerseySchemaUtils.deriveSchemaNameFromExternalRef(ref);
            if (externalSchemaName != null) {
                Map<String, Object> spec = ctx.spec;
                if (spec != null) {
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    Map<String, Object> schemas = components != null ? Util.asStringObjectMap(components.get("schemas")) : null;
                    if (schemas != null && schemas.containsKey(externalSchemaName)) {
                        return JerseyNamingUtils.toJavaClassName(externalSchemaName);
                    }
                }
            }
        }

        String type = (String) schema.get("type");
        String format = (String) schema.get("format");
        Object nullableVal = schema.get("nullable");
        boolean nullable = schema.containsKey("nullable") && (nullableVal instanceof Boolean ? (Boolean) nullableVal :
                        nullableVal instanceof String && Boolean.parseBoolean((String) nullableVal));

        switch (type) {
            case "string" -> {
                if ("date".equals(format)) {
                    return "XMLGregorianCalendar";
                } else if ("date-time".equals(format)) {
                    return "XMLGregorianCalendar";
                } else {
                    return "String";
                }
            }
            case "integer" -> {
                if ("int64".equals(format)) {
                    return resolveNumericType("long", "Long", schema, nullable);
                } else {
                    return resolveNumericType("int", "Integer", schema, nullable);
                }
            }
            case "number" -> {
                if ("float".equals(format)) {
                    return resolveNumericType("float", "Float", schema, nullable);
                } else {
                    return resolveNumericType("double", "Double", schema, nullable);
                }
            }
            case "boolean" -> {
                return "Boolean";
            }
            case "array" -> {
                if (schema.containsKey("items")) {
                    Object itemsObj = schema.get("items");
                    String itemType = null;

                    // Handle items as Map
                    if (itemsObj instanceof Map) {
                        Map<String, Object> items = Util.asStringObjectMap(itemsObj);

                        // First priority: x-resolved-ref (parser sets this when external $ref was resolved, e.g. items from User.yaml)
                        String resolvedSchemaName = JerseySchemaUtils.getSchemaNameFromRef(items);
                        if (resolvedSchemaName != null) {
                            itemType = JerseyNamingUtils.toJavaClassName(resolvedSchemaName);
                        }

                        // Second priority: Check for $ref in items
                        if (itemType == null && items.containsKey("$ref")) {
                            String itemsRef = (String) items.get("$ref");
                            if (itemsRef != null && itemsRef.startsWith("#/components/schemas/")) {
                                String schemaRef = itemsRef.substring(itemsRef.lastIndexOf("/") + 1);
                                itemType = JerseyNamingUtils.toJavaClassName(schemaRef);
                            }
                        }

                        // Third priority: Use getJavaTypeInternal to resolve (it also checks for $ref)
                        if (itemType == null) {
                            itemType = getJavaTypeInternal(items);
                        }

                        // Fallback: If still Object but items has $ref or x-resolved-ref, resolve it manually
                        if ("Object".equals(itemType)) {
                            String fallbackName = JerseySchemaUtils.getSchemaNameFromRef(items);
                            if (fallbackName == null && items.containsKey("$ref")) {
                                String itemsRef = (String) items.get("$ref");
                                if (itemsRef != null && itemsRef.startsWith("#/components/schemas/")) {
                                    fallbackName = itemsRef.substring(itemsRef.lastIndexOf("/") + 1);
                                } else if (itemsRef != null && (itemsRef.endsWith(".yaml") || itemsRef.endsWith(".yml") || itemsRef.endsWith(".json"))) {
                                    // External file $ref (e.g. ./User.yaml) - derive name and use if schema exists in spec
                                    String externalSchemaName2 = JerseySchemaUtils.deriveSchemaNameFromExternalRef(itemsRef);
                                    if (externalSchemaName2 != null) {
                                        Map<String, Object> spec2 = ctx.spec;
                                        if (spec2 != null) {
                                            Map<String, Object> components = Util.asStringObjectMap(spec2.get("components"));
                                            Map<String, Object> schemas = components != null ? Util.asStringObjectMap(components.get("schemas")) : null;
                                            if (schemas != null && schemas.containsKey(externalSchemaName2)) {
                                                fallbackName = externalSchemaName2;
                                            }
                                        }
                                    }
                                }
                            }
                            if (fallbackName != null) {
                                itemType = JerseyNamingUtils.toJavaClassName(fallbackName);
                            }
                        }
                    } else {
                        // If items is not a Map, try to get type from it directly
                        itemType = getJavaTypeInternal(schema);
                        if (itemType != null && itemType.startsWith("List<")) {
                            // Already a List type, extract inner type
                            itemType = itemType.substring(5, itemType.length() - 1);
                        }
                    }

                    // Return the resolved type or default to Object
                    return "List<" + (itemType != null ? itemType : "Object") + ">";
                } else {
                    return "List<Object>";
                }
            }
            case "object" -> {
                if (schema.containsKey("x-java-type-ref")) {
                    String refName = (String) schema.get("x-java-type-ref");
                    if (refName != null && !refName.isEmpty()) {
                        return JerseyNamingUtils.toJavaClassName(refName);
                    }
                }
                // Check if this is an in-lined schema
                if (ctx.getInlinedSchemas().containsKey(schema)) {
                    return ctx.getInlinedSchemas().get(schema);
                }
                // Object with single property that is array of $ref -> List<RefType>
                String listType = JerseySchemaUtils.getListTypeForObjectWithSingleArrayOfRef(schema, ctx.spec);
                if (listType != null) {
                    return listType;
                }
                return "Object";
            }
            case null, default -> {
                return "Object";
            }
        }
    }

    /**
     * Resolve a registered component schema name when the schema is a named composition
     * (semantic allOf overlay), not a single-ref alias like ArticleTypeForCreateEditArticle.
     */
    private String resolveNamedComponentType(Map<String, Object> schema) {
        if (!shouldUseNamedComponentType(schema)) {
            return null;
        }
        return JerseySchemaUtils.findComponentSchemaName(schema, ctx.spec);
    }

    /** Skip named-component resolution for allOf schemas that should alias to a single base ref. */
    private static boolean shouldUseNamedComponentType(Map<String, Object> schema) {
        if (schema == null || !schema.containsKey("allOf")) {
            return true;
        }
        List<Map<String, Object>> allOf = Util.asStringObjectMapList(schema.get("allOf"));
        return allOf != null && JerseySchemaUtils.allOfHasPropertyOverlayBranches(allOf);
    }

    // ---------------------------------------------------------------------------
    //  Field type computation for model properties
    // ---------------------------------------------------------------------------

    /**
     * Compute the Java field type for a property (same logic as in generateModel field loop).
     */
    String computeFieldTypeForProperty(String fieldName, Map<String, Object> fieldSchema, boolean isArrayType, Map<String, Object> spec) {
        String componentType = resolveNamedComponentType(fieldSchema);
        if (componentType != null) {
            return JerseyNamingUtils.toJavaClassName(componentType);
        }
        if (isArrayType && "items".equals(fieldName)) {
            String itemType = null;
            if (fieldSchema != null) {
                if (fieldSchema.containsKey("$ref")) {
                    String r = (String) fieldSchema.get("$ref");
                    if (r != null && r.startsWith("#/components/schemas/")) {
                        String schemaRef = r.substring(r.lastIndexOf("/") + 1);
                        itemType = JerseyNamingUtils.toJavaClassName(schemaRef);
                    }
                }
                if (itemType == null || itemType.isEmpty()) {
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null && fieldSchema != null) {
                            for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                                Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                                if (candidateSchema == fieldSchema) {
                                    itemType = JerseyNamingUtils.toJavaClassName(schemaEntry.getKey());
                                    break;
                                }
                            }
                            if (itemType == null || itemType.isEmpty()) {
                                Object fieldTypeObj = fieldSchema.get("type");
                                Object fieldProperties = fieldSchema.get("properties");
                                for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                                    Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                                    if (candidateSchema != null) {
                                        Object candidateType = candidateSchema.get("type");
                                        if (fieldTypeObj != null && fieldTypeObj.equals(candidateType)) {
                                            Map<String, Object> candidateProperties = Util.asStringObjectMap(candidateSchema.get("properties"));
                                            int fieldPropsCount = (fieldProperties != null) ? ((Map<?, ?>) fieldProperties).size() : 0;
                                            int candidatePropsCount = (candidateProperties != null) ? candidateProperties.size() : 0;
                                            if (fieldPropsCount == candidatePropsCount && fieldPropsCount > 0) {
                                                itemType = JerseyNamingUtils.toJavaClassName(schemaEntry.getKey());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (itemType == null || itemType.isEmpty()) {
                    itemType = getJavaType(fieldSchema);
                    if ("Object".equals(itemType) && fieldSchema.containsKey("$ref")) {
                        String r = (String) fieldSchema.get("$ref");
                        if (r != null && r.startsWith("#/components/schemas/")) {
                            itemType = JerseyNamingUtils.toJavaClassName(r.substring(r.lastIndexOf("/") + 1));
                        }
                    }
                }
            }
            if (itemType == null || itemType.isEmpty()) {
                itemType = "Object";
            }
            return "List<" + itemType + ">";
        }
        return getJavaType(fieldSchema);
    }

    /**
     * Field type for a property when generating the parent model: array items use List&lt;...&gt;; object-with-single-array uses wrapper class; inline object uses inner class; else computeFieldTypeForProperty.
     * @param parentSchemaName current model or enclosing class name (e.g. "KnowledgeExport" or "KnowledgeExport.DataDestination")
     */
    String getFieldTypeForModelProperty(String parentSchemaName, String fieldName, Map<String, Object> fieldSchema, boolean isArrayType, Map<String, Object> spec) {
        // Special handling for array schema wrapper classes (e.g., ArticleTypes)
        if (isArrayType && "items".equals(fieldName)) {
            String itemType = null;
            if (fieldSchema != null) {
                // Try to resolve $ref for array items
                if (fieldSchema.containsKey("$ref")) {
                    String ref = (String) fieldSchema.get("$ref");
                    if (ref != null && ref.startsWith("#/components/schemas/")) {
                        String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
                        itemType = JerseyNamingUtils.toJavaClassName(schemaRef);
                    }
                }
                // If $ref was resolved by parser, match the resolved schema to its name
                if (itemType == null || itemType.isEmpty()) {
                    Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                    if (components != null) {
                        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                        if (schemas != null) {
                            for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
                                Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                                if (candidateSchema == fieldSchema) {
                                    itemType = JerseyNamingUtils.toJavaClassName(schemaEntry.getKey());
                                    break;
                                }
                            }
                        }
                    }
                }
                // Fallback to getJavaType
                if (itemType == null || itemType.isEmpty()) {
                    itemType = getJavaType(fieldSchema);
                }
            }
            if (itemType == null || itemType.isEmpty()) {
                itemType = "Object";
            }
            return "List<" + itemType + ">";
        }
        // Object-with-single-array: use ref'd schema name if present (separate class), else wrapper inner class name
        if (JerseySchemaUtils.isObjectWithSingleArrayOfRef(fieldSchema, spec)) {
            String refSchemaName2 = JerseySchemaUtils.getSchemaNameFromRef(fieldSchema);
            if (refSchemaName2 != null) {
                return JerseyNamingUtils.toJavaClassName(refSchemaName2);
            }
            return JerseySchemaUtils.getWrapperClassName(fieldName);
        }
        if (isInlineObjectProperty(fieldSchema, spec)) {
            return parentSchemaName + "." + getInnerClassNameForInlineProperty(fieldName, fieldSchema);
        }
        return computeFieldTypeForProperty(fieldName, fieldSchema, isArrayType, spec);
    }

    // ---------------------------------------------------------------------------
    //  Model imports
    // ---------------------------------------------------------------------------

    /**
     * Add referenced model type names from a field type string to the given set.
     * Skips excluded types, the current schema name, and inner classes (currentSchemaName.*).
     */
    void addModelImportTypes(String fieldType, String currentSchemaName, Set<String> modelImports) {
        if (fieldType == null || fieldType.isEmpty()) return;
        // Inner class of current model - same file, no import
        if (currentSchemaName != null && fieldType.startsWith(currentSchemaName + ".")) {
            return;
        }
        if (fieldType.startsWith("List<")) {
            int start = fieldType.indexOf('<') + 1;
            int end = fieldType.lastIndexOf('>');
            if (start > 0 && end > start) {
                String inner = fieldType.substring(start, end).trim();
                if (!MODEL_IMPORT_EXCLUDES.contains(inner) && !inner.equals(currentSchemaName)) {
                    modelImports.add(inner);
                }
            }
        } else {
            if (!MODEL_IMPORT_EXCLUDES.contains(fieldType) && !fieldType.equals(currentSchemaName)) {
                modelImports.add(fieldType);
            }
        }
    }

    // ---------------------------------------------------------------------------
    //  Validation annotations
    // ---------------------------------------------------------------------------

    /**
     * OpenAPI string formats that map to {@code XMLGregorianCalendar} in {@link #getJavaType}.
     * CharSequence-only Bean Validation annotations must not be applied to those fields.
     */
    private static boolean isOpenApiStringTemporalFormat(String format) {
        return "date".equals(format) || "date-time".equals(format);
    }

    /**
     * Generate validation annotations based on OpenAPI schema constraints.
     */
    String generateValidationAnnotations(Map<String, Object> schema, boolean isRequired) {
        if (schema == null) {
            return isRequired ? "@NotNull\n    " : "";
        }

        // Resolve property-level allOf/oneOf/anyOf to effective schema for validation constraints
        if (schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, ctx.spec);
            if (effective != null && effective != schema) {
                schema = effective;
            }
        }

        // Handle $ref - resolve the reference to get actual schema
        if (schema.containsKey("$ref")) {
            return isRequired ? "@NotNull\n    " : "";
        }

        StringBuilder annotations = new StringBuilder();

        // Add @NotNull for required fields
        if (isRequired) {
            annotations.append("@NotNull\n    ");
        }

        String type = (String) schema.get("type");
        if (type == null) {
            return annotations.toString();
        }

        // Handle string validations
        switch (type) {
            case "string" -> {
                String format = (String) schema.get("format");
                if (!isOpenApiStringTemporalFormat(format)) {
                    // Email format
                    if ("email".equals(format)) {
                        annotations.append("@Email\n    ");
                    }

                    // Enum validation - must be checked before pattern to avoid conflicts
                    List<?> enumValues = schema.get("enum") instanceof List<?> list ? list : null;
                    boolean hasEnum = enumValues != null && !enumValues.isEmpty();

                    if (hasEnum && enumValues != null) {
                        StringBuilder enumPattern = new StringBuilder();
                        for (int i = 0; i < enumValues.size(); i++) {
                            if (i > 0) {
                                enumPattern.append("|");
                            }
                            String enumValue = enumValues.get(i).toString();
                            enumValue = enumValue.replace("\\", "\\\\")
                                    .replace("^", "\\^")
                                    .replace("$", "\\$")
                                    .replace(".", "\\.")
                                    .replace("|", "\\|")
                                    .replace("?", "\\?")
                                    .replace("*", "\\*")
                                    .replace("+", "\\+")
                                    .replace("(", "\\(")
                                    .replace(")", "\\)")
                                    .replace("[", "\\[")
                                    .replace("]", "\\]")
                                    .replace("{", "\\{")
                                    .replace("}", "\\}")
                                    .replace("\"", "\\\"");
                            enumPattern.append("(").append(enumValue).append(")");
                        }
                        String enumPatternStr = JerseyNamingUtils.escapePatternForJavaStringLiteral(enumPattern.toString());
                        annotations.append("@Pattern(regexp = \"").append(enumPatternStr).append("\")\n    ");
                    }

                    // Pattern validation (only if enum is not present, as enum takes precedence)
                    if (!hasEnum) {
                        Object patternObj = schema.get("pattern");
                        if (patternObj != null) {
                            String pattern = patternObj.toString();
                            annotations.append("@Pattern(regexp = \"").append(JerseyNamingUtils.escapePatternForJavaStringLiteral(pattern)).append("\")\n    ");
                        }
                    }

                    // Min and max length together (preferred)
                    Object minLengthObj = schema.get("minLength");
                    Object maxLengthObj = schema.get("maxLength");
                    if (minLengthObj != null && maxLengthObj != null) {
                        int minLength = getIntValue(minLengthObj);
                        int maxLength = getIntValue(maxLengthObj);
                        annotations.append("@Size(min = ").append(minLength).append(", max = ").append(maxLength).append(")\n    ");
                    } else {
                        if (minLengthObj != null) {
                            int minLength = getIntValue(minLengthObj);
                            if (minLength > 0) {
                                annotations.append("@Size(min = ").append(minLength).append(")\n    ");
                            }
                        }
                        if (maxLengthObj != null) {
                            int maxLength = getIntValue(maxLengthObj);
                            annotations.append("@Size(max = ").append(maxLength).append(")\n    ");
                        }
                    }
                }
            }

            case "integer" -> {
                String format = (String) schema.get("format");
                boolean isLongValue = "int64".equals(format);
                Object minimumObj = schema.get("minimum");
                if (minimumObj != null) {
                    long minimum = getLongValue(minimumObj);
                    boolean exclusiveMinimum = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                    if (exclusiveMinimum) {
                        ++minimum;
                    }
                    if(isLongValue){
                        annotations.append("@Min(value = ").append(minimum).append("L)\n    ");
                    }else{
                        annotations.append("@Min(value = ").append(minimum).append(")\n    ");
                    }
                }
                Object maximumObj = schema.get("maximum");
                if (maximumObj != null) {
                    long maximum = getLongValue(maximumObj);
                    boolean exclusiveMaximum = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                    if (exclusiveMaximum) {
                        --maximum;
                    }
                    if(isLongValue){
                        annotations.append("@Max(value = ").append(maximum).append("L)\n    ");
                    }else{
                        annotations.append("@Max(value = ").append(maximum).append(")\n    ");
                    }
                }
            }

            case "number" -> {
                Object minimumObj = schema.get("minimum");
                if (minimumObj != null) {
                    double minimum = getDoubleValue(minimumObj);
                    boolean exclusiveMinimum = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                    String minValue = String.format("%.10f", exclusiveMinimum ? minimum + Double.MIN_VALUE : minimum);
                    annotations.append("@DecimalMin(value = \"").append(minValue).append("\")\n    ");
                }
                Object maximumObj = schema.get("maximum");
                if (maximumObj != null) {
                    double maximum = getDoubleValue(maximumObj);
                    boolean exclusiveMaximum = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                    String maxValue = String.format("%.10f", exclusiveMaximum ? maximum - Double.MIN_VALUE : maximum);
                    annotations.append("@DecimalMax(value = \"").append(maxValue).append("\")\n    ");
                }
            }

            case "array" -> {
                Object minItemsObj = schema.get("minItems");
                Object maxItemsObj = schema.get("maxItems");
                if (minItemsObj != null && maxItemsObj != null) {
                    int minItems = getIntValue(minItemsObj);
                    int maxItems = getIntValue(maxItemsObj);
                    annotations.append("@Size(min = ").append(minItems).append(", max = ").append(maxItems).append(")\n    ");
                } else {
                    if (minItemsObj != null) {
                        int minItems = getIntValue(minItemsObj);
                        if (minItems > 0) {
                            annotations.append("@Size(min = ").append(minItems).append(")\n    ");
                        }
                    }
                    if (maxItemsObj != null) {
                        int maxItems = getIntValue(maxItemsObj);
                        annotations.append("@Size(max = ").append(maxItems).append(")\n    ");
                    }
                }
            }
        }

        return annotations.toString();
    }

    // ---------------------------------------------------------------------------
    //  Inline object property helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns true if the given property schema is an inline object (not a $ref, not a top-level schema, not composition-only).
     * Such properties will be generated as static inner classes of the parent model.
     */
    boolean isInlineObjectProperty(Map<String, Object> fieldSchema, Map<String, Object> spec) {
        if (fieldSchema == null || spec == null) return false;
        // Do not create inner class for $ref: use separate model class (x-resolved-ref set when parser resolves $ref)
        if (JerseySchemaUtils.getSchemaNameFromRef(fieldSchema) != null) return false;
        if (fieldSchema.containsKey("$ref")) return false;
        // Not a top-level schema: schema must not be the same object as any component schema
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null) {
                for (Object schemaObj : schemas.values()) {
                    if (schemaObj == fieldSchema) return false;
                }
            }
        }
        // Inline allOf overlay (e.g. IdentityPayload oneOf branch user/group with BasicUser ref + readOnly:false on id)
        if (fieldSchema.containsKey("allOf")) {
            if (JerseySchemaUtils.findComponentSchemaName(fieldSchema, spec) != null) {
                return false;
            }
            List<Map<String, Object>> allOf = Util.asStringObjectMapList(fieldSchema.get("allOf"));
            if (allOf != null && !allOf.isEmpty()
                    && JerseySchemaUtils.allOfHasPropertyOverlayBranches(allOf)) {
                return true;
            }
            // Fall through: envelope may still have type + properties alongside ref-only allOf
        }
        if (!"object".equals(fieldSchema.get("type"))) return false;
        Map<String, Object> props = Util.asStringObjectMap(fieldSchema.get("properties"));
        if (props == null || props.isEmpty()) return false;
        return true;
    }

    /**
     * Java class name for a static inner class representing an inline object property.
     * Uses the property name (key in parent schema); optional schema title is not used.
     */
    String getInnerClassNameForInlineProperty(String propertyName, Map<String, Object> fieldSchema) {
        if (propertyName != null && !propertyName.isEmpty()) {
            return JerseyNamingUtils.toJavaClassName(JerseyNamingUtils.capitalize(propertyName));
        }
        return "Inline";
    }

    private String resolveNumericType(String primitive, String wrapper, Map<String, Object> schema, boolean nullable) {
        if (ctx.isUseBoxedPrimitives() || schema.containsKey("default") || nullable) {
            return wrapper;
        }
        return primitive;
    }

    // ---------------------------------------------------------------------------
    //  Primitive / boxing checks
    // ---------------------------------------------------------------------------

    /**
     * Returns true if the given type is a Java primitive, boxed primitive, or String.
     * Used to decide when to add @Valid (add when this returns false).
     */
    boolean isJavaPrimitiveOrBoxed(String fieldType) {
        return switch (fieldType) {
            case "int", "long", "boolean", "double", "float", "short", "byte", "char",
                 "Integer", "Long", "Boolean", "Double", "Float", "Short", "Byte", "Character",
                 "String" -> true;
            default -> false;
        };
    }

    /** True if fieldType is a Java primitive (not boxed). Used for isSetXxx(): primitives use default-value check. */
    static boolean isJavaPrimitiveType(String fieldType) {
        return switch (fieldType) {
            case "int", "long", "boolean", "double", "float", "short", "byte", "char" -> true;
            default -> false;
        };
    }

    boolean isEligibleForCascadingValidation(String fieldType) {
        if (fieldType == null) return false;
        if ("XMLGregorianCalendar".equals(fieldType)) {
            return false;
        }
        // if fieldType starts with List< then check the inner type for eligibility
        if (fieldType.startsWith("List<") && fieldType.endsWith(">")) {
            String innerType = fieldType.substring(5, fieldType.length() - 1);
            if ("XMLGregorianCalendar".equals(innerType)) {
                return false;
            }
            return !isJavaPrimitiveOrBoxed(innerType);
        }
        // Add @Valid for non-primitive, non-boxed types (e.g. custom classes, lists)
        return !isJavaPrimitiveOrBoxed(fieldType);
    }

    // ---------------------------------------------------------------------------
    //  Error schema detection
    // ---------------------------------------------------------------------------

    /**
     * Check if schema is an error schema.
     */
    boolean isErrorSchema(String schemaName) {
        if (schemaName == null) {
            return false;
        }
        String lowerName = schemaName.toLowerCase(Locale.ROOT);
        return lowerName.contains("error") ||
                lowerName.contains("exception") ||
                lowerName.contains("fault");
    }

    // ---------------------------------------------------------------------------
    //  Number helpers
    // ---------------------------------------------------------------------------

    /** Helper method to safely get int value from Object */
    static int getIntValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Helper method to safely get long value from Object */
    static long getLongValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Helper method to safely get double value from Object */
    static double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
