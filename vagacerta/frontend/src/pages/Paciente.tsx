import { useEffect, useMemo, useState } from 'react';
import { api, mensagemDeErro } from '../services/api';
import { Aviso, Botao, Campo, Vazio } from '../components/ui';
import { Senha } from '../components/Senha';
import { dataLonga, hora } from '../format';
import { useCarga, useRetorno } from '../hooks';
import type { Agendamento, HorarioLivre, Situacao, TipoAg, TipoInfo, Unidade, Usuario } from '../types';

const EXAMES: Record<string, string> = {
  'Eletrocardiograma (ECG)': 'Jejum de 4 horas; não ingerir cafeína ou estimulantes.',
  Hemograma: 'Jejum de 8 horas.',
  'Glicemia em jejum': 'Jejum de 8 horas; suspender medicamentos somente com orientação médica.',
  'Ultrassom abdominal': 'Jejum de 6 horas e bexiga cheia.',
};
const VACINAS = ['Febre Amarela', 'Influenza', 'Hepatite B', 'Tétano (dT)', 'HPV'];
const DOSES = ['Dose única', '1ª dose', '2ª dose', '3ª dose', 'Reforço'];

type Aba = 'meus' | 'agendar';

export default function Paciente({ usuario, tick }: { usuario: Usuario; tick: number }) {
  const [aba, setAba] = useState<Aba>('meus');
  const { retorno, setRetorno, executar } = useRetorno();
  const situacao = useCarga(() => api.get<Situacao>(`/pacientes/${usuario.id}/situacao`), [usuario.id, tick]);
  const bloqueado = situacao.dados ? !situacao.dados.podeAgendarOnline : false;

  return (
    <>
      <div className="abas" role="tablist" style={{ padding: 0, marginBottom: '1rem' }}>
        <button role="tab" className="aba" aria-selected={aba === 'meus'} onClick={() => setAba('meus')}>Meus agendamentos</button>
        <button role="tab" className="aba" aria-selected={aba === 'agendar'} onClick={() => setAba('agendar')}>Agendar</button>
      </div>
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      {situacao.dados && <SituacaoFaltas s={situacao.dados} />}
      {aba === 'meus' ? (
        <MeusAgendamentos usuario={usuario} tick={tick} executar={executar} aoAgendar={() => setAba('agendar')} aoMudar={situacao.recarregar} />
      ) : (
        <NovoAgendamento
          usuario={usuario}
          tick={tick}
          bloqueado={bloqueado}
          aoAgendado={(msg) => {
            setRetorno({ tipo: 'ok', texto: msg });
            situacao.recarregar();
            setAba('meus');
          }}
          aoErro={(msg) => setRetorno({ tipo: 'erro', texto: msg })}
        />
      )}
    </>
  );
}

function SituacaoFaltas({ s }: { s: Situacao }) {
  const bloqueado = !s.podeAgendarOnline;
  if (!bloqueado && s.faltasNaoJustificadas === 0) return null;
  return (
    <div className={`aviso ${bloqueado ? 'erro' : 'alerta'}`}>
      <div className="linha">
        <div className="faltas" aria-hidden="true">
          {Array.from({ length: s.limite }, (_, i) => (
            <span key={i} className={`falta-marca ${i < s.faltasNaoJustificadas ? 'cheia' : ''}`} />
          ))}
        </div>
        <span style={{ flex: 1 }}>
          {bloqueado
            ? `Agendamento online bloqueado: ${s.faltasNaoJustificadas} faltas sem justificativa nos últimos ${s.janelaMeses} meses. Procure o acolhimento da UBS com o atestado para voltar a agendar pelo sistema (RN03).`
            : `Você tem ${s.faltasNaoJustificadas} de ${s.limite} faltas permitidas nos últimos ${s.janelaMeses} meses. Avise ou cancele quando não puder comparecer.`}
        </span>
      </div>
    </div>
  );
}

