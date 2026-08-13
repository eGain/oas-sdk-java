package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two {@code Answer.yaml} files (common id+value vs GH text/image) must both survive generation.
 */
@DisplayName("Answer.yaml basename collision")
class JerseyModelGeneratorAnswerCollisionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Jakarta modelsOnly: common Answer is id+value; GH class is schemas-prefixed")
    void jakartaModelsOnlyKeepsBothAnswerSchemas() throws OASSDKException, IOException {
        Path bundle = tempDir.resolve("bundle");
        Path commonDir = bundle.resolve("common");
        Path schemasDir = bundle.resolve("schemas");
        Files.createDirectories(commonDir);
        Files.createDirectories(schemasDir);

        Files.writeString(commonDir.resolve("Answer.yaml"), """
                type: object
                title: Answer
                properties:
                  id:
                    type: string
                  value:
                    type: string
                """);
        Files.writeString(schemasDir.resolve("Answer.yaml"), """
                type: object
                title: Answer
                properties:
                  id:
                    type: string
                  text:
                    type: string
                  image:
                    type: object
                """);
        Path specFile = bundle.resolve("api.yaml");
        Files.writeString(specFile, """
                openapi: 3.0.0
                info:
                  title: Answer collision
                  version: 1.0.0
                paths: {}
                components:
                  schemas:
                    Wrapper:
                      type: object
                      properties:
                        common:
                          $ref: common/Answer.yaml
                        gh:
                          $ref: schemas/Answer.yaml
                """);

        Path outputDir = tempDir.resolve("out");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .useJakartaNamespace(true)
                .packageName("com.egain.bindings.ws.model.xsds.common.v4")
                .outputDir(outputDir.toString())
                .searchPaths(List.of(bundle.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", config.getPackageName(), outputDir.toString());
        }

        Path answerJava;
        Path schemasAnswerJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            List<Path> javaFiles = walk.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
            answerJava = javaFiles.stream()
                    .filter(p -> p.getFileName().toString().equals("Answer.java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Answer.java not found under " + outputDir + ": " + javaFiles));
            schemasAnswerJava = javaFiles.stream()
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("SchemasAnswer.java") || name.equals("schemas-Answer.java");
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("SchemasAnswer.java not found under " + outputDir + ": " + javaFiles));
        }

        String answer = Files.readString(answerJava, StandardCharsets.UTF_8);
        assertTrue(answer.contains("jakarta.xml.bind") || answer.contains("jakarta.validation"),
                "Jakarta generation should use jakarta imports");
        assertFalse(answer.contains("javax.xml.bind.annotation"),
                "Jakarta generation must not use javax JAXB imports");
        assertTrue(answer.contains("value"), "common Answer must have value");
        assertFalse(answer.contains("conceptName"), "common Answer must not have GH conceptName");
        assertFalse(answer.contains("private String text;"), "common Answer must not have GH text");

        String gh = Files.readString(schemasAnswerJava, StandardCharsets.UTF_8);
        assertTrue(gh.contains("class SchemasAnswer") || gh.contains("class Answer"),
                "Prefixed GH class should be generated");
        assertTrue(gh.contains("private String text;") || gh.contains("text"),
                "GH Answer must have text");
        assertTrue(gh.contains("image"), "GH Answer must have image");
        assertFalse(gh.contains("private String value;"), "GH Answer must not have common value");
    }
}
