package dao;

import util.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executa um bloco em uma única transação JDBC (commit explícito; rollback em qualquer falha).
 * Os services usam este utilitário e entregam a mesma Connection aos DAOs.
 */
public final class Transacao {
    private Transacao() { }

    public static <R> R executar(SqlFunction<Connection, R> corpo) {
        try (Connection c = ConnectionFactory.abrir()) {
            c.setAutoCommit(false);
            try {
                R resultado = corpo.aplicar(c);
                c.commit();
                return resultado;
            } catch (RuntimeException | SQLException e) {
                try { c.rollback(); } catch (SQLException ignorado) { /* rollback best-effort */ }
                if (e instanceof RuntimeException re) throw re;
                SQLException se = (SQLException) e;
                throw new PersistenciaException("Falha SQL: " + se.getMessage(), se,
                        ConnectionFactory.isViolacaoUnicidade(se));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao abrir/fechar conexão: " + e.getMessage(), e, false);
        }
    }
}
