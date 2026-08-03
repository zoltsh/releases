package sh.zolt.releases.publication;

public final class ZapPublicationPreparer {
    private final CandidateReleaseValidator candidateValidator;
    private final ReleaseMetadataWriter metadataWriter;

    public ZapPublicationPreparer() {
        this(new CandidateReleaseValidator(), new ReleaseMetadataWriter());
    }

    ZapPublicationPreparer(
            CandidateReleaseValidator candidateValidator, ReleaseMetadataWriter metadataWriter) {
        this.candidateValidator = candidateValidator;
        this.metadataWriter = metadataWriter;
    }

    public String prepare(ZapPublicationRequest request) {
        ValidatedCandidateRelease release = candidateValidator.validate(request);
        metadataWriter.write(
                release,
                request.previousChannel(),
                request.previousIndex(),
                request.output());
        return release.version();
    }
}
