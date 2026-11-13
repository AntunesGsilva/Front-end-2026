package model.enums;

import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.Vacinacao;

/**
 * Tabela de procedimentos (RN02/RN04). Os valores vêm das constantes das subclasses de
 * Agendamento, que são a fonte única de verdade e as expõem via métodos sobrescritos.
 */
public enum TipoAgendamento {
    CONSULTA("Consulta clínica", ConsultaClinica.DURACAO_MINUTOS, ConsultaClinica.JANELA_HORAS),
    EXAME("Exame diagnóstico", Exame.DURACAO_MINUTOS, Exame.JANELA_HORAS),
    VACINA("Vacinação", Vacinacao.DURACAO_MINUTOS, Vacinacao.JANELA_HORAS);

    private final String rotulo;
    private final int duracaoMinutos;
    private final int janelaConfirmacaoHoras;

    TipoAgendamento(String rotulo, int duracaoMinutos, int janelaConfirmacaoHoras) {
        this.rotulo = rotulo;
        this.duracaoMinutos = duracaoMinutos;
        this.janelaConfirmacaoHoras = janelaConfirmacaoHoras;
    }

    public String getRotulo() { return rotulo; }
    public int duracaoMinutos() { return duracaoMinutos; }
    public int janelaConfirmacaoHoras() { return janelaConfirmacaoHoras; }
}
