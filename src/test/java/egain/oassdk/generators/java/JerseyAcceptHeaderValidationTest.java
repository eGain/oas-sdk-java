package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.postman.PostmanNegativeRequestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for opt-in per-API Accept header validation via QueryParamValidators / ValidationMapHelper.
 */
@DisplayName("Jersey Accept header validation generation")
public class JerseyAcceptHeaderValidationTest {

    @TempDir
    Path tempOutputDir;

    private static final String PACKAGE = "com.test.accept";
    private static final String PACKAGE_PATH = "com/test/accept";
    private static final String VALIDATION_PACKAGE_PATH = "egain/ws/oas/validation";

    @Test
    @DisplayName("Required Accept emits RequiredHeaderValidator and ValidationMapHelper entry")
    public void testRequiredAcceptEmitsValidator() throws OASSDKException, IOException {
        String yaml = """
            openapi: 3.0.0
            info:
              title: Accept Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /folders:
                get:
                  operationId: getSubFolders
                  parameters:
                    - name: Accept
                      in: header
                      required: true
                      schema:
                        type: string
                        minLength: 1
                        example: application/json
                    - name: $attribute
                      in: query
                      schema:
                        type: string
                  responses:
                    '200':
                      description: OK
            """;

        Path outputDir = generateFromYaml("required-accept", yaml);

        Path validators = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/QueryParamValidators.java");
        assertTrue(Files.exists(validators));
        String validatorsContent = Files.readString(validators);
        assertTrue(validatorsContent.contains("RequiredHeaderValidator"),
            "QueryParamValidators should use RequiredHeaderValidator");
        assertTrue(validatorsContent.contains("L10N_HEADER_CANNOT_BE_EMPTY"),
            "Should use L10N_HEADER_CANNOT_BE_EMPTY");
        assertTrue(validatorsContent.contains("List.of(\"Accept\")"),
            "L10n args should include Accept");
        assertFalse(validatorsContent.contains("MinLengthValidator(\"Accept\""),
            "Should not emit separate MinLengthValidator for Accept");
        assertTrue(validatorsContent.contains("getSubFolders"),
            "Should have getSubFolders validator method");

        Path mapHelper = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/ValidationMapHelper.java");
        assertTrue(Files.exists(mapHelper));
        String mapContent = Files.readString(mapHelper);
        assertTrue(mapContent.contains("getSubFolders"),
            "ValidationMapHelper should map getSubFolders");
        assertTrue(mapContent.contains("/v1/folders"),
            "ValidationMapHelper should include full path");

        Path requiredHeader = outputDir.resolve("src/main/java/" + VALIDATION_PACKAGE_PATH + "/RequiredHeaderValidator.java");
        assertTrue(Files.exists(requiredHeader), "RequiredHeaderValidator.java should be generated");
        String rhContent = Files.readString(requiredHeader);
        assertTrue(rhContent.contains("headerParameters()"),
            "RequiredHeaderValidator should check headerParameters");
        assertTrue(rhContent.contains("isEmpty()"),
            "RequiredHeaderValidator should reject empty first value via isEmpty()");

        Path requestInfo = outputDir.resolve("src/main/java/egain/ws/oas/RequestInfo.java");
        assertTrue(Files.exists(requestInfo));
        String riContent = Files.readString(requestInfo);
        assertTrue(riContent.contains("headerParameters"),
            "RequestInfo should include headerParameters");

        Path validations = outputDir.resolve("src/main/java/egain/ws/oas/Validations.java");
        assertTrue(Files.exists(validations));
        assertTrue(Files.readString(validations).contains("getHeaderParameterValue"),
            "Validations should expose getHeaderParameterValue");
    }

    @Test
    @DisplayName("Accept-only operation still gets ValidationMapHelper entry")
    public void testAcceptOnlyOperationMapped() throws OASSDKException, IOException {
        String acceptOnlyYaml = """
            openapi: 3.0.0
            info:
              title: Accept Only API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /ping:
                get:
                  operationId: ping
                  parameters:
                    - name: Accept
                      in: header
                      required: true
                      schema:
                        type: string
                        minLength: 1
                  responses:
                    '200':
                      description: OK
            """;

        Path outputDir = generateFromYaml("accept-only", acceptOnlyYaml);
        Path validators = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/QueryParamValidators.java");
        assertTrue(Files.exists(validators), "QueryParamValidators should exist for Accept-only op");
        String content = Files.readString(validators);
        assertTrue(content.contains("RequiredHeaderValidator"));
        assertTrue(content.contains("ping"));

        Path mapHelper = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/ValidationMapHelper.java");
        String mapContent = Files.readString(mapHelper);
        assertTrue(mapContent.contains("QueryParamValidators::ping"));
        assertTrue(mapContent.contains("/v1/ping"));
    }

