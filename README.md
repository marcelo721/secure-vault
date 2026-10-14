# Cofre Digital Seguro

MVP full-stack de um cofre digital para armazenar credenciais, chaves de API e notas privadas. Cada usuário pode acessar e gerenciar apenas os próprios registros, cujos dados confidenciais são criptografados com AES-256-GCM.

> **Estado do projeto:** backend e frontend implementados, mas os testes e o build precisam ser executados no ambiente local antes da apresentação ou publicação do projeto.

## Sumário

1. Funcionalidades
2. Tecnologias
3. Arquitetura
4. Estrutura de diretórios
5. Pré-requisitos
6. Configuração do ambiente
7. Executando
8. Testes
9. API
10. Segurança
11. Decisões técnicas
12. Limitações conhecidas
13. Próximas versões
14. Publicação no GitHub

## 1. Funcionalidades

### Autenticação
- Cadastro, login, logout e consulta do usuário autenticado.
- Senhas armazenadas com hash BCrypt, custo 12.
- Autenticação por JWT em cookie `HttpOnly`.
- Expiração configurável da sessão.
- Bloqueio temporário após tentativas de login malsucedidas.

### Cofre
- CRUD de registros dos tipos `CREDENTIAL`, `API_KEY`, `SECURE_NOTE` e `OTHER`.
- Campos: título, tipo, usuário/identificador, segredo, URL, descrição e datas.
- Acesso restrito aos registros do usuário autenticado.
- Segredos e identificadores confidenciais criptografados no banco.

### Frontend
- Telas de login, cadastro, dashboard, detalhe, criação e edição.
- Busca por texto e filtro por tipo.
- Segredo oculto por padrão, com opções para revelar e copiar.
- Ocultação automática do segredo revelado após 30 segundos.
- Confirmação antes da exclusão.
- Estados de carregamento, erro e lista vazia.
- Tratamento de sessão expirada.

## 2. Tecnologias

| Camada | Tecnologias |
|---|---|
| Backend | Java 21, Spring Boot 3.5.7, Spring Web, Spring Security, Spring Data JPA, Bean Validation |
| Persistência | H2 Database em memória |
| Migrações | Flyway |
| Autenticação | JWT, jjwt 0.12.6, BCrypt |
| Criptografia | AES-256-GCM |
| Testes | JUnit 5, Mockito, Spring Security Test, MockMvc |
| Frontend | React 18, TypeScript, Vite 5, Tailwind CSS 3, React Router 6, lucide-react |
| Build | Maven, npm |


## 3. Por que Java com Spring Boot?

A escolha de Java com Spring Boot foi motivada pela necessidade de construir um backend seguro, organizado e fácil de manter, sem aumentar desnecessariamente a complexidade do MVP.

Java

Tipagem estática: ajuda a identificar inconsistências durante o desenvolvimento e favorece a manutenção do código.

Orientação a objetos: facilita separar responsabilidades e organizar regras de negócio em componentes bem definidos.

Ecossistema maduro: oferece bibliotecas consolidadas para criptografia, autenticação, validação e persistência de dados.

Portabilidade: permite executar a aplicação em diferentes ambientes que disponibilizem uma JVM compatível.

Spring Boot

Desenvolvimento de APIs REST: simplifica a criação de endpoints HTTP e a integração com o frontend React.

Injeção de dependências: reduz o acoplamento entre controllers, serviços e repositórios, facilitando testes e manutenção.

Spring Security: fornece mecanismos para autenticação, autorização e integração com filtros de segurança.

Spring Data JPA: simplifica o acesso ao banco de dados e a implementação da camada de persistência.

Bean Validation: permite validar os dados de entrada de forma declarativa, reduzindo a duplicação de regras.

Integração com Flyway: permite controlar a evolução do esquema do banco por meio de migrações versionadas.

Testabilidade: integra-se com JUnit, Mockito e MockMvc, permitindo verificar regras de negócio, endpoints e comportamentos relacionados à segurança.

Relação com os objetivos do projeto

Como o desafio prioriza backend e segurança, Java com Spring Boot permite concentrar o esforço nas regras de autorização por proprietário, no gerenciamento da sessão, na validação das requisições e na proteção dos dados confidenciais com AES-256-GCM.

A combinação também favorece uma arquitetura em camadas, com responsabilidades separadas entre controllers, serviços e repositórios. Isso torna o código mais fácil de testar, revisar e evoluir.

Trade-off: o ecossistema Spring oferece muitas abstrações e exige familiaridade com configuração e injeção de dependências. Para este MVP, esse custo é compensado pelos recursos integrados e pela estrutura que a plataforma oferece para desenvolver e testar uma API segura.


