param(
    [Parameter(Mandatory = $true)]
    [string]$CacheClusterId,
    [string]$CacheNodeId = "0001",
    [Parameter(Mandatory = $true)]
    [string]$SnsTopicArn,
    [string]$Region = "eu-west-1",
    [string]$AlarmPrefix = "grun-staging-redis",
    [double]$EngineCpuThreshold = 90,
    [double]$MemoryThreshold = 80,
    [double]$ConnectionsThreshold = 1000
)

$ErrorActionPreference = "Stop"

function Put-Alarm(
    [string]$Name,
    [string]$Metric,
    [string]$Statistic,
    [double]$Threshold,
    [string]$Comparison,
    [int]$EvaluationPeriods,
    [int]$DatapointsToAlarm,
    [string]$Unit = ""
) {
    $arguments = @(
        "cloudwatch", "put-metric-alarm",
        "--region", $Region,
        "--alarm-name", $Name,
        "--alarm-description", "GRun managed Redis alarm for $Metric",
        "--namespace", "AWS/ElastiCache",
        "--metric-name", $Metric,
        "--statistic", $Statistic,
        "--period", "60",
        "--evaluation-periods", $EvaluationPeriods,
        "--datapoints-to-alarm", $DatapointsToAlarm,
        "--threshold", $Threshold,
        "--comparison-operator", $Comparison,
        "--treat-missing-data", "missing",
        "--alarm-actions", $SnsTopicArn,
        "--ok-actions", $SnsTopicArn,
        "--dimensions", "Name=CacheClusterId,Value=$CacheClusterId", "Name=CacheNodeId,Value=$CacheNodeId"
    )
    if ($Unit) { $arguments += @("--unit", $Unit) }
    & aws @arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed to create alarm $Name." }
    Write-Host "- configured $Name"
}

Write-Host "=== ElastiCache CloudWatch Alarm Setup ==="
Put-Alarm -Name "$AlarmPrefix-engine-cpu-high" -Metric "EngineCPUUtilization" -Statistic "Average" -Threshold $EngineCpuThreshold -Comparison "GreaterThanThreshold" -EvaluationPeriods 5 -DatapointsToAlarm 5 -Unit "Percent"
Put-Alarm -Name "$AlarmPrefix-memory-high" -Metric "DatabaseMemoryUsagePercentage" -Statistic "Average" -Threshold $MemoryThreshold -Comparison "GreaterThanThreshold" -EvaluationPeriods 5 -DatapointsToAlarm 5 -Unit "Percent"
Put-Alarm -Name "$AlarmPrefix-evictions" -Metric "Evictions" -Statistic "Sum" -Threshold 0 -Comparison "GreaterThanThreshold" -EvaluationPeriods 2 -DatapointsToAlarm 2 -Unit "Count"
Put-Alarm -Name "$AlarmPrefix-connections-high" -Metric "CurrConnections" -Statistic "Average" -Threshold $ConnectionsThreshold -Comparison "GreaterThanThreshold" -EvaluationPeriods 10 -DatapointsToAlarm 10 -Unit "Count"

Write-Host "ElastiCache CloudWatch alarms configured."
