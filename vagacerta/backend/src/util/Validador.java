package util;

import model.RegraNegocioException;

/** Validações de entrada reutilizadas pelo domínio. */
public final class Validador {
    private Validador() { }

    public static String exigirTexto(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) {
            throw RegraNegocioException.validacao("O campo '" + campo + "' é obrigatório.");
        }
        String limpo = valor.trim();
        if (limpo.length() > max) {
            throw RegraNegocioException.validacao("O campo '" + campo + "' excede " + max + " caracteres.");
        }
        return limpo;
    }

    /** Remove máscara e exige exatamente {@code tamanho} dígitos. */
    public static String exigirDigitos(String valor, String campo, int tamanho) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.length() != tamanho) {
            throw RegraNegocioException.validacao("O campo '" + campo + "' deve conter " + tamanho + " dígitos.");
        }
        return digitos;
    }

    public static String exigirEmail(String valor) {
        String email = exigirTexto(valor, "e-mail", 120).toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw RegraNegocioException.validacao("E-mail inválido.");
        }
        return email;
    }
}
