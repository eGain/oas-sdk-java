package egain.oassdk.core.validator;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.ValidationException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validator for OpenAPI specifications
 */
public class OASValidator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    /** Minimum recommended maxLength for error developerMessage (CBD-8620 / v4 WSErrorCommon). */
    private static final int DEVELOPER_MESSAGE_MAX_LENGTH_FLOOR = 1024;

    private static final Set<String> PAGINATION_QUERY_PARAM_NAMES = Set.of(
            "$pagesize", "$pagenum", "pagesize", "pagenum");

    /**
     * Validate OpenAPI specification
     *
     * @param spec Parsed OpenAPI specification
     * @throws ValidationException if validation fails
     */
    public void validate(Map<String, Object> spec) throws ValidationException {
        List<String> errors = new ArrayList<>();

        // Basic structure validation
        validateBasicStructure(spec, errors);

        // Info section validation
        validateInfoSection(spec, errors);

        // Paths validation
        validatePaths(spec, errors);

        // Components validation
        validateComponents(spec, errors);

        // Security validation
        validateSecurity(spec, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException("OpenAPI validation failed:\n" + String.join("\n", errors));
        }
    }

    /**
     * Validate basic OpenAPI structure
     */
    private void validateBasicStructure(Map<String, Object> spec, List<String> errors) {
        if (!spec.containsKey("openapi") && !spec.containsKey("swagger")) {
            errors.add("Missing 'openapi' or 'swagger' field");
        }

        if (!spec.containsKey("info")) {
            errors.add("Missing required 'info' section");
        }

        if (!spec.containsKey("paths")) {
            errors.add("Missing required 'paths' section");
        }
    }

    /**
     * Validate info section
     */
    private void validateInfoSection(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) return;

        // Required fields
        if (!info.containsKey("title")) {
            errors.add("Info section missing required 'title' field");
        }

        if (!info.containsKey("version")) {
            errors.add("Info section missing required 'version' field");
        }

        // Validate version format when present and non-blank (blank is allowed)
        if (info.containsKey("version")) {
            String version = (String) info.get("version");
            if (version != null && !version.isBlank() && !VERSION_PATTERN.matcher(version).matches()) {
                errors.add("Invalid version format in info section: " + version);
            }
        }

        // Validate contact information
        if (info.containsKey("contact")) {
            validateContact(Util.asStringObjectMap(info.get("contact")), errors, "info.contact");
        }

        // Validate license
        if (info.containsKey("license")) {
            validateLicense(Util.asStringObjectMap(info.get("license")), errors, "info.license");
        }
    }

    /**
     * Validate contact information
     */
    private void validateContact(Map<String, Object> contact, List<String> errors, String path) {
        if (contact == null) return;

        if (contact.containsKey("email")) {
            String email = (String) contact.get("email");
            if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
                errors.add("Invalid email format in " + path + ": " + email);
            }
        }

        if (contact.containsKey("url")) {
            String url = (String) contact.get("url");
            if (url != null && !URL_PATTERN.matcher(url).matches()) {
                errors.add("Invalid URL format in " + path + ": " + url);
            }
        }
    }

    /**
     * Validate license information
     */
    private void validateLicense(Map<String, Object> license, List<String> errors, String path) {
        if (license == null) return;

        if (!license.containsKey("name")) {
            errors.add("License section missing required 'name' field in " + path);
        }

        if (license.containsKey("url")) {
            String url = (String) license.get("url");
            if (url != null && !URL_PATTERN.matcher(url).matches()) {
                errors.add("Invalid URL format in " + path + ": " + url);
            }
        }
    }

    /**
     * Validate paths section
     */
    private void validatePaths(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            validatePathItem(path, pathItem, errors);
        }
    }

    /**
     * Validate individual path item
     */
    private void validatePathItem(String path, Map<String, Object> pathItem, List<String> errors) {
        if (pathItem == null) return;

        // Validate HTTP methods
        String[] validMethods = Constants.HTTP_METHODS;
        for (String method : validMethods) {
            if (pathItem.containsKey(method)) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                validateOperation(path, method, operation, errors);
            }
        }

        // Validate parameters
        if (pathItem.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(pathItem.get("parameters"));
            validateParameters(parameters, errors, path);
        }
    }

    /**
     * Validate operation
     */
    private void validateOperation(String path, String method, Map<String, Object> operation, List<String> errors) {
        if (operation == null) return;

        // Validate operation ID
        if (operation.containsKey("operationId")) {
            String operationId = (String) operation.get("operationId");
            if (operationId != null && !isValidOperationId(operationId)) {
                errors.add("Invalid operationId in " + method.toUpperCase(Locale.ROOT) + " " + path + ": " + operationId);
            }
        }

        // Validate responses
        if (!operation.containsKey("responses")) {
            errors.add("Operation " + method.toUpperCase(Locale.ROOT) + " " + path + " missing required 'responses' section");
        } else {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            validateResponses(responses, errors, path, method);
        }

        // Validate parameters
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            validateParameters(parameters, errors, path + "." + method);
        }

        // Validate request body (CBD-8623: JSON body must be an object)
        if (operation.containsKey("requestBody")) {
            validateRequestBody(Util.asStringObjectMap(operation.get("requestBody")),
                    errors, path + "." + method);
        }
    }

    /**
     * Validate responses
     */
    private void validateResponses(Map<String, Object> responses, List<String> errors, String path, String method) {
        if (responses == null) return;

        // Check for at least one response
        if (responses.isEmpty()) {
            errors.add("Operation " + method.toUpperCase(Locale.ROOT) + " " + path + " has no responses defined");
        }

        // Validate response codes
        for (String responseCode : responses.keySet()) {
            if (!isValidResponseCode(responseCode)) {
                errors.add("Invalid response code in " + method.toUpperCase(Locale.ROOT) + " " + path + ": " + responseCode);
            }
        }
    }

    /**
     * Validate parameters
     */
    private void validateParameters(List<Map<String, Object>> parameters, List<String> errors, String path) {
        if (parameters == null) return;

        for (int i = 0; i < parameters.size(); i++) {
            Map<String, Object> param = parameters.get(i);
            if (param == null) continue;

            // Skip validation for $ref parameters - they will be validated when resolved
            if (param.containsKey("$ref")) {
                continue;
            }

            // Required fields
            if (!param.containsKey("name")) {
                errors.add("Parameter " + i + " in " + path + " missing required 'name' field");
            }

            if (!param.containsKey("in")) {
                errors.add("Parameter " + i + " in " + path + " missing required 'in' field");
            } else {
                String in = (String) param.get("in");
                if (!Arrays.asList("query", "header", "path", "cookie").contains(in)) {
                    errors.add("Invalid parameter location in " + path + ": " + in);
                }
            }

            validateParameterConstraints(param, errors, path, i);
        }
    }

    /**
     * Contract lints for parameter schemas (CBD-8623).
     * <p>
     * Path params with {@code pattern} but no {@code minLength}/{@code maxLength} are allowed:
     * published v4 specs often omit length bounds, and generation must not fail on that
     * (CBD-8451 lint removed).
     */
    private void validateParameterConstraints(Map<String, Object> param, List<String> errors, String path, int index) {
        Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
        if (schema == null) {
            return;
        }

        String name = param.get("name") instanceof String s ? s : ("#" + index);
        String in = param.get("in") instanceof String s ? s : "";
        String type = schema.get("type") instanceof String s ? s : null;

        // CBD-8623: pagination query strings must not allow blank values
        if ("query".equals(in) && PAGINATION_QUERY_PARAM_NAMES.contains(name) && "string".equals(type)) {
            boolean hasMinLength = schema.get("minLength") instanceof Number n && n.intValue() >= 1;
            boolean hasPattern = schema.containsKey("pattern");
            if (!hasMinLength && !hasPattern) {
                errors.add("Query parameter '" + name + "' in " + path
                        + " is a blankable string; add minLength/pattern or use type integer");
            }
        }
    }

    /**
     * Validate JSON request bodies require an object schema (CBD-8623).
     */
    private void validateRequestBody(Map<String, Object> requestBody, List<String> errors, String path) {
        if (requestBody == null || requestBody.containsKey("$ref")) {
            return;
        }
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) {
            return;
        }
        for (Map.Entry<String, Object> mediaEntry : content.entrySet()) {
            String mediaType = mediaEntry.getKey();
            if (mediaType == null || !mediaType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                continue;
            }
            Map<String, Object> media = Util.asStringObjectMap(mediaEntry.getValue());
            if (media == null) {
                continue;
            }
            Map<String, Object> schema = Util.asStringObjectMap(media.get("schema"));
            if (schema == null || isObjectLikeSchema(schema)) {
                continue;
            }
            errors.add("Request body for " + path + " (" + mediaType
                    + ") must use an object schema (type object, properties, allOf/oneOf/anyOf, or $ref)");
        }
    }

    private boolean isObjectLikeSchema(Map<String, Object> schema) {
        if (schema.containsKey("$ref")) {
            return true;
        }
        if (schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            return true;
        }
        if (schema.containsKey("properties")) {
            return true;
        }
        String type = schema.get("type") instanceof String s ? s : null;
        return "object".equals(type);
    }

    /**
     * Validate components section
     */
    private void validateComponents(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        // Validate schemas
        if (components.containsKey("schemas")) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            validateSchemas(schemas, errors);
        }

        // Validate reusable parameters (same contract lints as inline params)
        if (components.containsKey("parameters")) {
            Map<String, Object> parameters = Util.asStringObjectMap(components.get("parameters"));
            if (parameters != null) {
                List<Map<String, Object>> paramList = new ArrayList<>();
                for (Object value : parameters.values()) {
                    Map<String, Object> param = Util.asStringObjectMap(value);
                    if (param != null) {
                        paramList.add(param);
                    }
                }
                validateParameters(paramList, errors, "components.parameters");
            }
        }

        // Validate security schemes
        if (components.containsKey("securitySchemes")) {
            Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
            validateSecuritySchemes(securitySchemes, errors);
        }
    }

    /**
     * Validate schemas
     */
    private void validateSchemas(Map<String, Object> schemas, List<String> errors) {
        if (schemas == null) return;

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            if (schema == null) continue;

            // Validate schema name
            if (!isValidSchemaName(schemaName)) {
                errors.add("Invalid schema name: " + schemaName);
            }

            // CBD-8620: developerMessage maxLength must be large enough for localized errors
            validateDeveloperMessageMaxLength(schema, schemaName, errors);
        }
    }

    private void validateDeveloperMessageMaxLength(Map<String, Object> schema, String schemaName, List<String> errors) {
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null) {
            // Also inspect allOf members (common for WSError* compositions)
            Object allOf = schema.get("allOf");
            if (allOf instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> part = Util.asStringObjectMap(item);
                    if (part != null) {
                        validateDeveloperMessageMaxLength(part, schemaName, errors);
                    }
                }
            }
            return;
        }
        Map<String, Object> developerMessage = Util.asStringObjectMap(properties.get("developerMessage"));
        if (developerMessage == null) {
            return;
        }
        Object maxLengthObj = developerMessage.get("maxLength");
        if (!(maxLengthObj instanceof Number maxLengthNum)) {
            return;
        }
        int maxLength = maxLengthNum.intValue();
        if (maxLength < DEVELOPER_MESSAGE_MAX_LENGTH_FLOOR) {
            errors.add("Schema '" + schemaName + "' property developerMessage maxLength is " + maxLength
                    + "; expected at least " + DEVELOPER_MESSAGE_MAX_LENGTH_FLOOR
                    + " (use v4 WSErrorCommon, not v3)");
        }
    }

    /**
     * Validate security schemes
     */
    private void validateSecuritySchemes(Map<String, Object> securitySchemes, List<String> errors) {
        if (securitySchemes == null) return;

        for (Map.Entry<String, Object> schemeEntry : securitySchemes.entrySet()) {
            String schemeName = schemeEntry.getKey();
            Map<String, Object> scheme = Util.asStringObjectMap(schemeEntry.getValue());

            if (scheme == null) continue;

            if (!scheme.containsKey("type")) {
                errors.add("Security scheme " + schemeName + " missing required 'type' field");
            } else {
                String type = (String) scheme.get("type");
                if (!Arrays.asList("apiKey", "http", "oauth2", "openIdConnect").contains(type)) {
                    errors.add("Invalid security scheme type in " + schemeName + ": " + type);
                }
            }
        }
    }

    /**
     * Validate security section
     */
    private void validateSecurity(Map<String, Object> spec, List<String> errors) {
        if (!spec.containsKey("security")) return;

        List<Map<String, Object>> security = Util.asStringObjectMapList(spec.get("security"));
        if (security == null) return;

        for (int i = 0; i < security.size(); i++) {
            Map<String, Object> securityRequirement = security.get(i);
            if (securityRequirement == null) continue;

            // Each security requirement should reference a defined security scheme
            for (String schemeName : securityRequirement.keySet()) {
                if (!isSecuritySchemeDefined(spec, schemeName)) {
                    errors.add("Security requirement " + i + " references undefined security scheme: " + schemeName);
                }
            }
        }
    }

    /**
     * Check if security scheme is defined
     */
    private boolean isSecuritySchemeDefined(Map<String, Object> spec, String schemeName) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return false;

        Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
        if (securitySchemes == null) return false;

        return securitySchemes.containsKey(schemeName);
    }

    /**
     * Validate operation ID format
     */
    private boolean isValidOperationId(String operationId) {
        return operationId.matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$");
    }

    /**
     * Validate response code format
     */
    private boolean isValidResponseCode(String responseCode) {
        if ("default".equals(responseCode)) return true;

        try {
            int code = Integer.parseInt(responseCode);
            return code >= 100 && code <= 599;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate schema name format
     */
    private boolean isValidSchemaName(String schemaName) {
        return schemaName.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
    }
}
