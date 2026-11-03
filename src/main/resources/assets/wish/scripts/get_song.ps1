param (
    [string]$provider = "ALL",
    [switch]$detailed
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

[void][Windows.Foundation.IAsyncInfo, Windows.Foundation, ContentType=WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media, ContentType=WindowsRuntime]
Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Length -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }[0]

# 1. Primary: Check WinRT System Media Transport Controls for target provider
try {
    $op = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
    $task = $asTask.MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]).Invoke($null, @($op))
    [void]$task.Wait(2000)
    $mgr = $task.Result

    if ($mgr) {
        $sessions = @($mgr.GetCurrentSession()) + @($mgr.GetSessions())
        foreach ($session in $sessions) {
            if ($session) {
                try {
                    $appId = if ($session.SourceAppUserModelId) { $session.SourceAppUserModelId.ToLower() } else { "" }
                    $isMatch = $false

                    if ($provider -eq "Deezer") {
                        if ($appId -like "*deezer*") { $isMatch = $true }
                    } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
                        if ($appId -like "*youtubemusic*" -or $appId -like "*youtube-music*") { $isMatch = $true }
                    } elseif ($provider -eq "Spotify") {
                        if ($appId -like "*spotify*") { $isMatch = $true }
                    } else {
                        if ($appId -like "*deezer*" -or $appId -like "*youtubemusic*" -or $appId -like "*youtube-music*" -or $appId -like "*spotify*") {
                            $isMatch = $true
                        }
                    }

                    if ($isMatch) {
                        $op2 = $session.TryGetMediaPropertiesAsync()
                        $task2 = $asTask.MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]).Invoke($null, @($op2))
                        [void]$task2.Wait(2000)
                        $props = $task2.Result
                        if ($props -and $props.Title -and $props.Title.Trim() -ne "") {
                            $artist = if ($props.Artist) { $props.Artist.Trim() } else { "" }
                            $songTitle = $props.Title.Trim()
                            if ($detailed) {
                                $status = "Playing"
                                try {
                                    $pb = $session.GetPlaybackInfo()
                                    if ($pb -and $pb.PlaybackStatus -ne 4 -and $pb.PlaybackStatus.ToString() -ne "Playing") {
                                        $status = "Paused"
                                    }
                                } catch {}
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
                        }
                    }
                } catch {}
            }
        }
    }
} catch {}

# 2. Process MainWindowTitle Fallback for target provider
try {
    $procs = Get-Process
    foreach ($p in $procs) {
        try {
            $name = $p.ProcessName.ToLower()
            $title = $p.MainWindowTitle
            if ($title -and $title.Trim() -ne "") {
                $isProcMatch = $false
                if ($provider -eq "Deezer") {
                    if ($name -eq "deezer" -or $title -like "* - Deezer*") { $isProcMatch = $true }
                } elseif ($provider -eq "YTM" -or $provider -eq "YouTube Music") {
                    if ($name -like "*youtubemusic*" -or $name -like "*youtube-music*" -or $name -like "*youtube music*" -or $title -like "* - YouTube Music*") { $isProcMatch = $true }
                } elseif ($provider -eq "Spotify") {
                    if ($name -eq "spotify" -or $title -like "* - Spotify*") { $isProcMatch = $true }
                } else {
                    if ($name -eq "deezer" -or $name -eq "youtubemusic" -or $name -eq "spotify" -or $title -like "* - Deezer*" -or $title -like "* - YouTube Music*" -or $title -like "* - Spotify*") { $isProcMatch = $true }
                }

                if ($isProcMatch) {
                    $clean = $title -replace ' - Deezer$', '' -replace ' - YouTube Music$', '' -replace ' - Spotify$', ''
                    if ($clean -and $clean.Trim() -ne "" -and $clean -ne "Deezer" -and $clean -ne "YouTube Music" -and $clean -ne "Spotify" -and $clean -notlike "*YouTube Music Desktop App*") {
                        if ($detailed) {
                            $art = ""
                            $ttl = $clean.Trim()
                            if ($ttl -match "^(.+?)\s+-\s+(.+)$") {
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
                    }
                }
            }
        } catch {}
    }
} catch {}
