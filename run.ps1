# run.ps1 - one-step launcher for ticket-triage on this machine.
# Sets up Java/Maven (which aren't on PATH here), builds the jar if needed, and runs it.

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

# --- Locate the JDK -------------------------------------------------------
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
if (-not (Test-Path $jdk)) {
    # Fall back to any Temurin 17 JDK if the exact patch version differs.
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk -or -not (Test-Path $jdk)) {
    Write-Host "ERROR: No JDK 17 found under C:\Program Files\Eclipse Adoptium." -ForegroundColor Red
    Write-Host "Install one with:  winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Yellow
    exit 1
}

$maven = "$env:USERPROFILE\tools\apache-maven-3.9.9\bin"

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$maven;$env:Path"

Write-Host "Using JDK : $jdk" -ForegroundColor DarkGray

# --- Build the jar if it's missing ---------------------------------------
$jar = Get-ChildItem "target\ticket-triage-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "No jar found - building (first build downloads dependencies, give it a minute)..." -ForegroundColor Cyan
    mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }
    $jar = Get-ChildItem "target\ticket-triage-*.jar" | Select-Object -First 1
}

# --- Run ------------------------------------------------------------------
Write-Host ""
Write-Host "Starting ticket-triage ..." -ForegroundColor Green
Write-Host "  Swagger UI : http://localhost:8080/swagger-ui.html   (Authorize with key: local-dev-key)" -ForegroundColor Green
Write-Host "  Health     : http://localhost:8080/actuator/health" -ForegroundColor Green
Write-Host "  H2 console : http://localhost:8080/h2-console" -ForegroundColor Green
Write-Host "  Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host ""

java -jar $jar.FullName
