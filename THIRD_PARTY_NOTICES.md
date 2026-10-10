# Composants tiers

Equily embarque des bibliothèques libres. Leurs licences restent applicables indépendamment de celles du code Equily. Les textes OCR sont accessibles hors ligne dans Réglages → Licences.

## OCR

- [Tesseract4Android 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0), Apache-2.0 ; wrapper JNI mono-thread de Tesseract 5.5.1. Texte conservé dans `app/src/main/assets/licenses/tesseract4android-license.txt`.
- [Tesseract](https://github.com/tesseract-ocr/tesseract/tree/5.5.1), Apache-2.0.
- [Modèles tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), Apache-2.0, commit `87416418657359cb625c412a48b6e1d6d41c29bd`. Texte dans `app/src/main/assets/licenses/tessdata-license.txt`. Assets complets embarqués, pas téléchargés sur le téléphone.

| Modèle | SHA-256 |
| --- | --- |
| eng | `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2` |
| fra | `ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca` |
| nld | `ced0e5e046a84c908a6aa7accbef9a232c4a5d9a8276691b81c6ee64d02963f6` |

Le moteur natif inclut aussi Leptonica 1.85.0, libjpeg 9f et libpng 1.6.48. Leurs textes de licence upstream sont conservés dans les assets `licenses`. Les bibliothèques sont redistribuées sans modification.

## Autres dépendances

AndroidX/Compose/Room/DataStore/Navigation/Biometric et Tink : Apache-2.0. PDFBox Android 2.0.27.0 : Apache-2.0, basé sur Apache PDFBox. Les versions explicites sont dans `app/build.gradle.kts`, les versions Compose dans le BOM indiqué dans `DEVELOPMENT.md`. Le manifeste n’autorise ni INTERNET ni accès général au stockage. Aucun fichier de document utilisateur n’est envoyé à ces projets.
