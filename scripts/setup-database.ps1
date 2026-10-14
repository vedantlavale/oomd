<#
.SYNOPSIS
    Creates the VSMS database and a dedicated MySQL user (Windows PowerShell).

.DESCRIPTION
    Asks for the MySQL administrator ("root") password and for a NEW password for the
    application's database user. Nothing is written to disk and no password is stored
    in any file. Run it once, from the project folder:

        powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1

    Requires the "mysql" command-line client on the PATH (it is installed with MySQL Server;
    typically C:\Program Files\MySQL\MySQL Server 8.0\bin).
#>
param(
    [string]$DatabaseName = "vsms_db",
    [string]$AppUser = "vsms_user",
    [string]$MysqlHost = "localhost",
    [int]$Port = 3306,
    [string]$AdminUser = "root"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Write-Error "The 'mysql' client was not found. Add MySQL's bin folder to PATH (see README.md) and open a new PowerShell window."
}
if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$' -or $AppUser -notmatch '^[A-Za-z0-9_]+$') {
    Write-Error "Database and user names may only contain letters, digits and underscores."
}

function Read-PlainPassword([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

$adminPassword = Read-PlainPassword "MySQL password of user '$AdminUser'"
$appPassword   = Read-PlainPassword "NEW password for the application user '$AppUser' (letters + digits, 8+ characters)"
$confirm       = Read-PlainPassword "Repeat the new password"
if ($appPassword -ne $confirm) { Write-Error "The two passwords do not match." }
if ($appPassword.Length -lt 8) { Write-Error "Use at least 8 characters." }
if ($appPassword.Contains("'") -or $appPassword.Contains("\")) { Write-Error "Please avoid the characters ' and \ in this password." }

$sql = @"
CREATE DATABASE IF NOT EXISTS $DatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$appPassword';
ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON $DatabaseName.* TO '$AppUser'@'localhost';
FLUSH PRIVILEGES;
"@

# MYSQL_PWD keeps the password out of the command line; it only lives in this process.
$env:MYSQL_PWD = $adminPassword
try {
    $sql | mysql --host=$MysqlHost --port=$Port --user=$AdminUser
    if ($LASTEXITCODE -ne 0) { Write-Error "MySQL reported an error (see above)." }
}
finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $adminPassword = $null
    $appPassword = $null
    $confirm = $null
}

Write-Host ""
Write-Host "Done. Database '$DatabaseName' and user '$AppUser' are ready." -ForegroundColor Green
Write-Host "Next: start the application with  .\scripts\run.ps1"
