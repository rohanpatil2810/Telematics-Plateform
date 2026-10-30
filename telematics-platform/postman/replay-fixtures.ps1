# param(
#     [Parameter(Mandatory = $true)]
#     [ValidateSet("overspeed", "fatigue", "offline")]
#     [string]$Fixture,
#     [string]$BaseUrl = "http://localhost:8080"
# )

# $ErrorActionPreference = "Stop"
# $dataDirectory = Join-Path $PSScriptRoot "data"
# $ingestionResults = [System.Collections.Generic.List[object]]::new()

# function Set-TestClock([string]$Now) {
#     $body = @{ now = $Now } | ConvertTo-Json -Compress
#     Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/v1/test/clock" `
#         -ContentType "application/json" -Body $body | Out-Null
# }

# function Submit-Packets(
#     [object[]]$Packets,
#     [string]$Label,
#     [System.Collections.Generic.List[object]]$Results
# ) {
#     $body = ConvertTo-Json -InputObject @($Packets) -Depth 30 -Compress
#     $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/packets" `
#         -ContentType "application/json" -Body $body

#     # $Results.Add([pscustomobject]@{
#     #     batch = $Label
#     #     accepted = $response.acceptedCount
#     #     duplicates = $response.duplicateCount
#     #     outOfOrderIgnored = $response.outOfOrderIgnoredCount
#     #     rejected = $response.rejectedCount
#     # })
#     Write-Host "`n--- Ingestion response: $Label ---"
# $response | ConvertTo-Json -Depth 20

# $Results.Add([pscustomobject]@{
#     batch = $Label
#     accepted = $response.acceptedCount
#     duplicates = $response.duplicateCount
#     outOfOrderIgnored = $response.outOfOrderIgnoredCount
#     rejected = $response.rejectedCount
#     rejections = ($response.rejections | ConvertTo-Json -Depth 10 -Compress)
# })
# }

# # $existingAlerts = @(Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/alerts")
# # if ($existingAlerts.Count -gt 0) {
# #     throw "The server already contains alerts. Restart the test-profile application before replaying a fixture."
# # }
# $existingAlerts = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/alerts"

# Write-Host "Response type: $($existingAlerts.GetType().FullName)"
# Write-Host "Alert count: $(@($existingAlerts).Count)"
# Write-Host "Response content:"
# $existingAlerts | ConvertTo-Json -Depth 10

# if (@($existingAlerts).Count -gt 0) {
#     throw "The server already contains alerts. Restart the test-profile application before replaying a fixture."
# }

# switch ($Fixture) {
#     "overspeed" {
#         Set-TestClock "2026-10-27T00:00:00Z"
#         $packets = @(Get-Content -Raw -Path (Join-Path $dataDirectory "overspeed-packets.json") |
#             ConvertFrom-Json)
#         Submit-Packets -Packets $packets -Label "overspeed-packets.json" -Results $ingestionResults
#     }
#     "fatigue" {
#         Set-TestClock "2026-10-27T00:00:00Z"
#         $packets = @(Get-Content -Raw -Path (Join-Path $dataDirectory "fatigue-packets.json") |
#             ConvertFrom-Json)
#         Submit-Packets -Packets $packets -Label "fatigue-packets.json" -Results $ingestionResults
#     }
#     "offline" {
#         $scenario = Get-Content -Raw -Path (Join-Path $dataDirectory "offline-scenario.json") |
#             ConvertFrom-Json
#         foreach ($step in $scenario.steps) {
#             Set-TestClock $step.serverTime
#             if ($step.action -eq "INGEST") {
#                 Submit-Packets -Packets @($step.packets) `
#                     -Label "offline step $($step.step)" -Results $ingestionResults
#             } elseif ($step.action -ne "CHECK") {
#                 throw "Unsupported action '$($step.action)' in offline step $($step.step)."
#             }
#         }
#     }
# }

# $totals = [ordered]@{
#     accepted = 0
#     duplicates = 0
#     outOfOrderIgnored = 0
#     rejected = 0
# }
# foreach ($result in $ingestionResults) {
#     $totals.accepted += $result.accepted
#     $totals.duplicates += $result.duplicates
#     $totals.outOfOrderIgnored += $result.outOfOrderIgnored
#     $totals.rejected += $result.rejected
# }

