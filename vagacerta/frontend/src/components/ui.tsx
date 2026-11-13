import type { ButtonHTMLAttributes, ReactNode } from 'react';
import type { Retorno } from '../hooks';

type Variante = 'primario' | 'secundario' | 'perigo';

export function Botao({
  variante = 'primario',
  pequeno = false,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: Variante; pequeno?: boolean }) {
  const classes = ['botao', variante === 'primario' ? '' : variante, pequeno ? 'pequeno' : '', className].filter(Boolean).join(' ');
  return <button type="button" className={classes} {...props} />;
}

export function Campo({ rotulo, dica, children }: { rotulo: string; dica?: string; children: ReactNode }) {
  return (
    <label className="campo">
      <span>{rotulo}</span>
      {children}
      {dica && <small>{dica}</small>}
    </label>
  );
}

export function Selo({ status }: { status: string }) {
  return <span className={`selo st-${status}`}>{status.charAt(0) + status.slice(1).toLowerCase()}</span>;
}

export function Aviso({ retorno, aoFechar }: { retorno: Retorno | null; aoFechar?: () => void }) {
  if (!retorno) return null;
  return (
    <div className={`aviso ${retorno.tipo}`} role={retorno.tipo === 'erro' ? 'alert' : 'status'}>
      <div className="linha">
        <span style={{ flex: 1 }}>{retorno.texto}</span>
        {aoFechar && (
          <Botao variante="secundario" pequeno onClick={aoFechar} aria-label="Fechar mensagem">
            Fechar
          </Botao>
        )}
      </div>
    </div>
  );
}

export function Vazio({ children }: { children: ReactNode }) {
  return <div className="vazio">{children}</div>;
}

export function Abas<T extends string>({
  itens,
  ativa,
  aoMudar,
}: {
  itens: { id: T; rotulo: string }[];
  ativa: T;
  aoMudar: (id: T) => void;
}) {
  return (
    <div className="abas" role="tablist">
      {itens.map((i) => (
        <button key={i.id} role="tab" aria-selected={ativa === i.id} className="aba" onClick={() => aoMudar(i.id)}>
          {i.rotulo}
        </button>
      ))}
    </div>
  );
}
