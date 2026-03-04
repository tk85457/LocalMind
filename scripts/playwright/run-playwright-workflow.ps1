[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$WorkflowFile,
    [string]$OutputLabel = "",
    [string]$Session = "",
    [switch]$Headed,
    [switch]$KeepBrowserOpen,
    [switch]$VerboseCli
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-RunnerLog {
    param([string]$Message)
    Write-Host "[playwright-runner] $Message"
}

function Test-CommandExists {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-JsonProperty {
    param(
        [object]$Object,
        [string]$Name,
        $Default = $null
    )
    if ($null -eq $Object) {
        return $Default
    }

    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop) {
        return $Default
    }

    return $prop.Value
}

function To-SafeName {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return "workflow"
    }

    $safe = $Text -replace '[^a-zA-Z0-9._-]+', '-'
    $safe = $safe.Trim('-')
    if ([string]::IsNullOrWhiteSpace($safe)) {
        return "workflow"
    }

    return $safe
}

function Normalize-Target {
    param([object]$Target)
    if ($Target -is [string]) {
        return [pscustomobject]@{
            contains = [string]$Target
        }
    }

    return $Target
}

function Get-LatestSnapshotPath {
    $snapshot = Get-ChildItem -Path ".playwright-cli" -Filter "page-*.yml" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $snapshot) {
        throw "No snapshot file found under .playwright-cli. Run a snapshot step first."
    }

    return $snapshot.FullName
}

function Parse-SnapshotElements {
    param([string]$SnapshotPath)

    $elements = @()
    $lines = Get-Content -Path $SnapshotPath
    foreach ($line in $lines) {
        if ($line -match '^\s*-\s*(?<kind>[^"\[]+?)(?:\s+"(?<label>[^"]*)")?\s+\[ref=(?<ref>[A-Za-z0-9]+)\]') {
            $label = ""
            if ($Matches.ContainsKey("label")) {
                $label = [string]$Matches["label"]
            }

            $elements += [pscustomobject]@{
                Ref = $Matches.ref
                Kind = $Matches.kind.Trim()
                Label = $label.Trim()
                RawLine = $line.Trim()
            }
        }
    }

    return $elements
}

$npxCommand = Get-Command "npx.cmd" -ErrorAction SilentlyContinue
if ($null -eq $npxCommand) {
    $npxCommand = Get-Command "npx" -ErrorAction SilentlyContinue
}
if ($null -eq $npxCommand) {
    throw "npx is required but was not found on PATH. Install Node.js/npm first."
}
$npxExecutable = $npxCommand.Source

$workflowPath = (Resolve-Path $WorkflowFile).Path
$workflowDir = Split-Path -Parent $workflowPath
$workflow = Get-Content -Raw -Path $workflowPath | ConvertFrom-Json
$steps = @($workflow.steps)

if ($steps.Count -eq 0) {
    throw "Workflow file '$workflowPath' does not define any steps."
}

$workflowName = [string](Get-JsonProperty -Object $workflow -Name "name" -Default "")
$labelCandidate = if ([string]::IsNullOrWhiteSpace($OutputLabel)) { $workflowName } else { $OutputLabel }
$label = To-SafeName -Text $labelCandidate
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$outputDir = Join-Path $repoRoot "output\playwright\$label-$stamp"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

$logPath = Join-Path $outputDir "runner.log"
Write-RunnerLog "Workflow: $workflowPath"
Write-RunnerLog "Artifacts: $outputDir"

$defaultRetries = [int](Get-JsonProperty -Object (Get-JsonProperty -Object $workflow -Name "defaults" -Default $null) -Name "retries" -Default 3)
$defaultRetryDelayMs = [int](Get-JsonProperty -Object (Get-JsonProperty -Object $workflow -Name "defaults" -Default $null) -Name "retryDelayMs" -Default 700)

if ([string]::IsNullOrWhiteSpace($Session)) {
    $Session = [string](Get-JsonProperty -Object $workflow -Name "session" -Default "")
}
if ([string]::IsNullOrWhiteSpace($Session)) {
    $Session = "wf-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))"
}

$workflowHeaded = [bool](Get-JsonProperty -Object $workflow -Name "headed" -Default $false)
$shouldOpenHeaded = $Headed.IsPresent -or $workflowHeaded

