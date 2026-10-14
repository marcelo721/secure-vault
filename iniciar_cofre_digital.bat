 @echo off
setlocal EnableExtensions
title Cofre Digital Seguro - Inicializador

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "FRONTEND_DIR=%ROOT_DIR%frontend"
set "FRONTEND_URL=http://127.0.0.1:5173/"
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"

echo ==========================================
echo       COFRE DIGITAL SEGURO
echo ==========================================
echo.

REM ==========================================
REM 1. Verificar requisitos
REM ==========================================

where java >nul 2>&1
if errorlevel 1 (
    echo ERRO: Java nao encontrado.
    echo Instale o JDK 21 e configure o PATH.
    goto :ERROR
)

where node >nul 2>&1
if errorlevel 1 (
    echo ERRO: Node.js nao encontrado.
    echo Instale o Node.js LTS.
    goto :ERROR
)

where npm >nul 2>&1
if errorlevel 1 (
    echo ERRO: npm nao encontrado.
    echo Reinstale o Node.js LTS.
    goto :ERROR
)

echo [OK] Java encontrado.
echo [OK] Node.js encontrado.
echo [OK] npm encontrado.
echo.

REM ==========================================
REM 2. Verificar estrutura do projeto
REM ==========================================

if not exist "%BACKEND_DIR%\pom.xml" (
    echo ERRO: backend\pom.xml nao encontrado.
    goto :ERROR
)

if not exist "%FRONTEND_DIR%\package.json" (
    echo ERRO: frontend\package.json nao encontrado.
    goto :ERROR
)

echo [OK] Estrutura do projeto encontrada.
echo.

REM ==========================================
REM 3. Verificar arquivo .env
REM ==========================================

if not exist "%ROOT_DIR%.env" (
    echo AVISO: arquivo .env nao encontrado na raiz.
    echo Verifique se as variaveis de ambiente
    echo necessarias ao backend estao configuradas.
    echo.
)

REM ==========================================
REM 4. Verificar porta do backend
REM ==========================================

echo Verificando a porta %BACKEND_PORT%...

netstat -ano | findstr /R /C:":%BACKEND_PORT% .*LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo AVISO: a porta %BACKEND_PORT% ja esta em uso.
    echo O backend pode ja estar rodando ou ocorrer
    echo conflito de porta.
    echo.
    goto :START_FRONTEND
)

REM ==========================================
REM 5. Iniciar backend
REM ==========================================

echo Iniciando backend Spring Boot...

if exist "%BACKEND_DIR%\mvnw.cmd" (
    start "Cofre - Backend" cmd /k "cd /d ""%BACKEND_DIR%"" && mvnw.cmd spring-boot:run"
) else (
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo ERRO: Maven nao encontrado e mvnw.cmd inexistente.
        goto :ERROR
    )

    start "Cofre - Backend" cmd /k "cd /d ""%BACKEND_DIR%"" && mvn spring-boot:run"
)

echo Backend solicitado para iniciar.
echo.

REM ==========================================
REM 6. Preparar e iniciar frontend
REM ==========================================

:START_FRONTEND

echo Verificando dependencias do frontend...

if not exist "%FRONTEND_DIR%\node_modules" (
    echo node_modules nao encontrado.
    echo Instalando dependencias. Aguarde...

    pushd "%FRONTEND_DIR%"
    call npm install
    set "NPM_EXIT_CODE=%ERRORLEVEL%"
    popd

    if not "%NPM_EXIT_CODE%"=="0" (
        echo ERRO: npm install falhou.
        echo Verifique a conexao com a internet
        echo e as mensagens de erro acima.
        goto :ERROR
    )
)

echo.
echo Iniciando frontend Vite...

start "Cofre - Frontend" cmd /k "cd /d ""%FRONTEND_DIR%"" && npm run dev -- --host 127.0.0.1"

REM ==========================================
REM 7. Aguardar frontend responder
REM ==========================================

echo.
echo Aguardando o frontend iniciar...

set /a ATTEMPTS=0

:WAIT_FRONTEND
set /a ATTEMPTS+=1

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -Uri '%FRONTEND_URL%' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1

if not errorlevel 1 goto :FRONTEND_READY

if %ATTEMPTS% GEQ 30 goto :FRONTEND_TIMEOUT

timeout /t 2 /nobreak >nul
goto :WAIT_FRONTEND

:FRONTEND_READY

echo.
echo ==========================================
echo       FRONTEND INICIADO COM SUCESSO
echo ==========================================
echo.
echo Frontend: %FRONTEND_URL%
echo Backend:  http://localhost:%BACKEND_PORT%
echo.
echo O backend pode levar mais tempo para iniciar.
echo Mantenha as janelas dos servidores abertas.
echo.

start "" "%FRONTEND_URL%"

goto :FINISH

:FRONTEND_TIMEOUT

echo.
echo ==========================================
echo       ERRO AO INICIAR O FRONTEND
echo ==========================================
echo.
echo O frontend nao respondeu na porta %FRONTEND_PORT%
echo apos aproximadamente 60 segundos.
echo.
echo Verifique a janela Cofre - Frontend.
echo Procure erros de npm, Vite ou dependencias.
echo.
echo O backend pode continuar em execucao.
goto :FINISH

:ERROR
echo.
echo A inicializacao foi interrompida devido a um erro.
echo.
pause
exit /b 1

:FINISH
echo Pressione qualquer tecla para fechar esta janela.
echo As janelas dos servidores permanecerao abertas.
pause >nul
exit /b 0