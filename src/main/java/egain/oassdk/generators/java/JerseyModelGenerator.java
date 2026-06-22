package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Generates Java model classes (POJOs) from OpenAPI component schemas.
 * Each schema becomes a JAXB-annotated class with fields, getters/setters,
 * equals/hashCode/toString, and JAXBBean interface methods.
 */
class JerseyModelGenerator {

    private static final Logger logger = egain.oassdk.core.logging.LoggerConfig.getLogger(JerseyModelGenerator.class);

    private final JerseyGenerationContext ctx;
    private final JerseyTypeUtils typeUtils;
    private final JerseySchemaCollector schemaCollector;

    JerseyModelGenerator(JerseyGenerationContext ctx, JerseyTypeUtils typeUtils, JerseySchemaCollector schemaCollector) {
        this.ctx = ctx;
        this.typeUtils = typeUtils;
        this.schemaCollector = schemaCollector;
    }

    /**
     * Returns true when generating for standalone (open-source) mode, where proprietary
     * eGain platform classes (JAXBBean, CallerContext, etc.) are not available.
     * Controlled by the "standaloneMode" additional property in GeneratorConfig.
     */
    private boolean isStandaloneMode() {
        return ctx.config != null && ctx.config.getAdditionalProperties() != null
                && "true".equals(String.valueOf(ctx.config.getAdditionalProperties().get("standaloneMode")));
    }

    /**
     * Emit an {@code @Override} line for a JAXBBean interface method (getAttribute, setAttribute,
     * isSetAttribute, getAttributeNames) — but only in non-standalone mode. In standalone mode the
     * proprietary {@code JAXBBean} interface is not generated and the class does not implement it,
     * so these methods override nothing and {@code @Override} would fail to compile.
     */
    private void appendJaxbBeanOverride(StringBuilder content, String indent) {
        if (!isStandaloneMode()) {
            content.append(indent).append("@Override\n");
        }
    }

    private boolean legacyXorNestedIdAsserts() {
        return ctx.config != null && ctx.config.isLegacyXorNestedIdAsserts();
    }

    /** Holder for wrapper inner class to generate: outer property name, wrapper class name, inner property name, item type name. */
    static final class WrapperToGenerate {
        final String fieldName;
        final String wrapperClassName;
        final String innerPropertyName;
        final String itemTypeName;

        WrapperToGenerate(String fieldName, String wrapperClassName, String innerPropertyName, String itemTypeName) {
            this.fieldName = fieldName;
            this.wrapperClassName = wrapperClassName;
            this.innerPropertyName = innerPropertyName;
            this.itemTypeName = itemTypeName;
        }
    }

    // ---------------------------------------------------------------------------
    //  Main orchestrator
    // ---------------------------------------------------------------------------

    /**
     * Generate models.
     * Generates only schemas that are referenced (directly or transitively) from paths or components.
     * Skips schemas that are only used via allOf/oneOf/anyOf in other schemas.
     */
    void generateModels(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) return;

        String packagePath = packageName != null ? packageName : "com.example.api";

        // Collect all schema names referenced from paths (responses, requestBody, parameters) and components
        Set<String> allReferencedNames = new HashSet<>();
        try {
            allReferencedNames = schemaCollector.collectAllReferencedSchemaNames(spec);
        } catch (StackOverflowError e) {
            logger.warning("StackOverflow in collectAllReferencedSchemaNames, continuing with empty set");
        }
        // Fallback: when few or no refs were found, generate all schemas to avoid "very few models"
        int refCount = allReferencedNames.size();
        int schemaCount = schemas.size();
        boolean useFallback = refCount == 0
                || (schemaCount > 0 && refCount < schemaCount * 0.5)
                || (schemaCount > 10 && refCount < 10);
        if (useFallback) {
            allReferencedNames = new HashSet<>(schemas.keySet());
            logger.info("Few or no schema references found from paths/components; generating all schemas");
        }

        // Schemas referenced in responses (used to skip array types not referenced in any response)
        Set<String> schemasReferencedInResponses = schemaCollector.collectSchemasReferencedInResponses(spec);

        // Collect schemas that are only used via allOf/oneOf/anyOf
        Set<String> compositionOnlySchemas = schemaCollector.collectCompositionOnlySchemas(schemas);

        // Collect inline object schemas only from referenced top-level schemas
        schemaCollector.collectInlinedSchemasFromProperties(schemas, spec, allReferencedNames);

        // Set of top-level class names we generate (for ObjectFactory / jaxb.index)
        Set<String> generatedTopLevelClassNames = new HashSet<>();

        // Generate only referenced top-level schemas
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (schema == null) continue;

            // Skip schemas not referenced from paths or components
            if (!allReferencedNames.contains(schemaName)) {
                continue;
            }

            // Array types: only generate if referenced in a response (skip unreferenced array schemas)
            if (schema.containsKey("type") && "array".equals(schema.get("type"))) {
                if (!schemasReferencedInResponses.contains(schemaName)) {
                    continue;
                }
            }

            // Filter out error schemas
            if (typeUtils.isErrorSchema(schemaName)) {
                continue;
            }

            // Skip schemas that are only used via allOf/oneOf/anyOf AND are intermediate schemas
            if (compositionOnlySchemas.contains(schemaName)) {
                boolean isIntermediateSchema = schema.containsKey("allOf");
                if (isIntermediateSchema) {
                    continue;
                }
            }

            // Generate all schemas that have properties, allOf, oneOf, anyOf, or enum
            boolean hasStructure = schema.containsKey("properties") ||
                    schema.containsKey("allOf") ||
                    schema.containsKey("oneOf") ||
                    schema.containsKey("anyOf") ||
                    schema.containsKey("enum");

            if (!hasStructure && schema.containsKey("type")) {
                Object type = schema.get("type");
                if (type instanceof String typeStr) {
                    if ("array".equals(typeStr) && allReferencedNames.contains(schemaName)) {
                        // Generate model for array type that's referenced
                    } else if (!"object".equals(typeStr)) {
                        continue;
                    }
                }
            }

            String javaClassName = JerseyNamingUtils.toJavaClassName(schemaName);

