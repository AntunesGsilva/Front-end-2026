package dao;

import model.agendamento.RegistroFalta;
import util.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistência do histórico de faltas (RN03). */
public class RegistroFaltaDAO extends BaseDAO {

    public RegistroFaltaDAO() { super(); }

    public RegistroFaltaDAO(Connection c) { super(c); }

    public RegistroFalta inserir(RegistroFalta f) {
        long id = executar(c -> inserirRetornandoId(c,
                "INSERT INTO registro_falta (paciente_id, agendamento_id, data_falta, justificada) VALUES (?,?,?,?)",
                ps -> { ps.setLong(1, f.getPacienteId()); ps.setLong(2, f.getAgendamentoId());
                        ps.setString(3, Sql.dataHora(f.getDataFalta())); ps.setBoolean(4, f.isJustificada()); }));
        f.setId(id);
        return f;
    }

    public Optional<RegistroFalta> buscarPorId(long id) {
        return consultar("WHERE id=?", ps -> ps.setLong(1, id)).stream().findFirst();
    }

    public List<RegistroFalta> listarPorPaciente(long pacienteId) {
        return consultar("WHERE paciente_id=? ORDER BY data_falta DESC", ps -> ps.setLong(1, pacienteId));
    }

    /** Faltas não justificadas dentro da janela de apuração (RN03). */
    public List<RegistroFalta> listarNaoJustificadasDesde(long pacienteId, LocalDateTime desde) {
        return consultar("WHERE paciente_id=? AND justificada=? AND data_falta>=? ORDER BY data_falta",
                ps -> { ps.setLong(1, pacienteId); ps.setBoolean(2, false); ps.setString(3, Sql.dataHora(desde)); });
    }

    public void atualizarJustificativa(RegistroFalta f) {
        executar(c -> atualizar(c,
                "UPDATE registro_falta SET justificada=?, motivo_justificativa=?, justificado_em=?, justificado_por=? WHERE id=?",
                ps -> { ps.setBoolean(1, f.isJustificada()); ps.setString(2, f.getMotivoJustificativa());
                        ps.setString(3, Sql.dataHora(f.getJustificadoEm()));
                        if (f.getJustificadoPor() == null) ps.setNull(4, java.sql.Types.BIGINT);
                        else ps.setLong(4, f.getJustificadoPor());
                        ps.setLong(5, f.getId()); }));
    }

    private List<RegistroFalta> consultar(String clausula, Binder binder) {
        return executar(c -> {
            List<RegistroFalta> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM registro_falta " + clausula)) {
                binder.vincular(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
        });
    }

    private static RegistroFalta mapear(ResultSet rs) throws SQLException {
        long gestor = rs.getLong("justificado_por");
        Long justificadoPor = rs.wasNull() ? null : gestor;
        return new RegistroFalta(rs.getLong("id"), rs.getLong("paciente_id"), rs.getLong("agendamento_id"),
                Sql.lerDataHora(rs.getString("data_falta")), rs.getBoolean("justificada"),
                rs.getString("motivo_justificativa"), Sql.lerDataHora(rs.getString("justificado_em")), justificadoPor);
    }
}
