package egain.oassdk.core.metadata;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;

import java.util.*;

/**
 * Extracts and manages metadata from OpenAPI specifications
 */
public class OASMetadata {

    private Map<String, Object> metadata;

    public OASMetadata() {
        this.metadata = new HashMap<>();
    }

    /**
     * Extract metadata from OpenAPI specification
     *
     * @param spec Parsed OpenAPI specification
     */
    public void extract(Map<String, Object> spec) {
        this.metadata = new HashMap<>();

        // Basic info
        extractBasicInfo(spec);

        // API details
        extractAPIDetails(spec);

        // Endpoints
        extractEndpoints(spec);

        // Models/Schemas
        extractModels(spec);

        // Security
        extractSecurity(spec);

        // Servers
        extractServers(spec);

        // Tags
        extractTags(spec);

        // External docs
        extractExternalDocs(spec);
    }

    /**
     * Get extracted metadata
     *
     * @return Metadata map
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Extract basic information
     */
    private void extractBasicInfo(Map<String, Object> spec) {
        Map<String, Object> basicInfo = new HashMap<>();

        // OpenAPI version
        if (spec.containsKey("openapi")) {
            basicInfo.put("openapi_version", spec.get("openapi"));
        } else if (spec.containsKey("swagger")) {
            basicInfo.put("swagger_version", spec.get("swagger"));
        }

        // Info section
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info != null) {
            basicInfo.put("title", info.get("title"));
            basicInfo.put("version", info.get("version"));
            basicInfo.put("description", info.get("description"));
            basicInfo.put("terms_of_service", info.get("termsOfService"));

            // Contact
            Map<String, Object> contact = Util.asStringObjectMap(info.get("contact"));
            if (contact != null) {
                basicInfo.put("contact_name", contact.get("name"));
                basicInfo.put("contact_email", contact.get("email"));
                basicInfo.put("contact_url", contact.get("url"));
            }

            // License
            Map<String, Object> license = Util.asStringObjectMap(info.get("license"));
            if (license != null) {
                basicInfo.put("license_name", license.get("name"));
                basicInfo.put("license_url", license.get("url"));
            }
        }

