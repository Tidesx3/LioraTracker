# Routines from an AI

Any AI can make Liora routines: it writes a routine file (`<name>.liora.json`, the format in
[docs/routine-format.md](../../docs/routine-format.md)), and Liora imports it on the phone, showing what's in it
first. This tool gives the AI what it needs to do that well: Liora's exercise catalog to search, a check that
reads the file exactly as Liora will, and the person's own backup to plan from.

It's one Node script without dependencies (Node 18 or later), usable two ways:

- **As an MCP server** (`liora-routine.mjs mcp`), for AI harnesses that call tools: Claude Code, Codex, Gemini
  CLI, Cursor, VS Code, Claude Desktop and others.
- **As a command line**, for any AI that runs commands, and for people.

Run from a clone of this repository, it reads the catalog and the format from it; copied elsewhere on its own, it
fetches them from GitHub once a day.

## Commands

```sh
node tools/routine/liora-routine.mjs format                  # the routine file format, to read first
node tools/routine/liora-routine.mjs search incline press    # catalog exercises, common ones first
node tools/routine/liora-routine.mjs search --muscle glutes --equipment machine --limit 10
node tools/routine/liora-routine.mjs show "Romanian Deadlift" # one exercise in full
node tools/routine/liora-routine.mjs check push.liora.json   # errors, warnings, and what Liora makes of it
node tools/routine/liora-routine.mjs check push.liora.json --backup liora-backup.zip --json
node tools/routine/liora-routine.mjs profile liora-backup.zip # units, gyms, own exercises, routines, recent training
```

`check` exits with 1 when the file has errors, so an AI can loop until it's clean. A backup is what Liora writes
under Settings › Your data › Back up everything (a `.zip`, or the `liora.json` in it). It stays on the computer:
the tool only reads it.

## MCP tools

| Tool | What it does |
|---|---|
| `liora_routine_format` | The format, with examples |
| `liora_search_exercises` | Search the catalog by words, equipment, muscle or tracking |
| `liora_exercise_details` | One exercise in full |
| `liora_check_routine` | Check a file the way Liora reads it, and preview the result |
| `liora_save_routine` | Check and save it as `<name>.liora.json` (the Downloads folder unless a path is given) |
| `liora_read_backup` | A person's backup, summed up for planning |

The server also tells the AI how to go about it (its MCP instructions), so asking for "a Liora routine" is enough.

## Setting it up

Replace `/path/to/LioraTracker` with where this repository is (on Windows, e.g. `C:/LioraTracker`).

**Claude Code.** In this repository, the `liora-routine` skill (`.claude/skills/liora-routine`) is there already:
ask for a routine, or type `/liora-routine 3-day full body`. To use it anywhere, add the MCP server for your user,
and copy the skill to `~/.claude/skills/` if you like its workflow:

```sh
claude mcp add --scope user liora -- node /path/to/LioraTracker/tools/routine/liora-routine.mjs mcp
```

**Codex.** `codex mcp add liora -- node /path/to/LioraTracker/tools/routine/liora-routine.mjs mcp`, or in
`~/.codex/config.toml`:

```toml
[mcp_servers.liora]
command = "node"
args = ["/path/to/LioraTracker/tools/routine/liora-routine.mjs", "mcp"]
```

**Gemini CLI.** `gemini mcp add liora node /path/to/LioraTracker/tools/routine/liora-routine.mjs mcp`, or under
`mcpServers` in `~/.gemini/settings.json` as below.

**Cursor, Claude Desktop, Windsurf and most others** take the same JSON (Cursor: `~/.cursor/mcp.json`; Claude
Desktop: Settings › Developer › Edit Config). VS Code's `.vscode/mcp.json` calls the list `servers` instead.

```json
{
  "mcpServers": {
    "liora": {
      "command": "node",
      "args": ["/path/to/LioraTracker/tools/routine/liora-routine.mjs", "mcp"]
    }
  }
}
```

**Any other AI that can run commands** (an agent in this repository reads [AGENTS.md](../../AGENTS.md)): have it
read `format`, then search, write and `check` as above.

**An AI chat on the phone** (the Claude or ChatGPT app): in Liora, Train › the import button › "Copy the format for
your AI", paste that into the chat with what you'd like, then share the answer to Liora or copy it and tap Paste.

## Getting the file to the phone

Open the `.liora.json` with Liora from wherever it lands (Quick Share, Google Drive, email, a cable), or pick it
in Liora under Train › the import button › Open file. The JSON pasted there works too.

## Tests

```sh
node --test tools/routine/test/routine.test.mjs
```

They check the examples in the format's documentation against the real catalog (the app's
`RoutineImportFlowTest` imports the same examples), the matching against the app's, the problems, backups, the
command line and the MCP server.
