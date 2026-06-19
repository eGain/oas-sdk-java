package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.generators.common.OpenApiSchemaReferenceWalker;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates Pydantic model classes from OpenAPI component schemas for Python backends.
 */
public final class PythonModelGenerator {

    /**
     * Generate Pydantic models referenced by the spec (including transitive field references).
     */
    public void generate(PythonGenerationContext ctx, String outputDir, String packageName) throws IOException {
        Map<String, Object> spec = ctx.getSpec();
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return;
        }

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) {
            return;
        }

        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        Set<String> referencedSchemas = OpenApiSchemaReferenceWalker.collectReferencedSchemas(spec);
        boolean shouldFilterModels = !referencedSchemas.isEmpty();

        registerResolvedRefSchemas(schemas);

        Map<String, String> classToKey = new LinkedHashMap<>();
        for (String key : schemas.keySet()) {
            classToKey.putIfAbsent(PythonNamingUtils.toPythonClassName(key), key);
        }

        Set<String> selectedKeys = new LinkedHashSet<>();
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());
            if (PythonSchemaCollector.isErrorSchema(schemaName, schema)) {
                continue;
            }
            if (shouldFilterModels && !referencedSchemas.contains(schemaName)) {
                continue;
            }
            selectedKeys.add(schemaName);
        }

        Deque<String> work = new ArrayDeque<>(selectedKeys);
        while (!work.isEmpty()) {
            Map<String, Object> schema = Util.asStringObjectMap(schemas.get(work.poll()));
            if (schema == null) {
                continue;
            }
            Set<String> refClasses = new LinkedHashSet<>();
            collectModelReferencedClasses(schema, spec, refClasses);
            for (String cls : refClasses) {
                String depKey = classToKey.get(cls);
                if (depKey != null && selectedKeys.add(depKey)) {
                    work.add(depKey);
                }
            }
        }

        Set<String> knownClasses = new LinkedHashSet<>();
        for (String key : selectedKeys) {
            knownClasses.add(PythonNamingUtils.toPythonClassName(key));
        }

        List<String> generatedModels = new ArrayList<>();
        for (String key : selectedKeys) {
            Map<String, Object> schema = Util.asStringObjectMap(schemas.get(key));
            String pythonClassName = PythonNamingUtils.toPythonClassName(key);
            generateModel(pythonClassName, schema, outputDir, packagePath, spec, knownClasses);
            generatedModels.add(pythonClassName);
        }

        StringBuilder modelsInit = new StringBuilder();
        for (String cls : generatedModels) {
            modelsInit.append("from .").append(cls.toLowerCase()).append(" import ").append(cls).append("\n");
        }
        if (!generatedModels.isEmpty()) {
            modelsInit.append("import sys as _sys\n\n");
            modelsInit.append("_MODELS = (").append(String.join(", ", generatedModels)).append(",)\n");
            modelsInit.append("_CLASSES = {_c.__name__: _c for _c in _MODELS}\n");
            modelsInit.append("for _m in _MODELS:\n");
            modelsInit.append("    _ns = dict(vars(_sys.modules[_m.__module__]))\n");
            modelsInit.append("    _ns.update(_CLASSES)\n");
            modelsInit.append("    _m.model_rebuild(_types_namespace=_ns)\n");
        }
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/models/__init__.py", modelsInit.toString());
    }

    /**
     * Walk every schema's properties for inlined sub-schemas that carry an
     * x-resolved-ref to a named component schema (the parser inlines $ref-only
     * schemas) and register any that are missing from the top-level schema map, using
     * the inlined definition. Without this, a model field can reference a class that
     * was never generated.
     */
    private void registerResolvedRefSchemas(Map<String, Object> schemas) {
        Map<String, Object> toAdd = new LinkedHashMap<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object schemaObj : new ArrayList<>(schemas.values())) {
            collectResolvedRefSchemas(schemaObj, schemas, toAdd, visited);
        }
        schemas.putAll(toAdd);
    }

    private void collectResolvedRefSchemas(Object node, Map<String, Object> existing, Map<String, Object> toAdd,
                                           Set<Object> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> schema = Util.asStringObjectMap(node);
            String refName = OpenApiSchemaReferenceWalker.resolvedRefSchemaName(schema);
            if (refName != null && schema.containsKey("properties")
                    && !existing.containsKey(refName) && !toAdd.containsKey(refName)) {
                Map<String, Object> def = new LinkedHashMap<>(schema);
                def.remove("x-resolved-ref");
                toAdd.put(refName, def);
            }
            for (Object v : schema.values()) {
                collectResolvedRefSchemas(v, existing, toAdd, visited);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectResolvedRefSchemas(v, existing, toAdd, visited);
            }
        }
    }

    /**
     * Generate individual model (Pydantic model).
     */
    private void generateModel(String schemaName, Map<String, Object> schema, String outputDir, String packagePath,
                               Map<String, Object> spec, Set<String> knownClasses) throws IOException {
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();

        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                PythonSchemaMergeUtils.mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> compositionSchemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> subSchema : compositionSchemas) {
                PythonSchemaMergeUtils.mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else {
            PythonSchemaMergeUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec);
        }

        StringBuilder fields = new StringBuilder();
        Set<String> referencedClasses = new LinkedHashSet<>();
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

            String pythonFieldName = PythonNamingUtils.toSnakeCase(fieldName);
            String fieldType = PythonTypeUtils.getPythonType(fieldSchema);
            PythonTypeUtils.collectReferencedClasses(fieldSchema, referencedClasses);
            boolean isRequired = allRequired.contains(fieldName);

            fields.append("    ");
            boolean aliasNeeded = !fieldName.equals(pythonFieldName);
            fields.append(pythonFieldName).append(": ");
            if (isRequired) {
                fields.append(fieldType);
                if (aliasNeeded) {
                    fields.append(" = Field(..., alias=\"").append(fieldName).append("\")");
                }
            } else {
                fields.append("Optional[").append(fieldType).append("]");
                if (aliasNeeded) {
                    fields.append(" = Field(default=None, alias=\"").append(fieldName).append("\")");
                } else {
                    fields.append(" = None");
                }
            }
            fields.append("\n");
        }

        List<String> siblingImports = new ArrayList<>();
        for (String cls : referencedClasses) {
            if (knownClasses.contains(cls) && !cls.equals(schemaName)) {
                siblingImports.add(cls);
            }
        }

        StringBuilder content = new StringBuilder();
        content.append("from __future__ import annotations\n");
        content.append("from pydantic import BaseModel, Field\n");
        content.append("from typing import TYPE_CHECKING, Optional, List, Union, Any\n");
        content.append("from datetime import date, datetime\n");
        if (!siblingImports.isEmpty()) {
            content.append("if TYPE_CHECKING:\n");
            for (String cls : siblingImports) {
                content.append("    from .").append(cls.toLowerCase()).append(" import ").append(cls).append("\n");
            }
        }
        content.append("\n");

        content.append("class ").append(schemaName).append("(BaseModel):\n");
        if (allProperties.isEmpty()) {
            content.append("    pass\n");
        } else {
            content.append(fields);
        }

        content.append("\n    class Config:\n");
        content.append("        populate_by_name = True\n");
        content.append("        from_attributes = True\n");

        PythonNamingUtils.writeFile(
                outputDir + "/" + packagePath + "/models/" + schemaName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Merge a model schema's properties (allOf/oneOf/anyOf/direct, same as
     * generateModel) and collect the class names its fields reference. Used to close
     * the generated-model set over its own field references.
     */
    private void collectModelReferencedClasses(Map<String, Object> schema, Map<String, Object> spec, Set<String> out) {
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();
        if (schema.containsKey("allOf")) {
            for (Map<String, Object> sub : Util.asStringObjectMapList(schema.get("allOf"))) {
                PythonSchemaMergeUtils.mergeSchemaProperties(sub, allProperties, allRequired, spec);
            }
        } else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> subs = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> sub : subs) {
                PythonSchemaMergeUtils.mergeSchemaProperties(sub, allProperties, allRequired, spec);
            }
        } else {
            PythonSchemaMergeUtils.mergeSchemaProperties(schema, allProperties, allRequired, spec);
        }
        for (Object prop : allProperties.values()) {
            PythonTypeUtils.collectReferencedClasses(Util.asStringObjectMap(prop), out);
        }
    }
}
