#!/bin/bash
# Downloads the VOICEVOX speech engine pieces the app bundles (not committed: ~110 MB).
#   - voicevox_core Java/Android AAR       -> third_party/voicevox/maven (local Maven repo)
#   - VOICEVOX's ONNX Runtime for Android  -> app/src/main/jniLibs/arm64-v8a/
#   - Open JTalk dictionary + voice model  -> app/src/main/assets/voicevox/
# Voice model terms: credit "VOICEVOX:四国めたん" (shown in the app's 設定 → 關於).
set -euo pipefail
cd "$(dirname "$0")/.."

CORE=0.17.1
ORT=1.23.2
VVM=0.17.1
DL=third_party/voicevox/downloads
mkdir -p "$DL" third_party/voicevox/maven app/src/main/jniLibs/arm64-v8a app/src/main/assets/voicevox/dic

[ -f "$DL/java_packages.zip" ] || gh release download "$CORE" -R VOICEVOX/voicevox_core -p java_packages.zip -D "$DL"
[ -f "$DL/voicevox_onnxruntime-android-arm64-$ORT.tgz" ] || gh release download "voicevox_onnxruntime-$ORT" -R VOICEVOX/onnxruntime-builder -p "voicevox_onnxruntime-android-arm64-$ORT.tgz" -D "$DL"
[ -f "$DL/0.vvm" ] || gh release download "$VVM" -R VOICEVOX/voicevox_vvm -p 0.vvm -p TERMS.txt -D "$DL"
[ -f "$DL/open_jtalk_dic_utf_8-1.11.tar.gz" ] || gh release download v1.11.1 -R r9y9/open_jtalk -p open_jtalk_dic_utf_8-1.11.tar.gz -D "$DL"

unzip -q -o "$DL/java_packages.zip" -d third_party/voicevox/maven
tar -xzf "$DL/voicevox_onnxruntime-android-arm64-$ORT.tgz" -C "$DL"
cp "$DL/voicevox_onnxruntime-android-arm64-$ORT/lib/libvoicevox_onnxruntime.so" app/src/main/jniLibs/arm64-v8a/
tar -xzf "$DL/open_jtalk_dic_utf_8-1.11.tar.gz" -C "$DL"
cp "$DL"/open_jtalk_dic_utf_8-1.11/{char.bin,left-id.def,matrix.bin,pos-id.def,rewrite.def,right-id.def,sys.dic,unk.dic} app/src/main/assets/voicevox/dic/
cp "$DL/0.vvm" app/src/main/assets/voicevox/0.vvm
echo "VOICEVOX files ready."
