# Développement

## Environnement inspecté le 10 octobre 2026

Ubuntu/Linux x86_64, Java OpenJDK 17, SDK `/opt/android-sdk`, plateformes 33–36 et build-tools 34–36 installés, environ 313 Go disponibles au début. Dépôt GitHub initialement vide. GitHub CLI authentifié ; aucun secret n’est copié dans le dépôt. Aucun appareil physique et aucune accélération KVM disponible. Émulateur et image Android 35 default x86_64 installés pour tenter les vérifications sans accélération.

## Versions reproductibles

Gradle Wrapper 8.13, AGP 8.13.1, Kotlin/Compose compiler 2.2.10, Compose BOM 2026.03.01 (runtime/UI 1.10.6), Room 2.8.4, SDK compile/target 36, min 26. Versions stables compatibles choisies pour le Java/SDK/AGP déjà présents ; ce ne sont pas nécessairement les dernières versions stables publiées. Métadonnées Google/Maven vérifiées. Le BOM 2026.09.00 a été évalué puis rejeté : ses AAR exigent SDK 37 et AGP 9.1+, incompatibles avec la chaîne choisie. Le BOM 2026.03.01 inclut les correctifs stables de runtime 1.10.6 ; cela ne vaut pas preuve de résolution du crash UI avant réexécution. Orientation JPEG/PNG avec ExifInterface 1.4.2. Dépendances téléchargées à la compilation uniquement ; aucune acquisition de modèle OCR à l’exécution.

Sauvegardes segmentées : `tink-android:1.21.0`, version publiée vérifiée sur Maven et code/documentation officiels consultés. Paramètres de flux fixés et décrits dans `ARCHITECTURE.md` ; pas de keystore Android ni service distant utilisés pour ces sauvegardes par phrase secrète. Les tests de gros flux écrivent des fichiers temporaires, plutôt que des tableaux proportionnels à l’archive.

Sources : [compatibilité Kotlin/AGP](https://developer.android.com/build/kotlin-support), [Room](https://developer.android.com/jetpack/androidx/releases/room), [Tesseract4Android 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/README.md). La variante OCR mono-thread limite les ressources et conserve une instance par extraction ; les trois modèles `tessdata_fast` sont épinglés au commit `87416418657359cb625c412a48b6e1d6d41c29bd`, embarqués et vérifiés par SHA-256 avant utilisation. JitPack est restreint au seul groupe de cette dépendance. Aucune dépendance ML Kit ni Google Play Services dans l’application actuelle. Voir `THIRD_PARTY_NOTICES.md`.

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

Pour vérifier ensemble les APK instrumentés et lint après une modification des tests, séparer les invocations :

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:lintDebug
```

Une invocation incrémentale lançant lint pendant la régénération des stubs kapt AndroidTest a produit un `FileNotFoundException` interne à lint sur ce serveur. La séquence ci-dessus a ensuite réussi, sans désactiver aucun contrôle ni modifier les sources pour contourner l’analyse.

La dernière commande nécessite un émulateur/appareil démarré. Les tests Compose supposent une installation sans dossier préexistant. Les tests d’intégration utilisent des bases et répertoires dédiés pour sauvegarde/restauration et OCR synthétique. Une réinstallation de l’app de test ne garantit pas que le dossier de l’app cible est vide ; utiliser un émulateur de test dédié.

## Restauration après désinstallation réelle

### Suite complète et code appareil réel

Après compilation des APK debug/tests, `bash tools/verify-device-suite.sh emulator-5554 --wipe-test-app` exécute toute la suite Android et conserve son journal dans `artifacts/verification`. Ce script refuse les AVD non réservés et les API autres que 26, désinstalle **uniquement l’application de test**, configure temporairement un PIN synthétique public, vérifie les résultats du runner (y compris les tests ignorés) et retire son PIN en sortie. Ne jamais l’utiliser sur un téléphone ou un AVD personnel. Une credential préexistante empêche la configuration sans son ancien secret ; ne pas contourner cette protection.

Le test de credential confirme une annulation puis une authentification par Android, et le retour d’un vrai sélecteur SAF après import d’un PNG synthétique sous verrouillage. Le test de parcours général injecte les réponses de sélecteurs pour les fichiers synthétiques, tout en utilisant les contrats, écrans et services applicatifs réels. La biométrie physique et le chemin BiometricPrompt API 30+ restent distincts et doivent être déclarés non testés en l’absence d’appareil approprié.

Le parcours natif de credential/SAF utilise les libellés de l’image AOSP Android 8 anglaise x86_64 inspectée sur ce serveur, tandis que l’interface Equily est française. Les interactions avec le sélecteur attendent sa stabilisation et recherchent les fichiers dans sa liste de dossier, pas dans le fil d’Ariane.

### Tailles, polices et thèmes localisés

Avec les APK debug/tests installés et aucune autre instrumentation active, `bash tools/verify-layouts.sh emulator-5554 --wipe-test-app` réutilise le test visuel sur téléphone 320 × 640, tablette 1280 × 800 et téléphone avec police à 1,3, à densité 160. Chaque variante réussie conserve six captures FR/NL/EN × clair/sombre dans `artifacts/visual` et son journal dans `artifacts/verification`. Le script refuse les AVD non réservés/API autres que 26 et restaure les réglages de dimensions/densité/police en sortie. Le test remplace le dossier par une fixture synthétique puis le vide ; ne pas l’exécuter sur des données personnelles.

Les captures proviennent du rendu de la propre View de l’application de test ; FLAG_SECURE n’est jamais désactivé. Le test vérifie les libellés accessibles et l’apparence clair/sombre des barres système. Cela ne remplace pas une revue TalkBack audible ni une vérification de tous les gestes sur téléphone physique.

Le script suivant est destructif **pour les données Equily du seul émulateur de test choisi**, jamais pour un téléphone physique. Il refuse les AVD autres que `equily-api26` et `equily-clean-restore`. Ne pas y conserver de dossier personnel. Construire préalablement les APK debug et de tests, arrêter toute autre instrumentation, puis :

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
bash tools/verify-clean-restore.sh emulator-5554 --wipe-test-app
```

Le test prépare un dossier synthétique (hypothèque 1070 €, école 125 €, enfant, partage 50/50, paiements fournisseurs, remboursement partiel 200 €, accord, récurrence, original et audit). Il exporte une archive chiffrée, la garde temporairement hors de l’app sur le serveur, désinstalle réellement l’app, réinstalle le même APK debug, remet **uniquement l’archive** et restaure. Le test confirme l’absence de la base/source/originals/marqueur avant restauration, vérifie le contenu, le solde 397,50 € et la réouverture persistante. Les fichiers hors app sont effacés en fin de script. Les phases ciblées passent par AndroidJUnitRunner ; ce n’est pas un parcours complet d’interface ni une validation du sélecteur SAF.

Prévoir environ 500 Mo libres pour les tests d’archives volumineuses, plus l’installation Android. Sur ce serveur sans KVM, les tests cryptographiques et installations sont sensiblement plus lents que sur appareil accéléré. Le test `CleanInstallationBackupTest` réalise aussi une restauration vers une base vide lors d’une suite normale ; seule l’exécution du script prouve la désinstallation intermédiaire.

Le serveur dispose de peu de mémoire relative aux transformations dex : heap Gradle 1536 Mo et deux workers. Ne pas augmenter sans mesurer. Les premiers téléchargements et la première dexification sont plus longs.

Ne pas annoncer des tests instrumentés réussis s’ils n’ont pas été exécutés. Résultats effectifs dans `PROGRESS.md`, rapports générés sous `app/build/reports` (ignorés).
