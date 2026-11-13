-- ============================================================================
-- VagaCerta - Seeds para SQLite (rodar depois de 01_schema_sqlite.sql)
-- ============================================================================


-- ============================================================================
-- DML - DADOS INICIAIS PARA TESTES E DEMONSTRAÇÃO (SEEDS)
-- Todas as senhas de teste são "senha123" (PBKDF2-HMAC-SHA256, ver SenhaUtil.java).
-- Os horários e agendamentos seguem as regras: grade de 20 min (consulta), 30 min (exame)
-- e 5 min (vacina) -> RN04; um agendamento por horário -> RN01; faltas coerentes com
-- registro_falta -> RN03.
-- ============================================================================

-- 1. Unidades Básicas de Saúde
INSERT INTO unidade_saude (id, nome, cnes, endereco, telefone) VALUES
(1, 'UBS Central - Maringá', '2589631', 'Av. Brasil, 1500 - Zona 01, Maringá - PR', '(44) 3221-1000'),
(2, 'UBS Zona 7 - Dr. Ary Rodrigues', '2589632', 'Rua Paranaguá, 750 - Zona 07, Maringá - PR', '(44) 3221-2000');

-- 2. Usuários base (1 gestor, 3 profissionais, 3 pacientes)
INSERT INTO usuario (id, nome, cpf, email, senha_hash, telefone, perfil, ativo) VALUES
(1, 'Mariana Costa', '11122233344', 'mariana.costa@saude.maringa.gov.br', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99111-2233', 'GESTOR', TRUE),
(2, 'Dr. Carlos Eduardo Menezes', '22233344455', 'carlos.menezes@saude.maringa.gov.br', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99222-3344', 'PROFISSIONAL', TRUE),
(3, 'Dra. Beatriz Helena Silveira', '33344455566', 'beatriz.silveira@saude.maringa.gov.br', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99333-4455', 'PROFISSIONAL', TRUE),
(4, 'Lucas Pereira dos Santos', '44455566677', 'lucas.pereira@email.com', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99444-5566', 'PACIENTE', TRUE),
(5, 'Roberto Alves da Silva', '55566677788', 'roberto.alves@email.com', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99555-6677', 'PACIENTE', TRUE),
(6, 'Juliana Mendes Rocha', '66677788899', 'juliana.rocha@email.com', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99666-7788', 'PACIENTE', TRUE),
(7, 'Patrícia Lima Andrade', '77788899900', 'patricia.lima@saude.maringa.gov.br', 'pbkdf2$30000$dmFnYWNlcnRhLXNlZWQtMDE$Ncu-j_r2pex3H7U5cluxTRkAXpQ5mqMkGr3AljImHIc', '(44) 99777-8899', 'PROFISSIONAL', TRUE);

-- 2.1 Especializações de usuários (herança por tabelas separadas, PF)
INSERT INTO gestor_unidade (usuario_id, matricula, cargo) VALUES
(1, 'GES-2026-90', 'Coordenadora de Regulação e Agendamento');

INSERT INTO profissional_saude (usuario_id, registro_conselho, especialidade, carga_horaria_diaria) VALUES
(2, 'CRM-PR 34512', 'Clínica Geral', 8),
(3, 'CRM-PR 41289', 'Cardiologia', 6),
(7, 'COREN-PR 118233', 'Enfermagem - Sala de Vacinas', 6);

-- Paciente 4: 0 faltas (apto) | Paciente 5: 3 faltas não justificadas (bloqueado, RN03) | Paciente 6: 1 falta (apto)
INSERT INTO paciente (usuario_id, cartao_sus, data_nascimento, faltas_nao_justificadas) VALUES
(4, '700123456789012', '1992-05-14', 0),
(5, '700555666777888', '1985-11-20', 3),
(6, '700999888111222', '1998-03-30', 1);

-- 3. Agendas diárias (futuras: segunda 16/11/2026; passadas: base das faltas históricas)
INSERT INTO agenda_diaria (id, unidade_id, profissional_id, data, turno_inicio, turno_fim, bloqueada) VALUES
(1, 1, 2, '2026-11-16', '08:00:00', '12:00:00', FALSE),
(2, 2, 3, '2026-11-16', '13:00:00', '17:00:00', FALSE),
(3, 1, 7, '2026-11-16', '08:00:00', '10:00:00', FALSE),
(4, 1, 2, '2026-06-10', '09:00:00', '09:20:00', FALSE),
(5, 1, 2, '2026-08-15', '09:00:00', '09:20:00', FALSE),
(6, 1, 2, '2026-10-02', '09:00:00', '09:20:00', FALSE),
(7, 1, 2, '2026-09-21', '10:00:00', '10:20:00', FALSE);

-- 4. Horários (composição 1:N gerada pela RN04: agenda 1 = 12 x 20 min; agenda 2 = 8 x 30 min; agenda 3 = 24 x 5 min)
INSERT INTO horario (id, agenda_id, hora_inicio, hora_fim, status) VALUES
(1, 1, '08:00:00', '08:20:00', 'LIVRE'),
(2, 1, '08:20:00', '08:40:00', 'LIVRE'),
(3, 1, '08:40:00', '09:00:00', 'LIVRE'),
(4, 1, '09:00:00', '09:20:00', 'LIVRE'),
(5, 1, '09:20:00', '09:40:00', 'LIVRE'),
(6, 1, '09:40:00', '10:00:00', 'LIVRE'),
(7, 1, '10:00:00', '10:20:00', 'LIVRE'),
(8, 1, '10:20:00', '10:40:00', 'LIVRE'),
(9, 1, '10:40:00', '11:00:00', 'LIVRE'),
(10, 1, '11:00:00', '11:20:00', 'LIVRE'),
(11, 1, '11:20:00', '11:40:00', 'LIVRE'),
(12, 1, '11:40:00', '12:00:00', 'LIVRE'),
(13, 2, '13:00:00', '13:30:00', 'LIVRE'),
(14, 2, '13:30:00', '14:00:00', 'LIVRE'),
(15, 2, '14:00:00', '14:30:00', 'LIVRE'),
(16, 2, '14:30:00', '15:00:00', 'LIVRE'),
(17, 2, '15:00:00', '15:30:00', 'LIVRE'),
(18, 2, '15:30:00', '16:00:00', 'LIVRE'),
(19, 2, '16:00:00', '16:30:00', 'LIVRE'),
(20, 2, '16:30:00', '17:00:00', 'LIVRE'),
(21, 3, '08:00:00', '08:05:00', 'LIVRE'),
(22, 3, '08:05:00', '08:10:00', 'LIVRE'),
(23, 3, '08:10:00', '08:15:00', 'LIVRE'),
(24, 3, '08:15:00', '08:20:00', 'LIVRE'),
(25, 3, '08:20:00', '08:25:00', 'LIVRE'),
(26, 3, '08:25:00', '08:30:00', 'LIVRE'),
(27, 3, '08:30:00', '08:35:00', 'LIVRE'),
(28, 3, '08:35:00', '08:40:00', 'LIVRE'),
(29, 3, '08:40:00', '08:45:00', 'LIVRE'),
(30, 3, '08:45:00', '08:50:00', 'LIVRE'),
(31, 3, '08:50:00', '08:55:00', 'LIVRE'),
(32, 3, '08:55:00', '09:00:00', 'LIVRE'),
(33, 3, '09:00:00', '09:05:00', 'LIVRE'),
(34, 3, '09:05:00', '09:10:00', 'LIVRE'),
(35, 3, '09:10:00', '09:15:00', 'LIVRE'),
(36, 3, '09:15:00', '09:20:00', 'LIVRE'),
(37, 3, '09:20:00', '09:25:00', 'LIVRE'),
(38, 3, '09:25:00', '09:30:00', 'LIVRE'),
(39, 3, '09:30:00', '09:35:00', 'LIVRE'),
(40, 3, '09:35:00', '09:40:00', 'LIVRE'),
(41, 3, '09:40:00', '09:45:00', 'LIVRE'),
(42, 3, '09:45:00', '09:50:00', 'LIVRE'),
(43, 3, '09:50:00', '09:55:00', 'LIVRE'),
(44, 3, '09:55:00', '10:00:00', 'LIVRE'),
(45, 4, '09:00:00', '09:20:00', 'RESERVADO'),
(46, 5, '09:00:00', '09:20:00', 'RESERVADO'),
(47, 6, '09:00:00', '09:20:00', 'RESERVADO'),
(48, 7, '10:00:00', '10:20:00', 'RESERVADO');
UPDATE horario SET status = 'RESERVADO' WHERE id = 1;
UPDATE horario SET status = 'RESERVADO' WHERE id = 13;
UPDATE horario SET status = 'RESERVADO' WHERE id = 21;

-- 5. Agendamentos polimórficos (um por horário - RN01)
INSERT INTO agendamento (id, horario_id, paciente_id, tipo_agendamento, status, confirmado_em, data_criacao) VALUES
(1, 1, 4, 'CONSULTA', 'CONFIRMADO', '2026-11-14 07:30:00', '2026-11-10 10:00:00'),
(2, 13, 6, 'EXAME', 'AGENDADO', NULL, '2026-11-11 09:00:00'),
(3, 21, 4, 'VACINA', 'CONFIRMADO', '2026-11-12 14:00:00', '2026-11-12 14:00:00'),
(4, 45, 5, 'CONSULTA', 'FALTA', '2026-06-08 10:00:00', '2026-06-01 10:00:00'),
(5, 46, 5, 'CONSULTA', 'FALTA', '2026-08-13 10:00:00', '2026-08-05 10:00:00'),
(6, 47, 5, 'CONSULTA', 'FALTA', '2026-09-30 10:00:00', '2026-09-25 10:00:00'),
(7, 48, 6, 'CONSULTA', 'FALTA', '2026-09-19 10:00:00', '2026-09-10 10:00:00');

INSERT INTO consulta_clinica (agendamento_id, motivo, retorno) VALUES
(1, 'Avaliação de rotina / Monitoramento de Hipertensão Arterial', FALSE),
(4, 'Acompanhamento de diabetes mellitus', FALSE),
(5, 'Retorno - acompanhamento de diabetes mellitus', TRUE),
(6, 'Retorno - acompanhamento de diabetes mellitus', TRUE),
(7, 'Avaliação de rotina', FALSE);

INSERT INTO exame (agendamento_id, tipo_exame, orientacao_preparo) VALUES
(2, 'Eletrocardiograma (ECG)', 'Jejum prévio de 4 horas, não ingerir cafeína ou estimulantes.');

INSERT INTO vacinacao (agendamento_id, imunobiologico, dose) VALUES
(3, 'Vacina Febre Amarela', 'Dose Única');

-- 6. Histórico de faltas (RN03): Roberto com 3 faltas não justificadas na janela de 6 meses; Juliana com 1
INSERT INTO registro_falta (id, paciente_id, agendamento_id, data_falta, justificada, motivo_justificativa, justificado_em, justificado_por) VALUES
(1, 5, 4, '2026-06-10 09:00:00', FALSE, NULL, NULL, NULL),
(2, 5, 5, '2026-08-15 09:00:00', FALSE, NULL, NULL, NULL),
(3, 5, 6, '2026-10-02 09:00:00', FALSE, NULL, NULL, NULL),
(4, 6, 7, '2026-09-21 10:00:00', FALSE, NULL, NULL, NULL);

-- ============================================================================
-- FIM DO SCRIPT DDL / DML VAGACERTA
-- ============================================================================
