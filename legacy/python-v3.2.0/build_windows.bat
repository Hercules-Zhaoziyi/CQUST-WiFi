@echo off
setlocal
cd /d "%~dp0"
set "PYTHON=%~dp0.venv\Scripts\python.exe"
if not exist "%PYTHON%" (
  echo Missing .venv. Create it with: python -m venv .venv
  exit /b 1
)
"%PYTHON%" -m pip install -r requirements.txt
if errorlevel 1 exit /b 1
"%PYTHON%" make_icon.py
if errorlevel 1 exit /b 1
"%PYTHON%" -m unittest discover -s tests -v
if errorlevel 1 exit /b 1
"%PYTHON%" -m PyInstaller --noconfirm --clean --onefile --windowed --name CQUST_Wifi_v3_2 --icon assets\wifi.ico --add-data "assets/wifi.ico;assets" --add-data "assets/wifi.svg;assets" --add-data "assets/school_logo.png;assets" --collect-all customtkinter --collect-all keyring gui.py
if errorlevel 1 (
  echo.
  echo Build failed. Read the error above.
  exit /b 1
)
echo.
echo Build complete: dist\CQUST_Wifi_v3_2.exe
