# Run in PowerShell on the Windows box that has QAIRT 2.45.0 installed.
# Decides, before anything is copied or downloaded, whether the Genie/NPU
# branch is viable at all on SM8850 (Hexagon HTP v81).

$ErrorActionPreference = "Continue"

Write-Host "`n=== Locating QAIRT 2.45.0 ===" -ForegroundColor Cyan
$candidates = @(
  "$env:USERPROFILE\Qualcomm\AIStack\QAIRT\2.45.0",
  "C:\Qualcomm\AIStack\QAIRT\2.45.0",
  "$env:USERPROFILE\Qualcomm\AIStack\QAIRT\2.45.0.250xxx"
)
$QAIRT = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $QAIRT) {
  $QAIRT = Get-ChildItem -Path "C:\","$env:USERPROFILE" -Filter "2.45.0*" -Recurse -Directory `
             -ErrorAction SilentlyContinue -Depth 6 |
           Where-Object { $_.FullName -match "QAIRT" } |
           Select-Object -First 1 -ExpandProperty FullName
}
if (-not $QAIRT) {
  Write-Host "QAIRT 2.45.0 not found. Set it by hand:  `$QAIRT = 'C:\path\to\QAIRT\2.45.0'" -ForegroundColor Red
  exit 1
}
Write-Host "QAIRT = $QAIRT" -ForegroundColor Green

# --- THE DECISION ---------------------------------------------------------
Write-Host "`n=== HTP v81 skel (SM8850) — the go/no-go ===" -ForegroundColor Cyan
$v81 = Join-Path $QAIRT "lib\hexagon-v81\unsigned"
if (Test-Path $v81) {
  Write-Host "PRESENT: $v81" -ForegroundColor Green
  Get-ChildItem $v81 | ForEach-Object {
    "{0,-45} {1,10:N0} KB" -f $_.Name, ($_.Length/1KB) | Write-Host
  }
} else {
  Write-Host "MISSING: $v81" -ForegroundColor Red
  Write-Host "Hexagon versions this SDK DOES ship:" -ForegroundColor Yellow
  Get-ChildItem (Join-Path $QAIRT "lib") -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "hexagon-*" } | ForEach-Object { "  $($_.Name)" | Write-Host }
  Write-Host "`nSTOP. Report the list above — 2.45.0 may not target v81." -ForegroundColor Red
}

# --- The rest of the copy set --------------------------------------------
Write-Host "`n=== genie-t2t-run ===" -ForegroundColor Cyan
$t2t = Join-Path $QAIRT "bin\aarch64-android\genie-t2t-run"
if (Test-Path $t2t) { Write-Host "PRESENT ($([math]::Round((Get-Item $t2t).Length/1KB)) KB)" -ForegroundColor Green }
else { Write-Host "MISSING $t2t" -ForegroundColor Red
       Get-ChildItem (Join-Path $QAIRT "bin") -Directory -EA SilentlyContinue | % { "  bin\$($_.Name)" } }

Write-Host "`n=== lib\aarch64-android (Android runtime .so) ===" -ForegroundColor Cyan
$aa = Join-Path $QAIRT "lib\aarch64-android"
if (Test-Path $aa) {
  $so = Get-ChildItem $aa -Filter *.so
  Write-Host "$($so.Count) .so, $([math]::Round((($so|Measure-Object Length -Sum).Sum)/1MB)) MB total" -ForegroundColor Green
  $so | Where-Object { $_.Name -match "Genie|HtpV81|QnnHtp\.so|QnnSystem|HtpPrepare" } |
    ForEach-Object { "  $($_.Name)" | Write-Host }
} else { Write-Host "MISSING $aa" -ForegroundColor Red }

# --- Device ---------------------------------------------------------------
Write-Host "`n=== Device ===" -ForegroundColor Cyan
adb devices -l
Write-Host "SoC: " -NoNewline; adb shell getprop ro.soc.model
Write-Host "ABI: " -NoNewline; adb shell getprop ro.product.cpu.abi
Write-Host "Free on /data/local/tmp:"; adb shell df -h /data/local/tmp

Write-Host "`n=== Copy set (only if v81 is PRESENT) ===" -ForegroundColor Cyan
@("bin\aarch64-android\genie-t2t-run","lib\aarch64-android","lib\hexagon-v81\unsigned","include") |
  ForEach-Object {
    $p = Join-Path $QAIRT $_
    if (Test-Path $p) {
      $sz = (Get-ChildItem $p -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum
      "{0,-40} {1,8:N0} MB" -f $_, ($sz/1MB) | Write-Host
    } else { "{0,-40} {1}" -f $_, "MISSING" | Write-Host }
  }
Write-Host "`nQAIRT root for the next step: $QAIRT`n" -ForegroundColor Green
