package dao;

import model.relatorio.RelatorioBI;
import util.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Consultas agregadas (SQL portável MySQL/SQLite) que alimentam os indicadores de BI. */
public class RelatorioDAO extends BaseDAO {

    public RelatorioDAO() { super(); }

    public RelatorioDAO(Connection c) { super(c); }

    public RelatorioBI consolidar(LocalDate de, LocalDate ate) {
        RelatorioBI r = new RelatorioBI();
        r.de = de;
        r.ate = ate;
        String ini = Sql.data(de);
        String fim = Sql.data(ate);
        String liberadoIni = ini + " 00:00:00";
        String liberadoFim = Sql.data(ate.plusDays(1)) + " 00:00:00";

        executar(c -> {
            // Ocupação da grade
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(h.id) AS total, "
                    + "COALESCE(SUM(CASE WHEN h.status='RESERVADO' THEN 1 ELSE 0 END),0) AS res, "
                    + "COALESCE(SUM(CASE WHEN h.status='LIVRE' THEN 1 ELSE 0 END),0) AS liv, "
                    + "COALESCE(SUM(CASE WHEN h.status='BLOQUEADO' THEN 1 ELSE 0 END),0) AS blo "
                    + "FROM horario h JOIN agenda_diaria a ON a.id = h.agenda_id WHERE a.data BETWEEN ? AND ?")) {
                ps.setString(1, ini); ps.setString(2, fim);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.totalHorarios = rs.getInt("total"); r.reservados = rs.getInt("res");
                        r.livres = rs.getInt("liv"); r.bloqueados = rs.getInt("blo");
                    }
                }
            }
            // Atendimentos realizados / faltas
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ag.status, COUNT(*) AS n FROM agendamento ag JOIN horario h ON h.id = ag.horario_id "
                    + "JOIN agenda_diaria a ON a.id = h.agenda_id WHERE a.data BETWEEN ? AND ? GROUP BY ag.status")) {
                ps.setString(1, ini); ps.setString(2, fim);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String s = rs.getString("status");
                        if ("REALIZADO".equals(s)) r.realizados = rs.getInt("n");
                        else if ("FALTA".equals(s)) r.faltas = rs.getInt("n");
                    }
                }
            }
            // Agendamentos por tipo
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ag.tipo_agendamento, COUNT(*) AS n FROM agendamento ag JOIN horario h ON h.id = ag.horario_id "
                    + "JOIN agenda_diaria a ON a.id = h.agenda_id WHERE a.data BETWEEN ? AND ? "
                    + "GROUP BY ag.tipo_agendamento ORDER BY ag.tipo_agendamento")) {
                ps.setString(1, ini); ps.setString(2, fim);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.agendamentosPorTipo.put(rs.getString(1), rs.getInt(2));
                }
            }
            // Liberações de vaga no período
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT motivo, COUNT(*) AS n FROM liberacao_vaga WHERE liberado_em >= ? AND liberado_em < ? GROUP BY motivo")) {
                ps.setString(1, liberadoIni); ps.setString(2, liberadoFim);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        switch (rs.getString("motivo")) {
                            case "AUTOLIBERACAO" -> r.autoliberadas = rs.getInt("n");
                            case "CANCELAMENTO_PACIENTE" -> r.cancelamentosPaciente = rs.getInt("n");
                            case "CANCELAMENTO_GESTAO" -> r.cancelamentosGestao = rs.getInt("n");
                            default -> { }
                        }
                    }
                }
            }
            // Vagas devolvidas que foram reocupadas depois da liberação
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM liberacao_vaga l WHERE l.liberado_em >= ? AND l.liberado_em < ? "
                    + "AND EXISTS (SELECT 1 FROM agendamento ag WHERE ag.horario_id = l.horario_id AND ag.data_criacao >= l.liberado_em)")) {
                ps.setString(1, liberadoIni); ps.setString(2, liberadoFim);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) r.vagasReaproveitadas = rs.getInt(1);
                }
            }
            // Histórico por profissional
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u.id, u.nome, pr.especialidade, COUNT(h.id) AS total, "
                    + "COALESCE(SUM(CASE WHEN h.status='RESERVADO' THEN 1 ELSE 0 END),0) AS res, "
                    + "COALESCE(SUM(CASE WHEN ag.status='REALIZADO' THEN 1 ELSE 0 END),0) AS rea, "
                    + "COALESCE(SUM(CASE WHEN ag.status='FALTA' THEN 1 ELSE 0 END),0) AS fal "
                    + "FROM agenda_diaria a JOIN usuario u ON u.id = a.profissional_id "
                    + "JOIN profissional_saude pr ON pr.usuario_id = u.id "
                    + "JOIN horario h ON h.agenda_id = a.id LEFT JOIN agendamento ag ON ag.horario_id = h.id "
                    + "WHERE a.data BETWEEN ? AND ? GROUP BY u.id, u.nome, pr.especialidade ORDER BY u.nome")) {
                ps.setString(1, ini); ps.setString(2, fim);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        r.porProfissional.add(new RelatorioBI.LinhaProfissional(rs.getLong("id"), rs.getString("nome"),
                                rs.getString("especialidade"), rs.getInt("total"), rs.getInt("res"),
                                rs.getInt("rea"), rs.getInt("fal")));
                    }
                }
            }
            return null;
        });
        return r;
    }
}
