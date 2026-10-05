param(
    [Parameter(Mandatory = $true)]
    [string]$Workspace,
    [Parameter(Mandatory = $true)]
    [string]$LaunchSpecDirectory,
    [int]$BasePort = 25576,
    [ValidateSet("BOTH", "RHT", "LHT")]
    [string]$Handedness = "BOTH"
)

$ErrorActionPreference = "Stop"
$workspacePath = [System.IO.Path]::GetFullPath($Workspace)
$buildPath = [System.IO.Path]::GetFullPath(
    (Join-Path $workspacePath "build")
)
$matrixRunRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $buildPath "run\live-two-client")
)
$reportRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $buildPath "reports\roadbuilder-live-two-client")
)
if (-not $matrixRunRoot.StartsWith(
    $buildPath + [System.IO.Path]::DirectorySeparatorChar,
    [System.StringComparison]::OrdinalIgnoreCase
)) {
    throw "Matrix run root escaped the workspace build directory."
}
if (-not $reportRoot.StartsWith(
    $buildPath + [System.IO.Path]::DirectorySeparatorChar,
    [System.StringComparison]::OrdinalIgnoreCase
)) {
    throw "Matrix report root escaped the workspace build directory."
}

$launchSpecPath = [System.IO.Path]::GetFullPath($LaunchSpecDirectory)

function Wait-ForFile {
    param(
        [string]$Path,
        [System.Diagnostics.Process]$Process,
        [int]$TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $Path) {
            return
        }
        if ($Process.HasExited) {
            throw "Process $($Process.Id) exited with code $($Process.ExitCode) before $Path appeared."
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Timed out waiting for $Path."
}

function Wait-ForProcesses {
    param(
        [System.Diagnostics.Process[]]$Processes,
        [int]$TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (($Processes | Where-Object { -not $_.HasExited }).Count -eq 0) {
            foreach ($process in $Processes) {
                $process.WaitForExit()
                $process.Refresh()
            }
            return
        }
        Start-Sleep -Milliseconds 500
    }
    foreach ($process in $Processes) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }
    }
    throw "Timed out waiting for matrix processes."
}

