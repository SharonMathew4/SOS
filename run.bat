@echo off
REM Auto-detect MySQL Connector/J jar and compile+run the app
cd /d "%~dp0"

for %%f in (mysql-connector-j-*.jar) do (
  set JAR=%%f
  goto :found
)

echo ERROR: mysql-connector-j-*.jar not found in %~dp0
Echo Place the MySQL connector jar in this folder and re-run.
exit /b 1

:found
if not exist out mkdir out

REM Clean any stale class files in the project root to avoid classpath confusion
del /q *.class >nul 2>&1

echo Compiling with classpath: .;%JAR%
javac -cp ".;%JAR%" -d out *.java
if errorlevel 1 exit /b 1

echo Running...
REM Put 'out' before '.' so newly compiled classes take precedence over any root-level class files
java -cp "out;.;%JAR%" Main
