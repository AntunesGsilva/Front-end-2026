package dao;

import model.RegraNegocioException;
import model.agendamento.Agendamento;
import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.Vacinacao;
import model.enums.StatusAgendamento;
import model.enums.TipoAgendamento;
import util.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD do agendamento polimórfico (tabela base + consulta_clinica/exame/vacinacao).
 * A exclusividade do horário (RN01) é garantida fisicamente por UNIQUE(horario_id).
 */
public class AgendamentoDAO extends BaseDAO {

    private static final String SELECT = "SELECT a.id, a.horario_id, a.paciente_id, a.tipo_agendamento, a.status, "
            + "a.confirmado_em, a.data_criacao, h.hora_inicio, h.hora_fim, ad.data, ad.profissional_id, ad.unidade_id, "
            + "c.motivo, c.retorno, e.tipo_exame, e.orientacao_preparo, v.imunobiologico, v.dose "
            + "FROM agendamento a "
            + "JOIN horario h ON h.id = a.horario_id "
            + "JOIN agenda_diaria ad ON ad.id = h.agenda_id "
            + "LEFT JOIN consulta_clinica c ON c.agendamento_id = a.id "
            + "LEFT JOIN exame e ON e.agendamento_id = a.id "
            + "LEFT JOIN vacinacao v ON v.agendamento_id = a.id ";

    public AgendamentoDAO() { super(); }

    public AgendamentoDAO(Connection c) { super(c); }

    // ---------------- CREATE ----------------
    public Agendamento inserir(Agendamento a) {
        try {
            executarAtomico(c -> {
                long id = inserirRetornandoId(c,
                        "INSERT INTO agendamento (horario_id, paciente_id, tipo_agendamento, status, confirmado_em, data_criacao) VALUES (?,?,?,?,?,?)",
                        ps -> { ps.setLong(1, a.getHorarioId()); ps.setLong(2, a.getPacienteId());
                                ps.setString(3, a.tipo().name()); ps.setString(4, a.getStatus().name());
                                ps.setString(5, Sql.dataHora(a.getConfirmadoEm()));
                                ps.setString(6, Sql.dataHora(a.getDataCriacao())); });
                a.setId(id);
                inserirEspecializacao(c, a);
                return id;
            });
            return a;
        } catch (PersistenciaException e) {
            if (e.isViolacaoUnicidade()) {
                throw new RegraNegocioException("RN01", "O horário informado já possui um agendamento ativo.");
            }
            throw e;
        }
    }

    private void inserirEspecializacao(Connection c, Agendamento a) throws SQLException {
        if (a instanceof ConsultaClinica x) {
            atualizar(c, "INSERT INTO consulta_clinica (agendamento_id, motivo, retorno) VALUES (?,?,?)",
                    ps -> { ps.setLong(1, x.getId()); ps.setString(2, x.getMotivo()); ps.setBoolean(3, x.isRetorno()); });
        } else if (a instanceof Exame x) {
            atualizar(c, "INSERT INTO exame (agendamento_id, tipo_exame, orientacao_preparo) VALUES (?,?,?)",
                    ps -> { ps.setLong(1, x.getId()); ps.setString(2, x.getTipoExame()); ps.setString(3, x.getOrientacaoPreparo()); });
        } else if (a instanceof Vacinacao x) {
            atualizar(c, "INSERT INTO vacinacao (agendamento_id, imunobiologico, dose) VALUES (?,?,?)",
                    ps -> { ps.setLong(1, x.getId()); ps.setString(2, x.getImunobiologico()); ps.setString(3, x.getDose()); });
        }
    }

    // ---------------- READ ----------------
    public Optional<Agendamento> buscarPorId(long id) {
        return listarComFiltro("WHERE a.id = ?", ps -> ps.setLong(1, id)).stream().findFirst();
    }

    public List<Agendamento> listarPorPaciente(long pacienteId) {
        return listarComFiltro("WHERE a.paciente_id = ? ORDER BY ad.data DESC, h.hora_inicio DESC",
                ps -> ps.setLong(1, pacienteId));
    }

    public List<Agendamento> listarPorStatus(StatusAgendamento status) {
        return listarComFiltro("WHERE a.status = ? ORDER BY ad.data, h.hora_inicio", ps -> ps.setString(1, status.name()));
    }

