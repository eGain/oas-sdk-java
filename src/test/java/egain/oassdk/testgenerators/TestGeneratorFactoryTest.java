package egain.oassdk.testgenerators;

import egain.oassdk.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test cases for TestGeneratorFactory
 */
public class TestGeneratorFactoryTest {
    
    private TestGeneratorFactory factory;
    
    @BeforeEach
    public void setUp() {
        factory = new TestGeneratorFactory();
    }
    
    @Test
    public void testFactoryInitialization() {
        assertNotNull(factory);
    }
    
    @Test
    public void testGetUnitTestGenerator() {
        TestGenerator generator = factory.getGenerator("unit");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.unit.UnitTestGenerator);
    }
    
    @Test
    public void testGetIntegrationTestGenerator() {
        TestGenerator generator = factory.getGenerator("integration");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.integration.IntegrationTestGenerator);
    }
    
    @Test
    public void testGetNFRTestGenerator() {
        TestGenerator generator = factory.getGenerator("nfr");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.nfr.NFRTestGenerator);
    }
    
    @Test
    public void testGetPerformanceTestGenerator() {
        TestGenerator generator = factory.getGenerator("performance");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.performance.PerformanceTestGenerator);
    }
    
    @Test
    public void testGetSecurityTestGenerator() {
        TestGenerator generator = factory.getGenerator("security");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.security.SecurityTestGenerator);
    }
    
    @Test
    public void testGetPostmanTestGenerator() {
        TestGenerator generator = factory.getGenerator("postman");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.postman.PostmanTestGenerator);
    }

    @Test
    public void testGetSchemathesisTestGenerator() {
        TestGenerator generator = factory.getGenerator("schemathesis");
        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.schemathesis.SchemathesisTestGenerator);
    }
    
    @Test
    public void testGetMockDataGenerator() {
        TestGenerator generator1 = factory.getGenerator("mock_data");
        assertNotNull(generator1);
        assertTrue(generator1 instanceof egain.oassdk.testgenerators.mock.MockDataGenerator);
        
        TestGenerator generator2 = factory.getGenerator("mockdata");
        assertNotNull(generator2);
        assertTrue(generator2 instanceof egain.oassdk.testgenerators.mock.MockDataGenerator);
    }
    
    @Test
    public void testGetUnsupportedGenerator() {
        assertThrows(IllegalArgumentException.class, () -> {
            factory.getGenerator("unsupported");
        });
    }
    
    @Test
    public void testGetGeneratorCaseInsensitive() {
        TestGenerator generator1 = factory.getGenerator("UNIT");
        TestGenerator generator2 = factory.getGenerator("unit");
        
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertEquals(generator1.getClass(), generator2.getClass());
    }
    
    @Test
    public void testGetGeneratorWithConfig() {
        TestConfig config = new TestConfig();
        config.setTestFramework("junit5");
        
        TestGenerator generator = factory.getGenerator("unit", config);
        assertNotNull(generator);
        
        if (generator instanceof ConfigurableTestGenerator) {
            ConfigurableTestGenerator configurable = (ConfigurableTestGenerator) generator;
            assertEquals(config, configurable.getConfig());
        }
    }
    
    @Test
    public void testGetSupportedTestTypes() {
        String[] types = factory.getSupportedTestTypes();
        
        assertNotNull(types);
        assertTrue(types.length > 0);
        assertTrue(java.util.Arrays.asList(types).contains("unit"));
        assertTrue(java.util.Arrays.asList(types).contains("integration"));
        assertTrue(java.util.Arrays.asList(types).contains("nfr"));
        assertTrue(java.util.Arrays.asList(types).contains("performance"));
        assertTrue(java.util.Arrays.asList(types).contains("security"));
        assertTrue(java.util.Arrays.asList(types).contains("postman"));
        assertTrue(java.util.Arrays.asList(types).contains("schemathesis"));
        assertTrue(java.util.Arrays.asList(types).contains("mock_data"));
    }
    
    @Test
    public void testIsSupported() {
        assertTrue(factory.isSupported("unit"));
        assertTrue(factory.isSupported("integration"));
        assertTrue(factory.isSupported("nfr"));
        assertTrue(factory.isSupported("performance"));
        assertTrue(factory.isSupported("security"));
        assertTrue(factory.isSupported("postman"));
        assertTrue(factory.isSupported("schemathesis"));
        assertTrue(factory.isSupported("mock_data"));
        assertTrue(factory.isSupported("mockdata"));
        
        assertFalse(factory.isSupported("unsupported"));
        assertFalse(factory.isSupported("invalid"));
    }
    
    @Test
    public void testIsSupportedCaseInsensitive() {
        assertTrue(factory.isSupported("UNIT"));
        assertTrue(factory.isSupported("Integration"));
        assertTrue(factory.isSupported("NFR"));
        assertTrue(factory.isSupported("SCHEMATHESIS"));
    }

    @Test
    public void testGetUnitTestGenerator_WithPythonConfig() {
        TestConfig config = TestConfig.builder()
                .language("python")
                .framework("pytest")
                .build();

        TestGenerator generator = factory.getGenerator("unit", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.python.PytestUnitTestGenerator);
    }

    @Test
    public void testGetIntegrationTestGenerator_WithPythonConfig() {
        TestConfig config = TestConfig.builder()
                .language("python")
                .framework("pytest")
                .build();

        TestGenerator generator = factory.getGenerator("integration", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.python.PytestIntegrationTestGenerator);
    }

    @Test
    public void testGetUnitTestGenerator_WithNodejsConfig() {
        TestConfig config = TestConfig.builder()
                .language("nodejs")
                .framework("jest")
                .build();

        TestGenerator generator = factory.getGenerator("unit", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.nodejs.JestUnitTestGenerator);
    }

    @Test
    public void testGetUnitTestGenerator_WithJavaScriptConfig() {
        TestConfig config = TestConfig.builder()
                .language("javascript")
                .framework("jest")
                .build();

        TestGenerator generator = factory.getGenerator("unit", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.nodejs.JestUnitTestGenerator);
    }

    @Test
    public void testGetIntegrationTestGenerator_WithNodejsConfig() {
        TestConfig config = TestConfig.builder()
                .language("nodejs")
                .framework("jest")
                .build();

        TestGenerator generator = factory.getGenerator("integration", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.nodejs.JestIntegrationTestGenerator);
    }

    @Test
    public void testGetIntegrationTestGenerator_WithJavaScriptConfig() {
        TestConfig config = TestConfig.builder()
                .language("javascript")
                .framework("jest")
                .build();

        TestGenerator generator = factory.getGenerator("integration", config);

        assertNotNull(generator);
        assertTrue(generator instanceof egain.oassdk.testgenerators.nodejs.JestIntegrationTestGenerator);
    }
}

