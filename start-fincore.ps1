$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    throw ".env file not found"
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*(DB_USERNAME|DB_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or
    [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw "Database credentials missing"
}

Write-Host "Database configuration loaded."
Write-Host "Starting FinCore..."

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
