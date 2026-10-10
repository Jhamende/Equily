# Suivi vérifié — Equily

## Publication autorisée le 11 octobre 2026

Le propriétaire a explicitement autorisé la publication. Push des sources et CI distante en préparation ; pas encore de tag ni release. Confirmation demandée pour l'acceptation des limites physiques/TalkBack et pour la sauvegarde indépendante éprouvée de la clé. La porte d'acceptation reste fermée tant que ces points sont ouverts. Le bilan du 10 octobre ci-dessous décrit la livraison locale vérifiée.

## État au 10 octobre 2026

Application native locale implémentée ; aucun push, tag ou release publique. Publication subordonnée à l'autorisation explicite du propriétaire et aux portes de `ACCEPTANCE.md`. Sauvegarde indépendante de la clé de signature non confirmée.

## Vérifications réussies

- Compilation debug et APK de tests ; **38 tests unitaires, zéro échec**, réexécutés avec heap de tests de 64 Mo. Registre 17, cryptographie 7, parsing 6, CSV 2, règlement 2, crédits facultatifs 2, sécurité des récurrences 2. Lint debug : zéro erreur, 28 avertissements. Compilation/tests puis lint séparés pour éviter une course interne AGP lint/kapt.
- **Suite Android complète : 18 tests, zéro échec, zéro test ignoré**, 1947,116 secondes. AndroidJUnitRunner direct sur AVD dédié Android 8/API 26 sans KVM. Preuve : `artifacts/verification/android-api26-20261010T192729Z.txt`. Ce n'est pas une exécution de CI GitHub ou de connectedDebugAndroidTest.
- Parcours complet hors ligne : dossier/enfant, partage 50/50, hypothèque personnelle payée de 1070 € et template mensuel, annulation caméra avec nettoyage, facture école PNG 125 € reconnue localement, correction 125,01 → 125, original/enfant/payeur conservés, paiement reçu de 200 €, solde 397,50 €, rapports PDF/CSV et archive chiffrée ; restauration vers repository vide avec comparaison des enregistrements, hashes et solde. Les vrais contrats Activity Result sont utilisés, avec réponses synthétiques injectées pour certains sélecteurs d'import/export.
- Test distinct de **credential Android et SAF réellement externes** : annulation puis authentification PIN Android ; FLAG_SECURE ; navigation tactile DocumentsUI et import/OCR pendant le masque de verrouillage ; seconde authentification puis revue. Aucun résultat de sélecteur injecté ni déverrouillage applicatif simulé dans ce test. PIN synthétique du seul AVD supprimé après vérification.
- **Désinstallation réelle puis réinstallation et restauration réussies**. Export 64,031 secondes et restauration 65,632 secondes ; seule l'archive synthétique chiffrée de 1995 octets est retenue hors app. Absence des anciennes données vérifiée avant import, puis dossier, accords, récurrences, enfant, original, neuf événements d'audit, solde 397,50 € et réouverture persistante comparés. Journal : `artifacts/verification/clean-restore-final.txt`. Complète le parcours précédent ; ce n'est pas une simple remise à zéro de la base.
- Intégration : archive de plus de 75 Mo et hashes des originaux, compatibilité archives v1/v2, altération et ZIP invalide rejetés atomiquement, nettoyage sans supprimer un export actif ; compatibilité JSON historique, corrections d'accord sans recalcul rétroactif, règles hors capacité rejetées et récurrences datées.
- Documents/rapports : PNG malformé et PDF de 101 pages rejetés avant écriture ; PDF texte/image/mixte, JPEG orienté avec original inchangé, parsing école 125 €, rapport annuel NL multipage et affectations sur plusieurs périodes sans double comptage. Recréation conserve les champs ordinaires mais pas le secret. Erreur d'initialisation préserve le dossier et permet un réessai après réparation.
- **Trois variantes d'affichage réussies**, six langues/thèmes chacune : téléphone 320×640 (82,295 s), tablette 1280×800 (81,982 s), téléphone avec police 1,3 (82,510 s). Captures privées de la vue dans `artifacts/visual/{phone,tablet,large-font}` ; FLAG_SECURE reste activé. Examen visuel FR/NL/EN, dont tablette et grandes polices FR/NL ; bouton d'ajout/navigation accessibles, contenu défilant. Huit couples texte/fond principaux : contraste calculé 7,05 à 15,04, pas une certification de chaque état. Géométrie/police de l'AVD restaurées.

