# eGain OAS SDK Java Examples

This directory contains examples demonstrating how to use the eGain OAS SDK Java for different scenarios.

## 📁 Examples

### **Hello World API Example**
- **File**: `HelloWorldExample.java`
- **Description**: Complete example showing how to generate a Jersey application from OpenAPI specification
- **Features**: 
  - OpenAPI specification loading
  - Specification validation
  - Complete project generation
  - Test suite generation
  - SLA enforcement generation
  - Mock data generation

### **Configuration Example**
- **File**: `config.yaml`
- **Description**: Example configuration file showing all available options
- **Features**:
  - Generator configuration
  - Test configuration
  - SLA configuration
  - Security configuration
  - Performance configuration

### **Generate SDK and XSD for published**
- **File**: `egain/oassdk/examples/GeneratePublishedSDK.java`
- **Description**: Generates Java Jersey SDK and XSD for the published folder (core API in `core/`, referenced schemas in `models/`). Output is written under the published folder (e.g. `C:\eGain\published\sdk`).
- **Features**:
  - Uses `GeneratorConfig.searchPaths` so the spec and model refs resolve from any working directory
  - Single run produces both SDK and XSD (XSD under `sdk/src/main/resources/xsd/`)
  - Configurable via system property `oas.published.root` or env `OAS_PUBLISHED_ROOT` (default: `C:\eGain\published`)