function MeusAgendamentos({
  usuario,
  tick,
  executar,
  aoAgendar,
  aoMudar,
}: {
  usuario: Usuario;
  tick: number;
  executar: (acao: () => Promise<unknown>, sucesso: string) => Promise<boolean>;
  aoAgendar: () => void;
  aoMudar: () => void;
}) {
  const { dados, erro, carregando, recarregar } = useCarga(
    () => api.get<Agendamento[]>(`/agendamentos?pacienteId=${usuario.id}`),
    [usuario.id, tick],
  );
  const { abertos, encerrados } = useMemo(() => {
    const lista = dados ?? [];
    const chave = (a: Agendamento) => `${a.data} ${a.horaInicio}`;
    return {
      abertos: lista.filter((a) => a.status === 'AGENDADO' || a.status === 'CONFIRMADO').sort((x, y) => chave(x).localeCompare(chave(y))),
      encerrados: lista.filter((a) => !(a.status === 'AGENDADO' || a.status === 'CONFIRMADO')).sort((x, y) => chave(y).localeCompare(chave(x))),
    };
  }, [dados]);

  async function agir(acao: () => Promise<unknown>, msg: string) {
    if (await executar(acao, msg)) {
      recarregar();
      aoMudar();
    }
  }

  if (erro) return <Aviso retorno={{ tipo: 'erro', texto: erro }} />;
  if (carregando && !dados) return <p className="suave">Carregando seus agendamentos…</p>;

  return (
    <>
      <h2>Próximos atendimentos</h2>
      {abertos.length === 0 ? (
        <Vazio>
          <p>Você não tem atendimentos marcados.</p>
          <Botao onClick={aoAgendar}>Agendar agora</Botao>
        </Vazio>
      ) : (
        <div className="senhas">
          {abertos.map((a) => (
            <Senha
              key={a.id}
              ag={a}
              acoes={
                <>
                  {a.status === 'AGENDADO' && !a.isentoConfirmacao && (
                    <Botao onClick={() => agir(() => api.post(`/agendamentos/${a.id}/confirmar`), 'Presença confirmada. A vaga está garantida.')}>
                      Confirmar presença
                    </Botao>
                  )}
                  <Botao variante="perigo" onClick={() => agir(() => api.post(`/agendamentos/${a.id}/cancelar`), 'Agendamento cancelado. A vaga foi devolvida para outra pessoa.')}>
                    Cancelar
                  </Botao>
                </>
              }
            />
          ))}
        </div>
      )}
      {encerrados.length > 0 && (
        <>
          <div className="espaco" />
          <h2>Histórico</h2>
          <div className="senhas">
            {encerrados.map((a) => (
              <Senha key={a.id} ag={a} />
            ))}
          </div>
        </>
      )}
    </>
  );
}

