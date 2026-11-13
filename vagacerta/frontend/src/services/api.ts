/**
 * Cliente da API Java (/api). O "relógio de demonstração" envia o cabeçalho X-Agora para que as
 * regras temporais (RN02/RN03) rodem em um instante simulado, sem esperar horas de verdade.
 */
let relogio: string | null = null;

export function definirRelogio(valor: string | null): void {
  relogio = valor ? (valor.length === 16 ? `${valor}:00` : valor) : null;
}

export function agoraEfetiva(): Date {
  return relogio ? new Date(relogio) : new Date();
}

export class ApiError extends Error {
  constructor(
    message: string,
    public codigo: string,
    public status: number,
  ) {
    super(message);
  }
}

async function requisitar<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (corpo !== undefined) headers['Content-Type'] = 'application/json';
  if (relogio) headers['X-Agora'] = relogio;
  let resposta: Response;
  try {
    resposta = await fetch(`/api${caminho}`, {
      method: metodo,
      headers,
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
  } catch {
    throw new ApiError('Sem conexão com o servidor. Verifique se a API Java está em execução.', 'REDE', 0);
  }
  if (resposta.status === 204) return undefined as T;
  const dados = (await resposta.json().catch(() => null)) as { erro?: string; codigo?: string } | null;
  if (!resposta.ok) {
    throw new ApiError(dados?.erro ?? 'Erro inesperado no servidor.', dados?.codigo ?? 'ERRO', resposta.status);
  }
  return dados as T;
}

export const api = {
  get: <T,>(caminho: string) => requisitar<T>('GET', caminho),
  post: <T,>(caminho: string, corpo?: unknown) => requisitar<T>('POST', caminho, corpo ?? {}),
  put: <T,>(caminho: string, corpo: unknown) => requisitar<T>('PUT', caminho, corpo),
  del: (caminho: string) => requisitar<void>('DELETE', caminho),
};

export function mensagemDeErro(e: unknown): string {
  if (e instanceof ApiError) return e.codigo.startsWith('RN') ? `${e.codigo}: ${e.message}` : e.message;
  return e instanceof Error ? e.message : 'Erro desconhecido.';
}
