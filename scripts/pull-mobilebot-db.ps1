param(
    [string]$PackageName = "com.mobilebot",
    [string]$DatabaseName = "mobilebot.db",
    [string]$OutputDir = "",
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $allArgs = @()
    if ($Serial) {
        $allArgs += @("-s", $Serial)
    }
    $allArgs += $Arguments

    $result = & adb @allArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($allArgs -join ' ')"
    }
    return $result
}

function Test-RemoteFileExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RemotePath
    )

    $allArgs = @()
    if ($Serial) {
        $allArgs += @("-s", $Serial)
    }
    $allArgs += @(
        "shell",
        "run-as",
        $PackageName,
        "ls",
        $RemotePath
    )

    & adb @allArgs 1>$null 2>$null
    return $LASTEXITCODE -eq 0
}

function Export-RemoteFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RemotePath,
        [Parameter(Mandatory = $true)]
        [string]$LocalPath
    )

    $adbPrefix = "adb"
    if ($Serial) {
        $adbPrefix += " -s `"$Serial`""
    }

    $command = "$adbPrefix exec-out run-as $PackageName cat $RemotePath > `"$LocalPath`""
    cmd /c $command | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to export $RemotePath to $LocalPath"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "artifacts\device-db"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Invoke-AdbText -Arguments @("get-state") | Out-Null
Invoke-AdbText -Arguments @("shell", "run-as", $PackageName, "ls", "databases") | Out-Null

$remoteNames = @(
    $DatabaseName,
    "$DatabaseName-wal",
    "$DatabaseName-shm"
)

$pulled = New-Object System.Collections.Generic.List[string]
foreach ($name in $remoteNames) {
    $remotePath = "databases/$name"
    $localPath = Join-Path $OutputDir $name

    if (Test-RemoteFileExists -RemotePath $remotePath) {
        Export-RemoteFile -RemotePath $remotePath -LocalPath $localPath
        $pulled.Add($localPath)
    } elseif (Test-Path $localPath) {
        Remove-Item -Force $localPath
    }
}

if ($pulled.Count -eq 0) {
    throw "No database files were exported from /data/data/$PackageName/databases/"
}

Write-Host "Pulled database files:"
foreach ($file in $pulled) {
    Write-Host " - $file"
}
