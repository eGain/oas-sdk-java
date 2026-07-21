package egain.oassdk.testgenerators.postman;

import egain.oassdk.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds negative Postman test cases from OpenAPI parameters, aligned with
 * {@link egain.oassdk.generators.java.JerseyQueryParamValidatorGenerator} validation rules.
 */
public final class PostmanNegativeRequestFactory {

    /** Avoid multi-gigabyte strings when OpenAPI minLength/maxLength are extreme. */
    private static final int MAX_STRING_LENGTH_NEGATIVE_CASE = 8192;

    private PostmanNegativeRequestFactory() {
    }

    public static final class NegativeCase {
        public final String name;
        public final Map<String, String> pathLiterals;
        public final List<Map<String, Object>> queryEntries;
        public final Integer expectedStatusOverride;
        /** Optional header key→value overrides (e.g. empty Accept for CBD-8608). */
        public final Map<String, String> headerOverrides;

        public NegativeCase(String name,
                            Map<String, String> pathLiterals,
                            List<Map<String, Object>> queryEntries,
                            Integer expectedStatusOverride) {
            this(name, pathLiterals, queryEntries, expectedStatusOverride, Map.of());
        }

        public NegativeCase(String name,
                            Map<String, String> pathLiterals,
                            List<Map<String, Object>> queryEntries,
                            Integer expectedStatusOverride,
                            Map<String, String> headerOverrides) {
            this.name = name;
            this.pathLiterals = pathLiterals != null ? pathLiterals : Map.of();
            this.queryEntries = queryEntries;
            this.expectedStatusOverride = expectedStatusOverride;
            this.headerOverrides = headerOverrides != null ? headerOverrides : Map.of();
        }
    }

    public static List<NegativeCase> buildCases(String openApiPath,
                                                Map<String, Object> operation,
                                                List<Map<String, Object>> positiveQueryList,
                                                int maxCases,
                                                int default4xx) {
        List<NegativeCase> cases = new ArrayList<>();
        if (operation == null || maxCases <= 0) {
            return cases;
        }

        List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
        if (parameters == null) {
            parameters = List.of();
        }

        Map<String, Map<String, Object>> queryByName = new LinkedHashMap<>();
        Map<String, Map<String, Object>> pathByName = new LinkedHashMap<>();
        for (Map<String, Object> p : parameters) {
            if (p == null) {
                continue;
            }
            String name = (String) p.get("name");
            String in = (String) p.get("in");
            if (name == null || in == null) {
                continue;
            }
            if ("query".equals(in)) {
                queryByName.put(name, p);
            } else if ("path".equals(in)) {
                pathByName.put(name, p);
            }
        }

        Set<String> pathNames = new LinkedHashSet<>(PostmanParameterSupport.pathParameterNames(openApiPath));

        // Unsupported query parameter (AllowedParameterValidator)
        if (cases.size() < maxCases) {
            List<Map<String, Object>> q = deepCopyQuery(positiveQueryList);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("key", "x_oas_sdk_extra_probe");
            extra.put("value", "1");
            extra.put("description", "Unsupported parameter probe (should be rejected)");
            q.add(extra);
            cases.add(new NegativeCase("Unsupported query parameter", Map.of(), q, default4xx));
        }

        for (Map.Entry<String, Map<String, Object>> e : queryByName.entrySet()) {
            if (cases.size() >= maxCases) {
                break;
            }
            String paramName = e.getKey();
            Map<String, Object> param = e.getValue();
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            boolean required = Boolean.TRUE.equals(param.get("required"));

            if (required) {
                List<Map<String, Object>> q = queryWithoutKey(positiveQueryList, paramName);
                if (q != null) {
                    cases.add(new NegativeCase("Missing required query: " + paramName, Map.of(), q, default4xx));
                }
            }

            if (cases.size() >= maxCases) {
                break;
            }

            if (schema != null) {
                addSchemaQueryNegatives(cases, maxCases, positiveQueryList, paramName, schema, default4xx);
            }
        }

        for (String pathName : pathNames) {
            if (cases.size() >= maxCases) {
                break;
            }
            Map<String, Object> param = pathByName.get(pathName);
            Map<String, Object> schema = param != null ? Util.asStringObjectMap(param.get("schema")) : null;
            if (schema == null) {
                continue;
            }
            addPathNegatives(cases, maxCases, pathName, schema, default4xx, pathNames, positiveQueryList);
        }

        // CBD-8608: required Accept header with empty value must yield 4xx
        if (cases.size() < maxCases) {
            for (Map<String, Object> p : parameters) {
                if (p == null) {
                    continue;
                }
                if (!"header".equals(p.get("in"))) {
                    continue;
                }
                if (!Boolean.TRUE.equals(p.get("required"))) {
                    continue;
                }
                String headerName = (String) p.get("name");
                if (headerName != null && "Accept".equalsIgnoreCase(headerName)) {
                    cases.add(new NegativeCase(
                            "Empty Accept header",
                            Map.of(),
                            deepCopyQuery(positiveQueryList),
                            default4xx,
                            Map.of("Accept", "")));
                    break;
                }
            }
        }

        return cases;
    }

