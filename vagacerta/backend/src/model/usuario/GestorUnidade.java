package model.usuario;

import model.enums.PerfilAcesso;
import util.Validador;

public class GestorUnidade extends Usuario {
    private String matricula;
    private String cargo;

    public GestorUnidade(Long id, String nome, String cpf, String email, String senhaHash, String telefone,
                         boolean ativo, String matricula, String cargo) {
        super(id, nome, cpf, email, senhaHash, telefone, ativo);
        setMatricula(matricula);
        setCargo(cargo);
    }

    @Override
    public PerfilAcesso perfil() {
        return PerfilAcesso.GESTOR;
    }

    public String getMatricula() { return matricula; }
    public void setMatricula(String m) { this.matricula = Validador.exigirTexto(m, "matrícula", 20); }
    public String getCargo() { return cargo; }
    public void setCargo(String c) { this.cargo = Validador.exigirTexto(c, "cargo", 60); }
}
