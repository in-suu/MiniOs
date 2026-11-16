package arcadeos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FAULT DEFENDER - a tiny dependency-free JSON reader.
 *
 * Only what the level loader needs: objects, arrays, strings (single or double
 * quoted), numbers, true/false/null. Parsed values come back as
 * {@link Map}, {@link List}, {@link String}, {@link Double} or {@link Boolean}.
 * Malformed input throws {@link IllegalArgumentException}; callers treat that
 * as "this file is unusable" and fall back to the built-in library.
 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) throw p.err("trailing characters");
        return v;
    }

    // ── typed accessors (never throw; return the default when the type differs) ──

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Object o) {
        return (o instanceof List) ? (List<Object>) o : null;
    }

    /** Object member as an object, or an empty map when absent. */
    static Map<String, Object> child(Map<String, Object> m, String key) {
        Map<String, Object> c = obj(get(m, key));
        return c == null ? java.util.Collections.<String, Object>emptyMap() : c;
    }

    /** Object member as a list, or an empty list when absent. */
    static List<Object> children(Map<String, Object> m, String key) {
        List<Object> c = arr(get(m, key));
        return c == null ? java.util.Collections.emptyList() : c;
    }

    private static Object get(Map<String, Object> m, String key) {
        return m == null ? null : m.get(key);
    }

    static String str(Map<String, Object> m, String key, String def) {
        Object v = get(m, key);
        return v == null ? def : String.valueOf(v);
    }

    static double num(Map<String, Object> m, String key, double def) {
        Object v = get(m, key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }

    static int intg(Map<String, Object> m, String key, int def) {
        Object v = get(m, key);
        return (v instanceof Number) ? (int) Math.round(((Number) v).doubleValue()) : def;
    }

    static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = get(m, key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    // ── scanning ─────────────────────────────────────────────────────────────

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw err("unexpected end of input");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"':
            case '\'': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;                       // '{'
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            expect(':');
            m.put(key, value());
            ws();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw err("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<>();
        i++;                       // '['
        ws();
        if (peek() == ']') { i++; return out; }
        while (true) {
            out.add(value());
            ws();
            char c = next();
            if (c == ']') return out;
            if (c != ',') throw err("expected ',' or ']'");
        }
    }

    private String string() {
        char q = next();
        if (q != '"' && q != '\'') throw err("expected a quote");
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw err("unterminated string");
            char c = s.charAt(i++);
            if (c == q) return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        if (peek() == '-' || peek() == '+') i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') i++;
            else break;
        }
        if (start == i) throw err("expected a number");
        try {
            return Double.valueOf(Double.parseDouble(s.substring(start, i)));
        } catch (NumberFormatException ex) {
            throw err("bad number '" + s.substring(start, i) + "'");
        }
    }

    private void literal(String lit) {
        if (!s.startsWith(lit, i)) throw err("expected " + lit);
        i += lit.length();
    }

    private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

    private char next() {
        if (i >= s.length()) throw err("unexpected end of input");
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) throw err("expected '" + c + "'");
    }

    private IllegalArgumentException err(String msg) {
        int line = 1, col = 1;
        for (int k = 0; k < i && k < s.length(); k++) {
            if (s.charAt(k) == '\n') { line++; col = 1; } else col++;
        }
        return new IllegalArgumentException("JSON " + msg + " at line " + line + ", col " + col);
    }
}
