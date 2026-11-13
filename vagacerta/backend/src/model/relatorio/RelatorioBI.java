package model.relatorio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indicadores consolidados da F6 (Business Intelligence) para o gestor da unidade. */
public class RelatorioBI {
    public record LinhaProfissional(long profissionalId, String nome, String especialidade,
                                    int horarios, int reservados, int realizados, int faltas) {
        public double taxaAbsenteismo() {
            int atendiveis = realizados + faltas;
            return atendiveis == 0 ? 0.0 : 100.0 * faltas / atendiveis;
        }
    }

    public LocalDate de;
    public LocalDate ate;
    public int totalHorarios;
    public int reservados;
    public int livres;
    public int bloqueados;
    public int realizados;
    public int faltas;
    public int autoliberadas;
    public int cancelamentosPaciente;
    public int cancelamentosGestao;
    public int vagasReaproveitadas;
    public final Map<String, Integer> agendamentosPorTipo = new LinkedHashMap<>();
    public final List<LinhaProfissional> porProfissional = new ArrayList<>();

    /** Ocupação = horários reservados / horários disponíveis (exclui bloqueados). */
    public double taxaOcupacao() {
        int base = totalHorarios - bloqueados;
        return base <= 0 ? 0.0 : 100.0 * reservados / base;
    }

    public double taxaOciosidade() {
        return totalHorarios - bloqueados <= 0 ? 0.0 : 100.0 - taxaOcupacao();
    }

    /** Absenteísmo = faltas / (faltas + atendimentos realizados). */
    public double taxaAbsenteismo() {
        int base = faltas + realizados;
        return base == 0 ? 0.0 : 100.0 * faltas / base;
    }

    /** Parcela das vagas devolvidas à fila que foi reocupada por outro paciente. */
    public double taxaReaproveitamento() {
        int liberadas = autoliberadas + cancelamentosPaciente + cancelamentosGestao;
        return liberadas == 0 ? 0.0 : 100.0 * vagasReaproveitadas / liberadas;
    }

    private static double arredondar(double v) { return Math.round(v * 10.0) / 10.0; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("de", de.toString());
        m.put("ate", ate.toString());
        m.put("totalHorarios", totalHorarios);
        m.put("reservados", reservados);
        m.put("livres", livres);
        m.put("bloqueados", bloqueados);
        m.put("realizados", realizados);
        m.put("faltas", faltas);
        m.put("autoliberadas", autoliberadas);
        m.put("cancelamentosPaciente", cancelamentosPaciente);
        m.put("cancelamentosGestao", cancelamentosGestao);
        m.put("vagasReaproveitadas", vagasReaproveitadas);
        m.put("taxaOcupacao", arredondar(taxaOcupacao()));
        m.put("taxaOciosidade", arredondar(taxaOciosidade()));
        m.put("taxaAbsenteismo", arredondar(taxaAbsenteismo()));
        m.put("taxaReaproveitamento", arredondar(taxaReaproveitamento()));
        m.put("agendamentosPorTipo", new LinkedHashMap<>(agendamentosPorTipo));
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (LinhaProfissional l : porProfissional) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("profissionalId", l.profissionalId());
            x.put("nome", l.nome());
            x.put("especialidade", l.especialidade());
            x.put("horarios", l.horarios());
            x.put("reservados", l.reservados());
            x.put("realizados", l.realizados());
            x.put("faltas", l.faltas());
            x.put("taxaAbsenteismo", arredondar(l.taxaAbsenteismo()));
            linhas.add(x);
        }
        m.put("porProfissional", linhas);
        return m;
    }

    public String formatar() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== RELATÓRIO DE BI (%s a %s) ===%n", de, ate));
        sb.append(String.format("Horários na grade ........ %d (reservados %d | livres %d | bloqueados %d)%n",
                totalHorarios, reservados, livres, bloqueados));
        sb.append(String.format("Taxa de ocupação ......... %.1f%%   (ociosidade %.1f%%)%n", taxaOcupacao(), taxaOciosidade()));
        sb.append(String.format("Atendimentos realizados .. %d%n", realizados));
        sb.append(String.format("Faltas registradas ....... %d   (absenteísmo %.1f%%)%n", faltas, taxaAbsenteismo()));
        sb.append(String.format("Vagas autoliberadas ...... %d   | cancelamentos: paciente %d, gestão %d%n",
                autoliberadas, cancelamentosPaciente, cancelamentosGestao));
        sb.append(String.format("Vagas reaproveitadas ..... %d   (%.1f%% das vagas devolvidas)%n",
                vagasReaproveitadas, taxaReaproveitamento()));
        sb.append("Agendamentos por tipo .... ").append(agendamentosPorTipo).append('\n');
        for (LinhaProfissional l : porProfissional) {
            sb.append(String.format("  - %s (%s): %d horários, %d reservados, %d realizados, %d faltas (%.1f%%)%n",
                    l.nome(), l.especialidade(), l.horarios(), l.reservados(), l.realizados(), l.faltas(), l.taxaAbsenteismo()));
        }
        return sb.toString();
    }
}
