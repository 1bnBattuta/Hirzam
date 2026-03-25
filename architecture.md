# Architecture — Application de Messagerie Instantanée (Vert.x)

## Vue d'ensemble

L'application est une messagerie instantanée reposant sur un backend **Vert.x**, une base de données **PostgreSQL**, une API REST et un flux temps réel via **SockJS/WebSocket**. Elle suit le modèle **Verticle** de Vert.x, où chaque composant possède une responsabilité unique et communique via l'**Event Bus**.

---

## Structure des Verticles

```
MainVerticle
├── DatabaseVerticle   (gestion SQL / PostgreSQL)
└── HttpVerticle       (serveur HTTP, routeur, SockJS)
```

### `MainVerticle`
Point d'entrée de l'application. Orchestre le déploiement des autres Verticles en séquence :
1. Déploiement de `DatabaseVerticle` (la DB doit être prête avant tout)
2. Déploiement de `HttpVerticle`

> ⚠️ Les credentials de connexion à la base sont pour l'instant codés en dur — prévoir un `.env` ou des variables d'environnement.

---

### `DatabaseVerticle`
Responsable de toute interaction avec la base de données.

- Initialise un **JDBCPool** (max 10 connexions) vers PostgreSQL
- Crée le schéma au démarrage si inexistant (`CREATE TABLE IF NOT EXISTS`)
- Écoute sur l'**Event Bus** pour répondre aux requêtes SQL
- Expose les opérations :
  - `getLastMessages()` — récupération des 20 derniers messages triés par date
  - `addMessage(JsonObject)` — insertion d'un nouveau message
  - *(bonus)* `getMessage(id)`, `updateMessage(JsonObject)`, `deleteMessage(id)`

**Schéma de la table `messages` :**

| Colonne      | Type          | Contrainte                        |
|--------------|---------------|-----------------------------------|
| `id`         | SERIAL        | PRIMARY KEY                       |
| `username`   | VARCHAR(100)  | NOT NULL                          |
| `content`    | TEXT          | NOT NULL                          |
| `created_at` | TIMESTAMP     | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

---

### `HttpVerticle`
Responsable de la couche réseau HTTP.

- Crée un **Router** Vert.x Web sur le port `8080`
- Sert les fichiers statiques depuis `webroot/` (`index.html`)
- Expose l'**API REST** :

| Méthode | Route               | Description                         |
|---------|---------------------|-------------------------------------|
| GET     | `/api/messages`     | Récupère les 20 derniers messages   |
| POST    | `/api/messages`     | Ajoute un nouveau message           |
| *(bonus)* GET | `/api/message/:id` | Récupère un message par son id |
| *(bonus)* PUT | `/api/messages`    | Modifie un message existant    |
| *(bonus)* DELETE | `/api/message/:id` | Supprime un message par son id |

- Met en place le **flux SockJS** (`/eventbus/*`) pour diffuser en temps réel les nouveaux messages à tous les clients connectés

---

## Communication interne — Event Bus

Les Verticles ne s'appellent pas directement. Ils échangent des messages via l'Event Bus Vert.x :

```
HttpVerticle
    │  (requête : "db.getLastMessages")
    ▼
Event Bus
    │
    ▼
DatabaseVerticle  →  PostgreSQL
    │  (réponse : JsonArray de messages)
    ▼
Event Bus
    │
    ▼
HttpVerticle  →  Réponse HTTP au client
```

Pour les nouveaux messages (`POST /api/messages`), après insertion en base, un événement est publié sur l'Event Bus afin que `HttpVerticle` le diffuse via SockJS à tous les navigateurs connectés.

---

## Flux temps réel — SockJS / WebSocket

```
Navigateur A  ──POST /api/messages──▶  HttpVerticle
                                             │
                                       Event Bus publish
                                             │
                              ┌──────────────┴──────────────┐
                              ▼                             ▼
                         Navigateur A                  Navigateur B
                       (reçoit le msg                (reçoit le msg
                        via SockJS)                   via SockJS)
```

Le canal SockJS est accessible côté client via la bibliothèque `vertx-eventbus.js` (ou `sockjs-client`).

---

## Interface utilisateur (IHM)

Fichiers statiques servis depuis `src/main/resources/webroot/` :

```
webroot/
├── index.html       # page principale
├── app.js           # logique JS (gestion des événements, appels API, SockJS)
└── style.css        # mise en forme
```

**Fonctionnalités attendues :**
- Affichage des 20 derniers messages au chargement (appel `GET /api/messages`)
- Chaque message affiche : nom d'utilisateur, date/heure, contenu
- Formulaire avec champ `username`, champ `content` et bouton **Envoyer**
- À la soumission : appel `POST /api/messages` puis affichage automatique du message via SockJS

---

## Couche Service *(optionnelle, recommandée)*

Une interface `MessageService` peut être introduite pour abstraire l'accès aux données, en s'appuyant sur les **Service Proxies** de Vert.x. Cela découple `HttpVerticle` de l'implémentation SQL et facilite les tests unitaires.

```
HttpVerticle
    └── MessageService (proxy Event Bus)
            └── MessageServiceImpl
                    └── DatabaseVerticle (JDBCPool)
```

---

## Tests unitaires

Utilisation de **Vert.x JUnit5** (`vertx-junit5`) pour valider :
- `getLastMessages()` — retourne bien un tableau de messages
- `addMessage()` — insère correctement un message et retourne le résultat
- *(bonus)* `updateMessage()` / `deleteMessage()`

---

## Dépendances principales

| Module Vert.x              | Rôle                                      |
|----------------------------|-------------------------------------------|
| `vertx-web`                | Routeur HTTP, SockJS, fichiers statiques  |
| `vertx-jdbc-client`        | Pool de connexions JDBC                   |
| `vertx-junit5`             | Tests unitaires                           |
| `vertx-service-proxy`      | Couche Service (optionnel)                |
| `vertx-sockjs-service-proxy` | Intégration SockJS (optionnel)          |
| PostgreSQL JDBC Driver     | Connecteur base de données                |

---

## Arborescence du projet

```
src/
├── main/
│   ├── java/com/hirzam/
│   │   ├── MainVerticle.java
│   │   ├── DatabaseVerticle.java
│   │   ├── HttpVerticle.java
│   │   └── service/                  # (optionnel)
│   │       ├── MessageService.java
│   │       └── MessageServiceImpl.java
│   └── resources/
│       └── webroot/
│           ├── index.html
│           ├── app.js
│           └── style.css
└── test/
    └── java/com/hirzam/
        └── MessageServiceTest.java
```