    public List<Agendamento> listar(Long pacienteId, Long profissionalId, StatusAgendamento status, LocalDate data) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (pacienteId != null) { where.append(" AND a.paciente_id=?"); params.add(pacienteId); }
        if (profissionalId != null) { where.append(" AND ad.profissional_id=?"); params.add(profissionalId); }
        if (status != null) { where.append(" AND a.status=?"); params.add(status.name()); }
        if (data != null) { where.append(" AND ad.data=?"); params.add(Sql.data(data)); }
        where.append(" ORDER BY ad.data, h.hora_inicio");
        return listarComFiltro(where.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
        });
    }

    private List<Agendamento> listarComFiltro(String clausula, Binder binder) {
        return executar(c -> {
            List<Agendamento> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECT + clausula)) {
                binder.vincular(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
        });
    }

    /** Mapeamento objeto-relacional: o tipo gravado decide a subclasse concreta a instanciar. */
    private static Agendamento mapear(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long horarioId = rs.getLong("horario_id");
        long pacienteId = rs.getLong("paciente_id");
        StatusAgendamento status = StatusAgendamento.valueOf(rs.getString("status"));
        LocalDateTime confirmado = Sql.lerDataHora(rs.getString("confirmado_em"));
        LocalDateTime criacao = Sql.lerDataHora(rs.getString("data_criacao"));
        Agendamento a = switch (TipoAgendamento.valueOf(rs.getString("tipo_agendamento"))) {
            case CONSULTA -> new ConsultaClinica(id, horarioId, pacienteId, status, confirmado, criacao,
                    rs.getString("motivo"), rs.getBoolean("retorno"));
            case EXAME -> new Exame(id, horarioId, pacienteId, status, confirmado, criacao,
                    rs.getString("tipo_exame"), rs.getString("orientacao_preparo"));
            case VACINA -> new Vacinacao(id, horarioId, pacienteId, status, confirmado, criacao,
                    rs.getString("imunobiologico"), rs.getString("dose"));
        };
        a.definirContextoAtendimento(Sql.lerData(rs.getString("data")), Sql.lerHora(rs.getString("hora_inicio")),
                Sql.lerHora(rs.getString("hora_fim")), rs.getLong("profissional_id"), rs.getLong("unidade_id"));
        return a;
    }

    // ---------------- UPDATE ----------------
    /** Atualiza status/confirmação e os atributos específicos do procedimento. */
    public void atualizar(Agendamento a) {
        int n = executarAtomico(c -> {
            int linhas = atualizar(c, "UPDATE agendamento SET status=?, confirmado_em=? WHERE id=?",
                    ps -> { ps.setString(1, a.getStatus().name()); ps.setString(2, Sql.dataHora(a.getConfirmadoEm()));
                            ps.setLong(3, a.getId()); });
            if (linhas == 1) atualizarEspecializacao(c, a);
            return linhas;
        });
        if (n == 0) throw RegraNegocioException.naoEncontrado("Agendamento " + a.getId() + " não encontrado.");
    }

    private void atualizarEspecializacao(Connection c, Agendamento a) throws SQLException {
        if (a instanceof ConsultaClinica x) {
            atualizar(c, "UPDATE consulta_clinica SET motivo=?, retorno=? WHERE agendamento_id=?",
                    ps -> { ps.setString(1, x.getMotivo()); ps.setBoolean(2, x.isRetorno()); ps.setLong(3, x.getId()); });
        } else if (a instanceof Exame x) {
            atualizar(c, "UPDATE exame SET tipo_exame=?, orientacao_preparo=? WHERE agendamento_id=?",
                    ps -> { ps.setString(1, x.getTipoExame()); ps.setString(2, x.getOrientacaoPreparo()); ps.setLong(3, x.getId()); });
        } else if (a instanceof Vacinacao x) {
            atualizar(c, "UPDATE vacinacao SET imunobiologico=?, dose=? WHERE agendamento_id=?",
                    ps -> { ps.setString(1, x.getImunobiologico()); ps.setString(2, x.getDose()); ps.setLong(3, x.getId()); });
        }
    }

    // ---------------- DELETE ----------------
    /** Remove o agendamento (ON DELETE CASCADE apaga a linha de especialização). */
    public boolean excluir(long id) {
        return executar(c -> atualizar(c, "DELETE FROM agendamento WHERE id=?", ps -> ps.setLong(1, id))) > 0;
    }
}
