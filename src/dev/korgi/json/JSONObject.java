package dev.korgi.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class JSONObject {

    private Map<String, Object> values = new HashMap<>();

    public JSONObject() {
    }

    public JSONObject(Object obj) {
        if (obj == null)
            return;

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = wrapValue(entry.getValue());
                values.put(key, value);
            }
        } else {
            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    values.put(field.getName(), wrapValue(value));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Object wrapValue(Object value) {
        if (value == null)
            return null;

        Class<?> clazz = value.getClass();

        if (isPrimitiveOrWrapper(clazz) || value instanceof String) {
            return value;
        } else if (clazz.isArray()) {
            int length = Array.getLength(value);
            Object[] arr = new Object[length];
            for (int i = 0; i < length; i++) {
                arr[i] = wrapValue(Array.get(value, i));
            }
            return arr;
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> wrappedList = new ArrayList<>();
            for (Object elem : list)
                wrappedList.add(wrapValue(elem));
            return wrappedList;
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> wrappedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                wrappedMap.put(entry.getKey().toString(), wrapValue(entry.getValue()));
            }
            return wrappedMap;
        } else if (value instanceof JSONObject) {
            return value;
        } else {
            return new JSONObject(value);
        }
    }

    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == Integer.class
                || clazz == Double.class
                || clazz == Float.class
                || clazz == Long.class
                || clazz == Short.class
                || clazz == Byte.class
                || clazz == Boolean.class
                || clazz == Character.class;
    }

    public boolean hasKey(String... keys) {
        for (String key : keys) {
            if (!values.containsKey(key))
                return false;
        }
        return true;
    }

    public void set(String key, Object value) {
        values.put(key, wrapValue(value));
    }

    public Map<String, Object> toMap() {
        return values;
    }

    @Override
    public String toString() {
        return toJSONString();
    }

    public String toJSONString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            sb.append(valueToJSONString(entry.getValue()));
            if (i < values.size() - 1)
                sb.append(",");
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String valueToJSONString(Object value) {
        if (value == null)
            return "null";
        if (value instanceof String)
            return "\"" + escape((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean)
            return value.toString();
        if (value instanceof JSONObject)
            return valueToJSONString(((JSONObject) value).values);
        if (value instanceof Map) {
            return new JSONObject(value).toJSONString();
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                sb.append(valueToJSONString(list.get(i)));
                if (i < list.size() - 1)
                    sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < length; i++) {
                sb.append(valueToJSONString(Array.get(value, i)));
                if (i < length - 1)
                    sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        System.out.println(value.getClass());
        return new JSONObject(value).toJSONString();
    }

    private String escape(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========================
    // Typed getters for single values
    // ========================
    public String getString(String key) {
        Object value = values.get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String key) {
        Object value = values.get(key);
        if (value instanceof Number)
            return ((Number) value).intValue();
        return null;
    }

    public Double getDouble(String key) {
        Object value = values.get(key);
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        return null;
    }

    public Boolean getBoolean(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean)
            return (Boolean) value;
        return null;
    }

    public Long getLong(String key) {
        Object value = values.get(key);
        if (value instanceof Number)
            return ((Number) value).longValue();
        return null;
    }

    public Float getFloat(String key) {
        Object value = values.get(key);
        if (value instanceof Number)
            return ((Number) value).floatValue();
        return null;
    }

    public Character getChar(String key) {
        Object value = values.get(key);
        if (value instanceof Character)
            return (Character) value;
        if (value instanceof String && ((String) value).length() == 1)
            return ((String) value).charAt(0);
        return null;
    }

    public JSONObject getJSONObject(String key) {
        Object value = values.get(key);
        if (value instanceof JSONObject)
            return (JSONObject) value;
        return null;
    }

    // ========================
    // Typed getters for array/list values
    // ========================
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = values.get(key);
        if (value instanceof List)
            return (List<String>) value;
        if (value instanceof Object[])
            return Arrays.asList(Arrays.copyOf((Object[]) value, ((Object[]) value).length, String[].class));
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getIntList(String key) {
        Object value = values.get(key);
        if (value instanceof List)
            return (List<Integer>) value;
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Integer> list = new ArrayList<>();
            for (Object o : arr)
                list.add(((Number) o).intValue());
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Double> getDoubleList(String key) {
        Object value = values.get(key);
        if (value instanceof List)
            return (List<Double>) value;
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Double> list = new ArrayList<>();
            for (Object o : arr)
                list.add(((Number) o).doubleValue());
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Boolean> getBooleanList(String key) {
        Object value = values.get(key);
        if (value instanceof List)
            return (List<Boolean>) value;
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Boolean> list = new ArrayList<>();
            for (Object o : arr)
                list.add((Boolean) o);
            return list;
        }
        return null;
    }

    // ========================
    // Adders for single values and list forms
    // ========================
    public void addString(String key, String value) {
        addToList(key, value);
    }

    public void addInt(String key, int value) {
        addToList(key, value);
    }

    public void addDouble(String key, double value) {
        addToList(key, value);
    }

    public void addBoolean(String key, boolean value) {
        addToList(key, value);
    }

    public void addLong(String key, long value) {
        addToList(key, value);
    }

    public void addFloat(String key, float value) {
        addToList(key, value);
    }

    public void addChar(String key, char value) {
        addToList(key, value);
    }

    @SuppressWarnings("unchecked")
    private <T> void addToList(String key, T value) {
        Object existing = values.get(key);
        List<Object> list;
        if (existing instanceof List) {
            list = (List<Object>) existing;
        } else if (existing != null && existing.getClass().isArray()) {
            list = new ArrayList<>(Arrays.asList((Object[]) existing));
        } else {
            list = new ArrayList<>();
            if (existing != null)
                list.add(existing);
        }
        list.add(value);
        values.put(key, list);
    }

    // ========================
    // JSON string deserialization
    // ========================
    public static JSONObject fromJSONString(String json) throws RuntimeException {
        JSONParser parser = new JSONParser(json);
        return parser.parseObject();
    }

    public void fillObject(Object target) {
        if (target == null)
            return;

        Class<?> clazz = target.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String key = field.getName();

            if (!values.containsKey(key))
                continue; // Skip if key doesn't exist

            Object value = values.get(key);
            if (value == null)
                continue;

            try {
                Class<?> type = field.getType();

                // Handle primitive types and wrappers
                if (type == int.class || type == Integer.class) {
                    field.set(target, ((Number) value).intValue());
                } else if (type == double.class || type == Double.class) {
                    field.set(target, ((Number) value).doubleValue());
                } else if (type == float.class || type == Float.class) {
                    field.set(target, ((Number) value).floatValue());
                } else if (type == long.class || type == Long.class) {
                    field.set(target, ((Number) value).longValue());
                } else if (type == boolean.class || type == Boolean.class) {
                    field.set(target, (Boolean) value);
                } else if (type == char.class || type == Character.class) {
                    if (value instanceof Character)
                        field.set(target, value);
                    else if (value instanceof String && ((String) value).length() > 0)
                        field.set(target, ((String) value).charAt(0));
                } else if (type == String.class) {
                    field.set(target, value.toString());
                } else if (type.isArray() && value instanceof List) {
                    List<?> list = (List<?>) value;
                    Object array = Array.newInstance(type.getComponentType(), list.size());
                    for (int i = 0; i < list.size(); i++) {
                        Array.set(array, i, list.get(i));
                    }
                    field.set(target, array);
                } else if (value instanceof JSONObject) {
                    Object nested = type.getDeclaredConstructor().newInstance();
                    ((JSONObject) value).fillObject(nested);
                    field.set(target, nested);
                } else if (value instanceof Map) {
                    field.set(target, value);
                } else if (value instanceof List) {
                    field.set(target, value);
                }

            } catch (Exception e) {
                e.printStackTrace(); // Skip problematic fields
            }
        }
    }

    // ========================
    // Internal simple JSON parser
    // ========================
    private static class JSONParser {
        private final String json;
        private int pos = 0;

        public JSONParser(String json) {
            this.json = json.trim();
        }

        public JSONObject parseObject() {
            skipWhitespace();
            if (peek() != '{')
                throw new RuntimeException("Expected '{' at position " + pos);
            pos++; // skip '{'
            JSONObject obj = new JSONObject();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return obj;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                obj.set(key, value);
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else if (peek() == '}') {
                    pos++;
                    break;
                } else {
                    throw new RuntimeException("Expected ',' or '}' at position " + pos);
                }
            }
            return obj;
        }

        private Object parseValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"')
                return parseString();
            if (c == '{')
                return parseObject();
            if (c == '[')
                return parseArray();
            if (c == 't' || c == 'f')
                return parseBoolean();
            if (c == 'n')
                return parseNull();
            return parseNumber();
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else if (peek() == ']') {
                    pos++;
                    break;
                } else {
                    throw new RuntimeException("Expected ',' or ']' at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"')
                    break;
                if (c == '\\') {
                    c = next();
                    switch (c) {
                        case '"', '\\', '/' -> sb.append(c);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        default -> throw new RuntimeException("Invalid escape: \\" + c);
                    }
                } else
                    sb.append(c);
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("Invalid boolean at position " + pos);
        }

        private Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new RuntimeException("Invalid null at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (peek() == '-')
                pos++;
            while (pos < json.length() && Character.isDigit(peek()))
                pos++;
            if (pos < json.length() && peek() == '.') {
                pos++;
                while (pos < json.length() && Character.isDigit(peek()))
                    pos++;
                return Double.parseDouble(json.substring(start, pos));
            }
            return Long.parseLong(json.substring(start, pos));
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos)))
                pos++;
        }

        private char peek() {
            if (pos >= json.length())
                return '\0';
            return json.charAt(pos);
        }

        private char next() {
            if (pos >= json.length())
                throw new RuntimeException("Unexpected end of input");
            return json.charAt(pos++);
        }

        private void expect(char expected) {
            char c = next();
            if (c != expected)
                throw new RuntimeException(
                        "Expected '" + expected + "' but found '" + c + "' at position " + (pos - 1));
        }
    }
}
