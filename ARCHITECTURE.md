# Architecture

Un module Android, Kotlin/Compose/Material 3/Navigation Compose, ViewModel, Flow/Coroutines, Room et DataStore. Injection explicite par le ViewModel : pas de Hilt nécessaire pour ce graphe local. Présentation dans `Ui.kt`/`EquilyModel.kt`, types et calculs purs dans `domain`, persistance et services Android dans `data`. Aucune permission INTERNET ni stockage large.

## Registre financier

Les montants utilisent des `Long` en centimes, plafonnés à un milliard d’euros par entrée. Les saisies sont converties avec BigDecimal exact ; plus de deux décimales sont rejetées. Les parts utilisent des points de base (10000 = 100 %). La première part est arrondie HALF_UP au centime et la deuxième reçoit le reliquat : aucune perte de centime. Les pourcentages de chaque dépense sont des instantanés. Modifier une règle ne recalcule pas les dépenses historiques.

Coût net d’une dépense = montant − remboursements/crédits explicitement affectés. Le remboursement ne peut excéder le montant. Le total payé au fournisseur ne peut excéder le montant brut. Les sommes impayées au fournisseur sont `max(montant brut − paiements fournisseur, 0)` : recevoir un crédit partagé ou un remboursement ne prouve pas que le reste de la facture a été payé. Les avoirs fournisseur non décaissés nécessitent encore une modélisation distincte.

Pour chaque dépense non contestée, le solde financé est : paiement personnel net de Vous − votre part arrondie du total des paiements personnels nets. Un paiement personnel net déduit les remboursements reçus. Les virements de Vous vers l’autre augmentent ce solde ; ceux reçus le diminuent. Solde positif : l’autre doit à Vous, sur la base des saisies. Les dépenses encore totalement impayées ne deviennent pas des remboursements exigibles entre personnes. Les montants contestés restent déclarés séparément et sont exclus de ce calcul. Le solde initial facultatif est ajouté au cumul, sans simuler un paiement fournisseur.

Les contributions au compte commun augmentent sa trésorerie déclarée sans régler une dépense. Les sorties fournisseur diminuent cette trésorerie. Les dépenses payées par compte commun/source inconnue produisent un avertissement : les financements communs ne sont pas imputés artificiellement à une personne. Ce solde est donc partiel même si certaines contributions sont saisies. Les remboursements/crédits affectés à un compte commun y augmentent la trésorerie.

Les brouillons n’affectent pas les calculs. Archiver conserve les impacts financiers et le statut déclaratif. Accepté/contesté signifie toujours « saisi localement par l’utilisateur », aucune approbation bilatérale vérifiée. Le statut payé/partiellement payé est dérivé des paiements fournisseurs, distinct de l’état d’acceptation.

## Persistance

Room v1 conserve des enregistrements typés avec IDs stables et corps JSON versionné. Les codecs reconstruisent et valident les types du domaine. Les mutations passent par une transaction : validation de l’ensemble et audit avant/après, puis écriture. Les pièces jointes sont des fichiers privés immuables adressés par SHA-256, référencés par des IDs UUID. Les identifiants ne contiennent pas de noms familiaux ou fournisseurs. Les hash servent à détecter les doublons, sans conclure automatiquement qu’une dépense est identique.

Une transaction peut porter une affectation unique ou un ensemble d’affectations dépense/montant dont la somme égale exactement son total. Les affectations sont développées uniquement pour le calcul et les rapports, sans compter à nouveau le paiement principal. Les contributions au compte commun ne peuvent être affectées à des dépenses.

Le schéma Room exporté est versionné dans `app/schemas`. Il n’existe pas encore de schéma antérieur : aucune migration de production fictive et aucun `fallbackToDestructiveMigration`. Toute future modification structurelle doit augmenter la version, fournir une migration et ses tests. Les changements du corps JSON doivent conserver la lecture des archives et données existantes ou migrer explicitement leur version.

## Récurrences

IDs déterministes `règle:date`, mensuelles/trimestrielles/annuelles, générées transactionnellement à l’ouverture ou après création. Le jour est ancré sur la date initiale et réduit au dernier jour si nécessaire, sans dérive février/mars. Les échéances ne sont jamais marquées payées automatiquement. Une révision clôture l’ancienne règle la veille de la nouvelle date d’effet ; les dépenses déjà générées restent intactes. Une pause arrête la génération ; la reprise peut rattraper les échéances de la pause, limite connue à clarifier avant release.

## Archives

Format `EQUILY` version 1, sel 128 bits et nonce GCM 96 bits aléatoires. PBKDF2-HMAC-SHA256, 600000 itérations, clé AES 256 bits, AES-GCM tag 128 bits. L’en-tête est authentifié. ZIP interne : `records.json` et `attachments/<sha256>`. Aucun mot de passe enregistré. Le format et les paramètres sont fixes pour cette version.

Avant restauration, déchiffrement/authentification, validation des chemins, limites d’expansion, unicité, schéma, liens, types, montants, hashes et tailles. Les nouveaux fichiers adressés par contenu sont installés avant une transaction Room remplaçant tous les enregistrements. Un échec avant la transaction laisse le dossier précédent lisible, éventuellement avec des fichiers orphelins ; un fichier existant n’est jamais écrasé. Une restauration validée remplace les données, sans fusion silencieuse, puis nettoie les anciennes pièces non référencées. Limites conservatrices : 75 Mo de documents pour export, 100 Mo chiffrés/95 Mo décompressés pour import. Cette implémentation en mémoire doit encore être éprouvée sur appareil de faible mémoire ; la limite peut empêcher la sauvegarde d’un gros dossier, écart bloquant à résoudre avant release.

## Extensions futures

Interfaces de repository et IDs stables permettent d’ajouter une synchronisation, mais aucune n’est implémentée. Les statuts locaux ne doivent jamais être réutilisés comme preuves de consentement distant. Voir `PROGRESS.md` pour les éléments nécessaires avant publication.
