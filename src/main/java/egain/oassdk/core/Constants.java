package egain.oassdk.core;

import java.util.List;

/**
 * Shared constants used across the OAS SDK
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    /**
     * Standard HTTP methods supported by OpenAPI
     */
    public static final List<String> HTTP_METHODS = List.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace"
    );

    /**
     * Maximum recursion depth for schema resolution to prevent stack overflow
     * on circular references
     */
    public static final int MAX_SCHEMA_RECURSION_DEPTH = 50;

    /**
     * Default number of mock data instances to generate per schema
     */
    public static final int DEFAULT_MOCK_INSTANCES = 5;

    /**
     * Default maximum response time in milliseconds for sequence test validation
     */
    public static final long DEFAULT_MAX_RESPONSE_TIME_MS = 5000;
}
