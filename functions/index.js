const functions = require("firebase-functions/v1");
const logger = require("firebase-functions/logger");
const { defineString, defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

const ALLOWED_DOMAIN = "@vossie.net";
const CODE_TTL_MINUTES = 10; // how long a registration code stays valid
const VERIFICATION_COLLECTION = "emailVerifications";

/**
 * Two separate flows, two separate mechanisms:
 *   - New account      -> this file's 6-digit code, emailed via Resend below.
 *   - Forgot password  -> Firebase Authentication's own built-in reset-link
 *     email (FirebaseAuth.sendPasswordResetEmail on the Android side).
 *     Nothing in this file handles forgot-password anymore.
 */

/**
 * Resend (resend.com) is a free transactional-email API - no SMTP server,
 * no app passwords, just one HTTPS request. Free tier: 3,000 emails/month,
 * 100/day, which is plenty for a student project.
 *
 *   RESEND_FROM  -> functions/.env (see .env.example). Use
 *                   "onboarding@resend.dev" for testing (works immediately,
 *                   no domain setup) or your own verified sending domain later.
 *   RESEND_API_KEY -> Secret Manager (the deploy command prompts for it)
 */
const RESEND_FROM = defineString("RESEND_FROM", { default: "onboarding@resend.dev" });
const RESEND_API_KEY = defineSecret("RESEND_API_KEY");

async function sendEmail(to, subject, html, text) {
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${RESEND_API_KEY.value()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: RESEND_FROM.value(),
      to,
      subject,
      html,
      text,
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`Resend API error (${response.status}): ${errorBody}`);
  }
}

function generateSixDigitCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function assertVossieEmail(email) {
  if (typeof email !== "string" || !email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Invalid email. Please use your @vossie.net address."
    );
  }
}

// ---------------------------------------------------------------------------
// 1. Enforce the @vossie.net domain at account-creation time.
//    Client-side validation can be bypassed, so this trigger is the real
//    safety net: any account created with a non-vossie.net email is deleted
//    immediately after Firebase Auth creates it.
// ---------------------------------------------------------------------------
exports.enforceEmailDomainOnCreate = functions.auth.user().onCreate(async (user) => {
  const email = user.email || "";
  if (!email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
    await admin.auth().deleteUser(user.uid);
    logger.warn(`Deleted account with disallowed email domain: ${email}`);
  }
});

// ---------------------------------------------------------------------------
// 2. Registration step 1 - generate + email a 6-digit code right after
//    sign-up. Called from SignUpActivity, and again from LoginActivity if
//    someone tries to log in before verifying.
// ---------------------------------------------------------------------------
exports.sendRegistrationCode = functions.runWith({ secrets: [RESEND_API_KEY] }).https.onCall(async (data) => {
  const email = (data.email || "").trim().toLowerCase();
  assertVossieEmail(email);

  let user;
  try {
    user = await admin.auth().getUserByEmail(email);
  } catch (e) {
    throw new functions.https.HttpsError("not-found", "No account found for this email.");
  }

  const code = generateSixDigitCode();
  const expiresAt = Date.now() + CODE_TTL_MINUTES * 60 * 1000;

  await db.collection(VERIFICATION_COLLECTION).doc(user.uid).set({
    email,
    code,
    expiresAt,
  });

  try {
    await sendEmail(
      email,
      "Verify your EduSafe account",
      `<p>Your EduSafe verification code is:</p><h2>${code}</h2><p>It expires in ${CODE_TTL_MINUTES} minutes.</p>`,
      `Your EduSafe verification code is ${code}. It expires in ${CODE_TTL_MINUTES} minutes.`
    );
  } catch (err) {
    logger.error("Failed to send registration code email", err);
    throw new functions.https.HttpsError(
      "internal",
      "We could not send the email right now. Please try again later."
    );
  }

  return { success: true };
});

// ---------------------------------------------------------------------------
// 3. Registration step 2 - verify the 6-digit code. On success, marks the
//    account verified in both Firebase Auth and the Firestore profile.
// ---------------------------------------------------------------------------
exports.verifyRegistrationCode = functions.https.onCall(async (data) => {
  const email = (data.email || "").trim().toLowerCase();
  const code = (data.code || "").trim();
  assertVossieEmail(email);

  let user;
  try {
    user = await admin.auth().getUserByEmail(email);
  } catch (e) {
    throw new functions.https.HttpsError("not-found", "No account found for this email.");
  }

  const docRef = db.collection(VERIFICATION_COLLECTION).doc(user.uid);
  const doc = await docRef.get();

  if (!doc.exists) {
    throw new functions.https.HttpsError("not-found", "No code found for this account. Please request a new one.");
  }

  const record = doc.data();

  if (Date.now() > record.expiresAt) {
    throw new functions.https.HttpsError("deadline-exceeded", "This code has expired. Please request a new one.");
  }

  if (record.code !== code) {
    throw new functions.https.HttpsError("invalid-argument", "Incorrect code. Please try again.");
  }

  await admin.auth().updateUser(user.uid, { emailVerified: true });
  await db.collection("users").doc(user.uid).update({ verified: true });
  await docRef.delete(); // one-time use

  return { success: true };
});

// ---------------------------------------------------------------------------
// Bonus, matches the "admin manages users" requirement noted for this project:
// verify / ban / delete callables, restricted to signed-in admins via a custom claim.
// Set the claim once from a trusted environment, e.g.:
//   admin.auth().setCustomUserClaims(uid, { admin: true });
// ---------------------------------------------------------------------------
function assertIsAdmin(context) {
  if (!context.auth || context.auth.token.admin !== true) {
    throw new functions.https.HttpsError("permission-denied", "Admin access required.");
  }
}

exports.adminVerifyUser = functions.https.onCall(async (data, context) => {
  assertIsAdmin(context);
  await db.collection("users").doc(data.uid).update({ verified: true });
  return { success: true };
});

exports.adminBanUser = functions.https.onCall(async (data, context) => {
  assertIsAdmin(context);
  await admin.auth().updateUser(data.uid, { disabled: true });
  await db.collection("users").doc(data.uid).update({ banned: true });
  return { success: true };
});

exports.adminDeleteUser = functions.https.onCall(async (data, context) => {
  assertIsAdmin(context);
  await admin.auth().deleteUser(data.uid);
  await db.collection("users").doc(data.uid).delete();
  return { success: true };
});