# $alerts = @(Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/alerts")
# $statusCounts = @($alerts | Group-Object type, status | Sort-Object Name | ForEach-Object {
#     [pscustomobject]@{
#         typeAndStatus = $_.Name
#         count = $_.Count
#     }
# })

# Write-Output "Fixture: $Fixture"
# Write-Output "Ingestion counts by batch:"
# if ($ingestionResults.Count -eq 0) {
#     Write-Output "(none)"
# } else {
#     $ingestionResults | Format-Table -AutoSize | Out-String | Write-Output
# }
# Write-Output "Ingestion totals:"
# $totals | ConvertTo-Json
# Write-Output "Alert counts by type and status:"
# if ($statusCounts.Count -eq 0) {
#     Write-Output "(none)"
# } else {
#     $statusCounts | Format-Table -AutoSize | Out-String | Write-Output
# }
# Write-Output "Final alerts ($($alerts.Count)); each record is JSON:"
# foreach ($alert in $alerts) {
#     Write-Output (ConvertTo-Json -InputObject $alert -Depth 20 -Compress)
# }

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("overspeed", "fatigue", "offline")]
    [string]$Fixture,

    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$dataDirectory = Join-Path $PSScriptRoot "data"
$ingestionResults = [System.Collections.Generic.List[object]]::new()

# ============================================================
# Helper: safely get a property from a response object
# ============================================================

function Get-PropertyValue {
    param(
        [AllowNull()]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string[]]$Names,

        [AllowNull()]
        [object]$DefaultValue = $null
    )

    if ($null -eq $Object) {
        return $DefaultValue
    }

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]

        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $DefaultValue
}

# ============================================================
# Helper: GET JSON and normalize the response to an array
# Compatible with Windows PowerShell 5.1
# ============================================================

