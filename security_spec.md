# Vidhya Firestore Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Private student profiles (`/users/{userId}`) and all subcollections (`notes`, `study_tasks`, `focus_sessions`, `tutor_messages`, `saved_videos`) can ONLY be read, created, updated, or deleted by `request.auth.uid == userId`.
2. **PII Split Isolation**: `/public_profiles/{userId}` never stores email addresses, school names, passcodes, or private schedules. Public listing requires `resource.data.shareStatsWithFriends == true || resource.data.userId == request.auth.uid`.
3. **Friendship Integrity**: A friendship (`/friendships/{friendshipId}`) can only be created with `status == 'pending'`, `requesterId == request.auth.uid`, and `requesterId != receiverId`. Only `receiverId` can transition `status` from `'pending'` to `'accepted'` or `'declined'`. Once `'declined'`, the record is terminal (cannot be updated, only deleted).
4. **Immutable Creation Metadata**: `userId`, `createdAt`, `requesterId`, and `receiverId` are immutable across all updates.
5. **Temporal Integrity**: All write timestamps (`createdAt`, `updatedAt`) must be server timestamps (`is timestamp && <= request.time`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users").doc("alice").get()` -> Reject (`!isSignedIn()`).
2. **Cross-User Profile Read**: Bob reading `/users/alice` -> Reject (`request.auth.uid != userId`).
3. **Ghost Field Injection (Shadow Update)**: Alice updating `/users/alice` with `{ isAdmin: true }` -> Reject (`hasOnly()` key allowlist).
4. **Owner Spoofing on Create**: Alice creating `/users/alice/notes/n1` with `userId: "bob"` -> Reject (`incoming().userId == request.auth.uid`).
5. **Immutable Field Mutation**: Alice updating `createdAt` or `userId` on an existing note -> Reject (`incoming().createdAt == existing().createdAt`).
6. **Oversized String Denial-of-Wallet**: Creating a note with a 50,000-char `title` -> Reject (`title.size() <= 150`).
7. **Path ID Poisoning**: Creating a document with invalid characters in ID -> Reject (`isValidId()`).
8. **Invalid Enum State**: Creating a `StudyTask` with `taskType: "hacking"` -> Reject (`taskType in ['study', 'revision', 'practice']`).
9. **Self-Friendship Exploit**: Alice creating `/friendships/f1` with `requesterId == receiverId` -> Reject (`requesterId != receiverId`).
10. **Auto-Accepted Friendship Exploit**: Alice creating `/friendships/f1` with `status: "accepted"` -> Reject (`status == 'pending'` on create).
11. **Requester Self-Accepting Friendship**: Alice (the requester) updating `/friendships/f1` to `status: "accepted"` -> Reject (`request.auth.uid == existing().receiverId` required to accept).
12. **Unfiltered Public Profile List Scraping**: Alice running `db.collection("public_profiles").get()` without filtering `shareStatsWithFriends == true` -> Reject (Query Enforcer).
