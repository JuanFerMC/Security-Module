[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "No existe .env.example. Copia .env.example a .env, completa sus valores y vuelve a ejecutar .\run-local.ps1."
}
$previous = @{}
$loaded = @()
$values = @{}
try {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw "Línea inválida en .env: $line" }
        $name, $value = $Matches[1], $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    $required = @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','JWT_PRIVATE_KEY_LOCATION','JWT_PUBLIC_KEY_LOCATION','INIT_ADMIN_EMAIL','INIT_ADMIN_PASSWORD')
    foreach ($name in $required) {
        $value = $values[$name]
        if ([string]::IsNullOrWhiteSpace($value) -or $value -match '^<.*>$|^CHANGE_ME$|^TODO$') { throw "Completa la variable $name en .env." }
    }
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
        $loaded += $name
    }
    foreach ($name in @('JWT_PRIVATE_KEY_LOCATION','JWT_PUBLIC_KEY_LOCATION')) {
        $path = $values[$name] -replace '^file:', ''
        if (-not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $root $path }
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "No se encuentra el archivo configurado en ${name}: $path" }
    }
    Push-Location $root
    try { & .\mvnw.cmd spring-boot:run; if ($LASTEXITCODE -ne 0) { throw "Maven terminó con código $LASTEXITCODE." } }
    finally { Pop-Location }
}
finally {
    foreach ($name in $loaded) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
