package model.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

/** Visão de leitura de um horário livre com o contexto necessário para a tela de agendamento. */
public record HorarioDisponivel(long horarioId, long agendaId, LocalDate data, LocalTime horaInicio,
                                LocalTime horaFim, int duracaoMinutos, long unidadeId, String unidadeNome,
                                long profissionalId, String profissionalNome, String especialidade) { }
