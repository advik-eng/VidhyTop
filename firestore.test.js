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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

const serverTimestamp = () => new Date(Date.now() - 1000);

// --- DIRTY DOZEN & SECURITY BOUNDS TESTS ---

test("1. Unauthenticated user cannot read private profiles or notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
});

test("2. Cross-user profile read is rejected (PII isolation)", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      gradeLevel: "Grade 12",
      dailyStudyTargetMinutes: 120,
      pomodoroFocusMinutes: 25,
      pomodoroBreakMinutes: 5,
      onboardingCompleted: true,
      referralCode: "VIDALICE",
      shareStatsWithFriends: true,
      themeMode: "system",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("3. Authenticated owner can create and update their own UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sharma",
      schoolName: "Delhi Public School",
      gradeLevel: "Class 12",
      subjects: ["Physics", "Mathematics", "Chemistry"],
      examType: "Board Exams",
      nextExamDate: "2026-11-20",
      prepStartDate: "2026-10-11",
      dailyStudyTargetMinutes: 180,
      preferredSchedule: "Morning & Evening",
      pomodoroFocusMinutes: 25,
      pomodoroBreakMinutes: 5,
      onboardingCompleted: true,
      referralCode: "VIDALICE",
      shareStatsWithFriends: true,
      themeMode: "system",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Valid update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyStudyTargetMinutes: 210,
      updatedAt: serverTimestamp(),
    })
  );

  // Shadow update with ghost field is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );
});

test("4. Authenticated owner can CRUD Kalam StudyNotes; cross-user access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1");

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "Electromagnetic Induction",
      subject: "Physics",
      sourceType: "topic",
      sourceSummary: "Faraday's laws and Lenz's law overview",
      kalamNoteContent: "# Electromagnetic Induction\nFaraday's Law states that induced EMF is proportional to rate of change of magnetic flux.",
      keyTermsJson: "[]",
      formulasSummary: "e = -dPhi/dt",
      revisionSummary: "Lenz's law conserves energy.",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());

  // Bob cannot read Alice's notes
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").get());
});

test("5. Public profiles enforce Query Enforcer on list queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sharma",
      gradeLevel: "Class 12",
      referralCode: "VIDALICE",
      totalVerifiedStudyMinutes: 240,
      totalVerifiedFocusMinutes: 150,
      completedPomodoroSessions: 6,
      currentStreakDays: 3,
      longestStreakDays: 5,
      lastQualifyingSessionDate: "2026-10-11",
      earnedBadges: ["streak_1", "streak_3"],
      shareStatsWithFriends: true,
      updatedAt: serverTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Query with shareStatsWithFriends == true succeeds
  await assertSucceeds(
    bobDb.collection("public_profiles").where("shareStatsWithFriends", "==", true).get()
  );

  // Unfiltered query without shareStatsWithFriends filter fails
  await assertFails(
    bobDb.collection("public_profiles").get()
  );
});

test("6. Friendship lifecycle: request, receiver acceptance, self-accept rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice cannot create an already-accepted friendship
  await assertFails(
    aliceDb.collection("friendships").doc("f_alice_bob").set({
      requesterId: ALICE_UID,
      receiverId: BOB_UID,
      requesterName: "Alice",
      receiverName: "Bob",
      status: "accepted",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice creates a pending friend request to Bob
  await assertSucceeds(
    aliceDb.collection("friendships").doc("f_alice_bob").set({
      requesterId: ALICE_UID,
      receiverId: BOB_UID,
      requesterName: "Alice",
      receiverName: "Bob",
      status: "pending",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice (requester) cannot accept her own friend request
  await assertFails(
    aliceDb.collection("friendships").doc("f_alice_bob").update({
      status: "accepted",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob (receiver) can accept the friend request
  await assertSucceeds(
    bobDb.collection("friendships").doc("f_alice_bob").update({
      status: "accepted",
      updatedAt: serverTimestamp(),
    })
  );

  // Alice can query friendships where she is a participant
  await assertSucceeds(
    aliceDb.collection("friendships").where("participantUids", "array-contains", ALICE_UID).get()
  );
});