$codexHome = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $HOME ".codex" }
$wrapperPath = Join-Path $codexHome "skills\playwright\scripts\playwright_cli.sh"
$runningOnWindows = $env:OS -eq "Windows_NT"
$useWrapper = (Test-Path $wrapperPath) -and (Test-CommandExists "bash") -and (-not $runningOnWindows)

if ($useWrapper) {
    Write-RunnerLog "Using skill wrapper: $wrapperPath"
}
else {
    Write-RunnerLog "Using npx fallback: $npxExecutable --yes --package @playwright/cli playwright-cli"
}

$previousSession = $env:PLAYWRIGHT_CLI_SESSION
$env:PLAYWRIGHT_CLI_SESSION = $Session

$script:CurrentElements = @()
$script:CurrentSnapshotPath = ""
$browserOpened = $false

function Invoke-PwCli {
    param(
        [string[]]$Arguments,
        [string]$Purpose,
        [switch]$AllowCliError
    )

    $quoted = ($Arguments | ForEach-Object {
            if ($_ -match '\s') {
                '"' + $_ + '"'
            }
            else {
                $_
            }
        }) -join ' '
    $commandText = "playwright-cli $quoted"
    Add-Content -Path $logPath -Value "[$(Get-Date -Format o)] $commandText`n"

    $raw = if ($useWrapper) {
        & bash $wrapperPath @Arguments 2>&1
    }
    else {
        & $npxExecutable --yes --package @playwright/cli playwright-cli @Arguments 2>&1
    }

    $output = ($raw | ForEach-Object { $_.ToString() }) -join "`n"
    Add-Content -Path $logPath -Value "$output`n`n"
    if ($VerboseCli) {
        Write-Host $output
    }

    $hasCliError = $output -match '(?m)^### Error\b'
    if ($hasCliError -and -not $AllowCliError) {
        throw "$Purpose failed.`n$output"
    }

    return [pscustomobject]@{
        HasError = $hasCliError
        Output = $output
    }
}

function Refresh-SnapshotCache {
    for ($attempt = 1; $attempt -le $defaultRetries; $attempt++) {
        $snapshotResult = Invoke-PwCli -Arguments @("snapshot") -Purpose "snapshot" -AllowCliError
        if (-not $snapshotResult.HasError) {
            $script:CurrentSnapshotPath = Get-LatestSnapshotPath
            $script:CurrentElements = Parse-SnapshotElements -SnapshotPath $script:CurrentSnapshotPath
            Write-RunnerLog "Snapshot refreshed: $(Split-Path -Leaf $script:CurrentSnapshotPath) (refs: $($script:CurrentElements.Count))"
            return $snapshotResult
        }

        if ($attempt -lt $defaultRetries) {
            Write-RunnerLog "Snapshot failed on attempt $attempt/$defaultRetries. Retrying after $defaultRetryDelayMs ms."
            Start-Sleep -Milliseconds $defaultRetryDelayMs
            continue
        }

        throw "snapshot failed after $defaultRetries attempts.`n$($snapshotResult.Output)"
    }
}

