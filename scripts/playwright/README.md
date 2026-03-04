# Playwright Workflow Runner

This runner executes browser workflows from a JSON file using `@playwright/cli`.
It is designed for reliability by:

- refreshing snapshots before element actions
- resolving refs dynamically from `target` rules
- retrying failed element actions
- writing all artifacts under `output/playwright/<label>-<timestamp>/`

## Prerequisites

- Node.js + npm (`npx` must be on PATH)
- PowerShell

## Run

```powershell
pwsh -File .\scripts\playwright\run-playwright-workflow.ps1 `
  -WorkflowFile .\scripts\playwright\workflow.example.json `
  -OutputLabel example-run `
  -Headed
```

If you use Windows PowerShell (no `pwsh`), run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\playwright\run-playwright-workflow.ps1 `
  -WorkflowFile .\scripts\playwright\workflow.example.json `
  -OutputLabel example-run
```

## Workflow JSON format

Top-level keys:

- `name` (optional): used for artifact label
- `session` (optional): Playwright CLI session name
- `headed` (optional): open browser in headed mode by default
- `defaults.retries` / `defaults.retryDelayMs` (optional)
- `steps` (required): ordered actions

Supported `action` values:

- `open` (`url`, optional `headed`)
- `goto` (`url`)
- `snapshot`
- `click`, `dblclick`, `hover`, `check`, `uncheck` (require `target`)
- `fill` (`target`, `text`)
- `select` (`target`, `value`)
- `type` (`text`)
- `press` (`key`)
- `wait` (`ms`)
- `screenshot` (optional `target`)
- `tab-list`
- `tab-select` (`index`)
- `tab-close` (optional `index`)
- `tab-new` (optional `url`)
- `assert-url-contains` (`value`)
- `assert-title-contains` (`value`)
- `close`

`target` can be:

- `{ "ref": "e12" }`
- `{ "role": "button", "text": "Sign in" }`
- `{ "contains": "Continue", "index": 0 }`
- `{ "regex": "link \\\"Learn more\\\"" }`
- `"Sign in"` (shorthand for `contains`)

## Notes

- `index` is zero-based when multiple matches are found.
- Relative file paths in your workflow should be rooted to the current working directory for CLI commands.
- Detailed command output is saved to `runner.log` in the artifact directory.
