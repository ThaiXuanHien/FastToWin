$ErrorActionPreference = 'Stop'

$helperPath = Join-Path $PSScriptRoot '..\dev-network.ps1'
if (-not (Test-Path -LiteralPath $helperPath)) {
    throw 'Thiếu helper cấu hình mạng development.'
}
. $helperPath

function Assert-Equal {
    param([object]$Expected, [object]$Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Expected: '$Expected'. Actual: '$Actual'."
    }
}

Assert-Equal `
    '192.168.1.141' `
    (Resolve-DevLanHost -RequestedHost '192.168.1.141') `
    'IP LAN được chỉ định phải được giữ nguyên.'

Assert-Equal `
    '192.168.1.141' `
    (Resolve-DevLanHost -CandidateHosts @('192.168.1.141', '172.25.224.1')) `
    'Tự động nhận diện phải chọn địa chỉ LAN đầu tiên từ route đang hoạt động.'

foreach ($invalidHost in @('127.0.0.1', '8.8.8.8', 'not-an-ip')) {
    $rejected = $false
    try {
        Resolve-DevLanHost -RequestedHost $invalidHost | Out-Null
    } catch {
        $rejected = $true
    }
    if (-not $rejected) {
        throw "Địa chỉ không phải LAN phải bị từ chối: $invalidHost"
    }
}

Write-Host 'Dev network tests passed.'
