import { useEffect, useState } from 'react';
import { ApiError, api, definirRelogio, mensagemDeErro } from './services/api';
import { Aviso, Botao, Campo } from './components/ui';
import { dataHora, paraInputDataHora } from './format';
import { useCarga } from './hooks';
import Gestor from './pages/Gestor';
import Paciente from './pages/Paciente';
import Profissional from './pages/Profissional';
import type { Saude, Usuario } from './types';

const CONTAS = [
  { email: 'lucas.pereira@email.com', titulo: 'Lucas Pereira', detalhe: 'Paciente apto a agendar (consulta e vacina já marcadas)' },
  { email: 'juliana.rocha@email.com', titulo: 'Juliana Rocha', detalhe: 'Paciente com um exame aguardando confirmação' },
  { email: 'roberto.alves@email.com', titulo: 'Roberto Alves', detalhe: 'Paciente bloqueado por 3 faltas (RN03)' },
  { email: 'carlos.menezes@saude.maringa.gov.br', titulo: 'Dr. Carlos Menezes', detalhe: 'Profissional de saúde — gera e acompanha a agenda' },
  { email: 'mariana.costa@saude.maringa.gov.br', titulo: 'Mariana Costa', detalhe: 'Gestora da unidade — indicadores, faltas e cadastros' },
];

export default function App() {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [tick, setTick] = useState(0);
  const [simulando, setSimulando] = useState(false);
  const [instante, setInstante] = useState(paraInputDataHora(new Date()));
  const saude = useCarga(() => api.get<Saude>('/health'), [tick]);

  useEffect(() => {
    const id = window.setInterval(() => saude.recarregar(), 30000);
    return () => window.clearInterval(id);
  }, [saude.recarregar]);

  /** O relógio precisa ser definido antes de os filhos recarregarem, por isso é feito no evento e não em um efeito. */
  function aplicarRelogio(ativo: boolean, valor: string) {
    definirRelogio(ativo ? valor : null);
    setSimulando(ativo);
    setInstante(valor);
    setTick((t) => t + 1);
  }

  if (!usuario) return <Entrada aoEntrar={setUsuario} saude={saude.dados} erroSaude={saude.erro} />;

  return (
    <>
      <header className="topo">
        <div className="topo-int">
          <div className="marca">VagaCerta <small>agendamento nas UBS</small></div>
          <StatusBanco saude={saude.dados} erro={saude.erro} />
          <details>
            <summary className="pilula" style={{ cursor: 'pointer', background: simulando ? 'var(--ambar-claro)' : undefined }}>
              {simulando ? `Relógio simulado: ${dataHora(`${instante}:00`)}` : 'Relógio de demonstração'}
            </summary>
            <div className="painel" style={{ position: 'absolute', zIndex: 10, maxWidth: 340, marginTop: 6 }}>
              <p className="pequeno suave">Avance o tempo para ver prazos vencerem e a autoliberação devolver vagas, sem esperar horas.</p>
              <Campo rotulo="Data e hora simuladas">
                <input type="datetime-local" value={instante} onChange={(e) => aplicarRelogio(true, e.target.value)} />
              </Campo>
              <Botao variante="secundario" pequeno onClick={() => aplicarRelogio(false, paraInputDataHora(new Date()))}>Voltar à hora real</Botao>
            </div>
          </details>
          <span className="pequeno">{usuario.nome}</span>
          <Botao variante="secundario" pequeno onClick={() => { aplicarRelogio(false, paraInputDataHora(new Date())); setUsuario(null); }}>Sair</Botao>
        </div>
      </header>
      <main className="conteudo">
        <h1>{usuario.perfil === 'PACIENTE' ? 'Meus atendimentos' : usuario.perfil === 'PROFISSIONAL' ? 'Minha agenda' : 'Gestão da unidade'}</h1>
        {usuario.perfil === 'PACIENTE' && <Paciente usuario={usuario} tick={tick} />}
        {usuario.perfil === 'PROFISSIONAL' && <Profissional usuario={usuario} tick={tick} />}
        {usuario.perfil === 'GESTOR' && <Gestor usuario={usuario} tick={tick} />}
      </main>
    </>
  );
}

function StatusBanco({ saude, erro }: { saude: Saude | null; erro: string | null }) {
  if (erro || (saude && !saude.ok)) {
    return <span className="pilula" role="status"><span className="ponto erro" />Sem conexão com o banco</span>;
  }
  if (!saude) return <span className="pilula">Verificando banco…</span>;
  return (
    <span className="pilula" role="status" title={saude.url}>
      <span className="ponto" />
      {saude.banco} conectado · {saude.tabelas} tabelas · {saude.latenciaMs} ms
    </span>
  );
}

function Entrada({ aoEntrar, saude, erroSaude }: { aoEntrar: (u: Usuario) => void; saude: Saude | null; erroSaude: string | null }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [entrando, setEntrando] = useState(false);

  async function entrar(e: string, s: string) {
    setEntrando(true);
    setErro(null);
    try {
      aoEntrar(await api.post<Usuario>('/login', { email: e, senha: s }));
    } catch (x) {
      setErro(x instanceof ApiError ? x.message : mensagemDeErro(x));
    } finally {
      setEntrando(false);
    }
  }

  return (
    <div className="login">
      <section>
        <div className="marca" style={{ marginBottom: '1rem' }}>VagaCerta</div>
        <h1>Confirmou a presença? A vaga é sua. Não pode ir? Libere para quem espera.</h1>
        <p>
          Em Piracicaba (SP), 23,1% das consultas e exames agendados no primeiro trimestre de 2026 terminaram em falta, enquanto a fila de espera seguia crescendo. O
          VagaCerta pede a confirmação antes do atendimento e devolve automaticamente à fila os horários que ninguém confirmou.
        </p>
        <div style={{ marginTop: '1.25rem' }}>
          <StatusBanco saude={saude} erro={erroSaude} />
        </div>
      </section>
      <section className="painel">
        <h2>Entrar</h2>
        <Aviso retorno={erro ? { tipo: 'erro', texto: erro } : null} />
        <Campo rotulo="E-mail"><input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} /></Campo>
        <Campo rotulo="Senha"><input type="password" autoComplete="current-password" value={senha} onChange={(e) => setSenha(e.target.value)} /></Campo>
        <Botao disabled={entrando || !email || !senha} onClick={() => entrar(email, senha)}>{entrando ? 'Entrando…' : 'Entrar'}</Botao>
        <div className="espaco" />
        <h3>Contas de demonstração</h3>
        <p className="pequeno suave">Todas usam a senha <strong>senha123</strong>.</p>
        <div className="contas">
          {CONTAS.map((c) => (
            <button key={c.email} className="conta" disabled={entrando} onClick={() => entrar(c.email, 'senha123')}>
              <strong>{c.titulo}</strong>
              <small>{c.detalhe}</small>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