function Get-JsonArray {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri
    )

    $httpResponse = Invoke-WebRequest `
        -Method Get `
        -Uri $Uri `
        -UseBasicParsing

    $content = $httpResponse.Content

    if (
        [string]::IsNullOrWhiteSpace($content) -or
        $content.Trim() -eq "[]"
    ) {
        return ,([object[]]@())
    }

    $parsed = ConvertFrom-Json -InputObject $content

    if ($null -eq $parsed) {
        return ,([object[]]@())
    }

    # Support APIs that return {"value":[...]} as well.
    $valueProperty = $parsed.PSObject.Properties["value"]

    if (
        $null -ne $valueProperty -and
        $null -ne $valueProperty.Value
    ) {
        $parsed = $valueProperty.Value
    }

    # A one-item JSON array may be returned as one object by
    # Windows PowerShell 5.1. Normalize both cases.
    $items = @($parsed)

    return ,$items
}

# ============================================================
# Helper: set the test clock
# ============================================================

function Set-TestClock {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Now
    )

    $clockBody = @{
        now = $Now
    } | ConvertTo-Json -Compress

    $null = Invoke-RestMethod `
        -Method Put `
        -Uri "$BaseUrl/api/v1/test/clock" `
        -ContentType "application/json" `
        -Body $clockBody

    Write-Host "Test clock set to: $Now"
}

# ============================================================
# Helper: load a fixture containing a top-level JSON array
# ============================================================

function Read-PacketFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Fixture file not found: $Path"
    }

    $rawJson = Get-Content -LiteralPath $Path -Raw

    if ([string]::IsNullOrWhiteSpace($rawJson)) {
        throw "Fixture file is empty: $Path"
    }

    $parsed = ConvertFrom-Json -InputObject $rawJson

    if ($null -eq $parsed -or $parsed -isnot [array]) {
        throw "Expected a top-level JSON array of packet objects in: $Path"
    }

    foreach ($packet in $parsed) {
        if ($null -eq $packet -or $packet -is [string] -or $packet -is [array]) {
            throw "Invalid fixture structure: every array element must be a JSON packet object. File: $Path"
        }
    }

    Write-Host "Loaded fixture: $Path"
    Write-Host "Packets loaded: $($parsed.Count)"

    # Return only the packet array. Do not emit debug objects or JSON strings.
    return ,$parsed
}

# ============================================================
# Helper: submit a packet batch and capture ingestion results
# ============================================================

function Submit-Packets {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$Packets,

        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[object]]$Results
    )

    if ($null -eq $Packets -or $Packets.Count -eq 0) {
        throw "No packets were loaded for batch '$Label'."
    }

    $requestBody = ConvertTo-Json `
        -InputObject $Packets `
        -Depth 30 `
        -Compress

    if (-not $requestBody.TrimStart().StartsWith("[")) {
        throw "Request for '$Label' is not a JSON array. Submission cancelled."
    }

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "Submitting batch: $Label"
    Write-Host "Packet count: $($Packets.Count)"
    Write-Host "Request body length: $($requestBody.Length) characters"
    Write-Host "============================================================"

    Write-Host ""
    Write-Host "--- Request JSON sample (first 1000 characters) ---"

    $sampleLength = [Math]::Min(1000, $requestBody.Length)
    Write-Host $requestBody.Substring(0, $sampleLength)

    Write-Host ""
    Write-Host "--- Sending packets to API ---"

    try {
        $apiResponse = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/v1/packets" `
            -ContentType "application/json" `
            -Body $requestBody
    }
    catch {
        Write-Host ""
        Write-Host "ERROR: Packet submission failed for '$Label'."
        Write-Host $_.Exception.Message

        if ($null -ne $_.ErrorDetails -and
            -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            Write-Host "API error response:"
            Write-Host $_.ErrorDetails.Message
        }

        throw
    }

    Write-Host ""
    Write-Host "--- Full ingestion response: $Label ---"

    $apiResponse | ConvertTo-Json -Depth 30

    # Support the expected field names and common alternatives.
    $accepted = [int](Get-PropertyValue `
        -Object $apiResponse `
        -Names @("acceptedCount", "accepted") `
        -DefaultValue 0)

    $duplicates = [int](Get-PropertyValue `
        -Object $apiResponse `
        -Names @("duplicateCount", "duplicates") `
        -DefaultValue 0)

    $outOfOrder = [int](Get-PropertyValue `
        -Object $apiResponse `
        -Names @("outOfOrderIgnoredCount", "outOfOrderIgnored") `
        -DefaultValue 0)

    $rejected = [int](Get-PropertyValue `
        -Object $apiResponse `
        -Names @("rejectedCount", "rejected") `
        -DefaultValue 0)

    $rejectionDetails = Get-PropertyValue `
        -Object $apiResponse `
        -Names @("rejections", "rejectionDetails", "errors", "rejectedPackets") `
        -DefaultValue @()

    $rejectionJson = ConvertTo-Json `
        -InputObject @($rejectionDetails) `
        -Depth 30 `
        -Compress

    $Results.Add(
        [pscustomobject]@{
            batch             = $Label
            packetCount       = $Packets.Count
            accepted          = $accepted
            duplicates        = $duplicates
            outOfOrderIgnored = $outOfOrder
            rejected          = $rejected
            rejections        = $rejectionJson
        }
    )

    if ($rejected -gt 0) {
        Write-Warning "$rejected packet(s) were rejected in '$Label'. Review the full API response above."
    }
}

# ============================================================
# Validate the API is available and alerts are initially empty
# ============================================================

$alertsUri = "$BaseUrl/api/v1/alerts"

Write-Host "Checking API: $alertsUri"

try {
    $existingAlerts = Get-JsonArray -Uri $alertsUri
}
catch {
    throw "Cannot reach the alerts API at '$alertsUri'. Confirm the application is running. Details: $($_.Exception.Message)"
}

Write-Host "Existing alert count: $($existingAlerts.Count)"

if ($existingAlerts.Count -gt 0) {
    throw @"
The application already contains alerts.
Stop the current application, restart it with the test profile,
and run this fixture again. Use a fresh application instance for
each independent fixture.
"@
}

# ============================================================
# Run the selected fixture
# ============================================================

switch ($Fixture) {

    "overspeed" {
        Set-TestClock -Now "2026-10-27T00:00:00Z"

        $fixturePath = Join-Path $dataDirectory "overspeed-packets.json"
        $packets = Read-PacketFixture -Path $fixturePath

        Submit-Packets `
            -Packets $packets `
            -Label "overspeed-packets.json" `
            -Results $ingestionResults
    }

    "fatigue" {
        Set-TestClock -Now "2026-10-27T00:00:00Z"

        $fixturePath = Join-Path $dataDirectory "fatigue-packets.json"
        $packets = Read-PacketFixture -Path $fixturePath

        Submit-Packets `
            -Packets $packets `
            -Label "fatigue-packets.json" `
            -Results $ingestionResults
    }

    "offline" {
        $fixturePath = Join-Path $dataDirectory "offline-scenario.json"

        if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
            throw "Offline scenario file not found: $fixturePath"
        }

        $scenarioJson = Get-Content -Raw -LiteralPath $fixturePath
        $scenario = ConvertFrom-Json -InputObject $scenarioJson

        if ($null -eq $scenario.steps) {
            throw "The offline scenario file does not contain a 'steps' array."
        }

        foreach ($step in $scenario.steps) {

            Write-Host ""
            Write-Host "============================================================"
            Write-Host "Offline scenario step: $($step.step)"
            Write-Host "Action: $($step.action)"
            Write-Host "============================================================"

            Set-TestClock -Now ([string]$step.serverTime)

            if ($step.action -eq "INGEST") {

                $stepPackets = @($step.packets)

                Submit-Packets `
                    -Packets $stepPackets `
                    -Label "offline step $($step.step)" `
                    -Results $ingestionResults

            }
            elseif ($step.action -eq "CHECK") {

                Write-Host "Clock/check step; no packet submission."

            }
            else {
                throw "Unsupported action '$($step.action)' in offline step $($step.step)."
            }
        }
    }
}

