$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$originalPlacesKey = [Environment]::GetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", "Process")
$originalServerMapsKey = [Environment]::GetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", "Process")
$originalLoginServiceUrl = [Environment]::GetEnvironmentVariable("LOGIN_SERVICE_URL", "Process")
$loginServiceUrl = if ([string]::IsNullOrWhiteSpace($originalLoginServiceUrl)) { "http://localhost:8085" } else { $originalLoginServiceUrl }
$originalSessionLifetime = [Environment]::GetEnvironmentVariable("LOGIN_SESSION_LIFETIME", "Process")
$sessionLifetime = if ([string]::IsNullOrWhiteSpace($originalSessionLifetime)) { "PT8H" } else { $originalSessionLifetime }
$originalCookieSecure = [Environment]::GetEnvironmentVariable("LOGIN_COOKIE_SECURE", "Process")
$cookieSecure = if ([string]::IsNullOrWhiteSpace($originalCookieSecure)) { "false" } else { $originalCookieSecure }
$originalCustomerSecret = [Environment]::GetEnvironmentVariable("LOGIN_CUSTOMER_PROVISIONING_SECRET", "Process")
$originalDispatcherSecret = [Environment]::GetEnvironmentVariable("LOGIN_DISPATCHER_PROVISIONING_SECRET", "Process")
$placesApiKey = $originalPlacesKey
$serverMapsKey = $originalServerMapsKey
$customerSecret = $originalCustomerSecret
$dispatcherSecret = $originalDispatcherSecret
$secretNames = @(
    "LOGIN_CUSTOMER_VALIDATION_SECRET", "LOGIN_DISPATCHER_VALIDATION_SECRET",
    "LOGIN_ALLOCATION_VALIDATION_SECRET", "LOGIN_TARO_VALIDATION_SECRET",
    "ALLOCATION_CUSTOMER_REFERENCE_SECRET", "ALLOCATION_DISPATCHER_REFERENCE_SECRET",
    "ALLOCATION_TARO_REFERENCE_SECRET",
    "ALLOCATION_CUSTOMER_ASSERTION_SECRET", "ALLOCATION_DISPATCHER_ASSERTION_SECRET"
)
$originalSecrets = @{}
$serviceSecrets = @{}
foreach ($secretName in $secretNames) {
    $originalSecrets[$secretName] = [Environment]::GetEnvironmentVariable($secretName, "Process")
    $serviceSecrets[$secretName] = $originalSecrets[$secretName]
}
$envFile = Join-Path $root ".env"
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ([string]::IsNullOrWhiteSpace($placesApiKey) -and $line -match '^\s*GOOGLE_MAPS_BROWSER_API_KEY\s*=\s*(.*?)\s*$') {
            $placesApiKey = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($serverMapsKey) -and $line -match '^\s*GOOGLE_MAPS_SERVER_API_KEY\s*=\s*(.*?)\s*$') {
            $serverMapsKey = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($originalLoginServiceUrl) -and $line -match '^\s*LOGIN_SERVICE_URL\s*=\s*(.*?)\s*$') {
            $loginServiceUrl = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($originalSessionLifetime) -and $line -match '^\s*LOGIN_SESSION_LIFETIME\s*=\s*(.*?)\s*$') {
            $sessionLifetime = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($originalCookieSecure) -and $line -match '^\s*LOGIN_COOKIE_SECURE\s*=\s*(.*?)\s*$') {
            $cookieSecure = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($customerSecret) -and $line -match '^\s*LOGIN_CUSTOMER_PROVISIONING_SECRET\s*=\s*(.*?)\s*$') {
            $customerSecret = $Matches[1].Trim('"', "'")
        }
        if ([string]::IsNullOrWhiteSpace($dispatcherSecret) -and $line -match '^\s*LOGIN_DISPATCHER_PROVISIONING_SECRET\s*=\s*(.*?)\s*$') {
            $dispatcherSecret = $Matches[1].Trim('"', "'")
        }
        foreach ($secretName in $secretNames) {
            $match = [regex]::Match($line, '^\s*' + [regex]::Escape($secretName) + '\s*=\s*(.*?)\s*$')
            if ([string]::IsNullOrWhiteSpace($serviceSecrets[$secretName]) -and $match.Success) {
                $serviceSecrets[$secretName] = $match.Groups[1].Value.Trim('"', "'")
            }
        }
    }
}
function New-ProvisioningSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace("-", "").ToLowerInvariant()
}
$generatedProvisioningSecret = $false
if ([string]::IsNullOrWhiteSpace($customerSecret)) {
    $customerSecret = New-ProvisioningSecret
    $generatedProvisioningSecret = $true
}
if ([string]::IsNullOrWhiteSpace($dispatcherSecret)) {
    $dispatcherSecret = New-ProvisioningSecret
    $generatedProvisioningSecret = $true
}
if ($customerSecret.Length -lt 32 -or $dispatcherSecret.Length -lt 32) {
    throw "Provisioning secrets must contain at least 32 characters."
}
if ($customerSecret -ceq $dispatcherSecret) {
    throw "Customer and dispatcher provisioning secrets must be different."
}
$generatedSecrets = @()
$seenSecrets = @($customerSecret, $dispatcherSecret)
foreach ($secretName in $secretNames) {
    if ([string]::IsNullOrWhiteSpace($serviceSecrets[$secretName])) {
        $serviceSecrets[$secretName] = New-ProvisioningSecret
        $generatedSecrets += $secretName
    }
    if ($serviceSecrets[$secretName].Length -lt 32) {
        throw "$secretName must contain at least 32 characters."
    }
    if ($seenSecrets -ccontains $serviceSecrets[$secretName]) {
        throw "All internal service secrets must be different."
    }
    $seenSecrets += $serviceSecrets[$secretName]
}

