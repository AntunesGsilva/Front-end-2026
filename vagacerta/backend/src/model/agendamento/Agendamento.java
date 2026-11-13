package model.agendamento;

import model.RegraNegocioException;
import model.enums.StatusAgendamento;
import model.enums.TipoAgendamento;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Base abstrata polimórfica dos agendamentos (ConsultaClinica, Exame, Vacinacao).
 * As regras que variam por procedimento (RN02/RN04) são métodos abstratos sobrescritos nas subclasses,
 * de modo que o motor de autoliberação trabalha com {@code List<Agendamento>} sem if/else por tipo.
 */
public abstract class Agendamento {
    private Long id;
    private final long horarioId;
    private final long pacienteId;
    private StatusAgendamento status;
    private LocalDateTime confirmadoEm;
    private LocalDateTime dataCriacao;

    // Contexto do atendimento (preenchido pelo DAO/Service a partir de horario + agenda_diaria)
    private LocalDate dataAtendimento;
    private LocalTime horaAtendimento;
    private LocalTime horaFimAtendimento;
    private Long profissionalId;
    private Long unidadeId;

    protected Agendamento(Long id, long horarioId, long pacienteId, StatusAgendamento status,
                          LocalDateTime confirmadoEm, LocalDateTime dataCriacao) {
        this.id = id;
        this.horarioId = horarioId;
        this.pacienteId = pacienteId;
        this.status = status == null ? StatusAgendamento.AGENDADO : status;
        this.confirmadoEm = confirmadoEm;
        this.dataCriacao = dataCriacao;
    }

    // ---- Métodos polimórficos (cada procedimento sobrescreve com @Override) ----

    public abstract TipoAgendamento tipo();

    /** RN02: antecedência máxima, em horas, para confirmar. 0 = isento de confirmação. */
    public abstract int janelaConfirmacaoHoras();

    /** RN04: duração padrão do atendimento em minutos. */
    public abstract int duracaoMinutos();

    public abstract boolean exigePreparo();

    public abstract String gerarInstrucoesAtendimento();

    /** RN02: decide se a vaga deve ser devolvida à fila pública em {@code agora}. */
    public abstract boolean deveSerAutoliberado(LocalDate data, LocalTime hora, LocalDateTime agora);

    /** Atributos específicos do procedimento, para exibição/serialização. */
    public abstract Map<String, Object> detalhes();

    /** Atualiza os atributos específicos a partir de um mapa (campos ausentes são mantidos). */
    public abstract void aplicarDetalhes(Map<String, Object> campos);

    // ---- Comportamento comum ----

    public boolean isIsentoConfirmacao() {
        return janelaConfirmacaoHoras() == 0;
    }

    /** Último instante em que a confirmação é aceita: início do atendimento menos a janela. */
    public LocalDateTime prazoConfirmacao(LocalDate data, LocalTime hora) {
        return LocalDateTime.of(data, hora).minusHours(janelaConfirmacaoHoras());
    }

    public LocalDateTime prazoConfirmacao() {
        return prazoConfirmacao(dataAtendimento, horaAtendimento);
    }

    public boolean deveSerAutoliberado(LocalDateTime agora) {
        return deveSerAutoliberado(dataAtendimento, horaAtendimento, agora);
    }

    /** Utilitário das subclasses sujeitas à confirmação: pendente e com o prazo vencido. */
    protected final boolean prazoVencido(LocalDate data, LocalTime hora, LocalDateTime agora) {
        return status == StatusAgendamento.AGENDADO && agora.isAfter(prazoConfirmacao(data, hora));
    }

    /**
     * Define o estado inicial. Isentos (vacina) e reservas feitas depois do prazo de confirmação
     * (o próprio ato de agendar já é o compromisso do paciente) nascem CONFIRMADOS.
     */
    public void iniciarCiclo(LocalDateTime agora) {
        this.dataCriacao = agora;
        if (isIsentoConfirmacao() || agora.isAfter(prazoConfirmacao())) {
            this.status = StatusAgendamento.CONFIRMADO;
            this.confirmadoEm = agora;
        } else {
            this.status = StatusAgendamento.AGENDADO;
            this.confirmadoEm = null;
        }
    }

    /** Uso em testes: prepara um agendamento para inserção direta no DAO, sem passar pelo service. */
    public void iniciarCicloParaTeste() {
        this.dataCriacao = LocalDateTime.now();
        this.status = StatusAgendamento.AGENDADO;
    }

    public void confirmar(LocalDateTime agora) {
        if (status != StatusAgendamento.AGENDADO) {
            throw new RegraNegocioException("RN02", "Só é possível confirmar agendamentos pendentes (status atual: " + status + ").");
        }
        if (agora.isAfter(prazoConfirmacao())) {
            throw new RegraNegocioException("RN02", "O prazo de confirmação (" + prazoConfirmacao() + ") expirou.");
        }
        status = StatusAgendamento.CONFIRMADO;
        confirmadoEm = agora;
    }

    public void cancelar(LocalDateTime agora) {
        exigirAberto("cancelar");
        if (!agora.isBefore(LocalDateTime.of(dataAtendimento, horaAtendimento))) {
            throw RegraNegocioException.validacao("O atendimento já começou; não é possível cancelar.");
        }
        status = StatusAgendamento.CANCELADO;
    }

    /** Descarte automático (RN02): independe do início do atendimento e só vale para pendentes. */
    public void cancelarPorSistema() {
        exigirAberto("cancelar");
        status = StatusAgendamento.CANCELADO;
    }

    public void registrarFalta(LocalDateTime agora) {
        exigirAberto("registrar falta");
        exigirInicioAtendimento(agora);
        status = StatusAgendamento.FALTA;
    }

    public void marcarRealizado(LocalDateTime agora) {
        exigirAberto("marcar como realizado");
        exigirInicioAtendimento(agora);
        status = StatusAgendamento.REALIZADO;
    }

    private void exigirAberto(String acao) {
        if (!status.isAberto()) {
            throw RegraNegocioException.validacao("Não é possível " + acao + " um agendamento " + status + ".");
        }
    }

    private void exigirInicioAtendimento(LocalDateTime agora) {
        if (agora.isBefore(LocalDateTime.of(dataAtendimento, horaAtendimento))) {
            throw RegraNegocioException.validacao("O atendimento ainda não começou.");
        }
    }

    public void definirContextoAtendimento(LocalDate data, LocalTime inicio, LocalTime fim,
                                           long profissionalId, long unidadeId) {
        this.dataAtendimento = data;
        this.horaAtendimento = inicio;
        this.horaFimAtendimento = fim;
        this.profissionalId = profissionalId;
        this.unidadeId = unidadeId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getHorarioId() { return horarioId; }
    public long getPacienteId() { return pacienteId; }
    public StatusAgendamento getStatus() { return status; }
    public LocalDateTime getConfirmadoEm() { return confirmadoEm; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public LocalDate getDataAtendimento() { return dataAtendimento; }
    public LocalTime getHoraAtendimento() { return horaAtendimento; }
    public LocalTime getHoraFimAtendimento() { return horaFimAtendimento; }
    public Long getProfissionalId() { return profissionalId; }
    public Long getUnidadeId() { return unidadeId; }
}
