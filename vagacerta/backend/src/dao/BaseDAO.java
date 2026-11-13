package dao;

import util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base dos DAOs. Sem Connection no construtor, cada operação abre e fecha a sua própria
 * (auto-commit); com Connection (vinda de {@link Transacao}), participa da transação do service.
 */
abstract class BaseDAO {
    private final Connection compartilhada;

    protected BaseDAO() { this(null); }

    protected BaseDAO(Connection compartilhada) { this.compartilhada = compartilhada; }

    protected <R> R executar(SqlFunction<Connection, R> corpo) {
        if (compartilhada != null) {
            try {
                return corpo.aplicar(compartilhada);
            } catch (SQLException e) {
                throw traduzir(e);
            }
        }
        try (Connection c = ConnectionFactory.abrir()) {
            return corpo.aplicar(c);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /** Várias instruções que precisam ser atômicas (ex.: tabela base + especialização). */
    protected <R> R executarAtomico(SqlFunction<Connection, R> corpo) {
        if (compartilhada != null) return executar(corpo);
        return Transacao.executar(corpo);
    }

    protected static PersistenciaException traduzir(SQLException e) {
        return new PersistenciaException("Falha SQL: " + e.getMessage(), e, ConnectionFactory.isViolacaoUnicidade(e));
    }

    protected static long inserirRetornandoId(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.vincular(ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Chave gerada não retornada.");
    }

    protected static int atualizar(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.vincular(ps);
            return ps.executeUpdate();
        }
    }

    @FunctionalInterface
    protected interface Binder {
        void vincular(PreparedStatement ps) throws SQLException;
    }
}