- **How to run**:
  1. Build the SDK: from repo root run `mvn clean package -DskipTests`.
  2. **Option A – Java driver (recommended)**: In your IDE, add `examples` as a source folder (or ensure `egain.oassdk.examples` is on the classpath), then run `egain.oassdk.examples.GeneratePublishedSDK`. No need to change working directory; paths are resolved via `searchPaths`.
  3. **Option B – CLI from published folder**: The CLI does not pass search paths, so refs from the spec to `models/` (outside the spec directory) will fail resolution. Prefer **Option A** (Java driver) for the published layout. If your spec has no external refs outside its directory, you can use:
     ```bash
     cd C:\eGain\published
     java -jar path\to\oas-sdk-java\target\oas-sdk-java-1.11-SNAPSHOT.jar generate core/usermgr/v4/api.yaml -l java -f jersey -p egain.ws.usermgr.v4 -o ./sdk
     ```
     When it works, SDK and XSD are under `C:\eGain\published\sdk\` (XSD in `sdk/src/main/resources/xsd/`).

## 🚀 Quick Start

### **1. Basic Usage**
```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class BasicExample {
    public static void main(String[] args) {
        try {
            // Initialize SDK
            OASSDK sdk = new OASSDK();
            
            // Load specification
            sdk.loadSpec("openapi.yaml");
            
            // Generate application
            sdk.generateApplication(
                "java",
                "jersey",
                "com.example.api",
                "./generated-app"
            );
            
            // Generate tests
            sdk.generateTests(
                List.of("unit", "integration"),
                "./generated-tests"
            );
            
            // Generate documentation
            sdk.generateDocumentation("./generated-docs");
            
            System.out.println("Generation complete!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### **2. Advanced Usage with Configuration**
```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class AdvancedExample {
    public static void main(String[] args) {
        try {
            // Configure generators
            GeneratorConfig generatorConfig = new GeneratorConfig();
            generatorConfig.setLanguage("java");
            generatorConfig.setFramework("jersey");
            generatorConfig.setPackageName("com.example.api");
            
            TestConfig testConfig = new TestConfig();
            testConfig.setTestFramework("junit5");
            testConfig.setIncludePerformanceTests(true);
            testConfig.setIncludeSecurityTests(true);
            testConfig.setIncludeNFRTests(true);
            
            SLAConfig slaConfig = new SLAConfig();
            slaConfig.setSlaFile("sla.yaml");
            slaConfig.setMonitoringStack(List.of("prometheus", "grafana"));
            
            // Initialize SDK
            OASSDK sdk = new OASSDK(generatorConfig, testConfig, slaConfig);
            
            // Load specifications
            sdk.loadSpec("openapi.yaml");
            sdk.loadSLA("sla.yaml");
            
            // Validate specification
            boolean isValid = sdk.validateSpec();
            System.out.println("Specification is valid: " + isValid);
            
            // Generate everything
            sdk.generateAll("./generated");
            
            System.out.println("Generation complete!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### **3. Filtering APIs and Operations**
```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilteringExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Filter by paths
            sdk.filterPaths(List.of("/api/users", "/api/posts"));
            
            // Filter by operations
            Map<String, List<String>> operationFilters = new HashMap<>();
            operationFilters.put("/api/users", List.of("GET", "POST"));
            operationFilters.put("/api/posts", List.of("GET"));
            sdk.filterOperations(operationFilters);
            
            // Generate only for filtered APIs
            sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
            sdk.generateTests(List.of("unit", "integration"), "./generated-tests");
            
            // Clear filters if needed
            // sdk.clearFilters();
            
            System.out.println("Generation complete!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### **4. Python FastAPI Generation**
```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PythonExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Generate Python FastAPI application
            sdk.generateApplication(
                "python",
                "fastapi",
                "api",
                "./generated-python-app"
            );
            
            System.out.println("Python FastAPI application generated!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### **4a. Python FastAPI with Filtering**
```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PythonFilteringExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Filter to specific paths
            sdk.filterPaths(List.of("/api/users", "/api/posts"));
            
            // Or filter by operations
            Map<String, List<String>> operationFilters = new HashMap<>();
            operationFilters.put("/api/users", List.of("GET", "POST"));
            operationFilters.put("/api/posts", List.of("GET"));
            sdk.filterOperations(operationFilters);
            
            // Generate Python FastAPI app for filtered paths only
            // Only models referenced by filtered operations will be generated
            sdk.generateApplication("python", "fastapi", "api", "./generated-python-app");
            
            System.out.println("Python FastAPI application generated with filtering!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

**Python FastAPI Features:**
- Automatic OpenAPI integration with Swagger UI and ReDoc
- Pydantic models with full type safety and validation
- Async route handlers for high performance
- Intelligent path grouping (one router per parent path)
- Smart model generation (only referenced schemas)
- Full support for `allOf`, `oneOf`, `anyOf` schema composition
- CORS middleware pre-configured
- Global exception handlers
- Pydantic Settings for configuration management

### **5. CLI Usage**
```bash
# Generate all artifacts
java -jar oas-sdk.jar all --spec openapi.yaml --output ./generated

# Generate only application code (Java Jersey)
java -jar oas-sdk.jar generate --spec openapi.yaml --language java --framework jersey --output ./generated-app

# Generate only application code (Python FastAPI)
java -jar oas-sdk.jar generate --spec openapi.yaml --language python --framework fastapi --output ./generated-app

# Generate only documentation
java -jar oas-sdk.jar docs --spec openapi.yaml --output ./generated-docs

# Validate OpenAPI specification
java -jar oas-sdk.jar validate --spec openapi.yaml

# Generate tests
java -jar oas-sdk.jar tests --spec openapi.yaml --types unit,integration,postman --output ./generated-tests
```

## 🔧 Configuration Options

### **Generator Configuration**
```java
GeneratorConfig config = new GeneratorConfig();
config.setLanguage("java");              // java, python, nodejs, go, csharp
config.setFramework("jersey");     // jersey, fastapi, express, gin, aspnet
config.setPackageName("com.example.api");
config.setVersion("1.0.0");
config.setOutputDir("./generated");
```

### **Test Configuration**
```java
TestConfig testConfig = new TestConfig();
testConfig.setTestFramework("junit5");  // junit5, pytest, jest
testConfig.setIncludeUnitTests(true);
testConfig.setIncludeIntegrationTests(true);
testConfig.setIncludeNFRTests(true);
testConfig.setIncludePerformanceTests(true);
testConfig.setIncludeSecurityTests(true);
```

### **SLA Configuration**
```java
SLAConfig slaConfig = new SLAConfig();
slaConfig.setSlaFile("sla.yaml");
slaConfig.setMonitoringStack(List.of("prometheus", "grafana"));
slaConfig.setApiGateway("aws");  // aws, azure, gcp, kong, nginx
```

## 📊 Generated Output

### **Application Structure**
```
generated/
├── generated-app/         # Generated application code
│   ├── src/main/         # Main application code
│   └── src/test/         # Test code
├── generated-tests/      # Generated test suite
│   ├── unit/             # Unit tests (JUnit 5)
│   ├── integration/      # Integration tests (Jersey Test)
│   ├── nfr/              # NFR tests
│   └── postman/          # Postman collection
├── generated-mock-data/  # Mock data
│   ├── json/             # JSON examples
│   └── factories/        # Test data factories
├── generated-sla/        # SLA enforcement code
│   └── gateway/          # API Gateway scripts
├── generated-monitoring/ # Monitoring setup
│   ├── prometheus/       # Prometheus configuration
│   └── grafana/          # Grafana dashboards
└── generated-docs/      # Documentation
    ├── redocly/          # Redocly documentation
    ├── swagger-ui/       # Swagger UI
    └── markdown/         # Markdown documentation
```

### **Test Types**
- **Unit Tests**: Service, controller, and model tests (JUnit 5)
- **Integration Tests**: End-to-end API testing (Jersey Test)
- **NFR Tests**: Performance, security, scalability, reliability, compliance
- **Postman Collection**: Complete API testing collection with automated scripts
- **Mock Data**: Structured test data and generators
- **Randomized Sequence Tests**: Property-based testing with random API call sequences

### **SLA Enforcement**
- **API Gateway Policies**: Rate limiting, authentication, authorization
- **Monitoring**: Prometheus metrics, Grafana dashboards
- **Alerting**: SLA violation alerts
- **Compliance**: GDPR, ISO 27001 compliance features

## 🧪 Testing

### **Running Generated Tests**
```bash
# Run all tests
mvn test

# Run specific test types
mvn test -Dtest="*UnitTest"
mvn test -Dtest="*IntegrationTest"
mvn test -Dtest="*NFRTest"

# Run with coverage
mvn test jacoco:report
```

### **Test Execution**
- **Unit Tests**: Fast execution, isolated testing (JUnit 5)
- **Integration Tests**: End-to-end API testing (Jersey Test)
- **NFR Tests**: Performance, security, scalability validation
- **Postman Collection**: Run with Newman CLI
- **Mock Data**: Dynamic test data generation

## 📈 Performance Testing

### **Performance Requirements**
- **Response Time**: P95, P99 percentile testing
- **Throughput**: Requests per second testing
- **Concurrent Users**: Multi-user scenario testing
- **Load Testing**: Normal and peak load testing
- **Stress Testing**: Beyond normal capacity testing

### **Performance Configuration**
```yaml
performance:
  response_time_p95: "200ms"
  response_time_p99: "500ms"
  throughput_normal: 1000
  throughput_peak: 2000
  concurrent_users: 500
```

## 🔒 Security Testing

### **Security Features**
- **Injection Prevention**: SQL, XSS, LDAP, NoSQL injection
- **Rate Limiting**: Request throttling validation
- **Input Validation**: Data sanitization testing
- **Authentication**: API key, JWT, OAuth2 support
- **Authorization**: RBAC and permissions

### **Security Configuration**
```yaml
security:
  rate_limiting: true
  input_validation: true
  injection_prevention: true
  authentication: true
  authorization: true
```

## 📊 Monitoring

### **Generated Monitoring**
- **Prometheus**: Metrics collection and storage
- **Grafana**: Metrics visualization and dashboards
- **Health Checks**: Application health monitoring
- **Alerting**: SLA violation alerts

### **Monitoring Setup**
```bash
# Start monitoring stack
docker-compose -f monitoring/docker-compose.yml up -d

# Access Grafana
open http://localhost:3000
```

## 🚀 Deployment

### **Container Support**
- **Docker**: Application containerization
- **Docker Compose**: Multi-service orchestration
- **Kubernetes**: Container orchestration
- **Health Checks**: Container health monitoring

### **Deployment Commands**
```bash
# Build application
mvn clean package

# Build Docker image
docker build -t hello-world-api .

# Run with Docker Compose
docker-compose up -d
```

## 🔄 CI/CD Integration

### **Pipeline Generation**
- **GitHub Actions**: CI/CD pipeline
- **GitLab CI**: CI/CD pipeline
- **Jenkins**: CI/CD pipeline
- **Azure DevOps**: CI/CD pipeline

### **Pipeline Features**
- **Automated Testing**: Run all generated tests
- **Code Quality**: Static analysis and linting
- **Security Scanning**: Vulnerability scanning
- **Performance Testing**: Load and stress testing
- **Deployment**: Automated deployment

## 📚 Documentation

### **Generated Documentation**
- **Redocly Documentation**: Interactive, beautiful documentation with CLI integration
- **Swagger UI**: Professional interactive API documentation
- **Markdown Documentation**: Comprehensive markdown with HTML conversion
- **OpenAPI Specs**: Enhanced specifications with examples
- **Test Documentation**: Test guides and examples
- **Project Documentation**: Complete project documentation

### **Documentation Access**
- **Redocly**: Build and serve using generated scripts
- **Swagger UI**: Open `swagger-ui.html` in browser
- **Markdown**: View `API_DOCUMENTATION.md` or `API_DOCUMENTATION.html`
- **Test Docs**: `./generated-docs/markdown/TEST_DOCUMENTATION.md`
- **Project Docs**: `./generated-docs/markdown/PROJECT_DOCUMENTATION.md`

## 🏆 Benefits

### **Development Efficiency**
- **Faster Development**: OpenAPI-first approach
- **Reduced Errors**: Automated code generation
- **Consistent Quality**: Standardized templates
- **Comprehensive Testing**: Automated test generation

### **Quality Assurance**
- **Comprehensive Testing**: Unit, integration, and NFR tests
- **Security Validation**: Automated security testing
- **Performance Validation**: Performance requirement compliance
- **Compliance Validation**: Regulatory compliance testing

### **Operational Excellence**
- **Monitoring**: Comprehensive observability
- **SLA Enforcement**: Automated compliance monitoring
- **Scalability**: Auto-scaling and load balancing
- **Reliability**: Fault tolerance and graceful degradation

## 📖 Available Examples

### **HelloWorldExample.java**
Basic example showing:
- Loading OpenAPI and SLA specifications
- Generating Jersey application
- Generating test suites
- Generating mock data
- Generating SLA enforcement and monitoring
- Generating documentation
- Generating complete project

### **CompleteExample.java**
Advanced example demonstrating:
- Configuration-based SDK initialization
- Specification validation
- Metadata extraction
- Comprehensive test generation
- Complete project generation
- Test execution

### **config.yaml**
Example configuration file showing all available options for:
- Generator configuration
- Test configuration
- SLA configuration
- Security configuration
- Performance configuration

## 🔗 Related Documentation

- [Main README](../README.md) - Complete SDK documentation
- [SDK Documentation](../SDK_DOCUMENTATION.md) - Comprehensive SDK guide
- [CHANGELOG](../CHANGELOG.md) - Version history and changes
- [Release Notes](../RELEASE_NOTES_v1.0.0.md) - Release information

---

**eGain OAS SDK Java Examples**: Comprehensive examples for OpenAPI-first development  
**Version**: 1.0.0  
**License**: MIT  
**Maintainer**: Development Team