$services = @(
    @{ Name = "Login Service"; Directory = "login-service"; Port = 8085; ApplicationClass = "nz.ac.aut.comp713.login_service.LoginServiceApplication" },
    @{ Name = "Customer Service"; Directory = "customer-service"; Port = 8081; ApplicationClass = "nz.ac.aut.comp713.customer_service.CustomerServiceApplication" },
    @{ Name = "Dispatcher Service"; Directory = "dispatcher-service"; Port = 8084; ApplicationClass = "nz.ac.aut.comp713.dispatcher_service.DispatcherServiceApplication" },
    @{ Name = "Taro Service"; Directory = "taro-service"; Port = 8083; ApplicationClass = "nz.ac.aut.comp713.taro_service.TaroServiceApplication" },
    @{ Name = "Allocation Service"; Directory = "allocation-service"; Port = 8082; ApplicationClass = "nz.ac.aut.comp713.allocation_service.AllocationServiceApplication" }
)

if ($generatedProvisioningSecret -or $generatedSecrets.Count -gt 0) {
    foreach ($service in $services) {
        if (@(Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
            throw "A service is already listening on port $($service.Port). Set stable secrets in .env and restart the full stack before a partial launch."
        }
    }
}

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
        [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $null, "Process")
        [Environment]::SetEnvironmentVariable("LOGIN_SERVICE_URL", $loginServiceUrl, "Process")
        [Environment]::SetEnvironmentVariable("LOGIN_SESSION_LIFETIME", $sessionLifetime, "Process")
        [Environment]::SetEnvironmentVariable("LOGIN_COOKIE_SECURE", $cookieSecure, "Process")
        if ($service.Directory -eq "customer-service" -or $service.Directory -eq "dispatcher-service" -or $service.Directory -eq "login-service") {
            if (-not [string]::IsNullOrWhiteSpace($placesApiKey)) {
                [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $placesApiKey, "Process")
            }
        }
        [Environment]::SetEnvironmentVariable("LOGIN_CUSTOMER_PROVISIONING_SECRET", $null, "Process")
        [Environment]::SetEnvironmentVariable("LOGIN_DISPATCHER_PROVISIONING_SECRET", $null, "Process")
        foreach ($secretName in $secretNames) {
            [Environment]::SetEnvironmentVariable($secretName, $null, "Process")
        }
        if ($service.Directory -eq "login-service" -or $service.Directory -eq "customer-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_CUSTOMER_PROVISIONING_SECRET", $customerSecret, "Process")
        }
        if ($service.Directory -eq "login-service" -or $service.Directory -eq "dispatcher-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_DISPATCHER_PROVISIONING_SECRET", $dispatcherSecret, "Process")
        }
        if ($service.Directory -eq "login-service") {
            if (-not [string]::IsNullOrWhiteSpace($serverMapsKey)) {
                [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $serverMapsKey, "Process")
            }
            foreach ($secretName in @("LOGIN_CUSTOMER_VALIDATION_SECRET", "LOGIN_DISPATCHER_VALIDATION_SECRET", "LOGIN_ALLOCATION_VALIDATION_SECRET", "LOGIN_TARO_VALIDATION_SECRET")) {
                [Environment]::SetEnvironmentVariable($secretName, $serviceSecrets[$secretName], "Process")
            }
        }
        if ($service.Directory -eq "customer-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_CUSTOMER_VALIDATION_SECRET", $serviceSecrets["LOGIN_CUSTOMER_VALIDATION_SECRET"], "Process")
            [Environment]::SetEnvironmentVariable("ALLOCATION_CUSTOMER_REFERENCE_SECRET", $serviceSecrets["ALLOCATION_CUSTOMER_REFERENCE_SECRET"], "Process")
            [Environment]::SetEnvironmentVariable("ALLOCATION_CUSTOMER_ASSERTION_SECRET", $serviceSecrets["ALLOCATION_CUSTOMER_ASSERTION_SECRET"], "Process")
        }
        if ($service.Directory -eq "dispatcher-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_DISPATCHER_VALIDATION_SECRET", $serviceSecrets["LOGIN_DISPATCHER_VALIDATION_SECRET"], "Process")
            [Environment]::SetEnvironmentVariable("ALLOCATION_DISPATCHER_REFERENCE_SECRET", $serviceSecrets["ALLOCATION_DISPATCHER_REFERENCE_SECRET"], "Process")
            [Environment]::SetEnvironmentVariable("ALLOCATION_DISPATCHER_ASSERTION_SECRET", $serviceSecrets["ALLOCATION_DISPATCHER_ASSERTION_SECRET"], "Process")
        }
        if ($service.Directory -eq "allocation-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_ALLOCATION_VALIDATION_SECRET", $serviceSecrets["LOGIN_ALLOCATION_VALIDATION_SECRET"], "Process")
            foreach ($secretName in @("ALLOCATION_CUSTOMER_REFERENCE_SECRET", "ALLOCATION_DISPATCHER_REFERENCE_SECRET", "ALLOCATION_TARO_REFERENCE_SECRET")) {
                [Environment]::SetEnvironmentVariable($secretName, $serviceSecrets[$secretName], "Process")
            }
            foreach ($secretName in @("ALLOCATION_CUSTOMER_ASSERTION_SECRET", "ALLOCATION_DISPATCHER_ASSERTION_SECRET")) {
                [Environment]::SetEnvironmentVariable($secretName, $serviceSecrets[$secretName], "Process")
            }
        }
        if ($service.Directory -eq "taro-service") {
            [Environment]::SetEnvironmentVariable("LOGIN_TARO_VALIDATION_SECRET", $serviceSecrets["LOGIN_TARO_VALIDATION_SECRET"], "Process")
            [Environment]::SetEnvironmentVariable("ALLOCATION_TARO_REFERENCE_SECRET", $serviceSecrets["ALLOCATION_TARO_REFERENCE_SECRET"], "Process")
        }

        Start-Process -FilePath "cmd.exe" `
            -ArgumentList @("/k", "mvnw.cmd spring-boot:run") `
            -WorkingDirectory $workingDirectory
        $startedCount++
        Write-Host "Started $($service.Name) on http://localhost:$($service.Port)"
    }
} finally {
    [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_BROWSER_API_KEY", $originalPlacesKey, "Process")
    [Environment]::SetEnvironmentVariable("GOOGLE_MAPS_SERVER_API_KEY", $originalServerMapsKey, "Process")
    [Environment]::SetEnvironmentVariable("LOGIN_SERVICE_URL", $originalLoginServiceUrl, "Process")
    [Environment]::SetEnvironmentVariable("LOGIN_SESSION_LIFETIME", $originalSessionLifetime, "Process")
    [Environment]::SetEnvironmentVariable("LOGIN_COOKIE_SECURE", $originalCookieSecure, "Process")
    [Environment]::SetEnvironmentVariable("LOGIN_CUSTOMER_PROVISIONING_SECRET", $originalCustomerSecret, "Process")
    [Environment]::SetEnvironmentVariable("LOGIN_DISPATCHER_PROVISIONING_SECRET", $originalDispatcherSecret, "Process")
    foreach ($secretName in $secretNames) {
        [Environment]::SetEnvironmentVariable($secretName, $originalSecrets[$secretName], "Process")
    }
}

if ($startedCount -gt 0) {
    Write-Host "New services are running in Command Prompt windows. Close those windows to stop them."
} else {
    Write-Host "All services were already running."
}
