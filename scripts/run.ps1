<#
.SYNOPSIS
    Starts VSMS on Windows (PowerShell) without putting any password in a file.

.DESCRIPTION
    Asks for the database password and, on the very first start, the password for the initial
    administrator account. Both exist only in this PowerShell window while the application runs.

        powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1

    Optional switches:
        -NoSampleData   do not load the fictional demo data
        -ResetAdmin     reset the administrator password (use when it was forgotten)
        -SkipTests      (default) the application is started with the Maven wrapper, tests are not run
#>
param(
    [string]$DbUser = "vsms_user",
    [string]$DbName = "vsms_db",
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [int]$Port = 8080,
    [string]$AdminUser = "admin",
    [switch]$NoSampleData,
    [switch]$ResetAdmin
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

function Read-PlainPassword([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java 21 was not found. Install it first (see README.md, step 1)."
}

if (-not $env:VSMS_DB_PASSWORD) { $env:VSMS_DB_PASSWORD = Read-PlainPassword "Password of the MySQL user '$DbUser'" }
$env:VSMS_DB_USERNAME = $DbUser
$env:VSMS_DB_URL = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
$env:VSMS_PORT = "$Port"
$env:VSMS_ADMIN_USERNAME = $AdminUser

if ($ResetAdmin) {
    $env:VSMS_ADMIN_PASSWORD = Read-PlainPassword "NEW password for the administrator '$AdminUser' (8+ characters, letters and digits)"
    $env:VSMS_ADMIN_RESET = "true"
}
elseif (-not $env:VSMS_ADMIN_PASSWORD) {
    $answer = Read-Host "Choose the administrator password now? (Enter = no, a random one will be printed once in this window)"
    if ($answer -match '^(y|yes)$') {
        $env:VSMS_ADMIN_PASSWORD = Read-PlainPassword "Administrator password (8+ characters, letters and digits)"
    }
}
if ($NoSampleData) { $env:VSMS_LOAD_SAMPLE_DATA = "false" }

Write-Host "Starting VSMS on http://localhost:$Port ... (stop it with Ctrl+C)" -ForegroundColor Cyan
& .\mvnw.cmd spring-boot:run
