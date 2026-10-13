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


3. Por que Java com Spring Boot?

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

## 7. Configuração do ambiente

### 7.1. Arquivo de variáveis

Na raiz do repositório, mantenha `example.env` como modelo e crie uma cópia chamada `.env`.

No PowerShell, execute a partir da raiz do projeto:

```powershell
Copy-Item example.env .env
```

Edite o arquivo `.env` e configure as variáveis:

| Variável | Descrição |
|---|---|
| `JWT_SECRET` | Segredo utilizado para assinar os JWTs. Utilize um valor forte, com pelo menos 32 caracteres. |
| `JWT_EXPIRATION_MINUTES` | Duração da sessão em minutos. Padrão: 60. |
| `VAULT_ENCRYPTION_KEY` | Chave AES-256 de exatamente 32 bytes, codificada em Base64. |
| `COOKIE_SECURE` | `false` para desenvolvimento local em HTTP; `true` em produção com HTTPS. |
| `CORS_ALLOWED_ORIGINS` | Origens autorizadas, como `http://localhost:5173`. |

Exemplo de estrutura:

```properties
JWT_SECRET=SUBSTITUA_POR_UM_SEGREDO_FORTE
JWT_EXPIRATION_MINUTES=60
VAULT_ENCRYPTION_KEY=SUBSTITUA_PELA_CHAVE_BASE64
COOKIE_SECURE=false
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Os valores acima são ilustrativos. Gere seus próprios segredos e não publique o arquivo `.env`.

### 7.2. Geração das chaves no Windows

Para gerar a chave de criptografia AES-256 no PowerShell:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$key = New-Object byte[] 32
$rng.GetBytes($key)
[Convert]::ToBase64String($key)
$rng.Dispose()
```

Copie o resultado para `VAULT_ENCRYPTION_KEY` no `.env`.

O resultado representa 32 bytes aleatórios em Base64. Não gere uma chave nova a cada inicialização: registros criptografados com a chave anterior não poderão ser descriptografados com outra chave.

Para o `JWT_SECRET`, utilize um segredo forte, independente da chave AES.

### 7.3. Carregamento das variáveis pelo Spring Boot

No `backend/src/main/resources/application.properties`, a configuração para carregar o `.env` da raiz do repositório, ao executar a aplicação a partir de `backend/`, é:

```properties
spring.application.name=secure-vault
spring.config.import=optional:file:../.env[.properties]

app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-minutes=${JWT_EXPIRATION_MINUTES:60}
app.jwt.issuer=secure-vault

app.crypto.key=${VAULT_ENCRYPTION_KEY}

app.cookie.secure=${COOKIE_SECURE:false}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

O caminho `../.env` depende do diretório de trabalho da aplicação. Se você executar o backend a partir de outro diretório, ajuste o caminho.

O Spring Boot não carrega automaticamente arquivos `.env`: a propriedade `spring.config.import` é responsável por importar o arquivo. O nome configurado deve corresponder ao arquivo real.

O arquivo `example.env` serve apenas como modelo. Ele não será carregado pela configuração acima, a menos que o caminho seja alterado explicitamente.

Variáveis de ambiente do sistema podem sobrescrever os valores do arquivo importado.

### 7.4. Banco de dados H2

O projeto utiliza H2 em memória, configurado no `application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:securevault;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

O Flyway executa as migrações na inicialização, e o Hibernate valida o esquema existente.

**Limitação importante:** o banco é volátil. Os registros armazenados são perdidos quando a aplicação termina e o banco em memória é encerrado. Essa configuração é adequada para desenvolvimento e demonstração, mas não para armazenar dados reais de forma persistente.

## 8. Executando

### Backend

Em um terminal, a partir da raiz do projeto:

```powershell
cd backend
mvn spring-boot:run
```

O backend será iniciado na porta 8080, caso não exista outra configuração.

### Frontend

Em outro terminal:

```powershell
cd frontend
npm install
npm run dev
```

Abra:

`http://localhost:5173`

O Vite encaminha as requisições `/api` para o backend em `http://localhost:8080`, conforme a configuração do proxy.

Crie uma conta, faça login e teste as operações do cofre.

### Build de produção

Na pasta `frontend`:

```powershell
npm run build
```

Os arquivos gerados ficam em `frontend/dist`.

O projeto ainda exige configuração de hospedagem do frontend e de proxy reverso para uma implantação de produção. Utilize HTTPS e `COOKIE_SECURE=true` em produção.

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

## 15. Publicação no GitHub

Antes de publicar:

1. Confira se `.env` está no `.gitignore`.
2. Verifique se o arquivo não está sendo acompanhado pelo Git:

```powershell
git status
git check-ignore -v .env
```

3. Revise os arquivos em busca de senhas, tokens e chaves. Não publique valores reais de configuração.
4. Execute os testes:

```powershell
cd backend
mvn test

cd ../frontend
npm run build
```

5. Após verificar os resultados, publique o projeto:

```bash
git add -A
git commit -m "MVP do cofre digital seguro"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/SEU_REPOSITORIO.git
git push -u origin main
```

Substitua o endereço pelo repositório real. Mantenha `example.env` apenas com valores fictícios e nunca versione o `.env` com segredos reais.
