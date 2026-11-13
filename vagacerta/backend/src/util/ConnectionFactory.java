package util;

import dao.PersistenciaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Fábrica de conexões JDBC puras com alternância transparente entre MySQL 8 e SQLite.
 *
 * Configuração (prioridade: propriedade de sistema -Dvagacerta.* > variável de ambiente > padrão):
 *   VAGACERTA_JDBC_URL      ex.: jdbc:mysql://localhost:3306/vagacerta_db?createDatabaseIfNotExist=true
 *                           padrão: jdbc:sqlite:vagacerta.db (arquivo local autocontido)
 *   VAGACERTA_DB_USER       ex.: root
 *   VAGACERTA_DB_PASSWORD   ex.: senha
 *   VAGACERTA_SCHEMA_DIR    pasta com 01_schema.sql + 02_seed.sql (ou as versões *_sqlite.sql);
 *                           padrão: procura ./database, ../database
 */
public final class ConnectionFactory {
    public static final String URL_PADRAO = "jdbc:sqlite:vagacerta.db";
    private static final Object LOCK = new Object();

    private ConnectionFactory() { }

    public enum Dialeto { MYSQL, SQLITE }

    private static String config(String propriedade, String ambiente, String padrao) {
        String v = System.getProperty(propriedade);
        if (v == null || v.isBlank()) v = System.getenv(ambiente);
        return (v == null || v.isBlank()) ? padrao : v;
    }

    public static String url() { return config("vagacerta.jdbc.url", "VAGACERTA_JDBC_URL", URL_PADRAO); }

    public static Dialeto dialeto() {
        return url().startsWith("jdbc:sqlite:") ? Dialeto.SQLITE : Dialeto.MYSQL;
    }

    public static Connection abrir() throws SQLException {
        String url = url();
        Properties p = new Properties();
        if (dialeto() == Dialeto.SQLITE) {
            // IMMEDIATE evita deadlock de upgrade de lock quando duas transações tentam reservar o mesmo horário.
            p.setProperty("transaction_mode", "IMMEDIATE");
            p.setProperty("foreign_keys", "true");
            p.setProperty("busy_timeout", "15000");
        } else {
            p.setProperty("user", config("vagacerta.db.user", "VAGACERTA_DB_USER", "root"));
            p.setProperty("password", config("vagacerta.db.password", "VAGACERTA_DB_PASSWORD", ""));
        }
        Connection c = DriverManager.getConnection(url, p);
        if (dialeto() == Dialeto.SQLITE) {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        return c;
    }

    /** Cria o esquema (DDL + seeds) se a tabela usuario ainda não existir. Idempotente. */
    public static void inicializarBanco() {
        synchronized (LOCK) {
            try (Connection c = abrir()) {
                if (tabelaExiste(c, "usuario")) return;
                for (Path script : localizarScripts()) {
                    executarScript(c, Files.readString(script, StandardCharsets.UTF_8));
                }
            } catch (SQLException | IOException e) {
                throw new PersistenciaException("Falha ao inicializar o banco: " + e.getMessage(), e, false);
            }
        }
    }

    /** Diagnóstico da conexão ativa: abre, consulta metadados e conta as tabelas do esquema. */
    public static StatusConexao testarConexao() {
        long ini = System.nanoTime();
        try (Connection c = abrir()) {
            DatabaseMetaData md = c.getMetaData();
            int tabelas = 0;
            for (String t : new String[] {"usuario", "paciente", "profissional_saude", "gestor_unidade", "unidade_saude",
                    "agenda_diaria", "horario", "agendamento", "consulta_clinica", "exame", "vacinacao",
                    "registro_falta", "liberacao_vaga"}) {
                if (tabelaExiste(c, t)) tabelas++;
            }
            long ms = (System.nanoTime() - ini) / 1_000_000;
            return new StatusConexao(true, md.getDatabaseProductName(), md.getDatabaseProductVersion(),
                    mascarar(url()), tabelas, ms, tabelas == 13 ? "Conexão ativa e esquema completo." :
                    "Conexão ativa, mas o esquema está incompleto (" + tabelas + "/13 tabelas).");
        } catch (SQLException e) {
            return new StatusConexao(false, dialeto().name(), "-", mascarar(url()), 0,
                    (System.nanoTime() - ini) / 1_000_000, "Falha na conexão: " + e.getMessage());
        }
    }

    static boolean tabelaExiste(Connection c, String nome) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String candidato : new String[] {nome, nome.toUpperCase()}) {
            try (ResultSet rs = md.getTables(c.getCatalog(), null, candidato, new String[] {"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    /** Scripts de database/ na ordem de execução: estrutura (01) e depois dados iniciais (02). */
    private static List<Path> localizarScripts() throws IOException {
        boolean sqlite = dialeto() == Dialeto.SQLITE;
        List<Path> scripts = new ArrayList<>();
        scripts.add(localizarScript(sqlite ? "01_schema_sqlite.sql" : "01_schema.sql"));
        scripts.add(localizarScript(sqlite ? "02_seed_sqlite.sql" : "02_seed.sql"));
        return scripts;
    }

    private static Path localizarScript(String arquivo) throws IOException {
        List<Path> candidatos = new ArrayList<>();
        String dir = config("vagacerta.schema.dir", "VAGACERTA_SCHEMA_DIR", null);
        if (dir != null) candidatos.add(Path.of(dir, arquivo));
        for (String rel : new String[] {"database", "../database", "../../database"}) {
            candidatos.add(Path.of(rel, arquivo));
        }
        for (Path p : candidatos) {
            if (Files.isRegularFile(p)) return p;
        }
        throw new IOException("Script " + arquivo + " não encontrado. Defina VAGACERTA_SCHEMA_DIR. Procurado em: " + candidatos);
    }

    /** Executa um script SQL simples (comentários '--' e instruções terminadas em ';'). */
    static void executarScript(Connection c, String script) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (String linha : script.split("\\R")) {
            String t = linha.strip();
            if (t.startsWith("--") || t.isEmpty()) continue;
            sb.append(linha).append('\n');
        }
        try (Statement st = c.createStatement()) {
            for (String instrucao : sb.toString().split(";\\s*\\n")) {
                String sql = instrucao.strip();
                if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
                // o banco de destino é o da URL JDBC (createDatabaseIfNotExist=true cria se faltar)
                String maiusculo = sql.toUpperCase();
                if (maiusculo.startsWith("CREATE DATABASE") || maiusculo.startsWith("USE ")) continue;
                if (!sql.isBlank()) st.execute(sql);
            }
        }
    }

    public static boolean isViolacaoUnicidade(SQLException e) {
        String estado = e.getSQLState();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return (estado != null && estado.startsWith("23"))
                || msg.contains("UNIQUE constraint failed") || msg.contains("Duplicate entry")
                || msg.contains("SQLITE_CONSTRAINT_UNIQUE") || msg.contains("SQLITE_CONSTRAINT_PRIMARYKEY");
    }

    private static String mascarar(String url) {
        return url.replaceAll("(?i)(password|pwd)=[^&;]*", "$1=***");
    }
}
