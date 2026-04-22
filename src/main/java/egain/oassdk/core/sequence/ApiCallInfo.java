package egain.oassdk.core.sequence;

import java.util.List;
import java.util.Map;

/**
 * Extracted information about one API operation from an OpenAPI spec.
 *
 * <p>Produced by {@link ApiCallExtractor} and consumed by sequence-test
 * generators (chain enumeration, scenario emitters). The {@code operation}
 * component is the raw OpenAPI operation map — included so downstream code
 * can mine {@code requestBody} schemas without reparsing the spec. Treat
 * it as read-only even though the type cannot enforce that.
 */
public record ApiCallInfo(
        String method,
        String path,
        String operationId,
        String resourceName,
        boolean hasPathParams,
        boolean hasRequestBody,
        List<String> pathParamNames,
        Map<String, String> defaultQueryParams,
        Map<String, Object> operation) {

    public ApiCallInfo {
        pathParamNames = pathParamNames == null ? List.of() : List.copyOf(pathParamNames);
        defaultQueryParams = defaultQueryParams == null ? Map.of() : Map.copyOf(defaultQueryParams);
    }

    /** POST with no path parameters — a resource creator. */
    public boolean isCreator() {
        return "POST".equalsIgnoreCase(method) && !hasPathParams;
    }

    /** A path-templated op (GET/PUT/PATCH/DELETE by id, or POST on a sub-resource path). */
    public boolean isConsumer() {
        return hasPathParams;
    }
}
