package view;

import util.ConnectionFactory;
import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.HorarioDisponivel;
import model.agendamento.Agendamento;
import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.LiberacaoVaga;
import model.agendamento.Vacinacao;
import model.enums.TipoAgendamento;
import model.unidade.UnidadeSaude;
import model.usuario.GestorUnidade;
import model.usuario.Paciente;
import model.usuario.ProfissionalSaude;
import service.AbsenteismoService;
import service.AgendamentoService;
import service.RelatoriosService;
import service.UsuarioService;
import dao.UnidadeSaudeDAO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Demonstração automatizada de todo o ciclo: cadastro, grade, agendamento, autoliberação, absenteísmo e BI. */
public class DemoCompleta {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 13, 9, 0);
    private static final LocalDate DIA = LocalDate.of(2026, 11, 20);

    private final UsuarioService usuarios = new UsuarioService();
    private final AgendamentoService agendamentos = new AgendamentoService();
    private final AbsenteismoService absenteismo = new AbsenteismoService();
    private final Random rnd = new Random();

    public void executar() throws IOException {
        prepararBanco();
        titulo("0. CONEXÃO COM O BANCO DE DADOS");
        System.out.println(ConnectionFactory.testarConexao());

        titulo("1. CADASTRO UNIFICADO (herança: Usuario -> Paciente / ProfissionalSaude / GestorUnidade)");
        UnidadeSaude ubs = new UnidadeSaudeDAO().inserir(new UnidadeSaude(null, "UBS Demo Centro", digitos(7),
                "Av. Exemplo, 100 - Maringá/PR", "(44) 3000-0000"));
        GestorUnidade gestor = (GestorUnidade) usuarios.cadastrar(new GestorUnidade(null, "Marta Gestora", digitos(11), email("gestora"),
                "x", "(44) 99000-0001", true, "GES-" + digitos(5), "Coordenadora"), "senha123");
        ProfissionalSaude clinico = prof("Dr. Clínico Demo", "Clínica Geral", 8);
        ProfissionalSaude cardio = prof("Dra. Cardio Demo", "Cardiologia", 6);
        ProfissionalSaude enfermeira = prof("Enf. Vacina Demo", "Sala de Vacinas", 6);
        Paciente ana = paciente("Ana (apta)");
        Paciente bruno = paciente("Bruno (será reincidente)");
        Paciente carla = paciente("Carla (confirma presença)");
        System.out.printf("UBS %d | gestor %d | profissionais %d, %d, %d | pacientes %d, %d, %d%n", ubs.getId(), gestor.getId(),
                clinico.getId(), cardio.getId(), enfermeira.getId(), ana.getId(), bruno.getId(), carla.getId());
        System.out.println("Senhas gravadas como hash PBKDF2: " + ana.getSenhaHash().substring(0, 30) + "...");

        titulo("2. GERAÇÃO DINÂMICA DA GRADE (RN04 + composição AgendaDiaria 1:N Horario)");
        AgendaDiaria consultas = agendamentos.gerarAgenda(ubs.getId(), clinico.getId(), DIA, LocalTime.of(8, 0), LocalTime.of(12, 0), TipoAgendamento.CONSULTA);
        AgendaDiaria exames = agendamentos.gerarAgenda(ubs.getId(), cardio.getId(), DIA, LocalTime.of(13, 0), LocalTime.of(15, 0), TipoAgendamento.EXAME);
        AgendaDiaria vacinas = agendamentos.gerarAgenda(ubs.getId(), enfermeira.getId(), DIA, LocalTime.of(8, 0), LocalTime.of(8, 30), TipoAgendamento.VACINA);
        System.out.printf("Consultas 08-12h: %d horários de 20 min | Exames 13-15h: %d de 30 min | Vacinas 08:00-08:30: %d de 5 min%n",
                consultas.getHorarios().size(), exames.getHorarios().size(), vacinas.getHorarios().size());
        long[] c = consultas.getHorarios().stream().mapToLong(h -> h.getId()).toArray();
        long[] e = exames.getHorarios().stream().mapToLong(h -> h.getId()).toArray();
        long[] v = vacinas.getHorarios().stream().mapToLong(h -> h.getId()).toArray();

        titulo("3. AGENDAMENTO COM EXCLUSIVIDADE (RN01)");
        Agendamento a1 = agendamentos.agendar(new ConsultaClinica(c[0], ana.getId(), "Controle de pressão", false), true, AGORA);
        System.out.printf("Ana reservou %s às %s (status %s)%n", a1.tipo().getRotulo(), a1.getHoraAtendimento(), a1.getStatus());
        tentar("Bruno tenta o MESMO horário", () -> agendamentos.agendar(new ConsultaClinica(c[0], bruno.getId(), "Rotina", false), true, AGORA));
        corridaConcorrente(c[1], ana.getId(), carla.getId());

        titulo("4. CONFIRMAÇÃO POLIMÓRFICA E AUTOLIBERAÇÃO (RN02)");
        Agendamento exB = agendamentos.agendar(new Exame(e[0], bruno.getId(), "Eletrocardiograma", "Jejum de 4 horas e sem cafeína."), true, AGORA);
        Agendamento conC = agendamentos.agendar(new ConsultaClinica(c[3], carla.getId(), "Retorno cardiológico", true), true, AGORA);
        Agendamento conA = agendamentos.agendar(new ConsultaClinica(c[4], ana.getId(), "Dor lombar", false), true, AGORA);
        Agendamento vacA = agendamentos.agendar(new Vacinacao(v[0], ana.getId(), "Febre Amarela", "Dose única"), true, AGORA);
        for (Agendamento ag : List.of(exB, conC, conA, vacA)) {
            System.out.printf("- %-16s janela=%2dh duração=%2d min preparo=%-5s prazo=%s status=%s%n", ag.tipo().getRotulo(),
                    ag.janelaConfirmacaoHoras(), ag.duracaoMinutos(), ag.exigePreparo(),
                    ag.isIsentoConfirmacao() ? "isento" : ag.prazoConfirmacao(), ag.getStatus());
        }
        System.out.println("Instruções do exame: " + exB.gerarInstrucoesAtendimento());
        agendamentos.confirmar(conC.getId(), AGORA.plusHours(1));
        System.out.println("Carla confirmou presença na consulta.");

        LocalDateTime t1 = exB.prazoConfirmacao().plusMinutes(1);
        System.out.println("\n>>> Relógio avança para " + t1 + " (vence a janela de 48h dos exames pendentes, inclusive o de Bruno)");
        mostrarLiberados(agendamentos.executarMotorAutoliberacao(t1));
        System.out.println("Horário do exame voltou a LIVRE? " + agendamentos.listarHorariosLivres(ubs.getId(), cardio.getId(), DIA,
                TipoAgendamento.EXAME, t1).stream().anyMatch(h -> h.horarioId() == e[0]));
        Agendamento reaproveitado = agendamentos.agendar(new Exame(e[0], carla.getId(), "Eletrocardiograma", "Jejum de 4 horas."), true, t1.plusMinutes(5));
        System.out.println("Vaga reaproveitada: Carla ocupou o horário liberado (status " + reaproveitado.getStatus() + ").");

        LocalDateTime t2 = conA.prazoConfirmacao().plusMinutes(1);
        System.out.println("\n>>> Relógio avança para " + t2 + " (vence a janela de 24h das consultas pendentes, inclusive as não confirmadas de Ana)");
        mostrarLiberados(agendamentos.executarMotorAutoliberacao(t2));
        System.out.println("Consulta confirmada de Carla e vacina de Ana permanecem: "
                + agendamentos.buscar(conC.getId()).getStatus() + " / " + agendamentos.buscar(vacA.getId()).getStatus());

        titulo("5. ABSENTEÍSMO E RESTRIÇÃO POR REINCIDÊNCIA (RN03)");
        List<Agendamento> bruno3 = new ArrayList<>();
        for (int i = 5; i <= 7; i++) bruno3.add(agendamentos.agendar(new ConsultaClinica(c[i], bruno.getId(), "Consulta " + i, false), true, AGORA));
        LocalDateTime fimDoDia = LocalDateTime.of(DIA, LocalTime.of(12, 30));
        for (Agendamento ag : bruno3) absenteismo.registrarFalta(ag.getId(), fimDoDia);
        System.out.println("Bruno faltou 3 vezes sem justificativa. Pode agendar online? " + absenteismo.podeAgendarOnline(bruno.getId(), fimDoDia));
        tentar("Bruno tenta agendar online", () -> agendamentos.agendar(new ConsultaClinica(c[8], bruno.getId(), "Rotina", false), true, fimDoDia.minusDays(10)));
        Agendamento presencial = agendamentos.agendar(new ConsultaClinica(c[8], bruno.getId(), "Rotina", false), false, fimDoDia.minusDays(10));
        System.out.println("Atendimento presencial no acolhimento é permitido (agendamento " + presencial.getId() + ").");
        int n = absenteismo.acolhimentoPresencial(bruno.getId(), gestor.getId(), "Atestado médico validado pelo gestor", fimDoDia);
        System.out.println("Gestor validou o atestado e justificou " + n + " faltas. Pode agendar online? " + absenteismo.podeAgendarOnline(bruno.getId(), fimDoDia));

        titulo("6. BUSINESS INTELLIGENCE (F6)");
        System.out.println(new RelatoriosService().gerar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).formatar());
        System.out.println("Demonstração concluída com sucesso.");
    }

    // ------------------------------------------------------------------ apoio
    private void prepararBanco() throws IOException {
        boolean configurado = System.getProperty("vagacerta.jdbc.url") != null || System.getenv("VAGACERTA_JDBC_URL") != null;
        if (!configurado) {
            // sem configuração explícita: usa um SQLite descartável para a demonstração ser repetível
            for (String sufixo : new String[] {"", "-journal", "-wal", "-shm"}) Files.deleteIfExists(Path.of("vagacerta_demo.db" + sufixo));
            System.setProperty("vagacerta.jdbc.url", "jdbc:sqlite:vagacerta_demo.db");
            System.out.println("(banco de demonstração descartável: vagacerta_demo.db)");
        }
        ConnectionFactory.inicializarBanco();
    }

    private void corridaConcorrente(long horarioId, long p1, long p2) {
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger venceu = new AtomicInteger(), rejeitou = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long paciente = i % 2 == 0 ? p1 : p2;
            fs.add(pool.submit(() -> {
                try {
                    largada.await();
                    new AgendamentoService().agendar(new ConsultaClinica(horarioId, paciente, "Disputa", false), true, AGORA);
                    venceu.incrementAndGet();
                } catch (RegraNegocioException ex) {
                    rejeitou.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        largada.countDown();
        fs.forEach(f -> { try { f.get(); } catch (Exception ignorado) { /* contabilizado acima */ } });
        pool.shutdown();
        System.out.printf("Disputa concorrente por 1 horário com %d threads: %d venceu, %d rejeitadas (UNIQUE(horario_id) + CAS).%n",
                threads, venceu.get(), rejeitou.get());
    }

    private void mostrarLiberados(List<Agendamento> liberados) {
        System.out.println("Motor de autoliberação descartou " + liberados.size() + " agendamento(s):");
        for (Agendamento a : liberados) {
            System.out.printf("  - %s de %s em %s %s devolvido à fila pública%n", a.tipo().getRotulo(), a.getPacienteId(),
                    a.getDataAtendimento(), a.getHoraAtendimento());
        }
    }

    private void tentar(String descricao, Runnable acao) {
        try {
            acao.run();
            System.out.println(descricao + " -> ACEITO (inesperado)");
        } catch (RegraNegocioException ex) {
            System.out.println(descricao + " -> REJEITADO [" + ex.getCodigo() + "] " + ex.getMessage());
        }
    }

    private ProfissionalSaude prof(String nome, String especialidade, int horas) {
        return (ProfissionalSaude) usuarios.cadastrar(new ProfissionalSaude(null, nome, digitos(11), email("prof"), "x", "(44) 99000-0002",
                true, "REG-" + digitos(6), especialidade, horas), "senha123");
    }

    private Paciente paciente(String nome) {
        return (Paciente) usuarios.cadastrar(new Paciente(null, nome, digitos(11), email("pac"), "x", "(44) 99000-0003", true,
                digitos(15), LocalDate.of(1990, 1, 1), 0), "senha123");
    }

    private String digitos(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(rnd.nextInt(10));
        return sb.toString();
    }

    private String email(String prefixo) { return prefixo + System.nanoTime() + "@demo.vagacerta.local"; }

    private static void titulo(String t) { System.out.println("\n==== " + t + " ===="); }
}
