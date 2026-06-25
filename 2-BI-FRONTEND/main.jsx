import { useState, useCallback } from "react";
import PALAVRAS from "../data/palavras";

function sorteiaPalavra() {
  return PALAVRAS[Math.floor(Math.random() * PALAVRAS.length)];
}

function calcularRank(entrada, palavraAtual) {
  const palavra = entrada.trim().toLowerCase();
  if (palavra === palavraAtual.palavra) return 0;
  const idx = palavraAtual.relacionadas.indexOf(palavra);
  return idx === -1 ? null : idx + 1;
}

export function useContexto() {
  const [palavraAtual, setPalavraAtual] = useState(sorteiaPalavra);
  const [tentativas, setTentativas] = useState([]);
  const [ganhou, setGanhou] = useState(false);
  const [erro, setErro] = useState("");

  const tentar = useCallback(
    (entrada) => {
      const palavra = entrada.trim().toLowerCase();

      if (!palavra) {
        setErro("Digite uma palavra.");
        return;
      }
      if (tentativas.find((t) => t.palavra === palavra)) {
        setErro("Você já tentou essa palavra.");
        return;
      }

      setErro("");
      const rank = calcularRank(palavra, palavraAtual);
      const novaTentativa = { palavra, rank };

      setTentativas((prev) => [novaTentativa, ...prev]);

      if (rank === 0) setGanhou(true);
    },
    [tentativas, palavraAtual]
  );

  const novaPartida = useCallback(() => {
    setPalavraAtual(sorteiaPalavra());
    setTentativas([]);
    setGanhou(false);
    setErro("");
  }, []);

  const tentativasOrdenadas = [...tentativas].sort((a, b) => {
    if (a.rank === null && b.rank === null) return 0;
    if (a.rank === null) return 1;
    if (b.rank === null) return 1;
    return a.rank - b.rank;
  });

  const hintDesbloqueada =
    tentativas.some((t) => t.rank !== null && t.rank !== 0 && t.rank <= 5);

  return {
    palavraAtual,
    tentativas: tentativasOrdenadas,
    totalTentativas: tentativas.length,
    ganhou,
    erro,
    hintDesbloqueada,
    tentar,
    novaPartida,
  };
}
