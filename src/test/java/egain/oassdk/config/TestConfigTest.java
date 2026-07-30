package egain.oassdk.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Comprehensive test cases for TestConfig
 */
public class TestConfigTest {
    
    @Test
    public void testDefaultConstructor() {
        TestConfig config = new TestConfig();
        
        assertTrue(config.isUnitTests());
        assertTrue(config.isIntegrationTests());
        assertTrue(config.isNfrTests());
        assertTrue(config.isPerformanceTests());
        assertTrue(config.isSecurityTests());
        assertTrue(config.isScalabilityTests());
        assertTrue(config.isReliabilityTests());
        assertTrue(config.isComplianceTests());
        assertEquals("junit5", config.getTestFramework());
        assertEquals("java", config.getLanguage());
        assertEquals("junit5", config.getFramework());
        assertTrue(config.isMockData());
        assertTrue(config.isTestUtilities());
        assertTrue(config.isPlaywrightTests());
        assertNotNull(config.getAdditionalProperties());
    }
    
    @Test
    public void testConstructorWithParameters() {
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("key", "value");
        
        TestConfig config = new TestConfig(
            false, false, false, false, false, false, false, false,
            "testng", "java", "junit5", false, false, additionalProps
        );
        
        assertFalse(config.isUnitTests());
        assertFalse(config.isIntegrationTests());
        assertFalse(config.isNfrTests());
        assertFalse(config.isPerformanceTests());
        assertFalse(config.isSecurityTests());
        assertFalse(config.isScalabilityTests());
        assertFalse(config.isReliabilityTests());
        assertFalse(config.isComplianceTests());
        assertEquals("testng", config.getTestFramework());
        assertEquals("java", config.getLanguage());
        assertEquals("junit5", config.getFramework());
        assertFalse(config.isMockData());
        assertFalse(config.isTestUtilities());
        assertEquals("value", config.getAdditionalProperties().get("key"));
    }
    
    @Test
    public void testGettersAndSetters() {
        TestConfig config = new TestConfig();
        
        config.setUnitTests(false);
        assertFalse(config.isUnitTests());
        
        config.setIntegrationTests(false);
        assertFalse(config.isIntegrationTests());
        
        config.setNfrTests(false);
        assertFalse(config.isNfrTests());
        
        config.setPerformanceTests(false);
        assertFalse(config.isPerformanceTests());
        
        config.setSecurityTests(false);
        assertFalse(config.isSecurityTests());
        
        config.setScalabilityTests(false);
        assertFalse(config.isScalabilityTests());
        
        config.setReliabilityTests(false);
        assertFalse(config.isReliabilityTests());
        
        config.setComplianceTests(false);
        assertFalse(config.isComplianceTests());
        
        config.setTestFramework("testng");
        assertEquals("testng", config.getTestFramework());
        
        config.setLanguage("python");
        assertEquals("python", config.getLanguage());
        
        config.setFramework("pytest");
        assertEquals("pytest", config.getFramework());
        
        config.setMockData(false);
        assertFalse(config.isMockData());
        
        config.setTestUtilities(false);
        assertFalse(config.isTestUtilities());

        config.setPlaywrightTests(false);
        assertFalse(config.isPlaywrightTests());
        
        Map<String, Object> props = new HashMap<>();
        props.put("test", "value");
        config.setAdditionalProperties(props);
        assertEquals(props, config.getAdditionalProperties());
    }
    
    @Test
    public void testBuilder() {
        TestConfig config = TestConfig.builder()
            .unitTests(false)
            .integrationTests(false)
            .nfrTests(false)
            .performanceTests(false)
            .securityTests(false)
            .scalabilityTests(false)
            .reliabilityTests(false)
            .complianceTests(false)
            .testFramework("testng")
            .language("python")
            .framework("pytest")
            .mockData(false)
            .testUtilities(false)
            .playwrightTests(false)
            .build();
        
        assertFalse(config.isUnitTests());
        assertFalse(config.isIntegrationTests());
        assertFalse(config.isNfrTests());
        assertFalse(config.isPerformanceTests());
        assertFalse(config.isSecurityTests());
        assertFalse(config.isScalabilityTests());
        assertFalse(config.isReliabilityTests());
        assertFalse(config.isComplianceTests());
        assertEquals("testng", config.getTestFramework());
        assertEquals("python", config.getLanguage());
        assertEquals("pytest", config.getFramework());
        assertFalse(config.isMockData());
        assertFalse(config.isTestUtilities());
        assertFalse(config.isPlaywrightTests());
    }
    
    @Test
    public void testBuilderWithAdditionalProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("key1", "value1");
        props.put("key2", 123);
        
        TestConfig config = TestConfig.builder()
            .additionalProperties(props)
            .build();
        
        assertEquals("value1", config.getAdditionalProperties().get("key1"));
        assertEquals(123, config.getAdditionalProperties().get("key2"));
    }
    
    @Test
    public void testBuilderChaining() {
        TestConfig config = TestConfig.builder()
            .unitTests(true)
            .integrationTests(true)
            .nfrTests(true)
            .performanceTests(true)
            .securityTests(true)
            .scalabilityTests(true)
            .reliabilityTests(true)
            .complianceTests(true)
            .testFramework("junit5")
            .mockData(true)
            .testUtilities(true)
            .build();
        
        assertNotNull(config);
        assertTrue(config.isUnitTests());
        assertEquals("junit5", config.getTestFramework());
    }
    
    @Test
    public void testToString() {
        TestConfig config = new TestConfig();
        String str = config.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("TestConfig"));
        assertTrue(str.contains("unitTests"));
    }

    @Test
    public void testPlaywrightTestsDefaultTrue() {
        assertTrue(new TestConfig().isPlaywrightTests());
        assertTrue(TestConfig.builder().build().isPlaywrightTests());
    }
    
    @Test
    public void testNullAdditionalProperties() {
        TestConfig config = new TestConfig(
            true, true, true, true, true, true, true, true,
            "junit5", "java", "junit5", true, true, null
        );
        
        assertNotNull(config.getAdditionalProperties());
        assertTrue(config.getAdditionalProperties().isEmpty());
    }
}

