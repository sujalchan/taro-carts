$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$originalPlacesKey = [Environment]::GetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", "Process")
$placesApiKey = $originalPlacesKey
if ([string]::IsNullOrWhiteSpace($placesApiKey)) {
    $envFile = Join-Path $root ".env"
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ([string]::IsNullOrWhiteSpace($placesApiKey) -and $line -match '^\s*GOOGLE_MAPS_BROWSER_API_KEY\s*=\s*(.*?)\s*$') {
                $placesApiKey = $Matches[1].Trim('"', "'")
            }
        }
    }
}

$services = @(
    @{ Name = "Login Service"; Directory = "login-service"; Port = 8085; ApplicationClass = "nz.ac.aut.comp713.login_service.LoginServiceApplication" },
    @{ Name = "Customer Service"; Directory = "customer-service"; Port = 8081; ApplicationClass = "nz.ac.aut.comp713.customer_service.CustomerServiceApplication" },
    @{ Name = "Dispatcher Service"; Directory = "dispatcher-service"; Port = 8084; ApplicationClass = "nz.ac.aut.comp713.dispatcher_service.DispatcherServiceApplication" },
    @{ Name = "Taro Service"; Directory = "taro-service"; Port = 8083; ApplicationClass = "nz.ac.aut.comp713.taro_service.TaroServiceApplication" },
    @{ Name = "Allocation Service"; Directory = "allocation-service"; Port = 8082; ApplicationClass = "nz.ac.aut.comp713.allocation_service.AllocationServiceApplication" }
)

$startedCount = 0
try {
    foreach ($service in $services) {
        $workingDirectory = Join-Path $root $service.Directory
        $wrapper = Join-Path $workingDirectory "mvnw.cmd"
        if (-not (Test-Path -LiteralPath $wrapper)) {
            throw "Could not find Maven wrapper: $wrapper"
        }

        $listeners = @(Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue)
        if ($listeners.Count -gt 0) {
            $expectedServiceIsRunning = $false
            foreach ($processId in @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
                $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
                if ($processInfo.CommandLine -like "*$($service.ApplicationClass)*") {
                    $expectedServiceIsRunning = $true
                    break
                }
            }
            if ($expectedServiceIsRunning) {
                Write-Host "$($service.Name) is already running on http://localhost:$($service.Port); skipping."
                continue
            }
            throw "Port $($service.Port) is in use by another process; cannot start $($service.Name)."
        }

        # Only browser services with address autocomplete inherit the Maps API key.
        [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $null, "Process")
        if ($service.Directory -eq "customer-service" -or $service.Directory -eq "dispatcher-service" -or $service.Directory -eq "login-service") {
            if (-not [string]::IsNullOrWhiteSpace($placesApiKey)) {
                [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $placesApiKey, "Process")
            }
        }

        Start-Process -FilePath "cmd.exe" `
            -ArgumentList @("/k", "mvnw.cmd spring-boot:run") `
            -WorkingDirectory $workingDirectory
        $startedCount++
        Write-Host "Started $($service.Name) on http://localhost:$($service.Port)"
    }
} finally {
    [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $originalPlacesKey, "Process")
}

if ($startedCount -gt 0) {
    Write-Host "New services are running in Command Prompt windows. Close those windows to stop them."
} else {
    Write-Host "All services were already running."
}
