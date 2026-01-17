package egain.oassdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Util {

    public static Map<String, Object> asStringObjectMap(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Expected a Map, got: " +
                    value.getClass().getName());
        }

        Map<String, Object> out = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Non-String key found: " + e.getKey());
            }
            out.put(key, e.getValue()); // values can be any Object
        }
        return out;
    }

    public static List<Map<String, Object>> asStringObjectMapList(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("Expected a List, got: " +
                    value.getClass().getName());
        }

        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item == null) {
                out.add(null);
            } else {
                out.add(asStringObjectMap(item));
            }
        }
        return out;
    }

    public static List<String> asStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("Expected a List, got: " +
                    value.getClass().getName());
        }

        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item == null) {
                out.add(null);
            } else if (item instanceof String) {
                out.add((String) item);
            } else {
                throw new IllegalArgumentException("Expected List<String>, found non-String element: " +
                        item.getClass().getName());
            }
        }
        return out;
    }

    public static List<Object> asObjectList(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("Expected a List, got: " +
                    value.getClass().getName());
        }

        List<Object> out = new ArrayList<>(raw.size());

        out.addAll(raw);
        return out;
    }

}
