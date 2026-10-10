# Changelog

## 0.1.0 — candidate à la pré-release autorisée

- Projet Android natif Compose avec dossier local, alias/enfants facultatifs, dates, solde initial et parts exactes.
- Registre dépenses/paiements/virements/contributions/remboursements/crédits, audit local et statuts non vérifiés.
- Récurrences, accords datés, recherche, pièces jointes, OCR embarqué et extraction PDF locale.
- Exports mensuels PDF/CSV et archive chiffrée avec validation avant restauration transactionnelle.
- Français, néerlandais et anglais, thèmes clair/sombre et verrouillage appareil facultatif.
- Sauvegardes v2 segmentées avec Tink, lecture v1 compatible, hashes/imports d’archives en flux, nettoyage des temporaires privés.
- Pauses de récurrence datées sans rattrapage, jour ancré explicite, protection contre révisions recouvrant des échéances et doubles templates depuis une dépense.
- Conservation en mémoire de la navigation et des formulaires sous masque de verrouillage, menus/dialogues masqués et lanceurs Activity Result conservés.
- Dossier éditable, catégories renommables/ajoutables/archivables, corrections et historique des accords et paiements.
- Filtres enfant/règlement, récapitulatif annuel, rapports mensuels/annuels localisés avec pagination mesurée et CSV protégé contre les formules.
- Métadonnées de facture et doublons possibles, catégorie OCR proposée, orientation EXIF, zoom, nettoyage des scans abandonnés et photos caméra.
- OCR autonome Tesseract avec modèles français/néerlandais/anglais embarqués et épinglés, sans ML Kit ni Google Play Services ; licences consultables hors ligne.
- Crédits partagés facultatifs sans affectation, sans modification implicite du solde entre personnes.
- Pause/reprise/fin de récurrence avec date d’effet choisie ; formulaires ordinaires sauvegardés lors de recréation, reprise des opérations de sauvegarde après ressaisie du secret.
- 38 tests unitaires réussis sous heap 64 Mo, flux chiffré de 90 Mo vérifié ; validations Android ciblées de grande archive, règles, OCR, verrouillage et nettoyage.
- Validation sans mutation des fichiers malformés, des données de démarrage et des règles hors capacité ; réessai après erreur sans effacement du dossier.
- Restauration vérifiée après désinstallation réelle et réinstallation sur émulateur API 26, archive retenue hors app puis dossier synthétique et réouverture persistante vérifiés. Résultats et échecs intermédiaires dans `PROGRESS.md`.

Le scénario complet hors ligne, les 18 tests Android ensemble et la restauration après désinstallation réelle sont réussis sur émulateur API 26. CI GitHub : compilation, tests unitaires et lint réussis ; API 35 : 17 tests réussis, zéro échec, un test credential ignoré faute de harness PIN (réellement exécuté sur API 26). Trois variantes d'affichage FR/NL/EN et clair/sombre sont vérifiées. Pré-release autorisée le 11 octobre 2026 ; publication réelle seulement après confirmation GitHub.

## Installation et limites connues

- APK `Equily-v0.1.0-release.apk`, Android 8.0/API 26 minimum, français/néerlandais/anglais ; vérifier `SHA256SUMS` avant installation.
- Tout fonctionne localement, sans compte ni permission INTERNET. Les statuts sont saisis par l'utilisateur, pas une approbation bilatérale ni une determination juridique.
- Aucun appareil physique testé : photographie réelle, biométrie matérielle et TalkBack audible non vérifiés ; tests OCR synthétiques et credential/SAF Android sur émulateur ne les remplacent pas.
- Stockage privé actif non globalement chiffré. Rapports PDF/CSV en clair ; archives de sauvegarde chiffrées. Sauvegarder avant désinstallation/changement de téléphone ; phrase perdue non récupérable.
- Imports 25 Mo, PDF au plus 100 pages/10 analysées ; archives v2 au plus 1 Go avec espace temporaire requis, lecture v1 jusqu'à 100 Mo. Qualité OCR variable : revue obligatoire.
- Clé de signature dédiée conservée hors dépôt, alias `equily-release`, certificat SHA-256 `8D:2B:64:7D:44:1C:E6:37:E5:BF:A3:E4:79:23:DD:B1:89:44:AA:EC:41:F8:5A:C5:17:60:90:7E:18:41:14:13`, expiration 2 octobre 2056. Propriétaire : sauvegarder keystore et mot de passe dans un coffre chiffré indépendant hors serveur et vérifier une copie selon `RELEASE.md`. Cette sauvegarde n'est pas confirmée ; les secrets Actions ne la remplacent pas.
