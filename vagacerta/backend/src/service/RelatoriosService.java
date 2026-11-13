package service;

import dao.RelatorioDAO;
import model.RegraNegocioException;
import model.relatorio.RelatorioBI;
import java.time.LocalDate;

/** F6 - indicadores de Business Intelligence para o gestor. */
public class RelatoriosService {

    public RelatorioBI gerar(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de)) {
            throw RegraNegocioException.validacao("Período inválido: informe início e fim (fim não pode ser anterior ao início).");
        }
        return new RelatorioDAO().consolidar(de, ate);
    }
}
