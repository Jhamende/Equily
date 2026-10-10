# Publication

**Aucune release publiée à ce stade. Ne pas taguer `v0.1.0` avant tous les critères du brief et l’autorisation de publication.**

Artefact attendu : `Equily-v0.1.0-release.apk`. Package `be.equily.app`, version `0.1.0`, code 1. Tag `v0.1.0`, titre `Equily v0.1.0 — Initial Android Release`, statut pre-release. Android API 26 minimum ; FR/NL/EN. APK distribué sur https://github.com/Jhamende/Equily/releases uniquement après vérification.

## Signature

Clé dédiée hors dépôt : `/root/.local/share/equily/signing/equily-release.jks`, alias `equily-release`. Les mots de passe doivent rester hors dépôt et logs. Générer une seule fois, sauvegarder la clé et ses secrets dans une archive chiffrée indépendante, idéalement avec une seconde copie hors serveur. Tester cette sauvegarde. Ne jamais supprimer/regénérer cette clé pour contourner une erreur de build. L’alias et l’empreinte publique ne sont pas des secrets ; les mots de passe et clés le sont.

Le secret généré est dans le fichier privé `password` du même répertoire (permissions 600, répertoire 700). Le propriétaire doit sauvegarder les deux fichiers dans un coffre chiffré hors serveur, avec une phrase de coffre distincte et conservée indépendamment. Pour éprouver la sauvegarde, restaurer ces deux fichiers dans un répertoire privé temporaire, utiliser `keytool -list -v -keystore copie/equily-release.jks -storepass:file copie/password -alias equily-release` et comparer l’empreinte publique ci-dessous. Ne pas afficher le contenu du fichier de secret, l’envoyer dans une conversation, le committer ou changer la clé pour tester.

Variables de compilation : `EQUILY_KEYSTORE`, `EQUILY_STORE_PASSWORD`, `EQUILY_KEY_ALIAS`, `EQUILY_KEY_PASSWORD`. Sans ces variables, la tâche Gradle release ne constitue pas une livraison signée ; le pipeline doit rejeter un APK sans signature.

Certificat public généré le 10 octobre 2026 : alias `equily-release`, RSA 3072 bits, expiration 2 octobre 2056. Empreinte SHA-256 : `8D:2B:64:7D:44:1C:E6:37:E5:BF:A3:E4:79:23:DD:B1:89:44:AA:EC:41:F8:5A:C5:17:60:90:7E:18:41:14:13`. Le pipeline contrôle cette empreinte avant publication. Ces informations sont publiques ; aucun mot de passe n’est documenté.

Secrets Actions à configurer uniquement avec autorisation : `EQUILY_KEYSTORE_BASE64` (contenu du keystore encodé), `EQUILY_STORE_PASSWORD`, `EQUILY_KEY_ALIAS`, `EQUILY_KEY_PASSWORD`. Les entrer via interface Secrets/Actions ou `gh secret set` avec entrée standard/fichiers, sans les afficher. Limiter les accès et protéger l’environnement `release` avant publication. Aucun secret disponible dans les PRs non fiables.

## Portes de sortie

1. Toutes les exigences du brief satisfaites, écarts `PROGRESS.md` résolus.
2. Compilation, unit tests, lint et tests instrumentés réussis.
3. Scénario intégral accepté hors ligne sur appareil : dossier, hypothèque 1070 €, récurrence, facture école 125 €, OCR/correction/original/enfant/payeur, remboursement partiel, rapport, sauvegarde et restauration après installation propre, réouverture persistante.
4. Langues, contrastes, clavier, TalkBack et thème sombre vérifiés.
5. Signature vérifiée avec `apksigner verify --verbose --print-certs`, certificat conservé pour les mises à jour ; checksum avec `sha256sum`.
6. Autorisation explicite de push/tag/publication ; notes indiquant limitations restantes, installation et risques de perte de clé.

Installation APK : télécharger APK et SHA-256 de la release, vérifier checksum, autoriser l’installation pour le gestionnaire de fichiers choisi puis ouvrir l’APK. Un APK debug et un APK release signés avec des clés différentes ne se remplacent pas : sauvegarder le dossier avant désinstallation.

Ne pas attribuer un tag à un état différent de l’APK vérifié. Augmenter versionCode pour les mises à jour, conserver package et certificat et ajouter des migrations non destructrices. Les rapports et sauvegardes ne prouvent jamais l’accord de l’autre personne.
