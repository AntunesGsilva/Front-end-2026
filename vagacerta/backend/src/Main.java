
import api.ApiServer;
import util.ConnectionFactory;
import util.StatusConexao;
import view.CliInterativa;
import view.DemoCompleta;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Ponto de entrada do VagaCerta.
 *   (sem argumentos)   demonstração ponta a ponta automatizada (banco SQLite descartável, se nada estiver configurado)
 *   --interactive      modo terminal interativo (CLI)
 *   --api [porta]      API HTTP/JSON (padrão 8080) que também serve o front compilado (../frontend/dist)
 *   --db-check         testa a conexão com o banco configurado e valida o esquema
 */
public final class Main {
    private Main() { }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        String modo = args.length == 0 ? "--demo" : args[0];
        switch (modo) {
            case "--interactive" -> {
                ConnectionFactory.inicializarBanco();
                new CliInterativa().executar();
            }
            case "--api" -> iniciarApi(args);
            case "--db-check" -> verificarBanco();
            case "--demo" -> new DemoCompleta().executar();
            default -> {
                System.out.println("Uso: java -cp \"bin:lib/*\" Main [--interactive | --api [porta] | --db-check]");
                System.exit(modo.equals("--help") ? 0 : 1);
            }
        }
    }

    private static void verificarBanco() {
        System.out.println("Banco configurado: " + ConnectionFactory.url() + " (" + ConnectionFactory.dialeto() + ")");
        try {
            ConnectionFactory.inicializarBanco();
        } catch (RuntimeException e) {
            System.out.println("Falha ao inicializar o esquema: " + e.getMessage());
        }
        StatusConexao s = ConnectionFactory.testarConexao();
        System.out.println((s.ok() ? "[OK]   " : "[FALHA] ") + s.mensagem());
        System.out.printf("Servidor: %s %s | tabelas: %d/13 | latência: %d ms%n", s.banco(), s.versao(), s.tabelas(), s.latenciaMs());
        System.exit(s.ok() && s.tabelas() == 13 ? 0 : 1);
    }

    private static void iniciarApi(String[] args) throws Exception {
        ConnectionFactory.inicializarBanco();
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String web = System.getenv().getOrDefault("VAGACERTA_WEB_DIR", "../frontend/dist");
        new ApiServer(Path.of(web)).iniciar(porta);
        StatusConexao s = ConnectionFactory.testarConexao();
        System.out.println("VagaCerta API em http://localhost:" + porta + "  |  banco: " + s.banco() + " (" + s.tabelas() + " tabelas)");
        System.out.println("Interface web: http://localhost:" + porta + "/  (pasta '" + web + "')   |   Ctrl+C para encerrar");
    }
}
