package service;

import dao.UsuarioDAO;
import model.RegraNegocioException;
import model.enums.PerfilAcesso;
import model.usuario.Usuario;
import util.SenhaUtil;
import java.util.List;

/** Cadastro unificado (RF01), autenticação e CRUD de usuários. */
public class UsuarioService {
    private final UsuarioDAO dao = new UsuarioDAO();

    /** Cadastra um usuário já construído (a senha vem em texto e é convertida em hash seguro aqui). */
    public Usuario cadastrar(Usuario novo, String senhaPlana) {
        novo.definirSenha(senhaPlana);
        return dao.inserir(novo);
    }

    public Usuario autenticar(String email, String senha) {
        Usuario u = dao.buscarPorEmail(email == null ? "" : email)
                .orElseThrow(() -> new RegraNegocioException("PERMISSAO", "E-mail ou senha inválidos."));
        if (!u.autenticar(senha)) {
            throw new RegraNegocioException("PERMISSAO", "E-mail ou senha inválidos.");
        }
        return u;
    }

    public Usuario buscar(long id) {
        return dao.buscarPorId(id).orElseThrow(() -> RegraNegocioException.naoEncontrado("Usuário " + id + " não encontrado."));
    }

    public List<Usuario> listar(PerfilAcesso perfil) { return dao.listar(perfil); }

    public void atualizar(Usuario u, String novaSenhaOuNulo) {
        if (novaSenhaOuNulo != null && !novaSenhaOuNulo.isBlank()) u.definirSenha(novaSenhaOuNulo);
        dao.atualizar(u);
    }

    public void excluir(long id) {
        if (!dao.excluir(id)) throw RegraNegocioException.naoEncontrado("Usuário " + id + " não encontrado.");
    }

    /** Hash de senha para seeds/testes que constroem usuários diretamente. */
    public static String hash(String senha) { return SenhaUtil.gerarHash(senha); }
}