        metadata.put("basic_info", basicInfo);
    }

    /**
     * Extract API details
     */
    private void extractAPIDetails(Map<String, Object> spec) {
        Map<String, Object> apiDetails = new HashMap<>();

        // Count endpoints
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            int endpointCount = 0;
            int operationCount = 0;
            Map<String, Integer> methodCounts = new HashMap<>();

            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

                if (pathItem != null) {
                    endpointCount++;

                    for (String method : Constants.HTTP_METHODS) {
                        if (pathItem.containsKey(method)) {
                            operationCount++;
                            methodCounts.put(method.toUpperCase(Locale.ROOT), methodCounts.getOrDefault(method.toUpperCase(Locale.ROOT), 0) + 1);
                        }
                    }
                }
            }

            apiDetails.put("endpoint_count", endpointCount);
            apiDetails.put("operation_count", operationCount);
            apiDetails.put("method_counts", methodCounts);
        }

        metadata.put("api_details", apiDetails);
    }

    /**
     * Extract endpoints information
     */
    private void extractEndpoints(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        List<Map<String, Object>> endpoints = new ArrayList<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            Map<String, Object> endpointInfo = new HashMap<>();
            endpointInfo.put("path", path);

            List<Map<String, Object>> operations = new ArrayList<>();

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation != null) {
                        Map<String, Object> operationInfo = new HashMap<>();
                        operationInfo.put("method", method.toUpperCase(Locale.ROOT));
                        operationInfo.put("operation_id", operation.get("operationId"));
                        operationInfo.put("summary", operation.get("summary"));
                        operationInfo.put("description", operation.get("description"));
                        operationInfo.put("tags", Util.asStringList(operation.get("tags")));

                        // Extract parameters
                        if (operation.containsKey("parameters")) {
                            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
                            if (parameters != null) {
                                operationInfo.put("parameter_count", parameters.size());
                            }
                        }

                        // Extract responses
                        if (operation.containsKey("responses")) {
                            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
                            if (responses != null) {
                                operationInfo.put("response_codes", new ArrayList<>(responses.keySet()));
                            }
                        }

                        operations.add(operationInfo);
                    }
                }
            }

            endpointInfo.put("operations", operations);
            endpoints.add(endpointInfo);
        }

        metadata.put("endpoints", endpoints);
    }

    /**
     * Extract models/schemas information
     */
    private void extractModels(Map<String, Object> spec) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) return;

        List<Map<String, Object>> models = new ArrayList<>();

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            if (schema == null) continue;

            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("name", schemaName);
            modelInfo.put("type", schema.get("type"));
            modelInfo.put("description", schema.get("description"));

            // Extract properties
            if (schema.containsKey("properties")) {
                Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
                if (properties != null) {
                    modelInfo.put("property_count", properties.size());
                    modelInfo.put("properties", new ArrayList<>(properties.keySet()));
                }
            }

            // Extract required fields
            if (schema.containsKey("required")) {
                List<String> required = Util.asStringList(schema.get("required"));
                if (required != null) {
                    modelInfo.put("required_fields", required);
                    modelInfo.put("required_count", required.size());
                }
            }

            models.add(modelInfo);
        }

        metadata.put("models", models);
        metadata.put("model_count", models.size());
    }

    /**
     * Extract security information
     */
    private void extractSecurity(Map<String, Object> spec) {
        Map<String, Object> securityInfo = new HashMap<>();

        // Security schemes
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null && components.containsKey("securitySchemes")) {
            Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
            if (securitySchemes != null) {
                List<Map<String, Object>> schemes = new ArrayList<>();

                for (Map.Entry<String, Object> schemeEntry : securitySchemes.entrySet()) {
                    String schemeName = schemeEntry.getKey();
                    Map<String, Object> scheme = Util.asStringObjectMap(schemeEntry.getValue());

                    if (scheme != null) {
                        Map<String, Object> schemeInfo = new HashMap<>();
                        schemeInfo.put("name", schemeName);
                        schemeInfo.put("type", scheme.get("type"));
                        schemeInfo.put("description", scheme.get("description"));
                        schemes.add(schemeInfo);
                    }
                }

                securityInfo.put("security_schemes", schemes);
            }
        }

        // Global security
        if (spec.containsKey("security")) {
            List<Map<String, Object>> security = Util.asStringObjectMapList(spec.get("security"));
            if (security != null) {
                securityInfo.put("global_security", security);
            }
        }

        metadata.put("security", securityInfo);
    }

    /**
     * Extract servers information
     */
    private void extractServers(Map<String, Object> spec) {
        if (!spec.containsKey("servers")) return;

        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers == null) return;

        List<Map<String, Object>> serverInfo = new ArrayList<>();

        for (Map<String, Object> server : servers) {
            if (server != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("url", server.get("url"));
                info.put("description", server.get("description"));
                serverInfo.add(info);
            }
        }

        metadata.put("servers", serverInfo);
        metadata.put("server_count", serverInfo.size());
    }

    /**
     * Extract tags information
     */
    private void extractTags(Map<String, Object> spec) {
        if (!spec.containsKey("tags")) return;

        List<Map<String, Object>> tags = Util.asStringObjectMapList(spec.get("tags"));
        if (tags == null) return;

        List<Map<String, Object>> tagInfo = new ArrayList<>();

        for (Map<String, Object> tag : tags) {
            if (tag != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", tag.get("name"));
                info.put("description", tag.get("description"));
                info.put("external_docs", tag.get("externalDocs"));
                tagInfo.add(info);
            }
        }

        metadata.put("tags", tagInfo);
        metadata.put("tag_count", tagInfo.size());
    }

    /**
     * Extract external documentation
     */
    private void extractExternalDocs(Map<String, Object> spec) {
        if (!spec.containsKey("externalDocs")) return;

        Map<String, Object> externalDocs = Util.asStringObjectMap(spec.get("externalDocs"));
        if (externalDocs == null) return;

        Map<String, Object> docsInfo = new HashMap<>();
        docsInfo.put("description", externalDocs.get("description"));
        docsInfo.put("url", externalDocs.get("url"));

        metadata.put("external_docs", docsInfo);
    }

    /**
     * Get specific metadata value
     *
     * @param key Metadata key (supports dot notation)
     * @return Metadata value
     */
    public Object getMetadataValue(String key) {
        String[] keys = key.split("\\.");
        Object current = metadata;

        for (String k : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(k);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Check if metadata contains a key
     *
     * @param key Metadata key (supports dot notation)
     * @return true if key exists
     */
    public boolean hasMetadata(String key) {
        return getMetadataValue(key) != null;
    }
}
