# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Multi-User Isolation**: Every document in `/attendance_logs/{logId}` and `/user_settings/{userId}` MUST belong strictly to the authenticated caller (`request.auth.uid == data.userId`). Cross-user reads, queries, updates, and deletes are strictly forbidden.
2. **Immutable Identity & Creation Metadata**: On `update`, `userId`, `dateKey` (for `attendance_logs`), and `createdAt` MUST remain identical to `resource.data`.
3. **Strict Schema & Bounds**: Every string field has an explicit `.size()` bound, every integer field has range bounds (`0..23` for hours, `0..59` for minutes), and only allowlisted keys (`hasOnly`) are accepted.
4. **Temporal Integrity**: `createdAt` and `updatedAt` must be Firestore `timestamp` instances `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `GET /attendance_logs/log_1` with `auth == null` -> REJECTED.
2. **Cross-User Read**: Alice (`alice_123`) reads Bob's (`bob_456`) attendance log -> REJECTED.
3. **Unfiltered Collection Query**: Alice queries `/attendance_logs` without `.where("userId", "==", "alice_123")` when Bob's documents exist -> REJECTED.
4. **Identity Spoofing on Create**: Alice creates an attendance log with `userId: "bob_456"` -> REJECTED.
5. **Shadow / Ghost Field Injection**: Alice creates or updates a log with `isAdmin: true` or `extraField: "hack"` -> REJECTED by `hasOnly()`.
6. **Immutable Field Mutation (`userId`)**: Alice updates her log to transfer `userId` to `"bob_456"` -> REJECTED.
7. **Immutable Field Mutation (`createdAt`)**: Alice updates `createdAt` to a new timestamp -> REJECTED.
8. **Immutable Field Mutation (`dateKey`)**: Alice updates `dateKey` on an existing attendance log -> REJECTED.
9. **String Overflow (Denial of Wallet)**: Alice sends a 5,000-character `activitySummary` (> 2000 limit) -> REJECTED.
10. **Invalid Date Format**: Alice creates a log with `dateKey: "31-10-2026"` instead of `"YYYY-MM-DD"` -> REJECTED.
11. **Out-of-Bounds Reminder Time**: Alice sets `monThuHour: 25` or `friMinute: 75` in `/user_settings/{userId}` -> REJECTED.
12. **Future Timestamp Injection**: Alice sets `createdAt` to `request.time + 1 hour` -> REJECTED.
