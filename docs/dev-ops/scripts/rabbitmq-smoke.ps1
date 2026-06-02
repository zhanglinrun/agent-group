param(
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin_dev",
    [string]$MysqlHost = "127.0.0.1",
    [int]$MysqlPort = 13306,
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "agent_group_dev",
    [string]$MysqlDatabase = "agent_group",
    [string]$MysqlContainer = "agent-group-mysql",
    [string]$RabbitManagementUrl = "http://127.0.0.1:15672",
    [string]$RabbitUser = "agent_group",
    [string]$RabbitPassword = "agent_group_dev",
    [int]$TimeoutSeconds = 30,
    [string]$ReportDir = (Join-Path $PSScriptRoot "..\reports"),
    [switch]$UseLocalMysql
)

. "$PSScriptRoot\rabbitmq-common.ps1"

$reportDirPath = Ensure-ReportDirectory -ReportDir $ReportDir
$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$eventId = "RABBIT_SMOKE_$runId"
$orderId = "ORDER_RABBIT_SMOKE_$runId"
$bizId = "PAY_RABBIT_SMOKE_$runId"
$routingKey = "trade.event.pay.pay_success"
$headers = New-BasicAuthHeader -User $AdminUser -Password $AdminPassword

$insertSql = @"
insert into trade_event_outbox (
  event_id, order_id, biz_type, biz_id, event_type, routing_key,
  from_status, to_status, remark, send_count, send_status, create_time, update_time
) values (
  $(ConvertTo-SqlText $eventId),
  $(ConvertTo-SqlText $orderId),
  'PAY',
  $(ConvertTo-SqlText $bizId),
  'PAY_SUCCESS',
  $(ConvertTo-SqlText $routingKey),
  'WAIT_PAY',
  'SUCCESS',
  'rabbitmq smoke evidence',
  0,
  0,
  now(),
  now()
) on duplicate key update
  send_status = 0,
  send_count = 0,
  last_error = null,
  update_time = now();
"@

Invoke-AgentGroupMysql `
    -Sql $insertSql `
    -MysqlHost $MysqlHost `
    -MysqlPort $MysqlPort `
    -MysqlUser $MysqlUser `
    -MysqlPassword $MysqlPassword `
    -MysqlDatabase $MysqlDatabase `
    -MysqlContainer $MysqlContainer `
    -UseLocalMysql:$UseLocalMysql | Out-Null

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$dispatchResponses = @()
$outbox = $null
$consume = $null
do {
    $dispatchResponses += Invoke-RestMethod `
        -Method Post `
        -Uri "$BackendUrl/api/v1/trade/event/outbox/exec_job" `
        -Headers $headers `
        -TimeoutSec 20

    Start-Sleep -Milliseconds 500
    $outboxLine = Invoke-AgentGroupMysql `
        -Sql "select send_status, send_count, ifnull(last_error, ''), timestampdiff(microsecond, create_time, update_time) from trade_event_outbox where event_id = $(ConvertTo-SqlText $eventId);" `
        -MysqlHost $MysqlHost `
        -MysqlPort $MysqlPort `
        -MysqlUser $MysqlUser `
        -MysqlPassword $MysqlPassword `
        -MysqlDatabase $MysqlDatabase `
        -MysqlContainer $MysqlContainer `
        -UseLocalMysql:$UseLocalMysql
    $consumeLine = Invoke-AgentGroupMysql `
        -Sql "select consume_status, consume_count, ifnull(last_error, ''), timestampdiff(microsecond, create_time, update_time) from trade_event_consume_record where event_id = $(ConvertTo-SqlText $eventId);" `
        -MysqlHost $MysqlHost `
        -MysqlPort $MysqlPort `
        -MysqlUser $MysqlUser `
        -MysqlPassword $MysqlPassword `
        -MysqlDatabase $MysqlDatabase `
        -MysqlContainer $MysqlContainer `
        -UseLocalMysql:$UseLocalMysql

    $outbox = ConvertFrom-TabRow -Line ($outboxLine | Select-Object -First 1) -Columns @("sendStatus", "sendCount", "lastError", "outboxUseMicroseconds")
    $consume = ConvertFrom-TabRow -Line ($consumeLine | Select-Object -First 1) -Columns @("consumeStatus", "consumeCount", "lastError", "consumeUseMicroseconds")
} until (($outbox.sendStatus -eq "1" -and $consume.consumeStatus -eq "1") -or (Get-Date) -gt $deadline)

$queueSnapshots = Wait-RabbitQueuesDrained `
    -RabbitManagementUrl $RabbitManagementUrl `
    -RabbitUser $RabbitUser `
    -RabbitPassword $RabbitPassword

$passed = $outbox.sendStatus -eq "1" -and $consume.consumeStatus -eq "1"
$evidence = [ordered]@{
    case = "rabbitmq-smoke"
    result = $(if ($passed) { "PASS" } else { "FAIL" })
    runId = $runId
    eventId = $eventId
    orderId = $orderId
    routingKey = $routingKey
    backendUrl = $BackendUrl
    dispatchResponses = $dispatchResponses
    outbox = $outbox
    consume = $consume
    queues = $queueSnapshots
    createdAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
}

$markdown = @"
# RabbitMQ Smoke Evidence

Result: {0}

- eventId: {1}
- routingKey: {2}
- dispatchCalls: {3}
- outbox.sendStatus: {4}
- consume.consumeStatus: {5}
- outbox.lastError: {6}
- consume.lastError: {7}

CreatedAt: {8}
"@ -f $evidence['result'], $eventId, $routingKey, $dispatchResponses.Count,
    $outbox['sendStatus'], $consume['consumeStatus'], $outbox['lastError'],
    $consume['lastError'], $evidence['createdAt']

$name = "rabbitmq-smoke-$runId"
$paths = Write-EvidenceFiles -ReportDir $reportDirPath -Name $name -Evidence $evidence -Markdown $markdown
Write-Host "RabbitMQ smoke result: $($evidence['result'])"
Write-Host "JSON: $($paths.json)"
Write-Host "Markdown: $($paths.markdown)"

if (-not $passed) {
    exit 1
}
