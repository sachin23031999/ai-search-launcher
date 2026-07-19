# AI Search Launcher — Architecture

A hotkey overlay that turns a natural-language query into real Windows **Settings** deep-links and **file** results. An LLM does the *planning*; a local MCP server does the *authoritative* searching.

---

## 1. Block diagram (where each piece sits)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  DESKTOP APP  —  Compose Multiplatform (Kotlin/JVM)         process: JVM       │
│                                                                                │
│   ┌────────────────────────── UI layer (ui/) ──────────────────────────────┐  │
│   │  SearchApp · SearchBox · ChainProgress · ResultList                     │  │
│   │  renders SearchState → collects Flow<SearchState>                       │  │
│   └───────────────▲───────────────────────────────────────┬────────────────┘  │
│                   │ Flow<SearchState> (steps + results)    │ query string      │
│   ┌───────────────┴───────────────────────────────────────▼────────────────┐  │
│   │  Orchestrator layer (orchestrator/)                                     │  │
│   │                                                                         │  │
│   │   Main.kt  ──picks──►  cfg=LlmConfig.fromEnv()  &&  MCP server found?   │  │
│   │        ├── YES ─► KoogOrchestrator  (real Koog AIAgent)                 │  │
│   │        └── NO  ─► MockOrchestrator  (3 hard-coded results)              │  │
│   │                                                                         │  │
│   │   KoogOrchestrator ──► LlmConfig.executor()  ─┐   ┌── McpClient         │  │
│   └───────────────────────────────────────────────┼───┼─────────────────────┘  │
└───────────────────────────────────────────────────┼───┼──────────────────────┘
             (1) planning prompt / tool calls        │   │  (2) MCP over stdio
                                                      ▼   │  (spawns child proc)
                                    ┌─────────────────────┐│
                                    │  CLOUD LLM          ││
                                    │  OpenAI / Anthropic ││
                                    │  / Google (Koog)    ││
                                    └─────────────────────┘│
                                                           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  MCP SERVER  —  .NET 8 (C#)                        process: McpServer.exe      │
│  stdio transport · JSON-RPC (MCP)                                              │
│                                                                                │
│   Program.cs  ── registers tools ──►                                          │
│      ┌────────────────────────┐      ┌───────────────────────────┐            │
│      │ SettingsSearchTool     │      │ FilesSearchTool           │            │
│      │  settings_search(q)    │      │  files_search(q, …)       │            │
│      └──────────┬─────────────┘      └──────────┬────────────────┘            │
│                 ▼                                ▼                             │
│      ┌────────────────────────┐      ┌───────────────────────────┐            │
│      │ SettingsProvider       │      │ FilesProvider             │            │
│      │  settings-catalog.json │      │  Windows Search Indexer   │            │
│      │  → ms-settings: links  │      │  (SystemIndex via OLE DB, │            │
│      │                        │      │   AQS SQL) → real paths   │            │
│      └────────────────────────┘      └───────────────────────────┘            │
└──────────────────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
   Windows Settings                     Files on this PC
   (ms-settings: deep links)           (indexed by Windows Search)
```

---

## 2. Data flow (one query, real path)

1. **User types** a query in `SearchBox` (e.g. *"turn on bluetooth"*) → calls `orchestrator.search(query)`.
2. **KoogOrchestrator** opens a `channelFlow<SearchState>` and immediately emits an **"Understanding your request"** step.
3. **McpClient.toolRegistry()** lazily **spawns `McpServer.exe`** as a child process and builds an MCP **stdio** transport (child's stdin/stdout). Koog introspects the server's advertised tools → `ToolRegistry`.
4. **Koog `AIAgent`** sends the system prompt + user query to the **cloud LLM** (`LlmConfig.executor()`, temp 0.0). The LLM decides *which* tools to call.
5. LLM issues **tool calls** → Koog invokes them over MCP → `SettingsSearchTool` / `FilesSearchTool` run their **Providers**:
   - `SettingsProvider` matches against a bundled **settings catalog** → returns `ms-settings:` deep links.
   - `FilesProvider` builds **Advanced Query Syntax SQL**, queries the **Windows Search Index** (`Search.CollatorDSO` OLE DB) → returns real file paths + scores.
6. **Event handlers** (`onToolCallStarting/Completed/Failed`) stream a live **ChainStep** per tool ("Searching Settings → 3 matches") and parse the tool's JSON payload into **`ResultItem`s** — results come *only* from tool payloads, never invented by the LLM.
7. LLM returns **one summary sentence**; orchestrator emits a final `SearchState(isRunning=false, results, summary)`.
8. **UI** re-renders on every emission: `ChainProgress` shows the reasoning chain, `ResultList` shows ranked results. Clicking a result fires its `ResultAction` (open `ms-settings:` URI or reveal the file).

**Mock path:** if `LLM_API_KEY` is absent (or server missing), `Main.kt` wires `MockOrchestrator` instead — same `Flow<SearchState>` contract, 3 canned results. UI is identical.

---

## 3. Key contracts & boundaries

| Boundary | Mechanism | Notes |
|---|---|---|
| UI ↔ Orchestrator | `SearchOrchestrator.search(query): Flow<SearchState>` | One interface, two impls (Koog / Mock). Swappable. |
| Orchestrator ↔ LLM | Koog `PromptExecutor` (OpenAI/Anthropic/Google) | Cloud only for now; provider/model from env. |
| App (JVM) ↔ MCP server (.NET) | **MCP over stdio**, JSON-RPC, child process | Language-agnostic seam; server reusable by any MCP client. |
| Tool ↔ OS | Providers | Settings = static catalog JSON; Files = live Windows Search Index. |

## 4. Configuration (runtime, not baked in)

- `LLM_PROVIDER` (`openai`\|`anthropic`\|`google`, default openai)
- `LLM_API_KEY`  → **present = real agent, absent = mock**
- `LLM_MODEL`    → optional model id override
- `MCP_SERVER_PATH` / `DOTNET_ROOT` → server discovery & runtime (auto-resolved in packaged app)

> The shipped MSI is **identical** regardless of key — real-vs-mock is decided **at launch** from the OS environment.

## 5. Packaging

- `bundleMcpServer` publishes the .NET server into `app/resources/common/mcp-server/`; Compose stages it into the image so `McpClient` finds `mcp-server/McpServer.exe` at runtime.
- `release.yml` (on `v*` tags) → `packageReleaseMsi` → GitHub Release (currently **v0.1.0**).
- `ci.yml` → build server + compile app + `mcpSmoke` (mock/no-key transport test).
