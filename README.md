<p align="center"><img src="logo.png" alt="Merso Translator" width="160"></p>

<p align="center"><a href="#العربية">العربية</a> | <a href="#english">English</a></p>

# العربية

# مرسو للترجمة — Merso Translator

تطبيق أندرويد مجاني من قناة [Merso Gaming](https://www.youtube.com/@mersostore) لترجمة كلام الألعاب الإنجليزي للعربي على الشاشة أثناء اللعب.

## المميزات

- **ترجمة عن طريق Remote Play**: الريموت بلاي شغال جوه التطبيق نفسه، والتطبيق بيقرأ الكلام الإنجليزي ويترجمه ويبعته للتلفزيون.
- **ترجمة عن طريق الكاميرا**: وجّه كاميرا التليفون على الشاشة والترجمة تظهر على تليفونك.
- **الترجمة على التلفزيون**: بتظهر فوق اللعبة على شاشات LG وAndroid TV وGoogle TV (عن طريق Media Station X).
- **تفعيل الريموت بلاي بدون PSN**: للحسابات المحلية على الأجهزة المعدلة.
- التطبيق بالعربي والإنجليزي.

## التحميل

من صفحة [Releases](https://github.com/MersoStore/merso-translator/releases) — حمّل ملف `merso-translate.apk` وثبّته على تليفونك الأندرويد.

شرح طريقة الاستخدام بالفيديو: [Merso Gaming على يوتيوب](https://www.youtube.com/@mersostore)

## التراخيص

التطبيق بيستخدم محرك [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) مفتوح المصدر لتشغيل الريموت بلاي، ومرخّص تحت رخصة GNU Affero General Public License v3 (AGPL-3.0). المكتبة الأصلية (`libchiaki-jni.so`) مبنية من سورس Chiaki مع تعديلات صغيرة (كتم الصوت والتعامل مع السطح) — التعديلات موجودة في ملف `chiaki-patches.diff`.

التطبيق بيستخدم كمان أداة [PS5 Account Tools](https://github.com/MangoScango/ps5-account-tools) (GPL-3.0) لتفعيل الحسابات المحلية أوفلاين.

بما إن التطبيق مرتبط بكود AGPL-3.0، السورس ده منشور تحت نفس الرخصة AGPL-3.0.

## البناء

التطبيق بيتبنى بخطوات يدوية (aapt2 + kotlinc + R8 + apksigner) — شوف `build.sh` و`keep-rules.txt` (المسارات خاصة بجهاز البناء الأصلي وهتحتاج تتعدل).

التطبيق مش تابع لشركة Sony ولا لتطبيق سرد.

---

# English

# Merso Translator

A free Android app from the [Merso Gaming](https://www.youtube.com/@mersostore) channel that translates English game text to Arabic on screen while you play.

## Features

- **Remote Play translation**: Remote Play runs inside the app itself; the app reads the English text, translates it, and sends it to the TV.
- **Camera translation**: Point your phone camera at the screen and the translation shows on your phone.
- **Captions on the TV**: Shown over the game on LG, Android TV, and Google TV (via Media Station X).
- **Remote Play activation without PSN**: For local accounts on modded consoles.
- Bilingual app (Arabic / English).

## Download

From the [Releases](https://github.com/MersoStore/merso-translator/releases) page — download `merso-translate.apk` and install it on your Android phone.

Video tutorial: [Merso Gaming on YouTube](https://www.youtube.com/@mersostore)

## Licenses

This app uses the open-source [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) engine for Remote Play, licensed under the GNU Affero General Public License v3 (AGPL-3.0). The native library (`libchiaki-jni.so`) is built from upstream Chiaki with small patches (audio mute + surface handling); the patches are in `chiaki-patches.diff`.

It also embeds [PS5 Account Tools](https://github.com/MangoScango/ps5-account-tools) (GPL-3.0) for offline local-account activation.

Because the app links AGPL-3.0 code, this source is published under the AGPL-3.0 as well.

## Building

The app is built with a manual pipeline (aapt2 + kotlinc + R8 + apksigner); see `build.sh` and `keep-rules.txt` (paths are specific to the author's build machine and will need adjusting).

This app is not affiliated with Sony or with the SARD app.
