# Load KEY=VALUE lines from repo-root .env into the current PowerShell session.
# Skips comments, blank lines, and non KEY=VALUE blocks (e.g. embedded YAML).

param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) ".env")
)

if (-not (Test-Path $EnvFile)) {
    Write-Warning ".env not found: $EnvFile"
    return
}

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    if ($line -notmatch '^[A-Za-z_][A-Za-z0-9_]*=') { return }
    $eq = $line.IndexOf('=')
    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    Set-Item -Path "env:$name" -Value $value
}

Write-Host "Loaded environment variables from $EnvFile"
