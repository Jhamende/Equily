# Développement

## Environnement inspecté le 10 octobre 2026

Ubuntu/Linux x86_64, Java OpenJDK 17, SDK `/opt/android-sdk`, plateformes 33–36 et build-tools 34–36 installés, environ 313 Go disponibles au début. Dépôt GitHub initialement vide. GitHub CLI authentifié ; aucun secret n’est copié dans le dépôt. Aucun appareil physique et aucune accélération KVM disponible. Émulateur et image Android 35 default x86_64 installés pour tenter les vérifications sans accélération.

## Versions reproductibles

Gradle Wrapper 8.13, AGP 8.13.1, Kotlin/Compose compiler 2.2.10, Compose BOM 2025.11.01 (runtime/UI 1.9.5), Room 2.8.4, SDK compile/target 36, min 26. Versions stables compatibles choisies pour le Java/SDK/AGP déjà présents ; ce ne sont pas nécessairement les dernières versions stables publiées. Métadonnées Google/Maven vérifiées. La branche Compose 1.9 est mise à jour après un crash de la table de composition pendant le test UI ; cette mise à jour ne vaut pas validation de sa résolution. Dépendances téléchargées à la compilation uniquement ; aucune acquisition de modèle OCR à l’exécution.

Sauvegardes segmentées : `tink-android:1.21.0`, version publiée vérifiée sur Maven et code/documentation officiels consultés. Paramètres de flux fixés et décrits dans `ARCHITECTURE.md` ; pas de keystore Android ni service distant utilisés pour ces sauvegardes par phrase secrète. Les tests de gros flux écrivent des fichiers temporaires, plutôt que des tableaux proportionnels à l’archive.

Sources : [compatibilité Kotlin/AGP](https://developer.android.com/build/kotlin-support), [Room](https://developer.android.com/jetpack/androidx/releases/room), [reconnaissance de texte embarquée](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

Installer JDK 17 et Android SDK 36, accepter les licences. Définir `ANDROID_HOME` ou un `local.properties` ignoré contenant `sdk.dir=/chemin/sdk`. Puis :

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Pour vérifier les tests purs avec seulement 64 Mo de heap **du processus de tests** (pas du daemon Gradle) :

```sh
./gradlew -I tools/test-low-memory.gradle.kts :app:cleanTestDebugUnitTest :app:testDebugUnitTest
```

Le cas de 90 Mo ne doit pas charger toute l’archive en mémoire. Le script d’initialisation ne modifie pas la configuration normale des autres builds.

La dernière commande nécessite un émulateur/appareil démarré. Les tests Compose supposent une installation sans dossier préexistant. Les tests d’intégration utilisent des bases et répertoires dédiés pour sauvegarde/restauration et OCR synthétique. Une réinstallation de l’app de test ne garantit pas que le dossier de l’app cible est vide ; utiliser un émulateur de test dédié.

## Restauration après désinstallation réelle

Le script suivant est destructif **pour les données Equily du seul émulateur de test choisi**, jamais pour un téléphone physique. Il refuse les AVD autres que `equily-api26` et `equily-clean-restore`. Ne pas y conserver de dossier personnel. Construire préalablement les APK debug et de tests, arrêter toute autre instrumentation, puis :

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
bash tools/verify-clean-restore.sh emulator-5554 --wipe-test-app
```

Le test prépare un dossier synthétique (hypothèque 1070 €, école 125 €, enfant, partage 50/50, paiements fournisseurs, remboursement partiel 200 €, accord, récurrence, original et audit). Il exporte une archive chiffrée, la garde temporairement hors de l’app sur le serveur, désinstalle réellement l’app, réinstalle le même APK debug, remet **uniquement l’archive** et restaure. Le test confirme l’absence de la base/source/originals/marqueur avant restauration, vérifie le contenu, le solde 397,50 € et la réouverture persistante. Les fichiers hors app sont effacés en fin de script. Les phases ciblées passent par AndroidJUnitRunner ; ce n’est pas un parcours complet d’interface ni une validation du sélecteur SAF.

Prévoir environ 500 Mo libres pour les tests d’archives volumineuses, plus l’installation Android. Sur ce serveur sans KVM, les tests cryptographiques et installations sont sensiblement plus lents que sur appareil accéléré. Le test `CleanInstallationBackupTest` réalise aussi une restauration vers une base vide lors d’une suite normale ; seule l’exécution du script prouve la désinstallation intermédiaire.

Le serveur dispose de peu de mémoire relative aux transformations dex : heap Gradle 1536 Mo et deux workers. Ne pas augmenter sans mesurer. Les premiers téléchargements et la première dexification sont plus longs.

Ne pas annoncer des tests instrumentés réussis s’ils n’ont pas été exécutés. Résultats effectifs dans `PROGRESS.md`, rapports générés sous `app/build/reports` (ignorés).
