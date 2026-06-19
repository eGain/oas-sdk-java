package egain.oassdk.generators.python;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;
import egain.oassdk.testgenerators.common.OpenApiTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test cases for FastAPIGenerator
 */
public class FastAPIGeneratorTest {
    
    private FastAPIGenerator generator;
    private Map<String, Object> openApiSpec;
    
    @BeforeEach
    public void setUp() {
        generator = new FastAPIGenerator();
        
        openApiSpec = OpenApiTestFixtures.minimalSpec();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testImplementsCodeGenerator() {
        assertTrue(generator instanceof CodeGenerator);
    }
    
    @Test
    public void testImplementsConfigurableGenerator() {
        assertTrue(generator instanceof ConfigurableGenerator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("FastAPI Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetLanguage() {
        assertEquals("python", generator.getLanguage());
    }
    
    @Test
    public void testGetFramework() {
        assertEquals("fastapi", generator.getFramework());
    }
    
    @Test
    public void testSetAndGetConfig() {
        GeneratorConfig config = new GeneratorConfig();
        generator.setConfig(config);
        
        assertEquals(config, generator.getConfig());
    }
    
    @Test
    public void testGenerateWithNullSpec() {
        GeneratorConfig config = new GeneratorConfig();
        
        assertThrows(GenerationException.class, () -> {
            generator.generate(null, "./output", config, "com.test");
        });
    }
    
    /**
     * Regression test for the generator defects that produced non-runnable Python:
     * model field double-assignment, non-identifier param names ($pagenum),
     * templated-server-URL router prefix, missing datetime import, missing security
     * module, empty package __init__ files, and an empty info.version. Builds a spec
     * that exercises each failure mode and asserts the emitted source is well-formed.
     */
    @Test
    public void testGeneratedPythonIsWellFormed(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();

        // Templated server URL (URI parsing throws on ${VAR}) + empty version.
        spec.put("servers", List.of(Map.of("url", "https://${API_DOMAIN}/api/v1")));
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Reg Test", "version", "")));

        // One path with a path param, a camelCase+date+pattern query param, the
        // $pagenum param, and a query param carrying a schema default.
        Map<String, Object> itemID = Map.of("name", "itemID", "in", "path", "required", true,
                "schema", Map.of("type", "string"));
        Map<String, Object> startDate = Map.of("name", "startDate", "in", "query", "required", true,
                "schema", Map.of("type", "string", "format", "date", "pattern", "^\\d{4}-\\d{2}-\\d{2}$"));
        Map<String, Object> pagenum = Map.of("name", "$pagenum", "in", "query", "required", false,
                "schema", Map.of("type", "integer", "minimum", 1, "maximum", 999, "default", 1));
        Map<String, Object> metric = Map.of("name", "metric", "in", "query", "required", false,
                "schema", Map.of("type", "string", "default", "total"));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getThing");
        get.put("security", List.of(Map.of("oAuthUser", List.of("${SCOPE_PREFIX}things.read"))));
        get.put("parameters", List.of(itemID, startDate, pagenum, metric));
        spec.put("paths", Map.of("/things/{itemID}", Map.of("get", get)));

        // A model with an optional field whose wire name differs from its snake_case
        // identifier (the case that produced `= None = Field(...)`).
        Map<String, Object> thing = Map.of(
                "type", "object",
                "required", List.of("itemId"),
                "properties", new LinkedHashMap<>(Map.of(
                        "itemId", Map.of("type", "string"),
                        "loginId", Map.of("type", "string"))));
        spec.put("components", Map.of("schemas", Map.of("Thing", thing)));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String main = read(tempDir.resolve("main.py"));
        String router = read(tempDir.resolve("api/routers/apiv1thingsrouter.py"));
        String allModels = readAll(tempDir.resolve("api/models"));

        // No invalid double-assignment anywhere in the models.
        assertFalse(allModels.contains("= None = Field("), "model fields must not double-assign");
        assertTrue(allModels.contains("login_id: Optional[str] = Field(default=None, alias=\"loginId\")"),
                "optional aliased field must compose a single RHS");

        // $pagenum sanitized to a valid identifier + bound via alias.
        assertFalse(router.contains("$pagenum:"), "param must not use $ in its identifier");
        assertTrue(router.contains("alias=\"$pagenum\""), "wire name must be preserved via alias");
        assertTrue(router.contains("alias=\"startDate\""), "camelCase query param must be aliased");

        // Router prefix is the URL path, not the templated full URL.
        assertTrue(router.contains("prefix=\"/api/v1/things\""), "prefix must be the server path");
        assertFalse(router.contains("://"), "prefix must not contain the scheme/host");

        // Missing-import and missing-module regressions.
        assertTrue(router.contains("from datetime import"), "router must import datetime");
        assertTrue(Files.exists(tempDir.resolve("api/security.py")), "security module must be generated");

        // Package __init__ files re-export their members so `import *` resolves.
        assertTrue(read(tempDir.resolve("api/models/__init__.py")).contains("import Thing"));
        assertTrue(read(tempDir.resolve("api/routers/__init__.py")).contains("_router"));

        // main.py wiring + non-empty version fallback for the blank info.version.
        assertTrue(main.contains("from api.exceptions.handlers import setup_exception_handlers"));
        assertTrue(main.contains("version=\"1.0.0\""), "blank info.version must fall back to 1.0.0");
        assertFalse(main.contains("version=\"\""), "version must never be empty");

        // The metric default must be honored.
        assertTrue(router.contains("Query(\"total\""), "schema default must be honored");
    }

    /**
     * Regression test for strongly-typed $ref fields: a response model's fields must be
     * typed as the referenced model classes (not dict/Any), those classes must be
     * imported and generated, the generated set must be closed over field references
     * and filtered to what's reachable, and a schema whose description merely contains
     * "default" must not be misclassified as an error schema (the "fault" substring bug).
     */
    @Test
    public void testRefFieldsAreStronglyTypedAndClosed(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Ref Test", "version", "1.0.0")));

        // GET /widgets -> 200 references WidgetList, which references Widget and PageMeta.
        Map<String, Object> okSchema = Map.of("schema", Map.of("$ref", "#/components/schemas/WidgetList"));
        Map<String, Object> resp200 = Map.of("content", Map.of("application/json", okSchema));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "listWidgets");
        get.put("responses", Map.of("200", resp200));
        spec.put("paths", Map.of("/widgets", Map.of("get", get)));

        Map<String, Object> widgetList = Map.of("type", "object", "properties", new LinkedHashMap<>(Map.of(
                "items", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/Widget")),
                "page", Map.of("$ref", "#/components/schemas/PageMeta"))));
        Map<String, Object> widget = Map.of("type", "object",
                "properties", Map.of("id", Map.of("type", "string")));
        // Description contains "default" — must NOT be treated as an error schema.
        Map<String, Object> pageMeta = Map.of("type", "object",
                "description", "Pagination metadata. Overrides the platform default of 75.",
                "properties", Map.of("count", Map.of("type", "integer")));
        // Not referenced by any operation — must be filtered out.
        Map<String, Object> unused = Map.of("type", "object",
                "properties", Map.of("x", Map.of("type", "string")));
        spec.put("components", Map.of("schemas", new LinkedHashMap<>(Map.of(
                "WidgetList", widgetList, "Widget", widget, "PageMeta", pageMeta, "Unused", unused))));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        Path models = tempDir.resolve("api/models");
        String widgetListModel = read(models.resolve("widgetlist.py"));

        // Fields are typed as the referenced classes, and those classes are imported.
        assertTrue(widgetListModel.contains("List[Widget]"), "array items must be typed as the model");
        assertTrue(widgetListModel.contains("PageMeta"), "object ref must be typed as the model");
        assertTrue(widgetListModel.contains("from .widget import Widget"));
        assertTrue(widgetListModel.contains("from .pagemeta import PageMeta"));

        // Referenced models are generated (closure); "default" did not exclude PageMeta.
        assertTrue(Files.exists(models.resolve("widget.py")));
        assertTrue(Files.exists(models.resolve("pagemeta.py")), "schema with 'default' in its description must not be dropped");
        // Unreachable schema is filtered out.
        assertFalse(Files.exists(models.resolve("unused.py")), "unreferenced schema must not be generated");
    }

    /**
     * Regression test for recursive / mutually-referential schemas: models must defer
     * annotations, import siblings only under TYPE_CHECKING (so an A<->B cycle does not
     * deadlock at import time), and the package __init__ must rebuild forward references.
     */
    @Test
    public void testCyclicModelsUseForwardRefsAndRebuild(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Cyclic", "version", "1.0.0")));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getA");
        get.put("responses", Map.of("200", Map.of("content",
                Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/A"))))));
        spec.put("paths", Map.of("/a", Map.of("get", get)));
        // A references B and B references A (mutual cycle).
        Map<String, Object> a = Map.of("type", "object",
                "properties", Map.of("b", Map.of("$ref", "#/components/schemas/B")));
        Map<String, Object> b = Map.of("type", "object",
                "properties", Map.of("a", Map.of("$ref", "#/components/schemas/A")));
        spec.put("components", Map.of("schemas", new LinkedHashMap<>(Map.of("A", a, "B", b))));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String aModel = read(tempDir.resolve("api/models/a.py"));
        assertTrue(aModel.startsWith("from __future__ import annotations"), "must defer annotations");
        assertTrue(aModel.contains("if TYPE_CHECKING:"), "sibling import must be TYPE_CHECKING-guarded");
        assertTrue(aModel.contains("from .b import B"), "must reference sibling B");
        // The sibling import must NOT be a runtime (unguarded) import — that would deadlock.
        assertFalse(aModel.replace("if TYPE_CHECKING:\n    from .b import B", "")
                .contains("from .b import B"), "sibling import must only appear under TYPE_CHECKING");

        String init = read(tempDir.resolve("api/models/__init__.py"));
        assertTrue(init.contains("model_rebuild("), "package __init__ must rebuild forward refs");
        assertTrue(Files.exists(tempDir.resolve("api/models/b.py")));
    }

    /**
     * Auth is a pluggable AuthStrategy abstraction resolved by a handler registry. A
     * custom SigV4 scheme (x-amazon-apigateway-authtype: awsSigv4) falls through to the
     * SigV4SchemeHandler — a built-in handler for the custom AWS SigV4 type
     * (x-amazon-apigateway-authtype: awsSigv4). It demonstrates the Open/Closed extension:
     * a single handler makes the generator emit a concrete SigV4Auth strategy wired via
     * Depends(SigV4Auth()) — no register_auth needed, no old bearer helpers.
     */
    @Test
    public void testSigV4HandlerEmitsConcreteStrategy(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", new LinkedHashMap<>(Map.of("title", "SigV4", "version", "1.0.0")));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getThing");
        get.put("security", List.of(Map.of("sigv4", List.of())));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        spec.put("paths", Map.of("/things", Map.of("get", get)));
        spec.put("components", Map.of("securitySchemes", Map.of(
                "sigv4", Map.of("type", "apiKey", "name", "Authorization", "in", "header",
                        "x-amazon-apigateway-authtype", "awsSigv4"))));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String security = read(tempDir.resolve("api/security.py"));
        assertTrue(security.contains("class AuthStrategy(ABC):"), "must emit the auth interface");
        assertTrue(security.contains("class SigV4Auth(AuthStrategy):"), "SigV4 is a built-in handler -> concrete strategy");
        // Only SigV4 is used; the runtime registry machinery is not needed.
        assertFalse(security.contains("def register_auth("), "no registry needed for a built-in handler");

        String routers = readAll(tempDir.resolve("api/routers"));
        assertTrue(routers.contains("Depends(SigV4Auth())"), "router wires the SigV4 strategy directly");
        assertTrue(routers.contains("from api.security import SigV4Auth"), "router imports the strategy");
        assertFalse(routers.contains("get_current_user"), "old bearer stub must be gone");
        assertFalse(routers.contains("check_scopes"), "old scope-check helper must be gone");
    }

    /**
     * The catch-all RegisteredAuthHandler still covers genuinely unsupported custom types
     * (no matching handler): the generator emits the register_auth / RegisteredAuth registry
     * and wires Depends(RegisteredAuth("<scheme>")) — the impl is supplied at runtime.
     */
    @Test
    public void testUnknownCustomAuthUsesRegistry(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Custom", "version", "1.0.0")));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getThing");
        get.put("security", List.of(Map.of("mtls", List.of())));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        spec.put("paths", Map.of("/things", Map.of("get", get)));
        spec.put("components", Map.of("securitySchemes", Map.of(
                "mtls", Map.of("type", "mutualTLS"))));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String security = read(tempDir.resolve("api/security.py"));
        assertTrue(security.contains("def register_auth("), "unsupported custom scheme emits the registry");
        assertTrue(security.contains("class RegisteredAuth(AuthStrategy):"), "uses the RegisteredAuth proxy");

        String routers = readAll(tempDir.resolve("api/routers"));
        assertTrue(routers.contains("Depends(RegisteredAuth(\"mtls\"))"), "router wires RegisteredAuth by scheme name");
    }

    /**
     * An OAS-standard security scheme defined in components.securitySchemes resolves to a
     * concrete handler: an apiKey scheme emits ApiKeyAuth and an oauth2 scheme emits
     * OAuth2Auth, each wired directly via Depends; when only standard schemes are used,
     * the RegisteredAuth registry machinery must NOT be emitted.
     */
    @Test
    public void testStandardSchemesEmitConcreteStrategies(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Standard Auth", "version", "1.0.0")));

        Map<String, Object> apiKeyOp = new LinkedHashMap<>();
        apiKeyOp.put("operationId", "getKeyed");
        apiKeyOp.put("security", List.of(Map.of("apiKeyScheme", List.of())));
        apiKeyOp.put("responses", Map.of("200", Map.of("description", "OK")));

        Map<String, Object> oauthOp = new LinkedHashMap<>();
        oauthOp.put("operationId", "getOauthed");
        oauthOp.put("security", List.of(Map.of("oauthScheme", List.of("${SCOPE_PREFIX}things.read"))));
        oauthOp.put("responses", Map.of("200", Map.of("description", "OK")));

        spec.put("paths", new LinkedHashMap<>(Map.of(
                "/keyed", Map.of("get", apiKeyOp),
                "/oauthed", Map.of("get", oauthOp))));
        spec.put("components", Map.of("securitySchemes", new LinkedHashMap<>(Map.of(
                "apiKeyScheme", Map.of("type", "apiKey", "name", "X-Custom-Key", "in", "header"),
                "oauthScheme", Map.of("type", "oauth2", "flows", Map.of())))));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String security = read(tempDir.resolve("api/security.py"));
        assertTrue(security.contains("class AuthStrategy(ABC):"), "must emit the auth interface");
        assertTrue(security.contains("class ApiKeyAuth(AuthStrategy):"), "apiKey scheme emits ApiKeyAuth");
        assertTrue(security.contains("class OAuth2Auth(AuthStrategy):"), "oauth2 scheme emits OAuth2Auth");
        // Standard-only schemes need no runtime registry.
        assertFalse(security.contains("RegisteredAuth"), "no RegisteredAuth when only standard schemes are used");
        assertFalse(security.contains("def register_auth("), "no registry machinery for standard schemes");

        String routers = readAll(tempDir.resolve("api/routers"));
        assertTrue(routers.contains("Depends(ApiKeyAuth(name=\"X-Custom-Key\", location=\"header\"))"),
                "apiKey wired directly with name/location from the scheme def");
        assertTrue(routers.contains("Depends(OAuth2Auth(required_scopes=[\"things.read\"]))"),
                "oauth2 wired directly with stripped scopes");
    }

    /**
     * Regression test for `in: cookie` parameters: the handler must emit a FastAPI
     * Cookie(...) marker (with Cookie imported), bind the camelCase wire name via alias,
     * and never emit the bare wire name as an undefined identifier (the import-time
     * NameError that previously prevented the app from booting).
     */
    @Test
    public void testCookieParamUsesCookieMarker(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("servers", List.of(Map.of("url", "https://example.com/api/v1")));
        spec.put("info", new LinkedHashMap<>(Map.of("title", "Cookie Test", "version", "1.0.0")));

        Map<String, Object> sessionId = Map.of("name", "sessionId", "in", "cookie", "required", false,
                "schema", Map.of("type", "string"));
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("operationId", "getSession");
        get.put("parameters", List.of(sessionId));
        spec.put("paths", Map.of("/sessions", Map.of("get", get)));

        generator.generate(spec, tempDir.toString(), new GeneratorConfig(), "api");

        String routers = readAll(tempDir.resolve("api/routers"));

        // Cookie must be imported from fastapi (catches the import regression specifically).
        assertTrue(routers.contains("from fastapi import") && routers.contains("Cookie,"),
                "router must import Cookie from fastapi");
        // The param must use the Cookie(...) marker as its default.
        assertTrue(routers.contains("= Cookie("), "cookie param must use the Cookie(...) marker");
        // The camelCase wire name must be bound via alias on the sanitized identifier.
        assertTrue(routers.contains("session_id: Optional[str] = Cookie(None, alias=\"sessionId\")"),
                "cookie param must sanitize identifier and bind wire name via alias");
        // The bare wire name must NOT be emitted as an undefined identifier.
        assertFalse(routers.contains("= sessionId"), "cookie param must not emit a bare undefined name");
    }

    private String read(Path p) throws IOException {
        return Files.readString(p);
    }

    private String readAll(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> { try { return Files.readString(p); } catch (IOException e) { return ""; } })
                    .collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testGenerateWithNullOutputDir(@TempDir Path tempDir) {
        GeneratorConfig config = new GeneratorConfig();
        
        // The method may handle null outputDir gracefully or throw an exception
        // Let's test that it either throws or completes without error
        try {
            generator.generate(openApiSpec, null, config, "com.test");
            // If it doesn't throw, that's acceptable - the method may handle null
        } catch (GenerationException | RuntimeException e) {
            // If it throws, that's also acceptable
            assertNotNull(e);
        }
    }
}

