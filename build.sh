#!/bin/bash
set -e
export JAVA_HOME=~/workspace/tools/jdk
export PATH=$JAVA_HOME/bin:$PATH
unset JARVIS_TRACE_CONTEXT
SDK=~/workspace/tools/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
PROJ=~/workspace/android/merso-translate
AND=~/workspace/android
OUT=$AND/out
rm -rf $OUT && mkdir -p $OUT/flat $OUT/gen $OUT/classes $OUT/dex

echo "== AAR packages =="
PKGS=""
for aar in $AND/deps/*.aar; do
  p=$(unzip -p "$aar" AndroidManifest.xml 2>/dev/null | grep -oE 'package="[^"]+"' | head -1 | sed 's/package="//;s/"//')
  if [ -n "$p" ]; then PKGS="$PKGS:$p"; fi
done
PKGS=$(echo "$PKGS" | sed 's/^://')
echo "$PKGS" | tr ':' '\n' | head -50

echo "== aapt2 compile app res =="
$BT/aapt2 compile --dir $PROJ/app/src/main/res -o $OUT/flat/app.zip
FLATS="$OUT/flat/app.zip"
for d in $AND/res-aar/*/; do
  name=$(basename "$d")
  if [ -n "$(ls -A "$d" 2>/dev/null)" ]; then
    $BT/aapt2 compile --dir "$d" -o $OUT/flat/$name.zip 2>>$OUT/aapt2-compile.log || echo "res compile failed: $name"
    [ -f $OUT/flat/$name.zip ] && FLATS="$FLATS $OUT/flat/$name.zip"
  fi
done

echo "== aapt2 link =="
$BT/aapt2 link -o $OUT/base.apk -I $AJAR --manifest $PROJ/app/src/main/AndroidManifest.xml \
  --java $OUT/gen --extra-packages "$PKGS" --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 23 --version-name 1.0 \
  $FLATS 2>$OUT/aapt2-link.log || { cat $OUT/aapt2-link.log; exit 1; }
find $OUT/gen -name "*.java" | head

echo "== kotlinc =="
CP="$AJAR:$(ls $AND/jars/*.jar | tr '\n' ':')"
$AND/kotlinc/bin/kotlinc -cp "$CP" -d $OUT/classes \
  $(find $PROJ/app/src/main/java -name "*.kt") $(find $OUT/gen -name "*.java") 2>$OUT/kotlinc.log || { cat $OUT/kotlinc.log; exit 1; }

echo "== r8 (obfuscate) / d8 fallback =="
(cd $OUT/classes && jar cf $OUT/app.jar .)
if java -cp $AND/r8.jar com.android.tools.r8.R8 --output $OUT/dex --lib $AJAR --min-api 26 --pg-conf $AND/keep-rules.txt $OUT/app.jar $AND/jars/*.jar $AND/kotlinc/lib/kotlin-stdlib.jar $AND/kotlinc/lib/kotlin-stdlib-jdk7.jar $AND/kotlinc/lib/kotlin-stdlib-jdk8.jar 2>$OUT/r8.log; then
  echo R8_OK
else
  echo R8_FALLBACK_D8; tail -5 $OUT/r8.log
  $BT/d8 --lib $AJAR --min-api 26 --output $OUT/dex $OUT/app.jar $AND/jars/*.jar $AND/kotlinc/lib/kotlin-stdlib.jar $AND/kotlinc/lib/kotlin-stdlib-jdk7.jar $AND/kotlinc/lib/kotlin-stdlib-jdk8.jar 2>$OUT/d8.log || { cat $OUT/d8.log; exit 1; }
fi
ls -la $OUT/dex

echo "== package =="
cd $OUT
cp base.apk unsigned.apk
zip -q unsigned.apk -j dex/classes*.dex
# AAR assets (ML Kit OCR models etc.)
for aar in $AND/deps/*.aar; do unzip -o -q "$aar" "assets/*" -d . 2>/dev/null || true; done
# app assets (e.g. embedded PS5 payload ELF)
if [ -d $PROJ/app/src/main/assets ]; then mkdir -p assets && cp -r $PROJ/app/src/main/assets/. assets/; fi
if [ -d assets ]; then zip -q -r unsigned.apk assets; fi
# jar resources (.properties bundles, e.g. Paho nls)
mkdir -p resjar
for j in $AND/jars/*.jar; do unzip -o -q "$j" "*.properties" "META-INF/services/*" -d resjar 2>/dev/null || true; done
(cd resjar && find . -type f | sed 's|^\./||' | zip -q ../unsigned.apk -@)
# native libs stored uncompressed
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  if [ -d $AND/lib/$abi ]; then
    mkdir -p lib/$abi && cp $AND/lib/$abi/*.so lib/$abi/
    zip -q -0 unsigned.apk lib/$abi/*.so
  fi
done
LD_LIBRARY_PATH=/home/hatch/workspace/tools /home/hatch/workspace/tools/zipalign35 -f -P 16 4 unsigned.apk aligned.apk

echo "== sign (release) =="
/home/hatch/workspace/tools/bt35/apksigner sign --ks $AND/release.keystore --ks-key-alias merso --ks-pass file:$AND/release-keystore-password.txt --key-pass file:$AND/release-keystore-password.txt --out $OUT/merso-translate.apk aligned.apk
/home/hatch/workspace/tools/bt35/apksigner verify --verbose $OUT/merso-translate.apk | head -3
$BT/aapt2 dump badging $OUT/merso-translate.apk | head -5
ls -la $OUT/merso-translate.apk
echo BUILD_OK
