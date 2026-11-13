package service;

import dao.AgendamentoDAO;
import dao.RegistroFaltaDAO;
import dao.Transacao;
import dao.UsuarioDAO;
import model.RegraNegocioException;
import model.agendamento.Agendamento;
import model.agendamento.RegistroFalta;
import model.usuario.GestorUnidade;
import model.usuario.Paciente;
import model.usuario.Usuario;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

/** F5 - Controle de absenteísmo: registro de faltas, bloqueio (RN03) e acolhimento presencial. */
public class AbsenteismoService {

    /**
     * Recalcula o contador desnormalizado de faltas contando as não justificadas na janela
     * móvel de {@link Paciente#JANELA_MESES} meses, e o persiste.
     */
    static int sincronizarContador(Connection c, Paciente p, LocalDateTime agora) {
        int n = new RegistroFaltaDAO(c).listarNaoJustificadasDesde(p.getId(), agora.minusMonths(Paciente.JANELA_MESES)).size();
        p.setFaltasNaoJustificadas(n);
        new UsuarioDAO(c).atualizarFaltas(p.getId(), n);
        return n;
    }

    private static Paciente carregarPaciente(Connection c, long id) {
        Usuario u = new UsuarioDAO(c).buscarPorId(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Paciente " + id + " não encontrado."));
        if (!(u instanceof Paciente p)) throw RegraNegocioException.validacao("O usuário " + id + " não é paciente.");
        return p;
    }

    /** Marca a falta do agendamento, grava no histórico e reavalia o bloqueio do paciente (RN03). */
    public RegistroFalta registrarFalta(long agendamentoId, LocalDateTime agora) {
        return Transacao.executar(c -> {
            AgendamentoDAO dao = new AgendamentoDAO(c);
            Agendamento ag = dao.buscarPorId(agendamentoId)
                    .orElseThrow(() -> RegraNegocioException.naoEncontrado("Agendamento " + agendamentoId + " não encontrado."));
            ag.registrarFalta(agora);
            dao.atualizar(ag);
            RegistroFalta falta = new RegistroFaltaDAO(c).inserir(new RegistroFalta(null, ag.getPacienteId(), ag.getId(),
                    LocalDateTime.of(ag.getDataAtendimento(), ag.getHoraAtendimento()), false, null, null, null));
            sincronizarContador(c, carregarPaciente(c, ag.getPacienteId()), agora);
            return falta;
        });
    }

    public Agendamento marcarRealizado(long agendamentoId, LocalDateTime agora) {
        return Transacao.executar(c -> {
            AgendamentoDAO dao = new AgendamentoDAO(c);
            Agendamento ag = dao.buscarPorId(agendamentoId)
                    .orElseThrow(() -> RegraNegocioException.naoEncontrado("Agendamento " + agendamentoId + " não encontrado."));
            ag.marcarRealizado(agora);
            dao.atualizar(ag);
            return ag;
        });
    }

    /** RN03: só um Gestor da Unidade, com atestado validado, justifica a falta e restabelece o agendamento online. */
    public RegistroFalta justificarFalta(long faltaId, long gestorId, String motivo, LocalDateTime agora) {
        return Transacao.executar(c -> {
            exigirGestor(c, gestorId);
            RegistroFaltaDAO dao = new RegistroFaltaDAO(c);
            RegistroFalta f = dao.buscarPorId(faltaId)
                    .orElseThrow(() -> RegraNegocioException.naoEncontrado("Falta " + faltaId + " não encontrada."));
            if (f.isJustificada()) throw RegraNegocioException.validacao("Esta falta já foi justificada.");
            f.justificar(exigirMotivo(motivo), gestorId, agora);
            dao.atualizarJustificativa(f);
            sincronizarContador(c, carregarPaciente(c, f.getPacienteId()), agora);
            return f;
        });
    }

    /** Acolhimento presencial: o gestor valida o atestado e justifica as faltas pendentes na janela. Retorna quantas. */
    public int acolhimentoPresencial(long pacienteId, long gestorId, String motivo, LocalDateTime agora) {
        return Transacao.executar(c -> {
            exigirGestor(c, gestorId);
            Paciente p = carregarPaciente(c, pacienteId);
            RegistroFaltaDAO dao = new RegistroFaltaDAO(c);
            String texto = exigirMotivo(motivo);
            List<RegistroFalta> pendentes = dao.listarNaoJustificadasDesde(pacienteId, agora.minusMonths(Paciente.JANELA_MESES));
            for (RegistroFalta f : pendentes) {
                f.justificar(texto, gestorId, agora);
                dao.atualizarJustificativa(f);
            }
            sincronizarContador(c, p, agora);
            return pendentes.size();
        });
    }

    /** RN03 avaliada na janela móvel (não depende do contador gravado estar atualizado). */
    public boolean podeAgendarOnline(long pacienteId, LocalDateTime agora) {
        return Transacao.executar(c -> {
            Paciente p = carregarPaciente(c, pacienteId);
            sincronizarContador(c, p, agora);
            return p.podeAgendarOnline();
        });
    }

    public List<RegistroFalta> listarFaltas(long pacienteId) {
        return new RegistroFaltaDAO().listarPorPaciente(pacienteId);
    }

    private static void exigirGestor(Connection c, long gestorId) {
        Usuario u = new UsuarioDAO(c).buscarPorId(gestorId)
                .orElseThrow(() -> new RegraNegocioException("PERMISSAO", "Gestor não encontrado."));
        if (!(u instanceof GestorUnidade) || !u.isAtivo()) {
            throw new RegraNegocioException("PERMISSAO", "Somente um Gestor da Unidade ativo pode justificar faltas (RN03).");
        }
    }

    private static String exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw RegraNegocioException.validacao("Informe o atestado/justificativa validado pelo gestor.");
        }
        return motivo.trim();
    }
}
