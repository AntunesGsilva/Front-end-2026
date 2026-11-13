-- ============================================================================
-- VagaCerta - Sistema de Agendamento e Combate ao Absenteísmo em UBS
-- Script DDL (estrutura) para MySQL 8 / MariaDB. Os dados iniciais estão em 02_seed.sql
-- Centro Universitário de Maringá (UniCesumar) - Engenharia de Software (2026)
-- Equipe: Gustavo Antunes da Silva, Jonatas Marques da Silva, Rafael Garcia Ribeiro
--
-- REGRAS ESTRUTURAIS OBRIGATÓRIAS (AEP 4º Semestre):
-- 1. RN01 - Exclusividade de Horário: UNIQUE(horario_id) em agendamento
-- 2. Herança Relacional com Tabelas Separadas (PF - Chave Primária e Estrangeira Conjunta)
-- 3. Composição Estrita: horario pertence a agenda_diaria com ON DELETE CASCADE
-- 4. Carga Inicial de Testes (DML) coerente com as regras RN01-RN04
-- 5. liberacao_vaga: log de devolução de vagas à fila (alimenta o BI da F6; ver README)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS vagacerta_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vagacerta_db;

-- ----------------------------------------------------------------------------
-- Limpeza prévia para recriação limpa (ordem inversa de dependências)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS liberacao_vaga;
DROP TABLE IF EXISTS registro_falta;
DROP TABLE IF EXISTS vacinacao;
DROP TABLE IF EXISTS exame;
DROP TABLE IF EXISTS consulta_clinica;
DROP TABLE IF EXISTS agendamento;
DROP TABLE IF EXISTS horario;
DROP TABLE IF EXISTS agenda_diaria;
DROP TABLE IF EXISTS gestor_unidade;
DROP TABLE IF EXISTS profissional_saude;
DROP TABLE IF EXISTS paciente;
DROP TABLE IF EXISTS usuario;
DROP TABLE IF EXISTS unidade_saude;