# ============================================================
# Calculate totals
# ============================================================

$totals = [ordered]@{
    accepted          = 0
    duplicates        = 0
    outOfOrderIgnored = 0
    rejected          = 0
}

foreach ($result in $ingestionResults) {
    $totals.accepted += $result.accepted
    $totals.duplicates += $result.duplicates
    $totals.outOfOrderIgnored += $result.outOfOrderIgnored
    $totals.rejected += $result.rejected
}

# ============================================================
# Retrieve and normalize final alerts
# ============================================================

Write-Host ""
Write-Host "Retrieving final alerts..."

$alerts = Get-JsonArray -Uri $alertsUri

$statusCounts = @(
    $alerts |
        Group-Object { "$($_.type) / $($_.status)" } |
        Sort-Object Name |
        ForEach-Object {
            [pscustomobject]@{
                typeAndStatus = $_.Name
                count         = $_.Count
            }
        }
)

# ============================================================
# Final report
# ============================================================

Write-Host ""
Write-Host "============================================================"
Write-Host "FIXTURE REPLAY SUMMARY"
Write-Host "============================================================"

Write-Host "Fixture: $Fixture"
Write-Host ""
Write-Host "Ingestion counts by batch:"

if ($ingestionResults.Count -eq 0) {
    Write-Host "(none)"
}
else {
    $ingestionResults |
        Select-Object batch, packetCount, accepted, duplicates, outOfOrderIgnored, rejected |
        Format-Table -AutoSize |
        Out-String |
        Write-Host
}

Write-Host "Ingestion totals:"
$totals | ConvertTo-Json | Write-Host

Write-Host ""
Write-Host "Rejection details:"

$rejectedBatches = @(
    $ingestionResults |
        Where-Object { $_.rejected -gt 0 }
)

if ($rejectedBatches.Count -eq 0) {
    Write-Host "No rejected packets reported by the API."
}
else {
    foreach ($batch in $rejectedBatches) {
        Write-Host ""
        Write-Host "Batch: $($batch.batch)"
        Write-Host $batch.rejections
    }
}

Write-Host ""
Write-Host "Alert counts by type and status:"

if ($statusCounts.Count -eq 0) {
    Write-Host "(no alerts)"
}
else {
    $statusCounts |
        Format-Table -AutoSize |
        Out-String |
        Write-Host
}

Write-Host "Final alerts ($($alerts.Count)); each record is JSON:"

if ($alerts.Count -eq 0) {
    Write-Host "(no alerts generated)"
}
else {
    foreach ($alert in $alerts) {
        ConvertTo-Json -InputObject $alert -Depth 30 -Compress |
            Write-Host
    }
}

Write-Host ""
Write-Host "============================================================"
Write-Host "END OF FIXTURE REPLAY"
Write-Host "============================================================"

if ($totals.rejected -gt 0) {
    Write-Warning "This run had rejected packets. Inspect their rejection details before considering the fixture validated."
}