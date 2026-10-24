package in.anytimework.atwbackend.document.domain;

/**
 * Lifecycle of a {@link Document} row relative to its blob in storage.
 *
 * <p>A new row starts {@code PENDING} (metadata committed, blob not yet confirmed uploaded),
 * moves to {@code COMPLETE} once the upload and its follow-up commit both succeed, or to
 * {@code FAILED} if the reconciliation job finds a stale {@code PENDING} row with no blob behind
 * it. See {@code DocumentServiceImpl.upload()} and {@code DocumentUploadReconciliationJob}.</p>
 */
public enum DocumentUploadStatus {
    PENDING,
    COMPLETE,
    FAILED
}
