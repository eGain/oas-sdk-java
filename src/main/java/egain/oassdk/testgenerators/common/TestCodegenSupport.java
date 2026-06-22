package egain.oassdk.testgenerators.common;

/**
 * Shared Java source fragments emitted into generated test classes.
 */
public final class TestCodegenSupport {

    private TestCodegenSupport() {
    }

    public static String supportImport(String basePackage) {
        return "import " + TestOutputLayout.supportPackage(basePackage) + ".*;\n";
    }

    public static String baseUrlField() {
        return """
                private static String baseUrl() {
                    return TestEnv.baseUrl();
                }
                """;
    }

    public static String acceptLanguageHeader() {
        return ".header(\"Accept-Language\", TestEnv.acceptLanguage())";
    }

    public static String restAssuredInit() {
        return """
                @BeforeAll
                static void initRestAssured() {
                    TestClient.configureRestAssured();
                }
                """;
    }

    public static String httpClientInit() {
        return """
                @BeforeAll
                static void setUpAll() {
                    httpClient = TestHttp.client();
                    String url = baseUrl();
                    if (!IntegrationTestUtils.waitForServer(url, REQUEST_TIMEOUT)) {
                        System.err.println("Warning: Could not reach server at " + url);
                    }
                }
                """;
    }

    public static String buildUriHelper() {
        return """
                private URI buildUri(String path, Map<String, String> queryParams) {
                    StringBuilder uriBuilder = new StringBuilder(baseUrl()).append(path);
                    if (queryParams != null && !queryParams.isEmpty()) {
                        uriBuilder.append("?");
                        boolean first = true;
                        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                            if (!first) uriBuilder.append("&");
                            uriBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                            first = false;
                        }
                    }
                    return URI.create(uriBuilder.toString());
                }
                """;
    }

    public static String tokenHelpers() {
        return """
                private String getTokenClientApplication() {
                    return TestAuth.rawToken();
                }

                private String getTokenAuthenticatedCustomer() {
                    return TestAuth.rawToken();
                }

                private String getPreferredAuthToken() {
                    return TestAuth.rawToken();
                }

                private String getAuthToken() {
                    return TestAuth.rawToken();
                }
                """;
    }

    /**
     * Map OpenAPI parameter names to TestEnv accessor calls for generated tests.
     */
    public static String paramValueExpression(String paramName, String fallbackLiteral) {
        String key = paramName.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("department")) {
            return "TestEnv.departmentId()";
        }
        if (key.equals("filter[parent]") || key.contains("parent")) {
            return "TestEnv.parentFolderId()";
        }
        if (key.equals("folderid") || key.equals("folder_id") || key.contains("folderid")) {
            return "TestEnv.folderId()";
        }
        if (key.equals("$pagenum") || key.equals("pagenum")) {
            return "TestEnv.pageNum()";
        }
        if (key.equals("$pagesize") || key.equals("pagesize")) {
            return "TestEnv.pageSize()";
        }
        if (key.equals("$lang") || key.equals("lang") || key.contains("language")) {
            return "TestEnv.acceptLanguage()";
        }
        if (key.equals("promptid") || key.contains("prompt")) {
            return "TestEnv.get(\"test.prompt.id\", \"" + escapeJava(fallbackLiteral) + "\")";
        }
        return "\"" + escapeJava(fallbackLiteral) + "\"";
    }

    public static String escapeJava(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
