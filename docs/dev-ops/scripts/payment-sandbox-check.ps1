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

$missingItems = @()
if ($null -ne $data -and $null -ne $data.officialSandboxMissingItems) {
    $missingItems = @($data.officialSandboxMissingItems)
}
$missingText = if ($missingItems.Count -gt 0) { $missingItems -join ", " } else { "-" }

$channelLines = @()
if ($null -ne $data -and $null -ne $data.channels) {
    foreach ($channel in @($data.channels)) {
        $channelMissingItems = @()
        if ($null -ne $channel.missingItems) {
            $channelMissingItems = @($channel.missingItems)
        }
        $channelMissingText = if ($channelMissingItems.Count -gt 0) { $channelMissingItems -join ", " } else { "-" }
        $notifyText = if ([string]::IsNullOrWhiteSpace([string]$channel.notifyUrl)) { "-" } else { [string]$channel.notifyUrl }
        $lastErrorText = if ([string]::IsNullOrWhiteSpace([string]$channel.lastError)) { "-" } else { [string]$channel.lastError }
        $channelLines += "- $($channel.payChannel): configured=$($channel.configured), sandboxMode=$($channel.sandboxMode), readyItems=$($channel.readyItemCount)/$($channel.requiredItemCount), notifyUrl=$notifyText, missingItems=$channelMissingText, lastError=$lastErrorText"
    }
}
if ($channelLines.Count -eq 0) {
    $channelLines += "- no channel status returned"
}
$channelMarkdown = $channelLines -join [Environment]::NewLine

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
- recommendedChannel: {4}
- sandboxEvidence: {5}
- officialSandboxMissingItems: {6}
- message: {7}

## Channels
{8}

CreatedAt: {9}
"@ -f $evidence['result'], $data.mockReady, $data.officialGatewayReady,
    $data.officialSandboxReady, $data.recommendedChannel, $data.sandboxEvidence,
    $missingText, $data.message, $channelMarkdown, $evidence['createdAt']

$name = "payment-sandbox-$runId"
$paths = Write-EvidenceFiles -ReportDir $reportDirPath -Name $name -Evidence $evidence -Markdown $markdown
Write-Host "Payment sandbox result: $($evidence['result'])"
Write-Host "JSON: $($paths.json)"
Write-Host "Markdown: $($paths.markdown)"

if (-not $passed) {
    exit 1
}
