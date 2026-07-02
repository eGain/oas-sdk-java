package egain.oassdk.testgenerators.common;

/**
 * Shared Maven POM fragments for generated test modules.
 */
public final class TestMavenSupport {

    private TestMavenSupport() {
    }

    public static String pomHeader(String artifactId, String basePackage) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        <junit.version>5.12.2</junit.version>
                        <restassured.version>5.5.2</restassured.version>
                        <jacoco.version>0.8.13</jacoco.version>
                    </properties>
                """.formatted(basePackage, artifactId);
    }

    public static String junitDependency() {
        return """
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                            <scope>test</scope>
                        </dependency>
                """;
    }

    public static String restAssuredDependencies() {
        return """
                        <dependency>
                            <groupId>io.rest-assured</groupId>
                            <artifactId>rest-assured</artifactId>
                            <version>${restassured.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.rest-assured</groupId>
                            <artifactId>json-path</artifactId>
                            <version>${restassured.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.hamcrest</groupId>
                            <artifactId>hamcrest</artifactId>
                            <version>3.0</version>
                            <scope>test</scope>
                        </dependency>
                """;
    }

    public static String jsonSchemaValidatorDependency() {
        return """
                        <dependency>
                            <groupId>com.networknt</groupId>
                            <artifactId>json-schema-validator</artifactId>
                            <version>1.5.6</version>
                            <scope>test</scope>
                        </dependency>
                """;
    }

    public static String buildSectionWithTestSupport() {
        return """
                    <build>
                        <testSourceDirectory>src/test/java</testSourceDirectory>
                        <plugins>
                            <plugin>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>build-helper-maven-plugin</artifactId>
                                <version>3.6.0</version>
                                <executions>
                                    <execution>
                                        <id>add-test-support</id>
                                        <phase>generate-test-sources</phase>
                                        <goals><goal>add-test-source</goal></goals>
                                        <configuration>
                                            <sources>
                                                <source>../test-support/src/test/java</source>
                                            </sources>
                                        </configuration>
                                    </execution>
                                </executions>
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.5.3</version>
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.14.0</version>
                                <configuration>
                                    <release>21</release>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
    }
}
