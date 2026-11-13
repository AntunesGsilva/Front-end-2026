package dao;

import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.Horario;
import model.agenda.HorarioDisponivel;
import model.enums.StatusHorario;
import util.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD com persistência atômica da composição AgendaDiaria 1:N Horario. */
public class AgendaDiariaDAO extends BaseDAO {

    public AgendaDiariaDAO() { super(); }

    public AgendaDiariaDAO(Connection c) { super(c); }

    // ---------------- CREATE ----------------
    /** Grava a agenda e todos os seus horários na mesma transação; devolve a agenda reidratada (com ids). */
    public AgendaDiaria inserir(AgendaDiaria agenda) {
        try {
            long id = executarAtomico(c -> {
                long agendaId = inserirRetornandoId(c,
                        "INSERT INTO agenda_diaria (unidade_id, profissional_id, data, turno_inicio, turno_fim, bloqueada) VALUES (?,?,?,?,?,?)",
                        ps -> { ps.setLong(1, agenda.getUnidadeId()); ps.setLong(2, agenda.getProfissionalId());
                                ps.setString(3, Sql.data(agenda.getData())); ps.setString(4, Sql.hora(agenda.getTurnoInicio()));
                                ps.setString(5, Sql.hora(agenda.getTurnoFim())); ps.setBoolean(6, agenda.isBloqueada()); });
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO horario (agenda_id, hora_inicio, hora_fim, status) VALUES (?,?,?,?)")) {
                    for (Horario h : agenda.getHorarios()) {
                        ps.setLong(1, agendaId);
                        ps.setString(2, Sql.hora(h.getHoraInicio()));
                        ps.setString(3, Sql.hora(h.getHoraFim()));
                        ps.setString(4, h.getStatus().name());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return agendaId;
            });
            agenda.setId(id);
            return buscarPorId(id).orElseThrow();
        } catch (PersistenciaException e) {
            if (e.isViolacaoUnicidade()) {
                throw new RegraNegocioException("RN04", "O profissional já possui agenda cadastrada para esta data.");
            }
            throw new RegraNegocioException("VALIDACAO", "Unidade ou profissional inexistente para esta agenda.");
        }
    }

    // ---------------- READ ----------------
    public Optional<AgendaDiaria> buscarPorId(long id) {
        return executar(c -> carregar(c, "SELECT * FROM agenda_diaria WHERE id=?", ps -> ps.setLong(1, id)).stream().findFirst());
    }

    public Optional<AgendaDiaria> buscarPorHorario(long horarioId) {
        return executar(c -> carregar(c,
                "SELECT a.* FROM agenda_diaria a JOIN horario h ON h.agenda_id = a.id WHERE h.id=?",
                ps -> ps.setLong(1, horarioId)).stream().findFirst());
    }

    public List<AgendaDiaria> listar(Long unidadeId, Long profissionalId, LocalDate de, LocalDate ate) {
        StringBuilder sql = new StringBuilder("SELECT * FROM agenda_diaria WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (unidadeId != null) { sql.append(" AND unidade_id=?"); params.add(unidadeId); }
        if (profissionalId != null) { sql.append(" AND profissional_id=?"); params.add(profissionalId); }
        if (de != null) { sql.append(" AND data>=?"); params.add(Sql.data(de)); }
        if (ate != null) { sql.append(" AND data<=?"); params.add(Sql.data(ate)); }
        sql.append(" ORDER BY data, turno_inicio");
        return executar(c -> carregar(c, sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
        }));
    }

    private List<AgendaDiaria> carregar(Connection c, String sql, Binder binder) throws SQLException {
        List<AgendaDiaria> agendas = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.vincular(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    agendas.add(new AgendaDiaria(rs.getLong("id"), rs.getLong("unidade_id"), rs.getLong("profissional_id"),
                            Sql.lerData(rs.getString("data")), Sql.lerHora(rs.getString("turno_inicio")),
                            Sql.lerHora(rs.getString("turno_fim")), rs.getBoolean("bloqueada")));
                }
            }
        }
        for (AgendaDiaria a : agendas) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, hora_inicio, hora_fim, status FROM horario WHERE agenda_id=? ORDER BY hora_inicio")) {
                ps.setLong(1, a.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        a.restaurarHorario(rs.getLong("id"), Sql.lerHora(rs.getString("hora_inicio")),
                                Sql.lerHora(rs.getString("hora_fim")), StatusHorario.valueOf(rs.getString("status")));
                    }
                }
            }
        }
        return agendas;
    }

    /** Horários LIVRES e futuros (em relação a {@code agora}), opcionalmente filtrados por duração do procedimento. */
    public List<HorarioDisponivel> listarHorariosLivres(Long unidadeId, Long profissionalId, LocalDate data,
                                                        Integer duracaoMinutos, LocalDateTime agora) {
        StringBuilder sql = new StringBuilder(
                "SELECT h.id AS horario_id, a.id AS agenda_id, a.data, h.hora_inicio, h.hora_fim, "
                + "u.id AS unidade_id, u.nome AS unidade_nome, p.id AS prof_id, p.nome AS prof_nome, pr.especialidade "
                + "FROM horario h JOIN agenda_diaria a ON a.id = h.agenda_id "
                + "JOIN unidade_saude u ON u.id = a.unidade_id "
                + "JOIN usuario p ON p.id = a.profissional_id "
                + "JOIN profissional_saude pr ON pr.usuario_id = p.id "
                + "WHERE h.status = 'LIVRE' AND a.bloqueada = ? AND a.data >= ?");
        List<Object> params = new ArrayList<>();
        params.add(false);
        params.add(Sql.data(agora.toLocalDate()));
        if (unidadeId != null) { sql.append(" AND a.unidade_id=?"); params.add(unidadeId); }
        if (profissionalId != null) { sql.append(" AND a.profissional_id=?"); params.add(profissionalId); }
        if (data != null) { sql.append(" AND a.data=?"); params.add(Sql.data(data)); }
        sql.append(" ORDER BY a.data, h.hora_inicio");
        return executar(c -> {
            List<HorarioDisponivel> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate d = Sql.lerData(rs.getString("data"));
                        LocalTime ini = Sql.lerHora(rs.getString("hora_inicio"));
                        LocalTime fim = Sql.lerHora(rs.getString("hora_fim"));
                        int dur = (int) Duration.between(ini, fim).toMinutes();
                        if (!LocalDateTime.of(d, ini).isAfter(agora)) continue;
                        if (duracaoMinutos != null && duracaoMinutos != dur) continue;
                        lista.add(new HorarioDisponivel(rs.getLong("horario_id"), rs.getLong("agenda_id"), d, ini, fim, dur,
                                rs.getLong("unidade_id"), rs.getString("unidade_nome"), rs.getLong("prof_id"),
                                rs.getString("prof_nome"), rs.getString("especialidade")));
                    }
                }
            }
            return lista;
        });
    }

    // ---------------- Estado do horário (RN01) ----------------
    /** Compare-and-set atômico LIVRE -> RESERVADO. Retorna false se outro agendamento chegou primeiro. */
    public boolean reservarHorario(long horarioId) {
        return executar(c -> atualizar(c, "UPDATE horario SET status='RESERVADO' WHERE id=? AND status='LIVRE'",
                ps -> ps.setLong(1, horarioId))) == 1;
    }

    /** RESERVADO -> LIVRE imediato. */
    public boolean liberarHorario(long horarioId) {
        return executar(c -> atualizar(c, "UPDATE horario SET status='LIVRE' WHERE id=? AND status='RESERVADO'",
                ps -> ps.setLong(1, horarioId))) == 1;
    }

    // ---------------- UPDATE ----------------
    /** Bloqueia/desbloqueia a agenda inteira, propagando para os horários ainda livres. */
    public void atualizarBloqueio(long agendaId, boolean bloqueada) {
        int n = executarAtomico(c -> {
            int linhas = atualizar(c, "UPDATE agenda_diaria SET bloqueada=? WHERE id=?",
                    ps -> { ps.setBoolean(1, bloqueada); ps.setLong(2, agendaId); });
            if (linhas == 1) {
                String de = bloqueada ? "LIVRE" : "BLOQUEADO";
                String para = bloqueada ? "BLOQUEADO" : "LIVRE";
                atualizar(c, "UPDATE horario SET status=? WHERE agenda_id=? AND status=?",
                        ps -> { ps.setString(1, para); ps.setLong(2, agendaId); ps.setString(3, de); });
            }
            return linhas;
        });
        if (n == 0) throw RegraNegocioException.naoEncontrado("Agenda " + agendaId + " não encontrada.");
    }

    // ---------------- DELETE ----------------
    /** Remove a agenda e (ON DELETE CASCADE) seus horários. Falha se algum horário tiver agendamento. */
    public boolean excluir(long agendaId) {
        try {
            return executar(c -> atualizar(c, "DELETE FROM agenda_diaria WHERE id=?", ps -> ps.setLong(1, agendaId))) > 0;
        } catch (PersistenciaException e) {
            throw new RegraNegocioException("RN01", "Não é possível excluir: há agendamentos nos horários desta agenda.");
        }
    }
}
