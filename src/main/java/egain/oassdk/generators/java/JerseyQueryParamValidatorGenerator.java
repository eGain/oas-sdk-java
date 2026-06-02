package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.io.IOException;
import java.util.*;

import static egain.oassdk.generators.java.JerseyNamingUtils.escapePatternForJavaStringLiteral;
import static egain.oassdk.generators.java.JerseyNamingUtils.toJavaMethodName;

/**
 * Generates query-parameter validation classes (QueryParamValidators and ValidationMapHelper)
 * from OpenAPI parameter definitions.
 */
class JerseyQueryParamValidatorGenerator {

    private final JerseyGenerationContext ctx;

    // Counter for argument lists in validator methods
    private int argCounter = 0;

    JerseyQueryParamValidatorGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Generate query parameter validator classes for all operations in the spec.
     */
    void generate() throws IOException {
        generateQueryParamValidators(ctx.spec, ctx.outputDir, ctx.packageName);
    }

    private void generateQueryParamValidators(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        // Extract server base path for full path construction
        String serverBasePath = JerseyGenerationContext.extractServerBasePath(spec);

        // Collect all endpoint validators
        List<EndpointValidator> validators = new ArrayList<>();
        Map<String, Integer> methodNameCounters = new LinkedHashMap<>();

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Build full path with server base path
            String fullPath = buildFullPath(serverBasePath, path);

            // Process each HTTP method
            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    // Extract parameters
                    List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
                    if (params == null) {
                        params = new ArrayList<>();
                    }

                    // Get operation ID for method name
                    String operationId = (String) operation.get("operationId");
                    String methodName = generateValidatorMethodName(operationId, method, path, packageName, methodNameCounters);

                    // Generate validator method content
                    String validatorMethod = generateValidatorMethod(methodName, params);
					if(validatorMethod.isBlank())
						continue; // skip if no query/path parameters to validate

                    validators.add(new EndpointValidator(fullPath, method.toUpperCase(Locale.ROOT), methodName, validatorMethod));
                }
            }
        }

        // Generate QueryParamValidators.java
        generateQueryParamValidatorsFile(outputDir, validators, packageName);

        // Generate ValidationMapHelper.java
        generateValidationMapHelperFile(outputDir, validators, packageName);
    }

    /**
     * Build full path from server base path and relative path.
     */
    private String buildFullPath(String serverBasePath, String relativePath) {
        String normalizedRelative = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        if (serverBasePath != null && !serverBasePath.isEmpty()) {
            if (serverBasePath.endsWith("/")) {
                return serverBasePath + normalizedRelative.substring(1);
            } else {
                return serverBasePath + normalizedRelative;
            }
        }
        return normalizedRelative;
    }

    /**
     * Generate validator method name from operation ID, HTTP method, and path.
     */
    private String generateValidatorMethodName(String operationId, String method, String path, String packageName, Map<String, Integer> counters) {
        String baseName;

        if (operationId != null && !operationId.isEmpty()) {
            // Use operation ID as base - normalize to valid Java identifier (camelCase)
            baseName = toJavaMethodName(operationId);
        } else {
            // Generate from path and method
            String pathPart = path.replaceAll("[^a-zA-Z0-9]", "");
            // Convert to camelCase: first letter lowercase, rest as-is
            if (pathPart.isEmpty()) {
                baseName = method.toLowerCase(Locale.ROOT);
            } else {
                baseName = method.toLowerCase(Locale.ROOT) + pathPart.substring(0, 1).toUpperCase(Locale.ROOT) +
                        (pathPart.length() > 1 ? pathPart.substring(1) : "");
            }
        }

        // Handle duplicates
        String key = baseName + method.toUpperCase(Locale.ROOT);
        int count = counters.getOrDefault(key, 0);
        counters.put(key, count + 1);

        if (count > 0) {
            baseName = baseName + "_" + count;
        }

		String pkgPart = (packageName != null && !packageName.trim().isEmpty()) ? packageName.substring(packageName.lastIndexOf(".")+1) + "_" : "";

        return baseName + method.toUpperCase(Locale.ROOT) + "Parameter_" + pkgPart + (count + 1);
    }

    /**
     * Generate validator method content.
     */
    private String generateValidatorMethod(String methodName, List<Map<String, Object>> params) {
        // Reset counter for this method
        argCounter = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("  public static ValidationBuilder<RequestInfo> ").append(methodName).append("() {\n");
        sb.append("    ValidationBuilder<RequestInfo> v = new ValidationBuilder<>();\n");

        List<String> allowedParams = new ArrayList<>();
		int sbInitLength = sb.length();

        // Process parameters
        for (Map<String, Object> param : params) {
            String name = (String) param.get("name");
            String in = (String) param.get("in");
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            boolean isRequired = param.containsKey("required") ? (Boolean) param.get("required") : false;

            if (name == null || in == null || schema == null) continue;

            // Skip header parameters
            if ("header".equals(in)) {
                continue;
            }

            // Add to allowed parameters list for query params
            if ("query".equals(in)) {
                allowedParams.add(name);
            }

            // Generate validators based on schema
            generateParameterValidators(sb, name, in, schema, isRequired);
        }

        // Add allowed parameters validator
        if (!allowedParams.isEmpty()) {
            sb.append("    List<String> allowedParameters = List.of(");
            for (int i = 0; i < allowedParams.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(allowedParams.get(i)).append("\"");
            }
            sb.append(");\n");
            sb.append("    v.add(new AllowedParameterValidator(allowedParameters));\n");
        } else {
			if(sb.length() == sbInitLength) {
				// no parameters at all, return empty String
				return "";
			}
            sb.append("    v.add(new AllowedParameterValidator(Collections.emptyList()));\n");
        }

        sb.append("    return v;\n");
        sb.append("  }\n");

        return sb.toString();
    }

    /**
     * Generate validators for a single parameter.
     */
    private void generateParameterValidators(StringBuilder sb, String paramName, String paramType, Map<String, Object> schema, boolean isRequired) {
        String errorPrefix = "path".equals(paramType) ? "PATH_PARAM" : "QUERY_PARAM";

        // Required validator
        if (isRequired) {
            String errorCode = "path".equals(paramType)
                    ? "L10N_INVALID_VALUE_FOR_PATH_PARAM_REQUIRED"
                    : "I18N_REQUIRED_QUERY_PARAM_MISSING";
            sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName).append("\");\n");
            sb.append("    v.add(new IsRequiredValidator(\"").append(paramName).append("\", \"").append(paramName)
                    .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                    .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
        }

        String type = (String) schema.get("type");
        if (type == null) return;

        // String validations
        if ("string".equals(type)) {
            // Max length
            if (schema.containsKey("maxLength")) {
                Object maxLength = schema.get("maxLength");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_MORE_THAN_MAX_LEN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(maxLength).append("\");\n");
                sb.append("    v.add(new MaxLengthValidator(\"").append(paramName).append("\", \"").append(maxLength)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Min length
            if (schema.containsKey("minLength")) {
                Object minLength = schema.get("minLength");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_LESS_THAN_MIN_LEN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(minLength).append("\");\n");
                sb.append("    v.add(new MinLengthValidator(\"").append(paramName).append("\", \"").append(minLength)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Pattern
            if (schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_PATTERN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(escapePatternForJavaStringLiteral(pattern)).append("\");\n");
                sb.append("    v.add(new PatternValidator(\"").append(paramName).append("\", \"").append(escapePatternForJavaStringLiteral(pattern))
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

			// Format (e.g. email, uuid)
			if (schema.containsKey("format")){
				String format = (String) schema.get("format");
				if (schema.containsKey("pattern")) {
					String pattern = (String) schema.get("pattern");
					if (pattern != null) {
						if (pattern.startsWith("\\b")) pattern = "^" + pattern.substring(2);
						if (pattern.endsWith("\\b")) pattern = pattern.substring(0, pattern.length() - 2) + "$";
						format = pattern;
					}
				}
				String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_FORMAT";
				sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(
												paramName)
								.append("\", \"").append(format).append("\");\n");
				sb.append("    v.add(new FormatValidator(\"").append(paramName).append("\", \"").append(format)
								.append("\", \"").append(errorCode).append("\", arguments").append(
												getCurrentArgCounter())
								.append(", Collections.emptyList(), \"").append(paramType).append("\", false));\n");
			}

            // Enum
            List<?> enumValues = schema.get("enum") instanceof List<?> list ? list : null;
            if (enumValues != null && !enumValues.isEmpty()) {
                boolean isArray = schema.containsKey("x-array") || paramName.contains("[");
                StringBuilder enumStr = new StringBuilder();
                for (int i = 0; i < enumValues.size(); i++) {
                    if (i > 0) enumStr.append(",");
                    enumStr.append(enumValues.get(i).toString());
                }
				getNextArgCounter();//call this to keep argument counter correct
                sb.append("    v.add(new EnumValidator(\"").append(paramName).append("\", \"").append(enumStr)
                        .append("\", \"L10N_INVALID_VALUE_FOR_ENUM_ATTRIBUTE\", Collections.emptyList(), Collections.emptyList(), \"")
                        .append(paramType).append("\" ,").append(isArray).append("));\n");
            }
        }

        // Number validations
        if ("integer".equals(type) || "number".equals(type)) {

			if (ctx.modelsOnly)
			{
				// have fixed validation for integer number with at least 1 digit
				String format = "^-?\\\\d+$";
				String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_FORMAT";
				sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(
												paramName)
								.append("\", \"").append(format).append("\");\n");
				sb.append("    v.add(new FormatValidator(\"").append(paramName).append("\", \"").append(format)
								.append("\", \"").append(errorCode).append("\", arguments").append(
												getCurrentArgCounter())
								.append(", Collections.emptyList(), \"").append(paramType).append("\", false));\n");
			}
			else
			{
				// Format (int32, int64, float, double)
				if (schema.containsKey("format"))
				{
					String format = (String) schema.get("format");
					if (format != null && !format.isEmpty())
					{
						String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_FORMAT";
						sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(
														paramName)
										.append("\", \"").append(format).append("\");\n");
						sb.append("    v.add(new FormatValidator(\"").append(paramName).append("\", \"").append(format)
										.append("\", \"").append(errorCode).append("\", arguments").append(
														getCurrentArgCounter())
										.append(", Collections.emptyList(), \"").append(paramType).append("\", false));\n");
					}
				}
			}

            // Maximum
            if (schema.containsKey("maximum")) {
                Object maximum = schema.get("maximum");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_MORE_THAN_MAX";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(maximum).append("\");\n");
                sb.append("    v.add(new NumericMaxValidator(\"").append(paramName).append("\", \"").append(maximum)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false,false));\n");
            }

            // Minimum
            if (schema.containsKey("minimum")) {
                Object minimum = schema.get("minimum");
                String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_LESS_THAN_MIN";
                sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
                        .append("\", \"").append(minimum).append("\");\n");
                sb.append("    v.add(new NumericMinValidator(\"").append(paramName).append("\", \"").append(minimum)
                        .append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
                        .append(", Collections.emptyList(), \"").append(paramType).append("\",false,false));\n");
            }
        }

        // Array validations
        if ("array".equals(type)) {
            // Max items
            if (schema.containsKey("maxItems")) {
                Object maxItems = schema.get("maxItems");
				String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_MORE_THAN_MAX_ITEMS";
				sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
								.append("\", \"").append(maxItems).append("\");\n");
				sb.append("    v.add(new ArrayMaxItemsValidators(\"").append(paramName).append("\", \"").append(maxItems)
								.append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
								.append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            // Min items
            if (schema.containsKey("minItems")) {
                Object minItems = schema.get("minItems");
				String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_LESS_THAN_MIN_ITEMS";
				sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
								.append("\", \"").append(minItems).append("\");\n");
				sb.append("    v.add(new ArrayMinItemsValidator(\"").append(paramName).append("\", \"").append(minItems)
								.append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
								.append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
            }

            if (schema.containsKey("items")) {
                Map<String, Object> itemsSchema = Util.asStringObjectMap(schema.get("items"));
                if (itemsSchema != null) {
					// Enum on items: validate each array element against allowed values
                    List<?> enumValues = itemsSchema.get("enum") instanceof List<?> list ? list : null;
                    if (enumValues != null && !enumValues.isEmpty()) {
                        StringBuilder enumStr = new StringBuilder();
                        for (int i = 0; i < enumValues.size(); i++) {
                            if (i > 0) enumStr.append(",");
                            enumStr.append(enumValues.get(i).toString());
                        }
						getNextArgCounter();//call this to keep argument counter correct
                        sb.append("    v.add(new EnumValidator(\"").append(paramName).append("\", \"").append(enumStr)
                                .append("\", \"L10N_INVALID_VALUE_FOR_ENUM_ATTRIBUTE\", Collections.emptyList(), Collections.emptyList(), \"")
                                .append(paramType).append("\", true));\n");
                    }

					// Pattern
					if (itemsSchema.containsKey("pattern")) {
						String pattern = (String) itemsSchema.get("pattern");
						String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_PATTERN";
						sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
										.append("\", \"").append(escapePatternForJavaStringLiteral(pattern)).append("\");\n");
						sb.append("    v.add(new PatternValidator(\"").append(paramName).append("\", \"").append(escapePatternForJavaStringLiteral(pattern))
										.append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
										.append(", Collections.emptyList(), \"").append(paramType).append("\",true));\n");
					}
                }
            }
        }

        // Boolean validator
        if ("boolean".equals(type)) {
			String errorCode = "L10N_INVALID_VALUE_FOR_" + errorPrefix + "_INVALID_BOOLEAN";
			sb.append("    List<String> arguments").append(getNextArgCounter()).append(" = List.of(\"").append(paramName)
							.append("\", \"placeholder\");\n");
            sb.append("    v.add(new BooleanValidator(\"").append(paramName)
							.append("\", \"").append(errorCode).append("\", arguments").append(getCurrentArgCounter())
							.append(", Collections.emptyList(), \"").append(paramType).append("\",false));\n");
        }
    }

    private int getNextArgCounter() {
        return ++argCounter;
    }

    private int getCurrentArgCounter() {
        return argCounter;
    }

    /**
     * Generate QueryParamValidators.java file.
     */
    private void generateQueryParamValidatorsFile(String outputDir, List<EndpointValidator> validators, String packageName) throws IOException {
        String validatorPackage = packageName != null ? packageName : "egain.ws.oas.gen";
        String validatorPackagePath = validatorPackage.replace(".", "/");

        StringBuilder content = new StringBuilder();
        content.append("package ").append(validatorPackage).append(";\n\n");
        content.append("import egain.framework.validation.ValidationBuilder;\n");
        content.append("import egain.ws.oas.RequestInfo;\n");
        content.append("import ").append(validatorPackage).append(".AllowedParameterValidator;\n");
        content.append("import ").append(validatorPackage).append(".ArrayMaxItemsValidators;\n");
        content.append("import ").append(validatorPackage).append(".ArrayMinItemsValidator;\n");
        content.append("import ").append(validatorPackage).append(".BooleanValidator;\n");
        content.append("import ").append(validatorPackage).append(".EnumValidator;\n");
        content.append("import ").append(validatorPackage).append(".FormatValidator;\n");
        content.append("import ").append(validatorPackage).append(".IsRequiredValidator;\n");
        content.append("import ").append(validatorPackage).append(".MaxLengthValidator;\n");
        content.append("import ").append(validatorPackage).append(".MinLengthValidator;\n");
        content.append("import ").append(validatorPackage).append(".NumericMaxValidator;\n");
        content.append("import ").append(validatorPackage).append(".NumericMinValidator;\n");
        content.append("import ").append(validatorPackage).append(".PatternValidator;\n");
        content.append("import java.lang.String;\n");
        content.append("import java.util.Collections;\n");
        content.append("import java.util.List;\n\n");
        content.append("public class QueryParamValidators {\n");

        // Generate all validator methods
        for (EndpointValidator validator : validators) {
            content.append(validator.validatorMethod).append("\n");
        }

        content.append("}\n");

        // Write to proper package directory under src/main/java
        JerseyGenerationContext.writeFile(outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + validatorPackagePath + "/QueryParamValidators."+(ctx.modelsOnly?"txt":"java"), content.toString());
    }

    /**
     * Generate ValidationMapHelper.java file.
     */
    private void generateValidationMapHelperFile(String outputDir, List<EndpointValidator> validators, String packageName) throws IOException {
        String validatorPackage = packageName != null ? packageName : "egain.ws.oas.gen";
        String validatorPackagePath = validatorPackage.replace(".", "/");

        StringBuilder content = new StringBuilder();
        content.append("package ").append(validatorPackage).append(";\n\n");
        content.append("import egain.framework.validation.ValidationBuilder;\n");
        content.append("import egain.ws.oas.RequestInfo;\n");
        content.append("import egain.ws.oas.Validations.ParameterValidatorMapKey;\n");
        content.append("import java.util.Map;\n");
        content.append("import java.util.function.Supplier;\n\n");
        content.append("public class ValidationMapHelper {\n");
        content.append("  public static final Map<ParameterValidatorMapKey, Supplier<ValidationBuilder<RequestInfo>>> validationsListMap = Map.<ParameterValidatorMapKey, Supplier<ValidationBuilder<RequestInfo>>> ofEntries(\n");

        // Generate map entries
        for (int i = 0; i < validators.size(); i++) {
            EndpointValidator validator = validators.get(i);
            content.append("    Map.entry(new ParameterValidatorMapKey(\"").append(validator.path)
                    .append("\", \"").append(validator.httpMethod).append("\"), QueryParamValidators::")
                    .append(validator.methodName);
            if (i < validators.size() - 1) {
                content.append("),\n");
            } else {
                content.append(")\n");
            }
        }

        content.append("  );\n\n");
        content.append("  /**\n");
        content.append("   * Validate request parameters for a given path and HTTP method\n");
        content.append("   * This method can be called from resources or at the beginning of business logic\n");
        content.append("   * \n");
        content.append("   * @param path The request path\n");
        content.append("   * @param httpMethod The HTTP method (GET, POST, PUT, DELETE, PATCH)\n");
        content.append("   * @param requestInfo The RequestInfo object containing path and query parameters\n");
        content.append("   * @return ValidationError if validation fails, null if validation passes\n");
        content.append("   */\n");
        content.append("  public static egain.framework.validation.ValidationError validate(\n");
        content.append("      String path, String httpMethod, egain.ws.oas.RequestInfo requestInfo) {\n");
        content.append("    ParameterValidatorMapKey key = new ParameterValidatorMapKey(path, httpMethod);\n");
        content.append("    Supplier<ValidationBuilder<RequestInfo>> supplier = validationsListMap.get(key);\n");
        content.append("    if (supplier != null) {\n");
        content.append("      ValidationBuilder<RequestInfo> validationBuilder = supplier.get();\n");
        content.append("      return validationBuilder.build().validate(requestInfo).stream().findFirst().orElse(null);\n");
        content.append("    }\n");
        content.append("    return null;\n");
        content.append("  }\n");
        content.append("}\n");

        // Write to proper package directory under src/main/java
        JerseyGenerationContext.writeFile(outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/") + validatorPackagePath + "/ValidationMapHelper."+(ctx.modelsOnly?"txt":"java"), content.toString());
    }

    /**
     * Helper class to store endpoint validator information.
     */
    private static class EndpointValidator {
        String path;
        String httpMethod;
        String methodName;
        String validatorMethod;

        EndpointValidator(String path, String httpMethod, String methodName, String validatorMethod) {
            this.path = path;
            this.httpMethod = httpMethod;
            this.methodName = methodName;
            this.validatorMethod = validatorMethod;
        }
    }
}
