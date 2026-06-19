package egain.oassdk.generators;

import egain.oassdk.config.GeneratorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test cases for GeneratorFactory
 */
public class GeneratorFactoryTest {
    
    private GeneratorFactory factory;
    
    @BeforeEach
    public void setUp() {
        factory = new GeneratorFactory();
    }
    
    @Test
    public void testFactoryInitialization() {
        assertNotNull(factory);
    }
    
    @Test
    public void testGetJavaJerseyGenerator() {
        CodeGenerator generator = factory.getGenerator("java", "jersey");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.java.JerseyGenerator);
    }
    
    @Test
    public void testGetJavaJaxRSGenerator() {
        CodeGenerator generator = factory.getGenerator("java", "jax-rs");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.java.JerseyGenerator);
    }
    
    @Test
    public void testGetJavaJaxrsGenerator() {
        CodeGenerator generator = factory.getGenerator("java", "jaxrs");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.java.JerseyGenerator);
    }
    
    @Test
    public void testGetPythonFastAPIGenerator() {
        CodeGenerator generator = factory.getGenerator("python", "fastapi");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.python.FastAPIGenerator);
    }
    
    @Test
    public void testGetPythonFlaskGenerator() {
        CodeGenerator generator = factory.getGenerator("python", "flask");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.python.FlaskGenerator);
    }
    
    @Test
    public void testGetNodeJSExpressGenerator() {
        CodeGenerator generator = factory.getGenerator("nodejs", "express");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.nodejs.ExpressGenerator);
    }
    
    @Test
    public void testGetJavaScriptExpressGenerator() {
        CodeGenerator generator = factory.getGenerator("javascript", "express");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.nodejs.ExpressGenerator);
    }
    
    @Test
    public void testGetGoGinGenerator() {
        CodeGenerator generator = factory.getGenerator("go", "gin");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.go.GinGenerator);
    }
    
    @Test
    public void testGetCSharpASPNETGenerator() {
        CodeGenerator generator = factory.getGenerator("csharp", "aspnet");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.csharp.ASPNETGenerator);
    }
    
    @Test
    public void testGetCSharpASPDotNetGenerator() {
        CodeGenerator generator = factory.getGenerator("csharp", "asp.net");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.generators.csharp.ASPNETGenerator);
    }
    
    @Test
    public void testGetUnsupportedGenerator() {
        assertThrows(IllegalArgumentException.class, () -> {
            factory.getGenerator("unsupported", "framework");
        });
    }
    
    @Test
    public void testGetGeneratorCaseInsensitive() {
        CodeGenerator generator1 = factory.getGenerator("JAVA", "JERSEY");
        CodeGenerator generator2 = factory.getGenerator("java", "jersey");
        
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertEquals(generator1.getClass(), generator2.getClass());
    }
    
    @Test
    public void testGetGeneratorWithConfig() {
        GeneratorConfig config = new GeneratorConfig();
        config.setLanguage("java");
        config.setFramework("jersey");
        
        CodeGenerator generator = factory.getGenerator("java", "jersey", config);
        assertNotNull(generator);
    }
    
    @Test
    public void testGetSupportedCombinations() {
        String[] combinations = factory.getSupportedCombinations();
        
        assertNotNull(combinations);
        assertTrue(combinations.length > 0);
        assertTrue(java.util.Arrays.asList(combinations).contains("java-jersey"));
        assertTrue(java.util.Arrays.asList(combinations).contains("python-fastapi"));
        assertTrue(java.util.Arrays.asList(combinations).contains("nodejs-express"));
    }
    
    @Test
    public void testIsSupported() {
        assertTrue(factory.isSupported("java", "jersey"));
        assertTrue(factory.isSupported("python", "fastapi"));
        assertTrue(factory.isSupported("nodejs", "express"));
        assertTrue(factory.isSupported("go", "gin"));
        assertTrue(factory.isSupported("csharp", "aspnet"));
        
        assertFalse(factory.isSupported("unsupported", "framework"));
        assertFalse(factory.isSupported("java", "unsupported"));
    }
    
    @Test
    public void testImplementedAndStubCombinations() {
        String[] implemented = factory.getImplementedCombinations();
        String[] stubs = factory.getStubCombinations();

        assertEquals(4, implemented.length);
        assertEquals(2, stubs.length);
        assertTrue(java.util.Arrays.asList(implemented).contains("java-jersey"));
        assertFalse(java.util.Arrays.asList(implemented).contains("go-gin"));
        assertTrue(java.util.Arrays.asList(stubs).contains("go-gin"));
        assertTrue(java.util.Arrays.asList(stubs).contains("csharp-aspnet"));
    }

    @Test
    public void testEnsureImplementedRejectsStub() {
        CodeGenerator gin = factory.getGenerator("go", "gin");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.ensureImplemented(gin, "go", "gin"));
        assertTrue(ex.getMessage().contains("not yet implemented"));
    }

    @Test
    public void testGetSupportedCombinationsExcludingStubs() {
        String[] withoutStubs = factory.getSupportedCombinations(false);
        assertEquals(4, withoutStubs.length);
    }
}

