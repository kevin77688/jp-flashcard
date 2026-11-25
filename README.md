# 日語字卡 (jp-flashcard)

個人用的日文單字卡 Android App：五十音、自訂單字（內建羅馬拼音輸入與離線詞典）、1–5 分熟悉度的間隔複習，平板與手機透過 Firebase 同步。

## 建置

需要 JDK 17 與 Android SDK（compileSdk 37.2）。

```bash
./tools/fetch_voicevox.sh   # 第一次建置前：下載 VOICEVOX 語音引擎（約 110 MB，不放進 git）
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

## Mac 網頁版

`web/` 是在 Mac 上新增、修改、刪除單字的網頁，直接讀寫同一份 Firestore 資料，平板和手機幾秒內就會更新。只改內容欄位，不會動到複習進度。

```bash
./tools/web.sh   # 開在 http://localhost:5173（Google 登入只允許 localhost 和 Firebase 網域）
```

`web/firebase-config.js` 同樣不在 repo 裡，請照 `web/firebase-config.example.js`，從 Firebase 主控台的網頁應用程式設定填入。

## 發音

預設用 [VOICEVOX](https://voicevox.hiroshiba.jp/)（神經網路語音，聲音：四国めたん），在手機上離線合成，Open JTalk 詞典提供重音，所以單字的高低音比系統語音準。有漢字的單字用漢字合成，確認讀音一致才採用；只有假名的單字會先查詞典的漢字寫法來取得重音。合成過的單字會快取。設定裡可以隨時切回系統語音。

聲音使用規約要求標示「VOICEVOX:四国めたん」（顯示在設定 → 關於）。

## 詞典

`app/src/main/assets/jmdict.db.gz` 由 `tools/build_dictionary.py` 從 [jmdict-simplified](https://github.com/scriptin/jmdict-simplified) 的英文版 JSON 產生。打包時 Android 會自動解壓成 `jmdict.db`。

JMdict © Electronic Dictionary Research and Development Group，依 CC BY-SA 4.0 授權使用。
