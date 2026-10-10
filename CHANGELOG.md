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
- 27 tests unitaires réussis sous heap 64 Mo, flux chiffré de 90 Mo vérifié ; validations Android ciblées de grande archive, règles, OCR, verrouillage et nettoyage.
- Restauration vérifiée après désinstallation réelle et réinstallation sur émulateur API 26, archive retenue hors app puis dossier synthétique et réouverture persistante vérifiés. Résultats et échecs intermédiaires dans `PROGRESS.md`.

Cette liste décrit le code présent, pas une validation du scénario complet ni une publication. Aucun tag public ou APK de release accepté à ce stade.