function Start-MatrixProcess {
    param(
        [string]$Role,
        [string]$Phase,
        [string]$Handedness,
        [int]$Port,
        [string]$EvidenceDirectory,
        [string]$RunDirectory,
        [string]$LogPrefix
    )
    New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null
    $metadata = Get-Content -LiteralPath (
        Join-Path $launchSpecPath "$Role.json"
    ) -Raw | ConvertFrom-Json
    $argumentTemplate = [System.IO.File]::ReadAllText(
        $metadata.argument_file
    )
    $escapedEvidence = $EvidenceDirectory.Replace("\", "\\")
    $arguments = $argumentTemplate.Replace(
        "__PHASE__",
        $Phase
    ).Replace(
        "__HANDEDNESS__",
        $Handedness
    ).Replace(
        "__PORT__",
        $Port.ToString()
    ).Replace(
        "__EVIDENCE_DIR__",
        $escapedEvidence
    )
    $argumentFile = "$LogPrefix.args"
    [System.IO.File]::WriteAllText(
        $argumentFile,
        $arguments,
        [System.Text.UTF8Encoding]::new($false)
    )
    $stdout = "$LogPrefix.out.log"
    $stderr = "$LogPrefix.err.log"
    return Start-Process `
        -FilePath $metadata.executable `
        -ArgumentList @("@$argumentFile") `
        -WorkingDirectory $RunDirectory `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru
}

function Write-ServerConfiguration {
    param(
        [string]$ServerRunDirectory,
        [string]$Handedness,
        [int]$Port,
        [string]$RunId
    )
    New-Item -ItemType Directory -Force -Path $ServerRunDirectory | Out-Null
    New-Item -ItemType Directory -Force -Path (
        Join-Path $ServerRunDirectory "config"
    ) | Out-Null
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText(
        (Join-Path $ServerRunDirectory "eula.txt"),
        "eula=true`n",
        $utf8NoBom
    )
    $serverProperties = @(
        "allow-flight=true"
        "enable-command-block=false"
        "gamemode=creative"
        "generate-structures=false"
        "level-name=matrix-world-$RunId"
        "level-type=minecraft:flat"
        "max-players=4"
        "max-tick-time=-1"
        "motd=Road Builder live matrix"
        "online-mode=false"
        "server-port=$Port"
        "simulation-distance=8"
        "spawn-protection=0"
        "sync-chunk-writes=true"
        "view-distance=8"
    ) -join "`n"
    [System.IO.File]::WriteAllText(
        (Join-Path $ServerRunDirectory "server.properties"),
        $serverProperties + "`n",
        $utf8NoBom
    )
    $policyJson = @{
        schema_version = 1
        maximum_distance = 128.0
        maximum_affected_blocks = 32768
        maximum_touched_chunks = 64
        maximum_planning_candidates = 262144
        maximum_grade = 0.5
        transitions_per_tick = 64
        force_restore_permission_level = 2
        traffic_handedness = $Handedness
        durable_journal_required = $true
    } | ConvertTo-Json
    [System.IO.File]::WriteAllText(
        (
            Join-Path $ServerRunDirectory `
                "config\roadbuilder-server.json"
        ),
        $policyJson + "`n",
        $utf8NoBom
    )
}

function Assert-Evidence {
    param(
        [string]$EvidenceDirectory,
        [string]$Phase,
        [string[]]$Names
    )
    $failures = Get-ChildItem -LiteralPath $EvidenceDirectory `
        -Filter "$Phase-failure-*.txt" `
        -ErrorAction SilentlyContinue
    if ($failures) {
        $details = $failures | ForEach-Object {
            "$($_.Name): $(Get-Content -LiteralPath $_.FullName -Raw)"
        }
        throw ($details -join [Environment]::NewLine)
    }
    foreach ($name in $Names) {
        $path = Join-Path $EvidenceDirectory "$Phase-$name.txt"
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Required matrix evidence is missing: $path"
        }
    }
}

function Invoke-Phase {
    param(
        [string]$Handedness,
        [string]$Phase,
        [int]$Port,
        [string]$ServerRunDirectory,
        [string]$EvidenceDirectory,
        [string]$RunId
    )
    Get-ChildItem -LiteralPath $EvidenceDirectory `
        -Filter "$Phase-*.txt" `
        -ErrorAction SilentlyContinue |
        Remove-Item -Force
    $logDir = Join-Path $EvidenceDirectory "logs-$RunId"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $server = Start-MatrixProcess `
        -Role "server" `
        -Phase $Phase `
        -Handedness $Handedness `
        -Port $Port `
        -EvidenceDirectory $EvidenceDirectory `
        -RunDirectory $ServerRunDirectory `
        -LogPrefix (Join-Path $logDir "$Phase-server")
    Wait-ForFile `
        -Path (Join-Path $EvidenceDirectory "$Phase-server-ready.txt") `
        -Process $server `
        -TimeoutSeconds 180
    $observer = Start-MatrixProcess `
        -Role "observer" `
        -Phase $Phase `
        -Handedness $Handedness `
        -Port $Port `
        -EvidenceDirectory $EvidenceDirectory `
        -RunDirectory (
            Join-Path $matrixRunRoot "$Handedness\$Phase-observer"
        ) `
        -LogPrefix (Join-Path $logDir "$Phase-observer")
    $actor = $null
    try {
        Wait-ForFile `
            -Path (
                Join-Path $EvidenceDirectory `
                    "$Phase-client-ready-observer.txt"
            ) `
            -Process $observer `
            -TimeoutSeconds 180
        $actor = Start-MatrixProcess `
            -Role "actor" `
            -Phase $Phase `
            -Handedness $Handedness `
            -Port $Port `
            -EvidenceDirectory $EvidenceDirectory `
            -RunDirectory (
                Join-Path $matrixRunRoot "$Handedness\$Phase-actor"
            ) `
            -LogPrefix (Join-Path $logDir "$Phase-actor")
        Wait-ForProcesses -Processes @($actor, $observer) -TimeoutSeconds 360
        Wait-ForProcesses -Processes @($server) -TimeoutSeconds 120
    } catch {
        foreach ($process in @($actor, $observer, $server)) {
            if ($process -and -not $process.HasExited) {
                Stop-Process -Id $process.Id -Force
            }
        }
        throw
    }
    foreach ($process in @($actor, $observer, $server)) {
        if ($null -ne $process.ExitCode -and $process.ExitCode -ne 0) {
            throw "Matrix process $($process.Id) exited with code $($process.ExitCode). See $logDir."
        }
    }
}

New-Item -ItemType Directory -Force -Path $matrixRunRoot | Out-Null
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$runId = "{0}-{1}" -f (
    [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")
), $PID
$rows = @()
$handednessValues = if ($Handedness -eq "BOTH") {
    @("RHT", "LHT")
} else {
    @($Handedness)
}
foreach ($handedness in $handednessValues) {
    $port = $BasePort + $rows.Count
    $serverRunDirectory = Join-Path $matrixRunRoot "$handedness\server"
    $evidenceDirectory = Join-Path $reportRoot $handedness
    New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
    Get-ChildItem -LiteralPath $evidenceDirectory `
        -Filter "*.txt" `
        -ErrorAction SilentlyContinue |
        Remove-Item -Force
    Write-ServerConfiguration `
        -ServerRunDirectory $serverRunDirectory `
        -Handedness $handedness `
        -Port $port `
        -RunId $runId

    Invoke-Phase `
        -Handedness $handedness `
        -Phase "place" `
        -Port $port `
        -ServerRunDirectory $serverRunDirectory `
        -EvidenceDirectory $evidenceDirectory `
        -RunId $runId
    Assert-Evidence `
        -EvidenceDirectory $evidenceDirectory `
        -Phase "place" `
        -Names @(
            "channel-negotiation",
            "creative-survival-authority",
            "tool-straight",
            "tool-simple_curve",
            "tool-complex_curve",
            "tool-freehand",
            "exact-replay",
            "job-cancellation",
            "observer-reconnect",
            "observer-delta",
            "actor-complete",
            "observer-complete",
            "server-complete",
            "server-stopped"
        )

    Invoke-Phase `
        -Handedness $handedness `
        -Phase "reload" `
        -Port $port `
        -ServerRunDirectory $serverRunDirectory `
        -EvidenceDirectory $evidenceDirectory `
        -RunId $runId
    Assert-Evidence `
        -EvidenceDirectory $evidenceDirectory `
        -Phase "reload" `
        -Names @(
            "save-reload",
            "dimension-nether-observed",
            "dimension-round-trip",
            "graph-demolition",
            "observer-reload-snapshot",
            "observer-demolition-delta",
            "actor-complete",
            "observer-complete",
            "server-complete",
            "server-stopped"
        )

    $rows += @{
        handedness = $handedness
        status = "passed"
        dedicated_server_processes = 2
        real_client_processes_per_phase = 2
        phases = @("place", "reload")
        tools = @(
            "straight",
            "simple_curve",
            "complex_curve",
            "freehand"
        )
        checks = @(
            "channel_negotiation",
            "creative_survival_authority",
            "preview_server_block_agreement",
            "freehand_64_point_cap",
            "exact_request_replay",
            "observer_delta",
            "disconnect_reconnect",
            "dimension_round_trip",
            "full_snapshot_resync",
            "incremental_job_cancellation",
            "save_reload",
            "graph_backed_demolition"
        )
    }
}

$matrixReport = @{
    format_version = 1
    generated_at_utc = [DateTime]::UtcNow.ToString("o")
    run_id = $runId
    status = "passed"
    client_count = 2
    rows = $rows
}
$matrixReport | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath (
        Join-Path $reportRoot "matrix.json"
    ) -Encoding utf8
