param (
    [string]$provider = "ALL",
    [switch]$detailed,
    [switch]$debug
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ignoredTitles = @("spotify", "spotify free", "spotify premium", "deezer", "youtube music", "youtube music desktop app", "advertisement", "publicité", "pub")

$debugLog = @()
function Add-Dbg($msg) {
    if ($debug) {
        $script:debugLog += "DEBUG: $msg"
    }
}

Add-Dbg "Starting get_song.ps1: provider='$provider', detailed=$detailed, debug=$debug"

# Helper to await WinRT async operations safely without blocking or throwing unhandled exceptions
function Await-AsyncOp($asyncOp, [Type]$resultType, [int]$timeoutMs = 1500) {
    if (-not $asyncOp) { return $null }

    # Attempt 1: AsTask extension method if reflection succeeded
    if ($script:asTask -and $resultType) {
        try {
            $task = $script:asTask.MakeGenericMethod($resultType).Invoke($null, @($asyncOp))
            if ($task.Wait($timeoutMs) -and -not $task.IsFaulted -and -not $task.IsCanceled) {
                return $task.Result
            }
        } catch {
            Add-Dbg "AsTask invocation error: $_"
        }
    }

    # Attempt 2: Direct polling of IAsyncInfo.Status and GetResults() (pure WinRT COM)
    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        while ($asyncOp.Status -eq 0 -and $sw.ElapsedMilliseconds -lt $timeoutMs) {
            Start-Sleep -Milliseconds 20
        }
        if ($asyncOp.Status -eq 1) {
            return $asyncOp.GetResults()
        }
    } catch {
        Add-Dbg "Async status polling error: $_"
    }

    return $null
}

# 1. Primary: Check WinRT System Media Transport Controls (GSMTC)
try {
    [void][Windows.Foundation.IAsyncInfo, Windows.Foundation, ContentType=WindowsRuntime]
    [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime]
    [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media, ContentType=WindowsRuntime]
    Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue

    $script:asTask = $null
    try {
        $ext = [System.AppDomain]::CurrentDomain.GetAssemblies() | ForEach-Object { 
            try { $_.GetTypes() } catch {} 
        } | Where-Object { $_.Name -eq 'WindowsRuntimeSystemExtensions' } | Select-Object -First 1
        if ($ext) {
            $script:asTask = $ext.GetMethods() | Where-Object { 
                $_.Name -eq 'AsTask' -and $_.GetParameters().Length -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' 
            } | Select-Object -First 1
        }
    } catch {}

    $mgrOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
    $mgr = Await-AsyncOp $mgrOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]) 1500

    if ($mgr) {
        Add-Dbg "WinRT Manager acquired successfully."
        $curr = $mgr.GetCurrentSession()
        $all = @($mgr.GetSessions())
        Add-Dbg "Total WinRT sessions: $($all.Count)"

        $rawSessions = @()
        if ($curr) { $rawSessions += $curr }
        foreach ($s in $all) {
            if ($s -and ($null -eq $curr -or $s.SourceAppUserModelId -ne $curr.SourceAppUserModelId)) {
                $rawSessions += $s
            }
        }

        # Filter by provider first to prevent querying unresponsive non-target sessions
        $matchingSessions = @()
        foreach ($s in $rawSessions) {
            $aId = if ($s.SourceAppUserModelId) { $s.SourceAppUserModelId.ToLower() } else { "" }
            $isMatch = $false

            if ($provider -eq "Deezer") {
                if ($aId -like "*deezer*") { $isMatch = $true }
            } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
                if ($aId -like "*youtubemusic*" -or $aId -like "*youtube-music*") { $isMatch = $true }
            } elseif ($provider -eq "Spotify") {
                if ($aId -like "*spotify*") { $isMatch = $true }
            } else {
                if ($aId -like "*deezer*" -or $aId -like "*youtubemusic*" -or $aId -like "*youtube-music*" -or $aId -like "*spotify*") {
                    $isMatch = $true
                }
            }

            if ($isMatch) {
                $st = "Unknown"
                $isPl = $false
                try {
                    $pb = $s.GetPlaybackInfo()
                    if ($pb) {
                        $st = $pb.PlaybackStatus.ToString()
                        if ($pb.PlaybackStatus -eq 4 -or $st -eq "Playing") {
                            $isPl = $true
                        }
                    }
                } catch {}
                $matchingSessions += [PSCustomObject]@{
                    Session = $s
                    AppId = $aId
                    Status = $st
                    IsPlaying = $isPl
                }
                Add-Dbg "Matching WinRT session: AppId='$aId', Status='$st'"
            } else {
                Add-Dbg "Skipping non-target session: AppId='$aId'"
            }
        }

        # Sort so Playing sessions come first
        $matchingSessions = $matchingSessions | Sort-Object -Property IsPlaying -Descending

        foreach ($info in $matchingSessions) {
            $session = $info.Session
            $appId = $info.AppId

            $propOp = $session.TryGetMediaPropertiesAsync()
            $props = Await-AsyncOp $propOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]) 1200

            if ($props -and $props.Title -and $props.Title.Trim() -ne "") {
                $songTitle = $props.Title.Trim()
                $artist = if ($props.Artist) { $props.Artist.Trim() } else { "" }

                if ($songTitle.ToLower() -notin $ignoredTitles) {
                    Add-Dbg "Track detected via WinRT: '$songTitle' by '$artist'"
                    if ($debug) {
                        $debugLog | ForEach-Object { Write-Output $_ }
                    }
                    if ($detailed) {
                        $status = if ($info.IsPlaying) { "Playing" } else { "Paused" }
                        $posMs = -1
                        $durMs = 0
                        try {
                            $tl = $session.GetTimelineProperties()
                            if ($tl) {
                                $posMs = [long]$tl.Position.TotalMilliseconds
                                $durMs = [long]$tl.EndTime.TotalMilliseconds
                                if ($status -eq "Playing" -and $tl.LastUpdatedTime -and $tl.LastUpdatedTime.Year -gt 2000) {
                                    $elapsed = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds
                                    if ($elapsed -gt 0 -and $elapsed -lt 600000) {
                                        $posMs += [long]$elapsed
                                    }
                                }
                            }
                        } catch {}
                        Write-Output ($appId + "|||" + $status + "|||" + $posMs + "|||" + $artist + "|||" + $songTitle + "|||" + $durMs)
                    } else {
                        if ($artist -ne "") {
                            Write-Output ($songTitle + " - " + $artist)
                        } else {
                            Write-Output $songTitle
                        }
                    }
                    exit 0
                } else {
                    Add-Dbg "Ignored placeholder title: '$songTitle'"
                }
            }
        }
    } else {
        Add-Dbg "WinRT Manager is null (GSMTC not available or timed out)."
    }
} catch {
    Add-Dbg "Exception in WinRT check: $_"
}

