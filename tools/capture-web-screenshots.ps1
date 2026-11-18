<#
.SYNOPSIS
    Takes the web dashboard screenshots used in the README (docs/screenshots/web-*.png).

.DESCRIPTION
    Drives headless Chrome over its DevTools protocol against the dashboard preview, which serves the real
    server and web files over sample data. Start the preview first (it runs for 20 minutes):

        DUMPKIN_PREVIEW=1 ./gradlew testDebugUnitTest --tests '*WebPreview*' --rerun -i

    then run:

        powershell -ExecutionPolicy Bypass -File tools/capture-web-screenshots.ps1

    Needs Google Chrome (set CHROME_PATH if it lives somewhere unusual). The Android screenshots come from
    app/src/test/java/app/dumpkin/ui/Screenshots.kt instead.
#>
$ErrorActionPreference = 'Stop'

$out = Join-Path $PSScriptRoot '..\docs\screenshots'
$chrome = @($env:CHROME_PATH,
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe") |
    Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $chrome) { throw 'Chrome not found. Set CHROME_PATH to chrome.exe.' }

$previewUrl = 'http://127.0.0.1:18080/'
$password = 'preview-1234'
$port = 9333
$profile = Join-Path $env:TEMP 'dumpkin-screenshot-profile'

try { $null = Invoke-WebRequest -UseBasicParsing $previewUrl -TimeoutSec 5 }
catch { throw "The dashboard preview isn't running on $previewUrl. See the notes at the top of this script." }

Remove-Item -Recurse -Force $profile -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null

$null = Start-Process $chrome -PassThru -ArgumentList @(
    '--headless=new', "--remote-debugging-port=$port", '--remote-allow-origins=*', '--disable-extensions',
    "--user-data-dir=$profile", '--no-first-run', '--disable-gpu', '--hide-scrollbars', 'about:blank')

try {
    # Wait for the blank tab. Windows PowerShell 5.1 can hand the JSON array back wrapped in another array,
    # so flatten two levels.
    $ws = $null
    for ($i = 0; $i -lt 40 -and -not $ws; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            foreach ($outer in @(Invoke-RestMethod "http://127.0.0.1:$port/json")) {
                foreach ($t in $outer) {
                    if (-not $ws -and $t.type -eq 'page' -and $t.url -eq 'about:blank') { $ws = [string]$t.webSocketDebuggerUrl }
                }
            }
        } catch { }
    }
    if (-not $ws) { throw 'Chrome did not start.' }

    $sock = New-Object System.Net.WebSockets.ClientWebSocket
    $sock.ConnectAsync([Uri]$ws, [Threading.CancellationToken]::None).Wait()
    $script:id = 0

    # Returns the raw JSON reply: screenshots are too big for ConvertFrom-Json.
    function Send($method, $params = @{}) {
        $script:id++
        $msg = @{ id = $script:id; method = $method; params = $params } | ConvertTo-Json -Depth 10 -Compress
        $bytes = [Text.Encoding]::UTF8.GetBytes($msg)
        $sock.SendAsync([ArraySegment[byte]]$bytes, 'Text', $true, [Threading.CancellationToken]::None).Wait()
        while ($true) {
            $ms = New-Object IO.MemoryStream
            $buf = New-Object byte[] 262144
            do {
                $r = $sock.ReceiveAsync([ArraySegment[byte]]$buf, [Threading.CancellationToken]::None).Result
                $ms.Write($buf, 0, $r.Count)
            } until ($r.EndOfMessage)
            $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
            if ($text -match '^\{"id":(\d+)' -and [int]$Matches[1] -eq $script:id) { return $text }
        }
    }
    function Js($code) { $null = Send 'Runtime.evaluate' @{ expression = $code; awaitPromise = $true; returnByValue = $true } }
    function Size($w, $h, $dsf, $mobile) {
        $null = Send 'Emulation.setDeviceMetricsOverride' @{ width = $w; height = $h; deviceScaleFactor = $dsf; mobile = $mobile }
        Start-Sleep -Milliseconds 400
    }
    function Go($hash) { Js "location.hash = '$hash'; window.scrollTo(0, 0)"; Start-Sleep -Milliseconds 1500 }
    function Shot($name) {
        $reply = Send 'Page.captureScreenshot' @{ format = 'png' }
        if ($reply -notmatch '"data":"([^"]+)"') { throw "No screenshot data for $name" }
        [IO.File]::WriteAllBytes((Join-Path $out "$name.png"), [Convert]::FromBase64String($Matches[1]))
        Write-Output "saved $name.png"
    }

    $null = Send 'Page.enable'
    $null = Send 'Emulation.setEmulatedMedia' @{ features = @(@{ name = 'prefers-color-scheme'; value = 'dark' }) }

    Size 1280 620 1.5 $false
    $null = Send 'Page.navigate' @{ url = $previewUrl }
    Start-Sleep -Seconds 2
    Shot 'web-login'

    Js "const pw = document.querySelector('input[type=password]'); pw.value = '$password'; pw.form.requestSubmit();"
    Start-Sleep -Seconds 3
    Size 1280 470 1.5 $false
    Shot 'web-home'

    Size 1280 720 1.5 $false
    Go '#/server/smp'
    Start-Sleep -Milliseconds 1500
    Shot 'web-console'

    # Bedrock server: scroll the settings so the whole Network card (with its Bedrock port) is in view.
    Size 1280 860 1.5 $false
    Go '#/server/cre/settings'
    Js "const h = [...document.querySelectorAll('h2')].find(e => e.textContent.trim() === 'Network'); const card = h.closest('.card'); window.scrollTo(0, card.getBoundingClientRect().top + window.scrollY - 130)"
    Start-Sleep -Milliseconds 1000
    Shot 'web-bedrock'

    # Files and backups on a stopped server (changes are allowed there).
    Size 1280 820 1.5 $false
    Go '#/server/crash/files'
    Shot 'web-files'
    foreach ($label in 'nightly', 'before the update') {
        Js "fetch('/api/servers/crash/backups', { method: 'POST', headers: { 'X-Requested-With': 'Dumpkin', 'Content-Type': 'application/json' }, body: JSON.stringify({ label: '$label' }) })"
        Start-Sleep -Seconds 2
    }
    Size 1280 880 1.5 $false
    Go '#/server/crash/backups'
    Start-Sleep -Milliseconds 800
    Shot 'web-backups'

    # The same dashboard on a phone, which is how most people will open it.
    Size 390 560 2 $true
    Go '#/'
    Start-Sleep -Milliseconds 1500
    Shot 'web-mobile'
}
finally {
    Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*dumpkin-screenshot-profile*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
