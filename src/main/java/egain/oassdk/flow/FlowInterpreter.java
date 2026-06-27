package egain.oassdk.flow;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class FlowInterpreter {

    public void execute(FlowAst.FlowDefinition flow, Runtime runtime) {
        Map<String, String> vars = new HashMap<>();
        Runtime.Response last = null;
        for (FlowAst.FlowStep step : flow.steps()) {
            if (step instanceof FlowAst.CallStep call) {
                Map<String, String> pathVars = new HashMap<>();
                for (FlowAst.PathBind bind : call.pathBinds()) {
                    pathVars.put(bind.parameterName(), vars.get(bind.variableName()));
                }
                Map<String, String> headers = new HashMap<>();
                for (FlowAst.HeaderBind bind : call.headerBinds()) {
                    headers.put(bind.headerName(), vars.get(bind.variableName()));
                }
                String body = null;
                if (call.poison() != null) {
                    body = runtime.bodyFactory().withViolation(call.operationId(), call.poison());
                } else if (runtime.bodyFactory().hasBody(call.operationId())) {
                    body = runtime.bodyFactory().valid(call.operationId());
                }
                last = runtime.client().call(call.operationId(), pathVars, headers, body);
                if (call.expect() != null) {
                    assertStatus(last.statusCode(), call.expect().status());
                }
            } else if (step instanceof FlowAst.ExtractStep extract) {
                ensureLast(last, "extract");
                String value;
                if (extract.source() == FlowAst.ExtractSource.HEADER) {
                    value = last.header(extract.key());
                    if (extract.lastSegment() && value != null) {
                        int slash = value.lastIndexOf('/');
                        value = slash >= 0 ? value.substring(slash + 1) : value;
                    }
                } else {
                    value = last.jsonPath(extract.key());
                }
                if (value == null) {
                    throw new IllegalStateException("extract failed for " + extract.variable());
                }
                vars.put(extract.variable(), value);
            } else if (step instanceof FlowAst.WaitStep waitStep) {
                ensureLast(last, "wait");
                assertStatus(last.statusCode(), waitStep.status());
            } else if (step instanceof FlowAst.PollStep pollStep) {
                ensureLast(last, "poll");
                String target = last.header(pollStep.headerName());
                Duration timeout = parseTimeout(pollStep.timeout(), runtime.defaultPollTimeout());
                last = runtime.client().poll(target, pollStep.untilStatus(), timeout);
                vars.put(pollStep.variable(), target);
            }
        }
    }

    private static Duration parseTimeout(String timeout, Duration defaultTimeout) {
        if (timeout == null || timeout.isBlank()) {
            return defaultTimeout;
        }
        String t = timeout.trim().toLowerCase();
        if (t.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(t.substring(0, t.length() - 1)));
        }
        return Duration.ofSeconds(Long.parseLong(t));
    }

    private static void ensureLast(Runtime.Response last, String action) {
        if (last == null) {
            throw new IllegalStateException(action + " requires previous response");
        }
    }

    private static void assertStatus(int actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return;
        }
        if ("2xx".equalsIgnoreCase(expected)) {
            if (actual < 200 || actual >= 300) {
                throw new IllegalStateException("Expected 2xx but got " + actual);
            }
            return;
        }
        int e = Integer.parseInt(expected);
        if (actual != e) {
            throw new IllegalStateException("Expected status " + e + " but got " + actual);
        }
    }

    public interface Runtime {
        Client client();
        BodyFactory bodyFactory();
        Duration defaultPollTimeout();

        interface Client {
            Response call(String operationId, Map<String, String> pathBinds, Map<String, String> headerBinds, String body);
            Response poll(String targetUrl, String expectedStatus, Duration timeout);
        }

        interface BodyFactory {
            boolean hasBody(String operationId);
            String valid(String operationId);
            String withViolation(String operationId, FlowAst.PoisonClause poison);
        }

        interface Response {
            int statusCode();
            String header(String name);
            String jsonPath(String jsonPath);
        }
    }
}
