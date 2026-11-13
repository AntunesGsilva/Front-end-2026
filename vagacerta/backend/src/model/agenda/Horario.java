package model.agenda;

import model.RegraNegocioException;
import model.enums.StatusHorario;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Fatia de tempo da grade. Só existe dentro de uma {@link AgendaDiaria} (composição):
 * o construtor é restrito ao pacote e a agenda é a única responsável por criá-lo.
 */
public class Horario {
    private final Long id;
    private final Long agendaId;
    private final LocalTime horaInicio;
    private final LocalTime horaFim;
    private StatusHorario status;

    Horario(Long id, Long agendaId, LocalTime horaInicio, LocalTime horaFim, StatusHorario status) {
        this.id = id;
        this.agendaId = agendaId;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.status = status;
    }

    /** RN01: LIVRE -> RESERVADO. */
    public void reservar() {
        if (status != StatusHorario.LIVRE) {
            throw new RegraNegocioException("RN01", "Horário " + horaInicio + " não está livre (" + status + ").");
        }
        status = StatusHorario.RESERVADO;
    }

    /** RN01: volta imediatamente para LIVRE após cancelamento ou autoliberação. */
    public void liberar() {
        if (status == StatusHorario.RESERVADO) status = StatusHorario.LIVRE;
    }

    public void bloquear() {
        if (status == StatusHorario.LIVRE) status = StatusHorario.BLOQUEADO;
    }

    public boolean isLivre() { return status == StatusHorario.LIVRE; }
    public long duracaoMinutos() { return Duration.between(horaInicio, horaFim).toMinutes(); }

    public Long getId() { return id; }
    public Long getAgendaId() { return agendaId; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public StatusHorario getStatus() { return status; }
}
