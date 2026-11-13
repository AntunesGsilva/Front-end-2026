package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Conversões java.time <-> texto SQL. Usar texto ISO nos dois bancos (MySQL e SQLite) evita
 * diferenças de fuso/tipo entre os drivers.
 */
public final class Sql {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Sql() { }

    public static String data(LocalDate d) { return d == null ? null : d.toString(); }
    public static String hora(LocalTime h) { return h == null ? null : h.format(HORA); }
    public static String dataHora(LocalDateTime dh) { return dh == null ? null : dh.format(DATA_HORA); }

    public static LocalDate lerData(String s) {
        return s == null ? null : LocalDate.parse(s.substring(0, 10));
    }

    public static LocalTime lerHora(String s) {
        return s == null ? null : LocalTime.parse(s.length() > 8 ? s.substring(0, 8) : s);
    }

    public static LocalDateTime lerDataHora(String s) {
        return s == null ? null : LocalDateTime.parse(s.replace(' ', 'T'));
    }
}