## Corrections et échecs intermédiaires

Deux suites précédentes ont donné 17/18, pas 18/18 : course du sélecteur natif corrigée en attendant l'inactivité et en excluant le fil d'Ariane, puis assertion onboarding trouvant encore le champ pendant l'enregistrement. L'attente exige maintenant persistance, fin de l'opération et disparition du formulaire. Tests ciblés puis troisième suite globale réussis.

Le crash Compose initial provenait de publications UI depuis IO ; elles sont désormais sur Main.immediate. Contexte localisé conservant l'Activity et ressources Compose localisées ; interception Retour sous masque ; attente de disparition des messages avant actions tactiles. Aucun contournement des credentials Android.

Une relance de restauration a été interrompue après export par modification du script pendant son exécution ; elle n'est pas comptée comme restauration réussie. Relance finale ci-dessus terminée sans modification en cours. Les scripts refusent explicitement échecs et tests ignorés.

## Limites réellement non vérifiées

- Aucun appareil physique ni KVM : photographie réelle, qualité OCR de photos réelles, biométrie matérielle/API 30+, TalkBack audible et destruction complète du processus avec sélecteur externe non éprouvés. Annulation caméra, EXIF, OCR synthétique, sémantique accessible, clavier/formulaires et recréation d'Activity ont des vérifications distinctes ; ne pas les confondre.
- API 26 exécutée ; compilation/cible API 36. Workflow API 35 configuré mais pas exécuté à distance ; pas de matrice matérielle exhaustive.
- Room commence au schéma v1 : aucune migration structurelle fictive ; JSON historique et archives v1/v2 testés. Une évolution exige sa vraie migration et son test, sans effacement destructeur.
- Stockage privé actif non chiffré globalement. PDF/CSV en clair, archives chiffrées. Fournisseurs de fichiers/lecteurs externes choisis par l'utilisateur hors contrôle d'Equily. Pas de backend, synchro, approbation bilatérale vérifiée ou conclusion juridique.

## APK signé

Release compilée avec succès en 2 min 17 s et signée avec la clé dédiée existante : vérification apksigner v2 réussie, empreinte conforme à `RELEASE.md`, zipalign 16 Ko réussi et segments LOAD de toutes les bibliothèques arm64/x86_64 à 16 Ko. Package/version/minimum/cible conformes, non debuggable, aucune permission INTERNET. Artefact : `artifacts/Equily-v0.1.0-release.apk`, checksum dans `artifacts/SHA256SUMS`. Journaux dans `artifacts/verification/release-*.txt`. Le rapport non suivi `artifacts/DELIVERY.md` précise le commit et le checksum finaux, sans référence circulaire dans les fichiers intégrés au commit.

Installation propre de l'APK release signée réussie sur le seul AVD dédié. Lancement en 5125 ms, onboarding français sans compte/revenu ni alias obligatoire, part 50/50, création par le vrai bouton Android, tableau de bord à 0,00 €, puis arrêt forcé/reprise en 4587 ms avec dossier conservé. Hiérarchies natives dans `artifacts/verification/release-smoke-*.xml`. Ce smoke release complète les tests instrumentés debug de la même source applicative ; il ne prétend pas répéter les 18 tests sous la clé release.

## Livraison et interventions requises

Travail local indépendant terminé : application, tests, scripts reproductibles, documentation, APK signé et contrôles locaux. Rapport final dans `artifacts/DELIVERY.md`. Après le commit documentaire, reconstruire l'APK pour y intégrer exactement ce commit, revérifier signature/checksum et l'installer avec la même clé ; les sources applicatives et tests ne changent pas.

Publication bloquée sans autorisation explicite de push/configuration des secrets/tag/pre-release et revue des limites matérielles/accessibilité. Ne jamais régénérer la clé existante ; sa sauvegarde chiffrée hors serveur doit être effectuée et éprouvée par le propriétaire suivant `RELEASE.md`. Aucun résultat distant ou sauvegarde indépendante inventé.
