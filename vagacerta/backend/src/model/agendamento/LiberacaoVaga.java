package model.agendamento;

import model.enums.TipoAgendamento;
import java.time.LocalDateTime;

/**
 * Evento de devolução de uma vaga à fila pública (autoliberação RN02 ou cancelamento).
 * Existe porque a linha de agendamento é removida na liberação (UNIQUE(horario_id) precisa
 * permitir novo agendamento do mesmo horário); este registro alimenta os indicadores de BI.
 */
public class LiberacaoVaga {
    public enum Motivo { AUTOLIBERACAO, CANCELAMENTO_PACIENTE, CANCELAMENTO_GESTAO }

    private Long id;
    private final long horarioId;
    private final long pacienteId;
    private final TipoAgendamento tipo;
    private final Motivo motivo;
    private final LocalDateTime liberadoEm;

    public LiberacaoVaga(Long id, long horarioId, long pacienteId, TipoAgendamento tipo,
                         Motivo motivo, LocalDateTime liberadoEm) {
        this.id = id;
        this.horarioId = horarioId;
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.motivo = motivo;
        this.liberadoEm = liberadoEm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getHorarioId() { return horarioId; }
    public long getPacienteId() { return pacienteId; }
    public TipoAgendamento getTipo() { return tipo; }
    public Motivo getMotivo() { return motivo; }
    public LocalDateTime getLiberadoEm() { return liberadoEm; }
}