    private static void addPathNegatives(List<NegativeCase> cases,
                                         int maxCases,
                                         String pathName,
                                         Map<String, Object> schema,
                                         int default4xx,
                                         Set<String> allPathNames,
                                         List<Map<String, Object>> positiveQueryList) {
        String type = schema.get("type") != null ? String.valueOf(schema.get("type")) : "string";

        if (("integer".equals(type) || "number".equals(type)) && cases.size() < maxCases) {
            cases.add(new NegativeCase(
                    "Path " + pathName + " non-numeric",
                    Map.of(pathName, "not-a-number"),
                    deepCopyQuery(positiveQueryList),
                    default4xx));
        }

        if ("integer".equals(type) || "number".equals(type)) {
            if (schema.containsKey("minimum") && cases.size() < maxCases) {
                long min = toLong(schema.get("minimum"), 0);
                boolean excl = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                long bad = excl ? min : min - 1;
                cases.add(new NegativeCase(
                        "Path " + pathName + " below minimum",
                        Map.of(pathName, String.valueOf(bad)),
                        deepCopyQuery(positiveQueryList),
                        default4xx));
            }
            if (schema.containsKey("maximum") && cases.size() < maxCases) {
                long max = toLong(schema.get("maximum"), 0);
                boolean excl = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                long bad = excl ? max : max + 1;
                cases.add(new NegativeCase(
                        "Path " + pathName + " above maximum",
                        Map.of(pathName, String.valueOf(bad)),
                        deepCopyQuery(positiveQueryList),
                        default4xx));
            }
        }

        if ("string".equals(type) && schema.containsKey("pattern") && cases.size() < maxCases) {
            cases.add(new NegativeCase(
                    "Path " + pathName + " pattern violation",
                    Map.of(pathName, "!!!invalid!!!"),
                    deepCopyQuery(positiveQueryList),
                    default4xx));

            String tooMany = commaSeparatedPathTooMany(schema);
            if (tooMany != null) {
                cases.add(new NegativeCase(
                        "Path " + pathName + " too many items",
                        Map.of(pathName, tooMany),
                        deepCopyQuery(positiveQueryList),
                        default4xx));
            }
        }

        if ("string".equals(type) && schema.containsKey("minLength") && cases.size() < maxCases) {
            int minLen = schema.get("minLength") instanceof Number n ? n.intValue() : 0;
            if (minLen > 0 && minLen - 1 <= MAX_STRING_LENGTH_NEGATIVE_CASE) {
                cases.add(new NegativeCase(
                        "Path " + pathName + " shorter than minLength",
                        Map.of(pathName, PostmanParameterSupport.repeatChar('x', minLen - 1)),
                        deepCopyQuery(positiveQueryList),
                        default4xx));
            }
        }

        if ("string".equals(type) && schema.containsKey("maxLength") && cases.size() < maxCases) {
            int maxLen = schema.get("maxLength") instanceof Number n ? n.intValue() : 0;
            if (maxLen >= 0 && maxLen + 1 <= MAX_STRING_LENGTH_NEGATIVE_CASE) {
                cases.add(new NegativeCase(
                        "Path " + pathName + " exceeds maxLength",
                        Map.of(pathName, PostmanParameterSupport.repeatChar('x', maxLen + 1)),
                        deepCopyQuery(positiveQueryList),
                        default4xx));
            }
        }

        List<?> enumVals = schema.get("enum") instanceof List<?> l ? l : null;
        if ("string".equals(type) && enumVals != null && !enumVals.isEmpty() && cases.size() < maxCases) {
            cases.add(new NegativeCase(
                    "Path " + pathName + " invalid enum",
                    Map.of(pathName, PostmanParameterSupport.probeInvalidEnumValue()),
                    deepCopyQuery(positiveQueryList),
                    default4xx));
        }

        // Malformed path: empty last segment (404-style); only if single path param at end
        if (cases.size() < maxCases && allPathNames.size() == 1 && pathName.equals(allPathNames.iterator().next())) {
            cases.add(new NegativeCase(
                    "Path " + pathName + " empty segment",
                    Map.of(pathName, ""),
                    deepCopyQuery(positiveQueryList),
                    404));
        }
    }

