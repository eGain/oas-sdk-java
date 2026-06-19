package egain.oassdk.generators.go;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test cases for GinGenerator
 */
public class GinGeneratorTest {
    
    private GinGenerator generator;
    private Map<String, Object> openApiSpec;
    
    @BeforeEach
    public void setUp() {
        generator = new GinGenerator();
        
        // Create minimal OpenAPI spec
        openApiSpec = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Test API");
        info.put("version", "1.0.0");
        openApiSpec.put("info", info);
        openApiSpec.put("paths", new HashMap<>());
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
        assertEquals("Gin Generator", generator.getName());
    }
    
    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }
    
    @Test
    public void testGetLanguage() {
        assertEquals("go", generator.getLanguage());
    }
    
    @Test
    public void testGetFramework() {
        assertEquals("gin", generator.getFramework());
    }
    
    @Test
    public void testSetAndGetConfig() {
        GeneratorConfig config = new GeneratorConfig();
        generator.setConfig(config);
        
        assertEquals(config, generator.getConfig());
    }
    
    @Test
    public void testIsNotImplemented() {
        assertFalse(generator.isImplemented());
    }

    @Test
    public void testGenerateThrowsNotImplementedException() {
        GeneratorConfig config = new GeneratorConfig();
        
        assertThrows(GenerationException.class, () -> {
            generator.generate(openApiSpec, "./output", config, "com.test");
        });
    }
}

