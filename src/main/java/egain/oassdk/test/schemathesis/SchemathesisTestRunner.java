package egain.oassdk.test.schemathesis;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.schemathesis.SchemathesisTestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Prepares Schemathesis bundles (OpenAPI + properties + {@code schemathesis.toml} + {@code st run} script)
 * and optional Docker/CI templates.
 * Core artifacts are produced by {@link SchemathesisTestGenerator}.
 */
public class SchemathesisTestRunner {

    /**
     * Generate Schemathesis bundle and auxiliary Docker/CI files under {@code outputDir}.
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory for test results
     * @param baseUrl   Base URL for the API under test
     * @throws GenerationException if generation fails
     */
    public void executeTests(Map<String, Object> spec, String outputDir, String baseUrl) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            Map<String, Object> additional = new HashMap<>();
            additional.put("schemathesis.bundleDir", ".");
            if (baseUrl != null) {
                additional.put("schemathesis.baseUrl", baseUrl);
            }
            TestConfig config = TestConfig.builder()
                    .additionalProperties(additional)
                    .build();

            SchemathesisTestGenerator generator = new SchemathesisTestGenerator();
            generator.setConfig(config);
            generator.generate(spec, outputDir, config, null);

            Path bundleDir = SchemathesisTestGenerator.resolveBundleDirectory(outputDir, config);
            String effectiveBase = baseUrl != null ? baseUrl : "%BASEURL%";
            generateDockerConfig(bundleDir.toString(), effectiveBase);
            generateCICDIntegration(bundleDir.toString(), effectiveBase);

        } catch (Exception e) {
            throw new GenerationException("Failed to execute Schemathesis tests: " + e.getMessage(), e);
        }
    }

    private void generateDockerConfig(String outputDir, String baseUrl) throws IOException {
        String dockerfile = """
                FROM python:3.9-slim
                
                RUN apt-get update && apt-get install -y \\
                    curl \\
                    && rm -rf /var/lib/apt/lists/*
                
                RUN pip install schemathesis
                
                WORKDIR /app
                
                COPY openapi.yaml .
                COPY schemathesis.properties .
                COPY schemathesis.toml .
                COPY run-schemathesis.sh .
                RUN chmod +x run-schemathesis.sh
                
                ENV BASE_URL=%s
                
                CMD ["./run-schemathesis.sh"]
                """.formatted(baseUrl);

        Files.writeString(Paths.get(outputDir, "Dockerfile"), dockerfile, StandardCharsets.UTF_8);

        String dockerCompose = """
                version: '3.8'
                
                services:
                  schemathesis:
                    build: .
                    volumes:
                      - ./openapi.yaml:/app/openapi.yaml:ro
                      - ./schemathesis.properties:/app/schemathesis.properties:ro
                      - ./schemathesis.toml:/app/schemathesis.toml:ro
                      - ./results:/app/results
                    environment:
                      - BASE_URL=%s
                    command: ./run-schemathesis.sh
                """.formatted(baseUrl);

        Files.writeString(Paths.get(outputDir, "docker-compose.yml"), dockerCompose, StandardCharsets.UTF_8);
    }

    private void generateCICDIntegration(String outputDir, String baseUrl) throws IOException {
        String githubActions = """
                name: Schemathesis API Testing
                
                on:
                  push:
                    branches: [ main, develop ]
                  pull_request:
                    branches: [ main ]
                  schedule:
                    - cron: '0 2 * * *'
                
                jobs:
                  schemathesis-tests:
                    runs-on: ubuntu-latest
                
                    steps:
                    - uses: actions/checkout@v3
                
                    - name: Set up Python
                      uses: actions/setup-python@v4
                      with:
                        python-version: '3.9'
                
                    - name: Install Schemathesis
                      run: |
                        pip install schemathesis
                
                    - name: Run Schemathesis tests
                      run: |
                        chmod +x run-schemathesis.sh
                        ./run-schemathesis.sh
                      env:
                        BASE_URL: %s
                
                    - name: Upload test results
                      uses: actions/upload-artifact@v3
                      if: always()
                      with:
                        name: schemathesis-results
                        path: '*.xml'
                """.formatted(baseUrl);

        Files.writeString(Paths.get(outputDir, "github-actions.yml"), githubActions, StandardCharsets.UTF_8);

        String jenkinsPipeline = """
                pipeline {
                    agent any
                
                    stages {
                        stage('Checkout') {
                            steps {
                                checkout scm
                            }
                        }
                
                        stage('Setup') {
                            steps {
                                sh 'pip install schemathesis'
                            }
                        }
                
                        stage('Run Schemathesis Tests') {
                            steps {
                                sh 'chmod +x run-schemathesis.sh && ./run-schemathesis.sh'
                            }
                        }
                    }
                
                    post {
                        always {
                            archiveArtifacts artifacts: '*.xml, *.txt', fingerprint: true, allowEmptyArchive: true
                        }
                    }
                }
                """;

        Files.writeString(Paths.get(outputDir, "Jenkinsfile"), jenkinsPipeline, StandardCharsets.UTF_8);
    }
}
