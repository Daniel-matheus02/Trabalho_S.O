@echo off
setlocal
rem Executa o cliente desktop com o wrapper do Gradle.
if "%JAVA_HOME%"=="" (
  echo JAVA_HOME nao esta definido. Instale o JDK 21 e defina JAVA_HOME.
  exit /b 1
)
call "%~dp0gradlew.bat" run
endlocal
