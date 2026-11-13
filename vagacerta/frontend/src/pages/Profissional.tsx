import { useState } from 'react';
import { api } from '../services/api';
import { Aviso, Botao, Campo, Selo, Vazio } from '../components/ui';
import { dataLonga, hora } from '../format';
import { useCarga, useRetorno } from '../hooks';
import type { Agenda, Agendamento, TipoAg, TipoInfo, Unidade, Usuario } from '../types';

function minutos(h: string): number {
  const [a, b] = h.split(':').map(Number);
  return a * 60 + b;
}

export default function Profissional({ usuario, tick }: { usuario: Usuario; tick: number }) {
  const agendas = useCarga(() => api.get<Agenda[]>(`/agendas?profissionalId=${usuario.id}`), [usuario.id, tick]);
  const unidades = useCarga(() => api.get<Unidade[]>('/unidades'), []);
  const tipos = useCarga(() => api.get<TipoInfo[]>('/tipos'), []);
  const { retorno, setRetorno, executar } = useRetorno();
  const [selecionada, setSelecionada] = useState<number | null>(null);

  const [unidadeId, setUnidadeId] = useState('');
  const [data, setData] = useState('');
  const [inicio, setInicio] = useState('08:00');
  const [fim, setFim] = useState('12:00');
  const [tipo, setTipo] = useState<TipoAg>('CONSULTA');

  const info = tipos.dados?.find((t) => t.tipo === tipo);
  const previa = info && minutos(fim) > minutos(inicio) ? Math.floor((minutos(fim) - minutos(inicio)) / info.duracaoMinutos) : 0;

  async function gerar() {
    const ok = await executar(
      () => api.post('/agendas', { unidadeId: Number(unidadeId), profissionalId: usuario.id, data, inicio, fim, tipo }),
      `Grade gerada: ${previa} horários de ${info?.duracaoMinutos} min.`,
    );
    if (ok) agendas.recarregar();
  }

  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <h2>Gerar grade do dia</h2>
        <p className="suave">
          Os horários são calculados pelo turno e pela duração padrão do procedimento. Carga horária cadastrada: {usuario.cargaHorariaDiaria} h/dia.
        </p>
        <div className="campos">
          <Campo rotulo="Unidade">
            <select value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)}>
              <option value="">Selecione…</option>
              {(unidades.dados ?? []).map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
            </select>
          </Campo>
          <Campo rotulo="Data"><input type="date" value={data} onChange={(e) => setData(e.target.value)} /></Campo>
          <Campo rotulo="Início do turno"><input type="time" value={inicio} onChange={(e) => setInicio(e.target.value)} /></Campo>
          <Campo rotulo="Fim do turno"><input type="time" value={fim} onChange={(e) => setFim(e.target.value)} /></Campo>
          <Campo rotulo="Procedimento">
            <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoAg)}>
              {(tipos.dados ?? []).map((t) => <option key={t.tipo} value={t.tipo}>{t.rotulo} ({t.duracaoMinutos} min)</option>)}
            </select>
          </Campo>
        </div>
        <div className="linha">
          <Botao disabled={!unidadeId || !data || previa < 1} onClick={gerar}>Gerar {previa > 0 ? `${previa} horários` : 'grade'}</Botao>
          {previa > 0 && <span className="suave">Prévia: {previa} horários de {info?.duracaoMinutos} min.</span>}
        </div>
      </section>

      <section className="painel">
        <h2>Minhas agendas</h2>
        {agendas.erro && <Aviso retorno={{ tipo: 'erro', texto: agendas.erro }} />}
        {(agendas.dados ?? []).length === 0 && !agendas.carregando ? (
          <Vazio>Você ainda não gerou nenhuma grade.</Vazio>
        ) : (
          <div className="rolagem">
            <table>
              <thead>
                <tr><th>Data</th><th>Turno</th><th>Unidade</th><th className="dir">Ocupação</th><th /></tr>
              </thead>
              <tbody>
                {(agendas.dados ?? []).map((a) => (
                  <tr key={a.id}>
                    <td>{dataLonga(a.data)}</td>
                    <td className="num">{hora(a.turnoInicio)} – {hora(a.turnoFim)}</td>
                    <td>{(unidades.dados ?? []).find((u) => u.id === a.unidadeId)?.nome ?? a.unidadeId}</td>
                    <td className="dir num">{a.reservados}/{a.totalHorarios}{a.bloqueada && ' (bloqueada)'}</td>
                    <td className="linha fim">
                      <Botao pequeno variante="secundario" onClick={() => setSelecionada(selecionada === a.id ? null : a.id)}>
                        {selecionada === a.id ? 'Ocultar' : 'Abrir'}
                      </Botao>
                      <Botao pequeno variante="secundario" onClick={async () => { if (await executar(() => api.post(`/agendas/${a.id}/bloqueio`, { bloqueada: !a.bloqueada }), a.bloqueada ? 'Agenda desbloqueada.' : 'Agenda bloqueada.')) agendas.recarregar(); }}>
                        {a.bloqueada ? 'Desbloquear' : 'Bloquear'}
                      </Botao>
                      <Botao pequeno variante="perigo" onClick={async () => { if (await executar(() => api.del(`/agendas/${a.id}`), 'Agenda excluída.')) { setSelecionada(null); agendas.recarregar(); } }}>
                        Excluir
                      </Botao>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {selecionada !== null && <DetalheAgenda id={selecionada} profissionalId={usuario.id} tick={tick} executar={executar} aoMudar={agendas.recarregar} />}
    </div>
  );
}

function DetalheAgenda({
  id, profissionalId, tick, executar, aoMudar,
}: {
  id: number;
  profissionalId: number;
  tick: number;
  executar: (acao: () => Promise<unknown>, sucesso: string) => Promise<boolean>;
  aoMudar: () => void;
}) {
  const agenda = useCarga(() => api.get<Agenda>(`/agendas/${id}`), [id, tick]);
  const dia = agenda.dados?.data;
  const marcados = useCarga(
    () => (dia ? api.get<Agendamento[]>(`/agendamentos?profissionalId=${profissionalId}&data=${dia}`) : Promise.resolve([])),
    [dia, profissionalId, tick],
  );
  if (!agenda.dados) return null;

  async function agir(acao: () => Promise<unknown>, msg: string) {
    if (await executar(acao, msg)) {
      agenda.recarregar();
      marcados.recarregar();
      aoMudar();
    }
  }

  return (
    <section className="painel">
      <h2>Grade de {dataLonga(agenda.dados.data)}</h2>
      <div className="slots">
        {(agenda.dados.horarios ?? []).map((h) => (
          <div key={h.id} className="slot">
            <b className="num">{hora(h.horaInicio)}</b>
            <span className={`selo st-${h.status}`}>{h.status.charAt(0) + h.status.slice(1).toLowerCase()}</span>
          </div>
        ))}
      </div>
      <h3 style={{ marginTop: '1.25rem' }}>Pacientes do dia</h3>
      {(marcados.dados ?? []).length === 0 ? (
        <Vazio>Nenhum agendamento neste dia.</Vazio>
      ) : (
        <div className="rolagem">
          <table>
            <thead><tr><th>Hora</th><th>Paciente</th><th>Procedimento</th><th>Status</th><th /></tr></thead>
            <tbody>
              {(marcados.dados ?? []).map((a) => (
                <tr key={a.id}>
                  <td className="num">{hora(a.horaInicio)}</td>
                  <td>{a.pacienteNome}</td>
                  <td>{a.tipoRotulo}</td>
                  <td><Selo status={a.status} /></td>
                  <td className="linha fim">
                    {(a.status === 'AGENDADO' || a.status === 'CONFIRMADO') && (
                      <>
                        <Botao pequeno onClick={() => agir(() => api.post(`/agendamentos/${a.id}/realizado`), 'Atendimento registrado como realizado.')}>Realizado</Botao>
                        <Botao pequeno variante="perigo" onClick={() => agir(() => api.post(`/agendamentos/${a.id}/falta`), 'Falta registrada no histórico do paciente.')}>Faltou</Botao>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="pequeno suave">Realizado/Faltou só ficam disponíveis depois do horário (use o relógio de demonstração para avançar o tempo).</p>
    </section>
  );
}
