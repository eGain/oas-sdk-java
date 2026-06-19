package egain.oassdk.generators.python;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;
import egain.oassdk.testgenerators.common.OpenApiTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test cases for FlaskGenerator
 */
public class FlaskGeneratorTest {
    
    private FlaskGenerator generator;
    private Map<String, Object> openApiSpec;
    
    @BeforeEach
    public void setUp() {
        generator = new FlaskGenerator();
        
        openApiSpec = OpenApiTestFixtures.minimalSpec();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testImplementsCodeGenerator() {
        assertTrue(generator instanceof CodeGenerator);
    }
    
    @Test
    public void testImplementsConfigurableGenerator() {
        assertTrue(generator instanceof ConfigurableGenerator);
    }
    
    @Test
    public void testGetName() {
        assertEquals("Flask Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetLanguage() {
        assertEquals("python", generator.getLanguage());
    }
    
    @Test
    public void testGetFramework() {
        assertEquals("flask", generator.getFramework());
    }
    
    @Test
    public void testSetAndGetConfig() {
        GeneratorConfig config = new GeneratorConfig();
        generator.setConfig(config);
        
        assertEquals(config, generator.getConfig());
    }
    
    @Test
    public void testGenerateWithNullSpec() {
        GeneratorConfig config = new GeneratorConfig();
        
        assertThrows(GenerationException.class, () -> {
            generator.generate(null, "./output", config, "com.test");
        });
    }
}

