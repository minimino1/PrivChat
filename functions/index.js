/**
 * Cloud Function: schickt eine Push-Nachricht, sobald in der Realtime Database
 * eine Nachricht erstellt wird. Die App bekommt ausschliesslich Data-Messages,
 * damit sie selbst die Benachrichtigung rendern kann (Deep-Link in den Chat).
 *
 * Deployment (einmalig, aus dem Projektordner):
 *   cd functions
 *   npm install
 *   npx firebase-tools login
 *   npx firebase use <deine-project-id>
 *   npx firebase deploy --only functions,database
 */
const admin = require("firebase-admin");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const { onValueWritten } = require("firebase-functions/v2/database");

if (!getApps().length) initializeApp();

const REGION = "europe-west1";

exports.notifyOnNewMessage = onValueWritten(
  {
    ref: "/conversations/{conversationId}/messages/{messageId}",
    region: REGION,
  },
  async (event) => {
    const after = event.data.after.val();
    const before = event.data.before.val();
    // Nur beim Anlegen ausloesen, nicht bei Edits.
    if (!after || before) return null;

    const conversationId = event.params.conversationId;
    const conversationRef = event.data.ref.parent.parent; // /conversations/{id}

    const [conversationSnapshot, tokensSnapshot] = await Promise.all([
      conversationRef.get(),
      admin.database().ref("fcmTokens").get(),
    ]);

    const conversation = conversationSnapshot.val() || {};
    const members = Object.keys(conversation.memberIds || {});
    const senderId = after.senderId;
    const senderName = (conversation.memberNames || {})[senderId] || "Neue Nachricht";

    const allTokens = tokensSnapshot.val() || {};
    const tokens = [];
    for (const uid of members) {
      if (uid === senderId) continue; // sich selbst nicht benachrichtigen
      for (const device of Object.values(allTokens[uid] || {})) {
        if (device && device.token) tokens.push(device.token);
      }
    }

    if (tokens.length === 0) return null;

    const result = await getMessaging().sendEachForMulticast({
      tokens,
      data: {
        conversationId,
        senderId,
        title: senderName,
        text: String(after.text || ""),
      },
      android: { priority: "high" },
    });

    return { delivered: result.successCount, attempted: tokens.length };
  }
);