# Changelog

## 0.1.0 — en développement, non publié

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

Le scénario complet hors ligne, les 18 tests Android ensemble et la restauration après désinstallation réelle sont réussis sur émulateur API 26. Trois variantes d'affichage FR/NL/EN et clair/sombre sont vérifiées. Ce n'est pas une publication ni une validation sur appareil physique ; aucun tag public ou release acceptée à ce stade. Preuves et limites dans `PROGRESS.md`.
