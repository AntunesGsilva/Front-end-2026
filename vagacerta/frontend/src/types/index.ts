export type Perfil = 'PACIENTE' | 'PROFISSIONAL' | 'GESTOR';
export type TipoAg = 'CONSULTA' | 'EXAME' | 'VACINA';
export type StatusAg = 'AGENDADO' | 'CONFIRMADO' | 'REALIZADO' | 'FALTA' | 'CANCELADO';

export interface Usuario {
  id: number;
  perfil: Perfil;
  nome: string;
  cpf: string;
  email: string;
  telefone: string;
  ativo: boolean;
  cartaoSus?: string;
  dataNascimento?: string;
  faltasNaoJustificadas?: number;
  podeAgendarOnline?: boolean;
  registroConselho?: string;
  especialidade?: string;
  cargaHorariaDiaria?: number;
  matricula?: string;
  cargo?: string;
}

export interface Unidade {
  id: number;
  nome: string;
  cnes: string;
  endereco: string;
  telefone: string;
}

export interface TipoInfo {
  tipo: TipoAg;
  rotulo: string;
  duracaoMinutos: number;
  janelaConfirmacaoHoras: number;
}

export interface Agendamento {
  id: number;
  tipo: TipoAg;
  tipoRotulo: string;
  status: StatusAg;
  horarioId: number;
  pacienteId: number;
  pacienteNome: string;
  profissionalId: number;
  profissionalNome: string;
  unidadeId: number;
  unidadeNome: string;
  data: string;
  horaInicio: string;
  horaFim: string;
  duracaoMinutos: number;
  janelaConfirmacaoHoras: number;
  isentoConfirmacao: boolean;
  prazoConfirmacao: string | null;
  confirmadoEm: string | null;
  criadoEm: string | null;
  exigePreparo: boolean;
  instrucoes: string;
  detalhes: Record<string, string | boolean>;
}

export interface HorarioLivre {
  horarioId: number;
  agendaId: number;
  data: string;
  horaInicio: string;
  horaFim: string;
  duracaoMinutos: number;
  unidadeId: number;
  unidadeNome: string;
  profissionalId: number;
  profissionalNome: string;
  especialidade: string;
}

export interface HorarioAgenda {
  id: number;
  horaInicio: string;
  horaFim: string;
  status: 'LIVRE' | 'RESERVADO' | 'BLOQUEADO';
}

export interface Agenda {
  id: number;
  unidadeId: number;
  profissionalId: number;
  data: string;
  turnoInicio: string;
  turnoFim: string;
  bloqueada: boolean;
  totalHorarios: number;
  livres: number;
  reservados: number;
  horarios?: HorarioAgenda[];
}

export interface Falta {
  id: number;
  pacienteId: number;
  agendamentoId: number;
  dataFalta: string;
  justificada: boolean;
  motivoJustificativa: string | null;
  justificadoEm: string | null;
  justificadoPor: number | null;
}

export interface Saude {
  ok: boolean;
  banco: string;
  versao: string;
  url: string;
  tabelas: number;
  latenciaMs: number;
  mensagem: string;
}

export interface Situacao {
  podeAgendarOnline: boolean;
  faltasNaoJustificadas: number;
  limite: number;
  janelaMeses: number;
}

export interface Relatorio {
  de: string;
  ate: string;
  totalHorarios: number;
  reservados: number;
  livres: number;
  bloqueados: number;
  realizados: number;
  faltas: number;
  autoliberadas: number;
  cancelamentosPaciente: number;
  cancelamentosGestao: number;
  vagasReaproveitadas: number;
  taxaOcupacao: number;
  taxaOciosidade: number;
  taxaAbsenteismo: number;
  taxaReaproveitamento: number;
  agendamentosPorTipo: Record<string, number>;
  porProfissional: {
    profissionalId: number;
    nome: string;
    especialidade: string;
    horarios: number;
    reservados: number;
    realizados: number;
    faltas: number;
    taxaAbsenteismo: number;
  }[];
}
