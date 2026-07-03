package egain.oassdk.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Comprehensive test cases for GeneratorConfig
 */
public class GeneratorConfigTest {
    
    @Test
    public void testDefaultConstructor() {
        GeneratorConfig config = new GeneratorConfig();
        
        assertEquals("java", config.getLanguage());
        assertEquals("jersey", config.getFramework());
        assertEquals("com.example.api", config.getPackageName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("./generated", config.getOutputDir());
        assertNull(config.getTemplatesDir());
        assertFalse(config.isCustomTemplates());
        assertNotNull(config.getAdditionalProperties());
        assertNull(config.getIncludePaths());
        assertNull(config.getIncludeOperations());
        assertFalse(config.isModelsOnly());
        assertNull(config.getSpecZipPath());
    }
    
    @Test
    public void testModelsOnlyGetterAndSetter() {
        GeneratorConfig config = new GeneratorConfig();
        assertFalse(config.isModelsOnly());
        config.setModelsOnly(true);
        assertTrue(config.isModelsOnly());
        config.setModelsOnly(false);
        assertFalse(config.isModelsOnly());
    }

    @Test
    public void testUseBoxedPrimitivesDefaultAndBuilder() {
        GeneratorConfig config = new GeneratorConfig();
        assertFalse(config.isUseBoxedPrimitives());
        config.setUseBoxedPrimitives(true);
        assertTrue(config.isUseBoxedPrimitives());

        GeneratorConfig built = GeneratorConfig.builder().useBoxedPrimitives(true).build();
        assertTrue(built.isUseBoxedPrimitives());
    }
    
    @Test
    public void testSpecZipPathGetterAndSetter() {
        GeneratorConfig config = new GeneratorConfig();
        assertNull(config.getSpecZipPath());
        config.setSpecZipPath("/path/to/specs.zip");
        assertEquals("/path/to/specs.zip", config.getSpecZipPath());
        config.setSpecZipPath(null);
        assertNull(config.getSpecZipPath());
    }
    
    @Test
    public void testConstructorWithParameters() {
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("key", "value");
        
        GeneratorConfig config = new GeneratorConfig(
            "python", "fastapi", "com.test", "2.0.0",
            "./output", "./templates", true, additionalProps
        );
        
        assertEquals("python", config.getLanguage());
        assertEquals("fastapi", config.getFramework());
        assertEquals("com.test", config.getPackageName());
        assertEquals("2.0.0", config.getVersion());
        assertEquals("./output", config.getOutputDir());
        assertEquals("./templates", config.getTemplatesDir());
        assertTrue(config.isCustomTemplates());
        assertEquals("value", config.getAdditionalProperties().get("key"));
    }
    
    @Test
    public void testGettersAndSetters() {
        GeneratorConfig config = new GeneratorConfig();
        
        config.setLanguage("go");
        assertEquals("go", config.getLanguage());
        
        config.setFramework("gin");
        assertEquals("gin", config.getFramework());
        
        config.setPackageName("test.package");
        assertEquals("test.package", config.getPackageName());
        
        config.setVersion("3.0.0");
        assertEquals("3.0.0", config.getVersion());
        
        config.setOutputDir("./test-output");
        assertEquals("./test-output", config.getOutputDir());
        
        config.setTemplatesDir("./test-templates");
        assertEquals("./test-templates", config.getTemplatesDir());
        
        config.setCustomTemplates(true);
        assertTrue(config.isCustomTemplates());
        
        Map<String, Object> props = new HashMap<>();
        props.put("test", "value");
        config.setAdditionalProperties(props);
        assertEquals(props, config.getAdditionalProperties());
    }
    
    @Test
    public void testIncludePaths() {
        GeneratorConfig config = new GeneratorConfig();
        
        List<String> paths = Arrays.asList("/api/users", "/api/posts");
        config.setIncludePaths(paths);
        
        assertEquals(paths, config.getIncludePaths());
    }
    
    @Test
    public void testIncludeOperations() {
        GeneratorConfig config = new GeneratorConfig();
        
        Map<String, List<String>> operations = new HashMap<>();
        operations.put("/api/users", Arrays.asList("GET", "POST"));
        operations.put("/api/posts", Arrays.asList("GET"));
        config.setIncludeOperations(operations);
        
        assertEquals(operations, config.getIncludeOperations());
    }
    
    @Test
    public void testBuilder() {
        GeneratorConfig config = GeneratorConfig.builder()
            .language("python")
            .framework("flask")
            .packageName("com.example")
            .version("1.0.0")
            .outputDir("./output")
            .templatesDir("./templates")
            .customTemplates(true)
            .build();
        
        assertEquals("python", config.getLanguage());
        assertEquals("flask", config.getFramework());
        assertEquals("com.example", config.getPackageName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("./output", config.getOutputDir());
        assertEquals("./templates", config.getTemplatesDir());
        assertTrue(config.isCustomTemplates());
    }
    
    @Test
    public void testBuilderWithAdditionalProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("key1", "value1");
        props.put("key2", 123);
        
        GeneratorConfig config = GeneratorConfig.builder()
            .additionalProperties(props)
            .build();
        
        assertEquals("value1", config.getAdditionalProperties().get("key1"));
        assertEquals(123, config.getAdditionalProperties().get("key2"));
    }
    
    @Test
    public void testBuilderWithIncludePaths() {
        List<String> paths = Arrays.asList("/api/v1", "/api/v2");
        
        GeneratorConfig config = GeneratorConfig.builder()
            .includePaths(paths)
            .build();
        
        assertEquals(paths, config.getIncludePaths());
    }
    
    @Test
    public void testBuilderWithIncludeOperations() {
        Map<String, List<String>> operations = new HashMap<>();
        operations.put("/api/users", Arrays.asList("GET", "POST", "PUT"));
        
        GeneratorConfig config = GeneratorConfig.builder()
            .includeOperations(operations)
            .build();
        
        assertEquals(operations, config.getIncludeOperations());
    }
    
    @Test
    public void testBuilderWithModelsOnly() {
        GeneratorConfig config = GeneratorConfig.builder()
            .modelsOnly(true)
            .build();
        assertTrue(config.isModelsOnly());
        config = GeneratorConfig.builder().modelsOnly(false).build();
        assertFalse(config.isModelsOnly());
    }
    
    @Test
    public void testBuilderWithSpecZipPath() {
        GeneratorConfig config = GeneratorConfig.builder()
            .specZipPath("test/lib/platform-api-interfaces.zip")
            .build();
        assertEquals("test/lib/platform-api-interfaces.zip", config.getSpecZipPath());
    }
    
    @Test
    public void testBuilderChaining() {
        GeneratorConfig config = GeneratorConfig.builder()
            .language("java")
            .framework("jersey")
            .packageName("com.test")
            .version("1.0.0")
            .outputDir("./out")
            .templatesDir("./tmpl")
            .customTemplates(false)
            .build();
        
        assertNotNull(config);
        assertEquals("java", config.getLanguage());
    }
    
    @Test
    public void testToString() {
        GeneratorConfig config = new GeneratorConfig();
        String str = config.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("GeneratorConfig"));
        assertTrue(str.contains("language"));
    }
    
    @Test
    public void testNullAdditionalProperties() {
        GeneratorConfig config = new GeneratorConfig(
            "java", "jersey", "com.test", "1.0.0",
            "./out", null, false, null
        );
        
        assertNotNull(config.getAdditionalProperties());
        assertTrue(config.getAdditionalProperties().isEmpty());
    }
}

