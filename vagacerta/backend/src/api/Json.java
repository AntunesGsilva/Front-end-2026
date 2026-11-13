package api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializador/parser JSON mínimo (sem bibliotecas), suficiente para a API do VagaCerta. */
public final class Json {
    private Json() { }

    // ------------------------------------------------------------ escrita
    public static String escrever(Object o) {
        StringBuilder sb = new StringBuilder();
        escrever(o, sb);
        return sb.toString();
    }

    private static void escrever(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            texto(s, sb);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                texto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean primeiro = true;
            for (Object x : it) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(x, sb);
            }
            sb.append(']');
        } else {
            texto(String.valueOf(o), sb); // enums, java.time.*, etc.
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ leitura
    public static Object ler(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<String, Object>();
        Leitor l = new Leitor(json);
        Object v = l.valor();
        l.espacos();
        if (l.pos != json.length()) throw new IllegalArgumentException("JSON inválido: conteúdo após o fim do documento.");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lerObjeto(String json) {
        Object o = ler(json);
        if (!(o instanceof Map)) throw new IllegalArgumentException("Esperado um objeto JSON.");
        return (Map<String, Object>) o;
    }

    private static final class Leitor {
        final String s;
        int pos;

        Leitor(String s) { this.s = s; }

        void espacos() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object valor() {
            espacos();
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto.");
            char c = s.charAt(pos);
            if (c == '{') return objeto();
            if (c == '[') return lista();
            if (c == '"') return string();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            return numero();
        }

        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            espacos();
            if (s.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                espacos();
                String chave = string();
                espacos();
                esperar(':');
                m.put(chave, valor());
                espacos();
                if (s.charAt(pos) == ',') { pos++; continue; }
                esperar('}');
                return m;
            }
        }

        List<Object> lista() {
            List<Object> l = new ArrayList<>();
            pos++;
            espacos();
            if (s.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                l.add(valor());
                espacos();
                if (s.charAt(pos) == ',') { pos++; continue; }
                esperar(']');
                return l;
            }
        }

        String string() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16)); pos += 4; }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String JSON não terminada.");
        }

        Object numero() {
            int ini = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String n = s.substring(ini, pos);
            if (n.isEmpty()) throw new IllegalArgumentException("JSON inválido na posição " + ini + ".");
            return (n.contains(".") || n.contains("e") || n.contains("E")) ? (Object) Double.valueOf(n) : (Object) Long.valueOf(n);
        }

        void esperar(char c) {
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("JSON inválido: esperado '" + c + "' na posição " + pos + ".");
            }
            pos++;
        }
    }
}