            generatedTopLevelClassNames.add(javaClassName);
            generateModel(javaClassName, schema, outputDir, packagePath, spec);
            if (ctx.modelsOnly) {
                generateObjectFactory(javaClassName, outputDir, packagePath);
                generateJaxbIndex(javaClassName, outputDir, packagePath);
            }
        }

        // Generate models for in-lined schemas
        for (Map.Entry<Object, String> entry : ctx.getInlinedSchemas().entrySet()) {
            Object schemaObj = entry.getKey();
            String modelName = entry.getValue();

            Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
            if (schema != null) {
                generateModel(modelName, schema, outputDir, packagePath, spec);
                if (ctx.modelsOnly) {
                    generateObjectFactory(modelName, outputDir, packagePath);
                    generateJaxbIndex(modelName, outputDir, packagePath);
                }
            }
        }

        // When not models-only: single shared ObjectFactory and jaxb.index for all models
        if (!ctx.modelsOnly) {
            generateObjectFactory(generatedTopLevelClassNames, outputDir, packagePath);
            generateJaxbIndex(generatedTopLevelClassNames, outputDir, packagePath);
        }
    }

    // ---------------------------------------------------------------------------
    //  Single model generation
    // ---------------------------------------------------------------------------

    /**
     * Generate individual model.
     */
    void generateModel(String schemaName, Map<String, Object> schema, String outputDir, String packagePath, Map<String, Object> spec) throws IOException {
        StringBuilder content = new StringBuilder();

        // Build allProperties and fieldNames first (needed for propOrder and for collecting imports)
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();

        // Check if this is an array type schema
        boolean isArrayType = schema.containsKey("type") && "array".equals(schema.get("type"));

        if (isArrayType) {
            Object itemsObj = schema.get("items");
            if (itemsObj != null) {
                Map<String, Object> itemsSchema = Util.asStringObjectMap(itemsObj);
                if (itemsSchema != null) {
                    allProperties.put("items", itemsSchema);
                    allRequired.add("items");
                }
            }
        } else {
            if (schema.containsKey("allOf")) {
                List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
                JerseySchemaUtils.mergeAllOfBranchesIntoProperties(allOfSchemas, allProperties, allRequired, spec);
            } else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
                List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                        schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
                for (Map<String, Object> subSchema : schemas) {
                    JerseySchemaUtils.mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
                }
            } else {
                JerseySchemaUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec);
            }
        }

        // Simple two-branch oneOf: each branch requires exactly one distinct property — merge wrongly unionizes
        // required/readOnly; strip those for the pair and enforce XOR via @AssertTrue on the bean.
        Set<String> xorExclusiveJsonNames = Collections.emptySet();
        String[] oneOfXorPair = null;
        JerseySchemaOneOfXor.SimpleOneOfXorInfo oneOfXorInfo = null;
        if (!isArrayType) {
            oneOfXorInfo = JerseySchemaOneOfXor.findSimpleOneOfXorInfo(
                    schema, spec, new IdentityHashMap<>(), 0);
            if (oneOfXorInfo != null) {
                oneOfXorPair = new String[] { oneOfXorInfo.sortedJson0(), oneOfXorInfo.sortedJson1() };
                xorExclusiveJsonNames = Set.of(oneOfXorPair[0], oneOfXorPair[1]);
                allRequired.removeIf(xorExclusiveJsonNames::contains);
            }
        }

        List<String> fieldNames = new ArrayList<>(allProperties.keySet());

        // Wrappers to generate as static inner classes (object-with-single-array properties)
        List<WrapperToGenerate> wrappersToGenerate = new ArrayList<>();
        // Inline object properties to generate as static inner classes (propertyName -> fieldSchema)
        List<Map.Entry<String, Map<String, Object>>> innerClassesToGenerate = new ArrayList<>();

        // Collect referenced model types for imports (use wrapper class name for parent field; add item type for inner class)
        Set<String> modelImports = new LinkedHashSet<>();
        Set<String> innerClasses = new HashSet<>();
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
            // Only generate inline inner class for true inline objects; object-with-single-array uses wrapper inner class
            if (typeUtils.isInlineObjectProperty(fieldSchema, spec) && !JerseySchemaUtils.isObjectWithSingleArrayOfRef(fieldSchema, spec)) {
                String innerClassName = schemaName + "_" + typeUtils.getInnerClassNameForInlineProperty(fieldName, fieldSchema);
                if(!innerClasses.contains(innerClassName.toLowerCase(Locale.ENGLISH)))
                {
                    innerClassesToGenerate.add(new AbstractMap.SimpleEntry<>(fieldName, fieldSchema));
                    innerClasses.add(innerClassName.toLowerCase(Locale.ENGLISH));
                }
            }
            String fieldType = typeUtils.getFieldTypeForModelProperty(schemaName, fieldName, fieldSchema, isArrayType, spec);
            if (JerseySchemaUtils.isObjectWithSingleArrayOfRef(fieldSchema, spec) && JerseySchemaUtils.getSchemaNameFromRef(fieldSchema) == null) {
                JerseySchemaUtils.ObjectWithSingleArrayInfo info = JerseySchemaUtils.getObjectWithSingleArrayInfo(fieldSchema, spec);
                if (info != null) {
                    wrappersToGenerate.add(new WrapperToGenerate(fieldName, JerseySchemaUtils.getWrapperClassName(fieldName), info.innerPropertyName, info.itemTypeName));
                    typeUtils.addModelImportTypes("List<" + info.itemTypeName + ">", schemaName, modelImports);
                }
            } else {
                typeUtils.addModelImportTypes(fieldType, schemaName, modelImports);
            }

            // Collect imports from inner classes
            for (Map.Entry<String, Map<String, Object>> innerClassEntry : innerClassesToGenerate) {
                String innerClassName2 = typeUtils.getInnerClassNameForInlineProperty(innerClassEntry.getKey(), innerClassEntry.getValue());
                Map<String, Object> innerClassSchema = innerClassEntry.getValue();
                Map<String, Object> innerProps = Util.asStringObjectMap(innerClassSchema.get("properties"));
                if (innerProps != null) {
                    for (Map.Entry<String, Object> innerProp : innerProps.entrySet()) {
                        String innerFieldType = typeUtils.getFieldTypeForModelProperty(innerClassName2, innerProp.getKey(), Util.asStringObjectMap(innerProp.getValue()), false, spec);
                        if(innerFieldType!=null && !innerFieldType.contains(".") && !innerFieldType.equals(innerClassName2) && !innerFieldType.equals(schemaName))
                        {
                            typeUtils.addModelImportTypes(innerFieldType, schemaName, modelImports);
                        }
                    }
                }
            }
        }

        content.append("package ").append(packagePath).append(ctx.modelsOnly?"."+JerseyNamingUtils.sanitizePackageName(schemaName)+";\n\n":".model;\n\n");
        boolean includeJaxbBean = !isStandaloneMode();
        if (includeJaxbBean) {
            content.append("import com.egain.platform.common.JAXBBean;\n");
        }
        content.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        content.append("import ").append(ctx.validationNs).append(".constraints.*;\n");
        content.append("import ").append(ctx.validationNs).append(".Valid;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".annotation.*;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".JAXBElement;\n");
        content.append("import javax.xml.namespace.QName;\n");
        content.append("import java.io.Serializable;\n");
        content.append("import java.util.Objects;\n");
        content.append("import java.util.List;\n");
        content.append("import java.util.ArrayList;\n");
        content.append("import java.util.Map;\n");
        content.append("import java.util.HashMap;\n");
        content.append("import javax.xml.datatype.XMLGregorianCalendar;\n");
        for (String typeName : modelImports) {
            content.append("import ").append(packagePath).append(ctx.modelsOnly?"."+JerseyNamingUtils.sanitizePackageName(typeName)+".":".model.").append(typeName).append(";\n");
        }
        content.append("\n");

        // Add JAXB annotations
        content.append("@XmlRootElement(name = \"").append(schemaName).append("\")\n");
        content.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
        content.append("@XmlType(name = \"").append(schemaName).append("\", propOrder = {\n");

        if (!fieldNames.isEmpty()) {
            for (int i = 0; i < fieldNames.size(); i++) {
                content.append("    \"").append(JerseyNamingUtils.toPropOrderName(JerseyNamingUtils.toModelFieldName(fieldNames.get(i)))).append("\"");
                if (i < fieldNames.size() - 1) {
                    content.append(",\n");
                } else {
                    content.append("\n");
                }
            }
        }
        content.append("})\n");

        if (includeJaxbBean) {
            content.append("public class ").append(schemaName).append(" implements Serializable, JAXBBean {\n\n");
        } else {
            content.append("public class ").append(schemaName).append(" implements Serializable {\n\n");
        }

        // Add serialVersionUID for Serializable
        content.append("    private static final long serialVersionUID = 1L;\n\n");

        // Add field for dynamic attributes (excluded from JAXB binding) - only when JAXBBean is included
        if (includeJaxbBean) {
            content.append("    @XmlTransient\n");
            content.append("    private Map<String, Object> _attributes;\n\n");
        }

        // Simple two-branch oneOf: emit XOR + nested id @AssertTrue methods right after the last XOR field
        StringBuilder oneOfXorAssertBlock = null;
        String injectXorAssertsAfterField = null;
        if (oneOfXorPair != null && oneOfXorPair.length == 2 && oneOfXorInfo != null) {
            String xorJson0 = oneOfXorPair[0];
            String xorJson1 = oneOfXorPair[1];
            Map<String, Object> xorSchema0 = Util.asStringObjectMap(allProperties.get(xorJson0));
            Map<String, Object> xorSchema1 = Util.asStringObjectMap(allProperties.get(xorJson1));
            String xorType0 = typeUtils.getFieldTypeForModelProperty(schemaName, xorJson0, xorSchema0, isArrayType, spec);
            String xorType1 = typeUtils.getFieldTypeForModelProperty(schemaName, xorJson1, xorSchema1, isArrayType, spec);
            if (!JerseyTypeUtils.isJavaPrimitiveType(xorType0) && !JerseyTypeUtils.isJavaPrimitiveType(xorType1)) {
                oneOfXorAssertBlock = new StringBuilder();
                String xorJava0 = JerseyNamingUtils.toModelFieldName(xorJson0);
                String xorJava1 = JerseyNamingUtils.toModelFieldName(xorJson1);
                String xorMessage = "Either " + xorJson0 + " or " + xorJson1 + " must be set";
                oneOfXorAssertBlock.append("    @XmlTransient\n");
                oneOfXorAssertBlock.append("    @Valid\n");
                oneOfXorAssertBlock.append("    @AssertTrue(message = \"").append(JerseyNamingUtils.escapeJavaString(xorMessage)).append("\")\n");
                oneOfXorAssertBlock.append("    public boolean isValidRequiredMutuallyExclusive() {\n");
                oneOfXorAssertBlock.append("        return (this.").append(xorJava0).append(" != null) ^ (this.").append(xorJava1).append(" != null);\n");
                oneOfXorAssertBlock.append("    }\n\n");
                appendOneOfXorNestedIdAssertTrue(oneOfXorAssertBlock, oneOfXorInfo.sortedJson0(), oneOfXorInfo.nestedIdRequiredForSorted0(),
                        schemaName, allProperties, isArrayType, spec, legacyXorNestedIdAsserts());
                appendOneOfXorNestedIdAssertTrue(oneOfXorAssertBlock, oneOfXorInfo.sortedJson1(), oneOfXorInfo.nestedIdRequiredForSorted1(),
                        schemaName, allProperties, isArrayType, spec, legacyXorNestedIdAsserts());
                for (String fn : fieldNames) {
                    if (xorExclusiveJsonNames.contains(fn)) {
                        injectXorAssertsAfterField = fn;
                    }
                }
            }
        }

        // Generate fields
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

            content.append("    ");

            // Add JAXB @XmlElement annotation
            String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
            String fieldType = typeUtils.getFieldTypeForModelProperty(schemaName, fieldName, fieldSchema, isArrayType, spec);
            boolean isWrapperType = JerseySchemaUtils.isObjectWithSingleArrayOfRef(fieldSchema, spec);

            // Wrapper type: single @XmlElement(name=fieldName). Direct list: @XmlElementWrapper + @XmlElement(). Else: @XmlElement(name=fieldName).
            if (isWrapperType) {
                content.append("@XmlElement(name = \"").append(fieldName).append("\")\n    ");
            } else if (fieldType.startsWith("List<")) {
                content.append("@XmlElementWrapper(name = \"").append(fieldName).append("\")\n    ");
                content.append("@XmlElement()\n    ");
                if (isArrayType && fieldName.equals("items") && schema.containsKey("maxItems")) {
                    Object maxItems = schema.get("maxItems");
                    content.append("@Size(max = ").append(maxItems).append(")\n    ");
                }
            } else {
                content.append("@XmlElement(name = \"").append(fieldName).append("\"");
                boolean effectiveRequired = allRequired.contains(fieldName)
                        && !xorExclusiveJsonNames.contains(fieldName);
                if (effectiveRequired) {
                    content.append(", required = true");
                }
                content.append(")\n    ");
            }

            // Add @JsonProperty for name mapping and/or readOnly/writeOnly access
            appendJsonPropertyAccessAnnotation(content, "    ", fieldName, javaFieldName, fieldSchema,
                    xorExclusiveJsonNames.contains(fieldName));

            // Add validation annotations based on schema constraints
            boolean effectiveFieldRequired = allRequired.contains(fieldName)
                    && !xorExclusiveJsonNames.contains(fieldName);
            String validationAnnotations = typeUtils.generateValidationAnnotations(fieldSchema, effectiveFieldRequired);
            if (!validationAnnotations.isEmpty()) {
                content.append(validationAnnotations);
            }

            // Add @Valid for non-primitive/non-boxed types (nested object validation)
            if (typeUtils.isEligibleForCascadingValidation(fieldType)) {
                content.append("@Valid\n    ");
            }

            // Add field type and name
            content.append("private ").append(fieldType).append(" ").append(javaFieldName).append(";\n\n");

            if (oneOfXorAssertBlock != null && fieldName.equals(injectXorAssertsAfterField)) {
                content.append(oneOfXorAssertBlock);
            }
        }

        // Generate default constructor
        content.append("    public ").append(schemaName).append("() {\n");
        content.append("    }\n\n");

        // Generate getters and setters
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
            String fieldType = typeUtils.getFieldTypeForModelProperty(schemaName, fieldName, fieldSchema, isArrayType, spec);

            String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
            String capitalizedFieldName = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaFieldName);

            boolean isListTypeField = fieldType.startsWith("List<");

            String methodPrefix = fieldType.equals("boolean") || fieldType.equals("Boolean") ? "is" : "get";
            content.append("    public ").append(fieldType).append(" " + methodPrefix).append(capitalizedFieldName).append("() {\n");
            if (isListTypeField) {
                content.append("        if (").append(javaFieldName).append(" == null) {\n");
                content.append("            ").append(javaFieldName).append(" = new Array").append(fieldType).append("();\n");
                content.append("        }\n");
                content.append("        return this.").append(javaFieldName).append(";\n");
            } else {
                content.append("        return ").append(javaFieldName).append(";\n");
            }
            content.append("    }\n\n");

            if (!isListTypeField) {
                content.append("    public void set").append(capitalizedFieldName).append("(").append(fieldType).append(" ").append(javaFieldName).append(") {\n");
                content.append("        this.").append(javaFieldName).append(" = ").append(javaFieldName).append(";\n");
                content.append("    }\n\n");
            }

            // isSetXxx()
            if (isListTypeField) {
                content.append("    public boolean isSet").append(capitalizedFieldName).append("() {\n");
                content.append("        return (this.").append(javaFieldName).append(" != null) && (!this.").append(javaFieldName).append(".isEmpty());\n");
                content.append("    }\n\n");
                content.append("    public void unset").append(capitalizedFieldName).append("() {\n");
                content.append("        this.").append(javaFieldName).append(" = null;\n");
                content.append("    }\n\n");
            } else {
                content.append("    public boolean isSet").append(capitalizedFieldName).append("() {\n");
                if (JerseyTypeUtils.isJavaPrimitiveType(fieldType)) {
                    content.append("        return true;\n");
                } else {
                    content.append("        return (this.").append(javaFieldName).append(" != null);\n");
                }
                content.append("    }\n\n");
            }
        }

        // When isModelsOnly, skip equals/hashCode/toString
        if (!ctx.modelsOnly) {
            // Generate equals method
            content.append("    @Override\n");
            content.append("    public boolean equals(Object o) {\n");
            content.append("        if (this == o) return true;\n");
            content.append("        if (o == null || getClass() != o.getClass()) return false;\n");
            content.append("        ").append(schemaName).append(" that = (").append(schemaName).append(") o;\n");
            content.append("        return ");

            if (!fieldNames.isEmpty()) {
                for (int i = 0; i < fieldNames.size(); i++) {
                    if (i > 0) content.append("                ");
                    String modelField = JerseyNamingUtils.toModelFieldName(fieldNames.get(i));
                    content.append("Objects.equals(").append(modelField).append(", that.").append(modelField).append(")");
                    if (i < fieldNames.size() - 1) {
                        content.append(" &&\n");
                    } else {
                        content.append(";\n");
                    }
                }
            } else {
                content.append("true;\n");
            }
            content.append("    }\n\n");

            // Generate hashCode method
            content.append("    @Override\n");
            content.append("    public int hashCode() {\n");
            content.append("        return Objects.hash(");
            if (!fieldNames.isEmpty()) {
                for (int i = 0; i < fieldNames.size(); i++) {
                    if (i > 0) content.append(", ");
                    content.append(JerseyNamingUtils.toModelFieldName(fieldNames.get(i)));
                }
            }
            content.append(");\n");
            content.append("    }\n\n");

            // Generate toString method
            content.append("    @Override\n");
            content.append("    public String toString() {\n");
            content.append("        return \"").append(schemaName).append("{\" +\n");

            if (!fieldNames.isEmpty()) {
                for (int i = 0; i < fieldNames.size(); i++) {
                    String fieldName = fieldNames.get(i);
                    String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
                    content.append("                \"").append(javaFieldName).append("=\" + ").append(javaFieldName);
                    if (i < fieldNames.size() - 1) {
                        content.append(" + \", \" +\n");
                    } else {
                        content.append(" +\n");
                    }
                }
            }

            content.append("                '}';\n");
            content.append("    }\n\n");
        }

        // Generate JAXBBean interface methods (only when JAXBBean is included)
        if (includeJaxbBean) {
        content.append("    @Override\n");
        content.append("    public Object getAttribute(String name) {\n");
        if (!fieldNames.isEmpty()) {
            content.append("        if (_attributes != null && _attributes.containsKey(name)) {\n");
            content.append("            return _attributes.get(name);\n");
            content.append("        }\n");
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                Map<String, Object> fieldSchema = Util.asStringObjectMap(allProperties.get(fieldName));
                String fieldType = typeUtils.getFieldTypeForModelProperty(schemaName, fieldName, fieldSchema, isArrayType, spec);
                String methodPrefix = fieldType.equals("boolean") || fieldType.equals("Boolean") ? "is" : "get";
                String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
                String capitalizedFieldName = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaFieldName);
                content.append("            case \"").append(fieldName).append("\":\n");
                content.append("                return ").append(methodPrefix).append(capitalizedFieldName).append("();\n");
            }
            content.append("            default:\n");
            content.append("                return null;\n");
            content.append("        }\n");
        } else {
            content.append("        if (_attributes == null) return null;\n");
            content.append("        return _attributes.get(name);\n");
        }
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public boolean isSetAttribute(String name) {\n");
        content.append("        if (_attributes != null && _attributes.containsKey(name)) {\n");
        content.append("            return true;\n");
        content.append("        }\n");
        if (!fieldNames.isEmpty()) {
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
                String capitalizedFieldName = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaFieldName);
                content.append("            case \"").append(fieldName).append("\":\n");
                content.append("                return isSet").append(capitalizedFieldName).append("();\n");
            }
            content.append("            default:\n");
            content.append("                return false;\n");
            content.append("        }\n");
        } else {
            content.append("        return false;\n");
        }
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public List<String> getAttributeNames() {\n");
        content.append("        List<String> allNames = new ArrayList<>();\n");
        content.append("        if (_attributes != null) {\n");
        content.append("            allNames.addAll(_attributes.keySet());\n");
        content.append("        }\n");
        if (!fieldNames.isEmpty()) {
            for (String fieldName : fieldNames) {
                content.append("        allNames.add(\"").append(fieldName).append("\");\n");
            }
        }
        content.append("        return allNames;\n");
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public void setAttribute(String name, Object value) {\n");
        if (!fieldNames.isEmpty()) {
            content.append("        switch (name) {\n");
            for (String fieldName : fieldNames) {
                String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
                String capitalizedFieldName = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaFieldName);
                Map<String, Object> fieldSchema = Util.asStringObjectMap(allProperties.get(fieldName));
                String fieldType;

                if (isArrayType && "items".equals(fieldName)) {
                    String itemType = null;
                    if (fieldSchema != null) {
                        if (fieldSchema.containsKey("$ref")) {
                            String ref = (String) fieldSchema.get("$ref");
                            if (ref != null && ref.startsWith("#/components/schemas/")) {
                                String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
                                itemType = JerseyNamingUtils.toJavaClassName(schemaRef);
                            }
                        }
                        if (itemType == null || itemType.isEmpty()) {
                            Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                            if (components != null) {
                                Map<String, Object> schemas2 = Util.asStringObjectMap(components.get("schemas"));
                                if (schemas2 != null) {
                                    for (Map.Entry<String, Object> schemaEntry : schemas2.entrySet()) {
                                        Map<String, Object> candidateSchema = Util.asStringObjectMap(schemaEntry.getValue());
                                        if (candidateSchema == fieldSchema) {
                                            itemType = JerseyNamingUtils.toJavaClassName(schemaEntry.getKey());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (itemType == null || itemType.isEmpty()) {
                            itemType = typeUtils.getJavaType(fieldSchema);
                            if ("Object".equals(itemType) && fieldSchema.containsKey("$ref")) {
                                String ref = (String) fieldSchema.get("$ref");
                                if (ref != null && ref.startsWith("#/components/schemas/")) {
                                    String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
                                    itemType = JerseyNamingUtils.toJavaClassName(schemaRef);
                                }
                            }
                        }
                    }
                    if (itemType == null || itemType.isEmpty()) {
                        itemType = "Object";
                    }
                    fieldType = "List<" + itemType + ">";
                } else {
                    fieldType = typeUtils.getFieldTypeForModelProperty(schemaName, fieldName, fieldSchema, isArrayType, spec);
                }
                content.append("            case \"").append(fieldName).append("\":\n");
                if (fieldType.startsWith("List<")) {
                    String itemType = fieldType.substring(5, fieldType.length() - 1);
                    content.append("                get").append(capitalizedFieldName).append("().add((").append(
                                    itemType).append(") value);\n");
                } else {
                    content.append("                set").append(capitalizedFieldName).append("((").append(
                                    fieldType).append(") value);\n");
                }
                content.append("                return;\n");
            }
            content.append("            default:\n");
            content.append("                if (_attributes == null) {\n");
            content.append("                    _attributes = new HashMap<>();\n");
            content.append("                }\n");
            content.append("                _attributes.put(name, value);\n");
            content.append("                break;\n");
            content.append("        }\n");
        } else {
            content.append("        if (_attributes == null) {\n");
            content.append("            _attributes = new HashMap<>();\n");
            content.append("        }\n");
            content.append("        _attributes.put(name, value);\n");
        }
        content.append("    }\n");
        } // end JAXBBean interface methods

        // Generate static inner classes for inline object properties
        for (Map.Entry<String, Map<String, Object>> entry : innerClassesToGenerate) {
            appendInnerClassForInlineObject(content, schemaName, entry.getKey(), entry.getValue(), spec);
        }

        // Generate static inner wrapper classes (object-with-single-array)
        for (WrapperToGenerate w : wrappersToGenerate) {
            appendWrapperInnerClass(content, w, schemaName);
        }

        content.append("}\n");

        JerseyGenerationContext.writeFile(outputDir + (ctx.modelsOnly?"/":"/src/main/java/") + packagePath.replace(".", "/") + (ctx.modelsOnly?"/"+JerseyNamingUtils.sanitizePackageName(schemaName)+"/":"/model/") + schemaName + ".java", content.toString());
    }

    /**
     * Emit {@code @AssertTrue} for nested {@code id} when a simple XOR {@code oneOf} branch marks that object with
     * {@code required: [id]}. Predicates prefer OpenAPI {@code id} type over inferred Java type so merged refs cannot
     * force a wrong numeric check on string IDs. Skips when {@code id} is primitive {@code boolean}.
     */
    private void appendOneOfXorNestedIdAssertTrue(
            StringBuilder content,
            String xorJsonName,
            boolean nestedIdRequired,
            String schemaName,
            Map<String, Object> allProperties,
            boolean isArrayType,
            Map<String, Object> spec,
            boolean legacyXorNestedIdAsserts) {
        if (!nestedIdRequired) {
            return;
        }
        Map<String, Object> xorSchema = Util.asStringObjectMap(allProperties.get(xorJsonName));
        if (xorSchema == null) {
            return;
        }
        Map<String, Object> resolvedXor = JerseySchemaUtils.resolveRefInSchema(xorSchema, spec);
        Map<String, Object> effectiveXor = JerseySchemaUtils.resolveCompositionToEffectiveSchema(resolvedXor, spec);
        Map<String, Object> innerProps = Util.asStringObjectMap(effectiveXor != null ? effectiveXor.get("properties") : null);
        Map<String, Object> idSchema = Util.asStringObjectMap(innerProps != null ? innerProps.get("id") : null);
        if (idSchema == null) {
            return;
        }
        Map<String, Object> idEff = JerseySchemaUtils.resolveCompositionToEffectiveSchema(idSchema, spec);
        if (idEff == null) {
            idEff = idSchema;
        }
        String idJavaType = typeUtils.getFieldTypeForModelProperty(schemaName, "id", idEff, false, spec);
        String javaField = JerseyNamingUtils.toModelFieldName(xorJsonName);
        String openApiType = openApiScalarType(idEff);
        String returnClause = nestedXorNestedIdReturnClause(javaField, xorJsonName, idJavaType, openApiType, legacyXorNestedIdAsserts);
        if (returnClause == null) {
            return;
        }
        String methodCap = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaField);
        String msg = legacyXorNestedIdAsserts
                ? xorJsonName + ".id must be set when " + xorJsonName + " attribute is set"
                : "If " + xorJsonName + " is set then " + xorJsonName + ".id must be set";
        content.append("    @XmlTransient\n");
        content.append("    @Valid\n");
        content.append("    @AssertTrue(message = \"").append(JerseyNamingUtils.escapeJavaString(msg)).append("\")\n");
        content.append("    public boolean isValid").append(methodCap).append("() {\n");
        content.append("        return ").append(returnClause).append(";\n");
        content.append("    }\n\n");
    }

    private static String openApiScalarType(Map<String, Object> idEff) {
        if (idEff == null) {
            return null;
        }
        Object t = idEff.get("type");
        return t instanceof String ? (String) t : null;
    }

    /**
     * @return {@code null} when no {@code @AssertTrue} should be emitted (e.g. primitive {@code boolean} id).
     */
    static String nestedXorNestedIdReturnClause(
            String javaField,
            String xorJsonName,
            String idJavaType,
            String openApiType,
            boolean legacyXorNestedIdAsserts) {
        if (legacyXorNestedIdAsserts) {
            return legacyXorNestedIdReturnClause(javaField, xorJsonName, idJavaType, openApiType);
        }
        if ("string".equals(openApiType) || "String".equals(idJavaType)) {
            return "this." + javaField + " == null || this." + javaField + ".isSetId()";
        }
        if (JerseyTypeUtils.isJavaPrimitiveType(idJavaType)) {
            if ("boolean".equals(idJavaType)) {
                return null;
            }
            String idGetter = "get" + JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName("id"));
            String nonNullBranch = "this." + javaField + "." + idGetter + "()";
            String comparison = switch (idJavaType) {
                case "float" -> nonNullBranch + " != 0.0f";
                case "double" -> nonNullBranch + " != 0.0";
                case "int", "long", "short", "byte", "char" -> nonNullBranch + " != 0";
                default -> null;
            };
            if (comparison == null) {
                return null;
            }
            return "this." + javaField + " == null || " + comparison;
        }
        String boxed = boxedNumericIdPresentClause(javaField, idJavaType);
        if (boxed != null) {
            return boxed;
        }
        return "this." + javaField + " == null || this." + javaField + ".isSetId()";
    }

    /**
     * Legacy eGain-style XOR nested-id predicates ({@code GeneratorConfig.isLegacyXorNestedIdAsserts()}).
     * Note: the {@code parent} branch matches historical bindings; it inverts usual {@code @AssertTrue} semantics.
     */
    private static String legacyXorNestedIdReturnClause(String javaField, String xorJsonName, String idJavaType, String openApiType) {
        if ("parent".equals(xorJsonName)) {
            return "(this." + javaField + " != null && !this." + javaField + ".isSetId())";
        }
        if ("string".equals(openApiType) || "String".equals(idJavaType)) {
            return "(this." + javaField + " != null && !this." + javaField + ".isSetId())";
        }
        if (JerseyTypeUtils.isJavaPrimitiveType(idJavaType)) {
            if ("boolean".equals(idJavaType)) {
                return null;
            }
            String idGetter = "get" + JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName("id"));
            return "(this." + javaField + " != null && this." + javaField + "." + idGetter + "() == 0)";
        }
        if ("integer".equals(openApiType) || "number".equals(openApiType)
                || "Integer".equals(idJavaType) || "Long".equals(idJavaType) || "Short".equals(idJavaType) || "Byte".equals(idJavaType)
                || "Double".equals(idJavaType) || "Float".equals(idJavaType)) {
            String idGetter = "get" + JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName("id"));
            return "(this." + javaField + " != null && (this." + javaField + "." + idGetter + "() == null || this." + javaField + "." + idGetter + "() == 0))";
        }
        return "(this." + javaField + " != null && !this." + javaField + ".isSetId())";
    }

    /**
     * @return {@code null} when {@code idJavaType} is not a boxed numeric type we can compare to zero.
     */
    private static String boxedNumericIdPresentClause(String javaField, String idJavaType) {
        if (idJavaType == null) {
            return null;
        }
        String idGetter = "get" + JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName("id"));
        String base = "this." + javaField + " == null || this." + javaField + "." + idGetter + "() == null || ";
        return switch (idJavaType) {
            case "Integer", "Short", "Byte" -> base + "this." + javaField + "." + idGetter + "() != 0";
            case "Long" -> base + "this." + javaField + "." + idGetter + "() != 0L";
            case "Double" -> base + "this." + javaField + "." + idGetter + "() != 0.0";
            case "Float" -> base + "this." + javaField + "." + idGetter + "() != 0.0f";
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------
    //  Inner class generation
    // ---------------------------------------------------------------------------

    /**
     * Emit {@code @JsonProperty} when JSON name differs from the Java field or when {@code readOnly}/{@code writeOnly}
     * access must be reflected in Jackson annotations.
     */
    private static void appendJsonPropertyAccessAnnotation(
            StringBuilder content,
            String indent,
            String fieldName,
            String javaFieldName,
            Map<String, Object> fieldSchema,
            boolean skipReadOnly) {
        boolean readOnly = JerseySchemaUtils.isSchemaFlagTrue(fieldSchema, "readOnly") && !skipReadOnly;
        boolean writeOnly = JerseySchemaUtils.isSchemaFlagTrue(fieldSchema, "writeOnly");
        if (readOnly && writeOnly) {
            writeOnly = false;
        }
        String accessStr = null;
        if (readOnly) {
            accessStr = "READ_ONLY";
        } else if (writeOnly) {
            accessStr = "WRITE_ONLY";
        }
        boolean needName = !fieldName.equals(javaFieldName);
        if (!needName && accessStr == null) {
            return;
        }
        content.append(indent).append("@JsonProperty(");
        if (needName) {
            content.append("value = \"").append(fieldName).append("\"");
        }
        if (needName && accessStr != null) {
            content.append(", ");
        }
        if (accessStr != null) {
            content.append("access = JsonProperty.Access.").append(accessStr);
        }
        content.append(")\n").append(indent);
    }

    /**
     * Append a static inner class for an inline object property.
     */
    void appendInnerClassForInlineObject(StringBuilder content, String enclosingClassName, String propertyName, Map<String, Object> innerSchema, Map<String, Object> spec) {
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();
        if (innerSchema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(innerSchema.get("allOf"));
            JerseySchemaUtils.mergeAllOfBranchesIntoProperties(allOfSchemas, allProperties, allRequired, spec);
            List<String> envelopeRequired = Util.asStringList(innerSchema.get("required"));
            if (envelopeRequired != null) {
                for (String r : envelopeRequired) {
                    if (!allRequired.contains(r)) {
                        allRequired.add(r);
                    }
                }
            }
            Map<String, Object> envelopeProps = Util.asStringObjectMap(innerSchema.get("properties"));
            if (envelopeProps != null && !envelopeProps.isEmpty()) {
                JerseySchemaUtils.mergePropertiesIntoAll(allProperties, envelopeProps);
            }
        } else if (innerSchema.containsKey("oneOf") || innerSchema.containsKey("anyOf")) {
            List<Map<String, Object>> schemasList = Util.asStringObjectMapList(
                    innerSchema.containsKey("oneOf") ? innerSchema.get("oneOf") : innerSchema.get("anyOf"));
            if (schemasList != null) {
                for (Map<String, Object> subSchema : schemasList) {
                    if (subSchema != null) JerseySchemaUtils.mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
                }
            }
        } else {
            JerseySchemaUtils.mergeSchemaProperties(innerSchema, allProperties, allRequired, spec);
        }
        List<String> fieldNames = new ArrayList<>(allProperties.keySet());
        String innerClassName = typeUtils.getInnerClassNameForInlineProperty(propertyName, innerSchema);
        String fullEnclosing = enclosingClassName + "." + innerClassName;

        String indentClass = enclosingClassName.contains(".") ? "        " : "    ";
        String indentBody = indentClass + "    ";

        List<Map.Entry<String, Map<String, Object>>> nestedInners = new ArrayList<>();
        for (Map.Entry<String, Object> prop : allProperties.entrySet()) {
            Map<String, Object> propSchema = Util.asStringObjectMap(prop.getValue());
            if (typeUtils.isInlineObjectProperty(propSchema, spec)) {
                nestedInners.add(new AbstractMap.SimpleEntry<>(prop.getKey(), propSchema));
            }
        }

        content.append("\n").append(indentClass).append("@XmlAccessorType(XmlAccessType.FIELD)\n");
        content.append(indentClass).append("@XmlType(name = \"\", propOrder = {\n");
        for (int i = 0; i < fieldNames.size(); i++) {
            content.append(indentBody).append("\"").append(JerseyNamingUtils.toPropOrderName(JerseyNamingUtils.toModelFieldName(fieldNames.get(i)))).append("\"");
            content.append(i < fieldNames.size() - 1 ? ",\n" : "\n");
        }
        content.append(indentClass).append("})\n");
        if (!isStandaloneMode()) {
            content.append(indentClass).append("public static class ").append(innerClassName).append(" implements Serializable, JAXBBean {\n\n");
        } else {
            content.append(indentClass).append("public static class ").append(innerClassName).append(" implements Serializable {\n\n");
        }
        content.append(indentBody).append("private static final long serialVersionUID = 1L;\n\n");

        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
            String fieldType = typeUtils.getFieldTypeForModelProperty(fullEnclosing, fieldName, fieldSchema, false, spec);
            String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
            content.append(indentBody);
            content.append("@XmlElement(name = \"").append(fieldName).append("\"");
            if (allRequired.contains(fieldName)) content.append(", required = true");
            content.append(")\n").append(indentBody);
            appendJsonPropertyAccessAnnotation(content, indentBody, fieldName, javaFieldName, fieldSchema, false);
            String validationAnnotations = typeUtils.generateValidationAnnotations(fieldSchema, allRequired.contains(fieldName));
            if (!validationAnnotations.isEmpty()) {
                content.append(validationAnnotations.replace("\n    ", "\n" + indentBody));
            }
            if (typeUtils.isEligibleForCascadingValidation(fieldType)) {
                content.append("@Valid\n").append(indentBody);
            }
            content.append("private ").append(fieldType).append(" ").append(javaFieldName).append(";\n\n");
        }

        content.append(indentBody).append("public ").append(innerClassName).append("() {\n");
        content.append(indentBody).append("}\n\n");

        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
            String fieldType = typeUtils.getFieldTypeForModelProperty(fullEnclosing, fieldName, fieldSchema, false, spec);
            String javaFieldName = JerseyNamingUtils.toModelFieldName(fieldName);
            String capitalizedFieldName = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(javaFieldName);
            boolean isListTypeField = fieldType.startsWith("List<");
            String methodPrefix = fieldType.equals("boolean") || fieldType.equals("Boolean") ? "is" : "get";
            content.append(indentBody).append("public ").append(fieldType).append(" " + methodPrefix).append(
                            capitalizedFieldName).append("() {\n");
            if (isListTypeField) {
                content.append(indentBody).append("    if (").append(javaFieldName).append(" == null) {\n");
                content.append(indentBody).append("        ").append(javaFieldName).append(" = new Array").append(fieldType).append("();\n");
                content.append(indentBody).append("    }\n");
                content.append(indentBody).append("    return this.").append(javaFieldName).append(";\n");
            } else {
                content.append(indentBody).append("    return ").append(javaFieldName).append(";\n");
            }
            content.append(indentBody).append("}\n\n");
            if (!isListTypeField) {
                content.append(indentBody).append("public void set").append(capitalizedFieldName).append("(").append(fieldType).append(" ").append(javaFieldName).append(") {\n");
                content.append(indentBody).append("    this.").append(javaFieldName).append(" = ").append(javaFieldName).append(";\n");
                content.append(indentBody).append("}\n\n");
            }

            if (isListTypeField) {
                content.append(indentBody).append("public boolean isSet").append(capitalizedFieldName).append("() {\n");
                content.append(indentBody).append("    return (").append(javaFieldName).append(" != null) && (!").append(javaFieldName).append(".isEmpty());\n");
                content.append(indentBody).append("}\n\n");
                content.append(indentBody).append("public void unset").append(capitalizedFieldName).append("() {\n");
                content.append(indentBody).append("    this.").append(javaFieldName).append(" = null;\n");
                content.append(indentBody).append("}\n\n");
            } else {
                content.append(indentBody).append("public boolean isSet").append(capitalizedFieldName).append("() {\n");
                if (JerseyTypeUtils.isJavaPrimitiveType(fieldType)) {
                    content.append(indentBody).append("    return true;\n");
                } else {
                    content.append(indentBody).append("    return (").append(javaFieldName).append(" != null);\n");
                }
                content.append(indentBody).append("}\n\n");
            }
        }

        // When isModelsOnly, skip equals/hashCode/toString for inner class as well.
        if (!ctx.modelsOnly) {
            content.append(indentBody).append("@Override\n");
            content.append(indentBody).append("public boolean equals(Object o) {\n");
            content.append(indentBody).append("    if (this == o) return true;\n");
            content.append(indentBody).append("    if (o == null || getClass() != o.getClass()) return false;\n");
            content.append(indentBody).append("    ").append(innerClassName).append(" that = (").append(innerClassName).append(") o;\n");
            content.append(indentBody).append("    return ");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) content.append(indentBody).append("    ");
                String mf = JerseyNamingUtils.toModelFieldName(fieldNames.get(i));
                content.append("Objects.equals(").append(mf).append(", that.").append(mf).append(")");
                content.append(i < fieldNames.size() - 1 ? " &&\n" : ";\n");
            }
            if (fieldNames.isEmpty()) content.append("true;\n");
            content.append(indentBody).append("}\n\n");

            content.append(indentBody).append("@Override\n");
            content.append(indentBody).append("public int hashCode() {\n");
            content.append(indentBody).append("    return Objects.hash(");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) content.append(", ");
                content.append(JerseyNamingUtils.toModelFieldName(fieldNames.get(i)));
            }
            content.append(");\n");
            content.append(indentBody).append("}\n\n");

            content.append(indentBody).append("@Override\n");
            content.append(indentBody).append("public String toString() {\n");
            content.append(indentBody).append("    return \"").append(innerClassName).append("{\" +\n");
            for (int i = 0; i < fieldNames.size(); i++) {
                String jf = JerseyNamingUtils.toModelFieldName(fieldNames.get(i));
                content.append(indentBody).append("            \"").append(jf).append("=\" + ").append(jf);
                content.append(i < fieldNames.size() - 1 ? " + \", \" +\n" : " +\n");
            }
            content.append(indentBody).append("            '}';\n");
            content.append(indentBody).append("}\n\n");
        }

        appendJaxbBeanOverride(content, indentBody);
        content.append(indentBody).append("public Object getAttribute(String name) {\n");
        for (String fn : fieldNames) {
            Map<String, Object> fs = Util.asStringObjectMap(allProperties.get(fn));
            String fieldType = typeUtils.getFieldTypeForModelProperty(fullEnclosing, fn, fs, false, spec);
            String methodPrefix = fieldType.equals("boolean") || fieldType.equals("Boolean") ? "is" : "get";
            String cap = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName(fn));
            content.append(indentBody).append("    if (\"").append(fn).append("\".equals(name)) return ").append(methodPrefix).append(cap).append("();\n");
        }
        content.append(indentBody).append("    return null;\n");
        content.append(indentBody).append("}\n\n");

        appendJaxbBeanOverride(content, indentBody);
        content.append(indentBody).append("public boolean isSetAttribute(String name) {\n");
        for (String fn : fieldNames) {
            String cap = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(JerseyNamingUtils.toModelFieldName(fn));
            content.append(indentBody).append("    if (\"").append(fn).append("\".equals(name)) return isSet").append(cap).append("();\n");
        }
        content.append(indentBody).append("    return false;\n");
        content.append(indentBody).append("}\n\n");

        appendJaxbBeanOverride(content, indentBody);
        content.append(indentBody).append("public List<String> getAttributeNames() {\n");
        content.append(indentBody).append("    List<String> names = new ArrayList<>();\n");
        for (String fn : fieldNames) {
            content.append(indentBody).append("    names.add(\"").append(fn).append("\");\n");
        }
        content.append(indentBody).append("    return names;\n");
        content.append(indentBody).append("}\n\n");

        appendJaxbBeanOverride(content, indentBody);
        content.append(indentBody).append("public void setAttribute(String name, Object value) {\n");
        for (String fn : fieldNames) {
            String jf = JerseyNamingUtils.toModelFieldName(fn);
            String cap = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(jf);
            String ft = typeUtils.getFieldTypeForModelProperty(fullEnclosing, fn, Util.asStringObjectMap(allProperties.get(fn)), false, spec);
            if (ft.startsWith("List<")) {
                String it = ft.substring(5, ft.length() - 1);
                content.append(indentBody).append("    if (\"").append(fn).append("\".equals(name)) { get").append(cap).append(
                                "().add((").append(it).append(") value); return; }\n");
            } else {
                content.append(indentBody).append("    if (\"").append(fn).append("\".equals(name)) { set").append(cap).append(
                                "((").append(ft).append(") value); return; }\n");
            }
        }
        content.append(indentBody).append("}\n");

        for (Map.Entry<String, Map<String, Object>> entry : nestedInners) {
            appendInnerClassForInlineObject(content, fullEnclosing, entry.getKey(), entry.getValue(), spec);
        }

        content.append(indentClass).append("}\n");
    }

    /**
     * Append a static inner class for an object-with-single-array property.
     */
    void appendWrapperInnerClass(StringBuilder content, WrapperToGenerate w, String parentSchemaName) {
        String innerJavaField = JerseyNamingUtils.toModelFieldName(w.innerPropertyName);
        String innerCapitalized = JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(innerJavaField);

        content.append("\n    @XmlAccessorType(XmlAccessType.FIELD)\n");
        content.append("    @XmlType(name = \"\", propOrder = {\"").append(innerJavaField).append("\"})\n");
        if (!isStandaloneMode()) {
            content.append("    public static class ").append(w.wrapperClassName).append(" implements Serializable, JAXBBean {\n\n");
        } else {
            content.append("    public static class ").append(w.wrapperClassName).append(" implements Serializable {\n\n");
        }
        content.append("        private static final long serialVersionUID = 1L;\n\n");
        content.append("        @XmlElement(name = \"").append(w.innerPropertyName).append("\")\n");
        if (typeUtils.isEligibleForCascadingValidation(w.innerPropertyName)) {
            content.append("        @Valid\n");
        }
        content.append("        private List<").append(w.itemTypeName).append("> ").append(innerJavaField).append(";\n\n");
        content.append("        public ").append(w.wrapperClassName).append("() {\n");
        content.append("        }\n\n");
        content.append("        public List<").append(w.itemTypeName).append("> get").append(innerCapitalized).append("() {\n");
        content.append("            if (").append(innerJavaField).append(" == null) {\n");
        content.append("                ").append(innerJavaField).append(" = new ArrayList<>();\n");
        content.append("            }\n");
        content.append("            return ").append(innerJavaField).append(";\n");
        content.append("        }\n\n");
        content.append("        public boolean isSet").append(innerCapitalized).append("() {\n");
        content.append("            return ").append(innerJavaField).append(" != null && !").append(innerJavaField).append(".isEmpty();\n");
        content.append("        }\n\n");
        content.append("        public void unset").append(innerCapitalized).append("() {\n");
        content.append("            this.").append(innerJavaField).append(" = null;\n");
        content.append("        }\n\n");
        appendJaxbBeanOverride(content, "        ");
        content.append("        public Object getAttribute(String name) {\n");
        content.append("            if (\"").append(w.innerPropertyName).append("\".equals(name)) {\n");
        content.append("                return get").append(innerCapitalized).append("();\n");
        content.append("            }\n");
        content.append("            return null;\n");
        content.append("        }\n\n");
        appendJaxbBeanOverride(content, "        ");
        content.append("        public void setAttribute(String name, Object value) {\n");
        content.append("            if (\"").append(w.innerPropertyName).append("\".equals(name)) {\n");
        content.append("                get").append(innerCapitalized).append("().add((").append(w.itemTypeName).append(") value);\n");
        content.append("                return;\n");
        content.append("            }\n");
        content.append("        }\n\n");
        appendJaxbBeanOverride(content, "        ");
        content.append("        public boolean isSetAttribute(String name) {\n");
        content.append("            return \"").append(w.innerPropertyName).append("\".equals(name) && isSet").append(innerCapitalized).append("();\n");
        content.append("        }\n\n");
        appendJaxbBeanOverride(content, "        ");
        content.append("        public List<String> getAttributeNames() {\n");
        content.append("            List<String> names = new ArrayList<>();\n");
        content.append("            names.add(\"").append(w.innerPropertyName).append("\");\n");
        content.append("            return names;\n");
        content.append("        }\n");
        content.append("    }\n");
    }

    // ---------------------------------------------------------------------------
    //  JAXB support files
    // ---------------------------------------------------------------------------

    /**
     * Generate JAXBBean interface.
     */
    void generateJAXBBeanInterface(String outputDir, String packagePath) throws IOException {
        String content = "package " + packagePath + ".model;\n\n" +
                "import java.util.List;\n\n" +
                "/**\n" +
                " * Interface for JAXB beans with dynamic attribute support.\n" +
                " * All model classes implementing this interface are JAXB-compatible.\n" +
                " */\n" +
                "public interface JAXBBean {\n" +
                "    Object getAttribute(String name);\n\n" +
                "    boolean isSetAttribute(String name);\n\n" +
                "    List<String> getAttributeNames();\n\n" +
                "    void setAttribute(String name, Object value);\n" +
                "}\n";

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/model/JAXBBean.java", content);
    }

    /**
     * Generate ObjectFactory class for JAXB support (batch - all models).
     */
    void generateObjectFactory(Set<String> generatedTopLevelClassNames, String outputDir, String packagePath) throws IOException {
        Set<String> generatedClasses = collectJaxbModelClassNames(generatedTopLevelClassNames);

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".model;\n\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".JAXBElement;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".annotation.XmlElementDecl;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".annotation.XmlRegistry;\n");
        content.append("import ").append(ctx.validationNs).append(".constraints.*;\n");
        content.append("import javax.xml.namespace.QName;\n\n");

        content.append("@XmlRegistry\n");
        content.append("public class ObjectFactory {\n\n");

        content.append("    public ObjectFactory() {\n");
        content.append("    }\n\n");

        for (String javaClassName : generatedClasses) {
            generateObjectFactoryMethod(content, javaClassName);
        }

        content.append("}\n");

        String batchObjectFactoryPath = outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + packagePath.replace(".", "/") + "/model/ObjectFactory.java";
        JerseyGenerationContext.writeFile(batchObjectFactoryPath, content.toString());
    }

    /**
     * Generate ObjectFactory class for JAXB support (single model - modelsOnly).
     */
    void generateObjectFactory(String generatedTopLevelClassName, String outputDir, String packagePath) throws IOException {

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append("." + JerseyNamingUtils.sanitizePackageName(generatedTopLevelClassName) + ";\n\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".JAXBElement;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".annotation.XmlElementDecl;\n");
        content.append("import ").append(ctx.getXmlBindNs()).append(".annotation.XmlRegistry;\n");
        content.append("import ").append(ctx.validationNs).append(".constraints.*;\n");
        content.append("import javax.xml.namespace.QName;\n\n");

        content.append("@XmlRegistry\n");
        content.append("public class ObjectFactory {\n\n");

        content.append("    public ObjectFactory() {\n");
        content.append("    }\n\n");

        generateObjectFactoryMethod(content, generatedTopLevelClassName);

        content.append("}\n");

        JerseyGenerationContext.writeFile(outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + packagePath.replace(".", "/") + (ctx.modelsOnly ? "/" + JerseyNamingUtils.sanitizePackageName(generatedTopLevelClassName) : "/model") + "/ObjectFactory.java", content.toString());
    }

    /**
     * Generate factory method for a model class.
     */
    void generateObjectFactoryMethod(StringBuilder content, String className) {
        String elementName = className.substring(0, 1).toLowerCase(Locale.ROOT) + className.substring(1);
        String createMethodName = "create" + elementName.substring(0, 1).toUpperCase(Locale.ROOT) + elementName.substring(1);
        String qnameConstant = "_" + className.toUpperCase(Locale.ROOT) + "_QNAME";
        content.append("    private static final QName ").append(qnameConstant).append(" = new QName(\"\", \"").append(elementName).append("\");\n\n");
        content.append("    public ").append(className).append(" ").append(createMethodName).append("() {\n");
        content.append("        return new ").append(className).append("();\n");
        content.append("    }\n\n");
        content.append("    @XmlElementDecl(name = \"").append(elementName).append("\")\n");
        content.append("    public JAXBElement<").append(className).append("> create").append(className).append("(").append(className).append(" value) {\n");
        content.append("        return new JAXBElement<").append(className).append(">(").append(qnameConstant).append(", ").append(className).append(".class, null, value);\n");
        content.append("    }\n\n");
    }

    /**
     * Generate jaxb.index file for JAXB package-based context discovery (batch).
     */
    void generateJaxbIndex(Set<String> generatedTopLevelClassNames, String outputDir, String packagePath) throws IOException {
        Set<String> classNames = collectJaxbModelClassNames(generatedTopLevelClassNames);
        String indexContent = String.join("\n", classNames);
        String modelDir = outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + packagePath.replace(".", "/") + "/model";
        JerseyGenerationContext.writeFile(modelDir + "/jaxb.index", indexContent);
    }

    /**
     * Generate jaxb.index file (single model - modelsOnly).
     */
    void generateJaxbIndex(String generatedTopLevelClassName, String outputDir, String packagePath) throws IOException {
        String indexContent = String.join("\n", generatedTopLevelClassName);
        String modelDir = outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + packagePath.replace(".", "/") + (ctx.modelsOnly ? "/" + JerseyNamingUtils.sanitizePackageName(generatedTopLevelClassName) : "/model");
        JerseyGenerationContext.writeFile(modelDir + "/jaxb.index", indexContent);
    }

    /**
     * Collect the set of JAXB model class names (same set used for ObjectFactory and jaxb.index).
     */
    Set<String> collectJaxbModelClassNames(Set<String> generatedTopLevelClassNames) {
        Set<String> generatedClasses = new HashSet<>(generatedTopLevelClassNames);
        generatedClasses.addAll(ctx.getInlinedSchemas().values());
        return generatedClasses;
    }
}
