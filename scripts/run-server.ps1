# Builds and runs the .NET MCP search server standalone (stdio).
# Useful for driving it with scripts/test-mcp.ps1 (no LLM, no UI).
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-server.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$dotnetRoot = if ($env:DOTNET_ROOT) { $env:DOTNET_ROOT } else { Join-Path $root 'tools\dotnet' }
$dotnet = Join-Path $dotnetRoot 'dotnet.exe'
if (-not (Test-Path $dotnet)) { $dotnet = 'dotnet' }  # fall back to PATH

$env:DOTNET_ROOT = $dotnetRoot
$env:PATH = "$dotnetRoot;$env:PATH"

Push-Location (Join-Path $root 'mcp-server')
try {
    & $dotnet build -c Debug -f net8.0-windows --nologo
    if ($LASTEXITCODE -ne 0) { throw "build failed ($LASTEXITCODE)" }
    Write-Host "Starting MCP server (stdio). Ctrl+C to stop." -ForegroundColor Cyan
    & $dotnet run -c Debug -f net8.0-windows --no-build
} finally {
    Pop-Location
}
