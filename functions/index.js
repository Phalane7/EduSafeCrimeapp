```javascript
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

admin.initializeApp();

const db = admin.firestore();

// ---------------------------------------------------------
// EDU SAFE SETTINGS
// ---------------------------------------------------------

const ALLOWED_EMAIL_DOMAIN = "@vossie.net";
const CODE_TTL_MINUTES = 10;

const REGISTRATION_COLLECTION = "registrationOtps";
const PASSWORD_RESET_COLLECTION = "passwordResets";

// ---------------------------------------------------------
// EMAIL CONFIGURATION
// ---------------------------------------------------------

const SMTP_USER = defineSecret("SMTP_USER");
const SMTP_PASS = defineSecret("SMTP_PASS");

const SMTP_HOST = defineString("SMTP_HOST", {
  default: "smtp.gmail.com",
});

const SMTP_PORT = defineString("SMTP_PORT", {
  default: "465",
});

// ---------------------------------------------------------
// HELPER FUNCTIONS
// ---------------------------------------------------------

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function checkEmailDomain(email) {
  if (!email.toLowerCase().endsWith(ALLOWED_EMAIL_DOMAIN)) {
    throw new HttpsError(
      "invalid-argument",
      "Please use your @vossie.net email address."
    );
  }
}

function generateSixDigitCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function createMailTransport() {
  return nodemailer.createTransport({
    host: SMTP_HOST.value(),
    port: Number(SMTP_PORT.value()),
    secure: Number(SMTP_PORT.value()) === 465,
    auth: {
      user: SMTP_USER.value(),
      pass: SMTP_PASS.value(),
    },
  });
}

// ---------------------------------------------------------
// 1. REGISTRATION - SEND OTP
// ---------------------------------------------------------

exports.sendRegistrationCode = onCall(
  {
    secrets: [SMTP_USER, SMTP_PASS],
  },
  async (request) => {
    const email = normalizeEmail(request.data.email);

    if (!email) {
      throw new HttpsError(
        "invalid-argument",
        "Email address is required."
      );
    }

    checkEmailDomain(email);

    let user;

    try {
      user = await admin.auth().getUserByEmail(email);
    } catch (error) {
      if (error.code === "auth/user-not-found") {
        throw new HttpsError(
          "not-found",
          "Please create your account first."
        );
      }

      throw error;
    }

    if (user.emailVerified) {
      throw new HttpsError(
        "already-exists",
        "This email address has already been verified."
      );
    }

    const code = generateSixDigitCode();

    const expiresAt =
      Date.now() + CODE_TTL_MINUTES * 60 * 1000;

    await db
      .collection(REGISTRATION_COLLECTION)
      .doc(email)
      .set({
        code: code,
        expiresAt: expiresAt,
        verified: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    const transporter = createMailTransport();

    await transporter.sendMail({
      from: SMTP_USER.value(),
      to: email,
      subject: "EduSafe Email Verification Code",
      text:
        `Your EduSafe verification code is ${code}. ` +
        `This code expires in ${CODE_TTL_MINUTES} minutes.`,
      html:
        `<p>Your EduSafe verification code is:</p>` +
        `<h2>${code}</h2>` +
        `<p>This code expires in ${CODE_TTL_MINUTES} minutes.</p>`,
    });

    logger.info(`Registration code sent to ${email}`);

    return {
      success: true,
      message: "Verification code sent to your email.",
    };
  }
);

// ---------------------------------------------------------
// 2. REGISTRATION - VERIFY OTP
// ---------------------------------------------------------

exports.verifyRegistrationCode = onCall(
  async (request) => {
    const email = normalizeEmail(request.data.email);
    const code = String(request.data.code || "").trim();

    if (!email || !code) {
      throw new HttpsError(
        "invalid-argument",
        "Email and verification code are required."
      );
    }

    checkEmailDomain(email);

    if (!/^\d{6}$/.test(code)) {
      throw new HttpsError(
        "invalid-argument",
        "Verification code must contain 6 digits."
      );
    }

    const docRef = db
      .collection(REGISTRATION_COLLECTION)
      .doc(email);

    const doc = await docRef.get();

    if (!doc.exists) {
      throw new HttpsError(
        "not-found",
        "No verification code was found. Please request a new code."
      );
    }

    const record = doc.data();

    if (Date.now() > record.expiresAt) {
      await docRef.delete();

      throw new HttpsError(
        "deadline-exceeded",
        "This verification code has expired. Please request a new code."
      );
    }

    if (record.code !== code) {
      throw new HttpsError(
        "invalid-argument",
        "Incorrect verification code."
      );
    }

    const user = await admin.auth().getUserByEmail(email);

    await admin.auth().updateUser(user.uid, {
      emailVerified: true,
    });

    await db
      .collection("users")
      .doc(user.uid)
      .set(
        {
          email: email,
          emailVerified: true,
          verified: true,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    await docRef.delete();

    return {
      success: true,
      message: "Email verified successfully.",
    };
  }
);

// ---------------------------------------------------------
// 3. FORGOT PASSWORD - SEND OTP
// ---------------------------------------------------------

exports.sendPasswordResetCode = onCall(
  {
    secrets: [SMTP_USER, SMTP_PASS],
  },
  async (request) => {
    const email = normalizeEmail(request.data.email);

    if (!email) {
      throw new HttpsError(
        "invalid-argument",
        "Email address is required."
      );
    }

    checkEmailDomain(email);

    let user;

    try {
      user = await admin.auth().getUserByEmail(email);
    } catch (error) {
      if (error.code === "auth/user-not-found") {
        return {
          success: true,
          message:
            "If the account exists, a reset code has been sent.",
        };
      }

      throw error;
    }

    const code = generateSixDigitCode();

    const expiresAt =
      Date.now() + CODE_TTL_MINUTES * 60 * 1000;

    await db
      .collection(PASSWORD_RESET_COLLECTION)
      .doc(email)
      .set({
        uid: user.uid,
        code: code,
        expiresAt: expiresAt,
        verified: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    const transporter = createMailTransport();

    await transporter.sendMail({
      from: SMTP_USER.value(),
      to: email,
      subject: "EduSafe Password Reset Code",
      text:
        `Your EduSafe password reset code is ${code}. ` +
        `This code expires in ${CODE_TTL_MINUTES} minutes.`,
      html:
        `<p>Your EduSafe password reset code is:</p>` +
        `<h2>${code}</h2>` +
        `<p>This code expires in ${CODE_TTL_MINUTES} minutes.</p>`,
    });

    return {
      success: true,
      message:
        "If the account exists, a reset code has been sent.",
    };
  }
);

// ---------------------------------------------------------
// 4. FORGOT PASSWORD - VERIFY OTP
// ---------------------------------------------------------

exports.verifyPasswordResetCode = onCall(
  async (request) => {
    const email = normalizeEmail(request.data.email);
    const code = String(request.data.code || "").trim();

    if (!email || !code) {
      throw new HttpsError(
        "invalid-argument",
        "Email and reset code are required."
      );
    }

    checkEmailDomain(email);

    if (!/^\d{6}$/.test(code)) {
      throw new HttpsError(
        "invalid-argument",
        "Reset code must contain 6 digits."
      );
    }

    const docRef = db
      .collection(PASSWORD_RESET_COLLECTION)
      .doc(email);

    const doc = await docRef.get();

    if (!doc.exists) {
      throw new HttpsError(
        "not-found",
        "No reset code was found."
      );
    }

    const record = doc.data();

    if (Date.now() > record.expiresAt) {
      await docRef.delete();

      throw new HttpsError(
        "deadline-exceeded",
        "This reset code has expired."
      );
    }

    if (record.code !== code) {
      throw new HttpsError(
        "invalid-argument",
        "Incorrect reset code."
      );
    }

    await docRef.update({
      verified: true,
    });

    return {
      success: true,
      message: "Reset code verified.",
    };
  }
);

// ---------------------------------------------------------
// 5. FORGOT PASSWORD - SET NEW PASSWORD
// ---------------------------------------------------------

exports.resetPasswordWithCode = onCall(
  async (request) => {
    const email = normalizeEmail(request.data.email);
    const code = String(request.data.code || "").trim();
    const newPassword = String(request.data.newPassword || "");

    if (!email || !code || !newPassword) {
      throw new HttpsError(
        "invalid-argument",
        "Email, code and new password are required."
      );
    }

    checkEmailDomain(email);

    if (newPassword.length < 8) {
      throw new HttpsError(
        "invalid-argument",
        "Password must be at least 8 characters."
      );
    }

    const docRef = db
      .collection(PASSWORD_RESET_COLLECTION)
      .doc(email);

    const doc = await docRef.get();

    if (!doc.exists) {
      throw new HttpsError(
        "not-found",
        "No password reset request was found."
      );
    }

    const record = doc.data();

    if (Date.now() > record.expiresAt) {
      await docRef.delete();

      throw new HttpsError(
        "deadline-exceeded",
        "This reset code has expired."
      );
    }

    if (!record.verified || record.code !== code) {
      throw new HttpsError(
        "failed-precondition",
        "Please verify your reset code first."
      );
    }

    const user = await admin.auth().getUserByEmail(email);

    await admin.auth().updateUser(user.uid, {
      password: newPassword,
    });

    await docRef.delete();

    return {
      success: true,
      message: "Password changed successfully.",
    };
  }
);

// ---------------------------------------------------------
// 6. ADMIN - VERIFY USER
// ---------------------------------------------------------

function assertIsAdmin(request) {
  if (
    !request.auth ||
    request.auth.token.admin !== true
  ) {
    throw new HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }
}

exports.adminVerifyUser = onCall(
  async (request) => {
    assertIsAdmin(request);

    const uid = String(request.data.uid || "");

    if (!uid) {
      throw new HttpsError(
        "invalid-argument",
        "User ID is required."
      );
    }

    await db
      .collection("users")
      .doc(uid)
      .set(
        {
          verified: true,
          updatedAt:
            admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    return {
      success: true,
    };
  }
);

// ---------------------------------------------------------
// 7. ADMIN - BAN USER
// ---------------------------------------------------------

exports.adminBanUser = onCall(
  async (request) => {
    assertIsAdmin(request);

    const uid = String(request.data.uid || "");

    if (!uid) {
      throw new HttpsError(
        "invalid-argument",
        "User ID is required."
      );
    }

    await admin.auth().updateUser(uid, {
      disabled: true,
    });

    await db
      .collection("users")
      .doc(uid)
      .set(
        {
          banned: true,
          updatedAt:
            admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    return {
      success: true,
    };
  }
);

// ---------------------------------------------------------
// 8. ADMIN - DELETE USER
// ---------------------------------------------------------

exports.adminDeleteUser = onCall(
  async (request) => {
    assertIsAdmin(request);

    const uid = String(request.data.uid || "");

    if (!uid) {
      throw new HttpsError(
        "invalid-argument",
        "User ID is required."
      );
    }

    await admin.auth().deleteUser(uid);

    await db
      .collection("users")
      .doc(uid)
      .delete();

    return {
      success: true,
    };
  }
);

