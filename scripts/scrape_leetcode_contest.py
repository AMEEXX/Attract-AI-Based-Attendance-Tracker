#!/usr/bin/env python3
"""
LeetCode Contest Ranking Scraper & Search Tool
----------------------------------------------
Scrapes LeetCode contest rankings (bypassing Cloudflare bot mitigation
using a headless Chrome DevTools Protocol session) and searches the complete
participant database for specific keywords (e.g. 'arpita', 'gautam').

Outputs matched participant IDs, ranks, scores, and profile links to console,
JSON, and CSV.
"""

import argparse
import asyncio
import csv
import json
import os
import subprocess
import sys
import time
import urllib.request
from typing import List, Dict, Any, Optional

try:
    from tornado.websocket import websocket_connect
except ImportError:
    print("Error: 'tornado' is required for WebSocket communication.")
    print("Install it using: pip install tornado")
    sys.exit(1)


def find_chrome() -> Optional[str]:
    """Auto-detect Google Chrome or Microsoft Edge executable path."""
    candidates = [
        # Windows Chrome
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
        # Windows Edge
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        # Linux
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/chromium-browser",
        "/usr/bin/chromium",
        # macOS
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    ]
    for p in candidates:
        if os.path.exists(p):
            return p
    return None


def get_latest_contest_slug() -> str:
    """Fetch the latest completed LeetCode contest slug via GraphQL."""
    query = """
    query {
      allContests {
        title
        titleSlug
        startTime
        duration
      }
    }
    """
    req = urllib.request.Request(
        "https://leetcode.com/graphql",
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Content-Type": "application/json",
        },
        data=json.dumps({"query": query}).encode("utf-8"),
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            contests = data.get("data", {}).get("allContests", [])
            now = time.time()
            # Filter for completed contests
            past = [c for c in contests if c.get("startTime", 0) + c.get("duration", 0) < now]
            if past:
                return past[0]["titleSlug"]
    except Exception as e:
        print(f"Warning: Could not fetch latest contest via GraphQL ({e}). Defaulting to 'weekly-contest-522'.")
    return "weekly-contest-522"


