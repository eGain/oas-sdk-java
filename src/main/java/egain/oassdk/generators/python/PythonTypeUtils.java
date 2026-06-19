package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.generators.common.OpenApiSchemaReferenceWalker;

import java.util.List;
import java.util.Map;

/**
 * Maps OpenAPI schema types to Python type strings for generated models and routes.
 */
public final class PythonTypeUtils {

    private PythonTypeUtils() {
    }

    public static String getPythonType(Map<String, Object> schema) {
        if (schema == null) {
            return "Any";
        }

        String refName = OpenApiSchemaReferenceWalker.resolvedRefSchemaName(schema);
        if (refName != null) {
            return PythonNamingUtils.toPythonClassName(refName);
        }

        String type = (String) schema.get("type");
        String format = (String) schema.get("format");

        if (type == null) {
            return "Any";
        }

        return switch (type) {
            case "string" -> switch (format) {
                case "date" -> "date";
                case "date-time" -> "datetime";
                case null, default -> "str";
            };
            case "integer", "number" -> "int".equals(type) ? "int" : "float";
            case "boolean" -> "bool";
            case "array" -> {
                if (schema.containsKey("items")) {
                    Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                    if (items != null) {
                        yield "List[" + getPythonType(items) + "]";
                    }
                }
                yield "List[Any]";
            }
            case "object" -> {
                String ref = (String) schema.get("$ref");
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    yield PythonNamingUtils.toPythonClassName(ref.substring(ref.lastIndexOf('/') + 1));
                }
                yield "dict";
            }
            default -> "Any";
        };
    }

    public static void collectReferencedClasses(Map<String, Object> fieldSchema, java.util.Set<String> out) {
        if (fieldSchema == null) {
            return;
        }
        String refName = OpenApiSchemaReferenceWalker.resolvedRefSchemaName(fieldSchema);
        if (refName != null) {
            out.add(PythonNamingUtils.toPythonClassName(refName));
        }
        Map<String, Object> items = Util.asStringObjectMap(fieldSchema.get("items"));
        if (items != null) {
            collectReferencedClasses(items, out);
        }
        for (String comp : new String[]{"allOf", "oneOf", "anyOf"}) {
            List<Map<String, Object>> subs = Util.asStringObjectMapList(fieldSchema.get(comp));
            if (subs != null) {
                for (Map<String, Object> sub : subs) {
                    collectReferencedClasses(sub, out);
                }
            }
        }
    }
}
