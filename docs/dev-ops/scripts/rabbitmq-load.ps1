param(
    [int]$Count = 200,
    [int]$InsertBatchSize = 100,
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
    [int]$TimeoutSeconds = 90,
    [int]$PollIntervalMilliseconds = 500,
    [string]$ReportDir = (Join-Path $PSScriptRoot "..\reports"),
    [switch]$UseLocalMysql
)

. "$PSScriptRoot\rabbitmq-common.ps1"

if ($Count -lt 1) {
    throw "Count must be greater than 0."
}
if ($InsertBatchSize -lt 1) {
    throw "InsertBatchSize must be greater than 0."
}

$reportDirPath = Ensure-ReportDirectory -ReportDir $ReportDir
$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$eventPrefix = "RABBIT_LOAD_$runId"
$headers = New-BasicAuthHeader -User $AdminUser -Password $AdminPassword

for ($offset = 0; $offset -lt $Count; $offset += $InsertBatchSize) {
    $end = [Math]::Min($offset + $InsertBatchSize, $Count)
    $values = @()
    for ($i = $offset; $i -lt $end; $i++) {
        $seq = ($i + 1).ToString("000000")
        $eventId = "${eventPrefix}_$seq"
        $orderId = "ORD_RMQ_LOAD_${runId}_$seq"
        $bizId = "PAY_RMQ_LOAD_${runId}_$seq"
        $values += "($(ConvertTo-SqlText $eventId), $(ConvertTo-SqlText $orderId), 'PAY', $(ConvertTo-SqlText $bizId), 'PAY_SUCCESS', 'trade.event.pay.pay_success', 'WAIT_PAY', 'SUCCESS', 'rabbitmq load evidence', 0, 0, now(), now())"
    }

    $insertSql = @"
insert into trade_event_outbox (
  event_id, order_id, biz_type, biz_id, event_type, routing_key,
  from_status, to_status, remark, send_count, send_status, create_time, update_time
) values
  $($values -join ",`n  ")
on duplicate key update
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
}

$dispatchResponses = @()
$statusSamples = @()
$startedAt = Get-Date
$deadline = $startedAt.AddSeconds($TimeoutSeconds)
$outboxStats = $null
$consumeStats = $null
$latencyStats = $null
$dispatchCalls = 0

do {
    $dispatchCalls++
    $dispatchResponses += Invoke-RestMethod `
        -Method Post `
        -Uri "$BackendUrl/api/v1/trade/event/outbox/exec_job" `
        -Headers $headers `
        -TimeoutSec 30

    Start-Sleep -Milliseconds $PollIntervalMilliseconds

    $prefixSql = ConvertTo-SqlText ($eventPrefix + "_%")
    $outboxLine = Invoke-AgentGroupMysql `
        -Sql "select count(1), sum(case when send_status = 1 then 1 else 0 end), sum(case when send_status in (0, 2, 4) then 1 else 0 end), sum(case when send_status = 3 then 1 else 0 end) from trade_event_outbox where event_id like $prefixSql;" `
        -MysqlHost $MysqlHost `
        -MysqlPort $MysqlPort `
        -MysqlUser $MysqlUser `
        -MysqlPassword $MysqlPassword `
        -MysqlDatabase $MysqlDatabase `
        -MysqlContainer $MysqlContainer `
        -UseLocalMysql:$UseLocalMysql
    $consumeLine = Invoke-AgentGroupMysql `
        -Sql "select count(1), sum(case when consume_status = 1 then 1 else 0 end), sum(case when consume_status in (0, 2, 4) then 1 else 0 end), sum(case when consume_status = 3 then 1 else 0 end) from trade_event_consume_record where event_id like $prefixSql;" `
        -MysqlHost $MysqlHost `
        -MysqlPort $MysqlPort `
        -MysqlUser $MysqlUser `
        -MysqlPassword $MysqlPassword `
        -MysqlDatabase $MysqlDatabase `
        -MysqlContainer $MysqlContainer `
        -UseLocalMysql:$UseLocalMysql
    $latencyLine = Invoke-AgentGroupMysql `
        -Sql "select ifnull(round(avg(timestampdiff(microsecond, o.create_time, c.update_time)) / 1000, 2), 0), ifnull(round(max(timestampdiff(microsecond, o.create_time, c.update_time)) / 1000, 2), 0) from trade_event_outbox o join trade_event_consume_record c on o.event_id = c.event_id where o.event_id like $prefixSql and c.consume_status = 1;" `
        -MysqlHost $MysqlHost `
        -MysqlPort $MysqlPort `
        -MysqlUser $MysqlUser `
        -MysqlPassword $MysqlPassword `
        -MysqlDatabase $MysqlDatabase `
        -MysqlContainer $MysqlContainer `
        -UseLocalMysql:$UseLocalMysql

    $outboxStats = ConvertFrom-TabRow -Line ($outboxLine | Select-Object -First 1) -Columns @("total", "success", "pending", "dead")
    $consumeStats = ConvertFrom-TabRow -Line ($consumeLine | Select-Object -First 1) -Columns @("total", "success", "pending", "dead")
    $latencyStats = ConvertFrom-TabRow -Line ($latencyLine | Select-Object -First 1) -Columns @("avgMs", "maxMs")
    $statusSamples += [ordered]@{
        at = (Get-Date).ToString("HH:mm:ss.fff")
        outbox = $outboxStats
        consume = $consumeStats
        latency = $latencyStats
    }
} until (([int]$consumeStats.success -ge $Count) -or (Get-Date) -gt $deadline)

