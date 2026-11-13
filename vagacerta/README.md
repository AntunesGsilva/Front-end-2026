# VagaCerta: Sistema de Agendamento e Combate ao Absenteísmo em Unidades Básicas de Saúde

**Instituição:** Centro Universitário de Maringá (UniCesumar) · **Curso:** Engenharia de Software · **Ano letivo:** 2026 (AEP do 4º semestre)

**Autores:** Gustavo Antunes da Silva (RA 25000468-2) · Jonatas Marques da Silva (RA 25006769-2) · Rafael Garcia Ribeiro (RA 25000457-2)

**Repositório:** https://github.com/RafaelGarciaRibeiro/VagaCerta

## 1. Contexto, problema e ODS

Em Piracicaba (SP), o primeiro trimestre de 2026 registrou 57.332 faltas em 248.143 atendimentos agendados (23,1% na rede geral e 26,7% na Atenção Básica). Em 2025 foram 310.365 faltas, mais do que a fila de espera local (225.771 pessoas). O problema não é falta de vagas e sim o desperdício delas: sem confirmação prévia e sem liberação automática, a vaga caduca enquanto a fila cresce.

- **ODS 3, Meta 3.8 (cobertura universal de saúde):** o reaproveitamento de vagas ociosas amplia a capacidade assistencial sem ampliar estrutura física nem contratar pessoal.
- **ODS 3, Meta 3.4 (doenças não transmissíveis):** pacientes crônicos (hipertensão, diabetes) dependem de acompanhamento contínuo e são os mais prejudicados por faltas na agenda.

## 2. Requisitos funcionais

1. **O sistema deve permitir** o cadastro unificado de usuários nas categorias de Paciente, Profissional de Saúde e Gestor da Unidade, validando obrigatoriamente CPF único, senha em hash seguro e perfil de acesso atribuído.
2. **O sistema deve permitir** que o Profissional de Saúde ou Gestor gere dinamicamente a grade diária de atendimentos (AgendaDiaria), calculando os intervalos de horários a partir do turno de trabalho e da duração padrão de cada especialidade (20 minutos para consulta clínica, 30 minutos para exames e 5 minutos para vacinação).
3. **O sistema deve permitir** que o paciente realize o agendamento de consultas, exames e vacinações em horários disponíveis, garantindo exclusividade absoluta do horário selecionado através de restrição de unicidade transacional no banco de dados.
4. **O sistema deve permitir** a confirmação prévia de agendamentos por meio de janelas temporais polimórficas (24 horas de antecedência para consultas clínicas, 48 horas para exames e isenção de confirmação para vacinações).
5. **O sistema deve permitir** a execução de um motor de autoliberação de vagas que cancela agendamentos pendentes de confirmação fora da janela limite e reoferta imediatamente o horário como livre na fila pública de atendimento.
6. **O sistema deve permitir** o controle de faltas e reincidência de absenteísmo, bloqueando automaticamente o agendamento remoto para pacientes com 3 ou mais faltas não justificadas no período de seis meses, exigindo acolhimento presencial na UBS para liberação.
7. **O sistema deve permitir** a emissão de relatórios de Business Intelligence para a gestão, consolidando taxa de ocupação da grade, taxa percentual de absenteísmo, quantitativo de vagas autoliberadas e histórico por profissional.

## 3. Cronograma de execução

