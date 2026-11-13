param (
    [string]$provider = "ALL",
    [switch]$detailed,
    [switch]$debug
)

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

function Write-Utf8Line([string]$text) {
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($text + "`n")
        $stdout = [Console]::OpenStandardOutput()
        $stdout.Write($bytes, 0, $bytes.Length)
        $stdout.Flush()
    } catch {
        Write-Output $text
    }
}

$ignoredTitles = @(
    "spotify", "spotify free", "spotify premium",
    "deezer", "deezer - music & podcasts",
    "youtube music", "youtube music desktop app",
    "advertisement", "publicite", "pub", "audio ad"
)

function Test-IgnoredTitle([string]$t) {
    if (-not $t) { return $true }
    $low = $t.Trim().ToLower()
    if ($low -eq "" -or $low -in $ignoredTitles) { return $true }
    if ($low -match "^publicit") { return $true }
    return $false
}

$debugLog = @()
function Add-Dbg([string]$msg) {
    if ($debug) {
        $script:debugLog += "DEBUG: $msg"
    }
}

Add-Dbg "Starting get_song.ps1: provider='$provider', detailed=$detailed, debug=$debug"

function Await-AsyncOp($asyncOp, [Type]$resultType, [int]$timeoutMs = 1500) {
    if (-not $asyncOp) { return $null }

    if ($script:asTask -and $resultType) {
        try {
            $genericTask = $script:asTask.MakeGenericMethod($resultType)
            $netTask = $genericTask.Invoke($null, @($asyncOp))
            if ($netTask.Wait($timeoutMs) -and -not $netTask.IsFaulted -and -not $netTask.IsCanceled) {
                return $netTask.Result
            }
        } catch {
            Add-Dbg "AsTask invocation error: $_"
        }
    }

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

# 1. Primary: WinRT System Media Transport Controls (GSMTC)
try {
    [void][Windows.Foundation.IAsyncInfo, Windows.Foundation, ContentType=WindowsRuntime]
    [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime]
    [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media, ContentType=WindowsRuntime]
    Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue

    $script:asTask = $null
    try {
        $script:asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            $_.Name -eq 'AsTask' -and $_.GetParameters().Length -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } | Select-Object -First 1)
    } catch {
        Add-Dbg "Direct WindowsRuntimeSystemExtensions lookup failed: $_"
    }

    if (-not $script:asTask) {
        try {
            $ext = [System.AppDomain]::CurrentDomain.GetAssemblies() | ForEach-Object {
                try {
                    $_.GetTypes()
                } catch [System.Reflection.ReflectionTypeLoadException] {
                    $_.Types | Where-Object { $_ -ne $null }
                } catch {}
            } | Where-Object { $_ -and $_.Name -eq 'WindowsRuntimeSystemExtensions' } | Select-Object -First 1
            if ($ext) {
                $script:asTask = ($ext.GetMethods() | Where-Object {
                    $_.Name -eq 'AsTask' -and $_.GetParameters().Length -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
                } | Select-Object -First 1)
            }
        } catch {}
    }

    Add-Dbg "AsTask resolved: $($null -ne $script:asTask)"

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

        $browserMusicHint = ""
        try {
            $browserProcs = @(Get-Process -Name "chrome", "msedge", "firefox", "brave", "opera", "vivaldi" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle })
            foreach ($bp in $browserProcs) {
                $wt = $bp.MainWindowTitle
                if ($wt -match "(?i)(?:[-\u2013\u2014|\u2022]\s*Deezer|^Deezer\s*[-\u2013\u2014|\u2022])") { $browserMusicHint = "deezer"; break }
                if ($wt -match "(?i)(?:[-\u2013\u2014|\u2022]\s*Spotify|^Spotify\s*[-\u2013\u2014|\u2022])") { $browserMusicHint = "spotify"; break }
                if ($wt -match "(?i)(?:[-\u2013\u2014|\u2022]\s*YouTube Music)") { $browserMusicHint = "youtubemusic"; break }
            }
        } catch {}

        $matchingSessions = @()
        foreach ($s in $rawSessions) {
            $aId = if ($s.SourceAppUserModelId) { $s.SourceAppUserModelId.ToLower() } else { "" }
            $isMatch = $false
            $detectedTag = $aId

            $isDeezerSession = ($aId -like "*deezer*")
            $isSpotifySession = ($aId -like "*spotify*")
            $isYtmSession = ($aId -like "*youtubemusic*" -or $aId -like "*youtube-music*" -or $aId -like "*youtube_music*" -or $aId -like "*ytmdesktop*" -or $aId -like "*th-ch*" -or $aId -like "*ytm*")
            $isBrowserSession = ($aId -like "*chrome*" -or $aId -like "*msedge*" -or $aId -like "*firefox*" -or $aId -like "*brave*" -or $aId -like "*opera*" -or $aId -like "*vivaldi*" -or $aId -match "^[0-9a-f]{16}$")

            if ($provider -eq "Deezer") {
                if ($isDeezerSession) {
                    $isMatch = $true
                    $detectedTag = "deezer"
                } elseif ($isBrowserSession -and $browserMusicHint -eq "deezer") {
                    $isMatch = $true
                    $detectedTag = "deezer"
                }
            } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
                if ($isYtmSession) {
                    $isMatch = $true
                    $detectedTag = "youtubemusic"
                } elseif ($isBrowserSession -and $browserMusicHint -eq "youtubemusic") {
                    $isMatch = $true
                    $detectedTag = "youtubemusic"
                }
            } elseif ($provider -eq "Spotify") {
                if ($isSpotifySession) {
                    $isMatch = $true
                    $detectedTag = "spotify"
                } elseif ($isBrowserSession -and $browserMusicHint -eq "spotify") {
                    $isMatch = $true
                    $detectedTag = "spotify"
                }
            } else {
                if ($isDeezerSession) {
                    $isMatch = $true
                    $detectedTag = "deezer"
                } elseif ($isSpotifySession) {
                    $isMatch = $true
                    $detectedTag = "spotify"
                } elseif ($isYtmSession) {
                    $isMatch = $true
                    $detectedTag = "youtubemusic"
                } elseif ($isBrowserSession -and $browserMusicHint -ne "") {
                    $isMatch = $true
                    $detectedTag = $browserMusicHint
                }
            }

            if ($isMatch) {
                $st = "Unknown"
                $isPl = $false
                $priority = if ($isDeezerSession -or $isSpotifySession -or $isYtmSession) { 2 } else { 1 }
                try {
                    $pb = $s.GetPlaybackInfo()
                    if ($pb) {
                        $st = $pb.PlaybackStatus.ToString()
                        if ($pb.PlaybackStatus -eq 4 -or $st -eq "Playing") {
                            $isPl = $true
                            $priority += 10
                        }
                    }
                } catch {}
                $matchingSessions += [PSCustomObject]@{
                    Session = $s
                    AppId = $detectedTag
                    RawAppId = $aId
                    Status = $st
                    IsPlaying = $isPl
                    Priority = $priority
                }
                Add-Dbg "Matching WinRT session: AppId='$aId' (tag='$detectedTag'), Status='$st'"
            } else {
                Add-Dbg "Skipping non-target session: AppId='$aId'"
            }
        }

        $matchingSessions = @($matchingSessions | Sort-Object -Property Priority -Descending)

        foreach ($info in $matchingSessions) {
            $session = $info.Session
            $appId = $info.AppId

            $propOp = $session.TryGetMediaPropertiesAsync()
            $props = Await-AsyncOp $propOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]) 1200

            if ($props -and $props.Title -and $props.Title.Trim() -ne "") {
                $songTitle = $props.Title.Trim()
                $artist = if ($props.Artist) { $props.Artist.Trim() } else { "" }

                if (-not (Test-IgnoredTitle $songTitle)) {
                    Add-Dbg "Track detected via WinRT: '$songTitle' by '$artist'"
                    if ($debug) {
                        $debugLog | ForEach-Object { Write-Utf8Line $_ }
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
                        Write-Utf8Line ($appId + "|||" + $status + "|||" + $posMs + "|||" + $artist + "|||" + $songTitle + "|||" + $durMs)
                    } else {
                        if ($artist -ne "") {
                            Write-Utf8Line ($songTitle + " - " + $artist)
                        } else {
                            Write-Utf8Line $songTitle
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

# 2. Fast Targeted Process MainWindowTitle Fallback (avoids querying hung windows across the whole OS)
try {
    Add-Dbg "Starting targeted Process MainWindowTitle fallback..."

    $targetNames = @(
        "Spotify", "Deezer",
        "YouTubeMusic", "youtube-music", "YouTube Music Desktop App", "YouTube Music",
        "chrome", "msedge", "firefox", "brave", "opera", "vivaldi"
    )
    $candidateProcs = @(Get-Process -Name $targetNames -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.Trim() -ne "" })
    Add-Dbg "Candidate processes with MainWindowTitle: $($candidateProcs.Count)"

    $sortedProcs = @($candidateProcs | Sort-Object -Property @{
        Expression = {
            $n = $_.ProcessName.ToLower()
            if ($n -like "*spotify*" -or $n -like "*deezer*" -or $n -like "*youtube*") { 0 } else { 1 }
        }
    })

    foreach ($p in $sortedProcs) {
        try {
            $name = $p.ProcessName.ToLower()
            $title = $p.MainWindowTitle.Trim()

            $cleanTitle = $title -replace '(?i)\s+[-\u2013\u2014]\s+(Google Chrome|Microsoft\u200B?\s*Edge|Brave|Mozilla Firefox|Opera(\s+GX)?|Vivaldi|Zen(\s+Browser)?|Arc|Waterfox)$', ''
            $cleanTitle = $cleanTitle.Trim()

            $matchedProvider = ""
            if ($name -like "*deezer*" -or $cleanTitle -match "(?i)(?:[-\u2013\u2014|\u2022]\s*Deezer$|^Deezer\s*[-\u2013\u2014|\u2022])") {
                $matchedProvider = "deezer"
            } elseif ($name -like "*spotify*" -or $cleanTitle -match "(?i)(?:[-\u2013\u2014|\u2022]\s*Spotify(?:\s+Free|\s+Premium)?$|^Spotify\s*[-\u2013\u2014|\u2022])") {
                $matchedProvider = "spotify"
            } elseif ($name -like "*youtubemusic*" -or $name -like "*youtube-music*" -or $name -like "*youtube music*" -or $cleanTitle -match "(?i)(?:[-\u2013\u2014|\u2022]\s*YouTube Music$)") {
                $matchedProvider = "youtubemusic"
            }

            if ($matchedProvider -eq "") {
                continue
            }

            if ($provider -eq "Deezer" -and $matchedProvider -ne "deezer") { continue }
            if (($provider -eq "YTM" -or $provider -eq "YouTube Music") -and $matchedProvider -ne "youtubemusic") { continue }
            if ($provider -eq "Spotify" -and $matchedProvider -ne "spotify") { continue }

            $clean = $cleanTitle `
                -replace '(?i)\s+[-\u2013\u2014|\u2022]\s*Deezer$', '' `
                -replace '(?i)^Deezer\s*[-\u2013\u2014|\u2022]\s+', '' `
                -replace '(?i)\s+[-\u2013\u2014|\u2022]\s*YouTube Music$', '' `
                -replace '(?i)\s+[-\u2013\u2014|\u2022]\s*Spotify(?:\s+Free|\s+Premium)?$', '' `
                -replace '(?i)^Spotify(?:\s+Free|\s+Premium)?\s*[-\u2013\u2014|\u2022]\s+', ''
            $clean = $clean.Trim()

            if (-not (Test-IgnoredTitle $clean)) {
                Add-Dbg "Found process match: Process='$name', Provider='$matchedProvider', WindowTitle='$title', Clean='$clean'"
                if ($debug) {
                    $debugLog | ForEach-Object { Write-Utf8Line $_ }
                }

                $art = ""
                $ttl = $clean
                if ($clean -match "^(.+?)\s+\u2022\s+(.+)$") {
                    $ttl = $Matches[1].Trim()
                    $art = $Matches[2].Trim()
                } elseif ($clean -match "^(.+?)\s+[-\u2013\u2014]\s+(.+)$") {
                    if ($matchedProvider -eq "spotify") {
                        $art = $Matches[1].Trim()
                        $ttl = $Matches[2].Trim()
                    } else {
                        $ttl = $Matches[1].Trim()
                        $art = $Matches[2].Trim()
                    }
                }

                if ($detailed) {
                    Write-Utf8Line ($matchedProvider + "|||Playing|||-1|||" + $art + "|||" + $ttl + "|||0")
                } else {
                    if ($art -ne "") {
                        Write-Utf8Line ($ttl + " - " + $art)
                    } else {
                        Write-Utf8Line $ttl
                    }
                }
                exit 0
            } else {
                Add-Dbg "Ignored process title: '$clean'"
            }
        } catch {}
    }
} catch {
    Add-Dbg "Exception in Process fallback: $_"
}

if ($debug) {
    Add-Dbg "No track detected."
    $debugLog | ForEach-Object { Write-Utf8Line $_ }
}
