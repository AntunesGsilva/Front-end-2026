package model.usuario;

import model.enums.PerfilAcesso;
import util.SenhaUtil;
import util.Validador;

/** Classe base abstrata da hierarquia de atores (Paciente, ProfissionalSaude, GestorUnidade). */
public abstract class Usuario {
    private Long id;
    private String nome;
    private String cpf;
    private String email;
    private String senhaHash;
    private String telefone;
    private boolean ativo = true;

    protected Usuario(Long id, String nome, String cpf, String email, String senhaHash,
                      String telefone, boolean ativo) {
        this.id = id;
        setNome(nome);
        setCpf(cpf);
        setEmail(email);
        this.senhaHash = Validador.exigirTexto(senhaHash, "senha", 128);
        setTelefone(telefone);
        this.ativo = ativo;
    }

    /** Perfil de acesso da subclasse (polimorfismo). */
    public abstract PerfilAcesso perfil();

    public boolean autenticar(String senha) {
        return ativo && SenhaUtil.confere(senha, senhaHash);
    }

    public void definirSenha(String senhaPlana) {
        this.senhaHash = SenhaUtil.gerarHash(senhaPlana);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = Validador.exigirTexto(nome, "nome", 120); }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = Validador.exigirDigitos(cpf, "CPF", 11); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Validador.exigirEmail(email); }
    public String getSenhaHash() { return senhaHash; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = Validador.exigirTexto(telefone, "telefone", 20); }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