| Data | Atividade | Responsável |
| :--- | :--- | :--- |
| 05/08/2026 | Levantamento de requisitos, estudo de absenteísmo em UBS e alinhamento aos ODS 3 e 11 da ONU | Gustavo Antunes da Silva |
| 12/08/2026 | Definição das regras de negócio (RN01 a RN04) e arquitetura em camadas (MVC + DAO) | Rafael Garcia Ribeiro |
| 19/08/2026 | Modelagem orientada a objetos (Diagrama de Classes UML com Herança, Polimorfismo e Composição) | Jonatas Marques da Silva |
| 26/08/2026 | Modelagem física relacional (DER com PF para herança) | Gustavo Antunes da Silva |
| 02/09/2026 | Documentação formal do 1º Bimestre e consolidação do repositório no GitHub | Equipe (Gustavo, Jonatas, Rafael) |
| 09/09/2026 | Script DDL/DML SQL (`database/01_schema.sql` e `database/02_seed.sql`) com constraints de integridade | Rafael Garcia Ribeiro |
| 23/09/2026 | Classes de domínio em Java 17 puro (`backend/src/model`) | Jonatas Marques da Silva |
| 07/10/2026 | Camada de persistência JDBC/DAO com CRUD completo (`backend/src/dao`) | Gustavo Antunes da Silva |
| 21/10/2026 | Regras de negócio e motor de autoliberação (`backend/src/service`) | Rafael Garcia Ribeiro |
| 04/11/2026 | Suíte de testes unitários com JUnit 5 (`backend/src/test`) | Jonatas Marques da Silva |
| 10/11/2026 | Auditoria final, compatibilidade de SO, empacotamento e entrega integrada (API + front React) | Equipe (Gustavo, Jonatas, Rafael) |

## 4. Regras de negócio

- **RN01 Exclusividade de horário:** um horário admite um único agendamento ativo (`UNIQUE(horario_id)`); reservar tira o horário da grade, cancelar ou autoliberar devolve a `LIVRE` imediatamente.
- **RN02 Janela de confirmação e autoliberação:** consulta confirma até 24 h antes, exame até 48 h, vacina é isenta. Sem confirmação no prazo, o agendamento é descartado e o horário volta à fila pública. Quem agenda já dentro da janela nasce confirmado.
- **RN03 Reincidência:** 3 faltas não justificadas em 6 meses bloqueiam o agendamento online; o desbloqueio exige atestado validado por um Gestor no acolhimento presencial.
- **RN04 Grade dinâmica:** o turno é fatiado pela duração padrão (consulta 20 min, exame 30 min, vacina 5 min), e o procedimento só cabe em horário da duração correspondente.

## 5. Arquitetura e tecnologias

Camadas **Model, DAO, Service e View/Controller (MVC + DAO)**, em **Java 17 com JDBC puro (`java.sql.*`)**, sem ORM nem frameworks. Isso dá controle explícito de transação (commit/rollback), previsibilidade das consultas e execução leve. O banco é relacional (**MySQL 8 ou SQLite**), com integridade referencial e a RN01 garantida fisicamente por `UNIQUE(horario_id)`. A API HTTP usa `com.sun.net.httpserver`, que faz parte do JDK. O front é React + TypeScript e consome a API.

**Pilares de POO**
- *Herança:* `Usuario` (abstrata) → `Paciente`, `ProfissionalSaude`, `GestorUnidade`; `Agendamento` (abstrata) → `ConsultaClinica`, `Exame`, `Vacinacao`.
- *Composição 1:N:* `AgendaDiaria` compõe `Horario` (construtor restrito ao pacote; no banco, `ON DELETE CASCADE`). Um `Horario` não é uma `AgendaDiaria`, então herança violaria Liskov.
- *Polimorfismo (`@Override`):* `janelaConfirmacaoHoras()`, `duracaoMinutos()`, `exigePreparo()`, `gerarInstrucoesAtendimento()`, `deveSerAutoliberado()`, `detalhes()`. O motor de autoliberação percorre `List<Agendamento>` sem `if/else` por tipo.

## 6. Estrutura do repositório

