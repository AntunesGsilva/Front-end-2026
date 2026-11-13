package model.agendamento;

import model.enums.StatusAgendamento;
import model.enums.TipoAgendamento;
import util.Validador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class Vacinacao extends Agendamento {
    public static final int DURACAO_MINUTOS = 5;
    public static final int JANELA_HORAS = 0;

    private String imunobiologico;
    private String dose;

    public Vacinacao(Long id, long horarioId, long pacienteId, StatusAgendamento status,
                     LocalDateTime confirmadoEm, LocalDateTime dataCriacao, String imunobiologico, String dose) {
        super(id, horarioId, pacienteId, status, confirmadoEm, dataCriacao);
        setImunobiologico(imunobiologico);
        setDose(dose);
    }

    public Vacinacao(long horarioId, long pacienteId, String imunobiologico, String dose) {
        this(null, horarioId, pacienteId, null, null, null, imunobiologico, dose);
    }

    @Override public TipoAgendamento tipo() { return TipoAgendamento.VACINA; }

    /** 0h: vacinação é isenta de confirmação prévia e nunca sofre descarte por timeout. */
    @Override public int janelaConfirmacaoHoras() { return JANELA_HORAS; }

    @Override public int duracaoMinutos() { return DURACAO_MINUTOS; }

    @Override public boolean exigePreparo() { return false; }

    @Override
    public String gerarInstrucoesAtendimento() {
        return "Atendimento direto na sala de vacinas. Leve o cartão de vacinação e um documento com foto.";
    }

    @Override
    public boolean deveSerAutoliberado(LocalDate data, LocalTime hora, LocalDateTime agora) {
        return false;
    }

    @Override
    public Map<String, Object> detalhes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imunobiologico", imunobiologico);
        m.put("dose", dose);
        return m;
    }

    @Override
    public void aplicarDetalhes(Map<String, Object> campos) {
        if (campos.get("imunobiologico") != null) setImunobiologico(String.valueOf(campos.get("imunobiologico")));
        if (campos.get("dose") != null) setDose(String.valueOf(campos.get("dose")));
    }

    public String getImunobiologico() { return imunobiologico; }
    public void setImunobiologico(String i) { this.imunobiologico = Validador.exigirTexto(i, "imunobiológico", 60); }
    public String getDose() { return dose; }
    public void setDose(String d) { this.dose = Validador.exigirTexto(d, "dose", 20); }
}
