#!/usr/bin/env bash
# 流量 + 转化统计：解析 Caddy JSON 访问日志（docker logs）+ 生产 DB 计数
# 用法：ssh root@<server> 'bash ~/traffic.sh'
set -euo pipefail

docker logs factur-x-api-caddy-1 --since 720h 2>&1 | python3 -c "
import json,sys,datetime
from collections import Counter
SCAN=('wp-','.php','chosen','/ops','txets','hellopress','hello_world','/.env','/.well-known')

days=Counter(); dayips={}; first=None; last=None
paths48=Counter(); api=Counter(); refs=Counter()
now=datetime.datetime.now(datetime.timezone.utc).timestamp()
for line in sys.stdin:
    try: d=json.loads(line)
    except: continue
    r=d.get('request',{})
    if r.get('host')!='factur-x-api.com': continue
    ts=d.get('ts')
    if not ts: continue
    dt=datetime.datetime.fromtimestamp(ts,datetime.UTC)
    if first is None: first=dt
    last=dt
    day=dt.strftime('%m-%d')
    days[day]+=1
    dayips.setdefault(day,set()).add(r.get('client_ip',''))
    if now-ts <= 48*3600:
        uri=r.get('uri','/').split('?')[0]
        if not any(s in uri for s in SCAN):
            paths48[uri]+=1
            if uri.startswith('/api/'):
                api[(r.get('method'),uri,d.get('status'))]+=1
            ref=(r.get('headers',{}).get('Referer') or [''])[0]
            if ref and 'factur-x-api.com' not in ref:
                refs[ref]+=1

print('=== 日志窗口 ===')
print(f'{first:%Y-%m-%d %H:%M} UTC -> {last:%Y-%m-%d %H:%M} UTC')
print()
print('=== 每日流量（含爬虫/扫描器）===')
print('日期      请求数  独立IP')
for day in sorted(days):
    print(f'  {day}  {days[day]:6d}  {len(dayips[day]):5d}')
print()
print('=== 近48h 正常页面 ===')
for p,c in paths48.most_common(12): print(f'  {c:4d}  {p}')
print()
print('=== 近48h API 调用 ===')
if api:
    for k,c in api.most_common(): print(f'  {c:4d}  {k[0]} {k[1]} -> {k[2]}')
else:
    print('  （无）')
print()
print('=== 近48h 外部 Referer TOP10 ===')
if refs:
    for rf,c in refs.most_common(10): print(f'  {c:4d}  {rf[:100]}')
else:
    print('  （无）')
"

echo
echo '=== 转化（生产 DB）==='
docker exec factur-x-api-db-1 psql -U facturx -d facturx -tA -c "
SELECT 'api_keys 总计: ' || count(*) || ' (pro ' || count(*) FILTER (WHERE plan='pro') || ' / free ' || count(*) FILTER (WHERE plan='free') || ')' FROM api_keys;
SELECT 'subscribers: ' || count(*) FROM subscribers;
SELECT 'api_usage: ' || count(*) || ' (本月: ' || count(*) FILTER (WHERE created_at >= date_trunc('month', now())) || ')' FROM api_usage;
"
