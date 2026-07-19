# AI Search Launcher

A hotkey-style desktop launcher for Windows that turns a **natural-language query** into real,
actionable results: **Windows Settings** pages (with `ms-settings:` deep links) and **files on your
PC** (from the Windows Search index).

An LLM does the *planning* (what to search and where); a local **MCP server** does the
*authoritative* searching. The LLM never invents deep links or file paths — every result comes from
a real tool payload.

> **Status:** v0.1.0 (Windows MSI). Cloud LLMs supported today (OpenAI / Anthropic / Google); a
> local-model path is planned. With no API key the app runs a built-in **mock** so the UI is always
> usable.

---

## How it works

```
You type ──► Compose app (Kotlin/JVM) ──► Cloud LLM plans the search (Koog agent)
                    │                              │
                    │  spawns child process        │ tool calls (MCP / JSON-RPC over stdio)
                    ▼                              ▼
             McpServer.exe (.NET 8) ──► settings_search / files_search
                                            │                 │
                                            ▼                 ▼
                                   Settings catalog     Windows Search Index
                                   (ms-settings: links) (real file paths)
```

1. You type a query (e.g. *"turn on bluetooth"* or *"budget spreadsheet from last month"*).
2. The Compose app hands it to a **Koog `AIAgent`** driven by your chosen cloud LLM.
3. The agent decides which tools to call and invokes them on a **local .NET MCP server**.
4. The server runs the actual searches and returns structured results.
5. The app streams each step of the chain to the UI and renders ranked, clickable results.

For a deeper dive (block diagram, data flow, contracts), see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### App ↔ server transport

The Kotlin app **launches `McpServer.exe` as a child process** and speaks **MCP (JSON-RPC) over the
child's stdin/stdout** — no sockets, no named pipes. The server's `stderr` is reserved for logs so it
can never corrupt the protocol stream. The process is spawned lazily on the first search and reused.

---

## Project layout

```
ai-search-launcher/
├── app/                     # Compose Multiplatform desktop app (Kotlin/JVM)
│   └── src/main/kotlin/com/ai/search/
│       ├── Main.kt          # entry point; picks Koog (real) vs Mock orchestrator
│       ├── model/           # SearchState, ChainStep, ResultItem
│       ├── orchestrator/    # KoogOrchestrator, MockOrchestrator, McpClient, LlmConfig
│       └── ui/              # SearchApp, SearchBox, ChainProgress, ResultList
├── mcp-server/              # .NET 8 MCP server (C#)
│   ├── Program.cs           # registers tools, stdio transport
│   ├── Tools/               # settings_search, files_search (MCP tools)
│   ├── Providers/           # SettingsProvider, FilesProvider (the real search logic)
│   └── Data/                # settings-catalog.json
├── scripts/                 # run-app.ps1, run-server.ps1 (dev launchers)
├── .github/workflows/       # ci.yml (build + smoke), release.yml (MSI on v* tags)
└── .env.example             # template for LLM config
```

### Key components

| Component | Responsibility |
|---|---|
| `SearchOrchestrator` | Interface: `search(query): Flow<SearchState>`. Two impls, swappable. |
| `KoogOrchestrator` | Real path — runs the Koog agent, streams chain steps, parses tool results. |
| `MockOrchestrator` | No-key fallback — returns 3 canned results with the same contract. |
| `McpClient` | Spawns/supervises `McpServer.exe`, exposes its tools as a Koog `ToolRegistry`. |
| `LlmConfig` | Reads `LLM_*` env vars; `fromEnv()` returns `null` (→ mock) when no key. |
| `SettingsProvider` | Matches a bundled settings catalog → `ms-settings:` deep links. |
| `FilesProvider` | Queries the Windows Search index (`Search.CollatorDSO` OLE DB, AQS SQL). |

---

## Configuration

The app is configured entirely at **runtime** via environment variables — nothing is baked into the
build. The shipped MSI is identical whether or not you have a key.

| Variable | Values | Purpose |
|---|---|---|
| `LLM_API_KEY` | provider API key | **Present → real agent. Absent → mock.** |
| `LLM_PROVIDER` | `openai` \| `anthropic` \| `google` | Which cloud LLM (default `openai`). |
| `LLM_MODEL` | model id | Optional override (e.g. `gpt-4o`). |
| `MCP_SERVER_PATH` | path to `McpServer.exe` | Optional; auto-resolved in the packaged app. |
| `DOTNET_ROOT` | path to .NET runtime | Optional; for the framework-dependent server host. |

For local dev, copy `.env.example` to `.env` and fill in your key — `scripts/run-app.ps1` loads it
automatically. (The **installed MSI reads OS environment variables only**, not `.env`.)

```dotenv
LLM_PROVIDER=openai
LLM_API_KEY=sk-your-key-here
LLM_MODEL=gpt-4o
```

---

## Running from source

**Prerequisites:** Windows, JDK 21, .NET 8 SDK. (This repo also carries a self-contained toolchain
under `tools/` used by the dev scripts.)

```powershell
# 1. (optional) set up your LLM key
copy .env.example .env    # then edit .env

# 2. build the .NET server + run the Compose app (spawns the server itself)
powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1
```

Without a key the app launches the mock orchestrator (you'll see a yellow console notice). With a
valid key it uses the real Koog → MCP → results path.

Run the MCP server standalone (stdio) for debugging:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-server.ps1
# or a quick provider self-test bypassing MCP:
dotnet run --project mcp-server -- --selftest "budget"
```

---

## The MCP tools

The server exposes two tools over MCP:

- **`settings_search(query, limit=8)`** — Windows Settings pages/toggles by intent. Returns matches
  with an `ms-settings:` deep link each.
- **`files_search(query, types="", modifiedAfter="", modifiedBefore="", locations="", limit=15)`** —
  files via the Windows Search index. Optional CSV filters for extensions, date range, and folder
  prefixes. Returns ranked files with path, name, size and modified date.

Because it's a standard MCP stdio server, it can be reused by **any** MCP client, not just this app.

---

## CI/CD

- **`ci.yml`** (push/PR to `dev`/`main`): builds the .NET server, compiles the Kotlin app, and runs
  `:app:mcpSmoke` — a no-key MCP transport + tool-discovery smoke test. Fast and deterministic; no
  API key required.
- **`release.yml`** (on `v*` tags): builds the Windows MSI (`:app:packageReleaseMsi`) and publishes a
  GitHub Release with the installer attached.

No secrets are used to build the app, and no key is embedded in any artifact.

---

## Roadmap

- Local LLM support (currently cloud only).
- More result domains beyond Settings and Files.
- Global hotkey + tray integration.
- Code-signed installer (the current MSI is unsigned → SmartScreen may warn).
