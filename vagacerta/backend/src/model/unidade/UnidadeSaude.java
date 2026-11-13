package model.unidade;

import util.Validador;

public class UnidadeSaude {
    private Long id;
    private String nome;
    private String cnes;
    private String endereco;
    private String telefone;

    public UnidadeSaude(Long id, String nome, String cnes, String endereco, String telefone) {
        this.id = id;
        setNome(nome);
        setCnes(cnes);
        setEndereco(endereco);
        setTelefone(telefone);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String n) { this.nome = Validador.exigirTexto(n, "nome da unidade", 120); }
    public String getCnes() { return cnes; }
    public void setCnes(String c) { this.cnes = Validador.exigirDigitos(c, "CNES", 7); }
    public String getEndereco() { return endereco; }
    public void setEndereco(String e) { this.endereco = Validador.exigirTexto(e, "endereço", 160); }
    public String getTelefone() { return telefone; }
    public void setTelefone(String t) { this.telefone = Validador.exigirTexto(t, "telefone", 20); }
}
