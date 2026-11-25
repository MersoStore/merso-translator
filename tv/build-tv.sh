#!/bin/bash
set -e
export JAVA_HOME=~/workspace/tools/jdk
export PATH=$JAVA_HOME/bin:$PATH
unset JARVIS_TRACE_CONTEXT
SDK=~/workspace/tools/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
AND=~/workspace/android
PROJ=$AND/merso-tv
OUT=$PROJ/out
PAHO=$AND/jars/org.eclipse.paho.client.mqttv3-1.2.5.jar
rm -rf $OUT && mkdir -p $OUT/flat $OUT/gen $OUT/classes $OUT/dex

echo "== aapt2 compile res =="
$BT/aapt2 compile --dir $PROJ/app/src/main/res -o $OUT/flat/res.zip

echo "== aapt2 link =="
$BT/aapt2 link \
  --manifest $PROJ/app/src/main/AndroidManifest.xml \
  -I $AJAR \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 13 --version-name 2.2.0 \
  --java $OUT/gen \
  -o $OUT/base.apk \
  $OUT/flat/res.zip

echo "== kotlinc =="
$AND/kotlinc/bin/kotlinc -cp "$AJAR:$PAHO" -d $OUT/classes \
  $(find $PROJ/app/src/main/java -name "*.kt") $(find $OUT/gen -name "*.java") 2>$OUT/kotlinc.log || { cat $OUT/kotlinc.log; exit 1; }

echo "== d8 =="
(cd $OUT/classes && jar cf $OUT/app.jar .)
$BT/d8 --lib $AJAR --min-api 26 --output $OUT/dex $OUT/app.jar $PAHO \
  $AND/kotlinc/lib/kotlin-stdlib.jar $AND/kotlinc/lib/kotlin-stdlib-jdk7.jar $AND/kotlinc/lib/kotlin-stdlib-jdk8.jar \
  2>$OUT/d8.log || { cat $OUT/d8.log; exit 1; }

echo "== package =="
cd $OUT
cp base.apk unsigned.apk
zip -q unsigned.apk -j dex/classes*.dex
mkdir -p resjar
unzip -o -q "$PAHO" "*.properties" "META-INF/services/*" -d resjar 2>/dev/null || true
(cd resjar && find . -type f | sed 's|^\./||' | zip -q ../unsigned.apk -@)
# app assets (Cairo font for captions)
if [ -d $PROJ/app/src/main/assets ]; then mkdir -p assets && cp -r $PROJ/app/src/main/assets/. assets/ && zip -q -r unsigned.apk assets; fi
LD_LIBRARY_PATH=/home/hatch/workspace/tools /home/hatch/workspace/tools/zipalign35 -f -P 16 4 unsigned.apk aligned.apk

echo "== sign (release) =="
/home/hatch/workspace/tools/bt35/apksigner sign --ks $AND/release.keystore --ks-key-alias merso --ks-pass file:$AND/release-keystore-password.txt --key-pass file:$AND/release-keystore-password.txt --out $OUT/merso-translator-tv.apk aligned.apk
/home/hatch/workspace/tools/bt35/apksigner verify --verbose $OUT/merso-translator-tv.apk | head -3
$BT/aapt2 dump badging $OUT/merso-translator-tv.apk | head -5
ls -la $OUT/merso-translator-tv.apk
echo BUILD_OK
