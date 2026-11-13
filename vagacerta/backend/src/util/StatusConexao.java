package util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resultado do diagnóstico de conexão (usado por --db-check e GET /api/health). */
public record StatusConexao(boolean ok, String banco, String versao, String url, int tabelas,
                            long latenciaMs, String mensagem) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("banco", banco);
        m.put("versao", versao);
        m.put("url", url);
        m.put("tabelas", tabelas);
        m.put("latenciaMs", latenciaMs);
        m.put("mensagem", mensagem);
        return m;
    }
}
