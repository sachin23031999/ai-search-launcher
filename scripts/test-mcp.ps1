# Manual MCP smoke test: drives the server over stdio with raw JSON-RPC (no LLM, no UI).
# Reads stdout asynchronously so no responses are lost.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\test-mcp.ps1 [-SettingsQuery ..] [-FilesQuery ..]
param(
    [string]$SettingsQuery = "turn off bluetooth",
    [string]$FilesQuery = "budget"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "mcp-server\bin\Debug\net8.0-windows\McpServer.exe"
if (-not (Test-Path $exe)) {
    Write-Error "Server exe not found at $exe. Build it first: dotnet build mcp-server"
    exit 1
}

$messages = @(
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'
    '{"jsonrpc":"2.0","method":"notifications/initialized"}'
    '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
    ('{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"settings_search","arguments":{"query":' + (ConvertTo-Json $SettingsQuery) + '}}}')
    ('{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"files_search","arguments":{"query":' + (ConvertTo-Json $FilesQuery) + ',"limit":5}}}')
)

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $exe
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false

$localDotnet = Join-Path $root "tools\dotnet"
if (Test-Path (Join-Path $localDotnet "dotnet.exe")) {
    $psi.Environment["DOTNET_ROOT"] = $localDotnet
    $psi.Environment["PATH"] = $localDotnet + ";" + $env:PATH
}

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi

$outLines = [System.Collections.ArrayList]::Synchronized([System.Collections.ArrayList]::new())
$errLines = [System.Collections.ArrayList]::Synchronized([System.Collections.ArrayList]::new())
$onOut = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -MessageData $outLines -Action {
    if ($EventArgs.Data) { $Event.MessageData.Add($EventArgs.Data) | Out-Null }
}
$onErr = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -MessageData $errLines -Action {
    if ($EventArgs.Data) { $Event.MessageData.Add($EventArgs.Data) | Out-Null }
}

$proc.Start() | Out-Null
$proc.BeginOutputReadLine()
$proc.BeginErrorReadLine()

foreach ($m in $messages) {
    $proc.StandardInput.WriteLine($m)
    $proc.StandardInput.Flush()
    Start-Sleep -Milliseconds 700
}
# Wait for the last (possibly slow) query to produce output before shutting down.
Start-Sleep -Seconds 4
$proc.StandardInput.Close()
if (-not $proc.WaitForExit(6000)) { $proc.Kill() | Out-Null }
Start-Sleep -Milliseconds 300

Unregister-Event -SourceIdentifier $onOut.Name
Unregister-Event -SourceIdentifier $onErr.Name

Write-Host "===== STDOUT (JSON-RPC responses) =====" -ForegroundColor Cyan
foreach ($line in $outLines) {
    if (-not $line.Trim()) { continue }
    try {
        $obj = $line | ConvertFrom-Json
        Write-Host ("--- id=" + $obj.id + " ---") -ForegroundColor Yellow
        Write-Output ($obj | ConvertTo-Json -Depth 12 -Compress)
    } catch {
        Write-Output $line
    }
}

if ($errLines.Count -gt 0) {
    Write-Host "===== STDERR (logs) =====" -ForegroundColor DarkGray
    $errLines | ForEach-Object { Write-Output $_ }
}
