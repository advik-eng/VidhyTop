package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VidhyaRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveProfileAndCreateNote_authenticatedOwner_succeedsAndEmitsUpdates() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = VidhyaRepository(firestore, auth)

    val saveProfileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveOnboardingAndProfile(
        UserProfile(
          userId = aliceUid,
          displayName = "Alice Student",
          schoolName = "Vidhya Academy",
          gradeLevel = "Class 12",
          subjects = listOf("Physics", "Mathematics"),
          examType = "Board Finals",
          nextExamDate = "2026-11-25",
          prepStartDate = "2026-10-11",
          dailyStudyTargetMinutes = 150L,
          onboardingCompleted = true,
          referralCode = "VIDALICE12",
          shareStatsWithFriends = true
        )
      )
    }
    assertTrue("saveOnboardingAndProfile should succeed: ${saveProfileResult.exceptionOrNull()}", saveProfileResult.isSuccess)

    val createNoteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createNote(
        title = "Newton's Laws of Motion",
        subject = "Physics",
        sourceType = "topic",
        sourceSummary = "Core mechanics principles",
        kalamNoteContent = "# Newton's First Law\nAn object remains at rest or uniform motion unless acted upon by an external force.",
        keyTermsJson = "[]",
        formulasSummary = "F = m * a",
        revisionSummary = "Inertia depends on mass."
      )
    }
    assertTrue("createNote should succeed: ${createNoteResult.exceptionOrNull()}", createNoteResult.isSuccess)
    val noteId = createNoteResult.getOrThrow()
    assertTrue(noteId.isNotEmpty())

    val emittedNotes = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeNotes(aliceUid).first { list -> list.any { it.id == noteId } }
    }
    assertTrue(emittedNotes.any { it.id == noteId && it.title == "Newton's Laws of Motion" })
  }

  @Test
  fun getNoteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = VidhyaRepository(firestore, auth)
    val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createNote(
        title = "Private Calculus Note",
        subject = "Mathematics",
        sourceType = "topic",
        sourceSummary = null,
        kalamNoteContent = "Integration by parts formula",
        keyTermsJson = null,
        formulasSummary = null,
        revisionSummary = null
      ).getOrThrow()
    }

    // Switch to Bob and attempt to read Alice's private note
    signInTestUser(BOB_EMAIL)
    val bobRepo = VidhyaRepository(firestore, auth)
    val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getNoteById(aliceUid, noteId)
    }
    assertTrue("Cross-user read must fail", crossReadResult.isFailure)
    val ex = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
    assertNotNull("Expected FirebaseFirestoreException", ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = VidhyaRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeNotes(aliceUid).first()
      }
      fail("Expected FirebaseFirestoreException for unauthenticated user")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice.vidhya@test.com"
    const val BOB_EMAIL = "bob.vidhya@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
