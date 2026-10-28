#!/usr/bin/env bash
# 爬虫抓取分析：从 Caddy JSON 日志（docker logs）提取 Googlebot/Bingbot 活动与 /regles 抓取进度
# 用法：ssh root@<server> 'bash ~/bot-check.sh'   （与 traffic.sh 并排使用）
set -euo pipefail

docker logs factur-x-api-caddy-1 --since 336h 2>&1 | python3 -c "
import json,sys
from collections import Counter
bots=Counter(); pages=Counter(); regles_bot=Counter(); regles_human=Counter(); regles_status=Counter()
for line in sys.stdin:
    try: d=json.loads(line)
    except: continue
    r=d.get('request',{})
    if r.get('host')!='factur-x-api.com': continue
    ua=r.get('headers',{}).get('User-Agent',[''])[0]
    uri=r.get('uri','/').split('?')[0]
    st=d.get('status','')
    low=ua.lower()
    bot=('googlebot' in low or 'bingbot' in low or 'dataforseobot' in low
         or 'duckduck' in low or 'yandex' in low)
    if bot:
        name=('Googlebot' if 'googlebot' in low else
              'Bingbot' if 'bingbot' in low else
              'DataForSeoBot' if 'dataforseobot' in low else
              'DuckDuck' if 'duckduck' in low else 'Other')
        bots[name]+=1
        pages[uri]+=1
    if uri.startswith('/regles'):
        regles_status[st]+=1
        if bot: regles_bot[uri]+=1
        else: regles_human[r.get('client_ip','')+' -> '+uri]+=1

print('=== 爬虫 UA 汇总（近14天）===')
for k,v in bots.most_common(8): print(f'{v:6}  {k}')
print()
print('=== /regles* 抓取进度（按页去重看覆盖）===')
for u,c in sorted(regles_bot.items()): print(f'{c:6}  {u}')
print()
print('=== /regles* 人类访问（按 IP）===')
for k,v in regles_human.most_common(15): print(f'{v:6}  {k}')
print()
print('=== /regles* 状态码 ===')
for k,v in regles_status.most_common(): print(f'{v:6}  {k}')
"