function Resolve-RefFromTarget {
    param(
        [object]$Target,
        [string]$ActionName,
        [int]$StepNumber
    )

    $targetObj = Normalize-Target -Target $Target
    if ($null -eq $targetObj) {
        throw "Step $StepNumber ($ActionName) is missing 'target'."
    }

    $directRef = [string](Get-JsonProperty -Object $targetObj -Name "ref" -Default "")
    if (-not [string]::IsNullOrWhiteSpace($directRef)) {
        return $directRef
    }

    $role = [string](Get-JsonProperty -Object $targetObj -Name "role" -Default "")
    $text = [string](Get-JsonProperty -Object $targetObj -Name "text" -Default "")
    $contains = [string](Get-JsonProperty -Object $targetObj -Name "contains" -Default "")
    $regex = [string](Get-JsonProperty -Object $targetObj -Name "regex" -Default "")
    $index = [int](Get-JsonProperty -Object $targetObj -Name "index" -Default 0)

    if ([string]::IsNullOrWhiteSpace($role) -and [string]::IsNullOrWhiteSpace($text) -and [string]::IsNullOrWhiteSpace($contains) -and [string]::IsNullOrWhiteSpace($regex)) {
        throw "Step $StepNumber ($ActionName) target must include one of: ref, role, text, contains, regex."
    }

    $matches = @($script:CurrentElements)
    if (-not [string]::IsNullOrWhiteSpace($role)) {
        $roleEscaped = [regex]::Escape($role)
        $matches = @($matches | Where-Object {
                $_.Kind -match "(?i)\b$roleEscaped\b" -or $_.RawLine -match "(?i)\b$roleEscaped\b"
            })
    }
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        $textEscaped = [regex]::Escape($text)
        $matches = @($matches | Where-Object {
                $_.Label -eq $text -or $_.RawLine -match "(?i)$textEscaped"
            })
    }
    if (-not [string]::IsNullOrWhiteSpace($contains)) {
        $containsEscaped = [regex]::Escape($contains)
        $matches = @($matches | Where-Object {
                $_.Label -match "(?i)$containsEscaped" -or $_.RawLine -match "(?i)$containsEscaped"
            })
    }
    if (-not [string]::IsNullOrWhiteSpace($regex)) {
        $matches = @($matches | Where-Object {
                $_.RawLine -match $regex -or $_.Label -match $regex
            })
    }

    if ($matches.Count -eq 0) {
        $sample = ($script:CurrentElements | Select-Object -First 20 | ForEach-Object {
                "$($_.Ref): $($_.Kind) '$($_.Label)'"
            }) -join "; "
        throw "Step $StepNumber ($ActionName) could not find a matching element. Snapshot sample: $sample"
    }

    if ($index -lt 0 -or $index -ge $matches.Count) {
        throw "Step $StepNumber ($ActionName) target index $index is out of range. Matches found: $($matches.Count)."
    }

    return $matches[$index].Ref
}

function Invoke-TargetedAction {
    param(
        [object]$Step,
        [int]$StepNumber,
        [string]$ActionName,
        [scriptblock]$BuildArguments
    )

    $maxAttempts = [int](Get-JsonProperty -Object $Step -Name "retries" -Default $defaultRetries)
    if ($maxAttempts -lt 1) {
        $maxAttempts = 1
    }

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        Refresh-SnapshotCache | Out-Null
        try {
            $ref = Resolve-RefFromTarget -Target (Get-JsonProperty -Object $Step -Name "target" -Default $null) -ActionName $ActionName -StepNumber $StepNumber
        }
        catch {
            if ($attempt -lt $maxAttempts) {
                Write-RunnerLog "Step $StepNumber ($ActionName) could not resolve target on attempt $attempt/$maxAttempts. Retrying after $defaultRetryDelayMs ms."
                Start-Sleep -Milliseconds $defaultRetryDelayMs
                continue
            }

            throw
        }

        $args = & $BuildArguments $ref
        $result = Invoke-PwCli -Arguments $args -Purpose "Step $StepNumber ($ActionName)" -AllowCliError
        if (-not $result.HasError) {
            Write-RunnerLog "Step $StepNumber ($ActionName) succeeded on ref $ref."
            return
        }

        if ($attempt -lt $maxAttempts) {
            Write-RunnerLog "Step $StepNumber ($ActionName) failed on attempt $attempt/$maxAttempts. Retrying after $defaultRetryDelayMs ms."
            Start-Sleep -Milliseconds $defaultRetryDelayMs
            continue
        }

        throw "Step $StepNumber ($ActionName) failed after $maxAttempts attempts.`n$($result.Output)"
    }
}

function Invoke-ActionWithRetry {
    param(
        [string[]]$Arguments,
        [string]$ActionName,
        [int]$StepNumber,
        [int]$MaxAttempts = $defaultRetries
    )

    if ($MaxAttempts -lt 1) {
        $MaxAttempts = 1
    }

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $result = Invoke-PwCli -Arguments $Arguments -Purpose "Step $StepNumber ($ActionName)" -AllowCliError
        if (-not $result.HasError) {
            return
        }

        if ($attempt -lt $MaxAttempts) {
            Write-RunnerLog "Step $StepNumber ($ActionName) failed on attempt $attempt/$MaxAttempts. Retrying after $defaultRetryDelayMs ms."
            Start-Sleep -Milliseconds $defaultRetryDelayMs
            continue
        }

        throw "Step $StepNumber ($ActionName) failed after $MaxAttempts attempts.`n$($result.Output)"
    }
}

