# CENTRO UNIVERSITÁRIO DE MARINGÁ (UNICESUMAR)
## CURSO DE ENGENHARIA DE SOFTWARE (2026.2)
### ATIVIDADE DE ESTUDO PROGRAMADA (AEP) - 4º SEMESTRE

**PROJETO:** VAGACERTA - Sistema de Agendamento e Combate ao Absenteísmo em Unidades Básicas de Saúde (UBS)  
**EQUIPE DE DESENVOLVIMENTO:**
- Gustavo Antunes da Silva (RA: 25000468-2)
- Jonatas Marques da Silva (RA: 25006769-2)
- Rafael Garcia Ribeiro (RA: 25000457-2)

---

## 1. DESCOBERTA E NEGÓCIO

### 1.1 O Problema e as Dores dos Usuários
No primeiro trimestre de 2026, dados oficiais de municípios polo da saúde pública brasileira (como Piracicaba-SP, ABC Paulista e Pelotas-RS) apontaram taxas de absenteísmo em consultas e exames especializados entre 19,2% e 26,7%. Em Piracicaba, registraram-se 57.332 faltas em 248.143 procedimentos agendados. Em 2025, o total anual superou 310.365 faltas, ultrapassando o contingente de 225.771 cidadãos aguardando atendimento na fila de regulação municipal.

O problema central não é a ausência de vagas ofertadas, mas sim o seu desperdício operacional. Fila de espera reprimida e faltas não notificadas são duas faces da mesma ineficiência: quando um paciente falta sem aviso prévio, a vaga não pode ser repassada a tempo para outro usuário, gerando ociosidade da equipe clínica e custos sem atendimento prestado.

### 1.2 Partes Interessadas (Stakeholders) e Entrega de Valor
- **Paciente:** Usuário operador da ponta. Necessita de lembretes simplificados de confirmação, canais ágeis de cancelamento e acesso imediato a vagas liberadas por desistência.
- **Profissional de Saúde (Médicos, Enfermeiros, Especialistas):** Usuário operador clínico. Sofre perda severa de produtividade devido a janelas ociosas na escala diária. Necessita de uma grade dinâmica, atualizada e com tempo de atendimento padronizado por tipo de procedimento.
- **Gestor da Unidade Básica de Saúde:** Mantenedor e regulador. Responsável pelo cumprimento de metas de atendimento, taxa de ocupação das salas e redução de filas. Necessita de relatórios consolidados de absenteísmo por especialidade e bloqueio automático de pacientes com reincidência de faltas injustificadas.
- **Cidadãos na Fila de Regulação:** Beneficiários diretos. São atendidos mais rapidamente por meio da reoferta automática de vagas oriundas de autoliberação por descarte (RN02).

### 1.3 Alinhamento Estratégico aos Objetivos de Desenvolvimento Sustentável (ODS) da ONU
- **ODS 3 - Saúde e Bem-Estar:**
  - *Meta 3.8:* Assegurar a cobertura de saúde universal e o acesso a serviços essenciais de saúde de qualidade. O VagaCerta reaproveita horários ociosos, expandindo a capacidade instalada sem necessidade de novos aportes financeiros em infraestrutura física.
  - *Meta 3.4:* Reduzir a mortalidade prematura por doenças crônicas não transmissíveis, assegurando que pacientes hipertensos, diabéticos e cardiopatas não tenham seus tratamentos descontinuados por desperdício de consultas.
- **ODS 11 - Cidades e Comunidades Sustentáveis:**
  - *Meta 11.a:* Promoção da eficiência dos serviços públicos urbanos por meio de inovação tecnológica e descarte inteligente de ociosidade operacional.

---

## 2. ESCOPO DO PROJETO E LISTA DE REQUISITOS (PADRÃO FORMAL EXIGIDO)

Em estrita consonância com a métrica de aceitação da UniCesumar, os requisitos do VagaCerta são redigidos no formato padronizado `"O sistema deve permitir..."`:

