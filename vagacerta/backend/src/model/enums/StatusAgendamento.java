package model.enums;

public enum StatusAgendamento {
    AGENDADO, CONFIRMADO, REALIZADO, FALTA, CANCELADO;

    /** Agendamento que ainda ocupa o horário e pode ser cancelado/confirmado/atendido. */
    public boolean isAberto() {
        return this == AGENDADO || this == CONFIRMADO;
    }
}
