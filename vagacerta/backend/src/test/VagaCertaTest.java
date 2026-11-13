package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dao.AgendaDiariaDAO;
import dao.AgendamentoDAO;
import util.ConnectionFactory;
import util.StatusConexao;
import dao.UnidadeSaudeDAO;
import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.Horario;
import model.agendamento.Agendamento;
import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.LiberacaoVaga;
import model.agendamento.Vacinacao;
import model.enums.PerfilAcesso;
import model.enums.StatusAgendamento;
import model.enums.StatusHorario;
import model.enums.TipoAgendamento;
import model.relatorio.RelatorioBI;
import model.unidade.UnidadeSaude;
import model.usuario.GestorUnidade;
import model.usuario.Paciente;
import model.usuario.ProfissionalSaude;
import model.usuario.Usuario;
import service.AbsenteismoService;
import service.AgendamentoService;
import service.RelatoriosService;
import service.UsuarioService;
import util.SenhaUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes de integração/unidade. Cada teste roda em um banco SQLite novo, criado a partir de
 * database/01_schema_sqlite.sql + 02_seed_sqlite.sql (mesmo modelo físico do MySQL), com os seeds oficiais.
 *
 * IDs dos seeds: gestor 1; profissionais 2 (clínica), 3 (cardio), 7 (vacinas); pacientes 4, 5 (bloqueado), 6.
 * Horários: agenda 1 = ids 1..12 (consultas 20 min, 16/11/2026 08:00); agenda 2 = ids 13..20 (exames 30 min, 13:00);
 * agenda 3 = ids 21..44 (vacinas 5 min, 08:00). Ocupados nos seeds: 1 (consulta confirmada), 13 (exame pendente), 21 (vacina).
 */
class VagaCertaTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 13, 10, 0);

    private Path arquivoBanco;
    private final AgendamentoService agendamentos = new AgendamentoService();
    private final AbsenteismoService absenteismo = new AbsenteismoService();
    private final UsuarioService usuarios = new UsuarioService();

    /**
     * Por padrão os testes usam um SQLite descartável. Para rodar a mesma suíte em MySQL/MariaDB:
     * -Dvagacerta.test.jdbc.url=jdbc:mysql://localhost:3306/vagacerta_test?createDatabaseIfNotExist=true
     * (com -Dvagacerta.db.user / -Dvagacerta.db.password). As tabelas desse banco são recriadas a cada teste.
     */
    private static final String URL_EXTERNA = System.getProperty("vagacerta.test.jdbc.url");

    @BeforeEach
    void criarBancoNovo() throws IOException, java.sql.SQLException {
        if (URL_EXTERNA != null) {
            System.setProperty("vagacerta.jdbc.url", URL_EXTERNA);
            try (java.sql.Connection c = ConnectionFactory.abrir(); java.sql.Statement st = c.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String t : new String[] {"liberacao_vaga", "registro_falta", "vacinacao", "exame", "consulta_clinica",
                        "agendamento", "horario", "agenda_diaria", "gestor_unidade", "profissional_saude", "paciente",
                        "usuario", "unidade_saude"}) {
                    st.execute("DROP TABLE IF EXISTS " + t);
                }
                st.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        } else {
            arquivoBanco = Files.createTempFile("vagacerta-test-", ".db");
            Files.deleteIfExists(arquivoBanco);
            System.setProperty("vagacerta.jdbc.url", "jdbc:sqlite:" + arquivoBanco.toAbsolutePath());
        }
        ConnectionFactory.inicializarBanco();
    }

    @AfterEach
    void removerBanco() throws IOException {
        System.clearProperty("vagacerta.jdbc.url");
        if (arquivoBanco != null) Files.deleteIfExists(arquivoBanco);
    }

    private static ProfissionalSaude profissional(Long id, int cargaHoraria) {
        return new ProfissionalSaude(id, "Dra. Teste", "12345678901", "teste@saude.gov.br",
                SenhaUtil.gerarHash("senha123"), "(44) 99999-0000", true, "CRM-PR 1", "Clínica Geral", cargaHoraria);
    }

    private Agendamento consulta(long horarioId, long pacienteId) {
        return new ConsultaClinica(horarioId, pacienteId, "Consulta de rotina", false);
    }

    // ============================================================ Herança (usuários)
    @Test
    @DisplayName("Herança: subclasses de Usuario reutilizam a base e sobrescrevem perfil()")
    void herancaDeUsuarios() {
        String hash = SenhaUtil.gerarHash("senha123");
        Usuario paciente = new Paciente(1L, "Ana", "111.222.333-44", "ana@email.com", hash, "(44) 1", true,
                "700123456789012", LocalDate.of(1990, 1, 1), 0);
        Usuario prof = profissional(2L, 8);
        Usuario gestor = new GestorUnidade(3L, "Gil", "99988877766", "gil@saude.gov.br", hash, "(44) 3", true, "G-1", "Coordenador");

        assertEquals(PerfilAcesso.PACIENTE, paciente.perfil());
        assertEquals(PerfilAcesso.PROFISSIONAL, prof.perfil());
        assertEquals(PerfilAcesso.GESTOR, gestor.perfil());
        assertEquals("11122233344", paciente.getCpf(), "a máscara do CPF deve ser removida");
        assertTrue(paciente.autenticar("senha123"));
        assertFalse(paciente.autenticar("errada"));
        assertNotEquals("senha123", paciente.getSenhaHash(), "a senha não pode ser guardada em texto");
        assertTrue(paciente.getSenhaHash().length() <= 128, "precisa caber em senha_hash VARCHAR(128)");
    }

    // ============================================================ RN04 - grade dinâmica + composição
    @Test
    @DisplayName("RN04: grade fatiada pela duração padrão (20/30/5 min) e limitada à carga horária")
    void rn04GradeParametrizadaPorProcedimento() {
        ProfissionalSaude prof = profissional(2L, 8);
        LocalDate dia = LocalDate.of(2026, 12, 1);
        LocalTime ini = LocalTime.of(8, 0), fim = LocalTime.of(12, 0);

        assertEquals(12, prof.gerarAgenda(dia, 1, ini, fim, TipoAgendamento.CONSULTA).getHorarios().size());
        assertEquals(8, prof.gerarAgenda(dia, 1, ini, fim, TipoAgendamento.EXAME).getHorarios().size());
        assertEquals(48, prof.gerarAgenda(dia, 1, ini, fim, TipoAgendamento.VACINA).getHorarios().size());

        AgendaDiaria agenda = prof.gerarAgenda(dia, 1, LocalTime.of(8, 0), LocalTime.of(8, 50), TipoAgendamento.CONSULTA);
        assertEquals(2, agenda.getHorarios().size(), "a sobra de 10 min não comporta uma consulta de 20 min");
        assertEquals(LocalTime.of(8, 40), agenda.getHorarios().get(1).getHoraFim());

        assertThrows(RegraNegocioException.class,
                () -> profissional(2L, 4).gerarAgenda(dia, 1, ini, LocalTime.of(14, 0), TipoAgendamento.CONSULTA),
                "turno de 6h excede carga horária de 4h");
    }

    @Test
    @DisplayName("Composição: Horario só existe dentro da AgendaDiaria (lista protegida, nasce LIVRE)")
    void composicaoAgendaHorario() {
        AgendaDiaria agenda = profissional(2L, 8).gerarAgenda(LocalDate.of(2026, 12, 1), 1,
                LocalTime.of(8, 0), LocalTime.of(9, 0), TipoAgendamento.CONSULTA);
        assertTrue(agenda.getHorarios().stream().allMatch(h -> h.getStatus() == StatusHorario.LIVRE));
        assertThrows(UnsupportedOperationException.class, () -> agenda.getHorarios().add(agenda.getHorarios().get(0)));
        assertThrows(RegraNegocioException.class, () -> agenda.gerarHorarios(20), "a grade não pode ser gerada duas vezes");
    }

    @Test
    @DisplayName("RN04: service persiste agenda + horários de forma atômica e impede agenda duplicada no dia")
    void rn04PersistenciaDaGrade() {
        AgendaDiaria salva = agendamentos.gerarAgenda(2, 3, LocalDate.of(2026, 11, 20),
                LocalTime.of(8, 0), LocalTime.of(10, 0), TipoAgendamento.EXAME);
        assertNotEquals(null, salva.getId());
        assertEquals(4, agendamentos.buscarAgenda(salva.getId()).getHorarios().size());
        assertTrue(salva.getHorarios().stream().allMatch(h -> h.getId() != null));

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> agendamentos.gerarAgenda(2, 3,
                LocalDate.of(2026, 11, 20), LocalTime.of(10, 0), LocalTime.of(12, 0), TipoAgendamento.EXAME));
        assertEquals("RN04", e.getCodigo());
    }

    // ============================================================ Polimorfismo (RN02/RN04)
    @Test
    @DisplayName("Polimorfismo: cada procedimento sobrescreve janela, duração, preparo e instruções")
    void polimorfismoDosAgendamentos() {
        List<Agendamento> todos = List.of(
                new ConsultaClinica(10, 4, "Rotina", false),
                new Exame(11, 4, "Hemograma", "Jejum de 8 horas."),
                new Vacinacao(12, 4, "Influenza", "Anual"));

        assertEquals(List.of(24, 48, 0), todos.stream().map(Agendamento::janelaConfirmacaoHoras).toList());
        assertEquals(List.of(20, 30, 5), todos.stream().map(Agendamento::duracaoMinutos).toList());
        assertEquals(List.of(false, true, false), todos.stream().map(Agendamento::exigePreparo).toList());
        assertTrue(todos.get(1).gerarInstrucoesAtendimento().contains("Jejum de 8 horas."));
        assertTrue(todos.get(2).isIsentoConfirmacao());
        assertFalse(todos.get(0).isIsentoConfirmacao());
    }

    @Test
    @DisplayName("RN02: descarte por prazo vencido é decidido polimorficamente (24h, 48h, vacina nunca)")
    void rn02DeveSerAutoliberado() {
        LocalDate dia = LocalDate.of(2026, 11, 16);
        LocalTime hora = LocalTime.of(8, 0);
        Agendamento consulta = new ConsultaClinica(1, 4, "Rotina", false);
        Agendamento exame = new Exame(2, 4, "ECG", "Jejum de 4 horas.");
        Agendamento vacina = new Vacinacao(3, 4, "Febre Amarela", "Dose única");

        LocalDateTime prazoConsulta = LocalDateTime.of(2026, 11, 15, 8, 0);
        assertFalse(consulta.deveSerAutoliberado(dia, hora, prazoConsulta), "no limite exato ainda é válido");
        assertTrue(consulta.deveSerAutoliberado(dia, hora, prazoConsulta.plusSeconds(1)));

        LocalDateTime prazoExame = LocalDateTime.of(2026, 11, 14, 8, 0);
        assertFalse(exame.deveSerAutoliberado(dia, hora, prazoExame));
        assertTrue(exame.deveSerAutoliberado(dia, hora, prazoExame.plusSeconds(1)));
        assertFalse(exame.deveSerAutoliberado(dia, hora, prazoExame.minusHours(1)));

        assertFalse(vacina.deveSerAutoliberado(dia, hora, LocalDateTime.of(2027, 1, 1, 0, 0)), "vacina é isenta");
    }

    // ============================================================ RN01 - exclusividade
    @Test
    @DisplayName("RN01: um horário admite um único agendamento; o segundo é rejeitado")
    void rn01Exclusividade() {
        agendamentos.agendar(consulta(2, 4), true, AGORA);
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> agendamentos.agendar(consulta(2, 6), true, AGORA));
        assertEquals("RN01", e.getCodigo());
    }

    @Test
    @DisplayName("RN01: UNIQUE(horario_id) barra a duplicidade mesmo contornando o service")
    void rn01RestricaoFisicaNoBanco() {
        // horário 1 já tem agendamento nos seeds; inserir direto pelo DAO viola a UNIQUE
        Agendamento duplicado = consulta(1, 6);
        duplicado.iniciarCicloParaTeste();
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> new AgendamentoDAO().inserir(duplicado));
        assertEquals("RN01", e.getCodigo());
    }

    @Test
    @DisplayName("RN01: com várias threads disputando o mesmo horário, só uma reserva vence")
    void rn01Concorrencia() throws Exception {
        int tentativas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger rejeitadas = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            long paciente = (i % 2 == 0) ? 4 : 6;
            futuros.add(pool.submit(() -> {
                try {
                    largada.await();
                    new AgendamentoService().agendar(consulta(3, paciente), true, AGORA);
                    sucessos.incrementAndGet();
                } catch (RegraNegocioException e) {
                    if ("RN01".equals(e.getCodigo())) rejeitadas.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) f.get();
        pool.shutdown();

        assertEquals(1, sucessos.get(), "exatamente um agendamento deve vencer a disputa");
        assertEquals(tentativas - 1, rejeitadas.get());
        assertEquals(1, agendamentos.listar(null, null, null, LocalDate.of(2026, 11, 16)).stream()
                .filter(a -> a.getHorarioId() == 3).count());
    }

    @Test
    @DisplayName("RN01: reserva tira o horário da grade livre; cancelamento o devolve imediatamente")
    void rn01ReservaECancelamento() {
        int livresAntes = agendamentos.listarHorariosLivres(1L, 2L, LocalDate.of(2026, 11, 16), TipoAgendamento.CONSULTA, AGORA).size();
        Agendamento ag = agendamentos.agendar(consulta(4, 4), true, AGORA);
        assertEquals(livresAntes - 1, agendamentos.listarHorariosLivres(1L, 2L, LocalDate.of(2026, 11, 16), TipoAgendamento.CONSULTA, AGORA).size());
        assertEquals(StatusHorario.RESERVADO, horario(4).getStatus());

        agendamentos.cancelar(ag.getId(), LiberacaoVaga.Motivo.CANCELAMENTO_PACIENTE, AGORA);
        assertEquals(StatusHorario.LIVRE, horario(4).getStatus());
        assertEquals(livresAntes, agendamentos.listarHorariosLivres(1L, 2L, LocalDate.of(2026, 11, 16), TipoAgendamento.CONSULTA, AGORA).size());
        // e o mesmo horário pode ser reservado de novo por outra pessoa
        assertEquals(StatusAgendamento.AGENDADO, agendamentos.agendar(consulta(4, 6), true, AGORA).getStatus());
    }

    @Test
    @DisplayName("RN04: procedimento só cabe em horário com a duração padrão correspondente")
    void rn04DuracaoIncompativel() {
        Exame exameEmHorarioDeConsulta = new Exame(5, 4, "ECG", "Jejum de 4 horas.");
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> agendamentos.agendar(exameEmHorarioDeConsulta, true, AGORA));
        assertEquals("RN04", e.getCodigo());
    }

    // ============================================================ RN02 - confirmação e autoliberação
    @Test
    @DisplayName("RN02: motor libera pendentes vencidos, preserva confirmados e vacinas, e a vaga é reofertada")
    void rn02MotorDeAutoliberacao() {
        // seeds: exame 2 (horário 13) pendente, prazo 14/11 13:00; consulta 1 confirmada; vacina 3 isenta
        assertEquals(0, agendamentos.executarMotorAutoliberacao(AGORA).size(), "antes do prazo nada é liberado");

        List<Agendamento> liberados = agendamentos.executarMotorAutoliberacao(LocalDateTime.of(2026, 11, 14, 13, 0, 1));
        assertEquals(1, liberados.size());
        assertEquals(TipoAgendamento.EXAME, liberados.get(0).tipo());
        assertEquals(StatusAgendamento.CANCELADO, liberados.get(0).getStatus());
        assertEquals(StatusHorario.LIVRE, horario(13).getStatus(), "horário volta imediatamente a LIVRE");

        // consulta confirmada e vacina permanecem
        assertEquals(StatusAgendamento.CONFIRMADO, agendamentos.buscar(1).getStatus());
        assertEquals(StatusAgendamento.CONFIRMADO, agendamentos.buscar(3).getStatus());

        // a vaga reofertada aparece na fila pública e pode ser reservada por outro paciente
        assertTrue(agendamentos.listarHorariosLivres(null, null, null, TipoAgendamento.EXAME,
                LocalDateTime.of(2026, 11, 14, 14, 0)).stream().anyMatch(h -> h.horarioId() == 13));
        Agendamento novo = agendamentos.agendar(new Exame(13, 4, "ECG", "Jejum de 4 horas."), true,
                LocalDateTime.of(2026, 11, 14, 14, 0));
        assertEquals(StatusAgendamento.CONFIRMADO, novo.getStatus(), "reserva feita dentro da janela nasce confirmada");
    }

    @Test
    @DisplayName("RN02: confirmar dentro do prazo evita a liberação; fora do prazo é recusado")
    void rn02Confirmacao() {
        Agendamento ag = agendamentos.agendar(consulta(6, 4), true, AGORA);
        assertEquals(StatusAgendamento.AGENDADO, ag.getStatus());
        LocalDateTime prazo = ag.prazoConfirmacao(); // 24h antes do início do horário 6 (09:40 de 16/11)

        RegraNegocioException tarde = assertThrows(RegraNegocioException.class,
                () -> agendamentos.confirmar(ag.getId(), prazo.plusMinutes(1)));
        assertEquals("RN02", tarde.getCodigo());

        agendamentos.confirmar(ag.getId(), prazo.minusHours(1));
        assertEquals(StatusAgendamento.CONFIRMADO, agendamentos.buscar(ag.getId()).getStatus());
        assertEquals(0, agendamentos.executarMotorAutoliberacao(prazo.plusDays(1)).stream()
                .filter(a -> a.getId().equals(ag.getId())).count(), "confirmado não é liberado");
    }

    @Test
    @DisplayName("RN02: reserva feita já dentro da janela é confirmada; vacina nasce isenta")
    void rn02ReservaDentroDaJanelaEVacina() {
        LocalDateTime vespera = LocalDateTime.of(2026, 11, 16, 7, 0);
        assertEquals(StatusAgendamento.CONFIRMADO, agendamentos.agendar(consulta(7, 4), true, vespera).getStatus());
        Agendamento vacina = agendamentos.agendar(new Vacinacao(22, 6, "Hepatite B", "Dose 1"), true, AGORA);
        assertEquals(StatusAgendamento.CONFIRMADO, vacina.getStatus());
        assertEquals(AGORA, vacina.getConfirmadoEm());
    }

    // ============================================================ RN03 - absenteísmo
    @Test
    @DisplayName("RN03: paciente com 3 faltas é bloqueado online, atendido presencialmente e liberado pelo gestor")
    void rn03BloqueioEAcolhimento() {
        Paciente roberto = (Paciente) usuarios.buscar(5);
        assertFalse(roberto.podeAgendarOnline());

        RegraNegocioException bloqueio = assertThrows(RegraNegocioException.class,
                () -> agendamentos.agendar(consulta(8, 5), true, AGORA));
        assertEquals("RN03", bloqueio.getCodigo());

        // presencial (acolhimento) continua permitido
        assertEquals(StatusAgendamento.AGENDADO, agendamentos.agendar(consulta(8, 5), false, AGORA).getStatus());

        // só gestor desbloqueia
        assertEquals("PERMISSAO", assertThrows(RegraNegocioException.class,
                () -> absenteismo.acolhimentoPresencial(5, 2, "Atestado médico", AGORA)).getCodigo());
        assertEquals("VALIDACAO", assertThrows(RegraNegocioException.class,
                () -> absenteismo.acolhimentoPresencial(5, 1, " ", AGORA)).getCodigo());

        assertEquals(3, absenteismo.acolhimentoPresencial(5, 1, "Atestado de internação validado", AGORA));
        assertTrue(absenteismo.podeAgendarOnline(5, AGORA));
        assertEquals(StatusAgendamento.AGENDADO, agendamentos.agendar(consulta(9, 5), true, AGORA).getStatus());
    }

    @Test
    @DisplayName("RN03: três faltas registradas pelo fluxo normal bloqueiam o próprio paciente")
    void rn03BloqueioPorFaltasRegistradas() {
        List<Agendamento> marcados = new ArrayList<>();
        for (long horario = 9; horario <= 11; horario++) marcados.add(agendamentos.agendar(consulta(horario, 4), true, AGORA));

        LocalDateTime depois = LocalDateTime.of(2026, 11, 16, 12, 0);
        for (int i = 0; i < 3; i++) {
            if (i == 2) assertTrue(absenteismo.podeAgendarOnline(4, depois), "ainda apto com 2 faltas");
            absenteismo.registrarFalta(marcados.get(i).getId(), depois);
        }
        assertFalse(absenteismo.podeAgendarOnline(4, depois));
        assertEquals(3, ((Paciente) usuarios.buscar(4)).getFaltasNaoJustificadas());
        assertEquals(StatusAgendamento.FALTA, agendamentos.buscar(marcados.get(0).getId()).getStatus());
        assertEquals(3, absenteismo.listarFaltas(4).size());
    }

    @Test
    @DisplayName("RN03: faltas fora da janela de 6 meses deixam de contar")
    void rn03JanelaDeSeisMeses() {
        // seeds: faltas de Roberto em 10/06, 15/08 e 02/10/2026
        assertFalse(absenteismo.podeAgendarOnline(5, LocalDateTime.of(2026, 11, 13, 10, 0)));
        assertTrue(absenteismo.podeAgendarOnline(5, LocalDateTime.of(2027, 1, 1, 10, 0)), "a de junho já expirou: restam 2");
    }

    @Test
    @DisplayName("Falta/realizado só podem ser lançados depois do início do atendimento")
    void ciclosDeVidaDoAtendimento() {
        Agendamento ag = agendamentos.agendar(consulta(10, 4), true, AGORA);
        assertThrows(RegraNegocioException.class, () -> absenteismo.registrarFalta(ag.getId(), AGORA));
        assertThrows(RegraNegocioException.class, () -> absenteismo.marcarRealizado(ag.getId(), AGORA));
        Agendamento realizado = absenteismo.marcarRealizado(ag.getId(), LocalDateTime.of(2026, 11, 16, 11, 0));
        assertEquals(StatusAgendamento.REALIZADO, realizado.getStatus());
        assertThrows(RegraNegocioException.class, () -> absenteismo.registrarFalta(ag.getId(), LocalDateTime.of(2026, 11, 16, 11, 0)));
    }

    // ============================================================ CRUD
    @Test
    @DisplayName("CRUD de usuários nas três subclasses, com unicidade de CPF/e-mail")
    void crudDeUsuarios() {
        Usuario novo = usuarios.cadastrar(new Paciente(null, "Carla Dias", "123.456.789-09", "carla@email.com",
                "x".repeat(10), "(44) 98888-7777", true, "700111222333444", LocalDate.of(1980, 3, 3), 0), "senha123");
        assertNotEquals(null, novo.getId());

        Usuario lido = usuarios.buscar(novo.getId());
        assertTrue(lido instanceof Paciente);
        assertEquals("12345678909", lido.getCpf());
        assertTrue(usuarios.autenticar("carla@email.com", "senha123").autenticar("senha123"));
        assertEquals("PERMISSAO", assertThrows(RegraNegocioException.class,
                () -> usuarios.autenticar("carla@email.com", "errada")).getCodigo());

        lido.setNome("Carla Dias Souza");
        ((Paciente) lido).setCartaoSus("700111222333555");
        usuarios.atualizar(lido, null);
        assertEquals("Carla Dias Souza", usuarios.buscar(novo.getId()).getNome());
        assertEquals("700111222333555", ((Paciente) usuarios.buscar(novo.getId())).getCartaoSus());

        assertThrows(RegraNegocioException.class, () -> usuarios.cadastrar(new Paciente(null, "Outra", "12345678909",
                "outra@email.com", "x".repeat(10), "(44) 1", true, "700111222333999", LocalDate.of(1970, 1, 1), 0), "senha123"));

        Usuario prof = usuarios.cadastrar(profissional(null, 6), "senha123");
        assertTrue(usuarios.buscar(prof.getId()) instanceof ProfissionalSaude);
        Usuario gestor = usuarios.cadastrar(new GestorUnidade(null, "Gestor Novo", "98765432100", "gn@saude.gov.br",
                "x".repeat(10), "(44) 2", true, "GES-9", "Diretor"), "senha123");
        assertTrue(usuarios.buscar(gestor.getId()) instanceof GestorUnidade);
        assertEquals(1, usuarios.listar(PerfilAcesso.GESTOR).stream().filter(u -> u.getNome().equals("Gestor Novo")).count());

        usuarios.excluir(novo.getId());
        assertThrows(RegraNegocioException.class, () -> usuarios.buscar(novo.getId()));
    }

    @Test
    @DisplayName("CRUD de unidades de saúde")
    void crudDeUnidades() {
        UnidadeSaudeDAO dao = new UnidadeSaudeDAO();
        UnidadeSaude nova = dao.inserir(new UnidadeSaude(null, "UBS Jardim Alvorada", "3456789", "Rua das Flores, 10", "(44) 3000-0000"));
        assertEquals("UBS Jardim Alvorada", dao.buscarPorId(nova.getId()).orElseThrow().getNome());

        nova.setTelefone("(44) 3111-1111");
        dao.atualizar(nova);
        assertEquals("(44) 3111-1111", dao.buscarPorId(nova.getId()).orElseThrow().getTelefone());
        assertEquals(3, dao.listar().size());
        assertThrows(RegraNegocioException.class, () -> dao.inserir(new UnidadeSaude(null, "Duplicada", "3456789", "Rua X", "1")));

        assertTrue(dao.excluir(nova.getId()));
        assertTrue(dao.buscarPorId(nova.getId()).isEmpty());
        assertThrows(RegraNegocioException.class, () -> dao.excluir(1), "UBS 1 possui agendas");
    }

    @Test
    @DisplayName("CRUD de agendamentos: criar, ler, atualizar detalhes e excluir (cascata na especialização)")
    void crudDeAgendamentos() {
        Agendamento criado = agendamentos.agendar(consulta(12, 4), true, AGORA);
        Agendamento lido = agendamentos.buscar(criado.getId());
        assertTrue(lido instanceof ConsultaClinica);
        assertEquals("Consulta de rotina", ((ConsultaClinica) lido).getMotivo());

        agendamentos.atualizarDetalhes(criado.getId(), Map.of("motivo", "Dor de cabeça persistente", "retorno", true));
        ConsultaClinica editada = (ConsultaClinica) agendamentos.buscar(criado.getId());
        assertEquals("Dor de cabeça persistente", editada.getMotivo());
        assertTrue(editada.isRetorno());

        assertEquals(1, agendamentos.listar(4L, null, null, null).stream().filter(a -> a.getId().equals(criado.getId())).count());
        assertTrue(new AgendamentoDAO().excluir(criado.getId()));
        assertTrue(new AgendamentoDAO().buscarPorId(criado.getId()).isEmpty());
    }

    @Test
    @DisplayName("CRUD de agenda: bloquear propaga aos horários livres; excluir só sem agendamentos")
    void crudDeAgenda() {
        AgendaDiaria nova = agendamentos.gerarAgenda(1, 2, LocalDate.of(2026, 12, 2), LocalTime.of(8, 0), LocalTime.of(9, 0), TipoAgendamento.CONSULTA);
        agendamentos.bloquearAgenda(nova.getId(), true);
        assertTrue(agendamentos.buscarAgenda(nova.getId()).getHorarios().stream().allMatch(h -> h.getStatus() == StatusHorario.BLOQUEADO));
        Horario h = agendamentos.buscarAgenda(nova.getId()).getHorarios().get(0);
        assertThrows(RegraNegocioException.class, () -> agendamentos.agendar(consulta(h.getId(), 4), true, AGORA));

        agendamentos.bloquearAgenda(nova.getId(), false);
        agendamentos.agendar(consulta(h.getId(), 4), true, AGORA);
        assertThrows(RegraNegocioException.class, () -> agendamentos.excluirAgenda(nova.getId()), "há agendamento nos horários");

        AgendaDiaria vazia = agendamentos.gerarAgenda(1, 2, LocalDate.of(2026, 12, 3), LocalTime.of(8, 0), LocalTime.of(9, 0), TipoAgendamento.CONSULTA);
        long primeiroHorario = vazia.getHorarios().get(0).getId();
        agendamentos.excluirAgenda(vazia.getId());
        assertTrue(new AgendaDiariaDAO().buscarPorId(vazia.getId()).isEmpty());
        assertTrue(new AgendaDiariaDAO().buscarPorHorario(primeiroHorario).isEmpty(), "horários caem em cascata com a agenda");
    }

    // ============================================================ Persistência / conexão / BI
    @Test
    @DisplayName("Persistência: conexão ativa com o banco e esquema completo (13 tabelas)")
    void conexaoComBancoDeDados() {
        StatusConexao status = ConnectionFactory.testarConexao();
        assertTrue(status.ok(), status.mensagem());
        assertEquals(13, status.tabelas());
        if (URL_EXTERNA == null) assertEquals("SQLite", status.banco());
    }

    @Test
    @DisplayName("Persistência: dados gravados sobrevivem a novas conexões (não são listas em memória)")
    void dadosPersistemEntreConexoes() {
        Agendamento ag = agendamentos.agendar(new Exame(14, 6, "ECG", "Jejum de 4 horas."), true, AGORA);
        Agendamento relido = new AgendamentoService().buscar(ag.getId());
        assertEquals(6L, relido.getPacienteId());
        assertEquals(LocalDate.of(2026, 11, 16), relido.getDataAtendimento());
        assertEquals(StatusHorario.RESERVADO, horario(14).getStatus());
    }

    @Test
    @DisplayName("BI (F6): ocupação, absenteísmo, autoliberação e reaproveitamento")
    void indicadoresDeBI() {
        LocalDate de = LocalDate.of(2026, 6, 1), ate = LocalDate.of(2026, 11, 30);
        RelatorioBI base = new RelatoriosService().gerar(de, ate);
        assertEquals(48, base.totalHorarios);
        assertEquals(7, base.reservados);
        assertEquals(4, base.faltas);
        assertEquals(0, base.autoliberadas);

        LocalDateTime depoisDoPrazo = LocalDateTime.of(2026, 11, 14, 13, 0, 1);
        agendamentos.executarMotorAutoliberacao(depoisDoPrazo);
        agendamentos.agendar(new Exame(13, 4, "ECG", "Jejum de 4 horas."), true, depoisDoPrazo.plusMinutes(5));

        RelatorioBI depois = new RelatoriosService().gerar(de, ate);
        assertEquals(1, depois.autoliberadas);
        assertEquals(1, depois.vagasReaproveitadas);
        assertEquals(100.0, depois.taxaReaproveitamento(), 0.001);
        assertEquals(7, depois.reservados, "a vaga liberada foi reocupada");
        assertTrue(depois.porProfissional.stream().anyMatch(l -> l.nome().startsWith("Dr. Carlos") && l.faltas() == 4));
        assertThrows(RegraNegocioException.class, () -> new RelatoriosService().gerar(ate, de));
    }

    private Horario horario(long horarioId) {
        return new AgendaDiariaDAO().buscarPorHorario(horarioId).orElseThrow().buscarHorario(horarioId).orElseThrow();
    }
}
