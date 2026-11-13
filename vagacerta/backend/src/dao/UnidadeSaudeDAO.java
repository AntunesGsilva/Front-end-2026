package dao;

import model.RegraNegocioException;
import model.unidade.UnidadeSaude;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD de Unidades Básicas de Saúde. */
public class UnidadeSaudeDAO extends BaseDAO {

    public UnidadeSaudeDAO() { super(); }

    public UnidadeSaudeDAO(Connection c) { super(c); }

    public UnidadeSaude inserir(UnidadeSaude u) {
        try {
            long id = executar(c -> inserirRetornandoId(c,
                    "INSERT INTO unidade_saude (nome, cnes, endereco, telefone) VALUES (?,?,?,?)",
                    ps -> { ps.setString(1, u.getNome()); ps.setString(2, u.getCnes());
                            ps.setString(3, u.getEndereco()); ps.setString(4, u.getTelefone()); }));
            u.setId(id);
            return u;
        } catch (PersistenciaException e) {
            if (e.isViolacaoUnicidade()) throw RegraNegocioException.validacao("Já existe uma unidade com este CNES.");
            throw e;
        }
    }

    public Optional<UnidadeSaude> buscarPorId(long id) {
        return executar(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM unidade_saude WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapear(rs)) : Optional.<UnidadeSaude>empty();
                }
            }
        });
    }

    public List<UnidadeSaude> listar() {
        return executar(c -> {
            List<UnidadeSaude> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM unidade_saude ORDER BY nome");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
            return lista;
        });
    }

    public void atualizar(UnidadeSaude u) {
        try {
            int n = executar(c -> atualizar(c, "UPDATE unidade_saude SET nome=?, cnes=?, endereco=?, telefone=? WHERE id=?",
                    ps -> { ps.setString(1, u.getNome()); ps.setString(2, u.getCnes()); ps.setString(3, u.getEndereco());
                            ps.setString(4, u.getTelefone()); ps.setLong(5, u.getId()); }));
            if (n == 0) throw RegraNegocioException.naoEncontrado("Unidade " + u.getId() + " não encontrada.");
        } catch (PersistenciaException e) {
            if (e.isViolacaoUnicidade()) throw RegraNegocioException.validacao("Já existe uma unidade com este CNES.");
            throw e;
        }
    }

    public boolean excluir(long id) {
        try {
            return executar(c -> atualizar(c, "DELETE FROM unidade_saude WHERE id=?", ps -> ps.setLong(1, id))) > 0;
        } catch (PersistenciaException e) {
            throw RegraNegocioException.validacao("Não é possível excluir: a unidade possui agendas vinculadas.");
        }
    }

    private static UnidadeSaude mapear(ResultSet rs) throws SQLException {
        return new UnidadeSaude(rs.getLong("id"), rs.getString("nome"), rs.getString("cnes"),
                rs.getString("endereco"), rs.getString("telefone"));
    }
}
