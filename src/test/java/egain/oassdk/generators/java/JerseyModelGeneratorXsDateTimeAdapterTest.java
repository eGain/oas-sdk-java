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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated {@code XMLGregorianCalendar} fields must carry {@code @XmlJavaTypeAdapter(XsDateTimeAdapter.class)}
 * the same way XJC beans such as {@code DateAndUser#date} do.
 */
@DisplayName("JerseyModelGenerator XMLGregorianCalendar XsDateTimeAdapter")
class JerseyModelGeneratorXsDateTimeAdapterTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("required date-time field emits XmlJavaTypeAdapter(XsDateTimeAdapter)")
    void dateTimeField_emitsXsDateTimeAdapter() throws OASSDKException, IOException {
        String content = generateModel("""
            DateAndUser:
              type: object
              required:
                - date
              properties:
                date:
                  type: string
                  format: date-time
                userName:
                  type: string
            """, "DateAndUser.java");

        assertTrue(content.contains("import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;"),
                "XmlJavaTypeAdapter lives in annotation.adapters, not annotation.*");
        assertTrue(content.contains("import egain.ws.framework.XsDateTimeAdapter;"),
                "adapter class is the digital bindings XsDateTimeAdapter");
        assertTrue(content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)"),
                "XMLGregorianCalendar field must have XsDateTimeAdapter");
        assertTrue(content.contains("private XMLGregorianCalendar date;"),
                "date-time maps to XMLGregorianCalendar");
        assertTrue(adapterImmediatelyPrecedesField(content, "date"),
                "adapter annotation must sit on the date field");
        assertFalse(adapterImmediatelyPrecedesField(content, "userName"),
                "String fields must not get XsDateTimeAdapter");
    }

    @Test
    @DisplayName("format date also maps to XMLGregorianCalendar and gets the adapter")
    void dateFormatField_emitsXsDateTimeAdapter() throws OASSDKException, IOException {
        String content = generateModel("""
            Availability:
              type: object
              properties:
                availabilityDate:
                  type: string
                  format: date
            """, "Availability.java");

        assertTrue(content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)"));
        assertTrue(content.contains("private XMLGregorianCalendar availabilityDate;"));
    }

    @Test
    @DisplayName("model with no date fields omits adapter imports")
    void noDateFields_omitsAdapterImports() throws OASSDKException, IOException {
        String content = generateModel("""
            BasicUser:
              type: object
              properties:
                userName:
                  type: string
            """, "BasicUser.java");

        assertFalse(content.contains("XsDateTimeAdapter"));
        assertFalse(content.contains("XmlJavaTypeAdapter"));
        assertFalse(content.contains("private XMLGregorianCalendar"));
    }

    @Test
    @DisplayName("inline object date-time field also gets the adapter")
    void innerClassDateTime_emitsXsDateTimeAdapter() throws OASSDKException, IOException {
        String content = generateModel("""
            Envelope:
              type: object
              properties:
                stamp:
                  type: object
                  required:
                    - date
                  properties:
                    date:
                      type: string
                      format: date-time
            """, "Envelope.java");

        assertTrue(content.contains("import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;"));
        assertTrue(content.contains("import egain.ws.framework.XsDateTimeAdapter;"));
        assertTrue(content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)"));
        assertTrue(content.contains("private XMLGregorianCalendar date;"));
    }

    private String generateModel(String schemasYaml, String fileName) throws OASSDKException, IOException {
        String yaml = """
            openapi: 3.0.0
            info:
              title: Date adapter test
              version: 1.0.0
            paths:
              /probe:
                get:
                  operationId: probe
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/%s'
            components:
              schemas:
            %s
            """.formatted(fileName.replace(".java", ""), schemasYaml.indent(4).stripTrailing());

        Path specFile = tempOutputDir.resolve(fileName.replace(".java", "") + "-spec.yaml");
        Files.writeString(specFile, yaml, StandardCharsets.UTF_8);

        Path outputDir = tempOutputDir.resolve(fileName.replace(".java", "") + "-out");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", "com.test.api", outputDir.toString());
        }

        try (Stream<Path> walk = Files.walk(outputDir)) {
            Path javaFile = walk
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(fileName + " not found under " + outputDir));
            return Files.readString(javaFile, StandardCharsets.UTF_8);
        }
    }

    private static boolean adapterImmediatelyPrecedesField(String content, String fieldName) {
        return content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)\n    @NotNull\n    private XMLGregorianCalendar "
                + fieldName + ";")
                || content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)\n    private XMLGregorianCalendar "
                + fieldName + ";")
                || content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)\n    @NotNull\n    private String "
                + fieldName + ";")
                || content.contains("@XmlJavaTypeAdapter(XsDateTimeAdapter.class)\n    private String "
                + fieldName + ";");
    }
}
