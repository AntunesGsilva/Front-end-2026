package api;

import util.ConnectionFactory;
import dao.PersistenciaException;
import dao.UnidadeSaudeDAO;
import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.agenda.Horario;
import model.agenda.HorarioDisponivel;
import model.agendamento.Agendamento;
import model.agendamento.ConsultaClinica;
import model.agendamento.Exame;
import model.agendamento.LiberacaoVaga;
import model.agendamento.RegistroFalta;
import model.agendamento.Vacinacao;
import model.enums.PerfilAcesso;
import model.enums.StatusAgendamento;
import model.enums.StatusHorario;
import model.enums.TipoAgendamento;
import model.unidade.UnidadeSaude;
import model.usuario.GestorUnidade;
import model.usuario.Paciente;
import model.usuario.ProfissionalSaude;
import model.usuario.Usuario;
import service.AbsenteismoService;
import service.AgendamentoService;
import service.RelatoriosService;
import service.UsuarioService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camada de entrada HTTP/JSON (View/Controller) construída sobre com.sun.net.httpserver, que faz parte do JDK:
 * mantém a restrição de "sem frameworks". O front React consome esta API.
 *
 * Relógio de demonstração: o cabeçalho opcional {@code X-Agora: 2026-11-14T13:00:01} faz as regras
 * temporais (RN02/RN03) rodarem naquele instante, permitindo demonstrar a autoliberação sem esperar horas.
 * Esta API não implementa autenticação por token: é adequada ao escopo acadêmico, não à produção.
 */
public class ApiServer {
    private record Resposta(int status, Object corpo) { }

    private record Ctx(HttpExchange ex, List<String> caminho, Map<String, String> query, Map<String, Object> corpo) {
        String q(String k) { String v = query.get(k); return (v == null || v.isBlank()) ? null : v; }
        Long qLong(String k) { String v = q(k); return v == null ? null : Long.valueOf(v); }
        LocalDate qData(String k) { String v = q(k); return v == null ? null : LocalDate.parse(v); }
        long id(int i) { return Long.parseLong(caminho.get(i)); }
        LocalDateTime agora() {
            String h = ex.getRequestHeaders().getFirst("X-Agora");
            return (h == null || h.isBlank()) ? LocalDateTime.now().withNano(0) : LocalDateTime.parse(h.trim());
        }
    }

    @FunctionalInterface
    private interface Handler { Object tratar(Ctx c); }

    private record Rota(String metodo, Pattern padrao, Handler handler) { }

    private final List<Rota> rotas = new ArrayList<>();
    private final UsuarioService usuarios = new UsuarioService();
    private final AgendamentoService agendamentos = new AgendamentoService();
    private final AbsenteismoService absenteismo = new AbsenteismoService();
    private final RelatoriosService relatorios = new RelatoriosService();
    private final UnidadeSaudeDAO unidades = new UnidadeSaudeDAO();
    private final Path pastaWeb;

    public ApiServer(Path pastaWeb) {
        this.pastaWeb = pastaWeb;
        registrarRotas();
    }

