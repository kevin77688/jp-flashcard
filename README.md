# 日語字卡 (jp-flashcard)

個人用的日文單字卡 Android App：五十音、自訂單字（內建羅馬拼音輸入與離線詞典）、1–5 分熟悉度的間隔複習，平板與手機透過 Firebase 同步。

## 建置

需要 JDK 17 與 Android SDK（compileSdk 37.2）。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`app/google-services.json` 不在 repo 裡（已 gitignore），請從 Firebase 主控台（專案 `jp-flashcard-1c8f9`）下載後放進 `app/`。

## 架構

- `domain/`：羅馬拼音 ⇄ 假名、SRS 排程、振り仮名切分、五十音資料（純 Kotlin，有單元測試）
- `data/`：Firebase Auth（Credential Manager 的 Google 登入）、Firestore（離線快取即本地資料庫）、TTS、JMdict 詞典、OpenAI 相容 LLM
- `ui/`：Jetpack Compose 畫面；寬度 ≥ 600dp 時改用側邊導覽與雙欄版面

Firestore 資料都放在 `/users/{uid}/…`，規則見 `firebase/firestore.rules`。

## 詞典

`app/src/main/assets/jmdict.db.gz` 由 `tools/build_dictionary.py` 從 [jmdict-simplified](https://github.com/scriptin/jmdict-simplified) 的英文版 JSON 產生。打包時 Android 會自動解壓成 `jmdict.db`。

JMdict © Electronic Dictionary Research and Development Group，依 CC BY-SA 4.0 授權使用。