async def scrape_and_search(
    contest_slug: str,
    keywords: List[str],
    chrome_path: str,
    batch_size: int = 20,
    max_pages: Optional[int] = None,
    output_dir: str = ".",
) -> List[Dict[str, Any]]:
    """
    Launch headless Chrome, navigate to contest ranking page,
    and concurrently fetch and filter ranking pages.
    """
    temp_dir = os.path.join(os.environ.get("TEMP", "."), f"chrome_lc_{int(time.time())}")
    os.makedirs(temp_dir, exist_ok=True)
    debug_port = 9222

    print(f"\n[*] Launching Headless Chrome via CDP on port {debug_port}...")
    chrome_proc = subprocess.Popen([
        chrome_path,
        "--headless=new",
        f"--remote-debugging-port={debug_port}",
        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        f"--user-data-dir={temp_dir}",
    ])

    await asyncio.sleep(2)

    try:
        ranking_url = f"https://leetcode.com/contest/{contest_slug}/ranking/?region=global_v2"
        print(f"[*] Navigating to: {ranking_url}")
        
        # Open page target via DevTools HTTP endpoint
        req = urllib.request.Request(
            f"http://127.0.0.1:{debug_port}/json/new?{ranking_url}", method="PUT"
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            target = json.loads(resp.read().decode("utf-8"))

        ws_url = target["webSocketDebuggerUrl"]
        print(f"[*] Connecting to Chrome DevTools WebSocket...")
        ws = await websocket_connect(ws_url)
        print("[+] WebSocket connected successfully!")

        # Allow initial page load & Cloudflare validation to settle
        print("[*] Waiting 4 seconds for page environment and session cookies to settle...")
        await asyncio.sleep(4)

        # Step 1: Probe page 1 to get total participants
        probe_script = f"""
        (async () => {{
            try {{
                const res = await fetch('/contest/api/ranking/{contest_slug}/?pagination=1&region=global_v2');
                if (!res.ok) return {{ error: 'HTTP ' + res.status }};
                const data = await res.json();
                return {{ user_num: data.user_num, page_count: data.total_rank ? data.total_rank.length : 0 }};
            }} catch (e) {{
                return {{ error: e.toString() }};
            }}
        }})()
        """
        eval_id = 1
        await ws.write_message(json.dumps({
            "id": eval_id,
            "method": "Runtime.evaluate",
            "params": {"expression": probe_script, "awaitPromise": True, "returnByValue": True}
        }))

        total_users = 0
        while True:
            raw_msg = await ws.read_message()
            msg = json.loads(raw_msg)
            if msg.get("id") == eval_id:
                res = msg.get("result", {}).get("result", {}).get("value", {})
                if "user_num" in res:
                    total_users = res["user_num"]
                elif "error" in res:
                    raise RuntimeError(f"Error querying LeetCode contest API: {res['error']}")
                break

        total_pages = (total_users + 24) // 25
        if max_pages and max_pages < total_pages:
            total_pages = max_pages

        print(f"[+] Total contest participants: {total_users:,}")
        print(f"[+] Total pages to scrape: {total_pages:,} (Batch size: {batch_size} pages/req)")
        print(f"[*] Target search keywords: {keywords}\n")

        all_matches: List[Dict[str, Any]] = []
        start_time = time.time()
        chunk_index = 0
        total_chunks = (total_pages + batch_size - 1) // batch_size

        # Keywords formatted for JS
        kw_json = json.dumps([k.lower().strip() for k in keywords])

        for start_page in range(1, total_pages + 1, batch_size):
            chunk_index += 1
            end_page = min(start_page + batch_size - 1, total_pages)
            eval_id += 1

            batch_script = f"""
            (async () => {{
                const contest = {json.dumps(contest_slug)};
                const keywords = {kw_json};
                const startP = {start_page};
                const endP = {end_page};
                
                async function fetchPage(p) {{
                    try {{
                        const res = await fetch(`/contest/api/ranking/${{contest}}/?pagination=${{p}}&region=global_v2`);
                        if (!res.ok) return null;
                        return await res.json();
                    }} catch (e) {{
                        return null;
                    }}
                }}

                const promises = [];
                for (let p = startP; p <= endP; p++) {{
                    promises.push(fetchPage(p));
                }}
                
                const pagesData = await Promise.all(promises);
                const matches = [];
                let scannedCount = 0;

                for (const d of pagesData) {{
                    if (!d || !d.total_rank) continue;
                    scannedCount += d.total_rank.length;
                    for (const u of d.total_rank) {{
                        const uname = (u.username || '').toLowerCase();
                        const uslug = (u.user_slug || '').toLowerCase();
                        const rname = (u.real_name || '').toLowerCase();
                        
                        for (const kw of keywords) {{
                            if (uname.includes(kw) || uslug.includes(kw) || rname.includes(kw)) {{
                                matches.push({{
                                    matched_keyword: kw,
                                    rank: u.rank,
                                    score: u.score,
                                    username: u.username || '',
                                    user_slug: u.user_slug || '',
                                    real_name: u.real_name || '',
                                    finish_time: u.finish_time,
                                    country: u.country_name || u.country_code || '',
                                    data_region: u.data_region || 'US'
                                }});
                                break;
                            }}
                        }}
                    }}
                }}
                return {{ scannedCount, matches }};
            }})()
            """

            await ws.write_message(json.dumps({
                "id": eval_id,
                "method": "Runtime.evaluate",
                "params": {"expression": batch_script, "awaitPromise": True, "returnByValue": True}
            }))

            while True:
                raw_msg = await ws.read_message()
                msg = json.loads(raw_msg)
                if msg.get("id") == eval_id:
                    val = msg.get("result", {}).get("result", {}).get("value", {})
                    chunk_matches = val.get("matches", [])
                    all_matches.extend(chunk_matches)
                    
                    elapsed = time.time() - start_time
                    percent = (end_page / total_pages) * 100
                    status_line = (
                        f"[{chunk_index}/{total_chunks}] Pages {start_page}..{end_page} ({percent:5.1f}%) "
                        f"| Total matches: {len(all_matches):2d} | Elapsed: {elapsed:5.1f}s"
                    )
                    print(status_line)
                    
                    if chunk_matches:
                        for m in chunk_matches:
                            print(f"    --> Found [{m['matched_keyword'].upper()}]: Rank {m['rank']} | User: {m['username']} ({m['user_slug']}) | Score: {m['score']}")
                    break

            # Brief pause to respect server rate-limits
            await asyncio.sleep(0.08)

        ws.close()
        return all_matches

    finally:
        chrome_proc.terminate()
        try:
            chrome_proc.wait(timeout=3)
        except Exception:
            chrome_proc.kill()


def format_finish_time(ts: Optional[int]) -> str:
    """Format unix timestamp to readable string."""
    if not ts:
        return "N/A"
    try:
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts))
    except Exception:
        return str(ts)


