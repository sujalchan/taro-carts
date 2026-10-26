$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$services = @(
    @{ Name = "Customer Service"; Directory = "customer-service"; Port = 8081 },
    @{ Name = "Taro Service"; Directory = "taro-service"; Port = 8083 },
    @{ Name = "Allocation Service"; Directory = "allocation-service"; Port = 8082 },
    @{ Name = "Login Service"; Directory = "login-service"; Port = 8084 }
)

foreach ($service in $services) {
    $workingDirectory = Join-Path $root $service.Directory
    $wrapper = Join-Path $workingDirectory "mvnw.cmd"
    if (-not (Test-Path -LiteralPath $wrapper)) {
        throw "Could not find Maven wrapper: $wrapper"
    }

    Start-Process -FilePath "cmd.exe" `
        -ArgumentList @("/k", "mvnw.cmd spring-boot:run") `
        -WorkingDirectory $workingDirectory
    Write-Host "Started $($service.Name) on http://localhost:$($service.Port)"
}

Write-Host "Each service is running in its own Command Prompt window. Close those windows to stop the services."
