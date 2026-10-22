# API fixtures — six VAT categories (EN 16931 BT-151)

One **valid** and one **invalid** JSON payload per category, for `POST /api/generate`.
`*-valid.json` returns `200` with self-validated EN 16931 XML; `*-invalid.json`
returns `400` with the rule ID, the field path and a suggested fix.

| File | Category | The rule the invalid file trips |
|---|---|---|
| `S-*.json` | Standard rated | `S-invalid`: 0% rate with category S → **BR-S-05** |
| `Z-*.json` | Zero-rated | `Z-invalid`: non-zero rate with Z → **BR-Z-05** |
| `E-*.json` | Exempt from VAT | `E-invalid`: missing `vatExemptionReason` → **BR-E-10** |
| `AE-*.json` | Reverse charge | `AE-invalid`: missing buyer `vatId` → **BR-AE-02** |
| `K-*.json` | Intra-community supply | `K-invalid`: missing `deliveryDate` → **BR-IC-11** |
| `G-*.json` | Export outside the EU | `G-invalid`: missing `vatExemptionReason` → **BR-G-10** |

## Smoke-test all twelve

```bash
KEY=facturx_your_key
for f in *.json; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST https://factur-x-api.com/api/generate \
    -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d @"$f")
  expected=$(echo "$f" | grep -q valid && echo 200 || echo 400)
  [ "$code" = "$expected" ] && echo "OK   $f -> $code" || echo "FAIL $f -> $code (expected $expected)"
done
```

Notes:
- `vatRate` 0 + category `S` is rejected at input time (never silently corrected);
  the error message names the rule, the field and the fix.
- Categories `E`/`AE`/`K`/`G` require a top-level `vatExemptionReason` (BT-120).
- `AE`/`K` additionally require the buyer's `vatId` (BT-48).
- `K` additionally requires `deliveryDate` (BT-72, emitted as the actual delivery date).