$finishedAt = Get-Date
$elapsedSeconds = [Math]::Max(0.001, ($finishedAt - $startedAt).TotalSeconds)
$successCount = [int]$consumeStats.success
$throughput = [Math]::Round($successCount / $elapsedSeconds, 2)
$passed = $successCount -ge $Count -and [int]$outboxStats.dead -eq 0 -and [int]$consumeStats.dead -eq 0
$queueSnapshots = Wait-RabbitQueuesDrained `
    -RabbitManagementUrl $RabbitManagementUrl `
    -RabbitUser $RabbitUser `
    -RabbitPassword $RabbitPassword

$evidence = [ordered]@{
    case = "rabbitmq-load"
    result = $(if ($passed) { "PASS" } else { "FAIL" })
    runId = $runId
    eventPrefix = $eventPrefix
    count = $Count
    dispatchCalls = $dispatchCalls
    elapsedSeconds = [Math]::Round($elapsedSeconds, 3)
    throughputPerSecond = $throughput
    outbox = $outboxStats
    consume = $consumeStats
    latency = $latencyStats
    queues = $queueSnapshots
    dispatchResponses = $dispatchResponses
    samples = $statusSamples
    createdAt = $finishedAt.ToString("yyyy-MM-dd HH:mm:ss")
}

$markdown = @"
# RabbitMQ Load Evidence

Result: {0}

- runId: {1}
- insertedEvents: {2}
- consumedSuccess: {3}
- deadLetters: outbox={4}, consume={5}
- elapsedSeconds: {6}
- throughputPerSecond: {7}
- avgLatencyMs: {8}
- maxLatencyMs: {9}

CreatedAt: {10}
"@ -f $evidence['result'], $runId, $Count, $consumeStats['success'],
    $outboxStats['dead'], $consumeStats['dead'], $evidence['elapsedSeconds'],
    $evidence['throughputPerSecond'], $latencyStats['avgMs'],
    $latencyStats['maxMs'], $evidence['createdAt']

$name = "rabbitmq-load-$runId"
$paths = Write-EvidenceFiles -ReportDir $reportDirPath -Name $name -Evidence $evidence -Markdown $markdown
Write-Host "RabbitMQ load result: $($evidence['result'])"
Write-Host "Throughput: $($evidence['throughputPerSecond'])/s"
Write-Host "JSON: $($paths.json)"
Write-Host "Markdown: $($paths.markdown)"

if (-not $passed) {
    exit 1
}
