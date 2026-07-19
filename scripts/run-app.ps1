# Builds and runs the Compose Multiplatform AI Search Launcher.
# The app spawns and supervises the .NET MCP server itself (stdio child process).
#
# Set an LLM key to use the real Koog orchestrator; otherwise the app runs the mock.
# Provide the key either via a .env file at the repo root (see .env.example) or by
# exporting the vars before running:
#   $env:LLM_PROVIDER = 'openai'      # openai | anthropic | google
#   $env:LLM_API_KEY  = 'sk-...'
#   $env:LLM_MODEL    = 'gpt-4o'      # optional; provider default otherwise
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

# Load key=value pairs from .env (repo root) into the process env, unless already set.
# Existing environment variables take precedence over the file.
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    Write-Host "Loading environment from $envFile" -ForegroundColor Cyan
    foreach ($line in Get-Content $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $eq = $trimmed.IndexOf('=')
        if ($eq -lt 1) { continue }
        $name = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim().Trim('"').Trim("'")
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

# Toolchain (self-contained under tools\, no system installs required).
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.7_6'
}
$dotnetRoot = if ($env:DOTNET_ROOT) { $env:DOTNET_ROOT } else { Join-Path $root 'tools\dotnet' }
$env:DOTNET_ROOT = $dotnetRoot
$env:PATH = "$env:JAVA_HOME\bin;$dotnetRoot;$env:PATH"

$gradle = Join-Path $root 'tools\gradle-8.10.2\bin\gradle.bat'
if (-not (Test-Path $gradle)) { $gradle = 'gradle' }  # fall back to PATH

# Ensure the MCP server is built so the app can spawn it in dev.
Push-Location (Join-Path $root 'mcp-server')
try {
    & (Join-Path $dotnetRoot 'dotnet.exe') build -c Debug -f net8.0-windows --nologo
    if ($LASTEXITCODE -ne 0) { throw "MCP server build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

if (-not $env:LLM_API_KEY) {
    Write-Host "No LLM_API_KEY set - app will run the mock orchestrator." -ForegroundColor Yellow
}

Push-Location $root
try {
    & $gradle :app:run --console=plain
} finally {
    Pop-Location
}
