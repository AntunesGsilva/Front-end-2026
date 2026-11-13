package dao;

/** Falha de infraestrutura/SQL encapsulada para as camadas superiores (sem vazar java.sql). */
public class PersistenciaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final boolean violacaoUnicidade;

    public PersistenciaException(String mensagem, Throwable causa, boolean violacaoUnicidade) {
        super(mensagem, causa);
        this.violacaoUnicidade = violacaoUnicidade;
    }

    public boolean isViolacaoUnicidade() {
        return violacaoUnicidade;
    }
}