-- ----------------------------------------------------------------------------
-- 1. Tabela: Unidade de Saúde (UBS)
-- ----------------------------------------------------------------------------
CREATE TABLE unidade_saude (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    cnes CHAR(7) NOT NULL UNIQUE,
    endereco VARCHAR(160) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. Tabela Base: Usuario (Hierarquia Abstrata com Herança)
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    cpf CHAR(11) NOT NULL UNIQUE,
    email VARCHAR(120) NOT NULL UNIQUE,
    senha_hash VARCHAR(128) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_usuario_perfil CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL', 'GESTOR'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2.1 Especialização: Paciente (Herança - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE paciente (
    usuario_id BIGINT PRIMARY KEY,
    cartao_sus CHAR(15) NOT NULL UNIQUE,
    data_nascimento DATE NOT NULL,
    faltas_nao_justificadas INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_paciente_usuario FOREIGN KEY (usuario_id) 
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2.2 Especialização: Profissional de Saúde (Herança - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE profissional_saude (
    usuario_id BIGINT PRIMARY KEY,
    registro_conselho VARCHAR(20) NOT NULL UNIQUE,
    especialidade VARCHAR(60) NOT NULL,
    carga_horaria_diaria INT NOT NULL DEFAULT 8,
    CONSTRAINT fk_profissional_usuario FOREIGN KEY (usuario_id) 
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2.3 Especialização: Gestor da Unidade (Herança - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE gestor_unidade (
    usuario_id BIGINT PRIMARY KEY,
    matricula VARCHAR(20) NOT NULL UNIQUE,
    cargo VARCHAR(60) NOT NULL,
    CONSTRAINT fk_gestor_usuario FOREIGN KEY (usuario_id) 
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. Tabela: Agenda Diária (RN04)
-- ----------------------------------------------------------------------------
CREATE TABLE agenda_diaria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unidade_id BIGINT NOT NULL,
    profissional_id BIGINT NOT NULL,
    data DATE NOT NULL,
    turno_inicio TIME NOT NULL,
    turno_fim TIME NOT NULL,
    bloqueada BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agenda_unidade FOREIGN KEY (unidade_id) 
        REFERENCES unidade_saude(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agenda_profissional FOREIGN KEY (profissional_id) 
        REFERENCES profissional_saude(usuario_id) ON DELETE RESTRICT,
    CONSTRAINT uq_profissional_data UNIQUE (profissional_id, data)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. Tabela: Horário (Composição 1:N estrita com Agenda Diária)
-- ----------------------------------------------------------------------------
CREATE TABLE horario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agenda_id BIGINT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'LIVRE',
    CONSTRAINT fk_horario_agenda FOREIGN KEY (agenda_id) 
        REFERENCES agenda_diaria(id) ON DELETE CASCADE,
    CONSTRAINT chk_horario_status CHECK (status IN ('LIVRE', 'RESERVADO', 'BLOQUEADO'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. Tabela Base: Agendamento (RN01 - Exclusividade de Horário)
-- ----------------------------------------------------------------------------
CREATE TABLE agendamento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    horario_id BIGINT NOT NULL,
    paciente_id BIGINT NOT NULL,
    tipo_agendamento VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AGENDADO',
    confirmado_em DATETIME NULL,
    data_criacao DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agendamento_horario FOREIGN KEY (horario_id) 
        REFERENCES horario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agendamento_paciente FOREIGN KEY (paciente_id) 
        REFERENCES paciente(usuario_id) ON DELETE RESTRICT,
    CONSTRAINT chk_agendamento_tipo CHECK (tipo_agendamento IN ('CONSULTA', 'EXAME', 'VACINA')),
    CONSTRAINT chk_agendamento_status CHECK (status IN ('AGENDADO', 'CONFIRMADO', 'REALIZADO', 'FALTA', 'CANCELADO')),
    -- RN01: Exclusividade garantida no modelo físico via Unique Key
    CONSTRAINT uq_agendamento_horario UNIQUE (horario_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5.1 Especialização: Consulta Clínica (Polimorfismo - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE consulta_clinica (
    agendamento_id BIGINT PRIMARY KEY,
    motivo VARCHAR(160) NOT NULL,
    retorno BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_consulta_agendamento FOREIGN KEY (agendamento_id) 
        REFERENCES agendamento(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5.2 Especialização: Exame (Polimorfismo - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE exame (
    agendamento_id BIGINT PRIMARY KEY,
    tipo_exame VARCHAR(60) NOT NULL,
    orientacao_preparo VARCHAR(255) NOT NULL,
    CONSTRAINT fk_exame_agendamento FOREIGN KEY (agendamento_id) 
        REFERENCES agendamento(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5.3 Especialização: Vacinação (Polimorfismo - PF)
-- ----------------------------------------------------------------------------
CREATE TABLE vacinacao (
    agendamento_id BIGINT PRIMARY KEY,
    imunobiologico VARCHAR(60) NOT NULL,
    dose VARCHAR(20) NOT NULL,
    CONSTRAINT fk_vacinacao_agendamento FOREIGN KEY (agendamento_id) 
        REFERENCES agendamento(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. Histórico de Faltas e Justificativas (RN03 - Auditoria e Desbloqueio)
-- ----------------------------------------------------------------------------
CREATE TABLE registro_falta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paciente_id BIGINT NOT NULL,
    agendamento_id BIGINT NOT NULL,
    data_falta DATETIME NOT NULL,
    justificada BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_justificativa TEXT NULL,
    justificado_em DATETIME NULL,
    justificado_por BIGINT NULL,
    CONSTRAINT fk_falta_paciente FOREIGN KEY (paciente_id) 
        REFERENCES paciente(usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_falta_agendamento FOREIGN KEY (agendamento_id) 
        REFERENCES agendamento(id) ON DELETE CASCADE,
    CONSTRAINT fk_falta_gestor FOREIGN KEY (justificado_por) 
        REFERENCES gestor_unidade(usuario_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. Log de liberação de vagas (RN02 / cancelamentos) - suporte ao BI (F6)
--    A linha de agendamento é removida ao liberar o horário (UNIQUE(horario_id) precisa
--    permitir novo agendamento do mesmo horário); este log preserva o histórico.
-- ----------------------------------------------------------------------------
CREATE TABLE liberacao_vaga (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    horario_id BIGINT NOT NULL,
    paciente_id BIGINT NOT NULL,
    tipo_agendamento VARCHAR(20) NOT NULL,
    motivo VARCHAR(30) NOT NULL,
    liberado_em DATETIME NOT NULL,
    CONSTRAINT fk_liberacao_horario FOREIGN KEY (horario_id)
        REFERENCES horario(id) ON DELETE CASCADE,
    CONSTRAINT fk_liberacao_paciente FOREIGN KEY (paciente_id)
        REFERENCES paciente(usuario_id) ON DELETE CASCADE,
    CONSTRAINT chk_liberacao_tipo CHECK (tipo_agendamento IN ('CONSULTA', 'EXAME', 'VACINA')),
    CONSTRAINT chk_liberacao_motivo CHECK (motivo IN ('AUTOLIBERACAO', 'CANCELAMENTO_PACIENTE', 'CANCELAMENTO_GESTAO'))
) ENGINE=InnoDB;
