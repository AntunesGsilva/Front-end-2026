package dao;

import model.agendamento.LiberacaoVaga;
import util.Sql;
import java.sql.Connection;

/** Log de devolução de vagas à fila pública (alimenta o BI). */
public class LiberacaoVagaDAO extends BaseDAO {

    public LiberacaoVagaDAO() { super(); }

    public LiberacaoVagaDAO(Connection c) { super(c); }

    public LiberacaoVaga inserir(LiberacaoVaga l) {
        long id = executar(c -> inserirRetornandoId(c,
                "INSERT INTO liberacao_vaga (horario_id, paciente_id, tipo_agendamento, motivo, liberado_em) VALUES (?,?,?,?,?)",
                ps -> { ps.setLong(1, l.getHorarioId()); ps.setLong(2, l.getPacienteId());
                        ps.setString(3, l.getTipo().name()); ps.setString(4, l.getMotivo().name());
                        ps.setString(5, Sql.dataHora(l.getLiberadoEm())); }));
        l.setId(id);
        return l;
    }
}
