package model.agendamento;

import model.enums.StatusAgendamento;
import model.enums.TipoAgendamento;
import util.Validador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class Exame extends Agendamento {
    public static final int DURACAO_MINUTOS = 30;
    public static final int JANELA_HORAS = 48;

    private String tipoExame;
    private String orientacaoPreparo;

    public Exame(Long id, long horarioId, long pacienteId, StatusAgendamento status,
                 LocalDateTime confirmadoEm, LocalDateTime dataCriacao, String tipoExame, String orientacaoPreparo) {
        super(id, horarioId, pacienteId, status, confirmadoEm, dataCriacao);
        setTipoExame(tipoExame);
        setOrientacaoPreparo(orientacaoPreparo);
    }

    public Exame(long horarioId, long pacienteId, String tipoExame, String orientacaoPreparo) {
        this(null, horarioId, pacienteId, null, null, null, tipoExame, orientacaoPreparo);
    }

    @Override public TipoAgendamento tipo() { return TipoAgendamento.EXAME; }

    /** 48h: preparo prévio (jejum, interrupção de fármacos) e perda irreversível de insumos. */
    @Override public int janelaConfirmacaoHoras() { return JANELA_HORAS; }

    @Override public int duracaoMinutos() { return DURACAO_MINUTOS; }

    @Override public boolean exigePreparo() { return true; }

    @Override
    public String gerarInstrucoesAtendimento() {
        return "Preparo obrigatório: " + orientacaoPreparo
                + " Confirme a presença até 48 horas antes, ou a vaga será liberada.";
    }

    @Override
    public boolean deveSerAutoliberado(LocalDate data, LocalTime hora, LocalDateTime agora) {
        return prazoVencido(data, hora, agora);
    }

    @Override
    public Map<String, Object> detalhes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipoExame", tipoExame);
        m.put("orientacaoPreparo", orientacaoPreparo);
        return m;
    }

    @Override
    public void aplicarDetalhes(Map<String, Object> campos) {
        if (campos.get("tipoExame") != null) setTipoExame(String.valueOf(campos.get("tipoExame")));
        if (campos.get("orientacaoPreparo") != null) setOrientacaoPreparo(String.valueOf(campos.get("orientacaoPreparo")));
    }

    public String getTipoExame() { return tipoExame; }
    public void setTipoExame(String t) { this.tipoExame = Validador.exigirTexto(t, "tipo de exame", 60); }
    public String getOrientacaoPreparo() { return orientacaoPreparo; }
    public void setOrientacaoPreparo(String o) { this.orientacaoPreparo = Validador.exigirTexto(o, "orientação de preparo", 255); }
}
