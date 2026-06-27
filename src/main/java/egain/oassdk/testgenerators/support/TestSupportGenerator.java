package egain.oassdk.testgenerators.support;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestOutputLayout;
import egain.oassdk.testgenerators.common.TestSpecUtils;

import egain.oassdk.testgenerators.common.TestProfileSupport;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Emits shared test-support Java classes and {@code test-env.properties} template
 * used by all generated Java test modules.
 */
public class TestSupportGenerator {

    public void generate(Map<String, Object> spec, String outputDir, TestConfig config) throws GenerationException {
        generate(spec, outputDir, config, List.of("contract", "integration", "lifecycle", "nfr", "performance", "security"));
    }

    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, List<String> testTypes)
            throws GenerationException {
        try {
            String basePackage = resolvePackage(config);
            String supportPackage = TestOutputLayout.supportPackage(basePackage);
            String defaultBaseUrl = resolveTemplateBaseUrl(spec, config);
            List<String> modules = TestProfileSupport.aggregatorModules(testTypes);

            Path supportDir = Paths.get(TestOutputLayout.supportJavaDir(outputDir, basePackage));
            Files.createDirectories(supportDir);
            pruneObsoleteSupportSources(supportDir);
            Path supportResourcesDir = Paths.get(outputDir, "test-support", "src", "test", "resources", "bodies");
            if (config == null || config.isMockData()) {
                emitOperationBodies(spec, supportResourcesDir);
            }

            write(supportDir.resolve("TestEnv.java"), testEnvSource(supportPackage, defaultBaseUrl));
            write(supportDir.resolve("AuthProvider.java"), authProviderSource(supportPackage));
            write(supportDir.resolve("StaticTokenAuth.java"), staticTokenAuthSource(supportPackage));
            write(supportDir.resolve("CurlLoginAuth.java"), curlLoginAuthSource(supportPackage));
            write(supportDir.resolve("HttpChainAuth.java"), httpChainAuthSource(supportPackage));
            write(supportDir.resolve("AuthChainExecutor.java"), loadSupportSnippet("AuthChainExecutor.java", supportPackage));
            write(supportDir.resolve("AuthTokenCli.java"), authTokenCliSource(supportPackage));
            write(supportDir.resolve("RequestBodyFactory.java"), requestBodyFactorySource(supportPackage));
            write(supportDir.resolve("TestAuth.java"), testAuthSource(supportPackage));
            write(supportDir.resolve("TestHttp.java"), testHttpSource(supportPackage));
            write(supportDir.resolve("TestClient.java"), testClientSource(supportPackage));
            write(supportDir.resolve("TestContext.java"), testContextSource(supportPackage));
            write(supportDir.resolve("RequestBodyEnv.java"), requestBodyEnvSource(supportPackage));

            Path testsRoot = Paths.get(outputDir);
            Files.createDirectories(testsRoot);
            write(testsRoot.resolve("test-env.properties"), testEnvProperties(defaultBaseUrl));
            write(testsRoot.resolve("test-env.local.properties.example"), testEnvLocalExample());
            write(testsRoot.resolve("test-filter.sh"), testFilterScript());
            write(testsRoot.resolve("run-smoke.sh"), runSmokeScript());
            write(testsRoot.resolve("run-smoke.bat"), runSmokeBat());
            write(testsRoot.resolve("run-all.sh"), runAllScript(modules));
            write(testsRoot.resolve("run-all.bat"), runAllBat(modules));
            write(testsRoot.resolve("fetch-token.sh"), fetchTokenScript());
            write(testsRoot.resolve("pom.xml"), aggregatorPom(modules));
            write(testsRoot.resolve("README-TESTS.md"), readmeTests());

        } catch (IOException e) {
            throw new GenerationException("Failed to generate test support: " + e.getMessage(), e);
        }
    }

    private static String resolvePackage(TestConfig config) {
        if (config != null && config.getAdditionalProperties() != null) {
            Object pkg = config.getAdditionalProperties().get("packageName");
            if (pkg != null && !pkg.toString().isBlank()) {
                return pkg.toString().trim();
            }
        }
        return "com.example.api";
    }

    private static String resolveTemplateBaseUrl(Map<String, Object> spec, TestConfig config) {
        String resolved = TestSpecUtils.resolveBaseUrl(spec, config);
        if (resolved == null || resolved.isBlank()) {
            return "";
        }
        if (resolved.contains("${")) {
            return "";
        }
        return resolved;
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void pruneObsoleteSupportSources(Path supportDir) throws IOException {
        for (String fileName : List.of("EgainAuth.java", "EgainAsyncTaskHelper.java", "EgainInternalKbHelper.java")) {
            Files.deleteIfExists(supportDir.resolve(fileName));
        }
    }

    private static void emitOperationBodies(Map<String, Object> spec, Path bodiesDir) throws IOException {
        if (spec == null) {
            return;
        }
        Map<String, Object> paths = egain.oassdk.Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }
        Files.createDirectories(bodiesDir);
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = egain.oassdk.Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : List.of("get", "post", "put", "patch", "delete", "options", "head")) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = egain.oassdk.Util.asStringObjectMap(pathItem.get(method));
                if (operation == null || !operation.containsKey("requestBody")) {
                    continue;
                }
                String operationId = operationId(operation, method, pathEntry.getKey());
                String raw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
                String body = (raw == null || raw.isBlank()) ? "{}" : raw;
                write(bodiesDir.resolve(operationId + ".json"), body + "\n");
            }
        }
    }

    private static String operationId(Map<String, Object> operation, String method, String path) {
        Object operationId = operation.get("operationId");
        if (operationId != null && !operationId.toString().isBlank()) {
            return operationId.toString().trim();
        }
        return (method + "_" + path).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String testEnvSource(String pkg, String defaultBaseUrl) {
        return """
                package %s;

                import java.io.InputStream;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.Locale;
                import java.util.Properties;

                /**
                 * Loads test-env.properties (+ optional local override) and environment variables.
                 * Generated by OAS SDK TestSupportGenerator.
                 */
                public final class TestEnv {

                    private static final Properties PROPS = loadProperties();

                    private TestEnv() {
                    }

                    private static Properties loadProperties() {
                        Properties p = new Properties();
                        loadFile(p, resolveEnvFile());
                        loadFile(p, siblingFile("test-env.local.properties"));
                        return p;
                    }

                    private static String resolveEnvFile() {
                        String env = System.getenv("TEST_ENV_FILE");
                        if (env != null && !env.isBlank()) {
                            return env.trim();
                        }
                        return siblingPath("test-env.properties");
                    }

                    private static String siblingPath(String name) {
                        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
                        Path parent = cwd.getParent();
                        if (parent != null && Files.isRegularFile(parent.resolve(name))) {
                            return parent.resolve(name).toString();
                        }
                        if (Files.isRegularFile(cwd.resolve(name))) {
                            return cwd.resolve(name).toString();
                        }
                        return name;
                    }

                    private static String siblingFile(String name) {
                        return siblingPath(name);
                    }

                    private static void loadFile(Properties p, String path) {
                        Path file = Path.of(path);
                        if (!Files.isRegularFile(file)) {
                            try (InputStream in = TestEnv.class.getClassLoader().getResourceAsStream(path)) {
                                if (in != null) {
                                    p.load(in);
                                }
                            } catch (Exception ignored) {
                                // optional file
                            }
                            return;
                        }
                        try (InputStream in = Files.newInputStream(file)) {
                            p.load(in);
                        } catch (Exception ignored) {
                            // optional file
                        }
                    }

                    public static String get(String key, String defaultValue) {
                        String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_');
                        String fromEnv = System.getenv(envKey);
                        if (fromEnv != null && !fromEnv.isBlank()) {
                            return fromEnv.trim();
                        }
                        String fromProp = PROPS.getProperty(key);
                        if (fromProp != null && !fromProp.isBlank()) {
                            return fromProp.trim();
                        }
                        return defaultValue;
                    }

                    public static boolean getBoolean(String key, boolean defaultValue) {
                        String v = get(key, null);
                        if (v == null) {
                            return defaultValue;
                        }
                        return "true".equalsIgnoreCase(v) || "1".equals(v);
                    }

                    public static int getInt(String key, int defaultValue) {
                        String v = get(key, null);
                        if (v == null) {
                            return defaultValue;
                        }
                        try {
                            return Integer.parseInt(v.trim());
                        } catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }

                    public static String baseUrl() {
                        String env = System.getenv("API_BASE_URL");
                        if (env != null && !env.isBlank()) {
                            return env.trim();
                        }
                        return get("base.url", "%s");
                    }

                    public static String acceptLanguage() {
                        return get("accept.language", "en-US");
                    }

                    public static String departmentId() {
                        return get("test.department.id", "1");
                    }

                    public static String parentFolderId() {
                        return get("test.parent.folder.id", get("test.filter.parent.id", "1"));
                    }

                    public static String folderId() {
                        return get("test.folder.id", parentFolderId());
                    }

                    public static String hierarchyRootFolderId() {
                        return get("test.hierarchy.root.folder.id", get("test.filter.parent.id", parentFolderId()));
                    }

                    public static String userId() {
                        return get("test.user.id", "1");
                    }

                    public static String userGroupId() {
                        return get("test.user.group.id", "1");
                    }

                    public static String promptId() {
                        return get("test.prompt.id", "1");
                    }

                    public static String schemathesisIncludeOperations() {
                        String include = get("schemathesis.include.operations", null);
                        if (include != null && !include.isBlank()) {
                            return include;
                        }
                        return String.join(",", includeOperations());
                    }

                    public static java.util.Set<String> includeOperations() {
                        String include = get("test.include.operations", "");
                        if (include == null || include.isBlank()) {
                            include = get("schemathesis.include.operations", "");
                        }
                        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
                        if (include == null || include.isBlank()) {
                            return values;
                        }
                        for (String token : include.split(",")) {
                            String trimmed = token.trim();
                            if (!trimmed.isEmpty()) {
                                values.add(trimmed);
                            }
                        }
                        return values;
                    }

                    public static String flowsDir() {
                        return get("test.flows.dir", "src/test/flows");
                    }

                    public static String flowsManifest() {
                        return get("test.flows.manifest", "");
                    }

                    public static String pageNum() {
                        return String.valueOf(getInt("test.pagination.pageNum", 1));
                    }

                    public static String pageSize() {
                        return String.valueOf(getInt("test.pagination.pageSize", 10));
                    }

                    public static boolean tlsVerify() {
                        return getBoolean("tls.verify", true);
                    }

                    public static boolean bootstrapEnabled() {
                        return getBoolean("test.bootstrap.enabled", false);
                    }

                    public static boolean destructiveEnabled() {
                        return getBoolean("test.destructive.enabled", false);
                    }

                    public static boolean validateResponseSchema() {
                        return getBoolean("test.validateResponseSchema", false);
                    }

                    public static boolean bootstrapHierarchyEnabled() {
                        return getBoolean("test.bootstrap.hierarchy.enabled", false);
                    }

                    public static String topicParentFolderId() {
                        return get("test.topic.parent.folder.id", "");
                    }

                    public static String folderNoDeletePermissionId() {
                        return get("test.folder.no.delete.permission.id", "");
                    }

                    public static String folderNoCreatePermissionId() {
                        return get("test.folder.no.create.permission.id", "");
                    }

                    public static String resolveSystemUrl(String relativeOrAbsolute) {
                        if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
                            return baseUrl();
                        }
                        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                            return relativeOrAbsolute;
                        }
                        String base = baseUrl();
                        if (base.endsWith("/") && relativeOrAbsolute.startsWith("/")) {
                            return base.substring(0, base.length() - 1) + relativeOrAbsolute;
                        }
                        if (!base.endsWith("/") && !relativeOrAbsolute.startsWith("/")) {
                            return base + "/" + relativeOrAbsolute;
                        }
                        return base + relativeOrAbsolute;
                    }

                    public static String tokenForScheme(String scheme) {
                        if (scheme == null || scheme.isBlank()) {
                            return get("auth.token", "");
                        }
                        String key = "auth.token." + scheme.toLowerCase(Locale.ROOT).replace(' ', '_');
                        String v = get(key, null);
                        if (v != null && !v.isBlank()) {
                            return v;
                        }
                        return get("auth.token", "");
                    }

                    public static java.util.Set<String> keysWithPrefix(String prefix) {
                        java.util.TreeSet<String> keys = new java.util.TreeSet<>();
                        if (prefix == null) {
                            return keys;
                        }
                        for (String k : PROPS.stringPropertyNames()) {
                            if (k.startsWith(prefix)) {
                                keys.add(k);
                            }
                        }
                        return keys;
                    }
                }
                """.formatted(pkg, escapeJava(defaultBaseUrl));
    }

    private static String authProviderSource(String pkg) {
        return """
                package %s;

                public interface AuthProvider {
                    String getToken();

                    default String getInvalidToken() {
                        String token = getToken();
                        if (token == null || token.isBlank()) {
                            return "invalid-token";
                        }
                        int keep = Math.max(0, token.length() - 4);
                        return token.substring(0, keep) + "XXXX-invalid";
                    }
                }
                """.formatted(pkg);
    }

    private static String staticTokenAuthSource(String pkg) {
        return """
                package %s;

                public final class StaticTokenAuth implements AuthProvider {
                    @Override
                    public String getToken() {
                        String env = System.getenv("API_TOKEN");
                        if (env != null && !env.isBlank()) {
                            return env.trim();
                        }
                        env = System.getenv("API_BEARER_TOKEN");
                        if (env != null && !env.isBlank()) {
                            return env.trim();
                        }
                        return TestEnv.get("auth.token", "");
                    }
                }
                """.formatted(pkg);
    }

    private static String curlLoginAuthSource(String pkg) {
        return """
                package %s;

                /**
                 * Alias for chain auth — runs {@code auth.chain.*} when configured, else static token.
                 */
                public final class CurlLoginAuth implements AuthProvider {
                    private final HttpChainAuth chain = new HttpChainAuth();
                    private final StaticTokenAuth fallback = new StaticTokenAuth();

                    @Override
                    public String getToken() {
                        String token = chain.getToken();
                        if (token != null && !token.isBlank()) {
                            return token;
                        }
                        return fallback.getToken();
                    }
                }
                """.formatted(pkg);
    }

    private static String httpChainAuthSource(String pkg) {
        return """
                package %s;

                /**
                 * Runs {@code auth.chain.N.*} HTTP steps from test-env properties.
                 */
                public final class HttpChainAuth implements AuthProvider {

                    private final StaticTokenAuth fallback = new StaticTokenAuth();

                    @Override
                    public String getToken() {
                        String token = AuthChainExecutor.run(TestEnvPropertySource.INSTANCE, TestHttp.client());
                        if (token != null && !token.isBlank()) {
                            return token.trim();
                        }
                        return fallback.getToken();
                    }

                    private static final class TestEnvPropertySource implements AuthChainExecutor.PropertySource {
                        private static final TestEnvPropertySource INSTANCE = new TestEnvPropertySource();

                        @Override
                        public String get(String key, String defaultValue) {
                            return TestEnv.get(key, defaultValue);
                        }

                        @Override
                        public Iterable<String> keysWithPrefix(String prefix) {
                            return TestEnv.keysWithPrefix(prefix);
                        }
                    }
                }
                """.formatted(pkg);
    }

    private static String authTokenCliSource(String pkg) {
        return """
                package %s;

                /**
                 * Prints bearer token from configured auth provider (for fetch-token.sh).
                 */
                public final class AuthTokenCli {

                    private AuthTokenCli() {
                    }

                    public static void main(String[] args) {
                        TestAuth.clearCache();
                        String token = TestAuth.rawToken();
                        if (token == null || token.isBlank()) {
                            System.err.println("AuthTokenCli: could not obtain token — check auth.provider and chain settings");
                            System.exit(1);
                        }
                        System.out.print(token);
                    }
                }
                """.formatted(pkg);
    }

    private static String loadSupportSnippet(String fileName, String targetPackage) throws IOException {
        String content;
        Path devPath = Paths.get("src/main/java/egain/oassdk/testsupport/auth", fileName);
        if (Files.isRegularFile(devPath)) {
            content = Files.readString(devPath, StandardCharsets.UTF_8);
        } else {
            try (InputStream in = TestSupportGenerator.class.getResourceAsStream("/test-support-snippets/" + fileName)) {
                if (in == null) {
                    throw new IOException("Missing test-support snippet: " + fileName);
                }
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return content.replaceFirst("package egain\\.oassdk\\.testsupport\\.auth;",
                "package " + targetPackage + ";");
    }

    private static String requestBodyFactorySource(String pkg) {
        return """
                package %s;

                import java.io.InputStream;
                import java.nio.charset.StandardCharsets;

                public final class RequestBodyFactory {
                    private final String operationId;
                    private String violationPath;
                    private String violationKind;
                    private String violationValue;

                    private RequestBodyFactory(String operationId) {
                        this.operationId = operationId;
                    }

                    public static RequestBodyFactory forOperation(String operationId) {
                        return new RequestBodyFactory(operationId);
                    }

                    public String valid() {
                        return build();
                    }

                    public RequestBodyFactory withViolation(String bodyPath, String kind, String value) {
                        this.violationPath = bodyPath;
                        this.violationKind = kind;
                        this.violationValue = value;
                        return this;
                    }

                    public String build() {
                        String resource = "bodies/" + operationId + ".json";
                        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                            if (in == null) {
                                return "{}";
                            }
                            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            String bound = RequestBodyEnv.bind(json);
                            return applyViolation(bound);
                        } catch (Exception e) {
                            return "{}";
                        }
                    }

                    private String applyViolation(String json) {
                        if (violationPath == null || violationPath.isBlank() || violationKind == null || violationKind.isBlank()) {
                            return json;
                        }
                        String field = simpleFieldName(violationPath);
                        if (field == null || field.isBlank()) {
                            return json;
                        }
                        String needle = "\\""+ field + "\\":";
                        int idx = json.indexOf(needle);
                        if (idx < 0) {
                            return json;
                        }
                        int valueStart = idx + needle.length();
                        int valueEnd = findValueEnd(json, valueStart);
                        if (valueEnd <= valueStart) {
                            return json;
                        }
                        String replacement = switch (violationKind) {
                            case "NULL" -> "null";
                            case "MISSING" -> null;
                            case "TOO_LONG" -> "\\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\\"";
                            case "TOO_SHORT" -> "\\"\\"";
                            case "WRONG_TYPE" -> "12345";
                            case "BAD_ENUM", "BAD_PATTERN" -> "\\"__INVALID__\\"";
                            case "VALUE" -> violationValue == null ? "\\"\\"" : "\\""+ escapeJson(violationValue) + "\\"";
                            default -> null;
                        };
                        if (replacement == null) {
                            int commaStart = idx;
                            while (commaStart > 0 && Character.isWhitespace(json.charAt(commaStart - 1))) {
                                commaStart--;
                            }
                            if (commaStart > 0 && json.charAt(commaStart - 1) == ',') {
                                commaStart--;
                            }
                            int removeStart = commaStart;
                            int removeEnd = valueEnd;
                            while (removeEnd < json.length() && Character.isWhitespace(json.charAt(removeEnd))) {
                                removeEnd++;
                            }
                            if (removeEnd < json.length() && json.charAt(removeEnd) == ',') {
                                removeEnd++;
                            }
                            return json.substring(0, removeStart) + json.substring(removeEnd);
                        }
                        return json.substring(0, valueStart) + replacement + json.substring(valueEnd);
                    }

                    private static String simpleFieldName(String bodyPath) {
                        String normalized = bodyPath;
                        int dot = normalized.lastIndexOf('.');
                        if (dot >= 0 && dot + 1 < normalized.length()) {
                            normalized = normalized.substring(dot + 1);
                        }
                        int bracket = normalized.indexOf('[');
                        if (bracket > 0) {
                            normalized = normalized.substring(0, bracket);
                        }
                        return normalized;
                    }

                    private static int findValueEnd(String json, int start) {
                        int i = start;
                        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                            i++;
                        }
                        if (i >= json.length()) {
                            return start;
                        }
                        char c = json.charAt(i);
                        if (c == '"') {
                            i++;
                            while (i < json.length()) {
                                if (json.charAt(i) == '"' && json.charAt(i - 1) != '\\\\') {
                                    return i + 1;
                                }
                                i++;
                            }
                            return json.length();
                        }
                        int depth = 0;
                        while (i < json.length()) {
                            char ch = json.charAt(i);
                            if (ch == '{' || ch == '[') {
                                depth++;
                            } else if (ch == '}' || ch == ']') {
                                if (depth == 0) {
                                    return i;
                                }
                                depth--;
                            } else if (ch == ',' && depth == 0) {
                                return i;
                            }
                            i++;
                        }
                        return json.length();
                    }

                    private static String escapeJson(String value) {
                        return value.replace("\\\\", "\\\\\\\\").replace("\\"", "\\\\\\"");
                    }
                }
                """.formatted(pkg);
    }

    private static String testAuthSource(String pkg) {
        return """
                package %s;

                /**
                 * Resolves bearer token using configured AuthProvider.
                 */
                public final class TestAuth {

                    private static volatile String cachedToken;
                    private static volatile AuthProvider provider;

                    private TestAuth() {
                    }

                    public static void clearCache() {
                        cachedToken = null;
                        provider = null;
                    }

                    public static String rawToken() {
                        if (cachedToken != null && !cachedToken.isBlank()) {
                            return cachedToken;
                        }
                        String t = authProvider().getToken();
                        if (t != null && !t.isBlank()) {
                            cachedToken = t.trim();
                            return cachedToken;
                        }
                        return "";
                    }

                    public static String tokenForScheme(String scheme) {
                        String fromEnv = TestEnv.tokenForScheme(scheme);
                        if (fromEnv != null && !fromEnv.isBlank()) {
                            return fromEnv;
                        }
                        return rawToken();
                    }

                    public static void cacheToken(String token) {
                        if (token != null && !token.isBlank()) {
                            cachedToken = token.trim();
                        }
                    }

                    public static String invalidToken() {
                        return authProvider().getInvalidToken();
                    }

                    private static AuthProvider authProvider() {
                        if (provider != null) {
                            return provider;
                        }
                        String configured = TestEnv.get("auth.provider", "static").toLowerCase(java.util.Locale.ROOT);
                        provider = switch (configured) {
                            case "chain" -> new HttpChainAuth();
                            case "curl" -> new CurlLoginAuth();
                            default -> new StaticTokenAuth();
                        };
                        return provider;
                    }

                    public static String bearerHeader() {
                        String t = rawToken();
                        return t.isEmpty() ? "" : "Bearer " + t;
                    }
                }
                """.formatted(pkg);
    }

    private static String testHttpSource(String pkg) {
        return """
                package %s;

                import javax.net.ssl.SSLContext;
                import javax.net.ssl.TrustManager;
                import javax.net.ssl.X509TrustManager;
                import java.net.http.HttpClient;
                import java.security.cert.X509Certificate;
                import java.time.Duration;

                public final class TestHttp {

                    private TestHttp() {
                    }

                    public static HttpClient client() {
                        HttpClient.Builder builder = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(30));
                        if (!TestEnv.tlsVerify()) {
                            try {
                                TrustManager[] trustAll = new TrustManager[]{
                                        new X509TrustManager() {
                                            public void checkClientTrusted(X509Certificate[] c, String a) {}
                                            public void checkServerTrusted(X509Certificate[] c, String a) {}
                                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                                        }
                                };
                                SSLContext ssl = SSLContext.getInstance("TLS");
                                ssl.init(null, trustAll, new java.security.SecureRandom());
                                builder.sslContext(ssl);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to configure TLS trust", e);
                            }
                        }
                        return builder.build();
                    }
                }
                """.formatted(pkg);
    }

    private static String testClientSource(String pkg) {
        return """
                package %s;

                import io.restassured.RestAssured;
                import io.restassured.builder.RequestSpecBuilder;
                import io.restassured.specification.RequestSpecification;

                import static io.restassured.RestAssured.given;

                public final class TestClient {

                    private TestClient() {
                    }

                    public static void configureRestAssured() {
                        RestAssured.baseURI = TestEnv.baseUrl();
                        RestAssured.useRelaxedHTTPSValidation();
                        RestAssured.requestSpecification = new RequestSpecBuilder()
                                .addHeader("Accept-Language", TestEnv.acceptLanguage())
                                .build();
                    }

                    public static RequestSpecification givenAuth() {
                        RequestSpecification spec = given()
                                .header("Accept", "application/json")
                                .header("Accept-Language", TestEnv.acceptLanguage());
                        String token = TestAuth.rawToken();
                        if (token != null && !token.isEmpty()) {
                            spec = spec.header("Authorization", "Bearer " + token);
                        }
                        return spec;
                    }
                }
                """.formatted(pkg);
    }

    private static String testContextSource(String pkg) {
        return """
                package %s;

                import java.util.ArrayList;
                import java.util.Collections;
                import java.util.List;
                import java.util.concurrent.CopyOnWriteArrayList;

                /** Shared state for workflow and lifecycle tests. */
                public final class TestContext {

                    private static final CopyOnWriteArrayList<String> CREATED_IDS = new CopyOnWriteArrayList<>();
                    private static volatile String bootstrapParentFolderId;
                    private static volatile String bootstrapHierarchyRootId;
                    private static volatile String disposableFolderId;

                    private TestContext() {
                    }

                    public static void trackCreatedId(String id) {
                        if (id != null && !id.isBlank()) {
                            CREATED_IDS.add(id);
                        }
                    }

                    public static List<String> createdIds() {
                        return Collections.unmodifiableList(new ArrayList<>(CREATED_IDS));
                    }

                    public static void clearCreatedIds() {
                        CREATED_IDS.clear();
                    }

                    public static String bootstrapParentFolderId() {
                        return bootstrapParentFolderId;
                    }

                    public static void setBootstrapParentFolderId(String id) {
                        bootstrapParentFolderId = id;
                    }

                    public static String bootstrapHierarchyRootId() {
                        return bootstrapHierarchyRootId;
                    }

                    public static void setBootstrapHierarchyRootId(String id) {
                        bootstrapHierarchyRootId = id;
                    }

                    public static String disposableFolderId() {
                        return disposableFolderId;
                    }

                    public static void setDisposableFolderId(String id) {
                        disposableFolderId = id;
                    }

                    private static volatile boolean hierarchyTreeConfigured;

                    public static boolean hierarchyTreeConfigured() {
                        return hierarchyTreeConfigured;
                    }

                    public static void setHierarchyTreeConfigured(boolean configured) {
                        hierarchyTreeConfigured = configured;
                    }
                }
                """.formatted(pkg);
    }

    private static String requestBodyEnvSource(String pkg) {
        return """
                package %s;

                /**
                 * Binds request JSON bodies to {@link TestEnv} at runtime (parent folder, user IDs, etc.).
                 */
                public final class RequestBodyEnv {

                    private RequestBodyEnv() {
                    }

                    public static String bind(String json) {
                        if (json == null || json.isBlank()) {
                            return json;
                        }
                        String bound = json;
                        bound = bound.replace("${test.parent.folder.id}", TestEnv.parentFolderId());
                        bound = bound.replace("${test.filter.parent.id}", TestEnv.get("test.filter.parent.id", TestEnv.parentFolderId()));
                        bound = bound.replace("${test.user.id}", TestEnv.userId());
                        bound = bound.replace("${test.user.group.id}", TestEnv.userGroupId());
                        bound = bound.replace("${test.department.id}", TestEnv.departmentId());
                        bound = replaceParentId(bound, TestEnv.parentFolderId());
                        bound = replacePermissionUserIds(bound);
                        bound = uniqueArticleVersionNames(bound);
                        if (!bound.contains("\\"name\\"") && bound.contains("createFolder")) {
                            bound = bound.replaceFirst("\\\\{", "{\\"name\\":\\"SDK-generated-folder\\",");
                        }
                        return bound;
                    }

                    private static String uniqueArticleVersionNames(String json) {
                        if (!json.contains("\\"versions\\"") || !json.contains("\\"articleType\\"")) {
                            return json;
                        }
                        long ts = System.currentTimeMillis();
                        return json.replaceFirst(
                                "(\\\\"name\\\\"\\\\s*:\\\\s*\\\\\\")([^\\\\\\"]*)(\\\\\\")",
                                "$1$2-" + ts + "$3");
                    }

                    private static String replaceParentId(String json, String parentId) {
                        if (parentId == null || parentId.isBlank()) {
                            return json;
                        }
                        return json.replaceAll(
                                "\\"parent\\"\\\\s*:\\\\s*\\\\{[^}]*\\\\\\"id\\\\\\"\\\\s*:\\\\s*\\\\\\"[^\\\\\\"]*\\\\\\"",
                                "\\"parent\\":{\\"id\\":\\"" + parentId + "\\"}");
                    }

                    private static String replacePermissionUserIds(String json) {
                        if (json == null || json.isBlank()) {
                            return json;
                        }
                        String userId = TestEnv.userId();
                        String groupId = TestEnv.userGroupId();
                        String result = json;
                        if (userId != null && !userId.isBlank()) {
                            result = result.replaceAll(
                                    "(\\\\\\"user\\\\\\"\\\\s*:\\\\s*\\\\{[^}]*\\\\\\"id\\\\\\"\\\\s*:\\\\s*\\\\\\")[^\\\\\\"]*(\\\\\\")",
                                    "$1" + userId + "$2");
                        }
                        if (groupId != null && !groupId.isBlank()) {
                            result = result.replaceAll(
                                    "(\\\\\\"group\\\\\\"\\\\s*:\\\\s*\\\\{[^}]*\\\\\\"id\\\\\\"\\\\s*:\\\\s*\\\\\\")[^\\\\\\"]*(\\\\\\")",
                                    "$1" + groupId + "$2");
                        }
                        return result;
                    }
                }
                """.formatted(pkg);
    }

    private static String egainAuthSource(String pkg) {
        return """
                package %s;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;

                /**
                 * eGain v20 session login + advisor OAuth2 bearer exchange.
                 * Configure auth.login.base, auth.username, auth.password in test-env.properties.
                 */
                public final class EgainAuth {

                    private static volatile String cachedSessionId;
                    private static volatile String cachedBearer;

                    private EgainAuth() {
                    }

                    public static void clearCache() {
                        cachedSessionId = null;
                        cachedBearer = null;
                    }

                    public static String sessionId() {
                        if (cachedSessionId != null && !cachedSessionId.isBlank()) {
                            return cachedSessionId;
                        }
                        ensureLogin();
                        return cachedSessionId;
                    }

                    public static String fetchBearerToken() {
                        if (cachedBearer != null && !cachedBearer.isBlank()) {
                            return cachedBearer;
                        }
                        String username = TestEnv.get("auth.username", null);
                        String password = TestEnv.get("auth.password", null);
                        if (username == null || password == null || username.isBlank() || password.isBlank()) {
                            return null;
                        }
                        String loginBase = TestEnv.loginBase();
                        if (loginBase == null || loginBase.isBlank()) {
                            return null;
                        }
                        try {
                            if (!ensureLogin()) {
                                return null;
                            }
                            String bearer = exchangeBearer(loginBase, cachedSessionId);
                            if (bearer != null) {
                                cachedBearer = bearer;
                                TestAuth.cacheToken(bearer);
                            }
                            return bearer;
                        } catch (Exception e) {
                            System.err.println("EgainAuth: " + e.getMessage());
                            return null;
                        }
                    }

                    private static boolean ensureLogin() throws Exception {
                        if (cachedSessionId != null && !cachedSessionId.isBlank()) {
                            return true;
                        }
                        String username = TestEnv.get("auth.username", null);
                        String password = TestEnv.get("auth.password", null);
                        if (username == null || password == null || username.isBlank() || password.isBlank()) {
                            return false;
                        }
                        String loginBase = TestEnv.loginBase();
                        if (loginBase == null || loginBase.isBlank()) {
                            return false;
                        }
                        HttpClient client = TestHttp.client();
                        String loginUrl = loginBase.endsWith("/")
                                ? loginBase + "authentication/user/login"
                                : loginBase + "/authentication/user/login";
                        String sessionBody = "{\\"username\\":\\"" + escapeJson(username)
                                + "\\",\\"password\\":\\"" + escapeJson(password) + "\\"}";
                        HttpRequest login = HttpRequest.newBuilder()
                                .uri(URI.create(loginUrl))
                                .timeout(Duration.ofSeconds(30))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
                                .build();
                        HttpResponse<String> loginResp = client.send(login, HttpResponse.BodyHandlers.ofString());
                        if (loginResp.statusCode() >= 300) {
                            System.err.println("EgainAuth: login failed HTTP " + loginResp.statusCode());
                            return false;
                        }
                        cachedSessionId = loginResp.headers().firstValue("X-egain-session").orElse(null);
                        if (cachedSessionId == null) {
                            cachedSessionId = extractJsonString(loginResp.body(), "sessionId");
                        }
                        return cachedSessionId != null && !cachedSessionId.isBlank();
                    }

                    private static String exchangeBearer(String loginBase, String sessionId) throws Exception {
                        String exchangePath = TestEnv.exchangePath();
                        String exchangeUrl = loginBase.endsWith("/")
                                ? loginBase.substring(0, loginBase.length() - 1) + exchangePath
                                : loginBase + exchangePath;
                        HttpRequest exchange = HttpRequest.newBuilder()
                                .uri(URI.create(exchangeUrl))
                                .timeout(Duration.ofSeconds(30))
                                .header("Accept", "application/json")
                                .header("X-egain-session", sessionId)
                                .GET()
                                .build();
                        HttpResponse<String> exchangeResp = TestHttp.client().send(exchange, HttpResponse.BodyHandlers.ofString());
                        if (exchangeResp.statusCode() >= 300) {
                            System.err.println("EgainAuth: exchange failed HTTP " + exchangeResp.statusCode());
                            return null;
                        }
                        String bearer = extractJsonString(exchangeResp.body(), "access_token");
                        if (bearer == null) {
                            bearer = extractJsonString(exchangeResp.body(), "token");
                        }
                        return bearer;
                    }

                    public static void main(String[] args) {
                        String token = fetchBearerToken();
                        if (token != null && !token.isBlank()) {
                            System.out.print(token);
                        } else {
                            System.err.println("EgainAuth: could not obtain bearer token");
                            System.exit(1);
                        }
                    }

                    private static String extractJsonString(String json, String field) {
                        if (json == null) {
                            return null;
                        }
                        String needle = "\\"" + field + "\\":\\"";
                        int i = json.indexOf(needle);
                        if (i < 0) {
                            return null;
                        }
                        int start = i + needle.length();
                        int end = json.indexOf('"', start);
                        return end > start ? json.substring(start, end) : null;
                    }

                    private static String escapeJson(String s) {
                        return s.replace("\\\\", "\\\\\\\\").replace("\\"", "\\\\\\"");
                    }
                }
                """.formatted(pkg);
    }

    private static String egainAsyncTaskHelperSource(String pkg) {
        return """
                package %s;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;

                /** Poll v20 async task completion after v4 DELETE 202. */
                public final class EgainAsyncTaskHelper {

                    private EgainAsyncTaskHelper() {
                    }

                    public static String extractTaskId(HttpResponse<String> accepted) {
                        if (accepted == null) {
                            return null;
                        }
                        String location = accepted.headers().firstValue("Location").orElse(null);
                        if (location != null) {
                            int slash = location.lastIndexOf('/');
                            if (slash >= 0 && slash < location.length() - 1) {
                                return location.substring(slash + 1).replaceAll("[^0-9]", "");
                            }
                        }
                        String body = accepted.body();
                        if (body != null) {
                            for (String key : new String[]{"taskId", "id"}) {
                                String needle = "\\"" + key + "\\":\\"";
                                int i = body.indexOf(needle);
                                if (i >= 0) {
                                    int start = i + needle.length();
                                    int end = body.indexOf('"', start);
                                    if (end > start) {
                                        return body.substring(start, end);
                                    }
                                }
                            }
                        }
                        return null;
                    }

                    public static void pollUntilComplete(HttpClient client, HttpResponse<String> accepted,
                                                         Duration timeout) throws Exception {
                        String taskId = extractTaskId(accepted);
                        if (taskId == null || taskId.isBlank()) {
                            return;
                        }
                        String sessionId = EgainAuth.sessionId();
                        if (sessionId == null || sessionId.isBlank()) {
                            return;
                        }
                        String pollUrl = TestEnv.resolveSystemUrl("/system/ws/v20/async/task/" + taskId);
                        long deadline = System.nanoTime() + timeout.toNanos();
                        while (System.nanoTime() < deadline) {
                            HttpRequest poll = HttpRequest.newBuilder()
                                    .uri(URI.create(pollUrl))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Accept", "application/json")
                                    .header("X-egain-session", sessionId)
                                    .GET()
                                    .build();
                            HttpResponse<String> r = client.send(poll, HttpResponse.BodyHandlers.ofString());
                            if (r.statusCode() == 200 || r.statusCode() == 204) {
                                return;
                            }
                            Thread.sleep(500);
                        }
                        throw new AssertionError("Async task " + taskId + " did not complete within " + timeout);
                    }
                }
                """.formatted(pkg);
    }

    private static String egainInternalKbHelperSource(String pkg) {
        return """
                package %s;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;

                /** Authoritative folder state via v20 internal KB GET. */
                public final class EgainInternalKbHelper {

                    private EgainInternalKbHelper() {
                    }

                    public static HttpResponse<String> getFolder(HttpClient client, String folderId, Duration timeout)
                            throws Exception {
                        String sessionId = EgainAuth.sessionId();
                        if (sessionId == null || sessionId.isBlank()) {
                            throw new AssertionError("No session for internal KB GET");
                        }
                        String url = TestEnv.resolveSystemUrl("/system/ws/v20/internal/kb/folder/" + folderId);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(timeout)
                                .header("Accept", "application/json")
                                .header("X-egain-session", sessionId)
                                .GET()
                                .build();
                        return client.send(req, HttpResponse.BodyHandlers.ofString());
                    }

                    public static void assertFolderMatches(HttpClient client, String folderId, String createOrEditJson,
                                                           Duration timeout) throws Exception {
                        if (!TestEnv.verifyInternalKb()) {
                            return;
                        }
                        HttpResponse<String> resp = getFolder(client, folderId, timeout);
                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            throw new AssertionError("Internal KB GET failed: " + resp.statusCode());
                        }
                        String body = resp.body();
                        assertFieldEqual(createOrEditJson, body, "name");
                        assertFieldEqual(createOrEditJson, body, "description");
                        assertNestedFieldEqual(createOrEditJson, body, "parent", "id");
                    }

                    public static void assertFolderGone(HttpClient client, String folderId, Duration timeout)
                            throws Exception {
                        if (!TestEnv.verifyInternalKb()) {
                            return;
                        }
                        long deadline = System.nanoTime() + timeout.toNanos();
                        while (System.nanoTime() < deadline) {
                            HttpResponse<String> resp = getFolder(client, folderId, timeout);
                            if (resp.statusCode() == 404 || resp.statusCode() == 204) {
                                return;
                            }
                            Thread.sleep(500);
                        }
                        throw new AssertionError("Folder " + folderId + " still exists after delete");
                    }

                    public static void syncDeleteFolder(HttpClient client, String folderId, Duration timeout)
                            throws Exception {
                        String sessionId = EgainAuth.sessionId();
                        if (sessionId == null || sessionId.isBlank()) {
                            return;
                        }
                        String url = TestEnv.resolveSystemUrl("/system/ws/v20/internal/kb/folder/" + folderId);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(timeout)
                                .header("Accept", "application/json")
                                .header("X-egain-session", sessionId)
                                .DELETE()
                                .build();
                        client.send(req, HttpResponse.BodyHandlers.discarding());
                    }

                    private static void assertFieldEqual(String expectedJson, String actualJson, String field) {
                        String expected = extractJsonString(expectedJson, field);
                        if (expected == null) {
                            return;
                        }
                        String actual = extractJsonString(actualJson, field);
                        if (actual != null && !expected.equals(actual)) {
                            throw new AssertionError("Internal KB field '" + field + "' mismatch");
                        }
                    }

                    private static void assertNestedFieldEqual(String expectedJson, String actualJson,
                                                               String object, String field) {
                        String expected = extractNestedJsonString(expectedJson, object, field);
                        if (expected == null) {
                            return;
                        }
                        String actual = extractNestedJsonString(actualJson, object, field);
                        if (actual != null && !expected.equals(actual)) {
                            throw new AssertionError("Internal KB " + object + "." + field + " mismatch");
                        }
                    }

                    private static String extractJsonString(String json, String field) {
                        if (json == null) {
                            return null;
                        }
                        String needle = "\\"" + field + "\\":\\"";
                        int i = json.indexOf(needle);
                        if (i < 0) {
                            return null;
                        }
                        int start = i + needle.length();
                        int end = json.indexOf('"', start);
                        return end > start ? json.substring(start, end) : null;
                    }

                    private static String extractNestedJsonString(String json, String object, String field) {
                        if (json == null) {
                            return null;
                        }
                        String objNeedle = "\\"" + object + "\\":{";
                        int objIdx = json.indexOf(objNeedle);
                        if (objIdx < 0) {
                            return null;
                        }
                        int brace = json.indexOf('{', objIdx);
                        int depth = 0;
                        for (int i = brace; i < json.length(); i++) {
                            char c = json.charAt(i);
                            if (c == '{') {
                                depth++;
                            } else if (c == '}') {
                                depth--;
                                if (depth == 0) {
                                    return extractJsonString(json.substring(brace, i + 1), field);
                                }
                            }
                        }
                        return null;
                    }
                }
                """.formatted(pkg);
    }

    private static String testEnvProperties(String defaultBaseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated test environment — copy test-env.local.properties.example for secrets\n");
        if (defaultBaseUrl == null || defaultBaseUrl.isBlank()) {
            sb.append("base.url=\n");
        } else {
            sb.append("base.url=").append(defaultBaseUrl).append("\n");
        }
        sb.append("accept.language=en-US\n");
        sb.append("tls.verify=false\n\n");
        sb.append("# Test data IDs (discover via GET /folders?departmentId=...)\n");
        sb.append("test.department.id=\n");
        sb.append("test.parent.folder.id=\n");
        sb.append("test.filter.parent.id=\n");
        sb.append("test.hierarchy.root.folder.id=\n");
        sb.append("test.folder.id=\n");
        sb.append("test.user.id=\n");
        sb.append("test.user.group.id=\n");
        sb.append("test.prompt.id=\n");
        sb.append("test.topic.parent.folder.id=\n");
        sb.append("test.folder.no.delete.permission.id=\n");
        sb.append("test.folder.no.create.permission.id=\n\n");
        sb.append("test.pagination.pageNum=1\n");
        sb.append("test.pagination.pageSize=10\n\n");
        sb.append("test.bootstrap.enabled=false\n");
        sb.append("test.bootstrap.hierarchy.enabled=false\n");
        sb.append("test.destructive.enabled=false\n");
        sb.append("test.validateResponseSchema=false\n");
        sb.append("test.include.operations=\n");
        sb.append("schemathesis.include.operations=\n");
        sb.append("test.flows.dir=docs/examples\n");
        sb.append("test.flows.manifest=\n\n");
        sb.append("# Auth provider settings (static | chain | curl)\n");
        sb.append("auth.provider=static\n");
        sb.append("auth.token=\n");
        sb.append("auth.login.base=\n");
        sb.append("auth.login.curl=\n");
        sb.append("auth.token.header=Authorization\n");
        sb.append("auth.token.body.path=access_token\n");
        sb.append("auth.token.prefix=Bearer \n");
        sb.append("auth.username=\n");
        sb.append("auth.password=\n");
        sb.append("# Multi-step chain (auth.provider=chain) — see examples/auth-profiles/\n");
        sb.append("#auth.chain.1.url=${auth.login.base}/authentication/user/login?forceLogin=yes\n");
        sb.append("#auth.chain.1.method=POST\n");
        sb.append("#auth.chain.1.header.Content-Type=application/json\n");
        sb.append("#auth.chain.1.body={\"userName\":\"${auth.username}\",\"password\":\"${auth.password}\"}\n");
        sb.append("#auth.chain.1.extract.header=x-egain-session\n");
        sb.append("#auth.chain.1.save=session\n");
        sb.append("#auth.chain.2.url=${auth.login.base}/authentication/user/advisor/oauth2/token\n");
        sb.append("#auth.chain.2.method=GET\n");
        sb.append("#auth.chain.2.header.X-egain-session=${session}\n");
        sb.append("#auth.chain.2.header.accept=application/json, text/plain, */*\n");
        sb.append("#auth.chain.2.extract.json=access_token\n");
        sb.append("#auth.chain.final=access_token\n");
        return sb.toString();
    }

    private static String testEnvLocalExample() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Local overrides — do not commit\n");
        sb.append("base.url=https://api.example.com\n");
        sb.append("accept.language=en-US\n");
        sb.append("tls.verify=false\n\n");
        sb.append("auth.provider=static\n");
        sb.append("auth.token=PUT_TOKEN_HERE\n");
        sb.append("# Or use chain auth — copy from examples/auth-profiles/egain-v20-session-oauth.properties\n");
        sb.append("#auth.provider=chain\n");
        sb.append("#auth.login.base=https://host.example/system/ws/v20\n");
        sb.append("#auth.username=\n");
        sb.append("#auth.password=\n");
        sb.append("test.department.id=1001\n");
        sb.append("test.parent.folder.id=\n");
        sb.append("test.filter.parent.id=\n");
        sb.append("test.hierarchy.root.folder.id=\n");
        sb.append("test.folder.id=\n");
        sb.append("test.user.id=\n");
        sb.append("test.user.group.id=\n");
        sb.append("test.bootstrap.enabled=false\n");
        sb.append("test.bootstrap.hierarchy.enabled=false\n");
        sb.append("test.destructive.enabled=false\n");
        sb.append("test.include.operations=\n");
        sb.append("test.flows.dir=docs/examples\n");
        sb.append("test.flows.manifest=\n");
        return sb.toString();
    }

    private static String testFilterScript() {
        return """
                #!/usr/bin/env bash
                # Parse test.include.operations from property files for Surefire/JUnit tag filtering.
                # Source from run-all.sh / run-smoke.sh after ROOT and TEST_ENV_FILE are set.
                load_include_operations() {
                  local file line key val result=""
                  for file in "${TEST_ENV_FILE:-}" "$ROOT/test-env.properties" "$ROOT/test-env.local.properties"; do
                    [[ -z "$file" || ! -f "$file" ]] && continue
                    while IFS= read -r line || [[ -n "$line" ]]; do
                      [[ "$line" =~ ^[[:space:]]*# ]] && continue
                      [[ "$line" =~ ^[[:space:]]*test\\.include\\.operations[[:space:]]*= ]] || continue
                      val="${line#*=}"
                      val="$(echo "$val" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
                      result="$val"
                    done < "$file"
                  done
                  if [[ -z "$result" ]]; then
                    for file in "${TEST_ENV_FILE:-}" "$ROOT/test-env.properties" "$ROOT/test-env.local.properties"; do
                      [[ -z "$file" || ! -f "$file" ]] && continue
                      while IFS= read -r line || [[ -n "$line" ]]; do
                        [[ "$line" =~ ^[[:space:]]*# ]] && continue
                        [[ "$line" =~ ^[[:space:]]*schemathesis\\.include\\.operations[[:space:]]*= ]] || continue
                        val="${line#*=}"
                        val="$(echo "$val" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
                        result="$val"
                      done < "$file"
                    done
                  fi
                  SUREFIRE_GROUPS="$result"
                  export SUREFIRE_GROUPS
                }

                mvn_groups_args() {
                  if [[ -n "${SUREFIRE_GROUPS:-}" ]]; then
                    printf '%s' "-Dgroups=${SUREFIRE_GROUPS}"
                  fi
                }
                """;
    }

    private static String runSmokeScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                ROOT="$(cd "$(dirname "$0")" && pwd)"
                export TEST_ENV_FILE="${TEST_ENV_FILE:-$ROOT/test-env.properties}"
                # shellcheck disable=SC1091
                source "$ROOT/test-filter.sh"
                load_include_operations
                GROUPS_ARG="$(mvn_groups_args)"
                if [[ -n "$GROUPS_ARG" ]]; then
                  echo "Smoke filter: operations=$SUREFIRE_GROUPS"
                  cd "$ROOT/contract"
                  # shellcheck disable=SC2086
                  mvn -q test $GROUPS_ARG || true
                  cd "$ROOT/integration"
                  # shellcheck disable=SC2086
                  mvn -q test $GROUPS_ARG || true
                else
                  cd "$ROOT/contract"
                  mvn -q test -Dtest='FoldersApiTest#testGetSubFolders_ValidRequest' || true
                  cd "$ROOT/integration"
                  mvn -q test -Dtest='FoldersIntegrationTest#testGetSubFolders_Success_ClientApplication' || true
                fi
                echo "Smoke tests finished (see output above)."
                """;
    }

    private static String runSmokeBat() {
        return """
                @echo off
                set ROOT=%~dp0
                if not defined TEST_ENV_FILE set TEST_ENV_FILE=%ROOT%test-env.properties
                cd /d %ROOT%contract
                call mvn -q test -Dtest=FoldersApiTest#testGetSubFolders_ValidRequest
                cd /d %ROOT%integration
                call mvn -q test -Dtest=FoldersIntegrationTest#testGetSubFolders_Success_ClientApplication
                echo Smoke tests finished.
                """;
    }

    private static String aggregatorPom(List<String> modules) {
        StringBuilder moduleXml = new StringBuilder();
        for (String module : modules) {
            moduleXml.append("        <module>").append(module).append("</module>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example.api</groupId>
                    <artifactId>generated-api-tests</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                __MODULES__    </modules>
                </project>
                """.replace("__MODULES__", moduleXml.toString());
    }

    private static String runAllScript(List<String> modules) {
        String mvnModules = String.join(",", modules);
        String taggedModules = modules.stream()
                .filter(m -> !"lifecycle".equals(m))
                .reduce((a, b) -> a + "," + b)
                .orElse(mvnModules);
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                ROOT="$(cd "$(dirname "$0")" && pwd)"
                export TEST_ENV_FILE="${TEST_ENV_FILE:-$ROOT/test-env.properties}"
                # shellcheck disable=SC1091
                source "$ROOT/test-filter.sh"
                load_include_operations
                GROUPS_ARG="$(mvn_groups_args)"
                PROFILE="${TEST_PROFILE:-smoke}"
                echo "Running test profile: $PROFILE"
                if [[ -n "${SUREFIRE_GROUPS:-}" ]]; then
                  echo "Operation filter: $SUREFIRE_GROUPS"
                fi
                ./run-smoke.sh
                if [[ "$PROFILE" != "smoke" ]]; then
                  if [[ -n "$GROUPS_ARG" ]]; then
                    # shellcheck disable=SC2086
                    mvn -q test -pl __TAGGED_MODULES__ $GROUPS_ARG
                    if [[ ",__MVN_MODULES__," == *",lifecycle,"* ]]; then
                      mvn -q test -pl lifecycle
                    fi
                  else
                    mvn -q test -pl __MVN_MODULES__
                  fi
                else
                  if [[ -n "$GROUPS_ARG" ]]; then
                    # shellcheck disable=SC2086
                    mvn -q test -pl integration $GROUPS_ARG
                    if [[ ",__MVN_MODULES__," == *",lifecycle,"* ]]; then
                      mvn -q test -pl lifecycle
                    fi
                  else
                    mvn -q test -pl integration
                  fi
                fi
                if [[ -d "$ROOT/schemathesis" && -x "$ROOT/schemathesis/run-schemathesis.sh" ]]; then
                  (cd "$ROOT/schemathesis" && ./run-schemathesis.sh) || true
                fi
                if [[ -x "$ROOT/run-tests.sh" ]]; then
                  ./run-tests.sh || true
                fi
                echo "run-all finished."
                """
                .replace("__MVN_MODULES__", mvnModules)
                .replace("__TAGGED_MODULES__", taggedModules);
    }

    private static String runAllBat(List<String> modules) {
        String mvnModules = String.join(",", modules);
        return """
                @echo off
                set ROOT=%~dp0
                if not defined TEST_ENV_FILE set TEST_ENV_FILE=%ROOT%test-env.properties
                if not defined TEST_PROFILE set TEST_PROFILE=full
                call %ROOT%run-smoke.bat
                if /I "%TEST_PROFILE%"=="smoke" (
                  cd /d %ROOT%integration
                  call mvn -q test
                ) else (
                  cd /d %ROOT%
                  call mvn -q test -pl __MVN_MODULES__
                )
                echo run-all finished.
                """.replace("__MVN_MODULES__", mvnModules);
    }

    private static String fetchTokenScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                ROOT="$(cd "$(dirname "$0")" && pwd)"
                export TEST_ENV_FILE="${TEST_ENV_FILE:-$ROOT/test-env.properties}"
                cd "$ROOT/contract"
                mvn -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \\
                  -Dexec.mainClass=com.example.api.support.AuthTokenCli \\
                  -Dexec.classpathScope=test
                """;
    }

    private static String readmeTests() {
        return """
                # Generated API Tests

                ## Quick start

                1. Copy `test-env.local.properties.example` to `test-env.local.properties` and fill in values.
                2. Run all suites: `./run-all.sh` (or `run-all.bat` on Windows).
                3. Smoke only: `./run-smoke.sh`.
                4. Profile `smoke`: `TEST_PROFILE=smoke ./run-all.sh` (integration + schemathesis, no contract matrix).

                ## Configuration

                All Java test modules load `test-env.properties` via `TestEnv`. Environment variables override properties.

                Key properties: `base.url`, `test.department.id`, `test.parent.folder.id`, `test.hierarchy.root.folder.id`, `accept.language`.

                ## Suite overlap

                | Suite | Role |
                |-------|------|
                | Integration (JUnit) | Operation-level end-to-end checks |
                | Lifecycle (Flow DSL) | Multi-step flow scripts from `test.flows.dir` |
                | Contract (RestAssured) | Contract/param matrix against live API |
                | Schemathesis | Contract fuzz / property-based exploration |
                | Postman/Newman | Manual collection replay |

                Use `TEST_PROFILE=smoke` to reduce duplicate coverage between contract and integration.
                
                Auth defaults to `auth.provider=static` and reads `auth.token`.
                For multi-step login, set `auth.provider=chain` and configure `auth.chain.N.*` properties
                (see `examples/auth-profiles/egain-v20-session-oauth.properties` in the SDK repo).
                Run `./fetch-token.sh` to print a token from the configured provider.
                Set `test.include.operations` to run only selected operationIds.
                """
                + "\n\nSet `auth.token` or export `API_TOKEN` for static bearer auth.\n";
    }

    private static String escapeJava(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
