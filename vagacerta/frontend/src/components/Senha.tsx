import type { ReactNode } from 'react';
import { agoraEfetiva } from '../services/api';
import { dataHora, diaSemana, duracaoHumana, hora } from '../format';
import type { Agendamento } from '../types';
import { Selo } from './ui';

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/**
 * O agendamento como uma "senha" de fila de UBS. O destaque é o prazo de confirmação (RN02):
 * a barra mostra quanto da janela já foi consumida e o texto diz o que acontece com a vaga.
 */
export function Senha({ ag, acoes, mostrarPaciente = false }: { ag: Agendamento; acoes?: ReactNode; mostrarPaciente?: boolean }) {
  const [a, m, d] = ag.data.split('-').map(Number);
  const encerrada = ag.status === 'REALIZADO' || ag.status === 'FALTA' || ag.status === 'CANCELADO';
  return (
    <article className={`senha ${encerrada ? 'encerrada' : ''}`} aria-label={`${ag.tipoRotulo} em ${ag.data}`}>
      <div className="canhoto">
        <span className="mes">{diaSemana(ag.data)}, {MESES[m - 1]}</span>
        <span className="dia num">{String(d).padStart(2, '0')}</span>
        <span className="mes num">{a}</span>
        <span className="hora num">{hora(ag.horaInicio)}</span>
      </div>
      <div className="senha-corpo">
        <div className="linha" style={{ justifyContent: 'space-between' }}>
          <h3>{ag.tipoRotulo}</h3>
          <Selo status={ag.status} />
        </div>
        <div className="pequeno suave">
          {mostrarPaciente && <strong>{ag.pacienteNome} · </strong>}
          {ag.profissionalNome} · {ag.unidadeNome} · {ag.duracaoMinutos} min
        </div>
        <Prazo ag={ag} />
        <p className="instrucao">{ag.instrucoes}</p>
        {acoes && <div className="linha">{acoes}</div>}
      </div>
    </article>
  );
}

function Prazo({ ag }: { ag: Agendamento }) {
  if (ag.status === 'CONFIRMADO') {
    return (
      <div className="prazo-texto" style={{ color: 'var(--azul)' }}>
        {ag.isentoConfirmacao ? 'Vacinação não precisa de confirmação.' : `Presença confirmada${ag.confirmadoEm ? ` em ${dataHora(ag.confirmadoEm)}` : ''}.`}
      </div>
    );
  }
  if (ag.status !== 'AGENDADO' || !ag.prazoConfirmacao) return null;

  const agora = agoraEfetiva().getTime();
  const prazo = new Date(ag.prazoConfirmacao).getTime();
  const inicio = ag.criadoEm ? new Date(ag.criadoEm).getTime() : prazo - ag.janelaConfirmacaoHoras * 3600000;
  const vencido = agora > prazo;
  const pct = Math.min(100, Math.max(0, ((agora - inicio) / Math.max(1, prazo - inicio)) * 100));
  return (
    <div className="prazo">
      <div className={`prazo-texto ${vencido ? 'vencido' : 'correndo'}`}>
        {vencido
          ? 'Prazo vencido: a vaga será devolvida à fila na próxima execução do motor.'
          : `Confirme até ${dataHora(ag.prazoConfirmacao)} (faltam ${duracaoHumana(prazo - agora)}) ou a vaga será liberada.`}
      </div>
      <div className={`prazo-trilho ${vencido ? 'vencido' : ''}`} aria-hidden="true">
        <span style={{ width: `${vencido ? 100 : pct}%` }} />
      </div>
    </div>
  );
}
