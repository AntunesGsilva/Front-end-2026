package util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senha com PBKDF2-HMAC-SHA256 (JDK puro, sem bibliotecas).
 * Formato armazenado: {@code pbkdf2$iteracoes$salt$hash} (cabe em senha_hash VARCHAR(128)).
 */
public final class SenhaUtil {
    private static final int ITERACOES = 30_000;
    private static final int TAM_HASH_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private SenhaUtil() { }

    public static String gerarHash(String senha) {
        if (senha == null || senha.length() < 6) {
            throw new IllegalArgumentException("A senha deve ter ao menos 6 caracteres.");
        }
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return montar(ITERACOES, salt, derivar(senha, salt, ITERACOES));
    }

    public static boolean confere(String senha, String armazenado) {
        if (senha == null || armazenado == null) return false;
        String[] p = armazenado.split("\\$");
        if (p.length != 4 || !"pbkdf2".equals(p[0])) return false;
        try {
            int it = Integer.parseInt(p[1]);
            byte[] salt = Base64.getUrlDecoder().decode(p[2]);
            byte[] esperado = Base64.getUrlDecoder().decode(p[3]);
            return MessageDigest.isEqual(esperado, derivar(senha, salt, it));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String senha, byte[] salt, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, TAM_HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível na JVM.", e);
        }
    }

    private static String montar(int it, byte[] salt, byte[] hash) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return "pbkdf2$" + it + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash);
    }

    /** Usado apenas para gerar os hashes do seed (salt fixo, para o SQL ser reproduzível). */
    public static String hashComSaltFixo(String senha, String saltTexto) {
        byte[] salt = saltTexto.getBytes(StandardCharsets.UTF_8);
        return montar(ITERACOES, salt, derivar(senha, salt, ITERACOES));
    }
}
