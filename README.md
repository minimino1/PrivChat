# Chatt

Echtzeit-Chat-App: Kotlin + Jetpack Compose, Firebase Realtime Database als Backend,
Build komplett über GitHub Actions (kein Android SDK auf dem Rechner nötig).

## Was drin ist

- Anmeldung/Registrierung mit E-Mail + Passwort
- Chatliste in Echtzeit (letzte Nachricht, ungelesen-Markierung)
- Direktchats und Gruppenchats
- Nachrichten senden, bearbeiten, löschen
- „tippt gerade …"-Anzeige
- Online-/Offline-Status (über `onDisconnect`)
- Push-Benachrichtigungen (Cloud Function in `functions/`)
- Security Rules in `database.rules.json`

## 1. Firebase-Projekt anlegen

1. <https://console.firebase.google.com> → neues Projekt
2. **Build → Realtime Database** → *Erstellen* → Standort wählen
   (der Standort lässt sich später nicht mehr ändern)
3. **Authentication → Get started → E-Mail/Passwort aktivieren**
4. **Projekteinstellungen → Deine Apps → Android** hinzufügen
   - Paketname: `dev.and.chatt`
   - `google-services.json` herunterladen
5. Datei hierher kopieren:

   ```bash
   cp ~/Downloads/google-services.json app/google-services.json
   ```

   > Die eingecheckte `app/google-services.json` ist nur ein **Platzhalter**, damit der
   > CI-Build auch ohne Firebase-Konto durchläuft. Mit der echten Datei verbindet sich
   > die App wirklich.

6. Rules deployen:

   ```bash
   npm install -g firebase-tools   # oder: npx firebase-tools
   firebase login
   firebase use <deine-project-id>
   firebase deploy --only database
   ```

## 2. Bauen lassen (CI)

```bash
git remote add origin git@github.com:<dein-user>/chatt.git
git push -u origin main
```

Der Workflow `.github/workflows/android.yml` baut die APKs. Danach findest du sie unter
**Actions → letzter Lauf → Artifacts → chatt-debug** und installierst `app-debug.apk`
per Sideload.

Signiertes Release-Build braucht zusätzlich diese Repository-Secrets
(Settings → Secrets and variables → Actions):

| Secret | Inhalt |
|---|---|
| `KEYSTORE_FILE` | Pfad zur `.jks`/`.keystore`-Datei |
| `KEYSTORE_PASSWORD` | Store-Passwort |
| `KEY_ALIAS` | Key-Alias |
| `KEY_PASSWORD` | Key-Passwort |

Keystore erzeugen:

```bash
keytool -genkey -v -keystore chatt-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias chatt
```

## 3. Push-Benachrichtigungen (optional)

```bash
cd functions
npm install
npx firebase deploy --only functions
```

Der Spark-Tarif reicht dafür. Der Data-Payload enthält `conversationId`, `senderId`,
`title`, `text` – die App rendert daraus die Benachrichtigung und öffnet beim Tippen
den passenden Chat.

## Datenmodell

Alles besteht aus einfachen Skalaren und ist dadurch 1:1 auch mit dem JS-SDK
(`firebase-js-sdk`) konsumierbar – die Basis für ein späteres Web-Frontend.

```
users/{uid}
  displayName, email, avatarUrl, createdAt

conversations/{conversationId}
  id, title, isGroup, memberIds/{uid:true}, memberNames/{uid},
  createdBy, createdAt, updatedAt, lastMessageText, lastSenderId, lastMessageAt
  messages/{messageId}
    id, senderId, text, type, createdAt, editedAt?, deleted?

userConversations/{uid}/{conversationId}   # Index nur fuer den jeweiligen Nutzer
  ... (Kopie der Chatlisten-Daten) + lastReadAt

presence/{uid}      state: online|offline, lastSeen
typing/{conversationId}/{uid}   true | entfernt (onDisconnect)
fcmTokens/{uid}/{hash}          token, platform, updatedAt
```

`conversationId` für Direktchats ist `dm_<uidA>_<uidB>` (UIDs sortiert) – so finden
beide Seiten denselben Chat, ohne Coordination.

Der Index `userConversations` ist wichtig: So braucht die Chatliste **einen** Listener
statt einem pro Konversation, und die Security Rules können pro Nutzer filtern.

## Testen

Zum Testen auf **einem** Gerät einfach zwei Konten in derselben App anlegen
(dafür E-Mail/Passwort statt Google-Login).

## Bekannte Grenzen (v1)

- Ungelesen-Zähler ist nur ein Punkt (kein Zähler pro Nachricht); für echte Zähler
  bräuchte es eine Cloud Function.
- Keine Anhänge/Bilder, keine Sprachnachrichten, keine Suche im Chatverlauf.
- Online-Status ist für jeden angemeldeten Nutzer sichtbar (Rules vereinfacht).
- Gruppen-Chats lassen sich noch nicht umbenennen oder verlassen.