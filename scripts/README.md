# Lotto Data Update Scripts

## 📋 개요

이 디렉토리는 로또 데이터를 자동으로 업데이트하는 스크립트를 포함합니다.

## 🔄 자동 업데이트 시스템

### 실행 스케줄 (GitHub Actions)

매주 토요일 **1분 간격**으로 자동 실행:
- **20:40~21:10 KST** (31회 시도)
- 데이터 업데이트 성공 시 자동 중단

### 동작 방식

1. **스마트 중복 방지**: 이번 주에 이미 업데이트되었는지 Git 커밋 히스토리로 확인
2. **API 체크**: 새 회차 데이터가 공개되었는지 확인
3. **업데이트**: 데이터가 있으면 CSV 업데이트 후 커밋
4. **자동 중단**: 성공하면 그 주의 남은 스케줄 스킵

### 왜 이 방식인가?

로또 추첨 결과는 **20:35 이후 랜덤한 시간**에 공개됩니다:
- 빠르면 20:36
- 늦으면 20:50 이후

따라서 고정된 시간이 아닌 **1분 간격 재시도 방식**으로 최대한 빨리 업데이트합니다.

## 🛠️ 로컬 테스트

### 1. Python 환경 설정
```bash
pip install requests pandas
```

### 2. 스크립트 실행
```bash
cd scripts
python update_lotto_data.py
```

### 3. 예상 출력
```
🎰 Lotto Data Update Script
📅 Execution time: 2025-10-04 20:45:00
============================================================
📊 Current latest round in CSV: 1183
🔍 Checking for round 1184...
✅ Successfully added round 1184 to CSV
   Date: 2025-10-04
   Numbers: 3, 12, 25, 33, 38, 42 + 18
============================================================
🎉 Update completed successfully!
```

## 📁 파일 구조

```
scripts/
├── update_lotto_data.py   # 메인 업데이트 스크립트
└── README.md               # 이 파일

.github/workflows/
└── update-lotto-data.yml   # GitHub Actions 워크플로우
```

## 🔍 문제 해결

### 업데이트가 안 될 때

1. **API 응답 확인**
   ```bash
   curl "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=1184"
   ```

2. **로그 확인** (GitHub Actions)
   - Repository → Actions 탭
   - 최신 워크플로우 실행 클릭

3. **수동 실행**
   - Actions 탭 → "Update Lotto Data" 선택
   - "Run workflow" 버튼 클릭

### 중복 업데이트 방지 로직

Git 커밋 메시지에서 주차 확인:
```bash
git log -1 --grep="Auto-update lotto data" --format=%cd --date=format:'%Y-W%U'
```

## 📊 데이터 형식

### CSV 구조 (draw_kor.csv)
```csv
year,drawNo,date,n1,n2,n3,n4,n5,n6,bonus
2025,1184,2025-10-04,3,12,25,33,38,42,18
2025,1183,2025-09-27,7,15,22,28,35,41,9
...
```

### API 응답 예시
```json
{
  "returnValue": "success",
  "drwNo": 1184,
  "drwNoDate": "2025-10-04",
  "drwtNo1": 3,
  "drwtNo2": 12,
  "drwtNo3": 25,
  "drwtNo4": 33,
  "drwtNo5": 38,
  "drwtNo6": 42,
  "bnusNo": 18
}
```

## 🚀 배포

1. **파일 푸시**
   ```bash
   git add .github/workflows/update-lotto-data.yml
   git add scripts/
   git commit -m "Add smart lotto data update system"
   git push origin main
   ```

2. **GitHub Actions 확인**
   - Repository Settings → Actions → General
   - "Allow all actions and reusable workflows" 활성화

3. **다음 토요일 대기**
   - 20:40부터 자동 실행 시작
   - Actions 탭에서 실시간 로그 확인

## 📈 개선 사항

| 항목 | 기존 (20:50 고정) | 개선 (20:40~21:10 1분 간격) |
|------|------------------|---------------------------|
| 최소 업데이트 시간 | 15분 지연 | **1분 이내** |
| 최대 업데이트 시간 | 15분 지연 | 35분 지연 (극히 드묾) |
| 평균 업데이트 시간 | 15분 | **5~10분** |
| 시도 횟수 | 1회 | **31회** (성공 시 자동 중단) |
| 중복 실행 | 없음 | **자동 방지** |

기존 방식 대비:
- ✅ **빠른 업데이트**: 데이터 공개 즉시 반영 (평균 5~10분 이내)
- ✅ **중복 방지**: 이미 업데이트된 주는 스킵
- ✅ **재시도 로직**: API 응답 없으면 1분 후 재시도 (최대 31회)
- ✅ **자동 중단**: 성공하면 그 주의 남은 스케줄 취소

## 📞 지원

문제 발생 시:
1. GitHub Issues에 로그 첨부하여 리포트
2. Actions 탭에서 워크플로우 실행 로그 확인
3. 수동 실행으로 즉시 업데이트 가능
