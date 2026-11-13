package model.agendamento;

import model.enums.StatusAgendamento;
import model.enums.TipoAgendamento;
import util.Validador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConsultaClinica extends Agendamento {
    public static final int DURACAO_MINUTOS = 20;
    public static final int JANELA_HORAS = 24;

    private String motivo;
    private boolean retorno;

    public ConsultaClinica(Long id, long horarioId, long pacienteId, StatusAgendamento status,
                           LocalDateTime confirmadoEm, LocalDateTime dataCriacao, String motivo, boolean retorno) {
        super(id, horarioId, pacienteId, status, confirmadoEm, dataCriacao);
        setMotivo(motivo);
        this.retorno = retorno;
    }

    public ConsultaClinica(long horarioId, long pacienteId, String motivo, boolean retorno) {
        this(null, horarioId, pacienteId, null, null, null, motivo, retorno);
    }

    @Override public TipoAgendamento tipo() { return TipoAgendamento.CONSULTA; }

    @Override public int janelaConfirmacaoHoras() { return JANELA_HORAS; }

    @Override public int duracaoMinutos() { return DURACAO_MINUTOS; }

    @Override public boolean exigePreparo() { return false; }

    @Override
    public String gerarInstrucoesAtendimento() {
        return "Compareça 15 minutos antes com documento e Cartão SUS. "
                + "Confirme a presença até 24 horas antes do horário, ou a vaga será liberada.";
    }

    @Override
    public boolean deveSerAutoliberado(LocalDate data, LocalTime hora, LocalDateTime agora) {
        return prazoVencido(data, hora, agora);
    }

    @Override
    public Map<String, Object> detalhes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("motivo", motivo);
        m.put("retorno", retorno);
        return m;
    }

    @Override
    public void aplicarDetalhes(Map<String, Object> campos) {
        if (campos.get("motivo") != null) setMotivo(String.valueOf(campos.get("motivo")));
        if (campos.get("retorno") != null) setRetorno(Boolean.parseBoolean(String.valueOf(campos.get("retorno"))));
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String m) { this.motivo = Validador.exigirTexto(m, "motivo da consulta", 160); }
    public boolean isRetorno() { return retorno; }
    public void setRetorno(boolean retorno) { this.retorno = retorno; }
}
