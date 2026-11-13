import { useState } from 'react';
import { agoraEfetiva, api } from '../services/api';
import { Aviso, Botao, Campo, Selo, Vazio } from '../components/ui';
import { cpfMascara, dataHora, dataLonga, hora } from '../format';
import { useCarga, useRetorno } from '../hooks';
import type { Agendamento, Falta, Perfil, Relatorio, Situacao, TipoInfo, Unidade, Usuario } from '../types';

type Aba = 'indicadores' | 'autoliberacao' | 'absenteismo' | 'agendamentos' | 'cadastros';
const ABAS: { id: Aba; rotulo: string }[] = [
  { id: 'indicadores', rotulo: 'Indicadores' },
  { id: 'autoliberacao', rotulo: 'Autoliberação de vagas' },
  { id: 'absenteismo', rotulo: 'Faltas e acolhimento' },
  { id: 'agendamentos', rotulo: 'Agendamentos' },
  { id: 'cadastros', rotulo: 'Cadastros' },
];

export default function Gestor({ usuario, tick }: { usuario: Usuario; tick: number }) {
  const [aba, setAba] = useState<Aba>('indicadores');
  return (
    <>
      <div className="abas" role="tablist" style={{ padding: 0, marginBottom: '1rem' }}>
        {ABAS.map((a) => (
          <button key={a.id} role="tab" className="aba" aria-selected={aba === a.id} onClick={() => setAba(a.id)}>{a.rotulo}</button>
        ))}
      </div>
      {aba === 'indicadores' && <Indicadores tick={tick} />}
      {aba === 'autoliberacao' && <Autoliberacao tick={tick} />}
      {aba === 'absenteismo' && <Absenteismo gestor={usuario} tick={tick} />}
      {aba === 'agendamentos' && <Agendamentos tick={tick} />}
      {aba === 'cadastros' && <Cadastros />}
    </>
  );
}

