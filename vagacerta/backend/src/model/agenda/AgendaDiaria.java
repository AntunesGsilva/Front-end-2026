package model.agenda;

import model.RegraNegocioException;
import model.enums.StatusHorario;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Agregado raiz da grade (RN04). COMPÕE uma lista de {@link Horario} (1 para 1..*):
 * um Horario não é uma AgendaDiaria (não há herança) e não sobrevive a ela
 * (no banco: horario.agenda_id NOT NULL + ON DELETE CASCADE).
 */
public class AgendaDiaria {
    private Long id;
    private final long unidadeId;
    private final long profissionalId;
    private final LocalDate data;
    private final LocalTime turnoInicio;
    private final LocalTime turnoFim;
    private boolean bloqueada;
    private final List<Horario> horarios = new ArrayList<>();

    public AgendaDiaria(Long id, long unidadeId, long profissionalId, LocalDate data,
                        LocalTime turnoInicio, LocalTime turnoFim, boolean bloqueada) {
        if (data == null || turnoInicio == null || turnoFim == null || !turnoFim.isAfter(turnoInicio)) {
            throw RegraNegocioException.validacao("Agenda inválida: informe data e um turno com fim após o início.");
        }
        this.id = id;
        this.unidadeId = unidadeId;
        this.profissionalId = profissionalId;
        this.data = data;
        this.turnoInicio = turnoInicio;
        this.turnoFim = turnoFim;
        this.bloqueada = bloqueada;
    }

    /** RN04: fatia o turno em horários consecutivos de {@code duracaoMinutos}; sobra menor que a duração é descartada. */
    public void gerarHorarios(int duracaoMinutos) {
        if (duracaoMinutos <= 0) throw RegraNegocioException.validacao("Duração deve ser positiva.");
        if (!horarios.isEmpty()) throw RegraNegocioException.validacao("A grade desta agenda já foi gerada.");
        LocalTime inicio = turnoInicio;
        while (!inicio.plusMinutes(duracaoMinutos).isAfter(turnoFim)
                && inicio.plusMinutes(duracaoMinutos).isAfter(inicio)) {
            horarios.add(new Horario(null, id, inicio, inicio.plusMinutes(duracaoMinutos), StatusHorario.LIVRE));
            inicio = inicio.plusMinutes(duracaoMinutos);
        }
        if (horarios.isEmpty()) {
            throw RegraNegocioException.validacao("O turno é menor que a duração do atendimento.");
        }
    }

    /** Reconstitui um horário já persistido (usado pelos DAOs). */
    public Horario restaurarHorario(long horarioId, LocalTime inicio, LocalTime fim, StatusHorario status) {
        Horario h = new Horario(horarioId, id, inicio, fim, status);
        horarios.add(h);
        return h;
    }

    public Optional<Horario> buscarHorario(long horarioId) {
        return horarios.stream().filter(h -> h.getId() != null && h.getId() == horarioId).findFirst();
    }

    public List<Horario> horariosLivres() {
        return horarios.stream().filter(Horario::isLivre).toList();
    }

    public List<Horario> getHorarios() { return Collections.unmodifiableList(horarios); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getUnidadeId() { return unidadeId; }
    public long getProfissionalId() { return profissionalId; }
    public LocalDate getData() { return data; }
    public LocalTime getTurnoInicio() { return turnoInicio; }
    public LocalTime getTurnoFim() { return turnoFim; }
    public boolean isBloqueada() { return bloqueada; }
    public void setBloqueada(boolean bloqueada) { this.bloqueada = bloqueada; }
}
