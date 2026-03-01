# Benchmark Index (Skeleton)

The Benchmark Index provides anonymous, aggregate statistics across protocols and instrument types.
It stores only aggregate values and requires tenant opt-in.

## Privacy Notes
- No raw PHI/PII is stored.
- Only aggregates (counts, mean, stddev, drift rate) are stored.
- Aggregates are keyed by protocol type and instrument type, not tenant.
- Export is blocked unless the tenant has opted in.

## Opt-In
```
PUT /benchmark/opt-in?tenant_id=tenant-1&enabled=true
```

## Export Aggregates
```
GET /benchmark/aggregates?tenant_id=tenant-1&protocolType=regen&instrumentType=printer-v2
```

## Response Example
```json
{
  "aggregates": [
    {
      "protocolType": "regen",
      "instrumentType": "printer-v2",
      "metricKey": "pressure_kpa",
      "sampleCount": 1200,
      "meanValue": 89.4,
      "stddevValue": 1.8,
      "driftRate": 0.07,
      "updatedAt": "2026-02-25T12:00:00Z"
    }
  ]
}
```
