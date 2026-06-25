import { useState, useRef } from "react";
import { useContexto } from "./hooks/useContexto";
import GuessRow from "./components/GuessRow";
import HintBox from "./components/HintBox";
import styles from "./App.module.css";

function App() {
  const {
    palavraAtual,
    tentativas,
    totalTentativas,
    ganhou,
    erro,
    hintDesbloqueada,
    tentar,
    novaPartida,
  } = useContexto();

  const [input, setInput] = useState("");
  const inputRef = useRef(null);

  function handleTentar() {
    tentar(input);
    setInput("");
    inputRef.current?.focus();
  }

  function handleKeyDown(e) {
    if (e.key === "Enter") handleTentar();
  }

  function handleNova() {
    novaPartida();
    setInput("");
    inputRef.current?.focus();
  }

  return (
    <div className={styles.wrap}>
      <h1 className={styles.title}>CONTEXTO</h1>
      <p className={styles.subtitle}>
        Descubra a palavra secreta. Quanto menor o número, mais próximo você está.
      </p>

      {ganhou && (
        <p className={styles.winMsg}>
          Parabéns! A palavra era &quot;{palavraAtual.palavra}&quot; —
          encontrada em {totalTentativas} tentativa(s)!
        </p>
      )}

      <div className={styles.inputRow}>
        <input
          ref={inputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Digite uma palavra..."
          disabled={ganhou}
          className={styles.input}
          autoComplete="off"
        />
        <button onClick={handleTentar} disabled={ganhou} className={styles.btnTentar}>
          Tentar
        </button>
      </div>

      {erro && <p className={styles.erro}>{erro}</p>}

      {totalTentativas > 0 && (
        <p className={styles.contador}>{totalTentativas} tentativa(s)</p>
      )}

      <div className={styles.guesses}>
        {tentativas.map((t) => (
          <GuessRow key={t.palavra} palavra={t.palavra} rank={t.rank} />
        ))}
      </div>

      {hintDesbloqueada && <HintBox palavraAtual={palavraAtual} />}

      <div className={styles.footer}>
        <button onClick={handleNova} className={styles.btnNova}>
          Nova palavra
        </button>
      </div>
    </div>
  );
}

export default App;
