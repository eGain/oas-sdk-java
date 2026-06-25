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

    public static String invalidTestConstants() {
        return """
                private static final String INVALID_PARENT_ID = "not-a-valid-folder-id";
                private static final String INVALID_DEPARTMENT_ID = "not-a-number";
                private static final String INVALID_FOLDER_ID = "invalid-folder";
                private static final String INVALID_PAGE_SIZE = "9999";
                private static final String INVALID_ASSIGNEE_ID = "999999999999999";
                private static final String NONEXISTENT_PARENT_FOLDER_ID = "NONEXISTENT_PARENT_FOLDER_ID";
                """;
    }

    public static String requestBodyBind(String escapedJson) {
        return "RequestBodyEnv.bind(\"" + escapedJson + "\")";
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
            return "TestContext.disposableFolderId() != null ? TestContext.disposableFolderId() : TestEnv.folderId()";
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
            return "TestEnv.promptId()";
        }
        return "\"" + escapeJava(fallbackLiteral) + "\"";
    }

    /**
     * Invalid literals for negative tests — never use OAS example IDs.
     */
    public static String paramValueExpressionNegative(String paramName) {
        String key = paramName.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("department")) {
            return "INVALID_DEPARTMENT_ID";
        }
        if (key.equals("filter[parent]") || key.contains("parent")) {
            return "INVALID_PARENT_ID";
        }
        if (key.equals("folderid") || key.equals("folder_id") || key.contains("folderid")) {
            return "INVALID_FOLDER_ID";
        }
        if (key.equals("$pagesize") || key.equals("pagesize")) {
            return "INVALID_PAGE_SIZE";
        }
        return "\"invalid-value\"";
    }

    public static String destructiveGate() {
        return "        Assumptions.assumeTrue(TestEnv.destructiveEnabled(), \"Skip: test.destructive.enabled=false\");\n";
    }

    public static String escapeJava(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
