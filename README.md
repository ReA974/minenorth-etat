# MineNorth État (`minenorthetat`)

Mairie du serveur RP MineNorth (Forge 1.20.1, package `fr.minenorth.etat`) : trésor et impôts, salaires par poste, agents municipaux, élections, lois et annonces officielles, bureau de vote, portefeuille.

## Tablette de la mairie
Objet tablette (maire, agents municipaux, OP) : onglets Résumé (trésor, impôts sur les comptes et les salaires, derniers mouvements), Salaires, Agents, Élection, Lois, Annonce. Chaque action est un `TabletAction` revérifié par le serveur (`TabletService`) ; un agent voit tout en lecture seule, un maire suspendu aussi.

## Application « Mairie » dans le téléphone MRPhone (30/10)
L'icône verte **Mairie** du téléphone patché (`minenorth-phone`, `JobsHook`, visible seulement pour le maire et les agents : `PhoneApp` / `PhoneNet.State`) ouvre l'application **dans l'écran du téléphone** : `client/phone/PhoneMairieScreen`, sous-classe de `AbstractZPhoneScreen` (barre d'état, cadre, retour à l'accueil par la barre du bas ou Échap), portrait, couleurs MineNorth. Mêmes onglets et actions que la tablette (Résumé, Salaires : toucher un poste pour changer son salaire, Agents : révoquer / nommer, Élection, Lois : toutes les lois, abroger / promulguer, Annonce), champs de saisie à la police du téléphone (clic pour écrire, Ctrl+V, Échap pour quitter le champ), bandeau vert / rouge pour le message du serveur.
- Ouverture : `PhoneAppClient.openFromPhone` → `phone.PhoneMairie.request()` envoie `PhoneNet.Open` sans fermer le téléphone ; le serveur revérifie le métier (`PhoneApp.open`) puis envoie l'état de la tablette, qui ouvre l'application (`PhoneNavigationState.open`) ; les états suivants la mettent à jour. Aucun paquet nouveau.
- La tablette (objet) garde son écran `TabletScreen`. Sans MRPhone : comportement d'avant. `PhoneAppClient.phone()` est testé AVANT tout appel à `phone.PhoneMairie` (la vérification de cette classe par la JVM charge les classes du téléphone).
- **Non testé en jeu** (mise en page, tailles de texte, saisie).

## Construire
```
JAVA_HOME=<JDK 17> ./gradlew build --offline
```
Construire `minenorth-api` d'abord (`../minenorth-api/build/libs`) et déposer `libs/mrphone-0.9.4.jar` (copie de « MRPhone 2 0.9.4 beta.jar », non redistribuée). Le jar `build/libs/minenorth-etat-1.0.0.jar` va dans `mods/` (serveur **et** clients).

## Licence
Tous droits réservés - MineNorthRP. Voir `LICENSE`.
