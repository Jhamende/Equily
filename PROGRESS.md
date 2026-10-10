# Suivi vérifié — Equily

## Plan

1. Inspecter dépôt et environnement (fait : dépôt distant vide, JDK/SDK disponibles).
2. Implémenter domaine exact et persistance transactionnelle (fait, tests purs exécutés).
3. Implémenter interfaces locales, OCR, rapports et sauvegarde (code présent ; validation Android en cours).
4. Vérifier intégration, corriger les écarts et éprouver le scénario complet.
5. Signature dédiée, CI, vérification d’APK.
6. Commit local reproductible ; push/tag/publication seulement avec autorisation et critères satisfaits.

## Résultats établis

- 18 tests unitaires exécutés et réussis le 10 octobre 2026 : 13 registre/récurrences, 3 parsing OCR, 2 cryptographie.
- Compilations debug et release signée, 18 tests unitaires, lint debug et compilation des tests instrumentés réussis sur le dernier code. Lint garde des avertissements (versions de dépendances, recommandations et code tiers), sans erreur.
- Aucun appareil physique. Android 35 sans KVM trop lent ; émulateur Android 8/API 26 démarré avec un cœur et GPU désactivé. Installation debug et lancement de MainActivity confirmés (8 secondes environ pour le lancement).
- Trois tests d’intégration Android exécutés et réussis : sauvegarde complète vers base/répertoire vierges avec pièce jointe et rejet d’altération ; règles datées et rejet transactionnel de surpaiement ; OCR embarqué sur facture école synthétique 125 €. Ce n’est pas une restauration après désinstallation réelle ni un test de facture photographiée réelle.
- Dernière suite Android complète : **4 tests réussis**, dont test Compose onboarding → ajout d’une hypothèque 1070 €, durée totale 269 secondes sur émulateur sans accélération. Exécution directe avec AndroidJUnitRunner via ADB, pas de résultat inventé de connectedDebugAndroidTest/CI distante. Première tentative en échec ; boîte ANR System UI fermée, Compose mis à jour et notification de réussite recouvrant le bouton d’ajout retirée après navigation.
- Aucun commit poussé, aucun tag, aucune release publique.

## Écarts à résoudre avant release

- Affectations multiples implémentées et testées en calcul : une transaction conserve son total et ses affectations exactes ; vérifier la saisie sur appareil.
- Remboursements partiels et crédits reçus avant paiement couverts par tests ; flux mixtes compte commun/personnels à éprouver sur appareil. Le compte commun est volontairement qualifié.
- Récurrences : révisions datées disponibles, préciser et tester la pause/reprise, et prévenir les doublons entre dépense manuelle et début de template. Configuration depuis la dépense et historique des versions à améliorer.
- Accords : règles datées et documents associés disponibles ; édition et présentation historique des versions à compléter.
- Filtres annuels, indicateur animé des paiements fournisseurs et solde détaillé par dépense présents ; rapports annuels et présentation graphique à améliorer.
- Prévisualisation intégrée images/PDF avec pagination présente ; orientation EXIF des photos et zoom à compléter.
- Retrait de document nettoie le fichier si non référencé, restauration nettoie les pièces remplacées. Nettoyage des fichiers caméra et imports abandonnés à finaliser ; préférences du téléphone hors archive.
- UX : examen visuel, contrastes, TalkBack, petites/grandes tailles, NL/EN et clavier à valider sur appareil. Catégories prédéfinies présentes ; personnalisation des noms/listes et édition du dossier à compléter.
- Verrouillage : code appareil prévu pour API 26–29, biométrie/code pour API 30+. Préservation des formulaires et flux import/export pendant le verrouillage à compléter/tester ; aucun test physique de biométrie réalisé.
- Rapports : exports mensuels présents ; pagination, localisation des en-têtes PDF et rapports annuels à améliorer. Détection de doublons par métadonnées de facture à compléter au-delà du hash de fichier.
- Schéma Room v1 exporté ; aucune ancienne version à migrer. Prévoir une vraie migration et son test lors du prochain schéma.
- Clé dédiée créée hors dépôt ; dernier APK release signé vérifié avec apksigner (v2), empreinte dans RELEASE.md. Manifestes debug/release vérifiés : aucune permission INTERNET. Workflows CI et release écrits et YAML parsé, pas exécutés sur GitHub. Aucune publication tant que scénario complet et autorisation manquent.

## Reprise

Lire ce fichier et `EQUILY_CODEX_PROMPT.md`, vérifier l’état Git et exécuter les commandes de `DEVELOPMENT.md`. Examiner les rapports de tests et lint. Conserver la clé de signature existante ; ne jamais la recréer. Mettre à jour ce suivi avec des résultats effectifs, jamais des suppositions.
