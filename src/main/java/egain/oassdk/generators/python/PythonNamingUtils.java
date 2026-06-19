package egain.oassdk.generators.python;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

/**
 * Python identifier and naming utilities shared by FastAPI and Flask generators.
 */
public final class PythonNamingUtils {

    private static final Set<String> PY_KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
            "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or",
            "pass", "raise", "return", "try", "while", "with", "yield", "match", "case");

    private PythonNamingUtils() {
    }

    public static String pyStr(String s) {
        if (s == null) {
            return "\"\"";
        }
        String esc = s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + esc + "\"";
    }

    public static String pyLiteral(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return pyStr(value.toString());
    }

    public static String pyRegex(String pattern) {
        if (pattern == null) {
            return "\"\"";
        }
        if (!pattern.contains("\"") && !endsWithOddBackslashRun(pattern)) {
            return "r\"" + pattern + "\"";
        }
        return pyStr(pattern);
    }

    public static boolean endsWithOddBackslashRun(String s) {
        int count = 0;
        for (int i = s.length() - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            count++;
        }
        return (count % 2) == 1;
    }

    public static boolean isValidPyIdentifier(String s) {
        if (s == null || s.isEmpty() || PY_KEYWORDS.contains(s)) {
            return false;
        }
        if (!Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public static String toPyIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return "param";
        }
        String snake = toSnakeCase(name);
        StringBuilder sb = new StringBuilder();
        for (char c : snake.toCharArray()) {
            sb.append((Character.isLetterOrDigit(c) || c == '_') ? c : '_');
        }
        String id = sb.toString().replaceAll("^_+", "");
        if (id.isEmpty()) {
            id = "param";
        }
        if (Character.isDigit(id.charAt(0))) {
            id = "_" + id;
        }
        if (PY_KEYWORDS.contains(id)) {
            id = id + "_";
        }
        return id;
    }

    public static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else if (c == '-' || c == ' ') {
                result.append('_');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else {
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    public static String toPythonClassName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "Unknown";
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : schemaName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else if (c == '-' || c == '_' || c == ' ' || c == '.') {
                capitalizeNext = true;
            }
        }
        if (result.isEmpty() || !Character.isLetter(result.charAt(0))) {
            return "Schema" + result;
        }
        return result.toString();
    }

    public static String generateRouterName(String path) {
        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Router";
    }

    public static String generateBlueprintName(String path) {
        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Blueprint";
    }

    public static void writeFile(String filePath, String content) throws java.io.IOException {
        Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
    }

    public static void createInitFile(String dirPath) throws java.io.IOException {
        writeFile(dirPath + "/__init__.py", "");
    }
}
