# Suivi vérifié — Equily

## Plan

1. Inspecter dépôt et environnement (fait : dépôt distant vide, JDK/SDK disponibles).
2. Implémenter domaine exact et persistance transactionnelle (fait, tests purs exécutés).
3. Implémenter interfaces locales, OCR, rapports et sauvegarde (code présent ; tests ciblés Android réussis, acceptation complète restante).
4. Vérifier intégration, corriger les écarts et éprouver le scénario complet.
5. Signature dédiée, CI, vérification d’APK.
6. Commit local reproductible ; push/tag/publication seulement avec autorisation et critères satisfaits.

## Résultats établis

- Première étape : 18 tests unitaires exécutés et réussis le 10 octobre 2026 : 13 registre/récurrences, 3 parsing OCR, 2 cryptographie. Suite actuelle à 27 tests, voir la continuation ci-dessous.
- Vérifications initiales : compilations debug et release signée, 18 tests unitaires, lint debug et compilation des tests instrumentés réussis. Lint garde des avertissements (versions de dépendances, recommandations et code tiers), sans erreur.
- Aucun appareil physique. Android 35 sans KVM trop lent ; émulateur Android 8/API 26 démarré avec un cœur et GPU désactivé. Installation debug et lancement de MainActivity confirmés (8 secondes environ pour le lancement).
- Trois tests d’intégration Android exécutés et réussis : sauvegarde complète vers base/répertoire vierges avec pièce jointe et rejet d’altération ; règles datées et rejet transactionnel de surpaiement ; OCR embarqué sur facture école synthétique 125 €. Ce n’est pas une restauration après désinstallation réelle ni un test de facture photographiée réelle.
- Suite Android initiale complète : **4 tests réussis**, dont test Compose onboarding → ajout d’une hypothèque 1070 €, durée totale 269 secondes sur émulateur sans accélération. Exécution directe avec AndroidJUnitRunner via ADB, pas de résultat inventé de connectedDebugAndroidTest/CI distante. Première tentative en échec ; boîte ANR System UI fermée, Compose mis à jour et notification de réussite recouvrant le bouton d’ajout retirée après navigation.
- Aucun commit poussé, aucun tag, aucune release publique.
- Après le correctif du contexte Android localisé, le test Compose a été réexécuté et réussi (92 secondes) : onboarding, hypothèque 1070 €, ouverture du scan et de la sauvegarde. Le contexte conserve désormais son Activity pour les lanceurs de sélection/import/export ; cela ne valide pas encore les flux complets de fichiers ni leur interaction avec le verrouillage.

## Écarts à résoudre avant release

- Affectations multiples implémentées et testées en calcul : une transaction conserve son total et ses affectations exactes ; vérifier la saisie sur appareil.
- Remboursements partiels et crédits reçus avant paiement couverts par tests ; flux mixtes compte commun/personnels à éprouver sur appareil. Le compte commun est volontairement qualifié.
- Récurrences : pauses datées sans rattrapage, jour ancré explicite, enfant/notes conservés, prévention des doubles templates depuis une dépense et révisions rejetées si elles recouvrent une échéance existante. Tests unitaires et intégration Android réussis. Regroupement visuel des versions et revue du parcours de configuration à améliorer.
- Accords : règles datées et documents associés disponibles ; édition et présentation historique des versions à compléter.
- Filtres annuels, indicateur animé des paiements fournisseurs et solde détaillé par dépense présents ; rapports annuels et présentation graphique à améliorer.
- Prévisualisation intégrée images/PDF avec pagination présente ; orientation EXIF des photos et zoom à compléter.
- Retrait de document nettoie le fichier si non référencé, restauration nettoie les pièces remplacées. Nettoyage des fichiers caméra et imports abandonnés à finaliser ; préférences du téléphone hors archive.
- UX : examen visuel, contrastes, TalkBack, petites/grandes tailles, NL/EN et clavier à valider sur appareil. Catégories prédéfinies présentes ; personnalisation des noms/listes et édition du dossier à compléter.
- Verrouillage : code appareil prévu pour API 26–29, biométrie/code pour API 30+. Navigation, formulaires et lanceurs Activity Result conservés sous masque opaque sans contenu accessible ; test Compose de conservation et interception du retour réussi. Retour de sélecteur externe réel, rotation/processus et biométrie physique restent à éprouver.
- Rapports : exports mensuels présents ; pagination, localisation des en-têtes PDF et rapports annuels à améliorer. Détection de doublons par métadonnées de facture à compléter au-delà du hash de fichier.
- Schéma Room v1 exporté ; aucune ancienne version à migrer. Prévoir une vraie migration et son test lors du prochain schéma.
- Clé dédiée créée hors dépôt ; dernier APK release signé vérifié avec apksigner (v2), empreinte dans RELEASE.md. Manifestes debug/release vérifiés : aucune permission INTERNET. Workflows CI et release écrits et YAML parsé, pas exécutés sur GitHub. Aucune publication tant que scénario complet et autorisation manquent.

