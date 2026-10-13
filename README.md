# Cofre Digital Seguro

MVP full-stack de um cofre digital: usuários autenticados guardam credenciais, chaves de API e notas privadas, e só eles conseguem ler, alterar ou excluir os próprios registros. Os segredos ficam criptografados no banco (AES-256-GCM).

> **Estado da validação.** O backend e o frontend foram escritos e revisados, mas o ambiente em que este código foi produzido bloqueava o acesso ao Maven Central e ao registro do npm. Por isso, **`mvn test` e `npm run build` ainda não foram executados**. Rode ambos (seção [Testes](#testes)) antes de publicar ou apresentar o projeto, e corrija qualquer falha que apareça.

## Sumário

1. [Funcionalidades](#funcionalidades)
2. [Tecnologias](#tecnologias)
3. [Arquitetura](#arquitetura)
4. [Estrutura de diretórios](#estrutura-de-diretórios)
5. [Pré-requisitos](#pré-requisitos)
6. [Configuração do ambiente](#configuração-do-ambiente)
7. [Executando](#executando)
8. [Testes](#testes)
9. [API](#api)
10. [Segurança](#segurança)
11. [Trade-offs e decisões técnicas](#trade-offs-e-decisões-técnicas)
12. [Limitações conhecidas](#limitações-conhecidas)
13. [Próximas versões](#próximas-versões)
14. [Publicação no GitHub](#publicação-no-github)

## Funcionalidades

**Autenticação**
- Cadastro, login, logout e consulta do usuário autenticado (`/auth/me`).
- Senhas com hash BCrypt (custo 12).
- Sessão por JWT em cookie `HttpOnly`, com expiração configurável.
- Bloqueio temporário após tentativas de login malsucedidas.

**Cofre**
- CRUD completo de registros dos tipos `CREDENTIAL`, `API_KEY`, `SECURE_NOTE` e `OTHER`.
- Campos: título, tipo, usuário/identificador (opcional), segredo, URL (opcional), descrição (opcional), datas de criação e atualização.
- Cada usuário enxerga apenas os próprios registros.

**Frontend**
- Telas de login, cadastro, dashboard (com busca por texto e filtro por tipo), detalhe, criação e edição.
- Segredo mascarado por padrão, com botões de mostrar/ocultar e copiar. Ao revelar, volta a ficar oculto sozinho após 30 segundos.
- Exclusão com diálogo de confirmação, estados de carregamento, erro e vazio, e tratamento de sessão expirada.

## Tecnologias

| Camada | Tecnologias |
|---|---|
| Backend | Java 21, Spring Boot 3.5.7, Spring Web, Spring Security, Spring Data JPA, Bean Validation, Flyway, jjwt 0.12.6, Maven |
| Banco | PostgreSQL 16 (instalado localmente, sem Docker) |
| Testes | JUnit 5, Mockito, Spring Security Test, MockMvc |
| Frontend | React 18, TypeScript, Vite 5, Tailwind CSS 3, React Router 6, lucide-react |

## Arquitetura

```
Navegador (React SPA)
   │  /api/*  (mesma origem em dev, via proxy do Vite)
   ▼
Spring Boot ── RequestSizeLimitFilter → JwtAuthenticationFilter → CSRF → autorização
   │            controller → service → repository (JPA)
   ▼
PostgreSQL (Flyway: V1 users, V2 vault_items)
```

Fluxo de autenticação:

1. `POST /auth/login` valida as credenciais e responde com `Set-Cookie: vault_token=<JWT>; HttpOnly; SameSite=Strict; Path=/api`.
2. O navegador envia o cookie automaticamente. O `JwtAuthenticationFilter` valida assinatura, emissor e expiração e coloca o ID do usuário no contexto de segurança.
3. Controllers e services só usam esse ID. **Nenhum endpoint aceita ID de usuário vindo do cliente.**

Pacotes do backend (`com.securevault`): `config`, `security`, `auth`, `user`, `vault`, `crypto`, `exception`.

## Estrutura de diretórios

```
secure-vault/
├── .env.example            # variáveis fictícias (copie para .env)
├── .gitignore
├── README.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/securevault/{config,security,auth,user,vault,crypto,exception}
│       ├── main/resources/{application.yml,db/migration/}
│       └── test/                      # testes unitários e de integração
└── frontend/
    ├── package.json, vite.config.ts, tailwind.config.js
    └── src/{api,auth,components,pages,lib}
```

## Pré-requisitos

- JDK 21
- Maven 3.9+
- Node.js 20+ e npm
- PostgreSQL 14+ (o projeto foi escrito contra o 16) instalado na máquina

## Configuração do ambiente

### 1. PostgreSQL local

**Linux (Debian/Ubuntu)**
```bash
sudo apt install postgresql
sudo systemctl start postgresql      # ou: sudo pg_ctlcluster 16 main start
```
**macOS (Homebrew)**
```bash
brew install postgresql@16 && brew services start postgresql@16
```
**Windows:** instale pelo instalador oficial e inicie o serviço "postgresql-x64-16" (Serviços do Windows).

### 2. Usuário e bancos

Escolha uma senha forte e substitua `SUA_SENHA_AQUI` (não a versione em lugar nenhum). No Linux, entre com `sudo -u postgres psql`; nos demais sistemas, use o `psql` com o superusuário que você configurou.

```sql
CREATE USER vault_app WITH PASSWORD 'SUA_SENHA_AQUI';
CREATE DATABASE vault_db      OWNER vault_app;
CREATE DATABASE vault_test_db OWNER vault_app;
```

`vault_db` é o banco da aplicação. `vault_test_db` é dedicado aos testes de integração, que **apagam os dados** das tabelas antes de cada teste. Nunca aponte os testes para o banco real.

Verifique a conexão:
```bash
psql -h localhost -U vault_app -d vault_db -c "select 1"
```

### 3. Arquivo `.env`

Na raiz do repositório:
```bash
cp .env.example .env
```
Edite o `.env` e preencha:

| Variável | Descrição |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão com `vault_db` |
| `TEST_DB_URL` | Conexão com `vault_test_db` (usa as mesmas credenciais, salvo `TEST_DB_USERNAME`/`TEST_DB_PASSWORD`) |
| `JWT_SECRET` | Mínimo de 32 caracteres. `openssl rand -base64 48` |
| `JWT_EXPIRATION_MINUTES` | Duração da sessão (padrão 60) |
| `VAULT_ENCRYPTION_KEY` | Exatamente 32 bytes em Base64. `openssl rand -base64 32` |
| `COOKIE_SECURE` | `false` em `http://localhost`; `true` em HTTPS |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula (sem `*`) |

O backend lê `../.env` ao ser executado a partir de `backend/`. Variáveis de ambiente reais têm precedência sobre o arquivo. O `.env` está no `.gitignore`.

> **Atenção:** se você perder ou trocar `VAULT_ENCRYPTION_KEY`, os registros já salvos não poderão mais ser descriptografados.

### 4. Migrações

O Flyway roda automaticamente na inicialização do backend e cria as tabelas `users` e `vault_items`. O Hibernate apenas valida o schema (`ddl-auto=validate`). Para conferir:
```bash
psql -h localhost -U vault_app -d vault_db -c "select version, description, success from flyway_schema_history"
```

## Executando

Backend (porta 8080):
```bash
cd backend
mvn spring-boot:run
```

Frontend (porta 5173), em outro terminal:
```bash
cd frontend
npm install
npm run dev
```

Abra http://localhost:5173, crie uma conta e faça login. O Vite encaminha `/api` para `http://localhost:8080` (altere com `VITE_API_PROXY_TARGET`, ver `frontend/.env.example`). Assim o navegador usa uma única origem, o que é necessário para o cookie `SameSite=Strict`.

Build de produção do frontend: `npm run build` (saída em `frontend/dist`). O projeto não inclui servidor para os arquivos estáticos nem configuração de proxy reverso em produção (ver [Limitações](#limitações-conhecidas)).

## Testes

```bash
cd backend
mvn test            # exige PostgreSQL ativo e o banco vault_test_db criado

cd ../frontend
npm run build       # verifica os tipos (tsc) e gera o bundle
```

O backend tem testes unitários (serviços, JWT, criptografia, limitador de login) e testes de integração com MockMvc, filtros de segurança reais, Flyway e PostgreSQL. Cobrem:

- cadastro válido, inválido e com e-mail duplicado; login correto e com senha errada;
- acesso sem autenticação às rotas privadas;
- criação e listagem restritas ao proprietário;
- leitura, edição e exclusão de registro de **outro** usuário, que devem retornar 404 mesmo com o ID correto;
- rejeição de payloads inválidos, grandes demais ou com campos extras (por exemplo `ownerId`);
- verificação direto no banco de que segredo e usuário **não** estão em texto puro;
- falhas de criptografia respondidas como erro genérico, sem vazar detalhes.

O frontend não tem testes automatizados nesta versão.

## API

Prefixo `/api/v1`. Não há Swagger/OpenAPI neste MVP. Requisições que alteram estado (`POST`, `PUT`, `DELETE`) exigem o cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` (o frontend faz isso sozinho).

| Método e rota | Auth | Descrição | Respostas |
|---|---|---|---|
| `POST /auth/register` | não | Cria usuário | 201, 400, 409 |
| `POST /auth/login` | não | Autentica e define o cookie | 200, 400, 401, 429 |
| `POST /auth/logout` | não | Remove o cookie | 204 |
| `GET /auth/me` | sim | Usuário autenticado | 200, 401 |
| `GET /vault-items` | sim | Lista registros do usuário (sem segredos) | 200, 401 |
| `POST /vault-items` | sim | Cria registro | 201, 400, 401 |
| `GET /vault-items/{id}` | sim | Detalhe, com segredo descriptografado | 200, 401, 404 |
| `PUT /vault-items/{id}` | sim | Atualiza registro | 200, 400, 401, 404 |
| `DELETE /vault-items/{id}` | sim | Exclui registro | 204, 401, 404 |

Exemplo com `curl` (o cookie e o token CSRF ficam num arquivo de cookies):
```bash
J=/tmp/cookies.txt
curl -s -c $J http://localhost:8080/api/v1/auth/csrf
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J)

curl -s -b $J -c $J -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $X" \
  -d '{"name":"Ana","email":"ana@example.com","password":"uma-senha-bem-longa"}'

curl -s -b $J -c $J -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $X" \
  -d '{"email":"ana@example.com","password":"uma-senha-bem-longa"}'

curl -s -b $J -X POST http://localhost:8080/api/v1/vault-items \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $X" \
  -d '{"title":"GitHub","type":"CREDENTIAL","username":"ana","secret":"s3gredo","url":"https://github.com"}'
```

Erros seguem sempre o mesmo formato:
```json
{
  "timestamp": "2026-10-13T15:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Há campos inválidos na requisição.",
  "path": "/api/v1/vault-items",
  "fieldErrors": [{ "field": "title", "message": "O título é obrigatório." }]
}
```

## Segurança

### Autenticação e sessão
- JWT HS256 com apenas ID do usuário, emissor, emissão e expiração. A validação confere assinatura, emissor e expiração; tokens ausentes, inválidos ou expirados resultam em 401.
- O token vive em cookie `HttpOnly` (inacessível a JavaScript, o que reduz o impacto de XSS), `SameSite=Strict`, `Path=/api` e `Secure` quando `COOKIE_SECURE=true`.
- Como o navegador envia o cookie sozinho, há proteção CSRF por *double submit cookie*: o cookie `XSRF-TOKEN` precisa coincidir com o cabeçalho `X-XSRF-TOKEN` em toda requisição que altera estado. Isso soma-se a `SameSite=Strict` e a um CORS restrito.
- Login protegido por limitador em memória: 5 falhas em 15 minutos para a mesma combinação IP + e-mail bloqueiam novas tentativas por 15 minutos (HTTP 429 com `Retry-After`). A mensagem de erro de credenciais é a mesma para e-mail inexistente e senha errada.

### Autorização por proprietário
- O repositório de registros **não herda `JpaRepository`**: só existem consultas que exigem o ID do dono (`findByIdAndOwnerId`, `deleteByIdAndOwnerId` etc.). Não existe `findById` global, então não dá para esquecer a verificação.
- O proprietário é sempre o usuário autenticado. O DTO de entrada não tem campo de dono e o Jackson rejeita campos desconhecidos (`fail-on-unknown-properties`).
- Registro inexistente e registro alheio geram a mesma resposta 404.

### Criptografia dos registros
- Campos confidenciais (**segredo** e **usuário/identificador**) são cifrados com **AES-256-GCM**, com nonce aleatório de 96 bits novo a cada operação.
- Formato armazenado em `BYTEA`: `[versão: 1 byte][nonce: 12 bytes][texto cifrado + tag de 16 bytes]`.
- Dados autenticados adicionais (AAD) ligam cada texto cifrado ao proprietário, ao registro e ao campo. Mover um valor cifrado para outro registro faz a descriptografia falhar.
- A chave vem só de `VAULT_ENCRYPTION_KEY` e nunca é gravada no banco. A aplicação se recusa a iniciar com chave inválida.
- A listagem não descriptografa nada; só o `GET /vault-items/{id}` descriptografa, depois de verificar a propriedade.
- **Isto não é criptografia de ponta a ponta.** O servidor precisa descriptografar para entregar o dado ao usuário; quem controla o servidor e a chave consegue ler os segredos.
- Título, tipo, URL e descrição ficam em texto puro no banco, para listagem e busca.

### Demais medidas
- Bean Validation com limites de tamanho em todos os DTOs; corpo de requisição limitado a 64 KiB.
- Consultas parametrizadas via JPA; schema gerenciado só pelo Flyway.
- E-mail normalizado em minúsculas, com restrição de unicidade e `CHECK` no banco.
- `@RestControllerAdvice` central: erros sem stack trace, SQL ou detalhes internos; mensagens do Jackson não são repassadas.
- Cabeçalhos: `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (nas respostas da API), `Referrer-Policy: no-referrer`, `Permissions-Policy` e `Cache-Control: no-store`.
- CORS com origens explícitas (recusa `*`), credenciais habilitadas e apenas os cabeçalhos necessários.
- Segredos de configuração só por variáveis de ambiente; `.env` no `.gitignore`; `toString()` dos DTOs sensíveis mascara senhas e segredos.
- Frontend: segredos mantidos só em memória (sem `localStorage`), sem renderização de HTML do usuário (o React escapa o texto), links de URL só para `http(s)`, redirecionamento ao login quando a API responde 401. Essas verificações do frontend são apenas conveniência; a autorização real é do backend.

## Trade-offs e decisões técnicas

- **Cookie HttpOnly em vez de Bearer token no `localStorage`.** Um token em `localStorage` pode ser roubado por qualquer XSS. O custo do cookie é precisar de CSRF e de uma única origem (daí o proxy do Vite).
- **JWT stateless.** Simplifica o backend, mas o logout apenas apaga o cookie: um token já emitido continua válido até expirar (ver limitações). Por isso a expiração é curta (60 min por padrão).
- **Limitador de login em memória.** É simples e suficiente para um MVP de instância única, mas não sobrevive a reinício nem funciona com várias instâncias.
- **Chave única de criptografia por ambiente.** O byte de versão no formato abre caminho para rotação, mas a rotação em si não foi implementada.
- **Repositório restrito em vez de verificação manual.** Remove uma classe inteira de erros de autorização, ao custo de um pouco menos de conveniência.
- **Sem bibliotecas de estado/requisição no frontend.** Um cliente `fetch` pequeno cobre as necessidades do MVP e reduz dependências.
- **Fora do MVP por tempo e foco (96 h, 60% da avaliação em backend e segurança):** compartilhamento entre usuários, recuperação de conta, auditoria, rotação de chaves, OpenAPI, testes do frontend e CI.

## Limitações conhecidas

- O JWT não pode ser revogado no servidor antes de expirar; o logout só limpa o cookie.
- O limitador de login é local à instância e perde o estado ao reiniciar. O cadastro não tem limitação de taxa.
- O IP usado no limitador é o do socket. Atrás de um proxy reverso, todos os usuários parecem vir do mesmo IP até que isso seja configurado.
- Não há rotação da chave de criptografia nem recuperação de conta/senha.
- Sem auditoria de acessos aos segredos.
- Em produção é necessário HTTPS (`COOKIE_SECURE=true`) e servir frontend e API na mesma origem (por exemplo, com proxy reverso). Essa configuração não está incluída.
- Não foi feita análise de dependências nem teste de penetração.
- O frontend não tem testes automatizados e ainda não foi compilado nem testado manualmente (ver aviso no topo).

## Próximas versões

Itens **planejados, não implementados**:

- Compartilhamento temporário e revogável de registros.
- Criptografia ponta a ponta.
- Auditoria de acesso.
- Rotação de chaves.
- Recuperação segura de conta.
- Limitação de tentativas mais robusta (compartilhada entre instâncias, também no cadastro).
- Pipeline de integração contínua.
- Revogação de tokens e refresh tokens.
- OpenAPI/Swagger e testes do frontend.

## Publicação no GitHub

1. Confirme que `.env` não será versionado: `git status` não deve listá-lo e `git check-ignore -v .env` deve apontar o `.gitignore`.
2. Procure segredos antes do primeiro commit: `git grep -nIE "password|secret|key" -- ':!README.md' ':!*.example'` e revise o resultado.
3. Crie o repositório público no GitHub e publique:
   ```bash
   git add -A
   git commit -m "MVP do cofre digital seguro"
   git branch -M main
   git remote add origin https://github.com/<seu-usuario>/<repositorio>.git
   git push -u origin main
   ```
4. Execute `mvn test` e `npm run build` antes de enviar e confirme que passam.
