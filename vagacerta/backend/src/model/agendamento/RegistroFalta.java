package model.agendamento;

import java.time.LocalDateTime;

/** Histórico estruturado de faltas (dor D4 / F5): base da regra RN03 e do desbloqueio presencial. */
public class RegistroFalta {
    private Long id;
    private final long pacienteId;
    private final long agendamentoId;
    private final LocalDateTime dataFalta;
    private boolean justificada;
    private String motivoJustificativa;
    private LocalDateTime justificadoEm;
    private Long justificadoPor;

    public RegistroFalta(Long id, long pacienteId, long agendamentoId, LocalDateTime dataFalta,
                         boolean justificada, String motivoJustificativa, LocalDateTime justificadoEm,
                         Long justificadoPor) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.agendamentoId = agendamentoId;
        this.dataFalta = dataFalta;
        this.justificada = justificada;
        this.motivoJustificativa = motivoJustificativa;
        this.justificadoEm = justificadoEm;
        this.justificadoPor = justificadoPor;
    }

    public void justificar(String motivo, long gestorId, LocalDateTime agora) {
        this.justificada = true;
        this.motivoJustificativa = motivo;
        this.justificadoPor = gestorId;
        this.justificadoEm = agora;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getPacienteId() { return pacienteId; }
    public long getAgendamentoId() { return agendamentoId; }
    public LocalDateTime getDataFalta() { return dataFalta; }
    public boolean isJustificada() { return justificada; }
    public String getMotivoJustificativa() { return motivoJustificativa; }
    public LocalDateTime getJustificadoEm() { return justificadoEm; }
    public Long getJustificadoPor() { return justificadoPor; }
}
