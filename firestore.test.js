const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write attendance_logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("attendance_logs").get());
  await assertFails(
    unauthDb.collection("attendance_logs").doc("log_1").set({
      userId: ALICE_UID,
      dateKey: "2026-10-31",
      checkedOut: true,
      activitySummary: "Laporan harian magang",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user can create and query own attendance_logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("attendance_logs").doc("alice_2026-10-31").set({
      userId: ALICE_UID,
      dateKey: "2026-10-31",
      checkedOut: true,
      checkOutTime: "16:05 WIB",
      activitySummary: "Menyelesaikan laporan monev magang dan upload dokumentasi",
      driveFileId: "drive_file_abc123",
      driveWebViewLink: "https://drive.google.com/file/d/drive_file_abc123/view",
      driveFileName: "Dokumentasi_2026-10-31.jpg",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("attendance_logs")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user cannot read or update another user's attendance_logs", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("attendance_logs").doc("bob_log").set({
      userId: BOB_UID,
      dateKey: "2026-10-31",
      checkedOut: false,
      activitySummary: "Kegiatan Bob",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("attendance_logs").doc("bob_log").get());
  await assertFails(
    aliceDb.collection("attendance_logs").doc("bob_log").update({
      checkedOut: true,
      updatedAt: new Date(),
    })
  );
});

test("Rejects shadow fields, invalid dateKey, and userId spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Spoofed userId
  await assertFails(
    aliceDb.collection("attendance_logs").doc("spoof_log").set({
      userId: BOB_UID,
      dateKey: "2026-10-31",
      checkedOut: true,
      activitySummary: "Spoof",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid dateKey format
  await assertFails(
    aliceDb.collection("attendance_logs").doc("bad_date").set({
      userId: ALICE_UID,
      dateKey: "31/10/2026",
      checkedOut: true,
      activitySummary: "Bad date",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field injection
  await assertFails(
    aliceDb.collection("attendance_logs").doc("ghost_field").set({
      userId: ALICE_UID,
      dateKey: "2026-10-31",
      checkedOut: true,
      activitySummary: "Valid text",
      isVerifiedAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user can manage own user_settings and fails on invalid hours", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("user_settings").doc(ALICE_UID).set({
      userId: ALICE_UID,
      monThuHour: 16,
      monThuMinute: 0,
      friHour: 16,
      friMinute: 30,
      reminderEnabled: true,
      autoLoginEnabled: true,
      driveFolderName: "Dokumentasi MagangHub Kemnaker",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid hour (25)
  await assertFails(
    aliceDb.collection("user_settings").doc(ALICE_UID).update({
      monThuHour: 25,
      updatedAt: new Date(),
    })
  );
});
