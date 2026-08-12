# =====================================================================
#  CATACLISMO - build + deploy a cliente y server Mohist
#  Uso:  .\deploy.ps1              (compila y despliega)
#        .\deploy.ps1 -SoloCopiar  (salta el build, copia el jar ya hecho)
# =====================================================================
param(
    [switch]$SoloCopiar,
    [string]$Cliente = "C:\Users\Luis Angel\curseforge\minecraft\Instances\ss\mods",
    [string]$Server  = "C:\Users\Luis Angel\curseforge\minecraft\Instances\ss\minecraftServer\mods"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host ""
Write-Host "=== CATACLISMO - build + deploy ===" -ForegroundColor Cyan
Write-Host ""

# ---------- 1. BUILD ----------
if (-not $SoloCopiar) {
    Write-Host "[1/4] Compilando (la primera vez descarga Forge, tarda unos minutos)..." -ForegroundColor Yellow
    & .\gradlew.bat build --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "BUILD FALLIDO. Nada se ha copiado." -ForegroundColor Red
        Write-Host "Copia el error completo de arriba y pasamelo para arreglarlo." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[1/4] Build saltado (-SoloCopiar)." -ForegroundColor DarkGray
}

# ---------- 2. LOCALIZAR EL JAR ----------
Write-Host "[2/4] Buscando el jar..." -ForegroundColor Yellow
$jar = Get-ChildItem "build\libs\*.jar" -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notmatch '-(sources|javadoc|slim)\.jar$' } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (-not $jar) {
    Write-Host "No hay ningun jar en build\libs. Compila primero (.\deploy.ps1 sin -SoloCopiar)." -ForegroundColor Red
    exit 1
}
$hashOrigen = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash
Write-Host "      $($jar.Name)  ($([math]::Round($jar.Length/1MB,2)) MB)" -ForegroundColor Gray

# ---------- 3. COPIAR ----------
Write-Host "[3/4] Copiando a cliente y server..." -ForegroundColor Yellow
$destinos = @(
    @{ Nombre = "CLIENTE"; Ruta = $Cliente },
    @{ Nombre = "SERVER ";  Ruta = $Server  }
)

foreach ($d in $destinos) {
    if (-not (Test-Path $d.Ruta)) {
        Write-Host "      ERROR: no existe la carpeta $($d.Ruta)" -ForegroundColor Red
        exit 1
    }
    # borrar versiones viejas del mod: dos jars del mismo mod = crash al arrancar
    Get-ChildItem (Join-Path $d.Ruta "cataclysm-*.jar") -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne $jar.Name } |
        ForEach-Object {
            Write-Host "      quitando version vieja: $($_.Name)" -ForegroundColor DarkGray
            Remove-Item $_.FullName -Force
        }
    Copy-Item $jar.FullName $d.Ruta -Force
}

# ---------- 4. VERIFICAR HASH ----------
Write-Host "[4/4] Verificando hashes (un jar copiado a medias = NoClassDefFoundError)..." -ForegroundColor Yellow
$todoOk = $true
foreach ($d in $destinos) {
    $copia = Join-Path $d.Ruta $jar.Name
    $h = (Get-FileHash $copia -Algorithm SHA256).Hash
    if ($h -eq $hashOrigen) {
        Write-Host "      OK  $($d.Nombre)  $($d.Ruta)" -ForegroundColor Green
    } else {
        Write-Host "      MAL $($d.Nombre)  hash distinto en $copia" -ForegroundColor Red
        $todoOk = $false
    }
}

Write-Host ""
if ($todoOk) {
    Write-Host "LISTO. Reinicia el server y el cliente." -ForegroundColor Green
    Write-Host ""
    Write-Host "Pruebalo desde la consola del server (sin nombre = primer jugador online):" -ForegroundColor Cyan
    Write-Host "    disaster meteoros" -ForegroundColor White
    Write-Host "    disaster tornado" -ForegroundColor White
    Write-Host "    disaster ejecucion_natural" -ForegroundColor White
    Write-Host "    disaster stopall        <- corta todo si se desmadra" -ForegroundColor White
} else {
    Write-Host "DEPLOY INCOMPLETO: revisa los errores de arriba." -ForegroundColor Red
    exit 1
}
Write-Host ""
