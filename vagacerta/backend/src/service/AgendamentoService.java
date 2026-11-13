package service;

import dao.AgendaDiariaDAO;
import dao.AgendamentoDAO;
import dao.LiberacaoVagaDAO;
import dao.Transacao;
import dao.UsuarioDAO;
import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.Horario;
import model.agenda.HorarioDisponivel;
import model.agendamento.Agendamento;
import model.agendamento.LiberacaoVaga;
import model.enums.StatusAgendamento;
import model.enums.StatusHorario;
import model.enums.TipoAgendamento;
import model.usuario.Paciente;
import model.usuario.ProfissionalSaude;
import model.usuario.Usuario;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra as transações das regras RN01 (exclusividade), RN02 (confirmação e autoliberação),
 * RN03 (restrição por faltas) e RN04 (geração da grade).
 * Todos os métodos recebem {@code agora} para que o tempo possa ser simulado (demonstração/testes).
 */
public class AgendamentoService {

    // ------------------------------------------------------------------ RN04
    /** F2 - gera e persiste a grade do dia (agenda + horários, atomicamente). */
    public AgendaDiaria gerarAgenda(long unidadeId, long profissionalId, LocalDate data,
                                    LocalTime inicio, LocalTime fim, TipoAgendamento tipo) {
        Usuario u = new UsuarioDAO().buscarPorId(profissionalId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Profissional " + profissionalId + " não encontrado."));
        if (!(u instanceof ProfissionalSaude prof)) {
            throw RegraNegocioException.validacao("O usuário " + profissionalId + " não é profissional de saúde.");
        }
        AgendaDiaria gerada = prof.gerarAgenda(data, unidadeId, inicio, fim, tipo);
        return new AgendaDiariaDAO().inserir(gerada);
    }

    public List<HorarioDisponivel> listarHorariosLivres(Long unidadeId, Long profissionalId, LocalDate data,
                                                        TipoAgendamento tipo, LocalDateTime agora) {
        return new AgendaDiariaDAO().listarHorariosLivres(unidadeId, profissionalId, data,
                tipo == null ? null : tipo.duracaoMinutos(), agora);
    }

    // ------------------------------------------------------------------ RN01 + RN03
    /**
     * F3 - reserva o horário. {@code online=true} aplica a restrição por reincidência (RN03);
     * {@code online=false} representa o atendimento presencial no acolhimento da UBS.
     */
    public Agendamento agendar(Agendamento novo, boolean online, LocalDateTime agora) {
        return Transacao.executar(c -> {
            Usuario u = new UsuarioDAO(c).buscarPorId(novo.getPacienteId())
                    .orElseThrow(() -> RegraNegocioException.naoEncontrado("Paciente " + novo.getPacienteId() + " não encontrado."));
            if (!(u instanceof Paciente paciente) || !paciente.isAtivo()) {
                throw RegraNegocioException.validacao("Agendamentos exigem um paciente ativo.");
            }
            if (online) {
                AbsenteismoService.sincronizarContador(c, paciente, agora);
                if (!paciente.podeAgendarOnline()) {
                    throw new RegraNegocioException("RN03", "Agendamento online bloqueado: " + paciente.getFaltasNaoJustificadas()
                            + " faltas não justificadas nos últimos " + Paciente.JANELA_MESES
                            + " meses. Compareça ao acolhimento da UBS para justificar.");
                }
            }

            AgendaDiariaDAO agendaDAO = new AgendaDiariaDAO(c);
            AgendaDiaria agenda = agendaDAO.buscarPorHorario(novo.getHorarioId())
                    .orElseThrow(() -> RegraNegocioException.naoEncontrado("Horário " + novo.getHorarioId() + " não encontrado."));
            Horario horario = agenda.buscarHorario(novo.getHorarioId()).orElseThrow();
            if (agenda.isBloqueada() || horario.getStatus() == StatusHorario.BLOQUEADO) {
                throw RegraNegocioException.validacao("Esta agenda está bloqueada para novos agendamentos.");
            }
            if (horario.getStatus() != StatusHorario.LIVRE) {
                throw new RegraNegocioException("RN01", "O horário " + horario.getHoraInicio() + " já foi reservado.");
            }
            if (horario.duracaoMinutos() != novo.duracaoMinutos()) {
                throw new RegraNegocioException("RN04", novo.tipo().getRotulo() + " dura " + novo.duracaoMinutos()
                        + " min, mas este horário tem " + horario.duracaoMinutos() + " min.");
            }
            if (!LocalDateTime.of(agenda.getData(), horario.getHoraInicio()).isAfter(agora)) {
                throw RegraNegocioException.validacao("Este horário já passou.");
            }

            novo.definirContextoAtendimento(agenda.getData(), horario.getHoraInicio(), horario.getHoraFim(),
                    agenda.getProfissionalId(), agenda.getUnidadeId());
            // RN01: compare-and-set atômico LIVRE -> RESERVADO (quem chega depois recebe false)
            if (!agendaDAO.reservarHorario(novo.getHorarioId())) {
                throw new RegraNegocioException("RN01", "O horário acabou de ser reservado por outra pessoa.");
            }
            novo.iniciarCiclo(agora);
            return new AgendamentoDAO(c).inserir(novo); // UNIQUE(horario_id) como última barreira física
        });
    }

    // ------------------------------------------------------------------ RN02
    /** F4 - confirmação de presença dentro da janela do procedimento. */
    public Agendamento confirmar(long agendamentoId, LocalDateTime agora) {
        return Transacao.executar(c -> {
            AgendamentoDAO dao = new AgendamentoDAO(c);
            Agendamento ag = carregar(dao, agendamentoId);
            ag.confirmar(agora);
            dao.atualizar(ag);
            return ag;
        });
    }

    /**
     * F4 - motor de autoliberação: varre os agendamentos pendentes e devolve à fila pública,
     * imediatamente, os que perderam o prazo de confirmação. A decisão é polimórfica
     * ({@link Agendamento#deveSerAutoliberado}): não há if/else por tipo de procedimento.
     */
    public List<Agendamento> executarMotorAutoliberacao(LocalDateTime agora) {
        List<Agendamento> pendentes = new AgendamentoDAO().listarPorStatus(StatusAgendamento.AGENDADO);
        List<Agendamento> liberados = new ArrayList<>();
        for (Agendamento candidato : pendentes) {
            if (!candidato.deveSerAutoliberado(candidato.getDataAtendimento(), candidato.getHoraAtendimento(), agora)) {
                continue;
            }
            Agendamento liberado = Transacao.executar(c -> {
                AgendamentoDAO dao = new AgendamentoDAO(c);
                Agendamento atual = dao.buscarPorId(candidato.getId()).orElse(null);
                // revalida: pode ter sido confirmado/cancelado entre a leitura e o descarte
                if (atual == null || atual.getStatus() != StatusAgendamento.AGENDADO) return null;
                liberarVaga(c, atual, LiberacaoVaga.Motivo.AUTOLIBERACAO, agora);
                atual.cancelarPorSistema();
                return atual;
            });
            if (liberado != null) liberados.add(liberado);
        }
        return liberados;
    }

    // ------------------------------------------------------------------ Cancelamento (RN01)
    /** Cancela e devolve o horário imediatamente ao estado LIVRE. */
    public Agendamento cancelar(long agendamentoId, LiberacaoVaga.Motivo motivo, LocalDateTime agora) {
        if (motivo == LiberacaoVaga.Motivo.AUTOLIBERACAO) {
            throw RegraNegocioException.validacao("Autoliberação é executada apenas pelo motor automático.");
        }
        return Transacao.executar(c -> {
            Agendamento ag = carregar(new AgendamentoDAO(c), agendamentoId);
            ag.cancelar(agora);
            liberarVaga(c, ag, motivo, agora);
            return ag;
        });
    }

    /** Remove a linha do agendamento, libera o horário e registra o evento para o BI. */
    private static void liberarVaga(Connection c, Agendamento ag, LiberacaoVaga.Motivo motivo, LocalDateTime agora) {
        new LiberacaoVagaDAO(c).inserir(new LiberacaoVaga(null, ag.getHorarioId(), ag.getPacienteId(), ag.tipo(), motivo, agora));
        new AgendamentoDAO(c).excluir(ag.getId());
        new AgendaDiariaDAO(c).liberarHorario(ag.getHorarioId());
    }

    // ------------------------------------------------------------------ CRUD / consultas
    public Agendamento buscar(long id) {
        return carregar(new AgendamentoDAO(), id);
    }

    public List<Agendamento> listar(Long pacienteId, Long profissionalId, StatusAgendamento status, LocalDate data) {
        return new AgendamentoDAO().listar(pacienteId, profissionalId, status, data);
    }

    /** U do CRUD: altera os atributos específicos (motivo, preparo, dose...) de um agendamento em aberto. */
    public Agendamento atualizarDetalhes(long id, java.util.Map<String, Object> campos) {
        return Transacao.executar(c -> {
            AgendamentoDAO dao = new AgendamentoDAO(c);
            Agendamento ag = carregar(dao, id);
            if (!ag.getStatus().isAberto()) {
                throw RegraNegocioException.validacao("Só é possível editar agendamentos em aberto.");
            }
            ag.aplicarDetalhes(campos);
            dao.atualizar(ag);
            return ag;
        });
    }

    public AgendaDiaria buscarAgenda(long id) {
        return new AgendaDiariaDAO().buscarPorId(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Agenda " + id + " não encontrada."));
    }

    public List<AgendaDiaria> listarAgendas(Long unidadeId, Long profissionalId, LocalDate de, LocalDate ate) {
        return new AgendaDiariaDAO().listar(unidadeId, profissionalId, de, ate);
    }

    public void bloquearAgenda(long agendaId, boolean bloquear) {
        new AgendaDiariaDAO().atualizarBloqueio(agendaId, bloquear);
    }

    public void excluirAgenda(long agendaId) {
        if (!new AgendaDiariaDAO().excluir(agendaId)) {
            throw RegraNegocioException.naoEncontrado("Agenda " + agendaId + " não encontrada.");
        }
    }

    private static Agendamento carregar(AgendamentoDAO dao, long id) {
        return dao.buscarPorId(id).orElseThrow(() -> RegraNegocioException.naoEncontrado("Agendamento " + id + " não encontrado."));
    }
}