# 2. Process MainWindowTitle Fallback
try {
    Add-Dbg "Starting Process MainWindowTitle fallback..."
    
    $targetProcessNames = @()
    if ($provider -eq "Deezer") {
        $targetProcessNames = @("deezer")
    } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
        $targetProcessNames = @("youtubemusic", "youtube-music")
    } elseif ($provider -eq "Spotify") {
        $targetProcessNames = @("spotify")
    } else {
        $targetProcessNames = @("spotify", "deezer", "youtubemusic", "youtube-music")
    }

    $procs = @()
    foreach ($pName in $targetProcessNames) {
        $procs += @(Get-Process -Name $pName -ErrorAction SilentlyContinue)
    }

    if ($procs.Count -eq 0 -and $provider -eq "ALL") {
        $procs = @(Get-Process -ErrorAction SilentlyContinue)
    }

    $spotifyCount = ($procs | Where-Object { $_.ProcessName -like "*spotify*" }).Count
    Add-Dbg "Found $($procs.Count) target processes. Spotify processes: $spotifyCount"

    foreach ($p in $procs) {
        try {
            $name = $p.ProcessName.ToLower()
            $title = $p.MainWindowTitle
            if ($title -and $title.Trim() -ne "") {
                $isProcMatch = $false
                if ($provider -eq "Deezer") {
                    if ($name -eq "deezer" -or $title -like "* - Deezer*") { $isProcMatch = $true }
                } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
                    if ($name -like "*youtubemusic*" -or $name -like "*youtube-music*" -or $title -like "* - YouTube Music*") { $isProcMatch = $true }
                } elseif ($provider -eq "Spotify") {
                    if ($name -eq "spotify" -or $title -like "* - Spotify*") { $isProcMatch = $true }
                } else {
                    if ($name -eq "deezer" -or $name -like "*youtubemusic*" -or $name -eq "spotify" -or $title -like "* - Deezer*" -or $title -like "* - YouTube Music*" -or $title -like "* - Spotify*") { $isProcMatch = $true }
                }

                if ($isProcMatch) {
                    $clean = $title -replace ' - Deezer$', '' -replace ' - YouTube Music$', '' -replace ' - Spotify$', ''
                    $clean = $clean.Trim()
                    if ($clean -and $clean.ToLower() -notin $ignoredTitles) {
                        Add-Dbg "Found process match: Process='$name', WindowTitle='$title', Clean='$clean'"
                        if ($debug) {
                            $debugLog | ForEach-Object { Write-Output $_ }
                        }
                        if ($detailed) {
                            $art = ""
                            $ttl = $clean
                            if ($ttl -match "^(.+?)\s+[-–—]\s+(.+)$") {
                                if ($name -like "*spotify*") {
                                    $art = $Matches[1].Trim()
                                    $ttl = $Matches[2].Trim()
                                } else {
                                    $ttl = $Matches[1].Trim()
                                    $art = $Matches[2].Trim()
                                }
                            }
                            Write-Output ($name + "|||Playing|||-1|||" + $art + "|||" + $ttl + "|||0")
                        } else {
                            Write-Output $clean
                        }
                        exit 0
                    } else {
                        Add-Dbg "Ignored process title: '$clean'"
                    }
                }
            }
        } catch {}
    }
} catch {
    Add-Dbg "Exception in Process fallback: $_"
}

if ($debug) {
    Add-Dbg "No track detected."
    $debugLog | ForEach-Object { Write-Output $_ }
}