## 4. Arquitetura

```text
Navegador (React SPA)
        |
        | /api/*
        v
Spring Boot
  |
  +-- RequestSizeLimitFilter
  +-- JwtAuthenticationFilter
  +-- CSRF e autorização
  |
  v
Controller -> Service -> Repository
                          |
                          v
                     H2 Database
                     em memória

Flyway -> Migrações do banco
```

### Fluxo de autenticação

1. O usuário envia suas credenciais para o endpoint de login.
2. O backend valida as credenciais e retorna um cookie JWT `HttpOnly`.
3. O navegador envia o cookie automaticamente nas requisições seguintes.
4. O filtro JWT valida a assinatura, o emissor e a expiração do token.
5. O ID do usuário autenticado é utilizado para controlar o acesso aos registros.

Nenhum endpoint deve confiar em um ID de proprietário enviado pelo cliente.

Os pacotes do backend, sob `com.securevault`, são organizados em `config`, `security`, `auth`, `user`, `vault`, `crypto` e `exception`.

## 5. Estrutura de diretórios

```text
secure-vault/
├── .gitignore
├── example.env             # Modelo de configuração
├── .env                    # Configuração local, não versionada
├── README.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/securevault/
│       │   ├── config/
│       │   ├── security/
│       │   ├── auth/
│       │   ├── user/
│       │   ├── vault/
│       │   ├── crypto/
│       │   └── exception/
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/
│       └── test/
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tailwind.config.js
    └── src/
        ├── api/
        ├── auth/
        ├── components/
        ├── pages/
        └── lib/
```

## 6. Pré-requisitos

- JDK 21.
- Maven 3.9 ou superior.
- Node.js 20 ou superior.
- npm.

**Não é necessário instalar PostgreSQL.** O projeto utiliza H2 em memória para simplificar a execução local.
## 7. Configuração do ambiente e execução

### 7.1. Clonar o repositório

Clone o repositório GitHub:

```powershell
git clone https://github.com/marcelo721/secure-vault
```

Entre na pasta do projeto clonado:

```powershell
cd secure-vault
```


### 7.2. Verificar os pré-requisitos

Antes de executar o projeto, verifique se os seguintes programas estão instalados:

- **Java 21**
- **Node.js 20**
- **Git**

Abra o PowerShell ou o Prompt de Comando e execute:

```powershell
java -version
node -v
npm -v
git --version
```

Confirme se o Java corresponde à versão 21 e se o Node.js corresponde à versão 20. O npm é instalado junto com o Node.js.

O projeto utiliza Maven para o backend. Se o repositório incluir o Maven Wrapper (`mvnw.cmd`), não será necessário instalar o Maven globalmente. Caso contrário, instale o Maven 3.9 ou superior.

### 7.3. Criar o arquivo `.env`

Na raiz do repositório clonado, crie um arquivo chamado `.env`, no mesmo nível do `README.md` e do arquivo `.bat`.

O arquivo deve conter as seguintes variáveis:

