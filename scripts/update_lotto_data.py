#!/usr/bin/env python3
"""
로또 데이터 자동 업데이트 스크립트

매주 토요일 20:40부터 5분 간격으로 실행되며,
새로운 회차 데이터가 공개되면 CSV를 업데이트하고 종료합니다.
"""

import sys
import os
import requests
import pandas as pd
from datetime import datetime
import time

# 공식 로또 API
LOTTO_API_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={}"

# CSV 파일 경로
CSV_FILE = "draw_kor.csv"

def get_latest_round():
    """CSV 파일에서 최신 회차 번호를 가져옴"""
    try:
        df = pd.read_csv(CSV_FILE)
        if not df.empty:
            return int(df['drawNo'].max())
        return 0
    except Exception as e:
        print(f"❌ Error reading CSV: {e}")
        return 0

def fetch_lotto_data(draw_no):
    """공식 API에서 특정 회차 데이터 가져오기"""
    try:
        url = LOTTO_API_URL.format(draw_no)
        response = requests.get(url, timeout=10)

        if response.status_code != 200:
            print(f"❌ HTTP Error: {response.status_code}")
            return None

        data = response.json()

        # 성공 여부 확인
        if data.get('returnValue') != 'success':
            print(f"⏳ Round {draw_no} not available yet (returnValue: {data.get('returnValue')})")
            return None

        # 필수 데이터 확인
        required_fields = ['drwNo', 'drwNoDate', 'drwtNo1', 'drwtNo2', 'drwtNo3',
                          'drwtNo4', 'drwtNo5', 'drwtNo6', 'bnusNo']

        if not all(field in data for field in required_fields):
            print(f"❌ Missing required fields in response")
            return None

        return {
            'year': int(data['drwNoDate'][:4]),
            'drawNo': int(data['drwNo']),
            'date': data['drwNoDate'],
            'n1': int(data['drwtNo1']),
            'n2': int(data['drwtNo2']),
            'n3': int(data['drwtNo3']),
            'n4': int(data['drwtNo4']),
            'n5': int(data['drwtNo5']),
            'n6': int(data['drwtNo6']),
            'bonus': int(data['bnusNo'])
        }

    except requests.exceptions.Timeout:
        print(f"⏱️ Timeout while fetching round {draw_no}")
        return None
    except requests.exceptions.RequestException as e:
        print(f"❌ Network error: {e}")
        return None
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return None

def update_csv(new_data):
    """CSV 파일에 새 데이터 추가"""
    try:
        # 기존 CSV 읽기
        df = pd.read_csv(CSV_FILE)

        # 새 데이터를 DataFrame으로 변환
        new_row = pd.DataFrame([new_data])

        # 맨 위에 추가 (최신 데이터가 위로)
        df = pd.concat([new_row, df], ignore_index=True)

        # CSV 저장 (인덱스 제외)
        df.to_csv(CSV_FILE, index=False)

        print(f"✅ Successfully added round {new_data['drawNo']} to CSV")
        print(f"   Date: {new_data['date']}")
        print(f"   Numbers: {new_data['n1']}, {new_data['n2']}, {new_data['n3']}, "
              f"{new_data['n4']}, {new_data['n5']}, {new_data['n6']} + {new_data['bonus']}")
        return True

    except Exception as e:
        print(f"❌ Error updating CSV: {e}")
        return False

def main():
    """메인 실행 함수"""
    print(f"🎰 Lotto Data Update Script")
    print(f"📅 Execution time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"=" * 60)

    # 1. 현재 CSV의 최신 회차 확인
    current_round = get_latest_round()
    print(f"📊 Current latest round in CSV: {current_round}")

    # 2. 다음 회차 번호 계산
    next_round = current_round + 1
    print(f"🔍 Checking for round {next_round}...")

    # 3. API에서 다음 회차 데이터 가져오기
    new_data = fetch_lotto_data(next_round)

    if new_data is None:
        print(f"⏳ Round {next_round} is not available yet")
        print(f"ℹ️ This is normal - the draw results are published randomly after 20:35")
        print(f"✅ Will retry in the next scheduled run")
        sys.exit(1)  # 실패 코드로 종료 (재시도 필요)

    # 4. CSV 업데이트
    if update_csv(new_data):
        print(f"=" * 60)
        print(f"🎉 Update completed successfully!")
        print(f"✅ Round {next_round} has been added to {CSV_FILE}")
        sys.exit(0)  # 성공 코드로 종료
    else:
        print(f"❌ Failed to update CSV")
        sys.exit(1)  # 실패 코드로 종료

if __name__ == "__main__":
    main()
