---

# 📊 Auto EDA – Android CSV 자동 분석 앱

**CSV 파일을 업로드하면 자동으로 통계·시각화·데이터 품질 분석을 수행해주는 Android 앱**

Auto EDA는 데이터 과학을 처음 접하는 사용자부터, 빠르게 EDA 결과를 확인하고 싶은 전문가까지 모두를 위한 **모바일 자동 EDA(Android)** 애플리케이션입니다.
CSV 업로드만 하면 주요 통계, 결측치 분석, 히스토그램, 타깃 분석을 자동으로 생성합니다.

---

## 🚀 주요 기능 (Features)

### ✔ 1. CSV 파일 업로드

* 로컬 파일 시스템에서 CSV 선택
* 파일명 및 경로 표시
* 잘못된 파일 선택 시 에러 메시지 출력

### ✔ 2. 컬럼 통계 (Column Statistics)

각 변수별 기본 통계량 자동 계산:

* 평균, 중앙값, 최댓값, 최솟값
* 표준편차
* 값 개수(count)
* 데이터 타입 자동 판별

### ✔ 3. 데이터 품질 분석 (Data Quality Check)

* 결측치 비율 시각화
* 이상치 탐지
* 중복 행 탐지
* 데이터 타입 오류 검사

### ✔ 4. 히스토그램 시각화 (Histogram)

* 숫자형 컬럼 자동 탐색
* 각 컬럼별 히스토그램 생성
* 스크롤 방식으로 열람 가능

### ✔ 5. 타깃 분석 (Target Analysis)

* 사용자가 원하는 타깃 변수 선택
* 타깃 기준 그룹별 통계 비교
* 카테고리형/숫자형 자동 분기 처리
* 타깃 기반 데이터 분포 요약

---

## 📱 앱 화면 구성 (Screens)

### ▶ MainActivity

* CSV 업로드 버튼
* 분석 화면으로 이동 버튼

### ▶ UploadActivity / UploadScreen

* 파일 선택
* 선택된 파일 정보 확인

### ▶ ColumnStatsActivity

* 전체 컬럼 요약 통계 표시

### ▶ DataQualityActivity

* 결측값·이상치·데이터 타입 오류 등 품질 점검

### ▶ HistogramActivity

* 숫자형 변수 히스토그램 자동 생성

### ▶ TargetAnalysisActivity

* 타겟 변수 선택
* 타깃 기반 그룹별 통계 제공

---

## 🧱 프로젝트 구조 (Project Structure)

```
app/
 ├─ java/com/example/autoeda/
 │   ├─ MainActivity.kt
 │   ├─ UploadActivity.kt
 │   ├─ TargetAnalysisActivity.kt
 │   ├─ ColumnStatsActivity.kt
 │   ├─ DataQualityActivity.kt
 │   ├─ HistogramActivity.kt
 │   ├─ utils/
 │   │   ├─ CsvLoader.kt
 │   │   ├─ DataParser.kt
 │   │   └─ ChartUtils.kt
 │   └─ viewmodel/
 │       └─ EdaViewModel.kt
 └─ res/
     ├─ layout/
     ├─ drawable/
     └─ values/
```

---

## 🛠 기술 스택 (Tech Stack)

| 분야     | 사용 기술                             |
| ------ | --------------------------------- |
| 언어     | Kotlin                            |
| UI     | XML 기반 UI + 일부 Jetpack Compose    |
| 데이터 처리 | OpenCSV, Kotlin 표준 라이브러리          |
| 아키텍처   | MVVM (ViewModel 사용)               |
| 시각화    | MPAndroidChart (또는 Canvas 기반 커스텀) |
| 기타     | LiveData / StateFlow              |

---

## 🧪 CSV 예시 파일

* 앱 동작 테스트를 위한 샘플 CSV 포함
* 테스트 파일은 `/assets` 또는 `/sample_data` 폴더에 위치

---

## 🤝 기여 방법 (Contributing)

1. Issue 생성
2. Pull Request 제출
3. 정리된 코드 리뷰 후 병합

---

## 📄 라이선스

MIT License
자유롭게 수정·재배포 가능

---
