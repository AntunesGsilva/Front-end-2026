const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

/** "2026-11-16" -> Date local (sem deslocamento de fuso) */
function dataLocal(iso: string): Date {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

export function diaSemana(iso: string): string {
  return DIAS[dataLocal(iso).getDay()];
}

export function dataCurta(iso: string): string {
  const d = dataLocal(iso);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function dataLonga(iso: string): string {
  return `${diaSemana(iso)}, ${dataCurta(iso)}/${iso.slice(0, 4)}`;
}

export function hora(iso: string): string {
  return iso.slice(0, 5);
}

/** "2026-11-19T09:00" -> "qui 19/11, 09:00" */
export function dataHora(iso: string): string {
  const [d, h] = iso.split('T');
  return `${diaSemana(d)} ${dataCurta(d)}, ${hora(h)}`;
}

export function duracaoHumana(ms: number): string {
  const min = Math.floor(ms / 60000);
  const dias = Math.floor(min / 1440);
  const horas = Math.floor((min % 1440) / 60);
  const mins = min % 60;
  if (dias > 0) return `${dias} d ${horas} h`;
  if (horas > 0) return `${horas} h ${mins} min`;
  return `${mins} min`;
}

export function cpfMascara(cpf: string): string {
  return cpf.length === 11 ? `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}` : cpf;
}

export function paraInputDataHora(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