1. **O sistema deve permitir** o cadastro unificado de usuários nas categorias de Paciente, Profissional de Saúde e Gestor da Unidade, validando obrigatoriamente CPF único, senha em hash seguro e perfil de acesso atribuído.
2. **O sistema deve permitir** que o Profissional de Saúde ou Gestor gere dinamicamente a grade diária de atendimentos (AgendaDiaria), calculando os intervalos de horários a partir do turno de trabalho e da duração padrão definida para cada especialidade (20 minutos para consulta clínica, 30 minutos para exames e 5 minutos para vacinação).
3. **O sistema deve permitir** que o paciente realize o agendamento de consultas, exames e vacinações em horários disponíveis, garantindo exclusividade absoluta do horário selecionado através de restrição de unicidade transacional no banco de dados.
4. **O sistema deve permitir** a confirmação prévia de agendamentos por meio de janelas temporais polimórficas (24 horas de antecedência para consultas clínicas, 48 horas para exames laboratoriais e isenção de confirmação para vacinações).
5. **O sistema deve permitir** a execução de um motor de autoliberação de vagas que cancela agendamentos pendentes de confirmação fora da janela limite e reoferta imediatamente o horário como livre na fila pública de atendimento.
6. **O sistema deve permitir** o controle de faltas e reincidência de absenteísmo, bloqueando automaticamente o agendamento remoto para pacientes com 3 ou mais faltas não justificadas no período de seis meses, exigindo acolhimento presencial na UBS para liberação.
7. **O sistema deve permitir** a emissão de relatórios de Business Intelligence para a gestão, consolidando taxa de ocupação da grade, taxa percentual de absenteísmo, quantitativo de vagas autoliberadas e histórico por profissional.

---

## 3. TABELA DE CRONOGRAMA DE EXECUÇÃO

Conforme exigido pelo regulamento e espelhado no README do repositório:

| Data | Atividade | Responsável |
| :--- | :--- | :--- |
| **05/08/2026** | Levantamento de requisitos, estudo de absenteísmo em UBS e alinhamento aos ODS 3 e 11 da ONU | Gustavo Antunes da Silva |
| **12/08/2026** | Definição das regras de negócio (RN01 a RN04) e arquitetura em camadas (MVC + DAO) | Rafael Garcia Ribeiro |
| **19/08/2026** | Modelagem orientada a objetos (Diagrama de Classes UML com Herança, Polimorfismo e Composição) | Jonatas Marques da Silva |
| **26/08/2026** | Modelagem física relacional (Diagrama Entidade-Relacionamento DER com PF para herança) | Gustavo Antunes da Silva |
| **02/09/2026** | Elaboração da documentação formal do 1º Bimestre e consolidação do repositório no GitHub | Equipe (Gustavo, Jonatas, Rafael) |
| **09/09/2026** | Implementação do script DDL/DML SQL (`database/schema.sql`) com constraints de integridade | Rafael Garcia Ribeiro |
| **23/09/2026** | Desenvolvimento das classes de domínio em Java 17 puro (`src/br/com/vagacerta/model`) | Jonatas Marques da Silva |
| **07/10/2026** | Implementação da camada de persistência JDBC/DAO com CRUD completo (`src/br/com/vagacerta/dao`) | Gustavo Antunes da Silva |
| **21/10/2026** | Implementação das regras de negócio e motor de autoliberação (`src/br/com/vagacerta/service`) | Rafael Garcia Ribeiro |
| **04/11/2026** | Elaboração da suíte de testes unitários com JUnit 5 (`src/br/com/vagacerta/test`) | Jonatas Marques da Silva |
| **10/11/2026** | Auditoria final de código, compatibilidade SO, empacotamento e entrega final integrada | Equipe (Gustavo, Jonatas, Rafael) |

---

## 4. JUSTIFICATIVA TÉCNICA E ARQUITETURAL

### 4.1 Arquitetura em Camadas (Layered MVC + DAO)
Adotou-se uma arquitetura clássica em camadas, combinando os padrões **Model-View-Controller (MVC)** e **Data Access Object (DAO)**:
- **Camada Model (Domínio):** Encapsula o estado e o comportamento das regras de negócio. Utiliza hierarquias com classes abstratas e métodos polimórficos, garantindo o princípio Aberto/Fechado (OCP do SOLID): novas modalidades de agendamento são adicionadas estendendo a classe base sem modificar o motor existente.
- **Camada DAO (Acesso a Dados):** Isola integralmente os comandos SQL e a manipulação de recursos JDBC (`Connection`, `PreparedStatement`, `ResultSet`). Essa separação estrita impede o vazamento de detalhes de persistência para as camadas superiores.
- **Camada Service (Regras de Negócio):** Coordena os fluxos transacionais que envolvem múltiplas entidades, como a alocação exclusiva de horários (RN01), o motor de autoliberação por expiração de janela (RN02) e a verificação do limite de faltas (RN03).
- **Camada Controller/View (Interface):** Disponibiliza a interface em linha de comando (CLI) e os pontos de integração com sistemas externos e painel gerencial.

