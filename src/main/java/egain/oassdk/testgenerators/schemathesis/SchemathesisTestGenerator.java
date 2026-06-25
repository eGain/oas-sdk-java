package egain.oassdk.testgenerators.schemathesis;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.io.OpenApiMapYamlWriter;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Generates a Schemathesis bundle: resolved OpenAPI YAML, {@code schemathesis.properties}
 * (paths and CI placeholders such as {@code %HUB%}, {@code %BASEURL%}, {@code %TOKEN%}),
 * {@code schemathesis.toml} (Schemathesis CLI coverage and check defaults),
 * and {@code run-schemathesis.sh} wrapping {@code st run}.
 * <p>Optional {@link TestConfig#getAdditionalProperties()} keys:
 * <ul>
 *   <li>{@code schemathesis.bundleDir} (String, default {@code schemathesis}) — subdirectory under
 *       {@code outputDir}; use {@code .} to emit directly into {@code outputDir}</li>
 *   <li>{@code schemathesis.baseUrl} / {@code schemathesis.url} — value for {@code BASEURL} in properties
 *       (default: first {@code servers[].url} or {@code %BASEURL%})</li>
 *   <li>{@code schemathesis.specFileName} (String, default {@code openapi.yaml})</li>
 *   <li>{@code schemathesis.junitReport}, {@code schemathesis.vcrPath}, {@code schemathesis.consoleLog},
 *       {@code schemathesis.coverageDir}, {@code schemathesis.rateLimit}, {@code schemathesis.checks},
 *       {@code schemathesis.mode}, {@code schemathesis.phases}, {@code schemathesis.extraArgs}</li>
 *   <li>{@code schemathesis.tlsVerify} — {@code true} verifies HTTPS certificates (use for CI/production);
 *       {@code false} or omitted emits {@code TLS_VERIFY=false} and passes {@code --tls-verify=false}
 *       to {@code st run} (local/dev only; default when this key is omitted)</li>
 * </ul>
 */
public class SchemathesisTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private static final String DEFAULT_SCHEMATHESIS_TOML = """
            [phases.coverage]
            unexpected-methods = ["GET", "PUT", "POST", "DELETE", "OPTIONS", "PATCH"]

            [phases.stateful]
            max-steps = 10

            [checks.missing_required_header]
            expected-statuses = [400]
            """;

    private TestConfig config;

    /**
     * Directory that holds {@code openapi.yaml}, properties, {@code schemathesis.toml}, and scripts for this generator.
     */
    public static Path resolveBundleDirectory(String outputDir, TestConfig config) {
        Objects.requireNonNull(outputDir, "outputDir");
        if (config != null && config.getAdditionalProperties() != null) {
            Object o = config.getAdditionalProperties().get("schemathesis.bundleDir");
            if (o != null) {
                String s = o.toString().trim();
                if (s.isEmpty() || ".".equals(s)) {
                    return Paths.get(outputDir);
                }
                return Paths.get(outputDir, s);
            }
        }
        return Paths.get(outputDir, "schemathesis");
    }

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Path bundleDir = resolveBundleDirectory(outputDir, config);
            Files.createDirectories(bundleDir);

            Files.writeString(bundleDir.resolve("schemathesis.toml"), DEFAULT_SCHEMATHESIS_TOML, StandardCharsets.UTF_8);

            String specFileName = propString(config, "schemathesis.specFileName", "openapi.yaml");
            new OpenApiMapYamlWriter().write(spec, bundleDir.resolve(specFileName));

            String baseUrl = resolveBaseUrl(spec, config);
            Map<String, String> props = buildPropertyValues(baseUrl, specFileName, config);
            writeProperties(bundleDir.resolve("schemathesis.properties"), props);
            writeProperties(bundleDir.resolve("schemathesis.local.properties.example"), Map.of(
                    "TOKEN", "'Bearer <jwt-from-test-env-or-login>'",
                    "BASEURL", baseUrl.isBlank() ? "" : baseUrl
            ));

            String script = buildRunScript(specFileName);
            Path scriptPath = bundleDir.resolve("run-schemathesis.sh");
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
            scriptPath.toFile().setExecutable(true);

            Files.writeString(bundleDir.resolve("README-schemathesis.md"), buildReadme(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new GenerationException("Failed to generate Schemathesis bundle: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new GenerationException("Failed to generate Schemathesis bundle: " + e.getMessage(), e);
        }
    }

    private static String resolveBaseUrl(Map<String, Object> spec, TestConfig config) {
        String fromConfig = propString(config, "schemathesis.baseUrl", null);
        if (fromConfig == null || fromConfig.isBlank()) {
            fromConfig = propString(config, "schemathesis.url", null);
        }
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.trim();
        }
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers != null && !servers.isEmpty() && servers.get(0) != null) {
            Object url = servers.get(0).get("url");
            if (url instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return "%BASEURL%";
    }

    private Map<String, String> buildPropertyValues(String baseUrl, String specFileName, TestConfig config) {
        Map<String, String> m = new HashMap<>();
        m.put("BASEURL", baseUrl);
        m.put("SPEC_FILE", specFileName);
        m.put("JUNIT_REPORT", propString(config, "schemathesis.junitReport",
                "results-%HUB%-%DOT%-%BUILD_NUMBER%.xml"));
        m.put("VCR_CASSETTE", propString(config, "schemathesis.vcrPath",
                "cassette-%HUB%-%DOT%-%BUILD_NUMBER%.yaml"));
        m.put("CONSOLE_LOG", propString(config, "schemathesis.consoleLog",
                "runTest-%HUB%-%DOT%-%BUILD_NUMBER%.txt"));
        m.put("RATE_LIMIT", propString(config, "schemathesis.rateLimit", "10000/m"));
        m.put("CHECKS", propString(config, "schemathesis.checks", "all"));
        m.put("MODE", propString(config, "schemathesis.mode", "all"));
        m.put("PHASES", propString(config, "schemathesis.phases", "coverage,stateful"));
        m.put("HEADER_ACCEPT", propString(config, "schemathesis.headerAccept", "Accept: application/json"));
        m.put("HEADER_ACCEPT_LANG", propString(config, "schemathesis.headerAcceptLanguage", "Accept-language: en-US"));
        m.put("HEADER_AUTH", propString(config, "schemathesis.headerAuthorization", "Authorization: %TOKEN%"));
        m.put("EXTRA_ARGS", buildDefaultExtraArgs(config));
        m.put("TLS_VERIFY", tlsVerifyPropertyString(config));
        return m;
    }

    /**
     * {@code true}/{@code false} for {@code TLS_VERIFY} in properties; default {@code false} so generated
     * bundles work against typical dev/self-signed hosts (set {@code schemathesis.tlsVerify=true} for CI).
     */
    private static String tlsVerifyPropertyString(TestConfig config) {
        String v = propString(config, "schemathesis.tlsVerify", "false").trim().toLowerCase(Locale.ROOT);
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v)) {
            return "true";
        }
        return "false";
    }

    private static String buildDefaultExtraArgs(TestConfig config) {
        String custom = propString(config, "schemathesis.extraArgs", null);
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        String ops = propString(config, "schemathesis.include.operations",
                "createFolder,getSubFolders,editFolder,getFolder");
        StringBuilder sb = new StringBuilder();
        for (String part : ops.split(",")) {
            String op = part.trim();
            if (!op.isEmpty()) {
                sb.append(" --include-operation-id ").append(op);
            }
        }
        return sb.toString().trim();
    }

    private static void writeProperties(Path path, Map<String, String> values) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Schemathesis run configuration.\n");
        sb.append("# Jenkins/CI may replace tokens like %HUB%, %DOT%, %BUILD_NUMBER%, %BASEURL%, %TOKEN%.\n");
        sb.append("# For local runs, copy to schemathesis.local.properties (key=value) and export overrides.\n");
        sb.append("# TLS_VERIFY=false passes --tls-verify=false (dev/self-signed only); use true in production/CI.\n\n");
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String propString(TestConfig config, String key, String defaultValue) {
        if (config == null || config.getAdditionalProperties() == null) {
            return defaultValue;
        }
        Object v = config.getAdditionalProperties().get(key);
        if (v == null) {
            return defaultValue;
        }
        String s = v.toString();
        return s.isEmpty() ? defaultValue : s;
    }

    private static String buildRunScript(String specFile) {
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                cd "$SCRIPT_DIR"

                load_test_env() {
                  local env_root="${TEST_ENV_FILE:-$SCRIPT_DIR/../test-env.properties}"
                  if [[ -f "$env_root" ]]; then
                    while IFS='=' read -r key value; do
                      [[ -z "$key" || "$key" =~ ^# ]] && continue
                      key="$(echo "$key" | tr -d '[:space:]')"
                      value="$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
                      case "$key" in
                        base.url) BASEURL="${BASEURL:-$value}" ;;
                        auth.token) TOKEN="${TOKEN:-'Bearer '"$value"}" ;;
                        schemathesis.include.operations) SCHEMATHESIS_OPS="$value" ;;
                        test.destructive.enabled) DESTRUCTIVE="$value" ;;
                      esac
                    done < "$env_root"
                  fi
                  if [[ -f "$SCRIPT_DIR/../test-env.local.properties" ]]; then
                    # shellcheck disable=SC1091
                    source "$SCRIPT_DIR/../test-env.local.properties"
                  fi
                }
                load_test_env
                
                get_prop() {
                  local key="$1"
                  local def="$2"
                  local line
                  line="$(grep -m1 "^[[:space:]]*${key}=" schemathesis.properties 2>/dev/null || true)"
                  if [[ -z "$line" ]]; then
                    printf '%s' "$def"
                    return
                  fi
                  printf '%s' "${line#*=}"
                }
                
                if [[ -f schemathesis.local.properties ]]; then
                  set -a
                  # shellcheck disable=SC1091
                  source schemathesis.local.properties
                  set +a
                fi
                
                BASEURL="$(get_prop BASEURL '%BASEURL%')"
                SPEC_FILE="$(get_prop SPEC_FILE '__SCHEMA_FILE__')"
                JUNIT_REPORT="$(get_prop JUNIT_REPORT 'results-%HUB%-%DOT%-%BUILD_NUMBER%.xml')"
                VCR_CASSETTE="$(get_prop VCR_CASSETTE 'cassette-%HUB%-%DOT%-%BUILD_NUMBER%.yaml')"
                CONSOLE_LOG="$(get_prop CONSOLE_LOG 'runTest-%HUB%-%DOT%-%BUILD_NUMBER%.txt')"
                RATE_LIMIT="$(get_prop RATE_LIMIT '10000/m')"
                CHECKS="$(get_prop CHECKS 'all')"
                MODE="$(get_prop MODE 'all')"
                PHASES="$(get_prop PHASES 'coverage,stateful')"
                HEADER_ACCEPT="$(get_prop HEADER_ACCEPT 'Accept: application/json')"
                HEADER_ACCEPT_LANG="$(get_prop HEADER_ACCEPT_LANG 'Accept-language: en-US')"
                HEADER_AUTH="$(get_prop HEADER_AUTH 'Authorization: %TOKEN%')"
                EXTRA_ARGS="$(get_prop EXTRA_ARGS '')"
                if [[ -n "${SCHEMATHESIS_OPS:-}" ]]; then
                  IFS=',' read -ra OPS <<< "${SCHEMATHESIS_OPS}"
                  for op in "${OPS[@]}"; do
                    op="$(echo "$op" | tr -d '[:space:]')"
                    if [[ -n "$op" ]]; then
                      EXTRA_ARGS="${EXTRA_ARGS} --include-operation-id ${op}"
                    fi
                  done
                fi
                if [[ "${DESTRUCTIVE:-false}" != "true" && "${DESTRUCTIVE:-false}" != "1" ]]; then
                  EXTRA_ARGS="${EXTRA_ARGS} --exclude-operation-id deleteFolder"
                fi
                if [[ -n "${TOKEN:-}" ]]; then
                  HEADER_AUTH="Authorization: ${TOKEN}"
                fi
                TLS_VERIFY="$(get_prop TLS_VERIFY 'true')"
                tls_lc=$(printf '%s' "$TLS_VERIFY" | tr '[:upper:]' '[:lower:]')
                if [[ -z "$tls_lc" ]]; then tls_lc=true; fi
                TLS_EXTRA=()
                if [[ "$tls_lc" != "true" && "$tls_lc" != "1" && "$tls_lc" != "yes" ]]; then
                  TLS_EXTRA=(--tls-verify=false)
                fi
                
                if ! command -v st >/dev/null 2>&1; then
                  echo "Schemathesis CLI (st) not found. Install: pip install schemathesis" >&2
                  exit 1
                fi
                
                # shellcheck disable=SC2086
                st run \\
                  --checks "$CHECKS" \\
                  --mode "$MODE" \\
                  --phases="$PHASES" \\
                  --continue-on-failure \\
                  --rate-limit="$RATE_LIMIT" \\
                  --report-junit-path="$JUNIT_REPORT" \\
                  --header "$HEADER_ACCEPT" \\
                  --header "$HEADER_ACCEPT_LANG" \\
                  --header "$HEADER_AUTH" \\
                  --report-vcr-path="$VCR_CASSETTE" \\
                  --url="$BASEURL" \\
                  "${TLS_EXTRA[@]}" \\
                  "$SPEC_FILE" \\
                  $EXTRA_ARGS \\
                  2>&1 | tee "$CONSOLE_LOG"
                """.replace("__SCHEMA_FILE__", specFile);
    }

    private static String buildReadme() {
        return """
                # Schemathesis bundle
                
                ## Prerequisites
                
                - Python 3.10+ recommended
                - `pip install schemathesis` (installs the `st` CLI)
                
                ## Files
                
                - `openapi.yaml` — OpenAPI document used by Schemathesis (from the SDK parse/filter pipeline).
                - `schemathesis.properties` — paths and options; CI placeholders such as `%BASEURL%`, `%TOKEN%`, `%HUB%`, `%DOT%`, `%BUILD_NUMBER%` are left literal for your build server to replace.
                - `schemathesis.toml` — Schemathesis project config: coverage-phase unexpected HTTP methods, stateful phase (max-steps 10) for random producer-consumer workflows, and the `missing_required_header` check.
                - `run-schemathesis.sh` — runs `st run` with JUnit and VCR outputs; honors `TLS_VERIFY` (default in bundle: verify off for dev via `--tls-verify=false`; set `TLS_VERIFY=true` for production).
                - Optional `schemathesis.local.properties` — shell-sourced before the run; use for local secrets (do not commit).
                
                ## Run
                
                ```bash
                chmod +x run-schemathesis.sh
                ./run-schemathesis.sh
                ```
                
                Override defaults by editing `schemathesis.properties` or providing `schemathesis.local.properties`.
                Edit `schemathesis.toml` for Schemathesis-native settings (phases, checks, and similar).
                Set `TLS_VERIFY=true` when targeting environments with proper TLS trust stores.
                """;
    }

    @Override
    public String getName() {
        return "Schemathesis Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "schemathesis";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }
}