```dotenv
# JWT - autenticação
JWT_SECRET=4799078a7fc2f4766d01c6131eed4bdeb53c4d745e9e9aba6f0521dca0e4bb32
JWT_EXPIRATION_MINUTES=60

# Criptografia dos arquivos do cofre
VAULT_ENCRYPTION_KEY=jkZTwubXTYnj/1aljZGGI5pTSXrR51rwMbQxWcj+Pys=

# Cookies
COOKIE_SECURE=false

# CORS - endereço do frontend
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

**Atenção:** os valores acima são exemplos de configuração para desenvolvimento local. Para uso real, gere um `JWT_SECRET` forte e independente e uma chave AES-256 aleatória de 32 bytes, codificada em Base64, para `VAULT_ENCRYPTION_KEY`.

Mantenha o arquivo `.env` fora do Git. Não publique suas chaves nem as compartilhe em repositórios públicos.

A chave de criptografia deve permanecer a mesma enquanto existirem registros criptografados com ela. Se ela for perdida ou substituída, os dados existentes poderão ficar irrecuperáveis.

### 7.4. Executar o projeto

Com o `.env` criado e os pré-requisitos verificados, execute o arquivo `.bat` localizado na raiz do repositório.

Você pode dar dois cliques no arquivo pelo Explorador de Arquivos ou executá-lo pelo terminal:

```powershell
iniciar_cofre_digital.bat
```


O script é responsável por iniciar o backend e o frontend conforme sua implementação. Mantenha as janelas de terminal abertas enquanto estiver utilizando a aplicação.

Quando os serviços estiverem disponíveis, acesse o frontend:

**http://localhost:5173**

Se o navegador abrir antes de o frontend estar pronto, aguarde alguns segundos e atualize a página.

Caso a aplicação não inicie, confira as mensagens exibidas nos terminais e verifique se as portas 8080 (backend) e 5173 (frontend) estão livres.
Pré-requisitos e instalação

Antes de executar o Cofre Digital Seguro, instale o Java JDK 21 e o Node.js 20. No Windows, você pode instalar ambos pelo terminal utilizando o Windows Package Manager (winget).

### Instalar o Java 21

Abra o PowerShell ou CMD e execute:
```powershell
winget install --id Microsoft.OpenJDK.21 -e --source winget
```


Verifique a instalação:

java -version
javac -version

2. Instalar o Node.js 20

Execute no terminal:
```powershell
winget install --id OpenJS.NodeJS.20 -e --source winget
```


Verifique a instalação do Node.js e do npm:

node -v
npm -v

O Node.js deve apresentar uma versão v20.x.

3. Preparar o ambiente

Após instalar os requisitos, feche e abra novamente o terminal para atualizar as variáveis de ambiente.

O backend utiliza o Spring Boot e o Maven Wrapper (mvnw.cmd), que permite executar o Maven sem precisar instalá-lo globalmente. Na primeira execução, é necessário ter conexão com a internet para baixar o Maven e as dependências do projeto.

O frontend utiliza React, Vite e npm para instalar as dependências e iniciar o servidor de desenvolvimento.

Observação: o banco de dados e as variáveis de ambiente definidas no arquivo .env também precisam estar configurados conforme os requisitos do projeto.
## 8. Acessando o banco de dados H2

O projeto utiliza o **H2 Database em memória** para armazenar os dados durante a execução local. O banco é inicializado pelo backend, e as migrações do Flyway são executadas na inicialização.

Para visualizar as tabelas e consultar os dados, siga os passos abaixo.

### 8.1. Abrir o H2 Console

Com o backend em execução, abra o navegador e acesse:

**http://localhost:8080/h2-console**

A interface de conexão do H2 será exibida.

### 8.2. Preencher os dados de conexão

Na tela de login do H2 Console, utilize os seguintes valores:

| Campo | Valor |
|---|---|
| Saved Settings | Generic H2 (Embedded), se disponível |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:securevault` |
| User Name | `sa` |
| Password | Deixe vazio |

Clique em **Connect** para estabelecer a conexão.

