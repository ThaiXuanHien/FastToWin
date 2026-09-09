function Test-DevPrivateIPv4 {
    param([Parameter(Mandatory = $true)][string]$Address)

    $parsed = $null
    if (-not [Net.IPAddress]::TryParse($Address, [ref]$parsed)) { return $false }
    if ($parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { return $false }
    $bytes = $parsed.GetAddressBytes()
    return $bytes[0] -eq 10 -or
        ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
}

function Resolve-DevLanHost {
    param(
        [string]$RequestedHost,
        [string[]]$CandidateHosts
    )

    if ($RequestedHost) {
        if (-not (Test-DevPrivateIPv4 $RequestedHost)) {
            throw "LanHost phai la dia chi IPv4 rieng (10.x, 172.16-31.x hoac 192.168.x): $RequestedHost"
        }
        return ([Net.IPAddress]::Parse($RequestedHost)).ToString()
    }

    if (-not $CandidateHosts) {
        $CandidateHosts = @(
            [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
                Where-Object {
                    $_.OperationalStatus -eq [Net.NetworkInformation.OperationalStatus]::Up -and
                    $_.NetworkInterfaceType -ne [Net.NetworkInformation.NetworkInterfaceType]::Loopback
                } |
                Sort-Object Speed -Descending |
                ForEach-Object {
                    $properties = $_.GetIPProperties()
                    $hasIPv4Gateway = $properties.GatewayAddresses |
                        Where-Object {
                            $_.Address.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork -and
                            $_.Address.ToString() -ne '0.0.0.0'
                        } |
                        Select-Object -First 1
                    if ($hasIPv4Gateway) {
                        $properties.UnicastAddresses |
                            Where-Object { $_.Address.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork } |
                            ForEach-Object { $_.Address.ToString() }
                    }
                }
        )
    }
    $selected = $CandidateHosts | Where-Object { Test-DevPrivateIPv4 $_ } | Select-Object -First 1
    if (-not $selected) {
        throw 'Khong tim thay IPv4 LAN. Hay truyen -LanHost, vi du: -LanHost 192.168.1.10'
    }
    return ([Net.IPAddress]::Parse($selected)).ToString()
}
