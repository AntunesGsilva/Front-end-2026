package view;

import util.ConnectionFactory;
import dao.UnidadeSaudeDAO;
import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.HorarioDisponivel;
import model.agendamento.Agendamento;
import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.LiberacaoVaga;
import model.agendamento.Vacinacao;
import model.enums.PerfilAcesso;
import model.enums.TipoAgendamento;
import model.unidade.UnidadeSaude;
import model.usuario.Usuario;
import service.AbsenteismoService;
import service.AgendamentoService;
import service.RelatoriosService;
import service.UsuarioService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Scanner;

/** Interface de terminal para operar o sistema manualmente (View/Controller em modo CLI). */
public class CliInterativa {
    private final Scanner in = new Scanner(System.in);
    private final AgendamentoService agendamentos = new AgendamentoService();
    private final AbsenteismoService absenteismo = new AbsenteismoService();
    private final UsuarioService usuarios = new UsuarioService();
    private LocalDateTime relogio; // null = hora real

    public void executar() {
        System.out.println("=== VagaCerta - modo interativo ===");
        boolean sair = false;
        while (!sair) {
            menu();
            String op = in.nextLine().trim();
            try {
                switch (op) {
                    case "1" -> listarLivres();
                    case "2" -> agendar();
                    case "3" -> listarAgendamentos();
                    case "4" -> { Agendamento a = agendamentos.confirmar(lerLong("ID do agendamento"), agora());
                                  System.out.println("Confirmado. Status: " + a.getStatus()); }
                    case "5" -> { agendamentos.cancelar(lerLong("ID do agendamento"), LiberacaoVaga.Motivo.CANCELAMENTO_PACIENTE, agora());
                                  System.out.println("Cancelado; horário devolvido à fila."); }
                    case "6" -> { absenteismo.registrarFalta(lerLong("ID do agendamento"), agora()); System.out.println("Falta registrada."); }
                    case "7" -> { absenteismo.marcarRealizado(lerLong("ID do agendamento"), agora()); System.out.println("Atendimento realizado."); }
                    case "8" -> gerarAgenda();
                    case "9" -> System.out.println("Liberados: " + agendamentos.executarMotorAutoliberacao(agora()).size());
                    case "10" -> { int n = absenteismo.acolhimentoPresencial(lerLong("ID do paciente"), lerLong("ID do gestor"),
                                           lerTexto("Atestado/justificativa validado"), agora());
                                   System.out.println(n + " falta(s) justificada(s)."); }
                    case "11" -> System.out.println(new RelatoriosService().gerar(lerData("Início (AAAA-MM-DD)"), lerData("Fim (AAAA-MM-DD)")).formatar());
                    case "12" -> usuariosMenu();
                    case "13" -> new UnidadeSaudeDAO().listar().forEach(u -> System.out.printf("%d - %s (CNES %s)%n", u.getId(), u.getNome(), u.getCnes()));
                    case "14" -> System.out.println(ConnectionFactory.testarConexao());
                    case "15" -> definirRelogio();
                    case "0" -> sair = true;
                    default -> System.out.println("Opção inválida.");
                }
            } catch (RegraNegocioException e) {
                System.out.println("[" + e.getCodigo() + "] " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Entrada inválida ou erro: " + e.getMessage());
            }
        }
        System.out.println("Até logo!");
    }

    private void menu() {
        System.out.println("\nRelógio: " + agora() + (relogio == null ? " (real)" : " (simulado)"));
        System.out.println(" 1 Horários livres      2 Agendar            3 Listar agendamentos   4 Confirmar presença");
        System.out.println(" 5 Cancelar             6 Registrar falta    7 Marcar realizado      8 Gerar agenda (RN04)");
        System.out.println(" 9 Motor autoliberação 10 Acolhimento (RN03) 11 Relatório BI        12 Usuários");
        System.out.println("13 Unidades            14 Testar banco      15 Relógio simulado      0 Sair");
        System.out.print("> ");
    }

    private LocalDateTime agora() { return relogio != null ? relogio : LocalDateTime.now().withNano(0); }

    private void definirRelogio() {
        String t = lerTexto("Data/hora (AAAA-MM-DDTHH:MM) ou 'real'");
        relogio = t.equalsIgnoreCase("real") ? null : LocalDateTime.parse(t);
    }

    private void listarLivres() {
        TipoAgendamento tipo = lerTipo();
        List<HorarioDisponivel> l = agendamentos.listarHorariosLivres(null, null, null, tipo, agora());
        if (l.isEmpty()) System.out.println("Nenhum horário livre.");
        for (HorarioDisponivel h : l) {
            System.out.printf("horário %d | %s %s-%s (%d min) | %s - %s | %s%n", h.horarioId(), h.data(), h.horaInicio(), h.horaFim(),
                    h.duracaoMinutos(), h.profissionalNome(), h.especialidade(), h.unidadeNome());
        }
    }

    private void agendar() {
        TipoAgendamento tipo = lerTipo();
        long horario = lerLong("ID do horário"), paciente = lerLong("ID do paciente");
        boolean online = lerTexto("Canal (online/presencial)").equalsIgnoreCase("online");
        Agendamento novo = switch (tipo) {
            case CONSULTA -> new ConsultaClinica(horario, paciente, lerTexto("Motivo"), false);
            case EXAME -> new Exame(horario, paciente, lerTexto("Tipo de exame"), lerTexto("Orientação de preparo"));
            case VACINA -> new Vacinacao(horario, paciente, lerTexto("Imunobiológico"), lerTexto("Dose"));
        };
        Agendamento ag = agendamentos.agendar(novo, online, agora());
        System.out.printf("Agendamento %d criado (status %s). %s%n", ag.getId(), ag.getStatus(), ag.gerarInstrucoesAtendimento());
    }

    private void listarAgendamentos() {
        for (Agendamento a : agendamentos.listar(lerLongOuNulo("ID do paciente (vazio = todos)"), null, null, null)) {
            System.out.printf("#%d %-16s %s %s | paciente %d | %s%n", a.getId(), a.tipo().getRotulo(), a.getDataAtendimento(),
                    a.getHoraAtendimento(), a.getPacienteId(), a.getStatus());
        }
    }

    private void gerarAgenda() {
        AgendaDiaria a = agendamentos.gerarAgenda(lerLong("ID da unidade"), lerLong("ID do profissional"), lerData("Data (AAAA-MM-DD)"),
                LocalTime.parse(lerTexto("Início (HH:MM)")), LocalTime.parse(lerTexto("Fim (HH:MM)")), lerTipo());
        System.out.println("Agenda " + a.getId() + " criada com " + a.getHorarios().size() + " horários.");
    }

    private void usuariosMenu() {
        System.out.println("a) listar  b) excluir");
        String o = in.nextLine().trim();
        if (o.equals("a")) {
            for (Usuario u : usuarios.listar((PerfilAcesso) null)) System.out.printf("%d - %s [%s] %s%n", u.getId(), u.getNome(), u.perfil(), u.getEmail());
        } else if (o.equals("b")) {
            usuarios.excluir(lerLong("ID do usuário"));
            System.out.println("Usuário excluído.");
        }
    }

    private TipoAgendamento lerTipo() {
        String t = lerTexto("Tipo (CONSULTA/EXAME/VACINA)").toUpperCase();
        return TipoAgendamento.valueOf(t);
    }

    private String lerTexto(String rotulo) { System.out.print(rotulo + ": "); return in.nextLine().trim(); }
    private long lerLong(String rotulo) { return Long.parseLong(lerTexto(rotulo)); }
    private LocalDate lerData(String rotulo) { return LocalDate.parse(lerTexto(rotulo)); }
    private Long lerLongOuNulo(String rotulo) { String t = lerTexto(rotulo); return t.isEmpty() ? null : Long.valueOf(t); }
}