function NovoAgendamento({
  usuario,
  tick,
  bloqueado,
  aoAgendado,
  aoErro,
}: {
  usuario: Usuario;
  tick: number;
  bloqueado: boolean;
  aoAgendado: (msg: string) => void;
  aoErro: (msg: string) => void;
}) {
  const tipos = useCarga(() => api.get<TipoInfo[]>('/tipos'), []);
  const unidades = useCarga(() => api.get<Unidade[]>('/unidades'), []);
  const [tipo, setTipo] = useState<TipoAg>('CONSULTA');
  const [unidadeId, setUnidadeId] = useState('');
  const [data, setData] = useState('');
  const [escolhido, setEscolhido] = useState<HorarioLivre | null>(null);
  const [enviando, setEnviando] = useState(false);

  const [motivo, setMotivo] = useState('');
  const [retorno, setRetornoConsulta] = useState(false);
  const [tipoExame, setTipoExame] = useState(Object.keys(EXAMES)[0]);
  const [preparo, setPreparo] = useState(EXAMES[Object.keys(EXAMES)[0]]);
  const [vacina, setVacina] = useState(VACINAS[0]);
  const [dose, setDose] = useState(DOSES[0]);

  const query = `/horarios-livres?tipo=${tipo}${unidadeId ? `&unidadeId=${unidadeId}` : ''}${data ? `&data=${data}` : ''}`;
  const horarios = useCarga(() => api.get<HorarioLivre[]>(query), [query, tick]);

  useEffect(() => setEscolhido(null), [tipo, unidadeId, data]);

  const porDia = useMemo(() => {
    const mapa = new Map<string, HorarioLivre[]>();
    (horarios.dados ?? []).forEach((h) => mapa.set(h.data, [...(mapa.get(h.data) ?? []), h]));
    return [...mapa.entries()];
  }, [horarios.dados]);

  const info = tipos.dados?.find((t) => t.tipo === tipo);

  async function reservar() {
    if (!escolhido) return;
    const detalhes =
      tipo === 'CONSULTA' ? { motivo, retorno } : tipo === 'EXAME' ? { tipoExame, orientacaoPreparo: preparo } : { imunobiologico: vacina, dose };
    setEnviando(true);
    try {
      const criado = await api.post<Agendamento>('/agendamentos', { tipo, horarioId: escolhido.horarioId, pacienteId: usuario.id, online: true, detalhes });
      aoAgendado(
        criado.status === 'CONFIRMADO'
          ? `Horário reservado e confirmado: ${criado.tipoRotulo} em ${dataLonga(criado.data)}, ${hora(criado.horaInicio)}.`
          : `Horário reservado. Confirme sua presença até ${criado.prazoConfirmacao?.replace('T', ' às ')} para não perder a vaga.`,
      );
    } catch (e) {
      aoErro(mensagemDeErro(e));
      horarios.recarregar();
      setEscolhido(null);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="grade">
      <section>
        <h2>1. O que você precisa?</h2>
        <div className="tipos">
          {(tipos.dados ?? []).map((t) => (
            <button key={t.tipo} className="tipo" aria-pressed={tipo === t.tipo} onClick={() => setTipo(t.tipo)}>
              <strong>{t.rotulo}</strong>
              <span>
                {t.duracaoMinutos} min ·{' '}
                {t.janelaConfirmacaoHoras === 0 ? 'não precisa confirmar' : `confirmar até ${t.janelaConfirmacaoHoras} h antes`}
              </span>
            </button>
          ))}
        </div>
      </section>

      <section className="painel">
        <h2>2. Escolha o dia e o horário</h2>
        <div className="campos">
          <Campo rotulo="Unidade de saúde">
            <select value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)}>
              <option value="">Todas as unidades</option>
              {(unidades.dados ?? []).map((u) => (
                <option key={u.id} value={u.id}>{u.nome}</option>
              ))}
            </select>
          </Campo>
          <Campo rotulo="Data" dica="Deixe em branco para ver todas as datas.">
            <input type="date" value={data} onChange={(e) => setData(e.target.value)} />
          </Campo>
        </div>
        {horarios.erro && <Aviso retorno={{ tipo: 'erro', texto: horarios.erro }} />}
        {!horarios.erro && porDia.length === 0 && !horarios.carregando && (
          <Vazio>Nenhum horário livre para {info?.rotulo.toLowerCase() ?? 'este procedimento'} com esses filtros.</Vazio>
        )}
        {porDia.map(([dia, lista]) => (
          <div key={dia} className="dia-grupo">
            <h3>{dataLonga(dia)}</h3>
            {[...new Set(lista.map((h) => `${h.profissionalNome} · ${h.unidadeNome}`))].map((quem) => (
              <div key={quem} style={{ marginBottom: '0.6rem' }}>
                <div className="pequeno suave">{quem}</div>
                <div className="horarios">
                  {lista
                    .filter((h) => `${h.profissionalNome} · ${h.unidadeNome}` === quem)
                    .map((h) => (
                      <button key={h.horarioId} className="horario num" aria-pressed={escolhido?.horarioId === h.horarioId} onClick={() => setEscolhido(h)}>
                        {hora(h.horaInicio)}
                      </button>
                    ))}
                </div>
              </div>
            ))}
          </div>
        ))}
      </section>

      {escolhido && (
        <section className="painel destaque">
          <h2>3. Confirme os dados</h2>
          <p>
            <strong>{info?.rotulo}</strong> com {escolhido.profissionalNome} ({escolhido.especialidade}) em {dataLonga(escolhido.data)}, às{' '}
            {hora(escolhido.horaInicio)}, {escolhido.unidadeNome}.
          </p>
          {tipo === 'CONSULTA' && (
            <div className="campos">
              <Campo rotulo="Motivo da consulta">
                <input value={motivo} maxLength={160} onChange={(e) => setMotivo(e.target.value)} placeholder="Ex.: controle da pressão" />
              </Campo>
              <Campo rotulo="É um retorno?">
                <input type="checkbox" checked={retorno} onChange={(e) => setRetornoConsulta(e.target.checked)} />
              </Campo>
            </div>
          )}
          {tipo === 'EXAME' && (
            <div className="campos">
              <Campo rotulo="Exame">
                <select value={tipoExame} onChange={(e) => { setTipoExame(e.target.value); setPreparo(EXAMES[e.target.value]); }}>
                  {Object.keys(EXAMES).map((x) => <option key={x}>{x}</option>)}
                </select>
              </Campo>
              <Campo rotulo="Preparo necessário" dica="Exames têm janela de 48 h porque exigem preparo.">
                <textarea rows={2} value={preparo} onChange={(e) => setPreparo(e.target.value)} />
              </Campo>
            </div>
          )}
          {tipo === 'VACINA' && (
            <div className="campos">
              <Campo rotulo="Vacina">
                <select value={vacina} onChange={(e) => setVacina(e.target.value)}>{VACINAS.map((x) => <option key={x}>{x}</option>)}</select>
              </Campo>
              <Campo rotulo="Dose">
                <select value={dose} onChange={(e) => setDose(e.target.value)}>{DOSES.map((x) => <option key={x}>{x}</option>)}</select>
              </Campo>
            </div>
          )}
          {bloqueado && <Aviso retorno={{ tipo: 'erro', texto: 'Agendamento online bloqueado (RN03). Procure o acolhimento da UBS.' }} />}
          <Botao disabled={enviando || bloqueado || (tipo === 'CONSULTA' && motivo.trim() === '')} onClick={reservar}>
            {enviando ? 'Reservando…' : 'Reservar horário'}
          </Botao>
        </section>
      )}
    </div>
  );
}
