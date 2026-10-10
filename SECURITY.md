# Sécurité

Application sans permission INTERNET, sans publicité/analytique, sans journaux applicatifs contenant des données financières. Stockage privé Android, accès documents par SAF/FileProvider et capture par application caméra système. Aucun accès général au stockage, aucune permission caméra persistante. Les URI sont partagées seulement à la demande lors de l’ouverture d’un original.

Le stockage actif Room/pièces jointes n’est pas chiffré par Equily. La protection dépend du sandbox et du chiffrement du système Android. Ne pas annoncer une protection contre un appareil compromis/rooté. Le verrouillage biométrique/code appareil est facultatif et masque l’application après passage en arrière-plan ; il ne chiffre pas la base. Les captures/aperçus système sont bloqués par FLAG_SECURE.

Sauvegarde : AES-256-GCM et PBKDF2-HMAC-SHA256 à 600000 itérations, sel et nonce renouvelés. Voir `ARCHITECTURE.md`. Les erreurs de mot de passe, altération ou archive inconnue échouent avant mutation des enregistrements. Limites de taille, noms internes restreints, hashes et tailles vérifiés. Les fichiers temporaires de restauration sont supprimés en finally. La passphrase CharArray est effacée après usage, mais le champ Compose et ses copies JVM ne peuvent garantir un effacement matériel de toute mémoire.

Les imports sont limités et validés par signature/mime ; les parseurs PDF et images doivent rester mis à jour. Aucune garantie contre tous les fichiers malveillants n’est donnée. Les fichiers caméra temporaires et pièces orphelines nécessitent un nettoyage complémentaire avant release. Les corrections restent dans l’audit et sont donc conservées dans les sauvegardes.

La clé de signature Android est distincte du chiffrement des sauvegardes. Elle reste hors dépôt, avec permissions restrictives. Une perte de clé peut empêcher les mises à jour des APK installés. Ne jamais régénérer la clé de production à chaque build. Procédure dans `RELEASE.md`.

Pour signaler un problème, contacter le propriétaire du dépôt sans publier de documents familiaux, mots de passe ou clés dans une issue publique.
