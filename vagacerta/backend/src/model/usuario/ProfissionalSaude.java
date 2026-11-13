package model.usuario;

import model.RegraNegocioException;
import model.agenda.AgendaDiaria;
import model.enums.PerfilAcesso;
import model.enums.TipoAgendamento;
import util.Validador;
import java.time.LocalDate;
import java.time.LocalTime;

public class ProfissionalSaude extends Usuario {
    private String registroConselho;
    private String especialidade;
    private int cargaHorariaDiaria;

    public ProfissionalSaude(Long id, String nome, String cpf, String email, String senhaHash, String telefone,
                             boolean ativo, String registroConselho, String especialidade, int cargaHorariaDiaria) {
        super(id, nome, cpf, email, senhaHash, telefone, ativo);
        setRegistroConselho(registroConselho);
        setEspecialidade(especialidade);
        setCargaHorariaDiaria(cargaHorariaDiaria);
    }

    @Override
    public PerfilAcesso perfil() {
        return PerfilAcesso.PROFISSIONAL;
    }

    /** Turno padrão: começa em {@code inicio} e dura a carga horária diária. */
    public AgendaDiaria gerarAgenda(LocalDate data, long unidadeId, LocalTime inicio, TipoAgendamento tipo) {
        return gerarAgenda(data, unidadeId, inicio, inicio.plusHours(cargaHorariaDiaria), tipo);
    }

    /** RN04: gera a grade fatiando o turno [inicio, fim) pela duração padrão do procedimento. */
    public AgendaDiaria gerarAgenda(LocalDate data, long unidadeId, LocalTime inicio, LocalTime fim,
                                    TipoAgendamento tipo) {
        if (getId() == null) {
            throw RegraNegocioException.validacao("Profissional precisa estar persistido para gerar agenda.");
        }
        if (!fim.isAfter(inicio)) {
            throw RegraNegocioException.validacao("O fim do turno deve ser posterior ao início.");
        }
        long minutosTurno = java.time.Duration.between(inicio, fim).toMinutes();
        if (minutosTurno > cargaHorariaDiaria * 60L) {
            throw RegraNegocioException.validacao("O turno excede a carga horária diária do profissional ("
                    + cargaHorariaDiaria + "h).");
        }
        AgendaDiaria agenda = new AgendaDiaria(null, unidadeId, getId(), data, inicio, fim, false);
        agenda.gerarHorarios(tipo.duracaoMinutos());
        return agenda;
    }

    public String getRegistroConselho() { return registroConselho; }
    public void setRegistroConselho(String r) { this.registroConselho = Validador.exigirTexto(r, "registro no conselho", 20); }
    public String getEspecialidade() { return especialidade; }
    public void setEspecialidade(String e) { this.especialidade = Validador.exigirTexto(e, "especialidade", 60); }
    public int getCargaHorariaDiaria() { return cargaHorariaDiaria; }
    public void setCargaHorariaDiaria(int h) {
        if (h < 1 || h > 12) throw RegraNegocioException.validacao("Carga horária diária deve estar entre 1 e 12 horas.");
        this.cargaHorariaDiaria = h;
    }
}
