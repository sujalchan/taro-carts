$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$originalPlacesKey = [Environment]::GetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", "Process")
$originalServerPlacesKey = [Environment]::GetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", "Process")
$placesApiKey = $originalPlacesKey
$serverPlacesApiKey = $originalServerPlacesKey
if ([string]::IsNullOrWhiteSpace($placesApiKey) -or [string]::IsNullOrWhiteSpace($serverPlacesApiKey)) {
    $envFile = Join-Path $root ".env"
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ([string]::IsNullOrWhiteSpace($placesApiKey) -and $line -match '^\s*GOOGLE_MAPS_BROWSER_API_KEY\s*=\s*(.*?)\s*$') {
                $placesApiKey = $Matches[1].Trim('"', "'")
            } elseif ([string]::IsNullOrWhiteSpace($serverPlacesApiKey) -and $line -match '^\s*GOOGLE_MAPS_SERVER_API_KEY\s*=\s*(.*?)\s*$') {
                $serverPlacesApiKey = $Matches[1].Trim('"', "'")
            }
        }
    }
}

$services = @(
    @{ Name = "Customer Service"; Directory = "customer-service"; Port = 8081 },
    @{ Name = "Dispatcher Service"; Directory = "dispatcher-service"; Port = 8084 },
    @{ Name = "Taro Service"; Directory = "taro-service"; Port = 8083 },
    @{ Name = "Allocation Service"; Directory = "allocation-service"; Port = 8082 }
)

try {
    foreach ($service in $services) {
        $workingDirectory = Join-Path $root $service.Directory
        $wrapper = Join-Path $workingDirectory "mvnw.cmd"
        if (-not (Test-Path -LiteralPath $wrapper)) {
            throw "Could not find Maven wrapper: $wrapper"
        }

        # Only customer and dispatcher services inherit the Maps API keys.
        [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $null, "Process")
        [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $null, "Process")
        if ($service.Directory -eq "customer-service" -or $service.Directory -eq "dispatcher-service") {
            if (-not [string]::IsNullOrWhiteSpace($placesApiKey)) {
                [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $placesApiKey, "Process")
            }
            if (-not [string]::IsNullOrWhiteSpace($serverPlacesApiKey)) {
                [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $serverPlacesApiKey, "Process")
            }
        }

        Start-Process -FilePath "cmd.exe" `
            -ArgumentList @("/k", "mvnw.cmd spring-boot:run") `
            -WorkingDirectory $workingDirectory
        Write-Host "Started $($service.Name) on http://localhost:$($service.Port)"
    }
} finally {
    [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $originalPlacesKey, "Process")
    [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $originalServerPlacesKey, "Process")
}

Write-Host "Each service is running in its own Command Prompt window. Close those windows to stop the services."