/* ------------------------------------------------------------------ indicadores */
function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function Indicadores({ tick }: { tick: number }) {
  const hoje = agoraEfetiva();
  const [de, setDe] = useState(iso(new Date(hoje.getFullYear(), hoje.getMonth() - 5, 1)));
  const [ate, setAte] = useState(iso(new Date(hoje.getFullYear(), hoje.getMonth() + 2, 0)));
  const rel = useCarga(() => api.get<Relatorio>(`/relatorios?de=${de}&ate=${ate}`), [de, ate, tick]);
  const r = rel.dados;
  return (
    <div className="grade">
      <div className="painel">
        <div className="campos">
          <Campo rotulo="De"><input type="date" value={de} onChange={(e) => setDe(e.target.value)} /></Campo>
          <Campo rotulo="Até"><input type="date" value={ate} onChange={(e) => setAte(e.target.value)} /></Campo>
        </div>
        {rel.erro && <Aviso retorno={{ tipo: 'erro', texto: rel.erro }} />}
      </div>
      {r && (
        <>
          <div className="indicadores">
            <div className="indicador azul"><b className="num">{r.taxaOcupacao.toFixed(1)}%</b><span>Ocupação da grade ({r.reservados} de {r.totalHorarios - r.bloqueados} horários)</span></div>
            <div className={`indicador ${r.taxaAbsenteismo > 20 ? 'perigo' : ''}`}><b className="num">{r.taxaAbsenteismo.toFixed(1)}%</b><span>Absenteísmo ({r.faltas} faltas, {r.realizados} atendidos)</span></div>
            <div className="indicador alerta"><b className="num">{r.autoliberadas}</b><span>Vagas autoliberadas por falta de confirmação</span></div>
            <div className="indicador"><b className="num">{r.vagasReaproveitadas}</b><span>Vagas reaproveitadas ({r.taxaReaproveitamento.toFixed(0)}% das devolvidas)</span></div>
          </div>
          <div className="grade dois">
            <section className="painel">
              <h2>Por profissional</h2>
              {r.porProfissional.length === 0 ? <Vazio>Sem agendas no período.</Vazio> : (
                <div className="rolagem">
                  <table>
                    <thead><tr><th>Profissional</th><th className="dir">Horários</th><th className="dir">Reservados</th><th className="dir">Realizados</th><th className="dir">Faltas</th><th className="dir">Absenteísmo</th></tr></thead>
                    <tbody>
                      {r.porProfissional.map((p) => (
                        <tr key={p.profissionalId}>
                          <td>{p.nome}<div className="pequeno suave">{p.especialidade}</div></td>
                          <td className="dir num">{p.horarios}</td><td className="dir num">{p.reservados}</td>
                          <td className="dir num">{p.realizados}</td><td className="dir num">{p.faltas}</td>
                          <td className="dir num">{p.taxaAbsenteismo.toFixed(1)}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
            <section className="painel">
              <h2>Movimento de vagas</h2>
              <table>
                <tbody>
                  <tr><td>Horários livres</td><td className="dir num">{r.livres}</td></tr>
                  <tr><td>Horários bloqueados</td><td className="dir num">{r.bloqueados}</td></tr>
                  <tr><td>Ociosidade</td><td className="dir num">{r.taxaOciosidade.toFixed(1)}%</td></tr>
                  <tr><td>Cancelamentos pelo paciente</td><td className="dir num">{r.cancelamentosPaciente}</td></tr>
                  <tr><td>Cancelamentos pela gestão</td><td className="dir num">{r.cancelamentosGestao}</td></tr>
                  {Object.entries(r.agendamentosPorTipo).map(([t, n]) => (
                    <tr key={t}><td>Agendamentos de {t.toLowerCase()}</td><td className="dir num">{n}</td></tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ autoliberação (RN02) */
function Autoliberacao({ tick }: { tick: number }) {
  const tipos = useCarga(() => api.get<TipoInfo[]>('/tipos'), []);
  const pendentes = useCarga(() => api.get<Agendamento[]>('/agendamentos?status=AGENDADO'), [tick]);
  const { retorno, setRetorno, executar } = useRetorno();
  const [lista, setLista] = useState<Agendamento[] | null>(null);

  async function rodar() {
    try {
      const r = await api.post<{ liberados: number; agendamentos: Agendamento[] }>('/autoliberacao');
      setLista(r.agendamentos);
      setRetorno({ tipo: r.liberados > 0 ? 'ok' : 'info', texto: r.liberados > 0 ? `${r.liberados} vaga(s) devolvida(s) à fila pública.` : 'Nenhuma vaga com prazo vencido neste momento.' });
      pendentes.recarregar();
    } catch (e) {
      await executar(() => Promise.reject(e), '');
    }
  }

  const agora = agoraEfetiva().getTime();
  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <h2>Regras de confirmação (RN02)</h2>
        <table>
          <thead><tr><th>Procedimento</th><th>Confirmar até</th><th>Duração (RN04)</th></tr></thead>
          <tbody>
            {(tipos.dados ?? []).map((t) => (
              <tr key={t.tipo}><td>{t.rotulo}</td><td>{t.janelaConfirmacaoHoras === 0 ? 'Isento de confirmação' : `${t.janelaConfirmacaoHoras} h antes`}</td><td>{t.duracaoMinutos} min</td></tr>
            ))}
          </tbody>
        </table>
        <div className="espaco" />
        <Botao onClick={rodar}>Executar motor de autoliberação</Botao>
        <p className="pequeno suave" style={{ marginTop: '0.5rem' }}>O motor usa o horário atual do sistema (ou o relógio de demonstração, se estiver ativo).</p>
      </section>
      {lista && lista.length > 0 && (
        <section className="painel destaque">
          <h2>Vagas devolvidas agora</h2>
          <ul>{lista.map((a) => <li key={a.id}>{a.tipoRotulo} de {a.pacienteNome} em {dataLonga(a.data)}, {hora(a.horaInicio)}</li>)}</ul>
        </section>
      )}
      <section className="painel">
        <h2>Aguardando confirmação</h2>
        {(pendentes.dados ?? []).length === 0 ? <Vazio>Nenhum agendamento pendente.</Vazio> : (
          <div className="rolagem">
            <table>
              <thead><tr><th>Paciente</th><th>Procedimento</th><th>Atendimento</th><th>Prazo de confirmação</th></tr></thead>
              <tbody>
                {(pendentes.dados ?? []).map((a) => {
                  const vencido = a.prazoConfirmacao ? new Date(a.prazoConfirmacao).getTime() < agora : false;
                  return (
                    <tr key={a.id}>
                      <td>{a.pacienteNome}</td><td>{a.tipoRotulo}</td>
                      <td className="num">{dataLonga(a.data)}, {hora(a.horaInicio)}</td>
                      <td className="num" style={{ color: vencido ? 'var(--vermelho)' : undefined, fontWeight: vencido ? 700 : 400 }}>
                        {a.prazoConfirmacao ? dataHora(a.prazoConfirmacao) : '—'}{vencido && ' (vencido)'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

/* ------------------------------------------------------------------ absenteísmo (RN03) */
function Absenteismo({ gestor, tick }: { gestor: Usuario; tick: number }) {
  const pacientes = useCarga(() => api.get<Usuario[]>('/usuarios?perfil=PACIENTE'), [tick]);
  const [pid, setPid] = useState('');
  const faltas = useCarga(() => (pid ? api.get<Falta[]>(`/pacientes/${pid}/faltas`) : Promise.resolve([])), [pid, tick]);
  const situacao = useCarga(() => (pid ? api.get<Situacao>(`/pacientes/${pid}/situacao`) : Promise.resolve(null)), [pid, tick]);
  const { retorno, setRetorno, executar } = useRetorno();
  const [motivo, setMotivo] = useState('');

  async function agir(acao: () => Promise<unknown>, msg: string) {
    if (await executar(acao, msg)) {
      setMotivo('');
      faltas.recarregar();
      situacao.recarregar();
      pacientes.recarregar();
    }
  }

  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <h2>Pacientes e faltas</h2>
        <Campo rotulo="Paciente">
          <select value={pid} onChange={(e) => setPid(e.target.value)}>
            <option value="">Selecione…</option>
            {(pacientes.dados ?? []).map((p) => (
              <option key={p.id} value={p.id}>{p.nome} — {p.faltasNaoJustificadas ?? 0} falta(s){p.podeAgendarOnline === false ? ' (bloqueado)' : ''}</option>
            ))}
          </select>
        </Campo>
        {situacao.dados && (
          <div className={`aviso ${situacao.dados.podeAgendarOnline ? 'ok' : 'erro'}`}>
            {situacao.dados.podeAgendarOnline
              ? `Apto a agendar online (${situacao.dados.faltasNaoJustificadas} de ${situacao.dados.limite} faltas na janela de ${situacao.dados.janelaMeses} meses).`
              : `Bloqueado para agendamento online: ${situacao.dados.faltasNaoJustificadas} faltas não justificadas em ${situacao.dados.janelaMeses} meses.`}
          </div>
        )}
      </section>
      {pid && (
        <section className="painel">
          <h2>Histórico de faltas</h2>
          {(faltas.dados ?? []).length === 0 ? <Vazio>Sem faltas registradas.</Vazio> : (
            <div className="rolagem">
              <table>
                <thead><tr><th>Data da falta</th><th>Situação</th><th>Justificativa</th></tr></thead>
                <tbody>
                  {(faltas.dados ?? []).map((f) => (
                    <tr key={f.id}>
                      <td className="num">{dataHora(f.dataFalta)}</td>
                      <td>{f.justificada ? <span className="selo st-REALIZADO">Justificada</span> : <span className="selo st-FALTA">Sem justificativa</span>}</td>
                      <td>{f.motivoJustificativa ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="espaco" />
          <h3>Acolhimento presencial</h3>
          <p className="suave">O paciente comparece à UBS com o atestado; o gestor valida e as faltas deixam de contar, restabelecendo o agendamento online.</p>
          <Campo rotulo="Atestado / justificativa validada"><textarea rows={2} value={motivo} onChange={(e) => setMotivo(e.target.value)} /></Campo>
          <Botao
            disabled={motivo.trim() === '' || (faltas.dados ?? []).every((f) => f.justificada)}
            onClick={() => agir(() => api.post(`/pacientes/${pid}/acolhimento`, { gestorId: gestor.id, motivo }), 'Atestado validado: faltas justificadas e agendamento online restabelecido.')}
          >
            Validar atestado e justificar faltas
          </Botao>
        </section>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ agendamentos (CRUD) */
function Agendamentos({ tick }: { tick: number }) {
  const [status, setStatus] = useState('');
  const lista = useCarga(() => api.get<Agendamento[]>(`/agendamentos${status ? `?status=${status}` : ''}`), [status, tick]);
  const { retorno, setRetorno, executar } = useRetorno();
  const [editando, setEditando] = useState<Agendamento | null>(null);
  const [campos, setCampos] = useState<Record<string, string | boolean>>({});

  async function agir(acao: () => Promise<unknown>, msg: string) {
    if (await executar(acao, msg)) {
      setEditando(null);
      lista.recarregar();
    }
  }

  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <Campo rotulo="Filtrar por status">
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todos</option>
            {['AGENDADO', 'CONFIRMADO', 'REALIZADO', 'FALTA'].map((s) => <option key={s}>{s}</option>)}
          </select>
        </Campo>
        <p className="pequeno suave">Agendamentos cancelados ou autoliberados saem da lista: a linha é removida para o horário voltar à grade, e o evento fica no log da F6.</p>
        {(lista.dados ?? []).length === 0 && !lista.carregando ? <Vazio>Nenhum agendamento.</Vazio> : (
          <div className="rolagem">
            <table>
              <thead><tr><th>Atendimento</th><th>Paciente</th><th>Procedimento</th><th>Profissional</th><th>Status</th><th /></tr></thead>
              <tbody>
                {(lista.dados ?? []).map((a) => (
                  <tr key={a.id}>
                    <td className="num">{dataLonga(a.data)}, {hora(a.horaInicio)}</td>
                    <td>{a.pacienteNome}</td><td>{a.tipoRotulo}</td><td>{a.profissionalNome}</td>
                    <td><Selo status={a.status} /></td>
                    <td className="linha fim">
                      {(a.status === 'AGENDADO' || a.status === 'CONFIRMADO') && (
                        <>
                          <Botao pequeno variante="secundario" onClick={() => { setEditando(a); setCampos({ ...a.detalhes }); }}>Editar</Botao>
                          <Botao pequeno variante="perigo" onClick={() => agir(() => api.del(`/agendamentos/${a.id}`), 'Agendamento cancelado pela gestão e horário devolvido à grade.')}>Cancelar</Botao>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {editando && (
        <section className="painel destaque">
          <h2>Editar {editando.tipoRotulo.toLowerCase()} de {editando.pacienteNome}</h2>
          <div className="campos">
            {Object.entries(campos).map(([k, v]) => (
              <Campo key={k} rotulo={k}>
                {typeof v === 'boolean'
                  ? <input type="checkbox" checked={v} onChange={(e) => setCampos({ ...campos, [k]: e.target.checked })} />
                  : <input value={v} onChange={(e) => setCampos({ ...campos, [k]: e.target.value })} />}
              </Campo>
            ))}
          </div>
          <div className="linha">
            <Botao onClick={() => agir(() => api.put(`/agendamentos/${editando.id}`, { detalhes: campos }), 'Agendamento atualizado.')}>Salvar alterações</Botao>
            <Botao variante="secundario" onClick={() => setEditando(null)}>Descartar</Botao>
          </div>
        </section>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ cadastros (CRUD) */
function Cadastros() {
  const [sub, setSub] = useState<'usuarios' | 'unidades'>('usuarios');
  return (
    <>
      <div className="linha" style={{ marginBottom: '1rem' }}>
        <Botao variante={sub === 'usuarios' ? 'primario' : 'secundario'} onClick={() => setSub('usuarios')}>Usuários</Botao>
        <Botao variante={sub === 'unidades' ? 'primario' : 'secundario'} onClick={() => setSub('unidades')}>Unidades de saúde</Botao>
      </div>
      {sub === 'usuarios' ? <CadastroUsuarios /> : <CadastroUnidades />}
    </>
  );
}

const CAMPOS_PERFIL: Record<Perfil, { chave: string; rotulo: string; tipo?: string }[]> = {
  PACIENTE: [{ chave: 'cartaoSus', rotulo: 'Cartão SUS (15 dígitos)' }, { chave: 'dataNascimento', rotulo: 'Data de nascimento', tipo: 'date' }],
  PROFISSIONAL: [{ chave: 'registroConselho', rotulo: 'Registro no conselho' }, { chave: 'especialidade', rotulo: 'Especialidade' }, { chave: 'cargaHorariaDiaria', rotulo: 'Carga horária diária (h)', tipo: 'number' }],
  GESTOR: [{ chave: 'matricula', rotulo: 'Matrícula' }, { chave: 'cargo', rotulo: 'Cargo' }],
};

function CadastroUsuarios() {
  const [filtro, setFiltro] = useState<'' | Perfil>('');
  const lista = useCarga(() => api.get<Usuario[]>(`/usuarios${filtro ? `?perfil=${filtro}` : ''}`), [filtro]);
  const { retorno, setRetorno, executar } = useRetorno();
  const [aberto, setAberto] = useState<'novo' | number | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const perfil = (form.perfil ?? 'PACIENTE') as Perfil;

  function abrirNovo() {
    setForm({ perfil: 'PACIENTE' });
    setAberto('novo');
  }
  function abrirEdicao(u: Usuario) {
    const base: Record<string, string> = { perfil: u.perfil, nome: u.nome, cpf: u.cpf, email: u.email, telefone: u.telefone };
    CAMPOS_PERFIL[u.perfil].forEach((c) => { base[c.chave] = String((u as unknown as Record<string, unknown>)[c.chave] ?? ''); });
    setForm(base);
    setAberto(u.id);
  }
  async function salvar() {
    const corpo: Record<string, unknown> = { ...form };
    if (corpo.cargaHorariaDiaria) corpo.cargaHorariaDiaria = Number(corpo.cargaHorariaDiaria);
    if (aberto === 'novo' ? await executar(() => api.post('/usuarios', corpo), 'Usuário cadastrado.') : await executar(() => api.put(`/usuarios/${aberto}`, corpo), 'Usuário atualizado.')) {
      setAberto(null);
      lista.recarregar();
    }
  }

  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <div className="linha" style={{ justifyContent: 'space-between' }}>
          <Campo rotulo="Perfil">
            <select value={filtro} onChange={(e) => setFiltro(e.target.value as '' | Perfil)}>
              <option value="">Todos</option><option value="PACIENTE">Pacientes</option><option value="PROFISSIONAL">Profissionais</option><option value="GESTOR">Gestores</option>
            </select>
          </Campo>
          <Botao onClick={abrirNovo}>Novo usuário</Botao>
        </div>
        <div className="rolagem">
          <table>
            <thead><tr><th>Nome</th><th>Perfil</th><th>CPF</th><th>E-mail</th><th /></tr></thead>
            <tbody>
              {(lista.dados ?? []).map((u) => (
                <tr key={u.id}>
                  <td>{u.nome}{!u.ativo && <span className="selo st-CANCELADO" style={{ marginLeft: 6 }}>inativo</span>}</td>
                  <td>{u.perfil.charAt(0) + u.perfil.slice(1).toLowerCase()}</td><td className="num">{cpfMascara(u.cpf)}</td><td>{u.email}</td>
                  <td className="linha fim">
                    <Botao pequeno variante="secundario" onClick={() => abrirEdicao(u)}>Editar</Botao>
                    <Botao pequeno variante="perigo" onClick={async () => { if (await executar(() => api.del(`/usuarios/${u.id}`), 'Usuário excluído.')) lista.recarregar(); }}>Excluir</Botao>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {aberto !== null && (
        <section className="painel destaque">
          <h2>{aberto === 'novo' ? 'Novo usuário' : 'Editar usuário'}</h2>
          <div className="campos">
            {aberto === 'novo' && (
              <Campo rotulo="Perfil">
                <select value={perfil} onChange={(e) => setForm({ perfil: e.target.value })}>
                  <option value="PACIENTE">Paciente</option><option value="PROFISSIONAL">Profissional de saúde</option><option value="GESTOR">Gestor da unidade</option>
                </select>
              </Campo>
            )}
            {[{ chave: 'nome', rotulo: 'Nome completo' }, { chave: 'cpf', rotulo: 'CPF (11 dígitos)' }, { chave: 'email', rotulo: 'E-mail', tipo: 'email' }, { chave: 'telefone', rotulo: 'Telefone' }, ...CAMPOS_PERFIL[perfil]].map((c) => (
              <Campo key={c.chave} rotulo={c.rotulo}>
                <input type={(c as { tipo?: string }).tipo ?? 'text'} value={form[c.chave] ?? ''} onChange={(e) => setForm({ ...form, [c.chave]: e.target.value })} />
              </Campo>
            ))}
            <Campo rotulo={aberto === 'novo' ? 'Senha (mín. 6 caracteres)' : 'Nova senha (opcional)'}>
              <input type="password" autoComplete="new-password" value={form.senha ?? ''} onChange={(e) => setForm({ ...form, senha: e.target.value })} />
            </Campo>
          </div>
          <div className="linha">
            <Botao onClick={salvar}>Salvar</Botao>
            <Botao variante="secundario" onClick={() => setAberto(null)}>Descartar</Botao>
          </div>
        </section>
      )}
    </div>
  );
}

function CadastroUnidades() {
  const lista = useCarga(() => api.get<Unidade[]>('/unidades'), []);
  const { retorno, setRetorno, executar } = useRetorno();
  const [aberto, setAberto] = useState<'nova' | number | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const campos = [{ chave: 'nome', rotulo: 'Nome da unidade' }, { chave: 'cnes', rotulo: 'CNES (7 dígitos)' }, { chave: 'endereco', rotulo: 'Endereço' }, { chave: 'telefone', rotulo: 'Telefone' }];

  async function salvar() {
    if (aberto === 'nova' ? await executar(() => api.post('/unidades', form), 'Unidade cadastrada.') : await executar(() => api.put(`/unidades/${aberto}`, form), 'Unidade atualizada.')) {
      setAberto(null);
      lista.recarregar();
    }
  }

  return (
    <div className="grade">
      <Aviso retorno={retorno} aoFechar={() => setRetorno(null)} />
      <section className="painel">
        <div className="linha fim"><Botao onClick={() => { setForm({}); setAberto('nova'); }}>Nova unidade</Botao></div>
        <div className="rolagem">
          <table>
            <thead><tr><th>Unidade</th><th>CNES</th><th>Endereço</th><th>Telefone</th><th /></tr></thead>
            <tbody>
              {(lista.dados ?? []).map((u) => (
                <tr key={u.id}>
                  <td>{u.nome}</td><td className="num">{u.cnes}</td><td>{u.endereco}</td><td className="num">{u.telefone}</td>
                  <td className="linha fim">
                    <Botao pequeno variante="secundario" onClick={() => { setForm({ nome: u.nome, cnes: u.cnes, endereco: u.endereco, telefone: u.telefone }); setAberto(u.id); }}>Editar</Botao>
                    <Botao pequeno variante="perigo" onClick={async () => { if (await executar(() => api.del(`/unidades/${u.id}`), 'Unidade excluída.')) lista.recarregar(); }}>Excluir</Botao>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {aberto !== null && (
        <section className="painel destaque">
          <h2>{aberto === 'nova' ? 'Nova unidade' : 'Editar unidade'}</h2>
          <div className="campos">
            {campos.map((c) => (
              <Campo key={c.chave} rotulo={c.rotulo}><input value={form[c.chave] ?? ''} onChange={(e) => setForm({ ...form, [c.chave]: e.target.value })} /></Campo>
            ))}
          </div>
          <div className="linha">
            <Botao onClick={salvar}>Salvar</Botao>
            <Botao variante="secundario" onClick={() => setAberto(null)}>Descartar</Botao>
          </div>
        </section>
      )}
    </div>
  );
}
