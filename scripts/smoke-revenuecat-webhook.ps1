param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$WebhookAuthorization,
    [Parameter(Mandatory = $true)]
    [long]$UserId,
    [string]$ProductId = "grun_pro_monthly",
    [string]$EntitlementId = "pro",
    [string]$AiAddonProductId = "grun_ai_15_credits"
)

$ErrorActionPreference = "Stop"

function Invoke-RevenueCatWebhook {
    param(
        [string]$EventType,
        [string]$EventId,
        [string]$Product,
        [string[]]$Entitlements = @(),
        [string]$CancelReason = $null
    )

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $expirationMs = [DateTimeOffset]::UtcNow.AddMonths(1).ToUnixTimeMilliseconds()
    $event = @{
        id = $EventId
        type = $EventType
        app_user_id = "user:$UserId"
        product_id = $Product
        entitlement_ids = $Entitlements
        transaction_id = "smoke_tx_$EventId"
        original_transaction_id = "smoke_otx_$UserId"
        event_timestamp_ms = $nowMs
        purchased_at_ms = $nowMs
        expiration_at_ms = $expirationMs
        period_type = "NORMAL"
        store = "APP_STORE"
        environment = "SANDBOX"
    }
    if ($CancelReason) {
        $event.cancel_reason = $CancelReason
    }
    $body = @{ event = $event } | ConvertTo-Json -Depth 8
    Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/webhooks/revenuecat" `
        -Headers @{ Authorization = $WebhookAuthorization } `
        -ContentType "application/json" `
        -Body $body
}

Write-Host "=== RevenueCat Webhook Smoke Test ==="

$purchase = Invoke-RevenueCatWebhook `
    -EventType "INITIAL_PURCHASE" `
    -EventId ("smoke_purchase_" + [Guid]::NewGuid().ToString("N")) `
    -Product $ProductId `
    -Entitlements @($EntitlementId)
Write-Host ("Purchase event: status={0}, processed={1}" -f $purchase.status, $purchase.processed)
if ($purchase.status -ne "PROCESSED") {
    throw "Purchase webhook was not processed: $($purchase.message)"
}

$addon = Invoke-RevenueCatWebhook `
    -EventType "NON_RENEWING_PURCHASE" `
    -EventId ("smoke_addon_" + [Guid]::NewGuid().ToString("N")) `
    -Product $AiAddonProductId
Write-Host ("AI add-on event: status={0}, processed={1}" -f $addon.status, $addon.processed)
if ($addon.status -ne "PROCESSED") {
    throw "AI add-on webhook was not processed: $($addon.message)"
}

$refund = Invoke-RevenueCatWebhook `
    -EventType "CANCELLATION" `
    -EventId ("smoke_refund_" + [Guid]::NewGuid().ToString("N")) `
    -Product $ProductId `
    -Entitlements @($EntitlementId) `
    -CancelReason "CUSTOMER_SUPPORT"
Write-Host ("Refund/cancellation event: status={0}, processed={1}" -f $refund.status, $refund.processed)
if ($refund.status -ne "PROCESSED") {
    throw "Refund webhook was not processed: $($refund.message)"
}

Write-Host "RevenueCat webhook smoke test passed."
