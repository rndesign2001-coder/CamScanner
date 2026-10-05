# 📚 Kitob Skaner

Kitob sahifalarini suratga olib (yoki tayyor rasmlarni tanlab) ularning **matnini ajratadigan**, oddiy va **qalin (bold)** yozuvlarni farqlab, matnni **qayta sahifalab chiroyli PDF kitob** yaratadigan Android ilova.

Barcha ishlov telefonning o'zida (CPU/RAM) bajariladi — internet kerak emas, rasmlar hech qayerga yuborilmaydi.

## Imkoniyatlar

| | |
|---|---|
| 📷 **Aqlli skaner** | Google ML Kit Document Scanner: sahifa qirralarini avtomatik topish, perspektivani to'g'rilash, soyalarni tozalash. Galereyadan rasm yuklash, oddiy kamera, boshqa ilovadan "Ulashish". |
| 🔤 **Oflayn OCR** | Tesseract 5 (LSTM, `tessdata_best` modellari): **Oʻzbek (lotin)**, **Ўзбек (кирилл)**, **Русский**, **English**. Avto rejimda kitob tili o'zi aniqlanadi. |
| 𝐁 **Qalin matn** | Har bir so'z shtrixlarining qalinligi o'lchanadi va satr balandligiga nisbatan solishtiriladi — qalin so'zlar PDF/Word'da ham qalin bo'lib chiqadi. Sezgirlik sozlanadi. |
| 📐 **Tuzilmani tiklash** | Sarlavhalar (shrift o'lchami, qalinlik, markazlash), xatboshilar, she'rlar, satr oxiridagi bo'g'in ko'chirishlarni (de-) birlashtirish, sahifalar orasida uzilgan xatboshilarni ulash, kolontitul va sahifa raqamlarini olib tashlash, kitobdagi rasmlarni kesib olish. |
| 📘 **Kitob PDF** | Matn qayta sahifalanadi: A4 / B5 / A5 / 6×9", Noto Serif yoki Noto Sans, kenglik bo'yicha tekislash, bo'g'in ko'chirish, titul sahifa, sahifa raqamlari, mundarija. **Matnni nusxalash mumkin.** |
| 🔍 **Qidiriladigan skan PDF** | Asl sahifa rasmi + ko'rinmas matn qatlami (nusxalash va qidirish ishlaydi). |
| 🖼 **Oddiy skan PDF** | Faqat tozalangan rasmlar (Asl / Yorqin / Kulrang / Oq-qora filtrlar, sifat tanlovi). |
| 📝 **Word / TXT** | Tahrirlash uchun DOCX (sarlavhalar, qalin matn, rasmlar) va oddiy matn. |
| ✏️ **Tahrirlash** | Har bir sahifa matnini ko'rish va tuzatish (`# sarlavha`, `**qalin**`). Sahifalarni burish, tartibini o'zgartirish, o'chirish, qayta aniqlash. |

## APK'ni yuklab olish

Har bir push'da GitHub Actions APK yig'adi:

* **Releases** bo'limida eng so'nggi `KitobSkaner-1.0.N.apk` fayli;
* yoki **Actions → Build APK → Artifacts**.

## O'zingiz yig'ish

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Yig'ish vaqtida Tesseract modellari (`eng`, `rus`, `uzb`, `uzb_cyrl` — `tessdata_best`) avtomatik yuklab olinadi va APK ichiga joylanadi.
Talablar: JDK 17, Android SDK 35.

## Texnologiyalar

Kotlin • Jetpack Compose (Material 3) • WorkManager (fon rejimida OCR) • Tesseract4Android (Tesseract 5.5) •
ML Kit Document Scanner • PdfBox-Android • Android PdfDocument/StaticLayout • Noto fontlari (OFL).

## Imzo kaliti

`keystore/release.jks` — yangilanishlar bir-birining ustiga o'rnatilishi uchun repozitoriyda saqlangan umumiy kalit.
Play Market uchun o'z kalitingizdan foydalaning: `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` muhit o'zgaruvchilari.
