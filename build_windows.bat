@echo off
setlocal
cd /d "%~dp0"
set "PYTHON=%~dp0.venv\Scripts\python.exe"
if not exist "%PYTHON%" (
  echo Missing .venv. Create it with: python -m venv .venv
  exit /b 1
)
"%PYTHON%" -m pip install -r requirements.txt
"%PYTHON%" -m PyInstaller --noconfirm --clean --onefile --windowed --name CQUST_Wifi --collect-all customtkinter --collect-all keyring gui.py
if errorlevel 1 (
  echo.
  echo Build failed. Read the error above.
  exit /b 1
)
echo.
echo Build complete: dist\CQUST_Wifi.exe
