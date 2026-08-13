package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for contentmgr-style {@code /folders} resource generation:
 * singular class name, per-method {@code @Actor}, on-behalf OAuth mapping, scope enum names, List import, media types.
 */
@DisplayName("Jersey folders resource generation (contentmgr-style)")
class JerseyFoldersResourceGenerationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("FolderResource: singular name, per-method Actor, CLIENT_ON_BEHALF_OF scopes, List import, JSON media types")
    void foldersResourceMatchesContentmgrStyle() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: Folders API
                  version: 1.0.0
                servers:
                  - url: https://api.example.com/knowledge/contentmgr/v4
                paths:
                  /folders:
                    post:
                      summary: Create Folder
                      operationId: createFolder
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.manage
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.manage
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                      responses:
                        '201':
                          description: Created
                    get:
                      summary: Get Sub Folders
                      operationId: getSubFolders
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.read
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.read
                      parameters:
                        - name: folderAdditionalAttributes
                          in: query
                          schema:
                            type: array
                            items:
                              type: string
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                  /folders/{folderID}:
                    get:
                      summary: Get Folders By ID
                      operationId: getFolder
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.read
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.read
                      parameters:
                        - name: folderID
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                components:
                  securitySchemes:
                    oAuthUser:
                      type: oauth2
                      flows: {}
                    oAuthOnBehalfOfUser:
                      type: oauth2
                      flows: {}
                """;

        Path specFile = tempDir.resolve("folders-api.yaml");
        Files.writeString(specFile, yaml);

        Path out = tempDir.resolve("gen");
        String pkg = "com.test.contentmgr";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path resource = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/resources/FolderResource.java");
        assertTrue(Files.exists(resource), "Expected FolderResource.java (singular) at " + resource);
        String content = Files.readString(resource);

        assertTrue(content.contains("public class FolderResource"), "Class should be FolderResource");
        int classDecl = content.indexOf("public class FolderResource");
        assertTrue(classDecl > 0);
        assertFalse(content.substring(0, classDecl).contains("@Actor("),
                "@Actor should be per-method only, not on the class");

        assertTrue(content.contains("ActorType.USER"), "Should include USER actor");
        assertTrue(content.contains("ActorType.CLIENT_ON_BEHALF_OF_USER"), "Should map oAuthOnBehalfOfUser");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_CLIENT_ON_BEHALF_OF_MANAGE"),
                "onbehalfof.manage should become CLIENT_ON_BEHALF_OF_MANAGE");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_CLIENT_ON_BEHALF_OF_READ"),
                "onbehalfof.read should become CLIENT_ON_BEHALF_OF_READ");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_MANAGE"), "direct manage scope");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_READ"), "direct read scope");

        assertTrue(content.contains("import java.util.List;"), "Array query param should require List import");
        assertTrue(content.contains("List<String>"), "folderAdditionalAttributes should be List<String>");

        assertTrue(content.contains("@Produces(MediaType.APPLICATION_JSON)"), "Inferred JSON-only produces");
        assertTrue(content.contains("@Consumes(MediaType.APPLICATION_JSON)"), "Inferred JSON-only consumes");
        assertTrue(content.contains("    @Consumes\n"), "GET without body should override consumes");

        String getSubAnns = annotationsBefore(content, "public Response getSubFolders(");
        assertTrue(getSubAnns.contains("@Produces(MediaType.APPLICATION_JSON)"), "JSON-only method should declare JSON produces");
        assertFalse(getSubAnns.contains("APPLICATION_XML"), "JSON-only method must not advertise XML");
    }

    @Test
    @DisplayName("x-egain-resource-class-name overrides class name")
    void resourceClassNameExtensionOverridesHeuristic() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: X API
                  version: 1.0.0
                paths:
                  /folders:
                    x-egain-resource-class-name: CustomFoldersFacade
                    get:
                      operationId: listFolders
                      security:
                        - oAuthUser:
                            - a.b.read
                      responses:
                        '200':
                          description: OK
                components:
                  securitySchemes:
                    oAuthUser:
                      type: oauth2
                      flows: {}
                """;

        Path specFile = tempDir.resolve("x-folder.yaml");
        Files.writeString(specFile, yaml);

        Path out = tempDir.resolve("gen-x");
        String pkg = "com.test.x";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path resource = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/resources/CustomFoldersFacade.java");
        assertTrue(Files.exists(resource), "Expected CustomFoldersFacade.java from extension");
        assertTrue(Files.readString(resource).contains("public class CustomFoldersFacade"));
    }

    @Test
    @DisplayName("XML sibling does not contaminate JSON-only method; JSON is listed first when both exist")
    void xmlSiblingDoesNotContaminateJsonOnlyMethod() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: Portal API
                  version: 1.0.0
                paths:
                  /portals/{portalID}/articles/{articleID}:
                    get:
                      operationId: getArticleById
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                  /portals/{portalID}/export:
                    get:
                      operationId: exportPortal
                      responses:
                        '200':
                          description: OK
                          content:
                            application/xml:
                              schema:
                                type: object
                            application/json:
                              schema:
                                type: object
                """;

        Path specFile = tempDir.resolve("mixed-media.yaml");
        Files.writeString(specFile, yaml);
        Path out = tempDir.resolve("gen-mixed");
        String pkg = "com.test.portal";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path resource = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/resources/PortalResource.java");
        assertTrue(Files.exists(resource), "Expected PortalResource.java at " + resource);
        String content = Files.readString(resource);

        int classDecl = content.indexOf("public class PortalResource");
        assertTrue(classDecl > 0);
        String classAnns = content.substring(0, classDecl);
        assertTrue(classAnns.contains("@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})"),
                "Class-level produces should list JSON before XML");

        String jsonAnns = annotationsBefore(content, "public Response getArticleById(");
        assertTrue(jsonAnns.contains("@Produces(MediaType.APPLICATION_JSON)"), "JSON-only operation produces JSON");
        assertFalse(jsonAnns.contains("APPLICATION_XML"), "JSON-only operation must not advertise XML");

        String xmlAnns = annotationsBefore(content, "public Response exportPortal(");
        assertTrue(xmlAnns.contains("@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})"),
                "Mixed operation should list JSON before XML");
    }

    @Test
    @DisplayName("GenericExceptionMapper passes through WebApplicationException status")
    void genericExceptionMapperPassesThroughWebApplicationException() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: Mapper API
                  version: 1.0.0
                paths:
                  /folders:
                    get:
                      operationId: listFolders
                      responses:
                        '200':
                          description: OK
                """;

        Path specFile = tempDir.resolve("mapper-spec.yaml");
        Files.writeString(specFile, yaml);
        Path out = tempDir.resolve("gen-mapper");
        String pkg = "com.test.mapper";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path mapper = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/exception/GenericExceptionMapper.java");
        assertTrue(Files.exists(mapper), "Expected GenericExceptionMapper.java");
        String mapperContent = Files.readString(mapper);
        assertTrue(mapperContent.contains("import javax.ws.rs.WebApplicationException;")
                        || mapperContent.contains("import jakarta.ws.rs.WebApplicationException;"),
                "Mapper should import WebApplicationException");
        assertTrue(mapperContent.contains("if (exception instanceof WebApplicationException wae)"),
                "Mapper should pass through WebApplicationException");
        assertTrue(mapperContent.contains("return wae.getResponse();"),
                "Mapper should return the original JAX-RS response");
    }

    private static String annotationsBefore(String content, String methodSig) {
        int method = content.indexOf(methodSig);
        assertTrue(method > 0, "Expected method signature: " + methodSig);
        int classOpen = content.indexOf("{\n", content.indexOf("public class"));
        int prevEnd = content.lastIndexOf("    }\n\n", method);
        int start = classOpen >= 0 ? classOpen + 2 : 0;
        if (prevEnd >= start) {
            start = prevEnd + "    }\n\n".length();
        }
        return content.substring(start, method);
    }
}
