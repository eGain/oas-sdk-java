package egain.oassdk.test.nfr;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for egain.oassdk.test.nfr.NFRTestGenerator.
 */
class NFRTestGeneratorTest {

    @Test
    void generateNFRTests_withMinimalSpec_createsOutputDirectoryAndKeyFiles(@TempDir Path tempDir) throws GenerationException {
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> slaSpec = Map.of(
                "name", "Test SLA",
                "version", "1.0"
        );

        generator.generateNFRTests(slaSpec, tempDir.toString());

        assertThat(tempDir).exists();
        assertThat(tempDir.resolve("PerformanceTests.java")).exists();
        assertThat(tempDir.resolve("ScalabilityTests.java")).exists();
        assertThat(tempDir.resolve("ReliabilityTests.java")).exists();
        assertThat(tempDir.resolve("AvailabilityTests.java")).exists();
        assertThat(tempDir.resolve("SecurityTests.java")).exists();
        assertThat(tempDir.resolve("LoadTests.java")).exists();
        assertThat(tempDir.resolve("StressTests.java")).exists();
        assertThat(tempDir.resolve("NFRTestConfig.java")).exists();
    }

    @Test
    void generateNFRTests_createsPerformanceTestsWithExpectedContent(@TempDir Path tempDir) throws Exception {
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> slaSpec = Map.of("name", "SLA");

        generator.generateNFRTests(slaSpec, tempDir.toString());

        String content = Files.readString(tempDir.resolve("PerformanceTests.java"));
        assertThat(content).contains("PerformanceTests").contains("responseTime");
    }

    @Test
    void generateNFRTests_whenOutputDirIsFile_throwsGenerationException(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "x");
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> slaSpec = Map.of("name", "SLA");

        GenerationException ex = assertThrows(GenerationException.class,
                () -> generator.generateNFRTests(slaSpec, filePath.resolve("sub").toString()));

        assertThat(ex.getMessage()).contains("Failed to generate NFR tests");
    }
}
