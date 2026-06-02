param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin_dev",
    [string]$ReportDir = (Join-Path $PSScriptRoot "..\reports"),
    [switch]$RequireOfficialSandbox
)

. "$PSScriptRoot\rabbitmq-common.ps1"

$reportDirPath = Ensure-ReportDirectory -ReportDir $ReportDir
$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$headers = New-BasicAuthHeader -User $AdminUser -Password $AdminPassword

$response = Invoke-RestMethod `
    -Method Get `
    -Uri "$BackendUrl/api/v1/payment/gateway/status" `
    -Headers $headers `
    -TimeoutSec 20

$data = $response.data
$passed = $null -ne $data
if ($RequireOfficialSandbox) {
    $passed = $passed -and [bool]$data.officialSandboxReady
}

$evidence = [ordered]@{
    case = "payment-sandbox-check"
    result = $(if ($passed) { "PASS" } else { "FAIL" })
    runId = $runId
    requireOfficialSandbox = [bool]$RequireOfficialSandbox
    status = $data
    createdAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
}

$markdown = @"
# Payment Sandbox Check

Result: {0}

- mockReady: {1}
- officialGatewayReady: {2}
- officialSandboxReady: {3}
- message: {4}

CreatedAt: {5}
"@ -f $evidence['result'], $data.mockReady, $data.officialGatewayReady,
    $data.officialSandboxReady, $data.message, $evidence['createdAt']

$name = "payment-sandbox-$runId"
$paths = Write-EvidenceFiles -ReportDir $reportDirPath -Name $name -Evidence $evidence -Markdown $markdown
Write-Host "Payment sandbox result: $($evidence['result'])"
Write-Host "JSON: $($paths.json)"
Write-Host "Markdown: $($paths.markdown)"

if (-not $passed) {
    exit 1
}
