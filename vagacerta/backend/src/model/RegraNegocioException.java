package model;

/**
 * Violação de regra de negócio, validação ou permissão.
 * O campo {@code codigo} identifica a regra (RN01..RN04, VALIDACAO, NAO_ENCONTRADO, PERMISSAO).
 */
public class RegraNegocioException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String codigo;

    public RegraNegocioException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public static RegraNegocioException validacao(String mensagem) {
        return new RegraNegocioException("VALIDACAO", mensagem);
    }

    public static RegraNegocioException naoEncontrado(String mensagem) {
        return new RegraNegocioException("NAO_ENCONTRADO", mensagem);
    }
}
