import styles from "./GuessRow.module.css";

function getTemperatura(rank) {
  if (rank === 0) return { label: "Acertou!", cor: "#639922", pct: 100 };
  if (rank <= 3)  return { label: "Quente",  cor: "#D85A30", pct: 90 - rank * 5 };
  if (rank <= 7)  return { label: "Morno",   cor: "#BA7517", pct: 70 - rank * 3 };
  if (rank <= 12) return { label: "Frio",    cor: "#378ADD", pct: 40 - rank * 1.5 };
  return             { label: "Gelado",  cor: "#888780", pct: Math.max(5, 20 - rank) };
}

function GuessRow({ palavra, rank }) {
  if (rank === null) {
    return (
      <div className={styles.row}>
        <span className={styles.word}>{palavra}</span>
        <span className={styles.foraRanking}>fora do ranking</span>
      </div>
    );
  }

  const { label, cor, pct } = getTemperatura(rank);

  return (
    <div className={styles.row}>
      <span className={styles.word}>{palavra}</span>
      <div className={styles.barWrap}>
        <div
          className={styles.bar}
          style={{ width: `${Math.round(pct)}%`, background: cor }}
        />
      </div>
      <span className={styles.tempLabel} style={{ color: cor }}>
        {label}
      </span>
      <span className={styles.rankLabel}>
        {rank === 0 ? "#1" : `#${rank}`}
      </span>
    </div>
  );
}

export default GuessRow;