def main():
    parser = argparse.ArgumentParser(
        description="Scrape and search LeetCode contest rankings for specific keywords."
    )
    parser.add_argument(
        "--contest",
        type=str,
        default=None,
        help="Contest slug (e.g. 'weekly-contest-522'). Defaults to latest completed contest.",
    )
    parser.add_argument(
        "--keywords",
        type=str,
        nargs="+",
        default=["arpita", "gautam"],
        help="Keywords to search in username, profile slug, or real name (default: arpita gautam).",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=20,
        help="Number of pages to fetch concurrently per batch (default: 20).",
    )
    parser.add_argument(
        "--max-pages",
        type=int,
        default=None,
        help="Optional limit on number of pages to scan.",
    )
    parser.add_argument(
        "--chrome-path",
        type=str,
        default=None,
        help="Path to chrome.exe (defaults to auto-detected system Chrome).",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=".",
        help="Directory to save JSON/CSV result files (default: current directory).",
    )

    args = parser.parse_args()

    # Detect Chrome
    chrome = args.chrome_path or find_chrome()
    if not chrome:
        print("Error: Could not locate Chrome or Edge on this machine. Please specify with --chrome-path.")
        sys.exit(1)
    print(f"[+] Using Browser executable: {chrome}")

    # Determine Contest
    contest = args.contest or get_latest_contest_slug()
    print(f"[+] Target Contest: {contest}")

    # Run scraper
    matches = asyncio.run(
        scrape_and_search(
            contest_slug=contest,
            keywords=args.keywords,
            chrome_path=chrome,
            batch_size=args.batch_size,
            max_pages=args.max_pages,
            output_dir=args.output_dir,
        )
    )

    # Sort matches by Rank ascending
    matches.sort(key=lambda x: (x.get("rank") if x.get("rank") is not None and x.get("rank") > 0 else 999999, -(x.get("score") or 0)))

    # Save to JSON
    json_path = os.path.join(args.output_dir, f"leetcode_{contest}_matches.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(matches, f, indent=2, ensure_ascii=False)

    # Save to CSV
    csv_path = os.path.join(args.output_dir, f"leetcode_{contest}_matches.csv")
    fields = ["rank", "score", "username", "user_slug", "matched_keyword", "profile_url", "country", "finish_time"]
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for m in matches:
            writer.writerow({
                "rank": m.get("rank"),
                "score": m.get("score"),
                "username": m.get("username"),
                "user_slug": m.get("user_slug"),
                "matched_keyword": m.get("matched_keyword"),
                "profile_url": f"https://leetcode.com/u/{m.get('user_slug')}/",
                "country": m.get("country"),
                "finish_time": format_finish_time(m.get("finish_time")),
            })

    print(f"\n{'='*90}")
    print(f"SEARCH RESULTS: Found {len(matches)} matching candidates in {contest}")
    print(f"{'='*90}")
    print(f"{'Rank':<8} | {'Score':<6} | {'Matched':<8} | {'Username':<24} | {'User Slug / Profile ID':<25}")
    print(f"{'-'*8}-+-{'-'*6}-+-{'-'*8}-+-{'-'*24}-+-{'-'*25}")

    for m in matches:
        rank_str = str(m.get("rank"))
        score_str = str(m.get("score"))
        kw = m.get("matched_keyword", "")
        uname = (m.get("username") or "")[:24]
        uslug = (m.get("user_slug") or "")[:25]
        print(f"{rank_str:<8} | {score_str:<6} | {kw:<8} | {uname:<24} | {uslug:<25}")

    print(f"{'='*90}")
    print(f"[+] Saved JSON report to: {os.path.abspath(json_path)}")
    print(f"[+] Saved CSV report to:  {os.path.abspath(csv_path)}")


if __name__ == "__main__":
    main()
