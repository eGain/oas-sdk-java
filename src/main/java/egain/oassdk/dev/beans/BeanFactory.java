package egain.oassdk.dev.beans;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates Spring beans based on OpenAPI specification
 */
public class BeanFactory {

    /**
     * Generate Spring beans
     *
     * @param spec        OpenAPI specification
     * @param outputDir   Output directory
     * @param packageName Package name for generated beans
     * @throws GenerationException if generation fails
     */
    public void generateBeans(Map<String, Object> spec, String outputDir, String packageName) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate model beans
            generateModelBeans(spec, outputDir, packageName);

            // Generate service beans
            generateServiceBeans(outputDir, packageName);

            // Generate controller beans
            generateControllerBeans(outputDir, packageName);

            // Generate configuration beans
            generateConfigurationBeans(outputDir, packageName);

            // Generate exception beans
            generateExceptionBeans(outputDir, packageName);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate beans: " + e.getMessage(), e);
        }
    }

    /**
     * Generate model beans
     */
    private void generateModelBeans(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null || !components.containsKey("schemas")) {
            return;
        }

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        String packagePath = packageName.replace(".", "/");

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            String modelContent = generateModelBean(schemaName, schema, packageName);
            Files.writeString(Paths.get(outputDir, packagePath, schemaName + ".java"), modelContent);
        }
    }

    /**
     * Generate individual model bean
     */
    private String generateModelBean(String schemaName, Map<String, Object> schema, String packageName) {
        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(".model;\n\n");
        content.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        content.append("import javax.validation.constraints.*;\n");
        content.append("import java.util.Objects;\n\n");

        content.append("public class ").append(schemaName).append(" {\n\n");

        // Generate fields
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            List<String> required = Util.asStringList(schema.getOrDefault("required", new ArrayList<String>()));

            for (Map.Entry<String, Object> property : properties.entrySet()) {
                String fieldName = property.getKey();
                Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

                // Generate field declaration
                content.append("    ");

                // Add validation annotations
                if (required.contains(fieldName)) {
                    content.append("@NotNull\n    ");
                }

                // Add field type and name
                String fieldType = getJavaType(fieldSchema);
                String javaFieldName = toCamelCase(fieldName);
                content.append("private ").append(fieldType).append(" ").append(javaFieldName).append(";\n\n");
            }
        }

        // Generate constructor
        content.append("    public ").append(schemaName).append("() {\n");
        content.append("    }\n\n");

        // Generate getters and setters
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));

            for (Map.Entry<String, Object> property : properties.entrySet()) {
                String fieldName = property.getKey();
                Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());
                String fieldType = getJavaType(fieldSchema);
                String javaFieldName = toCamelCase(fieldName);
                String getterName = "get" + capitalize(javaFieldName);
                String setterName = "set" + capitalize(javaFieldName);

                // Getter
                content.append("    public ").append(fieldType).append(" ").append(getterName).append("() {\n");
                content.append("        return ").append(javaFieldName).append(";\n");
                content.append("    }\n\n");

                // Setter
                content.append("    public void ").append(setterName).append("(").append(fieldType).append(" ").append(javaFieldName).append(") {\n");
                content.append("        this.").append(javaFieldName).append(" = ").append(javaFieldName).append(";\n");
                content.append("    }\n\n");
            }
        }

        // Generate equals and hashCode
        content.append("    @Override\n");
        content.append("    public boolean equals(Object o) {\n");
        content.append("        if (this == o) return true;\n");
        content.append("        if (o == null || getClass() != o.getClass()) return false;\n");
        content.append("        ").append(schemaName).append(" that = (").append(schemaName).append(") o;\n");
        content.append("        return Objects.equals(");

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            String[] fieldNames = properties.keySet().toArray(new String[0]);
            for (int i = 0; i < fieldNames.length; i++) {
                if (i > 0) content.append(", ");
                content.append(toCamelCase(fieldNames[i]));
            }
        }

        content.append(", that.").append(schema.containsKey("properties") ? toCamelCase((Util.asStringObjectMap(schema.get("properties"))).keySet().iterator().next()) : "").append(");\n");
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public int hashCode() {\n");
        content.append("        return Objects.hash(");

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            String[] fieldNames = properties.keySet().toArray(new String[0]);
            for (int i = 0; i < fieldNames.length; i++) {
                if (i > 0) content.append(", ");
                content.append(toCamelCase(fieldNames[i]));
            }
        }

        content.append(");\n");
        content.append("    }\n\n");

        content.append("    @Override\n");
        content.append("    public String toString() {\n");
        content.append("        return \"").append(schemaName).append("{\" +\n");

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            String[] fieldNames = properties.keySet().toArray(new String[0]);
            for (int i = 0; i < fieldNames.length; i++) {
                String fieldName = fieldNames[i];
                String javaFieldName = toCamelCase(fieldName);
                content.append("                \"").append(javaFieldName).append("=\" + ").append(javaFieldName);
                if (i < fieldNames.length - 1) {
                    content.append(" + \", \" +\n");
                } else {
                    content.append(" +\n");
                }
            }
        }

        content.append("                '}';\n");
        content.append("    }\n");
        content.append("}\n");

        return content.toString();
    }

    /**
     * Generate service beans
     */
    private void generateServiceBeans(String outputDir, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");
        String serviceContent = """
                package %s.service;
                
                import jakarta.inject.Singleton;
                import jakarta.inject.Inject;
                import %s.model.*;
                import %s.repository.*;
                
                @Singleton
                public class ApiService {
                
                    @Inject
                    private ApiRepository apiRepository;
                
                    // Business logic methods will be implemented here
                    // These are placeholder methods based on OpenAPI specification
                
                    public String processRequest(Object request) {
                        // Implement business logic
                        return "Processed: " + request.toString();
                    }
                
                    public Object processResponse(Object response) {
                        // Implement business logic
                        return response;
                    }
                }
                """.formatted(packageName, packageName, packageName);

        Files.writeString(Paths.get(outputDir, packagePath, "ApiService.java"), serviceContent);
    }

    /**
     * Generate controller beans
     */
    private void generateControllerBeans(String outputDir, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");
        String controllerContent = """
                package %s.resources;
                
                import jakarta.ws.rs.*;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import jakarta.inject.Inject;
                import jakarta.inject.Singleton;
                import %s.service.ApiService;
                import %s.model.*;
                
                @Path("/api")
                @Produces(MediaType.APPLICATION_JSON)
                @Consumes(MediaType.APPLICATION_JSON)
                @Singleton
                public class ApiResource {
                
                    @Inject
                    private ApiService apiService;
                
                    // Resource methods will be generated based on OpenAPI paths
                    // These are placeholder methods
                
                    @GET
                    @Path("/health")
                    public Response health() {
                        return Response.ok("OK").build();
                    }
                }
                """.formatted(packageName, packageName, packageName);

        Files.writeString(Paths.get(outputDir, packagePath, "ApiResource.java"), controllerContent);
    }

    /**
     * Generate configuration beans
     */
    private void generateConfigurationBeans(String outputDir, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");
        String configContent = """
                package %s.config;
                
                import jakarta.ws.rs.container.ContainerRequestContext;
                import jakarta.ws.rs.container.ContainerResponseContext;
                import jakarta.ws.rs.container.ContainerResponseFilter;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;
                
                @Provider
                public class CorsFilter implements ContainerResponseFilter {
                
                    @Override
                    public void filter(ContainerRequestContext requestContext,
                                     ContainerResponseContext responseContext) throws IOException {
                        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
                        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                        responseContext.getHeaders().add("Access-Control-Allow-Headers", "*");
                    }
                }
                """.formatted(packageName);

        Files.writeString(Paths.get(outputDir, packagePath, "CorsFilter.java"), configContent);
    }

    /**
     * Generate exception beans
     */
    private void generateExceptionBeans(String outputDir, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");
        String exceptionContent = """
                package %s.exception;
                
                import jakarta.ws.rs.WebApplicationException;
                import jakarta.ws.rs.core.Response;
                import jakarta.ws.rs.ext.ExceptionMapper;
                import jakarta.ws.rs.ext.Provider;
                
                @Provider
                public class GenericExceptionMapper implements ExceptionMapper<Exception> {
                
                    @Override
                    public Response toResponse(Exception exception) {
                        if (exception instanceof WebApplicationException wae) {
                            return wae.getResponse();
                        }
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity("An error occurred: " + exception.getMessage())
                                .build();
                    }
                }
                """.formatted(packageName);

        String illegalArgExceptionContent = """
                package %s.exception;
                
                import jakarta.ws.rs.core.Response;
                import jakarta.ws.rs.ext.ExceptionMapper;
                import jakarta.ws.rs.ext.Provider;
                
                @Provider
                public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
                
                    @Override
                    public Response toResponse(IllegalArgumentException exception) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("Invalid argument: " + exception.getMessage())
                                .build();
                    }
                }
                """.formatted(packageName);

        Files.writeString(Paths.get(outputDir, packagePath, "GenericExceptionMapper.java"), exceptionContent);
        Files.writeString(Paths.get(outputDir, packagePath, "IllegalArgumentExceptionMapper.java"), illegalArgExceptionContent);
    }

    /**
     * Convert OpenAPI type to Java type
     */
    private String getJavaType(Map<String, Object> schema) {
        String type = (String) schema.get("type");
        String format = (String) schema.get("format");

        switch (type) {
            case "string" -> {
                return switch (format) {
                    case "date", "date-time" -> "XMLGregorianCalendar";
                    case null, default -> "String";
                };
            }
            case "integer" -> {
                if ("int32".equals(format)) {
                    return "Integer";
                } else if ("int64".equals(format)) {
                    return "Long";
                } else {
                    return "Integer";
                }
            }
            case "number" -> {
                if ("float".equals(format)) {
                    return "Float";
                } else if ("double".equals(format)) {
                    return "Double";
                } else {
                    return "Double";
                }
            }
            case "boolean" -> {
                return "Boolean";
            }
            case "array" -> {
                if (schema.containsKey("items")) {
                    Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                    String itemType = getJavaType(items);
                    return "List<" + itemType + ">";
                } else {
                    return "List<Object>";
                }
            }
            case null, default -> {
                return "Object";
            }
        }
    }

    /**
     * Convert snake_case to camelCase
     */
    private String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i]));
        }
        return result.toString();
    }

    /**
     * Capitalize first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }
}
