package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;
import egain.oassdk.generators.common.OpenApiPathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * FastAPI code generator — thin orchestrator delegating to specialised sub-generators.
 */
public class FastAPIGenerator implements CodeGenerator, ConfigurableGenerator {

    private GeneratorConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName)
            throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            PythonGenerationContext ctx = new PythonGenerationContext(spec, config);
            FastAPIRouteGenerator routeGenerator = new FastAPIRouteGenerator(ctx);
            ctx.setSecuritySchemes(routeGenerator.collectSecuritySchemes(spec));

            createDirectoryStructure(outputDir, packageName);
            generateMainApplication(spec, outputDir, packageName);

            new PythonSchemaCollector(ctx).collectInlinedSchemas();
            routeGenerator.generateRouters(outputDir, packageName);
            new PythonModelGenerator().generate(ctx, outputDir, packageName);

            generateServices(outputDir, packageName);
            generateConfiguration(spec, outputDir, packageName);
            generateExceptionHandlers(outputDir, packageName);

            if (routeGenerator.anyOperationHasSecurity(spec)) {
                routeGenerator.generateSecurity(outputDir, packageName, spec);
            }

            new FastAPIBuildGenerator(ctx).generateBuildFiles(outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate FastAPI application: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "FastAPI Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public String getFramework() {
        return "fastapi";
    }

    @Override
    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    @Override
    public GeneratorConfig getConfig() {
        return this.config;
    }

    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        for (String dir : new String[]{
                outputDir + "/" + packagePath,
                outputDir + "/" + packagePath + "/routers",
                outputDir + "/" + packagePath + "/models",
                outputDir + "/" + packagePath + "/services",
                outputDir + "/" + packagePath + "/config",
                outputDir + "/" + packagePath + "/exceptions",
                outputDir + "/tests"
        }) {
            Files.createDirectories(Paths.get(dir));
        }

        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath);
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/routers");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/models");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/services");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/config");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/exceptions");
    }

    private void generateMainApplication(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        StringBuilder content = new StringBuilder();
        String pkg = packageName != null ? packageName : "api";
        content.append("from fastapi import FastAPI\n");
        content.append("from fastapi.middleware.cors import CORSMiddleware\n");
        content.append("from ").append(pkg).append(".exceptions.handlers import setup_exception_handlers\n");

        String serverBasePath = OpenApiPathUtils.extractServerBasePath(spec);
        Map<String, Object> pathsForImport = Util.asStringObjectMap(spec.get("paths"));
        Set<String> importedRouters = new LinkedHashSet<>();
        if (pathsForImport != null) {
            for (String path : pathsForImport.keySet()) {
                String routerModule = PythonNamingUtils.generateRouterName(
                        OpenApiPathUtils.buildFullPath(serverBasePath, OpenApiPathUtils.extractParentPath(path))
                ).toLowerCase();
                if (importedRouters.add(routerModule)) {
                    content.append("from ").append(pkg).append(".routers.").append(routerModule)
                            .append(" import ").append(routerModule).append("_router\n");
                }
            }
        }
        content.append("\n");

        content.append("app = FastAPI(\n");
        content.append("    title=").append(PythonNamingUtils.pyStr(OpenApiPathUtils.getApiTitle(spec))).append(",\n");
        content.append("    description=").append(PythonNamingUtils.pyStr(OpenApiPathUtils.getApiDescription(spec))).append(",\n");
        content.append("    version=").append(PythonNamingUtils.pyStr(OpenApiPathUtils.getApiVersion(spec))).append("\n");
        content.append(")\n\n");

        content.append("# CORS middleware\n");
        content.append("app.add_middleware(\n");
        content.append("    CORSMiddleware,\n");
        content.append("    allow_origins=[\"*\"],\n");
        content.append("    allow_credentials=True,\n");
        content.append("    allow_methods=[\"*\"],\n");
        content.append("    allow_headers=[\"*\"],\n");
        content.append(")\n\n");

        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            String svcName = obsConfig.getServiceName() != null ? obsConfig.getServiceName() : OpenApiPathUtils.getApiTitle(spec);
            if (obsConfig.isEnableTracing()) {
                content.append("# Observability: OpenTelemetry tracing\n");
                content.append("from opentelemetry import trace\n");
                content.append("from opentelemetry.sdk.trace import TracerProvider\n");
                content.append("from opentelemetry.sdk.trace.export import BatchSpanProcessor\n");
                content.append("from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter\n");
                content.append("from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor\n");
                content.append("from opentelemetry.sdk.resources import Resource\n\n");
                content.append("resource = Resource.create({\"service.name\": \"").append(svcName).append("\"})\n");
                content.append("provider = TracerProvider(resource=resource)\n");
                content.append("provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))\n");
                content.append("trace.set_tracer_provider(provider)\n");
                content.append("FastAPIInstrumentor.instrument_app(app)\n\n");
            }
            if (obsConfig.isEnableMetrics()) {
                content.append("# Observability: Prometheus metrics\n");
                content.append("from prometheus_fastapi_instrumentator import Instrumentator\n");
                content.append("Instrumentator().instrument(app).expose(app, endpoint=\"/metrics\")\n\n");
            }
        }

        content.append("# Setup exception handlers\n");
        content.append("setup_exception_handlers(app)\n\n");

        content.append("# Include routers\n");
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Set<String> includedRouters = new LinkedHashSet<>();
            for (String path : paths.keySet()) {
                String routerModule = PythonNamingUtils.generateRouterName(
                        OpenApiPathUtils.buildFullPath(serverBasePath, OpenApiPathUtils.extractParentPath(path))
                ).toLowerCase();
                if (includedRouters.add(routerModule)) {
                    content.append("app.include_router(").append(routerModule).append("_router)\n");
                }
            }
        }

        content.append("\n@app.get(\"/\")\n");
        content.append("async def root():\n");
        content.append("    return {\"message\": \"API is running\"}\n");

        PythonNamingUtils.writeFile(outputDir + "/main.py", content.toString());
    }

    private void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String serviceContent = "# Business logic implementation placeholder\n"
                + "# This service should contain the core business logic for the API\n"
                + "# Implement methods that correspond to the operations defined in the OpenAPI specification\n\n"
                + "class ApiService:\n"
                + "    pass\n\n"
                + "api_service = ApiService()\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/services/api_service.py", serviceContent);
    }

    private void generateConfiguration(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String configContent = "from pydantic_settings import BaseSettings\n\n"
                + "class Settings(BaseSettings):\n"
                + "    app_name: str = " + PythonNamingUtils.pyStr(OpenApiPathUtils.getApiTitle(spec)) + "\n"
                + "    debug: bool = False\n\n"
                + "    class Config:\n"
                + "        env_file = \".env\"\n\n"
                + "settings = Settings()\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/config/settings.py", configContent);
    }

    private void generateExceptionHandlers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String exceptionContent = "from fastapi import FastAPI, Request, status\n"
                + "from fastapi.responses import JSONResponse\n"
                + "from fastapi.exceptions import RequestValidationError\n"
                + "import traceback\n\n"
                + "def setup_exception_handlers(app: FastAPI):\n"
                + "    @app.exception_handler(Exception)\n"
                + "    async def global_exception_handler(request: Request, exc: Exception):\n"
                + "        return JSONResponse(\n"
                + "            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,\n"
                + "            content={\"detail\": f\"An error occurred: {str(exc)}\"}\n"
                + "        )\n\n"
                + "    @app.exception_handler(RequestValidationError)\n"
                + "    async def validation_exception_handler(request: Request, exc: RequestValidationError):\n"
                + "        return JSONResponse(\n"
                + "            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,\n"
                + "            content={\"detail\": exc.errors()}\n"
                + "        )\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/exceptions/handlers.py", exceptionContent);
    }
}
