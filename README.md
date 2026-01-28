# OAS SDK - OpenAPI Specification SDK

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![Jersey](https://img.shields.io/badge/Jersey-3.1.3-blue.svg)](https://eclipse-ee4j.github.io/jersey/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg)](CONTRIBUTING.md)

A comprehensive Java SDK for generating applications, tests, mock data for tests, SLA enforcement, and professional documentation from OpenAPI specifications. Built with modern libraries and best practices for enterprise-grade API development.

## 📖 Table of Contents

- [Features](#-features)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)
- [Support](#-support)

All generated Java applications use industry-standard **JAX-RS annotations** (@Path, @GET, @POST, etc.) with Jakarta EE, Grizzly HTTP server, and HK2 dependency injection for lightweight, standards-based RESTful web services.

**Production-Ready**: Code quality validated, fully tested, and optimized for enterprise use.


## 🚀 Features

### Core Capabilities
- **📦 Code Generation**: Generate production-ready applications for multiple languages and frameworks
  - ✅ **Java Jersey/JAX-RS** (Fully Implemented - 100% Feature Complete)
    - Automatic @Actor annotations with security scheme extraction
    - API version extraction from server URLs
    - Inline schema model generation
    - Query parameter validation with Jakarta validation constraints
    - Smart filtering of intermediate composition schemas
    - Improved type resolution for arrays and references
  - ✅ **Python FastAPI** (Fully Implemented - 100% Feature Complete)
    - Inline schema collection with Pydantic models
    - API version extraction and router prefixes
    - Query parameter validation with Pydantic validators
    - Security decorators with dependency injection
    - Scope extraction and checking
  - ✅ **Python Flask** (Fully Implemented - 100% Feature Complete)
    - Inline schema collection with dataclasses
    - API version extraction and blueprint prefixes
    - Query parameter validation with explicit checks
    - Security decorators with middleware pattern
    - Scope extraction and checking
  - ✅ **Node.js Express** (Fully Implemented - 100% Feature Complete)
    - Inline schema collection
    - API version extraction and route prefixes
    - Query parameter validation with express-validator
    - JWT authentication middleware
    - Scope-based authorization
  - ⚠️ **Go Gin** (Stub Implementation)
  - ⚠️ **C# ASP.NET Core** (Stub Implementation)

- **🧪 Test Generation**: Comprehensive test suites with multiple languages and frameworks
  - **✨ Automatic Language Detection**: Tests are generated in the same language as your application
  - **Java**:
    - Unit Tests (JUnit 5 with Mockito)
    - Integration Tests (JAX-RS Client)
  - **Python**:
    - Unit Tests (pytest with unittest.mock)
    - Integration Tests (pytest with requests)
    - Configuration: pytest.ini, conftest.py fixtures
  - **Node.js**:
    - Unit Tests (Jest with mocks)
    - Integration Tests (Jest with axios)
    - Configuration: jest.config.js, setup.js
  - **Cross-Language**:
    - NFR Tests (Non-functional requirements)
    - Performance Tests (JMeter, Gatling)
    - Security Tests (OWASP ZAP)
    - Postman Collections with automated scripts
    - Randomized Sequence Testing

- **📊 Mock Data**: Realistic mock data generation based on OpenAPI schemas
  - JSON examples
  - Test data factories
  - cURL commands

- **🛡️ SLA Enforcement**: API Gateway scripts and SLA enforcement
  - Rate limiting
  - Response time monitoring
  - Error rate tracking
  - Prometheus and Grafana integration

- **📚 Professional Documentation**: Modern documentation generation using industry-standard tools
  - **Redocly**: Interactive documentation with CLI integration
  - **Swagger UI**: Professional Swagger UI with library integration
  - **Markdown**: Rich markdown with Flexmark (tables, TOC, syntax highlighting)
  - **OpenAPI Specs**: Enhanced specifications with examples and validation
  - **Template-based**: FreeMarker templates for customizable documentation

- **📝 Comprehensive Logging**: Production-ready logging with automatic file rotation
  - **File-based logging** with automatic rotation (configurable size, default: 1MB)
  - **Configurable settings** via properties file, system properties, or environment variables
  - **Error level logging** for all exception cases with full stack traces
  - **Console and file logging** support
  - **Customizable log format** and levels

## 📋 Prerequisites

- **Java 21** or higher (JDK)
- **Maven 3.6** or higher
- **Node.js 18+** (optional, for Redocly CLI documentation generation)
- **Python 3.8+** (optional, for running generated Python FastAPI applications)
- **Git** (for cloning the repository)

### Installing Java 21

**macOS (using Homebrew):**
```bash
brew install openjdk@21
```

**Linux (using apt):**
```bash
sudo apt-get update
sudo apt-get install openjdk-21-jdk
```

**Verify Installation:**
```bash
java -version
# Should show: openjdk version "21.x.x"
```

## 🏗️ Project Structure

```
oas-sdk-java/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── egain/oassdk/
│   │   │       ├── OASSDK.java              # Main SDK class
│   │   │       ├── cli/                     # Command-line interface
│   │   │       ├── config/                  # Configuration classes
│   │   │       ├── connectors/              # Business logic connectors
│   │   │       ├── core/                    # Core functionality
│   │   │       │   ├── exceptions/          # Exception classes
│   │   │       │   ├── logging/             # Logging configuration
│   │   │       │   │   └── LoggerConfig.java
│   │   │       │   ├── metadata/           # Metadata extraction
│   │   │       │   ├── parser/             # OpenAPI parser
│   │   │       │   └── validator/          # Specification validator
│   │   │       ├── docs/                    # Documentation generators
│   │   │       │   ├── DocumentationGenerator.java
│   │   │       │   ├── RedoclyConfigGenerator.java
│   │   │       │   ├── SwaggerUIGenerator.java
│   │   │       │   ├── MarkdownGenerator.java
│   │   │       │   ├── OpenAPISpecGenerator.java
│   │   │       │   └── TemplateGenerator.java
│   │   │       ├── generators/              # Code generators
│   │   │       │   ├── java/               # Java/Jersey generator
│   │   │       │   ├── python/              # Python generators (FastAPI, Flask)
│   │   │       │   ├── nodejs/              # Node.js/Express generator
│   │   │       │   ├── go/                 # Go/Gin generator
│   │   │       │   └── csharp/             # C#/ASPNET generator
│   │   │       ├── test/                    # Test generation
│   │   │       ├── testgenerators/          # Test generators
│   │   │       └── sla/                     # SLA processing
│   │   └── resources/
│   │       ├── logger.properties            # Logging configuration
│   │       └── templates/                   # FreeMarker templates
│   │           ├── test-documentation.ftl
│   │           └── project-documentation.ftl
│   └── test/                                # Unit tests
├── generated-code/                          # Generated code output (gitignored)
│   ├── api-v3/                             # Generated API v3 application
│   ├── openapi1/                           # Generated openapi1 application
│   ├── openapi3/                           # Generated openapi3 application
│   ├── openapi4/                           # Generated openapi4 application
│   └── openapi5/                           # Generated openapi5 application
└── target/                                 # Build output (class files)
    └── classes/
```

## 🚀 Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>egain.oas-sdk</groupId>
    <artifactId>egain-oas-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'egain.oas-sdk:egain-oas-sdk-java:1.0.0'
```

### 2. Basic Usage

```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Example {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            
            // Load OpenAPI specification
            sdk.loadSpec("openapi.yaml");
            
            // Generate Jersey application
            sdk.generateApplication(
                "java", 
                "jersey", 
                "com.example.api", 
                "./generated-app"
            );
            
            // Generate Python Flask application
            // sdk.generateApplication("python", "flask", "api", "./generated-app");
            
            // Or generate Node.js Express application
            // sdk.generateApplication("nodejs", "express", "api", "./generated-app");
            
            // Generate comprehensive test suites
            // Tests are automatically generated in the same language as your application
            sdk.generateTests(
                List.of("unit", "integration", "nfr", "postman"), 
                "./generated-tests"
            );
            
            // For multi-language projects, specify test language explicitly:
            // TestConfig testConfig = TestConfig.builder()
            //     .language("python")     // java, python, or nodejs
            //     .framework("pytest")    // junit5, pytest, or jest
            //     .build();
            // OASSDK sdk = new OASSDK(generatorConfig, testConfig, null);
            // sdk.generateTests(List.of("unit", "integration"), "./generated-tests");
            
            // Generate professional documentation
            sdk.generateDocumentation("./generated-docs");
            
            // Generate mock data
            sdk.generateMockData("./generated-mock-data");
            
            System.out.println("Generation complete!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### 3. Security and @Actor Annotations

The SDK automatically generates `@Actor` annotations for Jersey resources based on OpenAPI security specifications. The annotations include `ActorType` and `OAuthScope` enums extracted from security schemes.

**Example OpenAPI Security Definition:**
```yaml
security:
  - oAuthUser:
      - knowledge.contentmgr.read
  - oAuthCustomer:
      - knowledge.contentmgr.read
```

**Generated Resource:**
```java
@Actor(type = { ActorType.USER, ActorType.CUSTOMER }, scope = {
        OAuthScope.KNOWLEDGE_CONTENTMGR_READ
})
@Path("/api/v4/prompts")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class PromptsResource {
    // Resource methods...
}
```

**Security Scheme Mapping:**
- `oAuthUser` → `ActorType.USER`
- `oAuthCustomer` → `ActorType.CUSTOMER`
- `oAuthAnonymousCustomer` → `ActorType.ANONYMOUS_CUSTOMER`
- `oAuthClient` → `ActorType.CLIENT_APP`

Scopes are automatically converted to enum format (e.g., `knowledge.contentmgr.read` → `KNOWLEDGE_CONTENTMGR_READ`).

### 4. Filtering APIs and Operations

The SDK allows you to filter which APIs and operations to generate code for, rather than generating for all APIs in the OpenAPI specification. This is useful when working with large specifications where you only need specific endpoints.

#### Filter by Paths

Filter to include only specific paths (includes all HTTP methods for those paths):

**Note:** The SDK automatically extracts API version from server URLs and includes it in `@Path` annotations. For example, if your server URL is `https://api.example.com/knowledge/contentmgr/v4`, the generated `@Path` will include `/knowledge/contentmgr/v4` prefix.

```java
OASSDK sdk = new OASSDK();
sdk.loadSpec("openapi.yaml");

// Filter to include only specific paths
sdk.filterPaths(List.of("/api/users", "/api/posts", "/api/comments"));

// Generate only for filtered paths
sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
```

#### Filter by Operations

Filter to include only specific HTTP methods for specific paths:

```java
OASSDK sdk = new OASSDK();
sdk.loadSpec("openapi.yaml");

// Filter to include only specific operations
Map<String, List<String>> operationFilters = new HashMap<>();
operationFilters.put("/api/users", List.of("GET", "POST"));
operationFilters.put("/api/posts", List.of("GET"));
sdk.filterOperations(operationFilters);

// Generate only for filtered operations
sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
```

#### Combine Path and Operation Filters

You can combine both filters for fine-grained control:

```java
OASSDK sdk = new OASSDK();
sdk.loadSpec("openapi.yaml");

// First, filter by paths
sdk.filterPaths(List.of("/api/users", "/api/posts"));

// Then, filter operations for specific paths
Map<String, List<String>> operationFilters = new HashMap<>();
operationFilters.put("/api/users", List.of("GET", "POST"));
operationFilters.put("/api/posts", List.of("GET"));
sdk.filterOperations(operationFilters);

// Generate only for filtered paths and operations
sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
sdk.generateTests(List.of("unit", "integration"), "./generated-tests");
```

#### Clear Filters

To clear all filters and generate for all APIs:

```java
sdk.clearFilters();
```

**Note:** Filters apply to all generation methods:
- `generateApplication()` - Only generates controllers/routers for filtered paths/operations
- `generateTests()` - Only generates tests for filtered paths/operations
- `generateDocumentation()` - Only documents filtered paths/operations
- `generateMockData()` - Only generates mock data for filtered paths/operations

**Note:** The SDK automatically generates models for in-lined schemas in response bodies and filters out intermediate schemas used only in `allOf`/`oneOf`/`anyOf` compositions to reduce unnecessary code generation.

**Example: Complete Workflow with Filtering**

```java
OASSDK sdk = new OASSDK();
sdk.loadSpec("openapi.yaml");

// Filter to only generate for user management APIs
sdk.filterPaths(List.of("/api/users", "/api/user-roles"));

// Further filter to only GET and POST operations
Map<String, List<String>> operationFilters = new HashMap<>();
operationFilters.put("/api/users", List.of("GET", "POST"));
operationFilters.put("/api/user-roles", List.of("GET"));
sdk.filterOperations(operationFilters);

// Generate everything for filtered APIs only
sdk.generateApplication("java", "jersey", "com.example.api", "./generated-app");
sdk.generateTests(List.of("unit", "integration", "postman"), "./generated-tests");
sdk.generateDocumentation("./generated-docs");
sdk.generateMockData("./generated-mock-data");
```

### 5. External File References and Security

The SDK supports external file references in OpenAPI specifications using `$ref`. You can configure search paths for external schemas and files.

#### Configuring Search Paths

**Via Configuration:**
```java
GeneratorConfig config = GeneratorConfig.builder()
    .searchPaths(List.of("/path/to/schemas", "/another/path"))
    .build();

OASSDK sdk = new OASSDK(config, null, null);
sdk.loadSpec("openapi.yaml");
```

**Via Environment Variable:**
```bash
export OAS_SEARCH_PATH=/path/to/schemas
```

**Via System Property:**
```bash
java -Doas.search.path=/path/to/schemas ...
```

**Multiple Sources:**
The SDK checks search paths in this order:
1. Configuration (`GeneratorConfig.searchPaths`)
2. Environment variable (`OAS_SEARCH_PATH`)
3. System property (`oas.search.path`)

#### Security Features

The SDK includes comprehensive security features for handling external file references:

- **Path Traversal Protection**: Prevents directory escape attacks (e.g., `../../../etc/passwd`)
- **File Size Limits**: Maximum 100 MB per file to prevent resource exhaustion
- **File Extension Validation**: Only `.yaml`, `.yml`, and `.json` files are allowed
- **Resource Limits**: 
  - Maximum recursion depth: 50 levels for external file resolution
  - Maximum search depth: 10 levels for recursive file search
- **Input Validation**: All file paths are sanitized and validated before processing
- **Security Logging**: Path traversal attempts are logged for security monitoring

**Example with Security:**
```java
// The SDK automatically validates and protects against:
// - Path traversal attacks
// - Oversized files
// - Invalid file types
// - Resource exhaustion

OASSDK sdk = new OASSDK();
sdk.loadSpec("openapi.yaml"); // Automatically validates and protects
```

**Thread Safety:**
The `PathResolver` class is thread-safe and can be used concurrently by multiple threads. Recursion depth tracking is thread-local to prevent interference.

### 6. Advanced Usage with Configuration

You can also configure the SDK with custom configurations for more control:

```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.config.SLAConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class ConfiguredExample {
    public static void main(String[] args) {
        try {
            // Create configurations
            GeneratorConfig generatorConfig = GeneratorConfig.builder()
                .language("java")
                .framework("jersey")
                .packageName("com.example.api")
                .searchPaths(List.of("/path/to/schemas")) // Optional: external file search paths
                .build();
            
            TestConfig testConfig = new TestConfig();
            testConfig.setTestFramework("junit5");
            testConfig.setIncludePerformanceTests(true);
            testConfig.setIncludeSecurityTests(true);
            
            SLAConfig slaConfig = new SLAConfig();
            slaConfig.setSlaFile("sla.yaml");
            slaConfig.setMonitoringStack(List.of("prometheus", "grafana"));
            
            // Initialize SDK with configurations
            OASSDK sdk = new OASSDK(generatorConfig, testConfig, slaConfig);
            
            // Load specifications
            sdk.loadSpec("openapi.yaml");
            sdk.loadSLA("sla.yaml");
            
            // Validate specification
            boolean isValid = sdk.validateSpec();
            System.out.println("Specification is valid: " + isValid);
            
            // Generate all artifacts
            sdk.generateAll("./generated");
            
            System.out.println("Generation complete!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### 7. Multi-Language Test Generation

The SDK automatically generates tests in the same language as your application code. Here are examples for each supported language:

#### Automatic Test Language Detection

When you configure your application language, tests are automatically generated in the same language:

```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class AutoTestExample {
    public static void main(String[] args) {
        try {
            // Configure for Python FastAPI
            GeneratorConfig config = GeneratorConfig.builder()
                .language("python")
                .framework("fastapi")
                .packageName("api")
                .build();
            
            OASSDK sdk = new OASSDK(config, null, null);
            sdk.loadSpec("openapi.yaml");
            
            // Generate Python application
            sdk.generateApplication("python", "fastapi", "api", "./generated-app");
            
            // Generate tests - automatically uses Python/pytest!
            sdk.generateTests(List.of("unit", "integration"), "./generated-tests");
            
            System.out.println("Python app and tests generated!");
            
        } catch (OASSDKException e) {
            e.printStackTrace();
        }
    }
}
```

#### Explicit Test Language Configuration

You can also explicitly configure the test language and framework:

```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class ExplicitTestExample {
    public static void main(String[] args) {
        try {
            // Configure application
            GeneratorConfig generatorConfig = GeneratorConfig.builder()
                .language("nodejs")
                .framework("express")
                .packageName("api")
                .build();
            
            // Configure tests explicitly
            TestConfig testConfig = TestConfig.builder()
                .language("nodejs")        // Override test language
                .framework("jest")         // Override test framework
                .unitTests(true)
                .integrationTests(true)
                .build();
            
            OASSDK sdk = new OASSDK(generatorConfig, testConfig, null);
            sdk.loadSpec("openapi.yaml");
            
            // Generate Node.js application
            sdk.generateApplication("nodejs", "express", "api", "./generated-app");
            
            // Generate Node.js Jest tests
            sdk.generateTests(List.of("unit", "integration"), "./generated-tests");
            
            System.out.println("Node.js app and Jest tests generated!");
            
        } catch (OASSDKException e) {
            e.printStackTrace();
        }
    }
}
```

#### Cross-Language Project Example

For projects that need tests in multiple languages (e.g., microservices):

```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import java.util.List;

public class CrossLanguageExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Generate Java application with Java tests
            GeneratorConfig javaConfig = GeneratorConfig.builder()
                .language("java").framework("jersey").packageName("com.example.api").build();
            TestConfig javaTestConfig = TestConfig.builder()
                .language("java").framework("junit5").unitTests(true).integrationTests(true).build();
            
            OASSDK javaSdk = new OASSDK(javaConfig, javaTestConfig, null);
            javaSdk.loadSpec("openapi.yaml");
            javaSdk.generateApplication("java", "jersey", "com.example.api", "./java-service");
            javaSdk.generateTests(List.of("unit", "integration"), "./java-service/tests");
            
            // Generate Python application with Python tests
            GeneratorConfig pythonConfig = GeneratorConfig.builder()
                .language("python").framework("fastapi").packageName("api").build();
            TestConfig pythonTestConfig = TestConfig.builder()
                .language("python").framework("pytest").unitTests(true).integrationTests(true).build();
            
            OASSDK pythonSdk = new OASSDK(pythonConfig, pythonTestConfig, null);
            pythonSdk.loadSpec("openapi.yaml");
            pythonSdk.generateApplication("python", "fastapi", "api", "./python-service");
            pythonSdk.generateTests(List.of("unit", "integration"), "./python-service/tests");
            
            // Generate Node.js application with Jest tests
            GeneratorConfig nodejsConfig = GeneratorConfig.builder()
                .language("nodejs").framework("express").packageName("api").build();
            TestConfig nodejsTestConfig = TestConfig.builder()
                .language("nodejs").framework("jest").unitTests(true).integrationTests(true).build();
            
            OASSDK nodejsSdk = new OASSDK(nodejsConfig, nodejsTestConfig, null);
            nodejsSdk.loadSpec("openapi.yaml");
            nodejsSdk.generateApplication("nodejs", "express", "api", "./nodejs-service");
            nodejsSdk.generateTests(List.of("unit", "integration"), "./nodejs-service/tests");
            
            System.out.println("Multi-language microservices and tests generated!");
            
        } catch (OASSDKException e) {
            e.printStackTrace();
        }
    }
}
```

### 8. Python FastAPI Generation

Generate a complete Python FastAPI application from your OpenAPI specification:

```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;

public class PythonExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            
            // Load OpenAPI specification
            sdk.loadSpec("openapi.yaml");
            
            // Generate Python FastAPI application
            sdk.generateApplication(
                "python",      // Language
                "fastapi",     // Framework
                "api",         // Package name (Python module name)
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

#### Generated Python FastAPI Structure

```
generated-python-app/
├── main.py                    # FastAPI application entry point
├── requirements.txt            # Python dependencies
├── .env.example               # Environment variables template
├── README.md                  # Setup and usage instructions
└── api/                       # Main package
    ├── __init__.py
    ├── routers/               # API route handlers
    │   ├── __init__.py
    │   └── *.py              # Router files grouped by parent path
    ├── models/                # Pydantic models
    │   ├── __init__.py
    │   └── *.py              # Model files (only referenced schemas)
    ├── services/              # Business logic
    │   ├── __init__.py
    │   └── api_service.py
    ├── config/                # Configuration
    │   ├── __init__.py
    │   └── settings.py        # Pydantic Settings
    └── exceptions/            # Exception handlers
        ├── __init__.py
        └── handlers.py
```

#### Running the Generated Python Application

1. **Install Dependencies:**
   ```bash
   cd generated-python-app
   pip install -r requirements.txt
   ```

2. **Run the Application:**
   ```bash
   uvicorn main:app --reload
   ```

3. **Access the API:**
   - API: http://localhost:8000
   - Swagger UI: http://localhost:8000/docs
   - ReDoc: http://localhost:8000/redoc

#### Python FastAPI Features

- **Automatic OpenAPI Integration**: FastAPI automatically generates OpenAPI documentation from your code
- **Pydantic Models**: Type-safe models with automatic validation
  - Support for all OpenAPI data types
  - Field validation and constraints
  - Optional and required fields
  - Nested models and arrays
- **Async Support**: All route handlers are async by default for high performance
- **CORS Middleware**: Pre-configured CORS support with configurable origins
- **Exception Handling**: Global exception handlers for error management
  - Request validation errors
  - Global exception handler
  - Proper HTTP status codes
- **Schema Composition**: Full support for `allOf`, `oneOf`, and `anyOf` schema composition
  - Properties merged from all schemas in composition
  - Recursive schema resolution
  - Reference resolution (`$ref`)
- **Path Grouping**: Routes are intelligently grouped by parent path for better organization
  - One router per parent path (e.g., `/api/users` and `/api/users/{id}` share a router)
  - Clean separation of concerns
  - Easy to maintain and extend
- **Smart Model Generation**: Only generates models that are actually referenced by operations
  - Reduces unnecessary code
  - Faster generation times
  - Cleaner codebase
- **Configuration Management**: Pydantic Settings for environment-based configuration
  - Environment variable support
  - Type-safe configuration
  - `.env` file support

#### Filtering for Python FastAPI

You can use the same filtering API for Python generation to generate only specific APIs:

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
            sdk.generateApplication("python", "fastapi", "api", "./generated-python-app");
            
            // Only models referenced by filtered operations will be generated
            System.out.println("Python FastAPI application generated with filtering!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

**Benefits of Filtering:**
- **Reduced Code**: Only generates routers and models for filtered APIs
- **Faster Generation**: Less code to generate and process
- **Cleaner Codebase**: Only includes what you need
- **Microservices**: Generate separate services for different API groups

#### Advanced Python FastAPI Configuration

You can also use configuration objects for more control:

```java
import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;

public class PythonConfiguredExample {
    public static void main(String[] args) {
        try {
            // Create generator configuration
            GeneratorConfig generatorConfig = new GeneratorConfig();
            generatorConfig.setLanguage("python");
            generatorConfig.setFramework("fastapi");
            generatorConfig.setPackageName("api");
            
            // Initialize SDK with configuration
            OASSDK sdk = new OASSDK(generatorConfig, null, null);
            
            // Load and generate
            sdk.loadSpec("openapi.yaml");
            sdk.generateApplication("python", "fastapi", "api", "./generated-python-app");
            
            System.out.println("Python FastAPI application generated with configuration!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

#### Generated Code Examples

**Router Example:**
```python
from fastapi import APIRouter, Query, Path
from api.models import User, CreateUserRequest
from api.services import api_service

users_router = APIRouter(prefix="/api/users")

@users_router.get("")
async def get_users():
    """Get all users"""
    # Implementation placeholder
    return {"message": "Not implemented"}

@users_router.post("")
async def create_user(user: CreateUserRequest):
    """Create a new user"""
    # Implementation placeholder
    return {"message": "Not implemented"}
```

**Model Example:**
```python
from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

class User(BaseModel):
    id: int
    name: str
    email: str
    created_at: Optional[datetime] = None
    
    class Config:
        populate_by_name = True
        from_attributes = True
```

---

### Python Flask Generation

Generate a complete Flask REST API application from your OpenAPI specification.

```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;

public class PythonFlaskExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Generate Python Flask application
            sdk.generateApplication("python", "flask", "api", "./generated-flask-app");
            
            System.out.println("Python Flask application generated!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

Flask applications use blueprints for route organization, dataclasses for models, and include production-ready WSGI configuration with Gunicorn support. Generated applications follow the application factory pattern for better testing and configuration.

---

### Node.js Express Generation

Generate a complete Node.js Express REST API application from your OpenAPI specification with full feature parity.

```java
import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;

public class NodeJSExpressExample {
    public static void main(String[] args) {
        try {
            OASSDK sdk = new OASSDK();
            sdk.loadSpec("openapi.yaml");
            
            // Generate Node.js Express application
            sdk.generateApplication("nodejs", "express", "api", "./generated-express-app");
            
            System.out.println("Node.js Express application generated!");
            
        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

#### Generated Node.js Express Structure

```
generated-express-app/
├── app.js                     # Main Express application
├── package.json               # NPM dependencies
├── .env.example              # Environment variables template
├── README.md                 # Setup and usage instructions
└── api/                      # Main package
    ├── routes/               # Route handlers
    │   └── *.js             # Route files grouped by parent path
    ├── models/               # Data models
    │   └── *.js             # Model classes
    ├── middleware/           # Middleware functions
    │   ├── auth.js          # JWT authentication
    │   └── validators.js    # Request validation
    └── services/            # Business logic
        └── api_service.js
```

#### Running the Generated Node.js Application

1. **Install Dependencies:**
   ```bash
   cd generated-express-app/api
   npm install
   ```

2. **Configure Environment:**
   ```bash
   cp .env.example .env
   # Edit .env with your settings
   ```

3. **Run the Application:**
   ```bash
   npm start              # Production
   npm run dev           # Development with nodemon
   ```

4. **Access the API:**
   - API: http://localhost:3000
   - Health Check: http://localhost:3000/health

#### Node.js Express Features

- **✅ Full Feature Parity**: All features from Java and Python implementations
  - Inline schema collection and model generation
  - API version extraction and route prefixes
  - Query parameter validation with express-validator
  - JWT authentication middleware
  - Scope-based authorization
  - Security middleware integration

- **Express.js Best Practices**:
  - Modular route organization
  - Express-validator for comprehensive validation
  - JWT token authentication
  - Async/await error handling
  - CORS middleware pre-configured
  - Production-ready structure

- **Validation Features**:
  - Required/optional parameters
  - Type validation (string, integer, number)
  - Length constraints (min/max)
  - Range constraints (min/max for numbers)
  - Pattern matching (regex)
  - Custom validation messages

- **Security Features**:
  - JWT token verification
  - Scope-based access control
  - Authorization header parsing
  - Role-based permissions
  - Automatic security middleware application

- **Dependencies**:
  - `express`: Web framework
  - `express-validator`: Request validation
  - `jsonwebtoken`: JWT authentication
  - `cors`: CORS support
  - `dotenv`: Environment configuration

#### Generated Code Examples

**Route Example:**
```javascript
const express = require('express');
const router = express.Router();
const { authenticate, checkScopes } = require('../middleware/auth');
const { validate, validators } = require('../middleware/validators');

/**
 * Get all users
 */
router.get('/', 
  authenticate, 
  checkScopes(['users.read']),
  validate(validators.getUsers), 
  async (req, res) => {
  try {
    // Implementation placeholder
    res.status(501).json({ message: 'Not implemented' });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
```

**Validation Example:**
```javascript
const { query, param, validationResult } = require('express-validator');

const validators = {
  getUsers: [
    query('name')
      .notEmpty().withMessage('name is required')
      .isLength({ min: 1, max: 100 }),
    query('age')
      .isInt({ min: 18, max: 120 })
  ]
};

const validate = (validations) => {
  return async (req, res, next) => {
    await Promise.all(validations.map(validation => validation.run(req)));
    
    const errors = validationResult(req);
    if (errors.isEmpty()) {
      return next();
    }
    
    res.status(400).json({ errors: errors.array() });
  };
};
```

**Authentication Middleware Example:**
```javascript
const jwt = require('jsonwebtoken');

const authenticate = (req, res, next) => {
  const authHeader = req.headers.authorization;
  
  if (!authHeader) {
    return res.status(401).json({ error: 'No authorization header' });
  }
  
  const token = authHeader.replace('Bearer ', '');
  
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded;
    next();
  } catch (error) {
    return res.status(401).json({ error: 'Invalid token' });
  }
};

const checkScopes = (requiredScopes) => {
  return (req, res, next) => {
    const userScopes = req.user?.scopes || [];
    
    const hasAllScopes = requiredScopes.every(scope => 
      userScopes.includes(scope)
    );
    
    if (!hasAllScopes) {
      return res.status(403).json({ error: 'Insufficient permissions' });
    }
    
    next();
  };
};
```

---

### 9. Command-Line Usage

```bash
# Generate all artifacts
java -jar oas-sdk.jar all --spec openapi.yaml --output ./generated

# Generate only application code (Java Jersey)
java -jar oas-sdk.jar generate --spec openapi.yaml --language java --framework jersey --output ./generated-app

# Generate only application code (Python FastAPI)
java -jar oas-sdk.jar generate --spec openapi.yaml --language python --framework fastapi --output ./generated-app

# Generate only application code (Python Flask)
java -jar oas-sdk.jar generate --spec openapi.yaml --language python --framework flask --output ./generated-app

# Generate only application code (Node.js Express)
java -jar oas-sdk.jar generate --spec openapi.yaml --language nodejs --framework express --output ./generated-app

# Generate only documentation
java -jar oas-sdk.jar docs --spec openapi.yaml --output ./generated-docs

# Validate OpenAPI specification
java -jar oas-sdk.jar validate --spec openapi.yaml

# Generate tests (automatically matches application language)
java -jar oas-sdk.jar tests --spec openapi.yaml --types unit,integration,postman --output ./generated-tests

# Generate tests with explicit language/framework
java -jar oas-sdk.jar tests --spec openapi.yaml --types unit,integration --language python --framework pytest --output ./generated-tests
java -jar oas-sdk.jar tests --spec openapi.yaml --types unit,integration --language nodejs --framework jest --output ./generated-tests
```

## 📚 Documentation Generation

The SDK now generates professional documentation using industry-standard libraries:

### Redocly Documentation

Generates interactive, beautiful documentation using Redocly CLI:

```java
// Automatically generates:
// - redocly.yaml (configuration)
// - redocly.json (alternate config)
// - package.json (Node.js dependencies)
// - custom.css (theming)
// - build-docs.sh/.bat (build scripts)
// - serve-docs.sh/.bat (development server)
// - index.html (landing page)
// - README.md (documentation guide)
```

**Features:**
- Customizable themes and colors
- Code sample generation
- Request/response examples
- Interactive API explorer
- Cross-platform build scripts

### Swagger UI Documentation

Professional Swagger UI with library integration:

```java
// Generates:
// - swagger-ui.html (interactive UI)
// - swagger-ui-config.json (configuration)
// - package.json (dependencies)
// - build-swagger-ui.sh/.bat (build scripts)
```

**Features:**
- Configurable Swagger UI options
- Custom CSS support
- Deep linking
- Try-it-out functionality
- Request/response interceptors

### Markdown Documentation

Rich markdown with Flexmark library:

```java
// Generates:
// - API_DOCUMENTATION.md (main API docs)
// - API_DOCUMENTATION.html (HTML version)
// - TEST_DOCUMENTATION.md (test docs)
// - PROJECT_DOCUMENTATION.md (project docs)
```

**Features:**
- Table of Contents generation
- Code syntax highlighting
- Table support
- Emoji support
- Front matter (YAML)
- HTML conversion

### OpenAPI Specification Enhancement

Enhanced OpenAPI specifications with examples:

```java
// Generates:
// - openapi.yaml (enhanced YAML)
// - openapi.json (JSON format)
// - openapi-bundled.yaml (bundled version)
// - openapi-dereferenced.yaml (dereferenced version)
// - validation-report.md (validation report)
```

**Features:**
- Automatic example generation
- Security schemes injection
- Server information
- Tags and external documentation
- Validation reports

## 🎯 API Filtering

The SDK supports filtering which APIs and operations to generate code for. This is particularly useful when working with large OpenAPI specifications where you only need specific endpoints.

### Filter Methods

- **`filterPaths(List<String> paths)`**: Filter by specific API paths. Includes all HTTP methods for those paths.
- **`filterOperations(Map<String, List<String>> pathOperationMap)`**: Filter by specific HTTP methods for specific paths.
- **`clearFilters()`**: Clear all filters to generate for all APIs.

### When to Use Filtering

- **Large Specifications**: When your OpenAPI spec contains many APIs but you only need a subset
- **Incremental Development**: Generate code for specific APIs as you develop them
- **Microservices**: Generate separate services for different API groups
- **Testing**: Generate tests only for APIs you're actively testing

### Filtering Examples

See the [Filtering APIs and Operations](#3-filtering-apis-and-operations) section in the Quick Start guide for detailed examples.

## 🔍 Additional SDK Methods

### Validation and Metadata

```java
// Validate the loaded specification
boolean isValid = sdk.validateSpec();

// Get extracted metadata from specification
Map<String, Object> metadata = sdk.getMetadata();
```

### SLA and Monitoring

```java
// Load SLA specification
sdk.loadSLA("sla.yaml");

// Generate SLA enforcement
sdk.generateSLAEnforcement("sla.yaml", "./generated-sla");

// Generate monitoring setup
sdk.generateMonitoring(List.of("prometheus", "grafana"), "./generated-monitoring");
```

### Documentation Options

```java
// Generate all documentation types (default)
sdk.generateDocumentation("./generated-docs");

// Generate specific documentation types
sdk.generateDocumentation("./generated-docs", true, false, true); // API docs and project docs only
```

### Complete Project Generation

```java
// Generate complete project with all artifacts
sdk.generateAll("./complete-project");
```

## 📝 Logging Configuration

The SDK includes comprehensive logging using `java.util.logging` with automatic file rotation and configurable settings.

### Logging Features

- **File-based logging** with automatic rotation when files reach configured size
- **Configurable log directory**, file size, and rotation settings
- **Error level logging** for all exception cases
- **Console and file logging** support
- **Customizable log format** and levels

### Configuration Methods

Logging can be configured via multiple methods (priority order):

1. **System Properties** (highest priority)
2. **Environment Variables**
3. **`logger.properties` file** in classpath
4. **Default values** (lowest priority)

### Configuration File

Create a `logger.properties` file in `src/main/resources/`:

```properties
# Log directory (relative to current working directory or absolute path)
log.dir=logs

# Log file name
log.file=oas-sdk.log

# Maximum file size before rotation (supports KB, MB, GB suffixes or bytes)
# Examples: 1MB, 1024KB, 1048576
log.size=1MB

# Maximum number of backup files to keep
log.backups=10

# Log level (SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST)
log.level=INFO

# Enable console logging (true/false)
log.console=true
```

### System Properties

```bash
# Set log directory
java -Doas.sdk.log.dir=custom-logs ...

# Set log file size
java -Doas.sdk.log.size=2MB ...

# Set log level
java -Doas.sdk.log.level=WARNING ...

# Set maximum backup files
java -Doas.sdk.log.backups=5 ...

# Disable console logging
java -Doas.sdk.log.console=false ...
```

### Environment Variables

```bash
export OAS_SDK_LOG_DIR=custom-logs
export OAS_SDK_LOG_FILE=my-app.log
export OAS_SDK_LOG_SIZE=2MB
export OAS_SDK_LOG_BACKUPS=5
export OAS_SDK_LOG_LEVEL=WARNING
export OAS_SDK_LOG_CONSOLE=false
```

### Log File Rotation

Log files automatically rotate when they reach the configured size:
- Current log: `oas-sdk.log`
- Rotated logs: `oas-sdk.log.1`, `oas-sdk.log.2`, etc.
- Oldest files are deleted when maximum backup count is reached

### Log Format

Logs include:
- Timestamp (YYYY-MM-DD HH:mm:ss)
- Log level
- Source class name
- Message
- Stack traces for exceptions

Example log entry:
```
2025-01-15 14:30:45 [SEVERE] OASSDK: Failed to generate application: File not found
java.io.FileNotFoundException: openapi.yaml not found
    at egain.oassdk.OASSDK.loadSpec(OASSDK.java:236)
    ...
```

### Using Loggers in Your Code

The SDK automatically initializes logging when `OASSDK` is instantiated. All exception cases are logged at ERROR (SEVERE) level with full stack traces.

```java
import egain.oassdk.core.logging.LoggerConfig;
import java.util.logging.Logger;

public class MyClass {
    private static final Logger logger = LoggerConfig.getLogger(MyClass.class);
    
    public void myMethod() {
        try {
            // Your code
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Error occurred: " + e.getMessage(), e);
            throw e;
        }
    }
}
```

## ⚙️ Configuration

### Generator Configuration

```java
import egain.oassdk.config.GeneratorConfig;

GeneratorConfig config = GeneratorConfig.builder()
    .language("java")
    .framework("jersey")
    .packageName("com.example.api")
    .build();
```

### Test Configuration

Configure test generation with language-specific settings:

```java
import egain.oassdk.config.TestConfig;

// For Java (default)
TestConfig javaTestConfig = TestConfig.builder()
    .language("java")               // Target language: java, python, nodejs
    .framework("junit5")            // Test framework: junit5, pytest, jest
    .unitTests(true)
    .integrationTests(true)
    .performanceTests(true)
    .securityTests(true)
    .nfrTests(true)
    .build();

// For Python with pytest
TestConfig pythonTestConfig = TestConfig.builder()
    .language("python")
    .framework("pytest")
    .unitTests(true)
    .integrationTests(true)
    .build();

// For Node.js with Jest
TestConfig nodejsTestConfig = TestConfig.builder()
    .language("nodejs")
    .framework("jest")
    .unitTests(true)
    .integrationTests(true)
    .build();
```

**Automatic Language Detection:**
When `GeneratorConfig` is provided, tests are automatically generated in the same language as your application:

```java
GeneratorConfig genConfig = GeneratorConfig.builder()
    .language("python")
    .framework("fastapi")
    .build();

// TestConfig will automatically use Python/pytest if not explicitly set
OASSDK sdk = new OASSDK(genConfig, testConfig, null);
```

### SLA Configuration

```java
import egain.oassdk.config.SLAConfig;

SLAConfig slaConfig = SLAConfig.builder()
    .slaFile("sla.yaml")
    .monitoringStack(List.of("prometheus", "grafana"))
    .build();
```

## 🏗️ Building from Source

### Prerequisites
- Java 21 JDK
- Maven 3.6+

### Build Steps

```bash
# Clone the repository
git clone <repository-url>
cd oas-sdk-java

# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package the SDK
mvn package

# Install to local Maven repository
mvn clean install

# Skip checkstyle (if checkstyle.xml is missing)
mvn clean compile -Dcheckstyle.skip=true
```

### Build Output

All class files are generated in the `target/classes/` directory following Maven conventions:
- Source code: `src/main/java/`
- Compiled classes: `target/classes/`
- Resources: `src/main/resources/` → `target/classes/`
- Templates: `src/main/resources/templates/` → `target/classes/templates/`

## 📦 Generated Artifacts

### Application Code

**Java Jersey/JAX-RS:**
- Resources with JAX-RS endpoints (@Path, @GET, @POST, etc.)
  - **@Actor Annotations**: Automatic generation with `ActorType` and `OAuthScope` from OpenAPI security tags
  - **API Version Support**: Automatic extraction of API version from server URLs and inclusion in `@Path` annotations
  - **Media Type Priority**: XML prioritized over JSON in `@Consumes` and `@Produces` annotations
  - **Clean Code**: No unnecessary `@Inject` annotations; header parameters excluded from method signatures
- Service layer with business logic
- Models/DTOs with Bean Validation (`javax.validation.constraints`)
  - **In-lined Schema Support**: Models generated for in-lined schemas in response bodies
  - **Smart Schema Filtering**: Intermediate schemas filtered out to reduce unnecessary code
  - **Improved Type Resolution**: Better handling of `$ref` references in arrays and compositions
- Configuration classes with @Singleton
- Exception mappers with @Provider
- Grizzly HTTP server integration
- HK2 dependency injection

**Python FastAPI:**
- FastAPI application with automatic OpenAPI integration
- Route handlers (routers) grouped by parent path
- Pydantic models with validation
- Support for allOf, oneOf, anyOf schema composition
- Exception handlers
- CORS middleware configuration
- Service layer structure
- Configuration management with Pydantic Settings
- Requirements.txt with dependencies
- README.md with setup instructions

**Python Flask:**
- Flask application with factory pattern
- Blueprint route handlers grouped by parent path
- Dataclass models for data validation
- Support for allOf, oneOf, anyOf schema composition
- Comprehensive error handlers
- CORS support with Flask-CORS
- Service layer structure
- Environment-based configuration
- WSGI production configuration with Gunicorn
- Requirements.txt with dependencies
- README.md with setup instructions

**Node.js Express:**
- Express.js application with route handlers
- JWT authentication middleware
- Query parameter validation with express-validator
- Model classes with validation
- CORS and error handling middleware
- RESTful API structure
- Package.json with dependencies
- README.md with setup instructions
- API version support in route prefixes
- Security middleware with scope checking

**Go Gin:**
- Gin web framework application (stub implementation)
- Route handlers
- Middleware configuration
- Error handling

**C# ASP.NET Core:**
- ASP.NET Core Web API (stub implementation)
- Controllers
- Models
- Dependency injection

### Test Suites

Tests are generated in the same language as your application code for seamless integration:

#### Java Tests
- **Unit Tests**: JUnit 5 tests with Mockito for individual components
- **Integration Tests**: JAX-RS Client tests making real HTTP calls to a running server
- **Configuration**: `junit5` framework with Maven Surefire/Failsafe

#### Python Tests
- **Unit Tests**: pytest tests with unittest.mock for individual components
  - Test discovery: `test_*_unit.py` files
  - Fixtures in `conftest.py`
  - Dependencies: `pytest>=7.0.0`, `pytest-mock>=3.10.0`
- **Integration Tests**: pytest with `requests` library for real HTTP calls
  - Test discovery: `test_*_integration.py` files
  - Server health checks in test setup
  - Dependencies: `pytest>=7.0.0`, `requests>=2.28.0`
- **Configuration**: `pytest.ini` with test markers and options

#### Node.js Tests
- **Unit Tests**: Jest tests for individual components
  - Test discovery: `*.unit.test.js` files
  - Mock functions with Jest
  - Dependencies: `jest>=29.0.0`
- **Integration Tests**: Jest with `axios` for real HTTP calls
  - Test discovery: `*.integration.test.js` files
  - Server health checks in test setup
  - Dependencies: `jest>=29.0.0`, `axios>=1.4.0`
- **Configuration**: `jest.config.js` with environment and coverage settings

#### Cross-Language Tests
- **NFR Tests**: Non-functional requirements tests (Java-based)
- **Performance Tests**: Load and stress tests with JMeter/Gatling (Java-based)
- **Security Tests**: Security vulnerability tests with OWASP ZAP (Java-based)
- **Postman Collection**: Complete API testing collection with automated scripts (language-agnostic)
- **Randomized Sequence Tests**: Property-based testing with random API call sequences (Java-based)

#### Generated Test Features
All language-specific tests include:
- ✅ Success scenarios with valid inputs
- ✅ Missing required parameter validation
- ✅ Unauthorized access testing (if security is enabled)
- ✅ Helper functions for URL building and parameter replacement
- ✅ Authentication token management
- ✅ Configuration files (`pytest.ini`, `jest.config.js`, etc.)
- ✅ Dependency management (`requirements.txt`, `package.json`)

### Documentation

- **Redocly**: Interactive, beautiful documentation with CLI
- **Swagger UI**: Professional interactive API documentation
- **Markdown**: Comprehensive markdown documentation with HTML conversion
- **OpenAPI Specs**: Enhanced specifications with examples
- **Project Docs**: Complete project documentation with templates

## 🔧 Dependencies

### Core Dependencies
- **Jackson**: JSON/YAML processing
- **Swagger Parser**: OpenAPI specification parsing
- **Jersey**: JAX-RS implementation (RESTful web services)
- **Jakarta EE**: Jakarta WS-RS API, Jakarta Servlet API
- **Jakarta Validation**: Validation API (`javax.validation.constraints` for compatibility)
- **Grizzly HTTP Server**: Embedded HTTP server
- **HK2**: Dependency injection framework
- **Picocli**: Command-line interface

### Documentation Dependencies
- **Flexmark**: Advanced Markdown processing
- **FreeMarker**: Template engine for documentation
- **Redocly CLI**: Professional API documentation (via npm)
- **Swagger UI**: Interactive API documentation

### Logging Dependencies
- **java.util.logging**: Built-in Java logging framework (no external dependencies required)

### Testing Dependencies

#### Java
- **JUnit 5**: Unit testing framework (`junit-jupiter`)
- **JAX-RS Client**: Integration testing with HTTP calls
- **Mockito**: Mocking framework for unit tests

#### Python
- **pytest**: Testing framework with fixture support
- **pytest-cov**: Code coverage reporting
- **pytest-mock**: Enhanced mocking capabilities
- **requests**: HTTP library for integration tests
- **python-dotenv**: Environment variable management

#### Node.js
- **Jest**: JavaScript testing framework
- **axios**: HTTP client for integration tests
- **supertest**: Alternative HTTP assertion library (optional)

## 📖 Examples

See the `examples/` directory for complete usage examples:

- `HelloWorldExample.java` - Basic SDK usage
- `CompleteExample.java` - Advanced configuration and customization

### Test Resource Files

The SDK includes simplified OpenAPI specification files in `src/test/resources/` for testing and demonstration:

- `openapi.yaml` - Comprehensive example with multiple endpoints (Users, Products, Orders)
- `openapi1.yaml` - Simple items API with enum validation
- `openapi2.yaml` - Import job management API
- `openapi3.yaml` - Scheduling API with cron expressions
- `openapi4.yaml` - Content validation API
- `openapi5.yaml` - Portal management API
- `api_v3.yaml` - Article management with OAuth2 security
- `template.yaml` - Template management API
- `sla.yaml` - SLA configuration example

All test files are simplified (60-320 lines each) for easy understanding while maintaining OpenAPI 3.0 compliance.

## 🧪 Testing

### Running SDK Tests

```bash
# Run all SDK tests
mvn test

# Run specific test type
mvn test -Dtest=*UnitTest
mvn test -Dtest=*IntegrationTest
mvn test -Dtest=*NFRTest

# Run with coverage
mvn test jacoco:report
```

### Running Generated Tests

The SDK generates tests in the same language as your application. Here's how to run them:

#### Java (JUnit 5)
```bash
cd generated-tests
mvn test

# Run only unit tests
mvn test -Dtest=*UnitTest

# Run only integration tests
mvn test -Dtest=*IntegrationTest
```

#### Python (pytest)
```bash
cd generated-tests

# Install dependencies
pip install -r requirements.txt

# Run all tests
pytest

# Run only unit tests
pytest -m unit

# Run only integration tests
pytest -m integration

# Run with coverage
pytest --cov=api --cov-report=html
```

#### Node.js (Jest)
```bash
cd generated-tests

# Install dependencies
npm install

# Run all tests
npm test

# Run only unit tests
npm run test:unit

# Run only integration tests
npm run test:integration

# Run with coverage
npm run test:coverage
```

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Make your changes** and add tests
4. **Run tests**: `mvn test`
5. **Commit your changes**: `git commit -m 'Add amazing feature'`
6. **Push to the branch**: `git push origin feature/amazing-feature`
7. **Open a Pull Request**

### Code Style

- Follow Java coding conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Write unit tests for new features
- Ensure all tests pass before submitting

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:

- 📧 **Email**: Create an issue in the repository
- 📖 **Documentation**: Check the generated documentation
- 💬 **Issues**: Report bugs and request features via GitHub Issues
- 🔍 **Examples**: Review the examples in the `examples/` directory

## 🗺️ Roadmap

### Completed ✅
- [x] Java Jersey/JAX-RS code generation (fully implemented)
  - [x] @Actor annotation with ActorType and OAuthScope from security tags
  - [x] API version extraction and inclusion in @Path annotations
  - [x] In-lined schema model generation
  - [x] Smart filtering of intermediate composition schemas
  - [x] Improved array type resolution for $ref references
  - [x] Media type priority (XML over JSON)
  - [x] Clean resource classes (no unnecessary @Inject)
  - [x] Header parameter exclusion from method signatures
- [x] Python FastAPI code generation (fully implemented)
  - [x] Inline schema collection with Pydantic models
  - [x] API version extraction and router prefixes
  - [x] Query parameter validation with Pydantic validators (min/max length, regex, numeric ranges)
  - [x] Security decorators with dependency injection
  - [x] Scope extraction and checking from OpenAPI security definitions
- [x] Python Flask code generation (fully implemented)
  - [x] Inline schema collection with dataclasses
  - [x] API version extraction and blueprint prefixes
  - [x] Query parameter validation with explicit checks
  - [x] Security decorators with middleware pattern
  - [x] Scope extraction and checking from OpenAPI security definitions
- [x] Node.js Express code generation (fully implemented)
  - [x] Inline schema collection
  - [x] API version extraction and route prefixes
  - [x] Query parameter validation with express-validator
  - [x] JWT authentication middleware
  - [x] Scope-based authorization
- [x] Postman collection generation
- [x] Mock data generation
- [x] Redocly documentation with CLI integration
- [x] Swagger UI with library integration
- [x] Markdown documentation with Flexmark
- [x] Template-based documentation generation
- [x] Enhanced OpenAPI specification generation
- [x] Randomized sequence testing
- [x] Java 21 support
- [x] Proper Maven build structure
- [x] API path and operation filtering
- [x] Validation constraints package correction (javax.validation.constraints)
- [x] Multi-language test generation (Java JUnit 5, Python pytest, Node.js Jest)
  - [x] Language-aware test generator factory
  - [x] Automatic language detection from GeneratorConfig
  - [x] Python pytest unit and integration tests
  - [x] Node.js Jest unit and integration tests
  - [x] Test configuration files (pytest.ini, jest.config.js)
  - [x] Test dependency management (requirements.txt, package.json)
- [x] Comprehensive logging system
  - [x] java.util.logging integration with file rotation
  - [x] Configurable logging via properties file, system properties, or environment variables
  - [x] Error level logging for all exception cases
  - [x] Automatic log file rotation (configurable size, default: 1MB)
  - [x] Console and file logging support

### In Progress 🚧
- [ ] Go Gin full implementation
- [ ] C# ASP.NET Core full implementation
- [ ] Advanced mock data generation
- [ ] GraphQL API support

### Planned 📋
- [ ] Go Gin complete feature parity
- [ ] C# ASP.NET Core complete feature parity
- [ ] API versioning support
- [ ] CI/CD pipeline generation
- [ ] Advanced SLA monitoring
- [ ] WebSocket support
- [ ] GraphQL schema generation

## 🙏 Acknowledgments

- **Jersey**: JAX-RS implementation for RESTful web services
- **Jakarta EE**: Enterprise Java standards
- **Grizzly**: High-performance HTTP server
- **OpenAPI Generator**: Code generation inspiration
- **Redocly**: Professional documentation tooling
- **Swagger UI**: Interactive API documentation
- **Flexmark**: Advanced Markdown processing
- **FreeMarker**: Template engine
- **Maven**: Build management

## 📊 Version History

### 1.16-SNAPSHOT (Current)
- ✅ **Simplified Test Resources**
  - **File Simplification**: Reduced test YAML files from ~52,000 lines to ~1,100 lines (98% reduction)
  - **Consistent Naming**: Renamed test files to follow openapi[N].yaml convention for clarity
    - `openapi_18Nov.yaml` → `openapi3.yaml`
    - `openapi_12.yaml` → `openapi4.yaml`
    - `portalmgrapi.yaml` → `openapi5.yaml`
  - **Easy to Understand**: Each file now contains 2-4 paths with clear, minimal examples
  - **Maintained Compliance**: All files remain valid OpenAPI 3.0 specifications
  - **Updated Test References**: All test classes updated to use new file names
- ✅ **Standardized Dummy Data**
  - **Uniform URLs**: All server URLs use example.com domain
  - **Consistent Emails**: All email addresses use example.com domain (support@example.com, user@example.com)
  - **Test License**: All files include "Test License is required" in license section
- ✅ **Multi-Language Test Generation**
  - **Language-Aware Test Generators**: Tests are now generated in the same language as your application code
  - **Java Tests**: JUnit 5 for unit and integration tests with JAX-RS Client
  - **Python Tests**: pytest for unit and integration tests with fixtures, mocks, and requests library
  - **Node.js Tests**: Jest for unit and integration tests with axios for HTTP calls
  - **Automatic Language Detection**: TestConfig automatically inherits language/framework from GeneratorConfig
  - **Explicit Test Configuration**: Override test language/framework via TestConfig.builder()
  - **Configuration Files**: Language-specific test configurations (pytest.ini, jest.config.js)
  - **Dependency Management**: Automatic generation of requirements.txt (Python) and package.json (Node.js) for tests
  - **Test Utilities**: Shared fixtures (conftest.py), setup files (setup.js), and helper functions for each language
- ✅ **Enhanced Code Quality**
  - **Character Encoding**: All file operations now explicitly use UTF-8 encoding for platform independence
  - **Locale-Safe String Operations**: String case conversions use Locale.ROOT to prevent locale-specific bugs (e.g., Turkish 'i')
  - **SpotBugs Compliance**: Resolved all high-priority SpotBugs warnings for production-ready code
  - **Type Safety**: Addressed unchecked cast warnings with proper annotations using Util helper methods
  - **Defensive Copying**: Implemented defensive copying for mutable collections and arrays to prevent internal representation exposure
  - **Error Handling**: Comprehensive error logging at SEVERE level for all exception cases
- ✅ **Comprehensive Logging System**
  - **java.util.logging Integration**: Full logging support using Java's built-in logging framework
  - **Automatic File Rotation**: Log files automatically rotate when they reach configured size (default: 1MB)
  - **Configurable Settings**: Log directory, file size, backup count, and log level via properties file, system properties, or environment variables
  - **Error Level Logging**: All exception cases are logged at ERROR (SEVERE) level with full stack traces
  - **Configuration File**: `logger.properties` in classpath for easy configuration
  - **Console and File Logging**: Optional console output with file-based logging
  - **Custom Log Format**: Readable log format with timestamps, levels, and source information
- ✅ **Complete Feature Parity Across All Generators**
  - **Java Jersey/JAX-RS**: 100% complete with all features
  - **Python FastAPI**: 100% feature parity with Java
    - Inline schema collection with Pydantic models
    - API version extraction and router prefixes
    - Query parameter validation with Pydantic validators
    - Security decorators with dependency injection
    - Scope extraction and checking
  - **Python Flask**: 100% feature parity with Java
    - Inline schema collection with dataclasses
    - API version extraction and blueprint prefixes
    - Query parameter validation with explicit checks
    - Security decorators with middleware pattern
    - Scope extraction and checking
  - **Node.js Express**: 100% feature parity with Java
    - Inline schema collection
    - API version extraction and route prefixes
    - Query parameter validation with express-validator
    - JWT authentication middleware
    - Scope-based authorization
- ✅ **Enhanced Jersey/JAX-RS Resource Generation**
  - **@Actor Annotation**: Automatic generation of `@Actor` annotations with `ActorType` and `OAuthScope` enums extracted from OpenAPI security tags
  - **API Version Support**: Automatic extraction and inclusion of API version in `@Path` annotations from server URLs
  - **Media Type Priority**: XML content type prioritized over JSON in `@Consumes` and `@Produces` annotations
  - **Clean Resource Classes**: Removed unnecessary `@Inject` annotations and service field injection
  - **Header Parameter Exclusion**: Header parameters excluded from method signatures (handled by framework)
- ✅ **Improved Model Generation**
  - **In-lined Schema Support**: Models now generated for in-lined schemas in response bodies (not just `$ref` schemas)
  - **Smart Schema Filtering**: Intermediate schemas used only via `allOf`/`oneOf`/`anyOf` are filtered out to reduce unnecessary model generation
  - **Better Array Type Resolution**: Improved handling of `$ref` references in array items
  - **Response Model Naming**: Improved naming conventions for response models
- ✅ **Validation Improvements**
  - **Correct Validation Package**: Changed from `jakarta.validation.constraints` to `javax.validation.constraints` for compatibility
  - **Consistent Imports**: All model classes including `ObjectFactory` now have validation imports
- ✅ **Production-Ready Jersey/JAX-RS Implementation**
  - Pure Jersey/JAX-RS framework (no legacy dependencies)
  - JAX-RS annotations (@Path, @GET, @POST, etc.)
  - Grizzly HTTP server integration
  - HK2 dependency injection framework
  - JAX-RS filters and exception mappers
  - Code quality validated and optimized
- ✅ Java 21 support
- ✅ Java Jersey/JAX-RS code generation (fully implemented)
- ✅ Python FastAPI code generation (fully implemented)
- ✅ Professional documentation generation (Redocly, Swagger UI, Markdown)
- ✅ Template-based documentation
- ✅ Enhanced OpenAPI specification generation
- ✅ Proper Maven build structure
- ✅ Comprehensive test generation
- ✅ API path and operation filtering for selective code generation
- ✅ Comprehensive logging system with file rotation and configuration
- ✅ Error level logging for all exception cases

### 1.6.0
- ✅ **Production-Ready Jersey/JAX-RS Implementation**
  - Pure Jersey/JAX-RS framework (no legacy dependencies)
  - JAX-RS annotations (@Path, @GET, @POST, etc.)
  - Jakarta EE dependency injection (@Inject, @Singleton)
  - Grizzly HTTP server integration
  - HK2 dependency injection framework
  - JAX-RS filters and exception mappers
  - Code quality validated and optimized
- ✅ Java 21 support
- ✅ Java Jersey/JAX-RS code generation (fully implemented)
- ✅ Python FastAPI code generation (fully implemented)
- ✅ Professional documentation generation (Redocly, Swagger UI, Markdown)
- ✅ Template-based documentation
- ✅ Enhanced OpenAPI specification generation
- ✅ Proper Maven build structure
- ✅ Comprehensive test generation
- ✅ API path and operation filtering for selective code generation

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on:

- How to report bugs
- How to suggest enhancements
- How to submit pull requests
- Coding standards and guidelines
- Development setup instructions

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔒 Security

For security vulnerabilities, please see our [Security Policy](SECURITY.md).

## 💬 Support

- **Issues**: [GitHub Issues](https://github.com/egain/oas-sdk-java/issues)
- **Discussions**: [GitHub Discussions](https://github.com/egain/oas-sdk-java/discussions)
- **Documentation**: See [examples](examples/) for usage examples

## 🙏 Acknowledgments

- **Jersey**: JAX-RS implementation for RESTful web services
- **Jakarta EE**: Enterprise Java standards
- **Grizzly**: High-performance HTTP server
- **OpenAPI Generator**: Code generation inspiration
- **Redocly**: Professional documentation tooling
- **Swagger UI**: Interactive API documentation
- **Flexmark**: Advanced Markdown processing
- **FreeMarker**: Template engine
- **Maven**: Build management

---

**Built with ❤️ for the OpenAPI community**

For more information, visit our [documentation](docs/) or [examples](examples/).
