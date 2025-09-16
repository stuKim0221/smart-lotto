import requests
import pandas as pd
from datetime import datetime
import time
import json

def get_latest_draw_number():
    """현재 CSV에서 가장 최신 회차 번호를 가져옴"""
    try:
        df = pd.read_csv('draw_kor.csv')
        return df['drawNo'].max()
    except:
        return 1

def fetch_lotto_data(draw_no):
    """동행복권 API에서 특정 회차 데이터 가져오기"""
    url = f"https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={draw_no}"
    
    try:
        response = requests.get(url, timeout=10)
        data = response.json()
        
        if data['returnValue'] == 'success':
            draw_date = data['drwNoDate']
            year = datetime.strptime(draw_date, '%Y-%m-%d').year
            
            return {
                'year': year,
                'drawNo': draw_no,
                'date': draw_date,
                'n1': data['drwtNo1'],
                'n2': data['drwtNo2'],
                'n3': data['drwtNo3'],
                'n4': data['drwtNo4'],
                'n5': data['drwtNo5'],
                'n6': data['drwtNo6'],
                'bonus': data['bnusNo']
            }
        else:
            return None
    except Exception as e:
        print(f"Error fetching draw {draw_no}: {e}")
        return None

def update_lotto_csv():
    """CSV 파일 업데이트"""
    current_draw = get_latest_draw_number()
    print(f"Current latest draw: {current_draw}")
    
    new_data = []
    check_draw = current_draw + 1
    max_attempts = 3
    
    for attempt in range(max_attempts):
        print(f"Checking draw {check_draw}...")
        data = fetch_lotto_data(check_draw)
        
        if data:
            new_data.append(data)
            print(f"Found new draw {check_draw}: {data['date']}")
            check_draw += 1
        else:
            print(f"No data for draw {check_draw}")
            break
        
        time.sleep(2)
    
    if new_data:
        try:
            df_existing = pd.read_csv('draw_kor.csv')
        except:
            df_existing = pd.DataFrame()
        
        df_new = pd.DataFrame(new_data)
        df_updated = pd.concat([df_existing, df_new], ignore_index=True)
        df_updated = df_updated.drop_duplicates(subset=['drawNo'])
        df_updated = df_updated.sort_values('drawNo', ascending=False)
        
        df_updated.to_csv('draw_kor.csv', index=False)
        print(f"Added {len(new_data)} new draws to CSV")
        return len(new_data)
    else:
        print("No new data to add")
        return 0

if __name__ == "__main__":
    updated_count = update_lotto_csv()
    print(f"Update completed. {updated_count} new draws added.")