### 4.2 Defesa das Escolhas Tecnológicas
- **Java 17 (LTS):** Oferece tipagem estática forte, gerenciamento robusto de memória pela JVM, API moderna de data/hora (`java.time.LocalDate`, `LocalDateTime`, `LocalTime`) e alto desempenho em ambientes corporativos e governamentais.
- **Pure JDBC (`java.sql.*`):** A utilização de JDBC puro, sem frameworks ORM (como Hibernate ou Spring Data), elimina o overhead de mapeamento reflexivo, assegura total previsibilidade das queries executadas e atende integralmente às restrições acadêmicas do curso de Engenharia de Software.
- **Banco de Dados Relacional (MySQL 8 / SQLite):** Garante conformidade estrita com as propriedades ACID (Atomicidade, Consistência, Isolamento e Durabilidade). A exclusividade da vaga é garantida fisicamente por restrição de índice único (`UNIQUE(horario_id)`), evitando qualquer concorrência ou colisão de agendamento em nível de hardware.

---

## 5. ESPECIFICAÇÃO DOS DIAGRAMAS E PILARES DE POO

### 5.1 Pilares de POO Aplicados no Modelo de Classes
1. **Classe Abstrata / Interface:**
   - `Usuario`: Classe base abstrata contendo identificadores comuns (`id`, `nome`, `cpf`, `email`, `senhaHash`, `telefone`, `ativo`), método concreto `autenticar(senha)` e método abstrato `perfil(): PerfilAcesso`.
   - `Agendamento`: Classe base abstrata contendo identificador, chave do horário, paciente, data de criação, status e os métodos abstratos polimórficos `janelaConfirmacaoHoras()`, `duracaoMinutos()`, `exigePreparo()`, `deveSerAutoliberado(agora)` e `obterInstrucoesEspecializadas()`.
2. **Herança Legítima:**
   - `Usuario` é especializada por: `Paciente` (adiciona `cartaoSus`, `dataNascimento`, `faltasNaoJustificadas`), `ProfissionalSaude` (adiciona `registroConselho`, `especialidade`, `cargaHorariaDiaria`) e `GestorUnidade` (adiciona `matricula`, `cargo`).
   - `Agendamento` é especializado por: `ConsultaClinica` (adiciona `motivo`, `retorno`), `Exame` (adiciona `tipoExame`, `orientacaoPreparo`) e `Vacinacao` (adiciona `imunobiologico`, `dose`).
3. **Polimorfismo com Sobrescrita (`@Override`):**
   - Na subclasse `ConsultaClinica`: janela de 24 horas, duração de 20 minutos, sem preparo especial.
   - Na subclasse `Exame`: janela de 48 horas (devido ao jejum e insumos), duração de 30 minutos, com orientações de preparo obrigatório.
   - Na subclasse `Vacinacao`: janela de 0 horas (isenta de confirmação prévia), duração de 5 minutos, atendimento direto na sala de vacinas.
4. **Composição Estrita com Multiplicidade 1:N:**
   - A classe `AgendaDiaria` compõe uma coleção de instâncias de `Horario` (`1` para `1..*`). O objeto `Horario` não possui ciclo de vida independente: sua existência depende da existência da `AgendaDiaria`. No banco de dados relacional, essa relação de composição é refletida pela restrição `ON DELETE CASCADE`.

### 5.2 Mapeamento Objeto-Relacional (Estratégia de Tabelas Separadas)
Cada especialização da herança orientada a objetos é persistida em sua própria tabela física:
- As tabelas `paciente`, `profissional_saude` e `gestor_unidade` compartilham o identificador `usuario_id` que é concomitantemente Chave Primária e Chave Estrangeira (PF), referenciando a tabela base `usuario`.
- Da mesma forma, `consulta_clinica`, `exame` e `vacinacao` possuem a chave `agendamento_id` (PF) referenciando `agendamento`.
- A integridade da exclusividade de agendamento (RN01) é assegurada pela restrição física `CONSTRAINT uq_agendamento_horario UNIQUE (horario_id)`.
