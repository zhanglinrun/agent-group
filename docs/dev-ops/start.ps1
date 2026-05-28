param(
  [switch]$SkipPackage
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$DevOps = Join-Path $Root "docs\dev-ops"
$LocalEnv = Join-Path $Root ".env.local"

if (Test-Path $LocalEnv) {
  foreach ($RawLine in Get-Content $LocalEnv) {
    $Line = $RawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($Line) -or $Line.StartsWith("#")) {
      continue
    }
    $Index = $Line.IndexOf("=")
    if ($Index -le 0) {
      continue
    }
    $Name = $Line.Substring(0, $Index).Trim()
    $Value = $Line.Substring($Index + 1).Trim()
    if (($Value.StartsWith('"') -and $Value.EndsWith('"')) -or ($Value.StartsWith("'") -and $Value.EndsWith("'"))) {
      $Value = $Value.Substring(1, $Value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
  }
}

if (-not $SkipPackage) {
  Push-Location (Join-Path $Root "backend")
  mvn -pl agent-group-app -am clean package -DskipTests
  Pop-Location
}

Push-Location $DevOps
docker compose -f docker-compose-environment.yml up -d
docker compose -f docker-compose-app.yml up -d --build
Pop-Location

Write-Host "agent-group started"
Write-Host "backend: http://localhost:8080"
Write-Host "nginx:   http://localhost:18080"
