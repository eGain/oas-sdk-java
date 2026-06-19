package egain.oassdk.core.exceptions;

/**
 * Base exception for OAS SDK.
 *
 * <p>High cross-module usage is intentional: generators, parsers, CLI, DevSDK, and generated
 * Jersey runtime resources under {@code src/main/resources/runtime/} share this hierarchy for
 * consistent error propagation.
 */
public class OASSDKException extends Exception {

    /**
     * Constructor with message
     *
     * @param message Exception message
     */
    public OASSDKException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause
     *
     * @param message Exception message
     * @param cause   Cause of the exception
     */
    public OASSDKException(String message, Throwable cause) {
        super(message, cause);
    }
}
