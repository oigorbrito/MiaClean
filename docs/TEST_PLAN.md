# TEST_PLAN.md

## Unit Tests
- **Hashing (`data/hash`)**: Verify MD5 consistency and pHash Hamming distance logic.
- **Classifiers (`data/classify`)**: Test `MediaClassifier` with various filename patterns and metadata.
- **DAO (`data/db`)**: Use Room's in-memory database to test CRUD operations and duplicate grouping queries.
- **WorkManager (`work`)**: Test worker queuing and execution state.

## Integration & Mocking
- **Billing**: Use `PlayBillingRepository` with a fake/mock `BillingClient` to simulate successful and failed purchases.
- **Permissions**: Strategy involves manual verification using a debug screen or specialized test activity that simulates permission grants/revocations.

## Manual Smoke Tests
- Perform a full scan on a physical device with real media.
- Verify WhatsApp SAF fallback by revoking and granting access to the WhatsApp folder.
- Test batch deletion flow with a small set of dummy images.
- Verify widget updates after a scan completes.

## Future Harness
- Implement a `DummyMediaGenerator` to populate the device with known duplicate sets (MD5, pHash, and Semantic) for automated integration testing.
