param(
    [string]$JMeterHome = $env:JMETER_HOME,
    [string]$HostName = "127.0.0.1",
    [int]$Port = 8080,
    [string]$Protocol = "http",
    [int]$GuideThreads = 10,
    [int]$LockThreads = 10,
    [int]$PaymentThreads = 10,
    [int]$RampSeconds = 30,
    [int]$Loops = 1
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PlanFile = Join-Path $ScriptDir "agent-group-load-test.jmx"
$CsvFile = Join-Path $ScriptDir "guide_questions.csv"
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$ResultDir = Join-Path $ScriptDir "results"
$ReportDir = Join-Path $ScriptDir "reports\agent-group-$Timestamp"
$JtlFile = Join-Path $ResultDir "agent-group-$Timestamp.jtl"
$LogFile = Join-Path $ResultDir "agent-group-$Timestamp.log"

New-Item -ItemType Directory -Force $ResultDir | Out-Null
New-Item -ItemType Directory -Force (Split-Path -Parent $ReportDir) | Out-Null

if ($JMeterHome) {
    $JMeterBin = Join-Path $JMeterHome "bin\jmeter.bat"
} else {
    $JMeterBin = "jmeter"
}

$JMeterArgs = @(
    "-n",
    "-j", $LogFile,
    "-t", $PlanFile,
    "-l", $JtlFile,
    "-e",
    "-o", $ReportDir,
    "-Jprotocol=$Protocol",
    "-Jhost=$HostName",
    "-Jport=$Port",
    "-Jcsv_file=$CsvFile",
    "-Jguide_threads=$GuideThreads",
    "-Jlock_threads=$LockThreads",
    "-Jpayment_threads=$PaymentThreads",
    "-Jramp_seconds=$RampSeconds",
    "-Jloops=$Loops"
)

& $JMeterBin @JMeterArgs

Write-Host "JMeter result: $JtlFile"
Write-Host "JMeter log: $LogFile"
Write-Host "HTML report: $ReportDir"