## Reprise

Lire ce fichier et `EQUILY_CODEX_PROMPT.md`, vérifier l’état Git et exécuter les commandes de `DEVELOPMENT.md`. Examiner les rapports de tests et lint. Conserver la clé de signature existante ; ne jamais la recréer. Mettre à jour ce suivi avec des résultats effectifs, jamais des suppositions.

## Continuation du 10 octobre 2026

- 27 tests unitaires réussis, réexécutés avec 64 Mo de heap de tests : 17 registre/récurrences, 7 sauvegarde/cryptographie, 3 parsing OCR. Chiffrement/déchiffrement d’un flux de 90 Mo avec buffers bornés, limites de segments, troncature, ajout, réordonnancement, mauvais secret et lecture authentifiée v1 testés.
- Nouveau format de sauvegarde v2 Tink AES-GCM-HKDF Streaming, compatible lecture v1. Export/restauration et hashes d’originaux traités en flux ; limite 1 Go explicite, métadonnées 20 Mo en mémoire. Temporaires privés nettoyés, fichiers installés retirés en cas d’échec transactionnel. Le lecteur v1 demeure monolithique, 100 Mo maximum.
- Build debug et APK de tests réussis ; lint 0 erreur, 28 avertissements. Une compilation intermédiaire a échoué pendant l’ajout d’une méthode de repository, puis reconstruction cohérente réussie.
- Première suite Android étendue : 7 tests exécutés en 975 secondes, 5 réussis, 2 assertions de test erronées. L’archive supérieure à 75 Mo a été restaurée et ses enregistrements/hashes comparés, puis le contrôle des temporaires a confondu les fichiers `.db.lck` de Room avec des répertoires de sauvegarde. Le formulaire et la phrase de sauvegarde ont été conservés, puis le test a comparé le texte masqué `EditableText` au lieu de `InputText`. Assertions corrigées ; réexécution ciblée en préparation. Aucun succès global de cette suite n’est déclaré.
- Nouveau test et script pour conserver l’archive hors app, désinstaller réellement l’app de test, réinstaller, restaurer puis réouvrir la base. **Exécution réussie** : phase export 65,848 secondes, phase restauration 55,625 secondes. Absence de la base/source/originaux/marqueur précédente confirmée avant restauration ; contenu financier, accord, récurrence, enfant, original, audit et solde 397,50 € vérifiés, puis fermeture/réouverture de la base et seconde vérification. Cela utilise des fixtures synthétiques et bases dédiées, pas le parcours complet de l’interface. Aucun test d’authentification réelle ajouté.
- Réexécution ciblée : grande archive et nettoyage réussis ; test UI interrompu par un crash Compose à la destruction de l’Activity. Le bouton retour sous verrouillage exposait un défaut de priorité des callbacks : le handler de navigation pouvait être enregistré après celui du masque pendant l’initialisation/onboarding. Handler désormais lié au masque actif ; validation en cours, sans attribution certaine du crash à une version de bibliothèque.
- Après correction de priorité du retour : **2 tests ciblés réussis en 114 secondes**, conservation du formulaire/phrase sous verrouillage simulé avec retour intercepté et nettoyage initial ne supprimant pas un export actif d’une autre instance. Pas de crash lors de cette exécution. Le test de grande archive a aussi réussi après correction de son assertion. Les résultats cumulés ne doivent pas être présentés comme une réexécution globale réussie de toute la suite.
- Dernier build sur code corrigé : debug, APK de tests, release signée, 27 tests unitaires sous heap 64 Mo et lint réussis ; 0 erreur lint, 28 avertissements. Aucun push/tag/publication. Scénario intégral d’acceptation et vérifications physiques/linguistiques/accessibilité toujours incomplets.