```
vagacerta/
├── backend/                  # API e regras de negócio (Java 17, JDBC puro)
│   ├── lib/                  # JARs externos (SQLite, MySQL, JUnit) para rodar sem Maven
│   └── src/
│       ├── Main.java         # Ponto de entrada (demo, --interactive, --api, --db-check)
│       ├── model/            # Entidades de domínio (enums, usuario, unidade, agenda, agendamento, relatorio)
│       ├── dao/              # Acesso ao banco e queries SQL (JDBC)
│       ├── service/          # Regras de negócio e validações
│       ├── api/              # Endpoints HTTP (ApiServer) e JSON
│       ├── util/             # ConnectionFactory, StatusConexao e helpers (SenhaUtil, Validador, Sql)
│       ├── view/             # Demonstração e CLI interativa
│       └── test/             # Testes JUnit 5 das classes e fluxos principais
├── frontend/                 # Interface web (React + TypeScript + Vite)
│   ├── public/               # Arquivos estáticos
│   ├── src/
│   │   ├── components/       # Componentes reutilizáveis (ui, Senha)
│   │   ├── pages/            # Telas (Paciente, Profissional, Gestor)
│   │   ├── services/         # Requisições HTTP para o backend (api.ts)
│   │   ├── types/            # Interfaces TypeScript espelhando os models
│   │   ├── App.tsx           # Login, rotas e layout principal
│   │   ├── main.tsx          # Ponto de entrada da aplicação
│   │   ├── hooks.ts, format.ts, index.css
│   ├── index.html
│   ├── package.json, tsconfig.json, vite.config.ts
├── database/                 # Scripts SQL versionados
│   ├── 01_schema.sql         # Criação de tabelas, chaves e constraints (MySQL 8)
│   ├── 02_seed.sql           # Inserts iniciais para popular e testar (MySQL 8)
│   ├── 01_schema_sqlite.sql  # Mesmo modelo físico para SQLite
│   └── 02_seed_sqlite.sql    # Mesmos seeds para SQLite
├── docs/                     # PDFs da AEP, documentação técnica e AUDITORIA_CONFORMIDADE.md
└── README.md                 # Passo a passo para subir banco, back e front
```

## 7. Como executar

Requisitos: **JDK 17+** (Linux, macOS ou Windows) e, para o front, **Node 18+**.

**Compilar**
```bash
cd backend
mkdir -p bin
javac -encoding UTF-8 -cp "lib/*" -d bin $(find src -name "*.java")
```
No Windows (CMD/PowerShell) use `;` no lugar de `:` no classpath e liste os arquivos com `dir /s /b src\*.java > fontes.txt` seguido de `javac @fontes.txt ...`.

**Testes (JUnit 5)**
```bash
java -cp "bin:lib/*" org.junit.platform.console.ConsoleLauncher execute --scan-class-path --class-path bin
```

**Banco de dados.** Sem configuração, usa SQLite local (`vagacerta.db`, criado a partir de `database/01_schema_sqlite.sql` e `02_seed_sqlite.sql`). Para MySQL 8:
```bash
export VAGACERTA_JDBC_URL="jdbc:mysql://localhost:3306/vagacerta_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
export VAGACERTA_DB_USER=root
export VAGACERTA_DB_PASSWORD=sua_senha
java -cp "bin:lib/*" Main --db-check     # testa a conexão e valida as 13 tabelas
```
Na primeira execução, se a tabela `usuario` não existir, o esquema e os seeds de `database/` são criados automaticamente. Também dá para rodar `database/01_schema.sql` e depois `database/02_seed.sql` direto no MySQL Workbench. Os testes também rodam em MySQL com `-Dvagacerta.test.jdbc.url=...` (veja o comentário em `VagaCertaTest`).

**Demonstração ponta a ponta** (cadastro, grade, RN01 com concorrência, autoliberação, RN03, BI):
```bash
java -cp "bin:lib/*" Main
java -cp "bin:lib/*" Main --interactive    # CLI
```

**API + front**
```bash
cd backend  && java -cp "bin:lib/*" Main --api 8080          # terminal 1
cd frontend && npm install && npm run dev                      # terminal 2: http://localhost:3000
# ou: cd frontend && npm run build e abrir http://localhost:8080 (a API serve ../frontend/dist)
```
Contas de demonstração (senha `senha123`): `lucas.pereira@email.com` (paciente), `roberto.alves@email.com` (paciente bloqueado), `carlos.menezes@saude.maringa.gov.br` (profissional), `mariana.costa@saude.maringa.gov.br` (gestora). O "relógio de demonstração" do cabeçalho envia `X-Agora` para simular o passar do tempo.

## 8. Observações

- A API não tem autenticação por token; é adequada ao escopo acadêmico, não à produção.
- Cancelar/autoliberar remove a linha de `agendamento` (para o `UNIQUE(horario_id)` permitir novo agendamento do horário); o histórico fica em `liberacao_vaga`. Veja `docs/AUDITORIA_CONFORMIDADE.md` para as diferenças em relação ao DER do 1º bimestre.
