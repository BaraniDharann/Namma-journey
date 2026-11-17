# Opens the visible Chrome windows for the live walkthrough, side by side, each with its own
# profile (so owner, traveller and driver stay logged in separately) and a local debugging port.
param([string[]]$Who = @('owner', 'user', 'driver'))
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$root = Join-Path $env:TEMP 'namma-live-profiles'
$layout = @{
  owner  = @{ port = 9301; x = 0;    title = 'Owner' }
  user   = @{ port = 9302; x = 640;  title = 'Traveller' }
  driver = @{ port = 9303; x = 1280; title = 'Driver' }
  admin  = @{ port = 9304; x = 320;  title = 'Admin' }
}
foreach ($w in $Who) {
  $l = $layout[$w]
  $dir = Join-Path $root $w
  New-Item -ItemType Directory -Force $dir | Out-Null
  Start-Process $chrome -ArgumentList @(
    "--remote-debugging-port=$($l.port)", "--user-data-dir=$dir", '--no-first-run', '--no-default-browser-check',
    "--window-position=$($l.x),0", '--window-size=640,1000', 'https://nammajourneys.site/'
  )
}
