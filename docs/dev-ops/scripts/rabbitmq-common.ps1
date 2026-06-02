Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-BasicAuthHeader {
    param(
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $pair = "{0}:{1}" -f $User, $Password
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    return @{ Authorization = "Basic $token" }
}

function ConvertTo-SqlText {
    param([AllowNull()][string]$Value)

    if ($null -eq $Value) {
        return "null"
    }
    return "'" + $Value.Replace("'", "''") + "'"
}

function Ensure-ReportDirectory {
    param([Parameter(Mandatory = $true)][string]$ReportDir)

    if (-not (Test-Path $ReportDir)) {
        New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    }
    return (Resolve-Path $ReportDir).Path
}

function Invoke-AgentGroupMysql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$MysqlHost = "127.0.0.1",
        [int]$MysqlPort = 13306,
        [string]$MysqlUser = "root",
        [string]$MysqlPassword = "agent_group_dev",
        [string]$MysqlDatabase = "agent_group",
        [string]$MysqlContainer = "agent-group-mysql",
        [switch]$UseLocalMysql
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($UseLocalMysql) {
            $output = & mysql `
                "--host=$MysqlHost" `
                "--port=$MysqlPort" `
                "--user=$MysqlUser" `
                "--password=$MysqlPassword" `
                "--database=$MysqlDatabase" `
                "--default-character-set=utf8mb4" `
                "--batch" `
                "--raw" `
                "--skip-column-names" `
                "--execute=$Sql" 2>&1
        } else {
            $output = & docker exec -i $MysqlContainer mysql `
                "--user=$MysqlUser" `
                "--password=$MysqlPassword" `
                "--database=$MysqlDatabase" `
                "--default-character-set=utf8mb4" `
                "--batch" `
                "--raw" `
                "--skip-column-names" `
                "--execute=$Sql" 2>&1
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($LASTEXITCODE -ne 0) {
        throw "mysql command failed: $output"
    }
    return @($output) | Where-Object { $_ -notmatch "^mysql: \[Warning\]" }
}

function ConvertFrom-TabRow {
    param(
        [AllowNull()][string]$Line,
        [Parameter(Mandatory = $true)][string[]]$Columns
    )

    $result = [ordered]@{}
    foreach ($column in $Columns) {
        $result[$column] = $null
    }
    if ([string]::IsNullOrWhiteSpace($Line)) {
        return $result
    }

    $parts = $Line -split "`t", $Columns.Count
    for ($i = 0; $i -lt $Columns.Count; $i++) {
        if ($i -lt $parts.Count) {
            $result[$Columns[$i]] = $parts[$i]
        }
    }
    return $result
}

function Get-RabbitQueueSnapshot {
    param(
        [string]$RabbitManagementUrl = "http://127.0.0.1:15672",
        [string]$RabbitUser = "agent_group",
        [string]$RabbitPassword = "agent_group_dev",
        [string[]]$QueueNames = @(
            "agent.group.trade.event.queue",
            "agent.group.trade.event.team-success.queue",
            "agent.group.trade.event.refund-success.queue",
            "agent.group.trade.event.dlq"
        )
    )

    $headers = New-BasicAuthHeader -User $RabbitUser -Password $RabbitPassword
    $vhost = [uri]::EscapeDataString("/")
    $snapshots = @()
    foreach ($queueName in $QueueNames) {
        $encodedQueue = [uri]::EscapeDataString($queueName)
        $uri = "$RabbitManagementUrl/api/queues/$vhost/$encodedQueue"
        try {
            $queue = Invoke-RestMethod -Method Get -Uri $uri -Headers $headers -TimeoutSec 5
            $snapshots += [ordered]@{
                name = $queue.name
                messages = $queue.messages
                ready = $queue.messages_ready
                unacked = $queue.messages_unacknowledged
                consumers = $queue.consumers
            }
        } catch {
            $snapshots += [ordered]@{
                name = $queueName
                error = $_.Exception.Message
            }
        }
    }
    return $snapshots
}

function Wait-RabbitQueuesDrained {
    param(
        [string]$RabbitManagementUrl = "http://127.0.0.1:15672",
        [string]$RabbitUser = "agent_group",
        [string]$RabbitPassword = "agent_group_dev",
        [int]$TimeoutSeconds = 10
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $snapshots = @()
    do {
        $snapshots = Get-RabbitQueueSnapshot `
            -RabbitManagementUrl $RabbitManagementUrl `
            -RabbitUser $RabbitUser `
            -RabbitPassword $RabbitPassword
        $busy = @($snapshots | Where-Object {
                if ($_.Contains("error")) {
                    return $true
                }
                $messages = if ($null -eq $_.messages) { 0 } else { [int]$_.messages }
                $ready = if ($null -eq $_.ready) { 0 } else { [int]$_.ready }
                $unacked = if ($null -eq $_.unacked) { 0 } else { [int]$_.unacked }
                return $messages -gt 0 -or $ready -gt 0 -or $unacked -gt 0
            }).Count -gt 0
        if (-not $busy) {
            return $snapshots
        }
        Start-Sleep -Milliseconds 500
    } until ((Get-Date) -gt $deadline)
    return $snapshots
}

function Write-EvidenceFiles {
    param(
        [Parameter(Mandatory = $true)][string]$ReportDir,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)]$Evidence,
        [Parameter(Mandatory = $true)][string]$Markdown
    )

    $jsonPath = Join-Path $ReportDir "$Name.json"
    $mdPath = Join-Path $ReportDir "$Name.md"
    $Evidence | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 -Path $jsonPath
    $Markdown | Set-Content -Encoding UTF8 -Path $mdPath
    return [ordered]@{
        json = $jsonPath
        markdown = $mdPath
    }
}
