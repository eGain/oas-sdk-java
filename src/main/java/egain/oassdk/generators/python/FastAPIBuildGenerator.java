package egain.oassdk.generators.python;

import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.generators.common.OpenApiPathUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Generates build artifacts for FastAPI applications: requirements.txt, .env.example, README.
 */
public final class FastAPIBuildGenerator {

    private final PythonGenerationContext ctx;

    public FastAPIBuildGenerator(PythonGenerationContext ctx) {
        this.ctx = ctx;
    }

    public void generateBuildFiles(String outputDir) throws IOException {
        PythonNamingUtils.writeFile(outputDir + "/requirements.txt", generateRequirementsTxt());
        PythonNamingUtils.writeFile(outputDir + "/.env.example", generateEnvExample());
        PythonNamingUtils.writeFile(outputDir + "/README.md", generateReadme(ctx.getSpec()));
    }

    String generateRequirementsTxt() {
        StringBuilder reqs = new StringBuilder();
        reqs.append("fastapi==0.104.1\n");
        reqs.append("uvicorn[standard]==0.24.0\n");
        reqs.append("pydantic==2.5.0\n");
        reqs.append("pydantic-settings==2.1.0\n");
        reqs.append("python-multipart==0.0.6\n");

        ObservabilityConfig obsConfig = ctx.getConfig() != null ? ctx.getConfig().getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            if (obsConfig.isEnableTracing()) {
                reqs.append("opentelemetry-api==1.22.0\n");
                reqs.append("opentelemetry-sdk==1.22.0\n");
                reqs.append("opentelemetry-exporter-otlp==1.22.0\n");
                reqs.append("opentelemetry-instrumentation-fastapi==0.43b0\n");
            }
            if (obsConfig.isEnableMetrics()) {
                reqs.append("prometheus-fastapi-instrumentator==6.1.0\n");
            }
        }

        return reqs.toString();
    }

    String generateEnvExample() {
        return """
                # Application Settings
                DEBUG=False
                APP_NAME=API Service
                """;
    }

    String generateReadme(Map<String, Object> spec) {
        return "# " + OpenApiPathUtils.getApiTitle(spec) + "\n\n"
                + OpenApiPathUtils.getApiDescription(spec) + "\n\n"
                + "## Installation\n\n"
                + "```bash\n"
                + "pip install -r requirements.txt\n"
                + "```\n\n"
                + "## Running the Application\n\n"
                + "```bash\n"
                + "uvicorn main:app --reload\n"
                + "```\n\n"
                + "## API Documentation\n\n"
                + "Once the application is running, visit:\n"
                + "- Swagger UI: http://localhost:8000/docs\n"
                + "- ReDoc: http://localhost:8000/redoc\n";
    }
}
