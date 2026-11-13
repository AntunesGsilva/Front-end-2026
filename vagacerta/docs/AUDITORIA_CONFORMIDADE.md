# Auditoria de conformidade com a AEP

Comparação da 2ª entrega com o PDF do 1º bimestre (`docs/AEP_1_BIMESTRE.pdf`) e com as Diretrizes Gerais (`docs/Diretrizes_Gerais_AEP_4_Semestre.pdf`).

## Aderência ao planejamento (OK)
- Regras RN01 a RN04, funcionalidades F1 a F6, hierarquias `Usuario` e `Agendamento`, composição `AgendaDiaria` 1:N `Horario`, Java 17 + JDBC puro, MVC + DAO, MySQL 8 com herança por tabelas separadas (PF) e `UNIQUE(horario_id)`: todos implementados e cobertos por testes.
- Diretrizes: classe abstrata, herança e `@Override` (POO); CRUD sobre usuários, unidades, agendas e agendamentos; conexão real com banco (nenhuma lista em memória); script SQL em `/database` compatível com as classes; instruções de execução.

## Divergências que precisam de decisão do grupo
1. **PDF do 1º bimestre sem cronograma e sem a lista "O sistema deve permitir…".** Estão apenas em `docs/DOCUMENTACAO_TECNICA_1_BIMESTRE.md` e no README. As diretrizes zeram o critério quando faltam. Recomenda-se regerar o PDF incluindo as duas seções.
2. **Link do GitHub no PDF** é `.../ReDistribui.git`; o repositório atual é `.../VagaCerta`. Alinhar.
3. **Interface:** o PDF cita "Java Swing"; a entrega usa CLI e web (React). O próprio PDF prevê migração da View sem impacto no Service/DAO, e a AEP não avalia interface, mas convém ajustar o texto.
4. **ODS 11:** aparece só no `.md` técnico, não no PDF. Alinhar (o PDF usa apenas ODS 3).
5. **Tabelas fora do DER do PDF:** `registro_falta` (suporte à F5/D4, histórico de faltas) e `liberacao_vaga` (log de devoluções para o BI da F6). As 11 tabelas do DER foram mantidas. Atualizar o DER.
6. **Remoção da linha ao cancelar/autoliberar.** O `CANCELADO` existe no enum, mas a linha de `agendamento` é apagada para que `UNIQUE(horario_id)` permita reservar o horário de novo; `liberacao_vaga` guarda o evento.
7. **Seeds do `schema.sql` reescritos** (os originais tinham faltas ligadas a agendamentos de outros pacientes e exame/vacina em horários de 20 min). Senhas de teste: `senha123` (PBKDF2).
8. **Interpretação de "confirmar até Nh antes":** prazo-limite (início do atendimento menos a janela). Reserva feita depois do prazo nasce confirmada.
9. O cronograma do `.md` cita `src/br/com/vagacerta/...`; o código real está em `backend/src/...` (corrigido no README).

## Pendências fora do código
- **Contribuição de todos no repositório** é critério da 2ª entrega: os três integrantes precisam ter commits.
- Validação feita em SQLite e MariaDB 10.11; recomenda-se rodar `--db-check` e os testes no MySQL 8 de entrega.
