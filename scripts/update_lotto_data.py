#!/usr/bin/env python3
import sys, os
import requests
import pandas as pd
from datetime import datetime

LOTTO_API_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={}"
CSV_FILE = "app/src/main/assets/draw_kor.csv"  # ★ assets 경로로 고정

HEADERS = {
    "User-Agent": "smart-lotto-updater/1.0 (+github actions)"
}

REQUIRED_FIELDS = [
    "drwNo", "drwNoDate", "drwtNo1", "drwtNo2", "drwtNo3",
    "drwtNo4", "drwtNo5", "drwtNo6", "bnusNo"
]

COL_ORDER = ["year", "drawNo", "date", "n1", "n2", "n3", "n4", "n5", "n6", "bonus"]

def read_csv_safe(path: str) -> pd.DataFrame:
    if not os.path.exists(path):
        # 빈 CSV 초기화
        os.makedirs(os.path.dirname(path), exist_ok=True)
        df = pd.DataFrame(columns=COL_ORDER)
        df.to_csv(path, index=False, encoding="utf-8")
        return df
    try:
        return pd.read_csv(path, dtype={"drawNo": "Int64"})
    except Exception as e:
        print(f"❌ Error reading CSV: {e}")
        return pd.DataFrame(columns=COL_ORDER)

def get_latest_round() -> int:
    df = read_csv_safe(CSV_FILE)
    if df.empty:
        return 0
    try:
        return int(pd.to_numeric(df["drawNo"], errors="coerce").max())
    except Exception:
        return 0

def fetch_lotto_data(draw_no: int):
    url = LOTTO_API_URL.format(draw_no)
    try:
        r = requests.get(url, headers=HEADERS, timeout=5)
        if r.status_code != 200:
            print(f"❌ HTTP Error: {r.status_code}")
            return None
        data = r.json()
    except requests.exceptions.RequestException as e:
        print(f"❌ Network error: {e}")
        return None
    except ValueError:
        print("❌ Invalid JSON")
        return None

    if data.get("returnValue") != "success":
        print(f"⏳ Round {draw_no} not available yet (returnValue={data.get('returnValue')})")
        return None

    if not all(k in data for k in REQUIRED_FIELDS):
        print("❌ Missing required fields in response")
        return None

    return {
        "year": int(str(data["drwNoDate"])[:4]),
        "drawNo": int(data["drwNo"]),
        "date": data["drwNoDate"],
        "n1": int(data["drwtNo1"]),
        "n2": int(data["drwtNo2"]),
        "n3": int(data["drwtNo3"]),
        "n4": int(data["drwtNo4"]),
        "n5": int(data["drwtNo5"]),
        "n6": int(data["drwtNo6"]),
        "bonus": int(data["bnusNo"]),
    }

def atomic_write_csv(df: pd.DataFrame, path: str):
    tmp = path + ".tmp"
    df.to_csv(tmp, index=False, encoding="utf-8")
    os.replace(tmp, path)

def update_csv(new_row: dict) -> bool:
    df = read_csv_safe(CSV_FILE)
    row_df = pd.DataFrame([new_row])[COL_ORDER]
    df = pd.concat([row_df, df], ignore_index=True)
    df = df.drop_duplicates(subset="drawNo", keep="first")
    df["drawNo"] = pd.to_numeric(df["drawNo"], errors="coerce").astype("Int64")
    df = df.sort_values("drawNo", ascending=False, na_position="last").reset_index(drop=True)

    try:
        atomic_write_csv(df, CSV_FILE)
        print(f"✅ Added round {new_row['drawNo']} ({new_row['date']})")
        nums = f"{new_row['n1']}, {new_row['n2']}, {new_row['n3']}, {new_row['n4']}, {new_row['n5']}, {new_row['n6']} + {new_row['bonus']}"
        print(f"   Numbers: {nums}")
        return True
    except Exception as e:
        print(f"❌ Error writing CSV: {e}")
        return False

def main():
    print("🎰 Lotto Data Update Script")
    print(f"📅 {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')} UTC")
    print("=" * 60)

    current_round = get_latest_round()
    next_round = current_round + 1
    print(f"📊 Latest in CSV: {current_round} → 🔍 checking {next_round}")

    data = fetch_lotto_data(next_round)
    if data is None:
        # 공개 전은 정상: 변경 없음 → 0 종료 (폴링 계속됨)
        print("ℹ️ No new data yet (normal before publish).")
        sys.exit(0)

    if update_csv(data):
        print("=" * 60)
        print("🎉 Update completed successfully!")
        sys.exit(0)
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()
