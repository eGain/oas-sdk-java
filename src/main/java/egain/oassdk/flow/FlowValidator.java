package egain.oassdk.flow;

import java.util.HashSet;
import java.util.Set;

public final class FlowValidator {
    public void validate(FlowAst.FlowDefinition flow, OpenApiOperationCatalog catalog) {
        Set<String> definedVariables = new HashSet<>();
        boolean sawCall = false;
        for (FlowAst.FlowStep step : flow.steps()) {
            if (step instanceof FlowAst.CallStep call) {
                sawCall = true;
                if (!catalog.hasOperation(call.operationId())) {
                    throw new IllegalArgumentException("UNKNOWN_OPERATION: " + call.operationId());
                }
                OpenApiOperationCatalog.OperationMeta meta = catalog.operation(call.operationId());
                for (FlowAst.PathBind bind : call.pathBinds()) {
                    if (!meta.pathParameters().contains(bind.parameterName())) {
                        throw new IllegalArgumentException("MISSING_PATH_PARAM: " + bind.parameterName());
                    }
                }
                for (FlowAst.HeaderBind bind : call.headerBinds()) {
                    if (!definedVariables.contains(bind.variableName()) && !isEnvFallback(bind.variableName())) {
                        throw new IllegalArgumentException("UNBOUND_VARIABLE: " + bind.variableName());
                    }
                }
                if (call.poison() != null && !meta.hasBody()) {
                    throw new IllegalArgumentException("POISON_NO_BODY: " + call.operationId());
                }
                if (call.poison() != null && call.expect() == null) {
                    throw new IllegalArgumentException("POISON_WITHOUT_EXPECT: " + call.operationId());
                }
            } else if (step instanceof FlowAst.ExtractStep extract) {
                if (!sawCall) {
                    throw new IllegalArgumentException("UNBOUND_VARIABLE: extract before call");
                }
                definedVariables.add(extract.variable());
            } else if (step instanceof FlowAst.PollStep poll) {
                if (!sawCall) {
                    throw new IllegalArgumentException("UNBOUND_VARIABLE: poll before call");
                }
                definedVariables.add(poll.variable());
            }
        }
    }

    private static boolean isEnvFallback(String variableName) {
        return variableName != null && (variableName.endsWith("Id") || variableName.endsWith("ID"));
    }
}
