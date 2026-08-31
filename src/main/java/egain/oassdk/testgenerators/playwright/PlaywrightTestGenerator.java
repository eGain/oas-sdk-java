package egain.oassdk.testgenerators.playwright;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;
import egain.oassdk.testgenerators.TestGenerator;
import egain.oassdk.testgenerators.common.TestSpecUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a self-contained Playwright API test suite shaped like
 * eGainDev/playwright-api-tests ({@code tests/generated}, {@code data/generated}, thin API clients).
 * <p>Auth is portable env-based ({@code BASE_URL}, {@code TOKEN}); product-specific login helpers
 * are out of scope for v1.
 */
public class PlaywrightTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework)
            throws GenerationException {
        this.config = config;
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Path root = Paths.get(outputDir, "playwright");
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("apis"));
            Files.createDirectories(root.resolve("utilities"));
            Files.createDirectories(root.resolve("tests").resolve("generated"));
            Files.createDirectories(root.resolve("data").resolve("generated"));

            String apiTitle = TestSpecUtils.getApiTitle(spec);
            String baseUrl = TestSpecUtils.getBaseUrl(spec);
            String apiSlug = toKebabCase(apiTitle);

            writeScaffold(root, apiTitle, baseUrl);
            Map<String, List<OperationInfo>> byTag = collectOperationsByTag(spec);

            for (Map.Entry<String, List<OperationInfo>> entry : byTag.entrySet()) {
                String tag = entry.getKey();
                List<OperationInfo> ops = entry.getValue();
                String tagSlug = toKebabCase(tag);
                String folderSlug = apiSlug + "-" + tagSlug;
                String className = toPascalCase(tag) + "Api";
                String apiFileBase = tagSlug + ".api";

                writeApiClient(root.resolve("apis").resolve(apiFileBase + ".ts"), className, ops);

                Path testsDir = root.resolve("tests").resolve("generated").resolve(folderSlug);
                Path dataDir = root.resolve("data").resolve("generated").resolve(folderSlug);
                Files.createDirectories(testsDir);
                Files.createDirectories(dataDir);

                List<Map<String, Object>> positives = buildPositiveCases(ops, spec);
                Map<String, Object> negatives = buildNegativeCases(ops, spec);

                String posDataName = "tc01-P-" + tagSlug + "-positive.json";
                String negDataName = "tc02-N-" + tagSlug + "-negative.json";
                Files.writeString(dataDir.resolve(posDataName), MAPPER.writeValueAsString(positives),
                        StandardCharsets.UTF_8);
                Files.writeString(dataDir.resolve(negDataName), MAPPER.writeValueAsString(negatives),
                        StandardCharsets.UTF_8);

                Files.writeString(testsDir.resolve("tc01-P-" + tagSlug + "-positive.spec.ts"),
                        generatePositiveSpec(folderSlug, tag, tagSlug, className, apiFileBase, posDataName, ops),
                        StandardCharsets.UTF_8);
                Files.writeString(testsDir.resolve("tc02-N-" + tagSlug + "-negative.spec.ts"),
                        generateNegativeSpec(folderSlug, tag, tagSlug, className, apiFileBase, negDataName, ops),
                        StandardCharsets.UTF_8);
                Files.writeString(testsDir.resolve("README.md"),
                        generateReadme(apiTitle, tag, folderSlug, positives.size(),
                                countNegativeCases(negatives)),
                        StandardCharsets.UTF_8);
            }

            if (byTag.isEmpty()) {
                Path empty = root.resolve("tests").resolve("generated").resolve(apiSlug);
                Files.createDirectories(empty);
                Files.writeString(empty.resolve("README.md"),
                        "# " + apiSlug + "\n\nNo paths found in OpenAPI specification.\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            throw new GenerationException("Failed to generate Playwright tests: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "Playwright Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "playwright";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }

    private void writeScaffold(Path root, String apiTitle, String baseUrl) throws IOException {
        String title = apiTitle != null ? apiTitle : "API";
        String defaultBase = baseUrl != null ? baseUrl : "http://localhost:8080";

        Files.writeString(root.resolve("package.json"), """
                {
                  "name": "oas-sdk-playwright-tests",
                  "version": "1.0.0",
                  "private": true,
                  "type": "module",
                  "scripts": {
                    "test": "playwright test --workers=1",
                    "test:generated": "playwright test tests/generated --project=generated --workers=1"
                  },
                  "devDependencies": {
                    "@playwright/test": "^1.54.0",
                    "@types/node": "^22.7.5",
                    "typescript": "^5.7.3"
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(root.resolve("tsconfig.json"), """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "module": "ESNext",
                    "moduleResolution": "bundler",
                    "strict": true,
                    "esModuleInterop": true,
                    "resolveJsonModule": true,
                    "skipLibCheck": true,
                    "baseUrl": ".",
                    "paths": {
                      "@apis/*": ["apis/*"],
                      "@utilities/*": ["utilities/*"],
                      "@data/*": ["data/*"]
                    }
                  },
                  "include": ["apis/**/*", "utilities/**/*", "tests/**/*", "data/**/*", "playwright.config.ts"]
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(root.resolve("playwright.config.ts"), """
                import { defineConfig } from "@playwright/test";

                export default defineConfig({
                  testDir: "./tests",
                  timeout: 60_000,
                  retries: 0,
                  reporter: [["list"], ["html", { open: "never" }]],
                  use: {
                    baseURL: process.env.BASE_URL || "%s",
                    extraHTTPHeaders: {
                      Accept: "application/json",
                    },
                  },
                  projects: [
                    {
                      name: "generated",
                      testMatch: "generated/**/*.spec.ts",
                    },
                  ],
                });
                """.formatted(escapeTsString(defaultBase)), StandardCharsets.UTF_8);

        Files.writeString(root.resolve("apis").resolve("base.api.ts"), """
                import { APIRequestContext, APIResponse } from "@playwright/test";

                export interface APIRequestOptions {
                  data?: unknown;
                  headers?: Record<string, string>;
                  params?: Record<string, string | number | boolean>;
                  failOnStatusCode?: boolean;
                }

                /**
                 * Minimal portable BaseApi (env auth). When dropping into playwright-api-tests,
                 * prefer the repo's BaseApi and product helpers instead.
                 */
                export class BaseApi {
                  constructor(readonly request: APIRequestContext) {}

                  async get(url: string, options?: APIRequestOptions): Promise<APIResponse> {
                    return this.request.get(url, options);
                  }

                  async post(url: string, options?: APIRequestOptions): Promise<APIResponse> {
                    return this.request.post(url, options);
                  }

                  async put(url: string, options?: APIRequestOptions): Promise<APIResponse> {
                    return this.request.put(url, options);
                  }

                  async patch(url: string, options?: APIRequestOptions): Promise<APIResponse> {
                    return this.request.patch(url, options);
                  }

                  async delete(url: string, options?: APIRequestOptions): Promise<APIResponse> {
                    return this.request.delete(url, options);
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(root.resolve("utilities").resolve("helpers.ts"), """
                import { APIRequestContext, request } from "@playwright/test";

                /**
                 * Creates an APIRequestContext using BASE_URL and optional TOKEN bearer auth.
                 * Portable for oas-sdk output; replace with product LoginApi helpers in playwright-api-tests.
                 */
                export async function getAPIContext(): Promise<APIRequestContext> {
                  const baseURL = process.env.BASE_URL || "http://localhost:8080";
                  const token = process.env.TOKEN || process.env.API_BEARER_TOKEN || process.env.API_TOKEN;
                  const headers: Record<string, string> = {
                    Accept: "application/json",
                    "Content-Type": "application/json",
                  };
                  if (token) {
                    headers.Authorization = `Bearer ${token}`;
                  }
                  return request.newContext({ baseURL, extraHTTPHeaders: headers });
                }

                export async function getAnonymousAPIContext(): Promise<APIRequestContext> {
                  const baseURL = process.env.BASE_URL || "http://localhost:8080";
                  return request.newContext({
                    baseURL,
                    extraHTTPHeaders: {
                      Accept: "application/json",
                      "Content-Type": "application/json",
                    },
                  });
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(root.resolve("README.md"), """
                # %s — Playwright API tests (oas-sdk)

                Generated by OAS SDK Playwright test generator in the conventions of
                [playwright-api-tests](https://github.com/eGainDev/playwright-api-tests).

                ## Setup

                ```bash
                npm install
                npx playwright install
                export BASE_URL=https://your-api-host
                export TOKEN=your-bearer-token   # optional for secured ops
                npm run test:generated
                ```

                ## Layout

                - `apis/` — thin API clients per OpenAPI tag
                - `tests/generated/` — positive / negative specs (`tc##-P|N-*.spec.ts`)
                - `data/generated/` — data-driven JSON cases
                - `utilities/helpers.ts` — env-based `APIRequestContext` (portable auth)

                To promote into the full eGain Playwright harness, copy generated specs/data into that
                repo and swap helpers for product `@utilities/helpers` / LoginApi flows.
                """.formatted(title), StandardCharsets.UTF_8);
    }

    private Map<String, List<OperationInfo>> collectOperationsByTag(Map<String, Object> spec) {
        Map<String, List<OperationInfo>> byTag = new LinkedHashMap<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return byTag;
        }
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null) {
                    continue;
                }
                OperationInfo info = new OperationInfo();
                info.path = path;
                info.method = method;
                info.operation = operation;
                info.operationId = resolveOperationId(operation, method, path);
                info.methodName = toCamelCase(info.operationId);
                byTag.computeIfAbsent(getOperationTag(operation), k -> new ArrayList<>()).add(info);
            }
        }
        return byTag;
    }

    private void writeApiClient(Path file, String className, List<OperationInfo> ops) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { APIRequestContext, APIResponse } from \"@playwright/test\";\n");
        sb.append("import { BaseApi, APIRequestOptions } from \"./base.api\";\n\n");
        sb.append("/** Generated API client for OpenAPI tag operations. */\n");
        sb.append("export class ").append(className).append(" extends BaseApi {\n");
        sb.append("  constructor(readonly request: APIRequestContext) {\n");
        sb.append("    super(request);\n");
        sb.append("  }\n\n");
        sb.append("  private headers(options?: APIRequestOptions): Record<string, string> {\n");
        sb.append("    return {\n");
        sb.append("      \"Content-Type\": \"application/json\",\n");
        sb.append("      Accept: \"application/json\",\n");
        sb.append("      ...(options?.headers || {}),\n");
        sb.append("    };\n");
        sb.append("  }\n\n");
        sb.append("  private resolvePath(template: string, pathParams?: Record<string, string>): string {\n");
        sb.append("    let url = template;\n");
        sb.append("    if (pathParams) {\n");
        sb.append("      for (const [key, value] of Object.entries(pathParams)) {\n");
        sb.append("        url = url.replace(`{${key}}`, encodeURIComponent(value));\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    return url;\n");
        sb.append("  }\n\n");

        for (OperationInfo op : ops) {
            String methodUpper = op.method.toUpperCase(Locale.ROOT);
            boolean hasBody = "post".equals(op.method) || "put".equals(op.method) || "patch".equals(op.method);
            sb.append("  async ").append(op.methodName).append("(\n");
            if (hasBody) {
                sb.append("    body?: Record<string, unknown> | null,\n");
            }
            sb.append("    options?: APIRequestOptions & { pathParams?: Record<string, string> },\n");
            sb.append("  ): Promise<APIResponse> {\n");
            sb.append("    const url = this.resolvePath(\"").append(escapeTsString(op.path))
                    .append("\", options?.pathParams);\n");
            sb.append("    return this.").append(op.method).append("(url, {\n");
            if (hasBody) {
                sb.append("      data: body === undefined ? undefined : body,\n");
            }
            sb.append("      headers: this.headers(options),\n");
            sb.append("      params: options?.params,\n");
            sb.append("      failOnStatusCode: false,\n");
            sb.append("    });\n");
            sb.append("  }\n\n");
            sb.append("  /** OpenAPI ").append(methodUpper).append(" ").append(escapeTsString(op.path))
                    .append(" */\n");
            sb.append("  static readonly ").append(op.methodName).append("_PATH = \"")
                    .append(escapeTsString(op.path)).append("\";\n\n");
        }
        sb.append("}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> buildPositiveCases(List<OperationInfo> ops, Map<String, Object> spec) {
        List<Map<String, Object>> cases = new ArrayList<>();
        int idx = 1;
        for (OperationInfo op : ops) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - " + op.operationId + " success");
            c.put("operation", op.methodName);
            c.put("operationId", op.operationId);
            c.put("method", op.method.toUpperCase(Locale.ROOT));
            c.put("path", op.path);
            Map<String, String> pathParams = new LinkedHashMap<>();
            Map<String, String> queryParams = new LinkedHashMap<>();
            collectParams(op.operation, pathParams, queryParams);
            c.put("pathParams", pathParams);
            c.put("queryParams", queryParams);
            Object payload = parseJsonOrNull(IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(op.operation, spec));
            c.put("payload", payload);
            c.put("expectedRC", preferred2xxStatus(op.operation));
            c.put("requiresAuth", requiresAuth(op.operation));
            cases.add(c);

            Map<String, Object> bodySchema = IntegrationScenarioSupport.extractRequestBodySchema(op.operation, spec);
            for (IntegrationScenarioSupport.OneOfVariantBody variant :
                    IntegrationScenarioSupport.buildOneOfVariantBodies(bodySchema, spec)) {
                Map<String, Object> v = new LinkedHashMap<>(c);
                v.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - "
                        + op.operationId + " variant " + variant.label());
                v.put("payload", parseJsonOrNull(variant.jsonBody()));
                cases.add(v);
            }
        }
        return cases;
    }

    private Map<String, Object> buildNegativeCases(List<OperationInfo> ops, Map<String, Object> spec) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        int idx = 1;
        int maxBody = IntegrationScenarioSupport.maxInvalidBodyFields(config);
        int maxParam = IntegrationScenarioSupport.maxInvalidParamCases(config);

        for (OperationInfo op : ops) {
            Map<String, String> pathParams = new LinkedHashMap<>();
            Map<String, String> queryParams = new LinkedHashMap<>();
            collectParams(op.operation, pathParams, queryParams);
            boolean jsonBody = "post".equals(op.method) || "put".equals(op.method) || "patch".equals(op.method);
            boolean auth = requiresAuth(op.operation);
            String bodyRaw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(op.operation, spec);
            Map<String, Object> bodySchema = IntegrationScenarioSupport.extractRequestBodySchema(op.operation, spec);

            if (IntegrationScenarioSupport.emitDeclaredErrorCodes(config)) {
                for (IntegrationScenarioSupport.DeclaredErrorCase dec :
                        IntegrationScenarioSupport.buildDeclaredErrorCases(op.operation, queryParams)) {
                    Map<String, Object> c = baseNegative(op, pathParams, dec.queryParamsOverride(),
                            parseJsonOrNull(bodyRaw), auth);
                    c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - " + dec.label());
                    c.put("expectedRC", dec.expectedStatus());
                    c.put("expectedStatuses", List.of(dec.expectedStatus()));
                    testCases.add(c);
                }
            }

            for (IntegrationScenarioSupport.IntegrationParamNegativeCase nc :
                    IntegrationScenarioSupport.buildParamNegativeCases(
                            op.path, op.operation, pathParams, queryParams, maxParam)) {
                Map<String, Object> c = baseNegative(op, nc.pathParams, nc.queryParams,
                        parseJsonOrNull(bodyRaw), auth);
                c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - param " + nc.name);
                List<Integer> statuses = nc.expectedStatusCodes != null
                        ? nc.expectedStatusCodes : List.of(400, 404, 422);
                c.put("expectedStatuses", statuses);
                c.put("expectedRC", statuses.get(0));
                testCases.add(c);
            }

            if (auth) {
                Map<String, Object> c = baseNegative(op, pathParams, queryParams, parseJsonOrNull(bodyRaw), false);
                c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - anonymous");
                c.put("anonymous", true);
                c.put("expectedStatuses", List.of(401, 403));
                c.put("expectedRC", 401);
                testCases.add(c);
            }

            if (jsonBody) {
                if (IntegrationScenarioSupport.isRequestBodyRequired(op.operation, spec)) {
                    Map<String, Object> c = baseNegative(op, pathParams, queryParams, null, auth);
                    c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - missing body");
                    c.put("omitBody", true);
                    c.put("expectedStatuses", List.of(400, 422));
                    c.put("expectedRC", 400);
                    testCases.add(c);
                }
                String wrong = IntegrationScenarioSupport.generateWrongTypesBodyRaw(bodySchema, spec);
                if (wrong != null && !wrong.isBlank() && !"{}".equals(wrong.trim())) {
                    Map<String, Object> c = baseNegative(op, pathParams, queryParams, parseJsonOrNull(wrong), auth);
                    c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - wrong types");
                    c.put("expectedStatuses", List.of(400, 422));
                    c.put("expectedRC", 400);
                    testCases.add(c);
                }
                String missing = IntegrationScenarioSupport.generateMissingRequiredFieldsBodyRaw(bodySchema, spec);
                if (missing != null && !missing.isBlank()) {
                    Map<String, Object> c = baseNegative(op, pathParams, queryParams, parseJsonOrNull(missing), auth);
                    c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++) + " - missing required");
                    c.put("expectedStatuses", List.of(400, 422));
                    c.put("expectedRC", 400);
                    testCases.add(c);
                }
                for (IntegrationScenarioSupport.PerFieldInvalidBody field :
                        IntegrationScenarioSupport.buildPerFieldInvalidBodies(bodySchema, spec, maxBody)) {
                    Map<String, Object> c = baseNegative(op, pathParams, queryParams,
                            parseJsonOrNull(field.invalidJsonBody), auth);
                    c.put("tcName", "API-" + String.format(Locale.ROOT, "%02d", idx++)
                            + " - invalid field " + field.fieldName);
                    c.put("expectedStatuses", List.of(400, 422));
                    c.put("expectedRC", 400);
                    testCases.add(c);
                }
            }
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("testCases", testCases);
        return wrapper;
    }

    private Map<String, Object> baseNegative(OperationInfo op, Map<String, String> pathParams,
                                             Map<String, String> queryParams, Object payload, boolean auth) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("operation", op.methodName);
        c.put("operationId", op.operationId);
        c.put("method", op.method.toUpperCase(Locale.ROOT));
        c.put("path", op.path);
        c.put("pathParams", pathParams != null ? pathParams : Map.of());
        c.put("queryParams", queryParams != null ? queryParams : Map.of());
        c.put("payload", payload);
        c.put("requiresAuth", auth);
        return c;
    }

    private String generatePositiveSpec(String folderSlug, String tag, String tagSlug, String className,
                                        String apiFileBase, String dataFileName, List<OperationInfo> ops) {
        StringBuilder sb = new StringBuilder();
        sb.append("import { expect, APIRequestContext, test } from \"@playwright/test\";\n");
        sb.append("import { ").append(className).append(" } from \"@apis/").append(apiFileBase).append("\";\n");
        sb.append("import { getAPIContext } from \"@utilities/helpers\";\n");
        sb.append("import testData from \"@data/generated/").append(folderSlug).append("/")
                .append(dataFileName).append("\" assert { type: \"json\" };\n\n");
        sb.append("interface PositiveCase {\n");
        sb.append("  tcName: string;\n");
        sb.append("  operation: string;\n");
        sb.append("  pathParams?: Record<string, string>;\n");
        sb.append("  queryParams?: Record<string, string>;\n");
        sb.append("  payload?: Record<string, unknown> | null;\n");
        sb.append("  expectedRC: number;\n");
        sb.append("  requiresAuth?: boolean;\n");
        sb.append("}\n\n");
        sb.append("for (const tc of testData as PositiveCase[]) {\n");
        sb.append("  test.describe.serial(\n");
        sb.append("    `").append(escapeTsString(tag)).append(" - ${tc.tcName}`,\n");
        sb.append("    { tag: [\"@generated\", \"@").append(escapeTsString(tagSlug)).append("\"] },\n");
        sb.append("    () => {\n");
        sb.append("      let ctx: APIRequestContext;\n");
        sb.append("      let api: ").append(className).append(";\n\n");
        sb.append("      test.beforeAll(async () => {\n");
        sb.append("        ctx = await getAPIContext();\n");
        sb.append("        api = new ").append(className).append("(ctx);\n");
        sb.append("      });\n\n");
        sb.append("      test(`positive: ${tc.tcName}`, async () => {\n");
        sb.append("        if (tc.requiresAuth && !(process.env.TOKEN || process.env.API_BEARER_TOKEN || process.env.API_TOKEN)) {\n");
        sb.append("          test.skip(true, \"Set TOKEN for secured operations\");\n");
        sb.append("        }\n");
        sb.append("        const res = await invoke(api, tc);\n");
        sb.append("        expect.soft(res.status(), tc.tcName).toBe(tc.expectedRC);\n");
        sb.append("      });\n\n");
        sb.append("      test.afterAll(async () => {\n");
        sb.append("        if (ctx) await ctx.dispose();\n");
        sb.append("      });\n");
        sb.append("    },\n");
        sb.append("  );\n");
        sb.append("}\n\n");
        appendInvokeHelper(sb, className, ops);
        return sb.toString();
    }

    private String generateNegativeSpec(String folderSlug, String tag, String tagSlug, String className,
                                        String apiFileBase, String dataFileName, List<OperationInfo> ops) {
        StringBuilder sb = new StringBuilder();
        sb.append("import { expect, APIRequestContext, test } from \"@playwright/test\";\n");
        sb.append("import { ").append(className).append(" } from \"@apis/").append(apiFileBase).append("\";\n");
        sb.append("import { getAPIContext, getAnonymousAPIContext } from \"@utilities/helpers\";\n");
        sb.append("import testData from \"@data/generated/").append(folderSlug).append("/")
                .append(dataFileName).append("\" assert { type: \"json\" };\n\n");
        sb.append("interface NegativeTestCase {\n");
        sb.append("  tcName: string;\n");
        sb.append("  operation: string;\n");
        sb.append("  pathParams?: Record<string, string>;\n");
        sb.append("  queryParams?: Record<string, string>;\n");
        sb.append("  payload?: Record<string, unknown> | null;\n");
        sb.append("  omitBody?: boolean;\n");
        sb.append("  anonymous?: boolean;\n");
        sb.append("  expectedRC: number;\n");
        sb.append("  expectedStatuses?: number[];\n");
        sb.append("  expectedDevCode?: string;\n");
        sb.append("  expectedDevMessage?: string;\n");
        sb.append("  requiresAuth?: boolean;\n");
        sb.append("}\n\n");
        sb.append("test.describe.serial(\n");
        sb.append("  \"").append(escapeTsString(tag)).append(" - negative and expansion tests\",\n");
        sb.append("  { tag: [\"@generated\", \"@").append(escapeTsString(tagSlug)).append("\"] },\n");
        sb.append("  () => {\n");
        sb.append("    let ctx: APIRequestContext;\n");
        sb.append("    let anonCtx: APIRequestContext;\n");
        sb.append("    let api: ").append(className).append(";\n");
        sb.append("    let anonApi: ").append(className).append(";\n\n");
        sb.append("    test.beforeAll(async () => {\n");
        sb.append("      ctx = await getAPIContext();\n");
        sb.append("      anonCtx = await getAnonymousAPIContext();\n");
        sb.append("      api = new ").append(className).append("(ctx);\n");
        sb.append("      anonApi = new ").append(className).append("(anonCtx);\n");
        sb.append("    });\n\n");
        sb.append("    for (const tc of (testData as { testCases: NegativeTestCase[] }).testCases) {\n");
        sb.append("      test(`Validate negative scenario: ${tc.tcName}`, async () => {\n");
        sb.append("        const client = tc.anonymous ? anonApi : api;\n");
        sb.append("        const res = await invoke(client, tc);\n");
        sb.append("        const allowed = tc.expectedStatuses && tc.expectedStatuses.length\n");
        sb.append("          ? tc.expectedStatuses\n");
        sb.append("          : [tc.expectedRC];\n");
        sb.append("        expect.soft(allowed, tc.tcName).toContain(res.status());\n");
        sb.append("        if (tc.expectedDevCode || tc.expectedDevMessage) {\n");
        sb.append("          const body = await res.text();\n");
        sb.append("          try {\n");
        sb.append("            const json = body ? JSON.parse(body) : {};\n");
        sb.append("            if (tc.expectedDevCode) {\n");
        sb.append("              expect.soft(json.code as string, `${tc.tcName} devCode`).toBe(tc.expectedDevCode);\n");
        sb.append("            }\n");
        sb.append("            if (tc.expectedDevMessage) {\n");
        sb.append("              expect.soft(json.developerMessage as string, `${tc.tcName} devMessage`)\n");
        sb.append("                .toBe(tc.expectedDevMessage);\n");
        sb.append("            }\n");
        sb.append("          } catch {\n");
        sb.append("            if (tc.expectedDevMessage && body) {\n");
        sb.append("              expect.soft(body, `${tc.tcName} body`).toContain(tc.expectedDevMessage);\n");
        sb.append("            }\n");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      });\n");
        sb.append("    }\n\n");
        sb.append("    test.afterAll(async () => {\n");
        sb.append("      if (ctx) await ctx.dispose();\n");
        sb.append("      if (anonCtx) await anonCtx.dispose();\n");
        sb.append("    });\n");
        sb.append("  },\n");
        sb.append(");\n\n");
        appendInvokeHelper(sb, className, ops);
        return sb.toString();
    }

    private void appendInvokeHelper(StringBuilder sb, String className, List<OperationInfo> ops) {
        sb.append("async function invoke(\n");
        sb.append("  api: ").append(className).append(",\n");
        sb.append("  tc: {\n");
        sb.append("    operation: string;\n");
        sb.append("    pathParams?: Record<string, string>;\n");
        sb.append("    queryParams?: Record<string, string>;\n");
        sb.append("    payload?: Record<string, unknown> | null;\n");
        sb.append("    omitBody?: boolean;\n");
        sb.append("  },\n");
        sb.append(") {\n");
        sb.append("  const options = {\n");
        sb.append("    pathParams: tc.pathParams,\n");
        sb.append("    params: tc.queryParams as Record<string, string | number | boolean> | undefined,\n");
        sb.append("  };\n");
        sb.append("  switch (tc.operation) {\n");
        for (OperationInfo op : ops) {
            boolean hasBody = "post".equals(op.method) || "put".equals(op.method) || "patch".equals(op.method);
            sb.append("    case \"").append(op.methodName).append("\":\n");
            if (hasBody) {
                sb.append("      return api.").append(op.methodName)
                        .append("(tc.omitBody ? undefined : (tc.payload ?? null), options);\n");
            } else {
                sb.append("      return api.").append(op.methodName).append("(options);\n");
            }
        }
        sb.append("    default:\n");
        sb.append("      throw new Error(`Unknown operation: ${tc.operation}`);\n");
        sb.append("  }\n");
        sb.append("}\n");
    }

    private String generateReadme(String apiTitle, String tag, String folderSlug, int positives, int negatives) {
        return """
                # %s / %s

                - Folder: `%s`
                - Positive cases: %d
                - Negative cases: %d
                - Tags: `@generated`

                ```bash
                npx playwright test tests/generated/%s --project=generated --workers=1
                ```
                """.formatted(
                apiTitle != null ? apiTitle : "API",
                tag,
                folderSlug,
                positives,
                negatives,
                folderSlug);
    }

    private static int countNegativeCases(Map<String, Object> negatives) {
        Object list = negatives.get("testCases");
        if (list instanceof List<?> l) {
            return l.size();
        }
        return 0;
    }

    private static void collectParams(Map<String, Object> operation,
                                      Map<String, String> pathParams,
                                      Map<String, String> queryParams) {
        if (!operation.containsKey("parameters")) {
            return;
        }
        for (Map<String, Object> param : Util.asStringObjectMapList(operation.get("parameters"))) {
            String name = (String) param.get("name");
            String in = (String) param.get("in");
            if ("path".equals(in)) {
                pathParams.put(name, IntegrationScenarioSupport.getParameterExample(param));
            } else if ("query".equals(in)) {
                queryParams.put(name, IntegrationScenarioSupport.getParameterExample(param));
            }
        }
    }

    private static boolean requiresAuth(Map<String, Object> operation) {
        return operation.containsKey("security")
                && operation.get("security") instanceof List<?> list
                && !list.isEmpty();
    }

    private static int preferred2xxStatus(Map<String, Object> operation) {
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) {
            return 200;
        }
        for (String code : List.of("200", "201", "202", "204")) {
            if (responses.containsKey(code)) {
                return Integer.parseInt(code);
            }
        }
        for (String key : responses.keySet()) {
            if (key != null && key.matches("2\\d\\d")) {
                return Integer.parseInt(key);
            }
        }
        return 200;
    }

    private static Object parseJsonOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (IOException e) {
            return raw;
        }
    }

    private static String getOperationTag(Map<String, Object> operation) {
        if (operation.containsKey("tags")) {
            List<String> tags = Util.asStringList(operation.get("tags"));
            if (tags != null && !tags.isEmpty() && tags.get(0) != null && !tags.get(0).isBlank()) {
                return tags.get(0);
            }
        }
        return "default";
    }

    private static String resolveOperationId(Map<String, Object> operation, String method, String path) {
        Object id = operation.get("operationId");
        if (id != null && !String.valueOf(id).isBlank()) {
            return String.valueOf(id);
        }
        return method + "_" + path.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String toKebabCase(String input) {
        if (input == null || input.isBlank()) {
            return "default";
        }
        return input.trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String toPascalCase(String input) {
        String[] parts = toKebabCase(input).split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.length() == 0 ? "Default" : sb.toString();
    }

    private static String toCamelCase(String input) {
        String pascal = toPascalCase(input.replaceAll("[^a-zA-Z0-9]+", "-"));
        if (pascal.isEmpty()) {
            return "operation";
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private static String escapeTsString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static final class OperationInfo {
        String path;
        String method;
        Map<String, Object> operation;
        String operationId;
        String methodName;
    }
}
