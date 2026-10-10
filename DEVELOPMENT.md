# Développement

## Environnement inspecté le 10 octobre 2026

Ubuntu/Linux x86_64, Java OpenJDK 17, SDK `/opt/android-sdk`, plateformes 33–36 et build-tools 34–36 installés, environ 313 Go disponibles au début. Dépôt GitHub initialement vide. GitHub CLI authentifié ; aucun secret n’est copié dans le dépôt. Aucun appareil physique et aucune accélération KVM disponible. Émulateur et image Android 35 default x86_64 installés pour tenter les vérifications sans accélération.

## Versions reproductibles

Gradle Wrapper 8.13, AGP 8.13.1, Kotlin/Compose compiler 2.2.10, Compose BOM 2025.11.01 (runtime/UI 1.9.5), Room 2.8.4, SDK compile/target 36, min 26. Versions stables compatibles choisies pour le Java/SDK/AGP déjà présents ; ce ne sont pas nécessairement les dernières versions stables publiées. Métadonnées Google/Maven vérifiées. La branche Compose 1.9 est mise à jour après un crash de la table de composition pendant le test UI ; cette mise à jour ne vaut pas validation de sa résolution. Dépendances téléchargées à la compilation uniquement ; aucune acquisition de modèle OCR à l’exécution.

Sources : [compatibilité Kotlin/AGP](https://developer.android.com/build/kotlin-support), [Room](https://developer.android.com/jetpack/androidx/releases/room), [reconnaissance de texte embarquée](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

Installer JDK 17 et Android SDK 36, accepter les licences. Définir `ANDROID_HOME` ou un `local.properties` ignoré contenant `sdk.dir=/chemin/sdk`. Puis :

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

La dernière commande nécessite un émulateur/appareil démarré. Les tests Compose supposent une installation sans dossier préexistant. Les tests d’intégration utilisent des bases et répertoires dédiés pour sauvegarde/restauration et OCR synthétique. Une réinstallation de l’app de test ne garantit pas que le dossier de l’app cible est vide ; utiliser un émulateur de test dédié.

Le serveur dispose de peu de mémoire relative aux transformations dex : heap Gradle 1536 Mo et deux workers. Ne pas augmenter sans mesurer. Les premiers téléchargements et la première dexification sont plus longs.

Ne pas annoncer des tests instrumentés réussis s’ils n’ont pas été exécutés. Résultats effectifs dans `PROGRESS.md`, rapports générés sous `app/build/reports` (ignorés).
