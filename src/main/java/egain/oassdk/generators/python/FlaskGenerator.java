package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;
import egain.oassdk.generators.common.OpenApiPathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Flask code generator — thin orchestrator delegating to specialised sub-generators.
 */
public class FlaskGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(FlaskGenerator.class);

    private GeneratorConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName)
            throws GenerationException {
        if (spec == null) {
            throw new GenerationException("Specification cannot be null");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            PythonGenerationContext ctx = new PythonGenerationContext(spec, config);

            createDirectoryStructure(outputDir, packageName);
            generateMainApplication(spec, outputDir, packageName);

            new PythonSchemaCollector(ctx).collectInlinedSchemas();
            new FlaskBlueprintGenerator(ctx).generateBlueprints(outputDir, packageName);
            new PythonModelGenerator().generate(ctx, outputDir, packageName);

            generateServices(outputDir, packageName);
            generateConfiguration(spec, outputDir, packageName);
            generateExceptionHandlers(outputDir, packageName);
            new FlaskBuildGenerator(ctx).generateBuildFiles(outputDir, packageName);

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Flask application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Flask application: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "Flask Generator";
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
        return "flask";
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
                outputDir + "/" + packagePath + "/blueprints",
                outputDir + "/" + packagePath + "/models",
                outputDir + "/" + packagePath + "/services",
                outputDir + "/" + packagePath + "/config",
                outputDir + "/" + packagePath + "/exceptions",
                outputDir + "/tests"
        }) {
            Files.createDirectories(Paths.get(dir));
        }

        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath);
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/blueprints");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/models");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/services");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/config");
        PythonNamingUtils.createInitFile(outputDir + "/" + packagePath + "/exceptions");
    }

    private void generateMainApplication(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String pkg = packageName != null ? packageName : "api";
        StringBuilder content = new StringBuilder();
        content.append("from flask import Flask\n");
        content.append("from flask_cors import CORS\n");
        content.append("from ").append(pkg).append(".config.settings import settings\n");
        content.append("from ").append(pkg).append(".exceptions.handlers import register_error_handlers\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Set<String> parentPaths = new HashSet<>();
            for (String path : paths.keySet()) {
                String parentPath = OpenApiPathUtils.extractParentPath(path);
                if (parentPaths.add(parentPath)) {
                    String blueprintName = PythonNamingUtils.generateBlueprintName(parentPath);
                    content.append("from ").append(pkg).append(".blueprints.").append(blueprintName.toLowerCase())
                            .append(" import ").append(blueprintName.toLowerCase()).append("_bp\n");
                }
            }
        }

        content.append("\n\ndef create_app():\n");
        content.append("    \"\"\"Application factory pattern\"\"\"\n");
        content.append("    app = Flask(__name__)\n\n");
        content.append("    app.config.from_object(settings)\n\n");
        content.append("    CORS(app)\n\n");

        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            String svcName = obsConfig.getServiceName() != null ? obsConfig.getServiceName() : OpenApiPathUtils.getApiTitle(spec);
            if (obsConfig.isEnableTracing()) {
                content.append("    # Observability: OpenTelemetry tracing\n");
                content.append("    from opentelemetry import trace\n");
                content.append("    from opentelemetry.sdk.trace import TracerProvider\n");
                content.append("    from opentelemetry.sdk.trace.export import BatchSpanProcessor\n");
                content.append("    from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter\n");
                content.append("    from opentelemetry.instrumentation.flask import FlaskInstrumentor\n");
                content.append("    from opentelemetry.sdk.resources import Resource\n\n");
                content.append("    resource = Resource.create({\"service.name\": \"").append(svcName).append("\"})\n");
                content.append("    provider = TracerProvider(resource=resource)\n");
                content.append("    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))\n");
                content.append("    trace.set_tracer_provider(provider)\n");
                content.append("    FlaskInstrumentor().instrument_app(app)\n\n");
            }
            if (obsConfig.isEnableMetrics()) {
                content.append("    # Observability: Prometheus metrics\n");
                content.append("    from prometheus_flask_exporter import PrometheusMetrics\n");
                content.append("    metrics = PrometheusMetrics(app)\n\n");
            }
        }

        content.append("    register_error_handlers(app)\n\n");
        content.append("    # Register blueprints\n");
        if (paths != null) {
            Set<String> parentPaths = new HashSet<>();
            for (String path : paths.keySet()) {
                String parentPath = OpenApiPathUtils.extractParentPath(path);
                if (parentPaths.add(parentPath)) {
                    String blueprintName = PythonNamingUtils.generateBlueprintName(parentPath);
                    content.append("    app.register_blueprint(").append(blueprintName.toLowerCase())
                            .append("_bp, url_prefix='").append(parentPath).append("')\n");
                }
            }
        }

        content.append("\n    @app.route('/')\n");
        content.append("    def index():\n");
        content.append("        return {'message': 'API is running', 'title': '")
                .append(OpenApiPathUtils.getApiTitle(spec)).append("'}\n\n");
        content.append("    @app.route('/health')\n");
        content.append("    def health():\n");
        content.append("        return {'status': 'healthy'}\n\n");
        content.append("    return app\n\n");
        content.append("if __name__ == '__main__':\n");
        content.append("    app = create_app()\n");
        content.append("    app.run(host='0.0.0.0', port=5000, debug=settings.DEBUG)\n");

        PythonNamingUtils.writeFile(outputDir + "/app.py", content.toString());
    }

    private void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String serviceContent = "# Business logic implementation placeholder\n"
                + "# This service should contain the core business logic for the API\n\n"
                + "class ApiService:\n"
                + "    \"\"\"Core business logic service\"\"\"\n\n"
                + "    def __init__(self):\n"
                + "        pass\n\n"
                + "    # Add your business logic methods here\n\n"
                + "api_service = ApiService()\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/services/api_service.py", serviceContent);
    }

    private void generateConfiguration(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String configContent = "import os\n\n"
                + "class Settings:\n"
                + "    \"\"\"Application configuration\"\"\"\n"
                + "    DEBUG = os.getenv('DEBUG', 'False').lower() in ('true', '1', 't')\n"
                + "    TESTING = os.getenv('TESTING', 'False').lower() in ('true', '1', 't')\n"
                + "    SECRET_KEY = os.getenv('SECRET_KEY', 'dev-secret-key-change-in-production')\n"
                + "    APP_NAME = os.getenv('APP_NAME', '" + OpenApiPathUtils.getApiTitle(spec) + "')\n"
                + "    API_VERSION = os.getenv('API_VERSION', '" + OpenApiPathUtils.getApiVersion(spec) + "')\n\n"
                + "    DATABASE_URL = os.getenv('DATABASE_URL', 'sqlite:///app.db')\n\n"
                + "    CORS_ORIGINS = os.getenv('CORS_ORIGINS', '*').split(',')\n\n"
                + "settings = Settings()\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/config/settings.py", configContent);
    }

    private void generateExceptionHandlers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";
        String exceptionContent = "from flask import jsonify\n"
                + "from werkzeug.exceptions import HTTPException\n\n"
                + "def register_error_handlers(app):\n"
                + "    \"\"\"Register error handlers for the Flask app\"\"\"\n\n"
                + "    @app.errorhandler(404)\n"
                + "    def not_found_error(error):\n"
                + "        return jsonify({'error': 'Resource not found'}), 404\n\n"
                + "    @app.errorhandler(500)\n"
                + "    def internal_error(error):\n"
                + "        return jsonify({'error': 'Internal server error'}), 500\n\n"
                + "    @app.errorhandler(HTTPException)\n"
                + "    def handle_http_exception(error):\n"
                + "        return jsonify({\n"
                + "            'error': error.description,\n"
                + "            'code': error.code\n"
                + "        }), error.code\n\n"
                + "    @app.errorhandler(Exception)\n"
                + "    def handle_exception(error):\n"
                + "        app.logger.error(f'Unhandled exception: {error}')\n"
                + "        return jsonify({\n"
                + "            'error': 'An unexpected error occurred',\n"
                + "            'message': str(error)\n"
                + "        }), 500\n";
        PythonNamingUtils.writeFile(outputDir + "/" + packagePath + "/exceptions/handlers.py", exceptionContent);
    }
}