    public HttpServer iniciar(int porta) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.createContext("/", this::despachar);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server;
    }

    // ================================================================= rotas
    private void rota(String metodo, String regex, Handler h) {
        rotas.add(new Rota(metodo, Pattern.compile("^" + regex + "$"), h));
    }

    private void registrarRotas() {
        rota("GET", "/api/health", c -> {
            Map<String, Object> m = ConnectionFactory.testarConexao().toMap();
            m.put("agora", LocalDateTime.now().withNano(0).toString());
            return m;
        });
        rota("GET", "/api/tipos", c -> {
            List<Map<String, Object>> l = new ArrayList<>();
            for (TipoAgendamento t : TipoAgendamento.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tipo", t.name()); m.put("rotulo", t.getRotulo());
                m.put("duracaoMinutos", t.duracaoMinutos()); m.put("janelaConfirmacaoHoras", t.janelaConfirmacaoHoras());
                l.add(m);
            }
            return l;
        });

        // ---- login e usuários
        rota("POST", "/api/login", c -> usuarioMap(usuarios.autenticar(texto(c.corpo, "email"), texto(c.corpo, "senha"))));
        rota("GET", "/api/usuarios", c -> {
            PerfilAcesso p = c.q("perfil") == null ? null : PerfilAcesso.valueOf(c.q("perfil"));
            return usuarios.listar(p).stream().map(ApiServer::usuarioMap).toList();
        });
        rota("GET", "/api/usuarios/(\\d+)", c -> usuarioMap(usuarios.buscar(c.id(0))));
        rota("POST", "/api/usuarios", c -> new Resposta(201, usuarioMap(
                usuarios.cadastrar(construirUsuario(c.corpo), texto(c.corpo, "senha")))));
        rota("PUT", "/api/usuarios/(\\d+)", c -> {
            Usuario u = usuarios.buscar(c.id(0));
            aplicarUsuario(u, c.corpo);
            usuarios.atualizar(u, c.corpo.get("senha") == null ? null : String.valueOf(c.corpo.get("senha")));
            return usuarioMap(usuarios.buscar(u.getId()));
        });
        rota("DELETE", "/api/usuarios/(\\d+)", c -> { usuarios.excluir(c.id(0)); return new Resposta(204, null); });

        // ---- unidades
        rota("GET", "/api/unidades", c -> unidades.listar().stream().map(ApiServer::unidadeMap).toList());
        rota("POST", "/api/unidades", c -> new Resposta(201, unidadeMap(unidades.inserir(new UnidadeSaude(null,
                texto(c.corpo, "nome"), texto(c.corpo, "cnes"), texto(c.corpo, "endereco"), texto(c.corpo, "telefone"))))));
        rota("PUT", "/api/unidades/(\\d+)", c -> {
            UnidadeSaude u = unidades.buscarPorId(c.id(0)).orElseThrow(() -> RegraNegocioException.naoEncontrado("Unidade não encontrada."));
            if (c.corpo.get("nome") != null) u.setNome(texto(c.corpo, "nome"));
            if (c.corpo.get("cnes") != null) u.setCnes(texto(c.corpo, "cnes"));
            if (c.corpo.get("endereco") != null) u.setEndereco(texto(c.corpo, "endereco"));
            if (c.corpo.get("telefone") != null) u.setTelefone(texto(c.corpo, "telefone"));
            unidades.atualizar(u);
            return unidadeMap(u);
        });
        rota("DELETE", "/api/unidades/(\\d+)", c -> {
            if (!unidades.excluir(c.id(0))) throw RegraNegocioException.naoEncontrado("Unidade não encontrada.");
            return new Resposta(204, null);
        });

        // ---- agendas e horários (RN04)
        rota("GET", "/api/agendas", c -> agendamentos.listarAgendas(c.qLong("unidadeId"), c.qLong("profissionalId"),
                c.qData("de"), c.qData("ate")).stream().map(a -> agendaMap(a, false)).toList());
        rota("GET", "/api/agendas/(\\d+)", c -> agendaMap(agendamentos.buscarAgenda(c.id(0)), true));
        rota("POST", "/api/agendas", c -> new Resposta(201, agendaMap(agendamentos.gerarAgenda(
                numero(c.corpo, "unidadeId"), numero(c.corpo, "profissionalId"), LocalDate.parse(texto(c.corpo, "data")),
                LocalTime.parse(texto(c.corpo, "inicio")), LocalTime.parse(texto(c.corpo, "fim")),
                TipoAgendamento.valueOf(texto(c.corpo, "tipo"))), true)));
        rota("POST", "/api/agendas/(\\d+)/bloqueio", c -> {
            agendamentos.bloquearAgenda(c.id(0), Boolean.parseBoolean(String.valueOf(c.corpo.get("bloqueada"))));
            return agendaMap(agendamentos.buscarAgenda(c.id(0)), true);
        });
        rota("DELETE", "/api/agendas/(\\d+)", c -> { agendamentos.excluirAgenda(c.id(0)); return new Resposta(204, null); });
        rota("GET", "/api/horarios-livres", c -> agendamentos.listarHorariosLivres(c.qLong("unidadeId"), c.qLong("profissionalId"),
                c.qData("data"), c.q("tipo") == null ? null : TipoAgendamento.valueOf(c.q("tipo")), c.agora())
                .stream().map(ApiServer::horarioDisponivelMap).toList());

        // ---- agendamentos (RN01, RN02, RN03)
        rota("GET", "/api/agendamentos", c -> {
            StatusAgendamento st = c.q("status") == null ? null : StatusAgendamento.valueOf(c.q("status"));
            return agendamentoLista(agendamentos.listar(c.qLong("pacienteId"), c.qLong("profissionalId"), st, c.qData("data")));
        });
        rota("GET", "/api/agendamentos/(\\d+)", c -> agendamentoLista(List.of(agendamentos.buscar(c.id(0)))).get(0));
        rota("POST", "/api/agendamentos", c -> {
            boolean online = c.corpo.get("online") == null || Boolean.parseBoolean(String.valueOf(c.corpo.get("online")));
            Agendamento criado = agendamentos.agendar(construirAgendamento(c.corpo), online, c.agora());
            return new Resposta(201, agendamentoLista(List.of(criado)).get(0));
        });
        rota("PUT", "/api/agendamentos/(\\d+)", c -> {
            Agendamento a = agendamentos.atualizarDetalhes(c.id(0), mapa(c.corpo.get("detalhes")));
            return agendamentoLista(List.of(a)).get(0);
        });
        rota("DELETE", "/api/agendamentos/(\\d+)", c -> {
            agendamentos.cancelar(c.id(0), LiberacaoVaga.Motivo.CANCELAMENTO_GESTAO, c.agora());
            return new Resposta(204, null);
        });
        rota("POST", "/api/agendamentos/(\\d+)/confirmar", c -> agendamentoLista(List.of(agendamentos.confirmar(c.id(0), c.agora()))).get(0));
        rota("POST", "/api/agendamentos/(\\d+)/cancelar", c -> {
            agendamentos.cancelar(c.id(0), LiberacaoVaga.Motivo.CANCELAMENTO_PACIENTE, c.agora());
            return Map.of("cancelado", true);
        });
        rota("POST", "/api/agendamentos/(\\d+)/falta", c -> faltaMap(absenteismo.registrarFalta(c.id(0), c.agora())));
        rota("POST", "/api/agendamentos/(\\d+)/realizado", c ->
                agendamentoLista(List.of(absenteismo.marcarRealizado(c.id(0), c.agora()))).get(0));
        rota("POST", "/api/autoliberacao", c -> {
            List<Agendamento> liberados = agendamentos.executarMotorAutoliberacao(c.agora());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("executadoEm", c.agora().toString());
            m.put("liberados", liberados.size());
            m.put("agendamentos", agendamentoLista(liberados));
            return m;
        });

        // ---- absenteísmo (RN03)
        rota("GET", "/api/pacientes/(\\d+)/faltas", c -> absenteismo.listarFaltas(c.id(0)).stream().map(ApiServer::faltaMap).toList());
        rota("GET", "/api/pacientes/(\\d+)/situacao", c -> {
            Paciente p = (Paciente) usuarios.buscar(c.id(0));
            boolean pode = absenteismo.podeAgendarOnline(p.getId(), c.agora());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("podeAgendarOnline", pode);
            m.put("faltasNaoJustificadas", ((Paciente) usuarios.buscar(c.id(0))).getFaltasNaoJustificadas());
            m.put("limite", Paciente.LIMITE_FALTAS);
            m.put("janelaMeses", Paciente.JANELA_MESES);
            return m;
        });
        rota("POST", "/api/faltas/(\\d+)/justificar", c -> faltaMap(absenteismo.justificarFalta(
                c.id(0), numero(c.corpo, "gestorId"), texto(c.corpo, "motivo"), c.agora())));
        rota("POST", "/api/pacientes/(\\d+)/acolhimento", c -> Map.of("faltasJustificadas", absenteismo.acolhimentoPresencial(
                c.id(0), numero(c.corpo, "gestorId"), texto(c.corpo, "motivo"), c.agora())));

        // ---- BI (F6)
        rota("GET", "/api/relatorios", c -> {
            LocalDate hoje = c.agora().toLocalDate();
            LocalDate de = c.qData("de") == null ? hoje.minusMonths(6) : c.qData("de");
            LocalDate ate = c.qData("ate") == null ? hoje.plusMonths(1) : c.qData("ate");
            return relatorios.gerar(de, ate).toMap();
        });
    }

    // ================================================================= despacho
    private void despachar(HttpExchange ex) throws IOException {
        try {
            String caminho = ex.getRequestURI().getPath();
            cabecalhosCors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { enviar(ex, 204, null); return; }
            if (!caminho.startsWith("/api/")) { servirEstatico(ex, caminho); return; }

            for (Rota r : rotas) {
                Matcher m = r.padrao.matcher(caminho);
                if (!r.metodo.equals(ex.getRequestMethod()) || !m.matches()) continue;
                List<String> grupos = new ArrayList<>();
                for (int i = 1; i <= m.groupCount(); i++) grupos.add(m.group(i));
                String corpoTexto = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> corpo = corpoTexto.isBlank() ? new LinkedHashMap<>() : Json.lerObjeto(corpoTexto);
                Object saida = r.handler.tratar(new Ctx(ex, grupos, parseQuery(ex.getRequestURI().getRawQuery()), corpo));
                if (saida instanceof Resposta resp) enviar(ex, resp.status, resp.corpo);
                else enviar(ex, 200, saida);
                return;
            }
            enviar(ex, 404, erro("NAO_ENCONTRADO", "Rota não encontrada: " + ex.getRequestMethod() + " " + caminho));
        } catch (RegraNegocioException e) {
            int status = switch (e.getCodigo()) {
                case "NAO_ENCONTRADO" -> 404;
                case "VALIDACAO" -> 400;
                case "PERMISSAO" -> 403;
                default -> 409;
            };
            enviar(ex, status, erro(e.getCodigo(), e.getMessage()));
        } catch (IllegalArgumentException | DateTimeParseException | ClassCastException e) {
            enviar(ex, 400, erro("VALIDACAO", "Requisição inválida: " + e.getMessage()));
        } catch (PersistenciaException e) {
            System.err.println("[API] erro de persistência: " + e.getMessage());
            enviar(ex, 500, erro("PERSISTENCIA", "Falha ao acessar o banco de dados."));
        } catch (RuntimeException e) {
            e.printStackTrace();
            enviar(ex, 500, erro("INTERNO", "Erro interno do servidor."));
        }
    }

    private static Map<String, Object> erro(String codigo, String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("erro", mensagem);
        return m;
    }

    private static void cabecalhosCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,X-Agora");
    }

    private static void enviar(HttpExchange ex, int status, Object corpo) throws IOException {
        if (corpo == null) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        byte[] bytes = Json.escrever(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void servirEstatico(HttpExchange ex, String caminho) throws IOException {
        Path base = pastaWeb == null ? null : pastaWeb.toAbsolutePath().normalize();
        if (base == null || !Files.isDirectory(base)) {
            byte[] msg = ("VagaCerta API ativa. Interface web não encontrada em '" + pastaWeb
                    + "' (rode 'npm run build' ou use 'npm run dev').").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        Path alvo = base.resolve(caminho.equals("/") ? "index.html" : caminho.substring(1)).normalize();
        if (!alvo.startsWith(base) || !Files.isRegularFile(alvo)) alvo = base.resolve("index.html"); // fallback SPA
        String nome = alvo.getFileName().toString();
        String tipo = nome.endsWith(".html") ? "text/html; charset=utf-8" : nome.endsWith(".js") ? "text/javascript; charset=utf-8"
                : nome.endsWith(".css") ? "text/css; charset=utf-8" : nome.endsWith(".svg") ? "image/svg+xml"
                : nome.endsWith(".json") ? "application/json" : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(alvo);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isBlank()) return m;
        for (String par : raw.split("&")) {
            int i = par.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? par : par.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    // ================================================================= entrada
    private static String texto(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null || String.valueOf(v).isBlank()) throw RegraNegocioException.validacao("Campo obrigatório: " + chave);
        return String.valueOf(v);
    }

    private static long numero(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null) throw RegraNegocioException.validacao("Campo obrigatório: " + chave);
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    private static Usuario construirUsuario(Map<String, Object> b) {
        PerfilAcesso perfil = PerfilAcesso.valueOf(texto(b, "perfil"));
        String nome = texto(b, "nome"), cpf = texto(b, "cpf"), email = texto(b, "email"), tel = texto(b, "telefone");
        String temp = "pendente-hash"; // substituído pelo hash real em UsuarioService.cadastrar
        return switch (perfil) {
            case PACIENTE -> new Paciente(null, nome, cpf, email, temp, tel, true, texto(b, "cartaoSus"),
                    LocalDate.parse(texto(b, "dataNascimento")), 0);
            case PROFISSIONAL -> new ProfissionalSaude(null, nome, cpf, email, temp, tel, true, texto(b, "registroConselho"),
                    texto(b, "especialidade"), (int) numero(b, "cargaHorariaDiaria"));
            case GESTOR -> new GestorUnidade(null, nome, cpf, email, temp, tel, true, texto(b, "matricula"), texto(b, "cargo"));
        };
    }

    private static void aplicarUsuario(Usuario u, Map<String, Object> b) {
        if (b.get("nome") != null) u.setNome(texto(b, "nome"));
        if (b.get("cpf") != null) u.setCpf(texto(b, "cpf"));
        if (b.get("email") != null) u.setEmail(texto(b, "email"));
        if (b.get("telefone") != null) u.setTelefone(texto(b, "telefone"));
        if (b.get("ativo") != null) u.setAtivo(Boolean.parseBoolean(String.valueOf(b.get("ativo"))));
        if (u instanceof Paciente p) {
            if (b.get("cartaoSus") != null) p.setCartaoSus(texto(b, "cartaoSus"));
            if (b.get("dataNascimento") != null) p.setDataNascimento(LocalDate.parse(texto(b, "dataNascimento")));
        } else if (u instanceof ProfissionalSaude p) {
            if (b.get("registroConselho") != null) p.setRegistroConselho(texto(b, "registroConselho"));
            if (b.get("especialidade") != null) p.setEspecialidade(texto(b, "especialidade"));
            if (b.get("cargaHorariaDiaria") != null) p.setCargaHorariaDiaria((int) numero(b, "cargaHorariaDiaria"));
        } else if (u instanceof GestorUnidade g) {
            if (b.get("matricula") != null) g.setMatricula(texto(b, "matricula"));
            if (b.get("cargo") != null) g.setCargo(texto(b, "cargo"));
        }
    }

    private static Agendamento construirAgendamento(Map<String, Object> b) {
        TipoAgendamento tipo = TipoAgendamento.valueOf(texto(b, "tipo"));
        long horarioId = numero(b, "horarioId"), pacienteId = numero(b, "pacienteId");
        Map<String, Object> d = mapa(b.get("detalhes"));
        return switch (tipo) {
            case CONSULTA -> new ConsultaClinica(horarioId, pacienteId, texto(d, "motivo"),
                    Boolean.parseBoolean(String.valueOf(d.get("retorno"))));
            case EXAME -> new Exame(horarioId, pacienteId, texto(d, "tipoExame"), texto(d, "orientacaoPreparo"));
            case VACINA -> new Vacinacao(horarioId, pacienteId, texto(d, "imunobiologico"), texto(d, "dose"));
        };
    }

    // ================================================================= saída (DTOs)
    private static Map<String, Object> usuarioMap(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("perfil", u.perfil().name()); m.put("nome", u.getNome());
        m.put("cpf", u.getCpf()); m.put("email", u.getEmail()); m.put("telefone", u.getTelefone()); m.put("ativo", u.isAtivo());
        if (u instanceof Paciente p) {
            m.put("cartaoSus", p.getCartaoSus()); m.put("dataNascimento", p.getDataNascimento());
            m.put("faltasNaoJustificadas", p.getFaltasNaoJustificadas()); m.put("podeAgendarOnline", p.podeAgendarOnline());
        } else if (u instanceof ProfissionalSaude p) {
            m.put("registroConselho", p.getRegistroConselho()); m.put("especialidade", p.getEspecialidade());
            m.put("cargaHorariaDiaria", p.getCargaHorariaDiaria());
        } else if (u instanceof GestorUnidade g) {
            m.put("matricula", g.getMatricula()); m.put("cargo", g.getCargo());
        }
        return m;
    }

    private static Map<String, Object> unidadeMap(UnidadeSaude u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("nome", u.getNome()); m.put("cnes", u.getCnes());
        m.put("endereco", u.getEndereco()); m.put("telefone", u.getTelefone());
        return m;
    }

    private Map<String, Object> agendaMap(AgendaDiaria a, boolean comHorarios) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("unidadeId", a.getUnidadeId()); m.put("profissionalId", a.getProfissionalId());
        m.put("data", a.getData()); m.put("turnoInicio", a.getTurnoInicio()); m.put("turnoFim", a.getTurnoFim());
        m.put("bloqueada", a.isBloqueada());
        m.put("totalHorarios", a.getHorarios().size());
        m.put("livres", a.getHorarios().stream().filter(h -> h.getStatus() == StatusHorario.LIVRE).count());
        m.put("reservados", a.getHorarios().stream().filter(h -> h.getStatus() == StatusHorario.RESERVADO).count());
        if (comHorarios) {
            List<Map<String, Object>> hs = new ArrayList<>();
            for (Horario h : a.getHorarios()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", h.getId()); x.put("horaInicio", h.getHoraInicio()); x.put("horaFim", h.getHoraFim());
                x.put("status", h.getStatus().name());
                hs.add(x);
            }
            m.put("horarios", hs);
        }
        return m;
    }

    private static Map<String, Object> horarioDisponivelMap(HorarioDisponivel h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horarioId", h.horarioId()); m.put("agendaId", h.agendaId()); m.put("data", h.data());
        m.put("horaInicio", h.horaInicio()); m.put("horaFim", h.horaFim()); m.put("duracaoMinutos", h.duracaoMinutos());
        m.put("unidadeId", h.unidadeId()); m.put("unidadeNome", h.unidadeNome());
        m.put("profissionalId", h.profissionalId()); m.put("profissionalNome", h.profissionalNome());
        m.put("especialidade", h.especialidade());
        return m;
    }

    private List<Map<String, Object>> agendamentoLista(List<Agendamento> lista) {
        Map<Long, String> nomes = new HashMap<>();
        for (Usuario u : usuarios.listar(null)) nomes.put(u.getId(), u.getNome());
        Map<Long, String> ubs = new HashMap<>();
        for (UnidadeSaude u : unidades.listar()) ubs.put(u.getId(), u.getNome());
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Agendamento a : lista) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("tipo", a.tipo().name()); m.put("tipoRotulo", a.tipo().getRotulo());
            m.put("status", a.getStatus().name());
            m.put("horarioId", a.getHorarioId());
            m.put("pacienteId", a.getPacienteId()); m.put("pacienteNome", nomes.get(a.getPacienteId()));
            m.put("profissionalId", a.getProfissionalId()); m.put("profissionalNome", nomes.get(a.getProfissionalId()));
            m.put("unidadeId", a.getUnidadeId()); m.put("unidadeNome", ubs.get(a.getUnidadeId()));
            m.put("data", a.getDataAtendimento()); m.put("horaInicio", a.getHoraAtendimento()); m.put("horaFim", a.getHoraFimAtendimento());
            m.put("duracaoMinutos", a.duracaoMinutos());
            m.put("janelaConfirmacaoHoras", a.janelaConfirmacaoHoras());
            m.put("isentoConfirmacao", a.isIsentoConfirmacao());
            m.put("prazoConfirmacao", a.isIsentoConfirmacao() ? null : a.prazoConfirmacao());
            m.put("confirmadoEm", a.getConfirmadoEm());
            m.put("criadoEm", a.getDataCriacao());
            m.put("exigePreparo", a.exigePreparo());
            m.put("instrucoes", a.gerarInstrucoesAtendimento());
            m.put("detalhes", a.detalhes());
            saida.add(m);
        }
        return saida;
    }

    private static Map<String, Object> faltaMap(RegistroFalta f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId()); m.put("pacienteId", f.getPacienteId()); m.put("agendamentoId", f.getAgendamentoId());
        m.put("dataFalta", f.getDataFalta()); m.put("justificada", f.isJustificada());
        m.put("motivoJustificativa", f.getMotivoJustificativa()); m.put("justificadoEm", f.getJustificadoEm());
        m.put("justificadoPor", f.getJustificadoPor());
        return m;
    }
}
