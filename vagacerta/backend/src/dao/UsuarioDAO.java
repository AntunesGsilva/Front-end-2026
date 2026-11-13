package dao;

import model.RegraNegocioException;
import model.enums.PerfilAcesso;
import model.usuario.GestorUnidade;
import model.usuario.Paciente;
import model.usuario.ProfissionalSaude;
import model.usuario.Usuario;
import util.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD da hierarquia Usuario (tabela base + tabela de especialização com PK/FK conjunta). */
public class UsuarioDAO extends BaseDAO {

    private static final String SELECT = "SELECT u.id, u.nome, u.cpf, u.email, u.senha_hash, u.telefone, u.perfil, u.ativo, "
            + "p.cartao_sus, p.data_nascimento, p.faltas_nao_justificadas, "
            + "pr.registro_conselho, pr.especialidade, pr.carga_horaria_diaria, "
            + "g.matricula, g.cargo "
            + "FROM usuario u "
            + "LEFT JOIN paciente p ON p.usuario_id = u.id "
            + "LEFT JOIN profissional_saude pr ON pr.usuario_id = u.id "
            + "LEFT JOIN gestor_unidade g ON g.usuario_id = u.id ";

    public UsuarioDAO() { super(); }

    public UsuarioDAO(Connection c) { super(c); }

    // ---------------- CREATE ----------------
    public Usuario inserir(Usuario u) {
        try {
            return executarAtomico(c -> {
                long id = inserirRetornandoId(c,
                        "INSERT INTO usuario (nome, cpf, email, senha_hash, telefone, perfil, ativo) VALUES (?,?,?,?,?,?,?)",
                        ps -> {
                            ps.setString(1, u.getNome());
                            ps.setString(2, u.getCpf());
                            ps.setString(3, u.getEmail());
                            ps.setString(4, u.getSenhaHash());
                            ps.setString(5, u.getTelefone());
                            ps.setString(6, u.perfil().name());
                            ps.setBoolean(7, u.isAtivo());
                        });
                u.setId(id);
                inserirEspecializacao(c, u);
                return u;
            });
        } catch (PersistenciaException e) {
            throw traduzirDuplicidade(e);
        }
    }

    private void inserirEspecializacao(Connection c, Usuario u) throws SQLException {
        if (u instanceof Paciente p) {
            atualizar(c, "INSERT INTO paciente (usuario_id, cartao_sus, data_nascimento, faltas_nao_justificadas) VALUES (?,?,?,?)",
                    ps -> { ps.setLong(1, p.getId()); ps.setString(2, p.getCartaoSus());
                            ps.setString(3, Sql.data(p.getDataNascimento())); ps.setInt(4, p.getFaltasNaoJustificadas()); });
        } else if (u instanceof ProfissionalSaude p) {
            atualizar(c, "INSERT INTO profissional_saude (usuario_id, registro_conselho, especialidade, carga_horaria_diaria) VALUES (?,?,?,?)",
                    ps -> { ps.setLong(1, p.getId()); ps.setString(2, p.getRegistroConselho());
                            ps.setString(3, p.getEspecialidade()); ps.setInt(4, p.getCargaHorariaDiaria()); });
        } else if (u instanceof GestorUnidade g) {
            atualizar(c, "INSERT INTO gestor_unidade (usuario_id, matricula, cargo) VALUES (?,?,?)",
                    ps -> { ps.setLong(1, g.getId()); ps.setString(2, g.getMatricula()); ps.setString(3, g.getCargo()); });
        }
    }

    // ---------------- READ ----------------
    public Optional<Usuario> buscarPorId(long id) {
        return buscarUnico(SELECT + "WHERE u.id = ?", ps -> ps.setLong(1, id));
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return buscarUnico(SELECT + "WHERE u.email = ?", ps -> ps.setString(1, email.trim().toLowerCase()));
    }

    public Optional<Usuario> buscarPorCpf(String cpf) {
        return buscarUnico(SELECT + "WHERE u.cpf = ?", ps -> ps.setString(1, cpf.replaceAll("\\D", "")));
    }