    private static void addSchemaQueryNegatives(List<NegativeCase> cases,
                                                int maxCases,
                                                List<Map<String, Object>> positiveQueryList,
                                                String paramName,
                                                Map<String, Object> schema,
                                                int default4xx) {
        String type = schema.get("type") != null ? String.valueOf(schema.get("type")) : null;
        if (type == null) {
            return;
        }

        if ("boolean".equals(type) && cases.size() < maxCases) {
            List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, "maybe");
            cases.add(new NegativeCase("Query " + paramName + " invalid boolean", Map.of(), q, default4xx));
        }

        List<?> enumVals = schema.get("enum") instanceof List<?> l ? l : null;
        if (enumVals != null && !enumVals.isEmpty() && cases.size() < maxCases) {
            List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName,
                    PostmanParameterSupport.probeInvalidEnumValue());
            cases.add(new NegativeCase("Query " + paramName + " invalid enum", Map.of(), q, default4xx));
        }

        if ("string".equals(type) && schema.containsKey("pattern") && cases.size() < maxCases) {
            List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, "invalid");
            cases.add(new NegativeCase("Query " + paramName + " pattern violation", Map.of(), q, default4xx));
        }

        if ("string".equals(type) && schema.containsKey("minLength") && cases.size() < maxCases) {
            int minLen = schema.get("minLength") instanceof Number n ? n.intValue() : 0;
            if (minLen > 0 && minLen - 1 <= MAX_STRING_LENGTH_NEGATIVE_CASE) {
                List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName,
                        PostmanParameterSupport.repeatChar('a', minLen - 1));
                cases.add(new NegativeCase("Query " + paramName + " shorter than minLength", Map.of(), q, default4xx));
            }
        }

        if ("string".equals(type) && schema.containsKey("maxLength") && cases.size() < maxCases) {
            int maxLen = schema.get("maxLength") instanceof Number n ? n.intValue() : 0;
            if (maxLen >= 0 && maxLen + 1 <= MAX_STRING_LENGTH_NEGATIVE_CASE) {
                List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName,
                        PostmanParameterSupport.repeatChar('b', maxLen + 1));
                cases.add(new NegativeCase("Query " + paramName + " exceeds maxLength", Map.of(), q, default4xx));
            }
        }

        if (("integer".equals(type) || "number".equals(type)) && cases.size() < maxCases) {
            List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, "not-a-number");
            cases.add(new NegativeCase("Query " + paramName + " non-numeric", Map.of(), q, default4xx));
        }

        if ("integer".equals(type) || "number".equals(type)) {
            if (schema.containsKey("minimum") && cases.size() < maxCases) {
                long min = toLong(schema.get("minimum"), 0);
                boolean excl = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
                long bad = excl ? min : min - 1;
                List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, String.valueOf(bad));
                cases.add(new NegativeCase("Query " + paramName + " below minimum", Map.of(), q, default4xx));
            }
            if (schema.containsKey("maximum") && cases.size() < maxCases) {
                long max = toLong(schema.get("maximum"), 0);
                boolean excl = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
                long bad = excl ? max : max + 1;
                List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, String.valueOf(bad));
                cases.add(new NegativeCase("Query " + paramName + " above maximum", Map.of(), q, default4xx));
            }
        }

        if ("array".equals(type) && cases.size() < maxCases) {
            Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
            if (schema.containsKey("maxItems")) {
                int maxItems = schema.get("maxItems") instanceof Number n ? n.intValue() : 0;
                if (maxItems >= 0) {
                    String elem = "x";
                    if (items != null && items.get("enum") instanceof List<?> ev && !ev.isEmpty()) {
                        elem = String.valueOf(ev.get(0));
                    }
                    StringBuilder tooMany = new StringBuilder();
                    for (int i = 0; i < maxItems + 2; i++) {
                        if (i > 0) {
                            tooMany.append(',');
                        }
                        tooMany.append(elem);
                    }
                    List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, tooMany.toString());
                    cases.add(new NegativeCase("Query " + paramName + " too many items", Map.of(), q, default4xx));
                }
            }
            if (schema.containsKey("minItems") && cases.size() < maxCases) {
                int minItems = schema.get("minItems") instanceof Number n ? n.intValue() : 0;
                if (minItems > 0) {
                    List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName, "");
                    cases.add(new NegativeCase("Query " + paramName + " too few items", Map.of(), q, default4xx));
                }
            }
            if (items != null && items.get("enum") instanceof List<?> ev && !ev.isEmpty() && cases.size() < maxCases) {
                String valid = String.valueOf(ev.get(0));
                List<Map<String, Object>> q = replaceQueryValue(positiveQueryList, paramName,
                        valid + ",__invalid_enum_item_oas_sdk__");
                cases.add(new NegativeCase("Query " + paramName + " invalid enum item", Map.of(), q, default4xx));
            }
        }
    }

    /**
     * When a path string pattern bounds comma-separated repetitions (e.g. {@code {0,74}}), build one ID too many.
     */
    private static String commaSeparatedPathTooMany(Map<String, Object> schema) {
        Object patternObj = schema.get("pattern");
        if (!(patternObj instanceof String pattern)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{0,(\\d+)\\}").matcher(pattern);
        if (!m.find()) {
            return null;
        }
        int maxRepeats = Integer.parseInt(m.group(1));
        int count = maxRepeats + 2;
        if (count > 80) {
            return null;
        }
        String id = "20150000000203";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static long toLong(Object o, long dflt) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static List<Map<String, Object>> deepCopyQuery(List<Map<String, Object>> source) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (Map<String, Object> row : source) {
            if (row != null) {
                out.add(PostmanParameterSupport.shallowCopyQueryEntry(row));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> queryWithoutKey(List<Map<String, Object>> source, String keyToRemove) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean found = false;
        for (Map<String, Object> row : source) {
            if (row == null) {
                continue;
            }
            Object k = row.get("key");
            if (k != null && k.toString().equals(keyToRemove)) {
                found = true;
                continue;
            }
            out.add(PostmanParameterSupport.shallowCopyQueryEntry(row));
        }
        return found ? out : null;
    }

    private static List<Map<String, Object>> replaceQueryValue(List<Map<String, Object>> source,
                                                               String key,
                                                               String newValue) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean found = false;
        for (Map<String, Object> row : source) {
            Map<String, Object> copy = PostmanParameterSupport.shallowCopyQueryEntry(row);
            if (key.equals(String.valueOf(copy.get("key")))) {
                copy.put("value", newValue);
                found = true;
            }
            out.add(copy);
        }
        if (!found) {
            Map<String, Object> added = new LinkedHashMap<>();
            added.put("key", key);
            added.put("value", newValue);
            out.add(added);
        }
        return out;
    }
}
