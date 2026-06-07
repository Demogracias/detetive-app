@echo off
echo ============================================
echo  Detetive - Servidor Local
echo ============================================
echo.
echo A abrir servidor em http://localhost:8000
echo.
echo No telemovel, descubra o IP do computador
echo (ipconfig) e abra http://IP:8000 no Chrome.
echo.
echo Prima CTRL+C para parar o servidor.
echo ============================================
echo.

python --version >nul 2>&1
if %errorlevel% equ 0 (
    python -m http.server 8000
) else (
    echo Python nao encontrado. A tentar Node.js...
    node --version >nul 2>&1
    if %errorlevel% equ 0 (
        npx serve . -p 8000
    ) else (
        echo Nem Python nem Node.js estao instalados.
        echo Instale Python em https://www.python.org/
        pause
    )
)

pause