A URL JDBC deve corresponder ao banco configurado no `application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:securevault;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

**Importante:** no campo JDBC URL, utilize `jdbc:h2:mem:securevault`, e não um caminho de arquivo como `C:/Users/.../test`. O projeto está configurado para utilizar um banco em memória.

### 8.3. Consultar as tabelas

Após conectar, utilize o painel lateral para visualizar as tabelas existentes ou execute consultas SQL no editor.

Por exemplo, para listar as tabelas do esquema atual:

```sql
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'PUBLIC';
```

Para consultar os registros de uma tabela, utilize o nome correspondente ao esquema criado pelas migrações do Flyway. Por exemplo:

```sql
SELECT * FROM USERS;
```

O nome `USERS` é apenas ilustrativo: utilize o nome real da tabela caso seja diferente no seu projeto.

**Observação sobre os dados:** o H2 é volátil. Os dados são mantidos enquanto a instância do banco estiver ativa, mas podem ser perdidos quando a aplicação é encerrada e o banco em memória é destruído. Para consultar o banco, mantenha o backend em execução.

## 9. Testes

### Backend

Na pasta `backend`:

```powershell
mvn test
```

Com a configuração H2, os testes que utilizam essa configuração não precisam de uma instância PostgreSQL.

Se houver um perfil de testes separado, confira o `application-test.properties` ou `application-test.yml` para verificar qual banco ele utiliza.

Os testes previstos incluem:

- Serviços, JWT, criptografia e limitador de login.
- Cadastro válido e inválido.
- Rejeição de e-mail duplicado.
- Login correto e incorreto.
- Acesso não autenticado às rotas privadas.
- Restrição dos registros ao proprietário.
- Leitura, edição e exclusão de registros pertencentes a outros usuários.
- Rejeição de payloads inválidos, grandes ou com campos desconhecidos.
- Verificação da criptografia dos campos confidenciais.
- Tratamento genérico de falhas de criptografia.

### Frontend

Na pasta `frontend`:

```powershell
npm run build
```

Esse comando verifica os tipos TypeScript e gera o bundle de produção.

O frontend não possui testes automatizados nesta versão.

**Antes de apresentar o projeto, execute os dois comandos e registre os resultados reais.** Não declare os testes aprovados sem executá-los.

## 10. API

Prefixo: `/api/v1`.

| Método | Rota | Autenticação | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | Não | Cadastra usuário |
| `POST` | `/auth/login` | Não | Autentica e define o cookie |
| `POST` | `/auth/logout` | Conforme configuração de segurança | Encerra a sessão no navegador |
| `GET` | `/auth/me` | Sim | Consulta usuário autenticado |
| `GET` | `/vault-items` | Sim | Lista registros do usuário |
| `POST` | `/vault-items` | Sim | Cria registro |
| `GET` | `/vault-items/{id}` | Sim | Consulta registro e revela os campos autorizados |
| `PUT` | `/vault-items/{id}` | Sim | Atualiza registro |
| `DELETE` | `/vault-items/{id}` | Sim | Exclui registro |

As requisições que alteram estado podem exigir o cabeçalho `X-XSRF-TOKEN`, conforme a configuração CSRF do backend. O frontend deve obter e enviar o token CSRF conforme o fluxo implementado.

Consulte os controllers e a configuração de segurança para confirmar os códigos HTTP e os detalhes exatos de cada endpoint.

## 11. Segurança

### Autenticação e sessão

- JWT assinado com HS256, com validação de assinatura, emissor e expiração.
- Cookie `HttpOnly`, `SameSite=Strict`, `Path=/api` e atributo `Secure` configurável.
- Proteção CSRF para requisições que alteram estado.
- Limitador de tentativas de login em memória.
- Respostas de erro sem stack traces ou detalhes internos.

### Autorização por proprietário

- O proprietário é determinado pelo usuário autenticado.
- As operações de acesso ao cofre devem sempre filtrar pelo ID do proprietário.
- Registros inexistentes e registros pertencentes a outros usuários devem receber respostas equivalentes, evitando exposição de informações.
- DTOs não devem permitir que o cliente defina o proprietário.

### Criptografia

- AES-256-GCM para os campos confidenciais.
- Nonce aleatório de 96 bits por operação de criptografia.
- Tag de autenticação de 16 bytes.
- Chave carregada por `VAULT_ENCRYPTION_KEY`, nunca armazenada no banco.
- Validação da chave na inicialização.
- Título, tipo, URL e descrição podem permanecer em texto puro para permitir listagem e busca, conforme a implementação.

**A criptografia não é ponta a ponta.** O backend precisa descriptografar os dados para entregá-los ao usuário autorizado.

### Outras medidas

- Bean Validation nos DTOs.
- Limite de tamanho do corpo das requisições.
- Consultas parametrizadas via JPA.
- Migrações gerenciadas pelo Flyway.
- CORS com origens explicitamente configuradas.
- Cabeçalhos de segurança HTTP.
- Arquivo `.env` excluído do Git.
- Frontend sem armazenamento persistente de segredos no `localStorage`.

Essas medidas precisam ser validadas nos testes e revisadas antes de uma implantação real.

## 12. Decisões técnicas

- **H2 em memória:** reduz o trabalho de configuração e facilita a demonstração local. A desvantagem é a perda dos dados ao encerrar a aplicação.
- **Cookie HttpOnly em vez de JWT no localStorage:** reduz a exposição do token ao JavaScript, mas exige proteção CSRF.
- **JWT stateless:** simplifica a autenticação, mas o logout não revoga um token já emitido no servidor.
- **Limitador em memória:** adequado a uma única instância de MVP; perde o estado ao reiniciar.
- **Repositório restrito por proprietário:** reduz o risco de acesso indevido a registros.
- **Escopo reduzido:** prioriza backend, segurança, autenticação, criptografia e funcionalidades essenciais do cofre.

## 13. Limitações conhecidas

- Os dados do H2 em memória não são persistentes.
- O JWT não pode ser revogado no servidor antes de expirar, salvo implementação adicional.
- O limitador de login não compartilha estado entre instâncias.
- Não há rotação automática da chave de criptografia.
- Não há recuperação de conta ou senha.
- Não há auditoria de acesso aos segredos.
- O frontend não possui testes automatizados.
- A implantação de produção, o proxy reverso e o HTTPS precisam ser configurados separadamente.
- Não foi realizada auditoria de segurança ou teste de penetração.

## 14. Próximas versões

Itens planejados, não necessariamente implementados:

- Compartilhamento temporário e revogável de registros.
- Migração para banco persistente, como PostgreSQL.
- Criptografia ponta a ponta.
- Auditoria de acesso.
- Rotação de chaves.
- Recuperação segura de conta.
- Limitador de tentativas compartilhado entre instâncias.
- Pipeline de integração contínua.
- Revogação de sessões e tokens.
- OpenAPI/Swagger.
- Testes automatizados do frontend.
