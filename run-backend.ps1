Set-Location 'c:\react project\Travel Booking Platform'
Get-Content .env | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
  $p = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($p[0].Trim(), $p[1].Trim(), 'Process')
}
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& 'apache-maven-3.9.6\bin\mvn.cmd' spring-boot:run