function Ensure-BrowserOpen {
    if ($browserOpened) {
        return
    }

    $openArgs = @("open")
    if ($shouldOpenHeaded) {
        $openArgs += "--headed"
    }
    Invoke-PwCli -Arguments $openArgs -Purpose "open browser" | Out-Null
    $script:browserOpened = $true
}

function Get-RequiredString {
    param(
        [object]$Step,
        [string]$Name,
        [int]$StepNumber
    )

    $value = [string](Get-JsonProperty -Object $Step -Name $Name -Default "")
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Step $StepNumber requires '$Name'."
    }

    return $value
}

Push-Location $outputDir
try {
    for ($i = 0; $i -lt $steps.Count; $i++) {
        $stepNumber = $i + 1
        $step = $steps[$i]
        $action = [string](Get-JsonProperty -Object $step -Name "action" -Default "")
        if ([string]::IsNullOrWhiteSpace($action)) {
            throw "Step $stepNumber is missing 'action'."
        }

        $actionKey = $action.ToLowerInvariant()
        Write-RunnerLog "Running step $stepNumber/$($steps.Count): $actionKey"

        if ($actionKey -ne "open" -and $actionKey -ne "close") {
            Ensure-BrowserOpen
        }

        switch ($actionKey) {
            "open" {
                $url = [string](Get-JsonProperty -Object $step -Name "url" -Default "")
                $openArgs = @("open")
                if (-not [string]::IsNullOrWhiteSpace($url)) {
                    $openArgs += $url
                }

                $stepHeaded = [bool](Get-JsonProperty -Object $step -Name "headed" -Default $false)
                if ($shouldOpenHeaded -or $stepHeaded) {
                    $openArgs += "--headed"
                }

                Invoke-PwCli -Arguments $openArgs -Purpose "Step $stepNumber (open)" | Out-Null
                $browserOpened = $true
            }
            "goto" {
                $url = Get-RequiredString -Step $step -Name "url" -StepNumber $stepNumber
                Invoke-PwCli -Arguments @("goto", $url) -Purpose "Step $stepNumber (goto)" | Out-Null
            }
            "snapshot" {
                Refresh-SnapshotCache | Out-Null
            }
            "click" {
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "click" -BuildArguments {
                    param($ref)
                    @("click", $ref)
                }
            }
            "dblclick" {
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "dblclick" -BuildArguments {
                    param($ref)
                    @("dblclick", $ref)
                }
            }
            "hover" {
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "hover" -BuildArguments {
                    param($ref)
                    @("hover", $ref)
                }
            }
            "check" {
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "check" -BuildArguments {
                    param($ref)
                    @("check", $ref)
                }
            }
            "uncheck" {
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "uncheck" -BuildArguments {
                    param($ref)
                    @("uncheck", $ref)
                }
            }
            "fill" {
                $text = Get-RequiredString -Step $step -Name "text" -StepNumber $stepNumber
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "fill" -BuildArguments {
                    param($ref)
                    @("fill", $ref, $text)
                }
            }
            "select" {
                $value = Get-RequiredString -Step $step -Name "value" -StepNumber $stepNumber
                Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "select" -BuildArguments {
                    param($ref)
                    @("select", $ref, $value)
                }
            }
            "type" {
                $text = Get-RequiredString -Step $step -Name "text" -StepNumber $stepNumber
                Invoke-PwCli -Arguments @("type", $text) -Purpose "Step $stepNumber (type)" | Out-Null
            }
            "press" {
                $key = Get-RequiredString -Step $step -Name "key" -StepNumber $stepNumber
                Invoke-PwCli -Arguments @("press", $key) -Purpose "Step $stepNumber (press)" | Out-Null
            }
            "wait" {
                $milliseconds = [int](Get-JsonProperty -Object $step -Name "ms" -Default 500)
                if ($milliseconds -lt 0) {
                    throw "Step $stepNumber has invalid wait duration: $milliseconds"
                }
                Start-Sleep -Milliseconds $milliseconds
            }
            "screenshot" {
                $target = Get-JsonProperty -Object $step -Name "target" -Default $null
                if ($null -ne $target) {
                    Invoke-TargetedAction -Step $step -StepNumber $stepNumber -ActionName "screenshot" -BuildArguments {
                        param($ref)
                        @("screenshot", $ref)
                    }
                }
                else {
                    Invoke-ActionWithRetry -Arguments @("screenshot") -ActionName "screenshot" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
                }
            }
            "tab-list" {
                Invoke-ActionWithRetry -Arguments @("tab-list") -ActionName "tab-list" -StepNumber $stepNumber -MaxAttempts 1
            }
            "tab-select" {
                $index = [int](Get-JsonProperty -Object $step -Name "index" -Default -1)
                if ($index -lt 0) {
                    throw "Step $stepNumber (tab-select) requires a non-negative 'index'."
                }
                Invoke-ActionWithRetry -Arguments @("tab-select", "$index") -ActionName "tab-select" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
            }
            "tab-close" {
                $index = [int](Get-JsonProperty -Object $step -Name "index" -Default -1)
                if ($index -ge 0) {
                    Invoke-ActionWithRetry -Arguments @("tab-close", "$index") -ActionName "tab-close" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
                }
                else {
                    Invoke-ActionWithRetry -Arguments @("tab-close") -ActionName "tab-close" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
                }
            }
            "tab-new" {
                $url = [string](Get-JsonProperty -Object $step -Name "url" -Default "")
                if ([string]::IsNullOrWhiteSpace($url)) {
                    Invoke-ActionWithRetry -Arguments @("tab-new") -ActionName "tab-new" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
                }
                else {
                    Invoke-ActionWithRetry -Arguments @("tab-new", $url) -ActionName "tab-new" -StepNumber $stepNumber -MaxAttempts ([int](Get-JsonProperty -Object $step -Name "retries" -Default $defaultRetries))
                }
            }
            "assert-url-contains" {
                $expected = Get-RequiredString -Step $step -Name "value" -StepNumber $stepNumber
                $snapshotResult = Refresh-SnapshotCache
                $urlMatch = [regex]::Match($snapshotResult.Output, '(?m)^- Page URL:\s*(?<url>\S+)\s*$')
                if (-not $urlMatch.Success) {
                    throw "Step $stepNumber (assert-url-contains) could not read current URL."
                }

                $actualUrl = $urlMatch.Groups["url"].Value
                if ($actualUrl -notmatch [regex]::Escape($expected)) {
                    throw "Step $stepNumber expected URL to contain '$expected', but got '$actualUrl'."
                }
                Write-RunnerLog "URL assertion passed: $actualUrl"
            }
            "assert-title-contains" {
                $expected = Get-RequiredString -Step $step -Name "value" -StepNumber $stepNumber
                $snapshotResult = Refresh-SnapshotCache
                $titleMatch = [regex]::Match($snapshotResult.Output, '(?m)^- Page Title:\s*(?<title>.*)\s*$')
                if (-not $titleMatch.Success) {
                    throw "Step $stepNumber (assert-title-contains) could not read page title."
                }

                $actualTitle = $titleMatch.Groups["title"].Value
                if ($actualTitle -notmatch [regex]::Escape($expected)) {
                    throw "Step $stepNumber expected title to contain '$expected', but got '$actualTitle'."
                }
                Write-RunnerLog "Title assertion passed: $actualTitle"
            }
            "close" {
                Invoke-PwCli -Arguments @("close") -Purpose "Step $stepNumber (close)" -AllowCliError | Out-Null
                $browserOpened = $false
            }
            default {
                throw "Unsupported action '$action' at step $stepNumber."
            }
        }
    }

    if (-not $KeepBrowserOpen -and $browserOpened) {
        Invoke-PwCli -Arguments @("close") -Purpose "close browser" -AllowCliError | Out-Null
        $browserOpened = $false
    }

    Write-RunnerLog "Workflow completed successfully."
    Write-RunnerLog "Run log: $logPath"
}
finally {
    if (-not [string]::IsNullOrEmpty($previousSession)) {
        $env:PLAYWRIGHT_CLI_SESSION = $previousSession
    }
    else {
        Remove-Item Env:\PLAYWRIGHT_CLI_SESSION -ErrorAction SilentlyContinue
    }

    Pop-Location
}