    public List<Usuario> listar(PerfilAcesso perfil) {
        String sql = SELECT + (perfil == null ? "" : "WHERE u.perfil = ? ") + "ORDER BY u.nome";
        return executar(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (perfil != null) ps.setString(1, perfil.name());
                List<Usuario> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
                return lista;
            }
        });
    }

    private Optional<Usuario> buscarUnico(String sql, Binder binder) {
        return executar(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.vincular(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapear(rs)) : Optional.<Usuario>empty();
                }
            }
        });
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String nome = rs.getString("nome"), cpf = rs.getString("cpf"), email = rs.getString("email");
        String hash = rs.getString("senha_hash"), tel = rs.getString("telefone");
        boolean ativo = rs.getBoolean("ativo");
        return switch (PerfilAcesso.valueOf(rs.getString("perfil"))) {
            case PACIENTE -> new Paciente(id, nome, cpf, email, hash, tel, ativo, rs.getString("cartao_sus"),
                    Sql.lerData(rs.getString("data_nascimento")), rs.getInt("faltas_nao_justificadas"));
            case PROFISSIONAL -> new ProfissionalSaude(id, nome, cpf, email, hash, tel, ativo,
                    rs.getString("registro_conselho"), rs.getString("especialidade"), rs.getInt("carga_horaria_diaria"));
            case GESTOR -> new GestorUnidade(id, nome, cpf, email, hash, tel, ativo,
                    rs.getString("matricula"), rs.getString("cargo"));
        };
    }

    // ---------------- UPDATE ----------------
    public void atualizar(Usuario u) {
        if (u.getId() == null) throw RegraNegocioException.validacao("Usuário sem id não pode ser atualizado.");
        try {
            int linhas = executarAtomico(c -> {
                int n = atualizar(c, "UPDATE usuario SET nome=?, cpf=?, email=?, senha_hash=?, telefone=?, ativo=? WHERE id=?",
                        ps -> { ps.setString(1, u.getNome()); ps.setString(2, u.getCpf()); ps.setString(3, u.getEmail());
                                ps.setString(4, u.getSenhaHash()); ps.setString(5, u.getTelefone());
                                ps.setBoolean(6, u.isAtivo()); ps.setLong(7, u.getId()); });
                if (n == 1) atualizarEspecializacao(c, u);
                return n;
            });
            if (linhas == 0) throw RegraNegocioException.naoEncontrado("Usuário " + u.getId() + " não encontrado.");
        } catch (PersistenciaException e) {
            throw traduzirDuplicidade(e);
        }
    }

    private void atualizarEspecializacao(Connection c, Usuario u) throws SQLException {
        if (u instanceof Paciente p) {
            atualizar(c, "UPDATE paciente SET cartao_sus=?, data_nascimento=?, faltas_nao_justificadas=? WHERE usuario_id=?",
                    ps -> { ps.setString(1, p.getCartaoSus()); ps.setString(2, Sql.data(p.getDataNascimento()));
                            ps.setInt(3, p.getFaltasNaoJustificadas()); ps.setLong(4, p.getId()); });
        } else if (u instanceof ProfissionalSaude p) {
            atualizar(c, "UPDATE profissional_saude SET registro_conselho=?, especialidade=?, carga_horaria_diaria=? WHERE usuario_id=?",
                    ps -> { ps.setString(1, p.getRegistroConselho()); ps.setString(2, p.getEspecialidade());
                            ps.setInt(3, p.getCargaHorariaDiaria()); ps.setLong(4, p.getId()); });
        } else if (u instanceof GestorUnidade g) {
            atualizar(c, "UPDATE gestor_unidade SET matricula=?, cargo=? WHERE usuario_id=?",
                    ps -> { ps.setString(1, g.getMatricula()); ps.setString(2, g.getCargo()); ps.setLong(3, g.getId()); });
        }
    }

    /** Mantém o contador desnormalizado de faltas (RN03) em sincronia com registro_falta. */
    public void atualizarFaltas(long pacienteId, int faltas) {
        executar(c -> atualizar(c, "UPDATE paciente SET faltas_nao_justificadas=? WHERE usuario_id=?",
                ps -> { ps.setInt(1, faltas); ps.setLong(2, pacienteId); }));
    }

    // ---------------- DELETE ----------------
    public boolean excluir(long id) {
        try {
            return executar(c -> atualizar(c, "DELETE FROM usuario WHERE id=?", ps -> ps.setLong(1, id))) > 0;
        } catch (PersistenciaException e) {
            throw new RegraNegocioException("VALIDACAO",
                    "Não é possível excluir: o usuário possui agendas ou agendamentos vinculados. Desative-o em vez de excluir.");
        }
    }

    private static RuntimeException traduzirDuplicidade(PersistenciaException e) {
        if (e.isViolacaoUnicidade()) {
            return RegraNegocioException.validacao(
                    "Já existe cadastro com este CPF, e-mail, cartão SUS, matrícula ou registro no conselho.");
        }
        return e;
    }
}