    @Test
    @DisplayName("Non-Accept and optional Accept headers are ignored")
    public void testNonAcceptAndOptionalAcceptSkipped() throws OASSDKException, IOException {
        String yaml = """
            openapi: 3.0.0
            info:
              title: Header Skip API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /items:
                get:
                  operationId: listItems
                  parameters:
                    - name: Authorization
                      in: header
                      required: true
                      schema:
                        type: string
                    - name: X-Request-ID
                      in: header
                      required: true
                      schema:
                        type: string
                    - name: Accept
                      in: header
                      required: false
                      schema:
                        type: string
                        minLength: 1
                    - name: filter
                      in: query
                      schema:
                        type: string
                  responses:
                    '200':
                      description: OK
            """;

        Path outputDir = generateFromYaml("skip-headers", yaml);
        Path validators = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/QueryParamValidators.java");
        assertTrue(Files.exists(validators));
        String content = Files.readString(validators);
        assertFalse(content.contains("new RequiredHeaderValidator"),
            "Optional Accept should not emit RequiredHeaderValidator");
        assertFalse(content.contains("L10N_HEADER_CANNOT_BE_EMPTY"));
        assertFalse(content.contains("Authorization"),
            "Authorization header must not appear in QueryParamValidators");
        assertFalse(content.contains("X-Request-ID"),
            "Non-Accept required headers must not appear in QueryParamValidators");
        assertTrue(content.contains("filter") || content.contains("AllowedParameterValidator"),
            "Query params should still be validated");
    }

    @Test
    @DisplayName("Postman negatives include empty Accept when Accept is required (CBD-8608)")
    public void testPostmanEmptyAcceptNegativeCase() {
        Map<String, Object> operation = Map.of(
                "operationId", "getAsyncJobStatus",
                "parameters", List.of(
                        Map.of("name", "Accept", "in", "header", "required", true,
                                "schema", Map.of("type", "string", "minLength", 1))),
                "responses", Map.of("200", Map.of("description", "OK")));

        List<PostmanNegativeRequestFactory.NegativeCase> cases =
                PostmanNegativeRequestFactory.buildCases("/async/job/{jobID}", operation, List.of(), 50, 400);

        assertTrue(cases.stream().anyMatch(c ->
                        "Empty Accept header".equals(c.name)
                                && "".equals(c.headerOverrides.get("Accept"))),
                "Should emit Empty Accept header negative case");
    }

    @Test
    @DisplayName("Resource methods still exclude header parameters including Accept")
    public void testResourceStillExcludesAcceptHeaderParam() throws OASSDKException, IOException {
        String yaml = """
            openapi: 3.0.0
            info:
              title: Resource Accept API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
            paths:
              /folders:
                get:
                  operationId: getFolders
                  parameters:
                    - name: Accept
                      in: header
                      required: true
                      schema:
                        type: string
                        minLength: 1
                  responses:
                    '200':
                      description: OK
            """;

        Path outputDir = generateFromYaml("resource-no-header", yaml);
        Path resourcesDir = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/resources");
        assertTrue(Files.exists(resourcesDir));
        try (var paths = Files.list(resourcesDir)) {
            Path resourceFile = paths.filter(p -> p.toString().endsWith(".java")).findFirst().orElseThrow();
            String content = Files.readString(resourceFile);
            assertFalse(content.contains("@HeaderParam(\"Accept\")"),
                "Accept must not be emitted as @HeaderParam on resource");
            assertFalse(content.contains("HttpHeaders"),
                "Resource must not inject HttpHeaders for Accept (ValidationMapHelper path)");
        }
    }

    private Path generateFromYaml(String dirName, String yaml) throws OASSDKException, IOException {
        Path specFile = tempOutputDir.resolve(dirName + "-spec.yaml");
        Files.writeString(specFile, yaml);
        Path outputDir = tempOutputDir.resolve(dirName + "-out");
        OASSDK sdk = new OASSDK();
        sdk.loadSpec(specFile.toString());
        sdk.generateApplication("java", "jersey", PACKAGE, outputDir.toString());
        return outputDir;
    }
}
