import { useState } from "react";

function Square({ valor, onSquareClick, isWinner }) {
  return (
    <button
      className="square"
      onClick={onSquareClick}
      style={{ background: isWinner ? "#d4edda" : "" }}
    >
      {valor}
    </button>
  );
}

function Board() {
  const [squares, setSquares] = useState(Array(9).fill(null));
  const [xIsNext, setXIsNext] = useState(true);

  const result = checkWinner(squares);
  const winLine = result?.line || [];

  function handleClick(i) {
    if (squares[i] || result) return;
    const next = squares.slice();
    next[i] = xIsNext ? "X" : "O";
    setSquares(next);
    setXIsNext(!xIsNext);
  }

  function resetGame() {
    setSquares(Array(9).fill(null));
    setXIsNext(true);
  }

  let status;
  if (result?.winner) {
    status = `${result.winner} ganhou!`;
  } else if (squares.every(Boolean)) {
    status = "Empate!";
  } else {
    status = `Vez de ${xIsNext ? "X" : "O"}`;
  }

  return (
    <>
      <p>{status}</p>
      <div className="board-row">
        <Square valor={squares[0]} onSquareClick={() => handleClick(0)} isWinner={winLine.includes(0)} />
        <Square valor={squares[1]} onSquareClick={() => handleClick(1)} isWinner={winLine.includes(1)} />
        <Square valor={squares[2]} onSquareClick={() => handleClick(2)} isWinner={winLine.includes(2)} />
      </div>
      <div className="board-row">
        <Square valor={squares[3]} onSquareClick={() => handleClick(3)} isWinner={winLine.includes(3)} />
        <Square valor={squares[4]} onSquareClick={() => handleClick(4)} isWinner={winLine.includes(4)} />
        <Square valor={squares[5]} onSquareClick={() => handleClick(5)} isWinner={winLine.includes(5)} />
      </div>
      <div className="board-row">
        <Square valor={squares[6]} onSquareClick={() => handleClick(6)} isWinner={winLine.includes(6)} />
        <Square valor={squares[7]} onSquareClick={() => handleClick(7)} isWinner={winLine.includes(7)} />
        <Square valor={squares[8]} onSquareClick={() => handleClick(8)} isWinner={winLine.includes(8)} />
      </div>
      <button onClick={resetGame}>Nova partida</button>
    </>
  );
}

function checkWinner(squares) {
  const lines = [
    [0, 1, 2], [3, 4, 5], [6, 7, 8],
    [0, 3, 6], [1, 4, 7], [2, 5, 8],
    [0, 4, 8], [2, 4, 6],
  ];
  for (let [a, b, c] of lines) {
    if (squares[a] && squares[a] === squares[b] && squares[a] === squares[c]) {
      return { winner: squares[a], line: [a, b, c] };
    }
  }
  return null;
}

export default function App() {
  return <Board />;
}
