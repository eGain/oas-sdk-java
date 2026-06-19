package egain.oassdk.generators.python;

import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.generators.common.OpenApiPathUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Generates build artifacts for Flask applications: requirements.txt, .env.example, README, wsgi.py.
 */
public final class FlaskBuildGenerator {

    private final PythonGenerationContext ctx;

    public FlaskBuildGenerator(PythonGenerationContext ctx) {
        this.ctx = ctx;
    }

    public void generateBuildFiles(String outputDir, String packageName) throws IOException {
        PythonNamingUtils.writeFile(outputDir + "/requirements.txt", generateRequirementsTxt());
        PythonNamingUtils.writeFile(outputDir + "/.env.example", generateEnvExample());
        PythonNamingUtils.writeFile(outputDir + "/README.md", generateReadme(ctx.getSpec(), packageName));
        PythonNamingUtils.writeFile(outputDir + "/wsgi.py", generateWsgi());
    }

    String generateRequirementsTxt() {
        StringBuilder reqs = new StringBuilder();
        reqs.append("Flask==3.0.0\n");
        reqs.append("Flask-CORS==4.0.0\n");
        reqs.append("python-dotenv==1.0.0\n");
        reqs.append("gunicorn==21.2.0\n");
        reqs.append("marshmallow==3.20.1\n");

        ObservabilityConfig obsConfig = ctx.getConfig() != null ? ctx.getConfig().getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            if (obsConfig.isEnableTracing()) {
                reqs.append("opentelemetry-api==1.22.0\n");
                reqs.append("opentelemetry-sdk==1.22.0\n");
                reqs.append("opentelemetry-exporter-otlp==1.22.0\n");
                reqs.append("opentelemetry-instrumentation-flask==0.43b0\n");
            }
            if (obsConfig.isEnableMetrics()) {
                reqs.append("prometheus-flask-exporter==0.23.0\n");
            }
        }

        return reqs.toString();
    }

    String generateEnvExample() {
        return "# Application Settings\n"
                + "DEBUG=False\n"
                + "SECRET_KEY=your-secret-key-here\n"
                + "APP_NAME=API Service\n"
                + "API_VERSION=1.0.0\n\n"
                + "# Database Settings\n"
                + "DATABASE_URL=sqlite:///app.db\n\n"
                + "# CORS Settings\n"
                + "CORS_ORIGINS=*\n";
    }

    String generateReadme(Map<String, Object> spec, String packageName) {
        return "# " + OpenApiPathUtils.getApiTitle(spec) + "\n\n"
                + OpenApiPathUtils.getApiDescription(spec) + "\n\n"
                + "## Installation\n\n"
                + "```bash\n"
                + "pip install -r requirements.txt\n"
                + "```\n\n"
                + "## Configuration\n\n"
                + "Copy `.env.example` to `.env` and update with your settings:\n\n"
                + "```bash\n"
                + "cp .env.example .env\n"
                + "```\n\n"
                + "## Running the Application\n\n"
                + "### Development\n\n"
                + "```bash\n"
                + "python app.py\n"
                + "```\n\n"
                + "Or using Flask CLI:\n\n"
                + "```bash\n"
                + "export FLASK_APP=app.py\n"
                + "export FLASK_ENV=development\n"
                + "flask run\n"
                + "```\n\n"
                + "### Production\n\n"
                + "```bash\n"
                + "gunicorn -w 4 -b 0.0.0.0:5000 wsgi:app\n"
                + "```\n\n"
                + "## API Documentation\n\n"
                + "The API will be available at http://localhost:5000\n\n"
                + "## Testing\n\n"
                + "```bash\n"
                + "pytest tests/\n"
                + "```\n";
    }

    String generateWsgi() {
        return "from app import create_app\n\n"
                + "app = create_app()\n\n"
                + "if __name__ == '__main__':\n"
                + "    app.run()\n";
    }
}
