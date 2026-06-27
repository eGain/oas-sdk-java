package egain.oassdk.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight lexer that normalizes flow lines and indentation.
 */
public final class FlowLexer {

    public List<LexedLine> lex(String input) {
        List<LexedLine> lines = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return lines;
        }
        String[] rawLines = input.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < rawLines.length; i++) {
            String raw = rawLines[i];
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }
            lines.add(new LexedLine(i + 1, indent, trimmed));
        }
        return lines;
    }

    public record LexedLine(int line, int indent, String text) {
    }
}
