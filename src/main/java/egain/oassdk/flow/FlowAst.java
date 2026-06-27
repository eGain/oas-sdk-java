package egain.oassdk.flow;

import java.util.List;

public final class FlowAst {
    private FlowAst() {
    }

    public record FlowDefinition(String name, List<String> operations, List<FlowStep> steps) {
    }

    public sealed interface FlowStep permits CallStep, ExtractStep, WaitStep, PollStep {
    }

    public record CallStep(
            String operationId,
            List<PathBind> pathBinds,
            List<HeaderBind> headerBinds,
            PoisonClause poison,
            ExpectClause expect
    ) implements FlowStep {
    }

    public record ExtractStep(
            String variable,
            ExtractSource source,
            String key,
            boolean lastSegment
    ) implements FlowStep {
    }

    public record WaitStep(String status) implements FlowStep {
    }

    public record PollStep(String variable, String headerName, String untilStatus, String timeout) implements FlowStep {
    }

    public record PathBind(String parameterName, String variableName) {
    }

    public record HeaderBind(String headerName, String variableName) {
    }

    public record PoisonClause(String bodyPath, PoisonKind kind, String valueLiteral) {
    }

    public record ExpectClause(String status) {
    }

    public enum ExtractSource {
        HEADER,
        BODY
    }

    public enum PoisonKind {
        NULL,
        MISSING,
        TOO_LONG,
        TOO_SHORT,
        WRONG_TYPE,
        BAD_ENUM,
        BAD_PATTERN,
        VALUE
    }
}
