package model.usuario;

import model.enums.PerfilAcesso;
import util.Validador;
import java.time.LocalDate;

public class Paciente extends Usuario {
    /** RN03: limite de faltas não justificadas que bloqueia o agendamento online. */
    public static final int LIMITE_FALTAS = 3;
    /** RN03: janela de apuração das faltas, em meses. */
    public static final int JANELA_MESES = 6;

    private String cartaoSus;
    private LocalDate dataNascimento;
    private int faltasNaoJustificadas;

    public Paciente(Long id, String nome, String cpf, String email, String senhaHash, String telefone,
                    boolean ativo, String cartaoSus, LocalDate dataNascimento, int faltasNaoJustificadas) {
        super(id, nome, cpf, email, senhaHash, telefone, ativo);
        setCartaoSus(cartaoSus);
        setDataNascimento(dataNascimento);
        setFaltasNaoJustificadas(faltasNaoJustificadas);
    }

    @Override
    public PerfilAcesso perfil() {
        return PerfilAcesso.PACIENTE;
    }

    /** RN03: três ou mais faltas não justificadas na janela bloqueiam o agendamento remoto. */
    public boolean podeAgendarOnline() {
        return faltasNaoJustificadas < LIMITE_FALTAS;
    }

    public String getCartaoSus() { return cartaoSus; }
    public void setCartaoSus(String cartaoSus) { this.cartaoSus = Validador.exigirDigitos(cartaoSus, "cartão SUS", 15); }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate d) {
        if (d == null || d.isAfter(LocalDate.now())) {
            throw model.RegraNegocioException.validacao("Data de nascimento inválida.");
        }
        this.dataNascimento = d;
    }
    public int getFaltasNaoJustificadas() { return faltasNaoJustificadas; }
    public void setFaltasNaoJustificadas(int n) { this.faltasNaoJustificadas = Math.max(0, n); }
}
