# Equily

Application Android native pour suivre localement les dépenses partagées après une séparation. EUR, français par défaut, néerlandais et anglais. Aucun compte, revenu obligatoire, serveur, publicité ou synchronisation. Package `be.equily.app`, version de développement `0.1.0` (1), Android 8.0/API 26 minimum, cible API 36.

**État : développement, pas encore une release acceptée.** Les exigences de référence sont dans `EQUILY_CODEX_PROMPT.md`. Voir `PROGRESS.md` pour les validations et les écarts. Ne pas présenter les déclarations comme des dettes reconnues juridiquement ou validées par l’autre personne.

## Utilisation

Créer un dossier sans compte, indiquer éventuellement les alias/enfants et un solde initial. Ajouter une dépense, préciser son partage, puis enregistrer séparément les paiements et remboursements. Une facture n’est jamais une preuve de paiement. Les montants contestés sont isolés du solde financé entre personnes. Les dépenses impayées restent visibles comme échéances fournisseurs.

Les imports acceptent JPEG, PNG et PDF (25 Mo maximum). La lecture utilise le modèle latin ML Kit embarqué et l’extraction locale PDFBox ; les PDF scannés sont rendus et reconnus localement. Les propositions doivent être revues manuellement. Dix pages au plus sont analysées, cent pages au plus acceptées. Une prévisualisation intégrée affiche images et pages PDF ; l’ouverture dans un lecteur externe est facultative.

Les rapports mensuels PDF/CSV sont produits sur le téléphone. Les sauvegardes `.equily` contiennent le dossier, les transactions, règles, récurrences, documents et historique. Elles sont chiffrées avec une phrase secrète d’au moins douze caractères. Conserver fichier et phrase séparément ; aucune récupération de phrase perdue. La restauration remplace le dossier actuel après confirmation. Langue, thème et verrouillage sont des préférences propres au téléphone et ne sont pas restaurés.

Les nouveaux exports v2 traitent les pièces et le chiffrement en flux, avec une limite explicite de 1 Go et du stockage temporaire privé nécessaire (jusqu’à deux tailles d’archive). Les anciennes sauvegardes v1 restent lisibles, avec leur limite de 100 Mo et un besoin mémoire plus élevé. Les pauses de récurrence excluent les échéances suspendues sans rattrapage ; une révision ne change jamais les dépenses déjà créées.

## Compilation et installation

Voir `DEVELOPMENT.md`. Construire avec `./gradlew :app:assembleDebug`. Installer localement avec `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Cet APK debug n’est pas l’APK de distribution. Une release signée utilise une clé dédiée et reste bloquée tant que les critères d’acceptation ne sont pas tous vérifiés.

Documentation : `ARCHITECTURE.md`, `SECURITY.md`, `PRIVACY.md`, `RELEASE.md`, `CHANGELOG.md`.
