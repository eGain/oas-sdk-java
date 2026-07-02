package egain.oassdk.flow;

import java.util.ArrayList;
import java.util.List;

public final class FlowParser {
    private final FlowLexer lexer = new FlowLexer();

    public FlowAst.FlowDefinition parse(String input) {
        List<FlowLexer.LexedLine> lines = lexer.lex(input);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Flow file is empty.");
        }
        int i = 0;
        FlowLexer.LexedLine header = lines.get(i++);
        if (!header.text().startsWith("flow ")) {
            throw syntax(header, "Expected flow declaration.");
        }
        String flowName = quotedSuffix(header.text(), "flow ");
        List<String> operations = new ArrayList<>();
        if (i < lines.size() && lines.get(i).text().startsWith("operations:")) {
            operations.addAll(parseOperationList(lines.get(i++).text()));
        }
        List<FlowAst.FlowStep> steps = new ArrayList<>();
        while (i < lines.size()) {
            FlowLexer.LexedLine line = lines.get(i);
            String t = line.text();
            if (t.startsWith("call ")) {
                ParseCallResult call = parseCall(lines, i);
                steps.add(call.callStep);
                i = call.nextIndex;
                continue;
            }
            if (t.startsWith("extract ")) {
                steps.add(parseExtract(line));
                i++;
                continue;
            }
            if (t.startsWith("wait status ")) {
                steps.add(new FlowAst.WaitStep(quotedSuffix(t, "wait status ")));
                i++;
                continue;
            }
            if (t.startsWith("poll ")) {
                steps.add(parsePoll(line));
                i++;
                continue;
            }
            throw syntax(line, "Unknown step: " + t);
        }
        return new FlowAst.FlowDefinition(flowName, operations, steps);
    }

    private ParseCallResult parseCall(List<FlowLexer.LexedLine> lines, int start) {
        FlowLexer.LexedLine line = lines.get(start);
        String operationId = quotedSuffix(line.text(), "call ");
        List<FlowAst.PathBind> pathBinds = new ArrayList<>();
        List<FlowAst.HeaderBind> headerBinds = new ArrayList<>();
        FlowAst.PoisonClause poison = null;
        FlowAst.ExpectClause expect = null;

        int i = start + 1;
        while (i < lines.size() && lines.get(i).indent() > line.indent()) {
            FlowLexer.LexedLine child = lines.get(i);
            String t = child.text();
            if (t.startsWith("path ")) {
                String[] split = t.substring("path ".length()).split("=", 2);
                if (split.length != 2) {
                    throw syntax(child, "Invalid path bind.");
                }
                pathBinds.add(new FlowAst.PathBind(split[0].trim(), split[1].trim()));
            } else if (t.startsWith("header ")) {
                int eq = t.indexOf('=');
                if (eq < 0) {
                    throw syntax(child, "Invalid header bind.");
                }
                String headerName = quotedSuffix(t.substring(0, eq).trim(), "header ");
                String variable = t.substring(eq + 1).trim();
                headerBinds.add(new FlowAst.HeaderBind(headerName, variable));
            } else if (t.startsWith("poison body ")) {
                poison = parsePoison(child);
            } else if (t.startsWith("expect status ")) {
                expect = new FlowAst.ExpectClause(quotedSuffix(t, "expect status "));
            } else {
                throw syntax(child, "Unknown call option: " + t);
            }
            i++;
        }
        return new ParseCallResult(new FlowAst.CallStep(operationId, pathBinds, headerBinds, poison, expect), i);
    }

    private FlowAst.PoisonClause parsePoison(FlowLexer.LexedLine line) {
        String payload = line.text().substring("poison body ".length()).trim();
        int endPath = payload.indexOf('"', 1);
        if (!payload.startsWith("\"") || endPath < 1) {
            throw syntax(line, "Poison body path must be quoted.");
        }
        String path = payload.substring(1, endPath);
        String remainder = payload.substring(endPath + 1).trim();
        if (remainder.startsWith("value ")) {
            String v = quotedSuffix(remainder, "value ");
            return new FlowAst.PoisonClause(path, FlowAst.PoisonKind.VALUE, v);
        }
        FlowAst.PoisonKind kind = switch (remainder) {
            case "null" -> FlowAst.PoisonKind.NULL;
            case "missing" -> FlowAst.PoisonKind.MISSING;
            case "tooLong" -> FlowAst.PoisonKind.TOO_LONG;
            case "tooShort" -> FlowAst.PoisonKind.TOO_SHORT;
            case "wrongType" -> FlowAst.PoisonKind.WRONG_TYPE;
            case "badEnum" -> FlowAst.PoisonKind.BAD_ENUM;
            case "badPattern" -> FlowAst.PoisonKind.BAD_PATTERN;
            default -> throw syntax(line, "Unknown poison kind: " + remainder);
        };
        return new FlowAst.PoisonClause(path, kind, null);
    }

    private FlowAst.ExtractStep parseExtract(FlowLexer.LexedLine line) {
        String text = line.text();
        java.util.regex.Matcher header = java.util.regex.Pattern
                .compile("^extract\\s+(\\w+)\\s+from\\s+response\\.header\\s+\"([^\"]+)\"(\\s+lastSegment)?$")
                .matcher(text);
        if (header.matches()) {
            return new FlowAst.ExtractStep(
                    header.group(1),
                    FlowAst.ExtractSource.HEADER,
                    header.group(2),
                    header.group(3) != null
            );
        }
        java.util.regex.Matcher body = java.util.regex.Pattern
                .compile("^extract\\s+(\\w+)\\s+from\\s+response\\.body\\s+\"([^\"]+)\"$")
                .matcher(text);
        if (body.matches()) {
            return new FlowAst.ExtractStep(body.group(1), FlowAst.ExtractSource.BODY, body.group(2), false);
        }
        throw syntax(line, "Invalid extract syntax.");
    }

    private FlowAst.PollStep parsePoll(FlowLexer.LexedLine line) {
        String text = line.text();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^poll\\s+(\\w+)\\s+from\\s+response\\.header\\s+\"([^\"]+)\"\\s+until\\s+status\\s+\"([^\"]+)\"(?:\\s+timeout\\s+\"([^\"]+)\")?$"
        ).matcher(text);
        if (!m.matches()) {
            throw syntax(line, "Invalid poll syntax.");
        }
        return new FlowAst.PollStep(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    private List<String> parseOperationList(String text) {
        String list = text.substring("operations:".length()).trim();
        if (list.isEmpty()) {
            return List.of();
        }
        String[] parts = list.split(",");
        List<String> values = new ArrayList<>();
        for (String p : parts) {
            values.add(stripQuotes(p.trim()));
        }
        return values;
    }

    private static String quotedSuffix(String text, String prefix) {
        String tail = text.substring(prefix.length()).trim();
        return stripQuotes(tail);
    }

    private static String stripQuotes(String v) {
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static IllegalArgumentException syntax(FlowLexer.LexedLine line, String message) {
        return new IllegalArgumentException("Line " + line.line() + ": " + message);
    }

    private record ParseCallResult(FlowAst.CallStep callStep, int nextIndex) {
    }
}
