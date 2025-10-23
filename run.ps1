# Auto-detect MySQL Connector/J jar and compile+run the app

# Go to the script's directory
Set-Location -Path $PSScriptRoot

# Find the connector jar in current directory
$jar = Get-ChildItem -Filter "mysql-connector-j-*.jar" | Select-Object -First 1
if (-not $jar) {
  Write-Host "ERROR: mysql-connector-j-*.jar not found in $PSScriptRoot" -ForegroundColor Red
  Write-Host "Place the MySQL connector jar in this folder and re-run."
  exit 1
}

# Ensure out directory exists
if (-not (Test-Path -Path "out")) { New-Item -ItemType Directory -Path "out" | Out-Null }

# Clean any stale class files in the project root to avoid classpath confusion
Get-ChildItem -Filter "*.class" | Remove-Item -ErrorAction SilentlyContinue

# Compile
$cp = ".;{0}" -f $jar.Name
Write-Host "Compiling with classpath: $cp"
javac -cp $cp -d out *.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Run (ensure 'out' precedes '.' so latest compiled classes are used)
Write-Host "Running..." -ForegroundColor Cyan
$runtimeCp = "out;.;{0}" -f $jar.Name
java -cp $runtimeCp Main
