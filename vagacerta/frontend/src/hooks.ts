import { useCallback, useEffect, useState } from 'react';
import { mensagemDeErro } from './services/api';

export interface Carga<T> {
  dados: T | null;
  erro: string | null;
  carregando: boolean;
  recarregar: () => void;
}

/** Carrega dados da API e permite recarregar; refaz a busca quando as dependências mudam. */
export function useCarga<T>(buscar: () => Promise<T>, deps: unknown[]): Carga<T> {
  const [dados, setDados] = useState<T | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let cancelado = false;
    setCarregando(true);
    buscar()
      .then((d) => {
        if (!cancelado) {
          setDados(d);
          setErro(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelado) setErro(mensagemDeErro(e));
      })
      .finally(() => {
        if (!cancelado) setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, versao]);

  const recarregar = useCallback(() => setVersao((v) => v + 1), []);
  return { dados, erro, carregando, recarregar };
}

export interface Retorno {
  tipo: 'ok' | 'erro' | 'alerta' | 'info';
  texto: string;
}

/** Mensagem de retorno (sucesso/erro) para ações do usuário. */
export function useRetorno() {
  const [retorno, setRetorno] = useState<Retorno | null>(null);
  const executar = useCallback(async (acao: () => Promise<unknown>, sucesso: string) => {
    try {
      await acao();
      setRetorno({ tipo: 'ok', texto: sucesso });
      return true;
    } catch (e) {
      setRetorno({ tipo: 'erro', texto: mensagemDeErro(e) });
      return false;
    }
  }, []);
  return { retorno, setRetorno, executar };
}
