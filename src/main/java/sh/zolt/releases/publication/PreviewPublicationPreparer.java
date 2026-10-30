package sh.zolt.releases.publication;

import sh.zolt.releases.policy.ReleaseChannel;

public final class PreviewPublicationPreparer {
    private final CandidateReleaseValidator candidateValidator;
    private final ReleaseMetadataWriter metadataWriter;
    private final ReleaseChannel channel;

    public PreviewPublicationPreparer() {
        this(new CandidateReleaseValidator(), new ReleaseMetadataWriter());
    }

    public PreviewPublicationPreparer(ReleaseChannel channel) {
        this.candidateValidator = new CandidateReleaseValidator();
        this.metadataWriter = new ReleaseMetadataWriter();
        this.channel = channel;
    }

    PreviewPublicationPreparer(
            CandidateReleaseValidator candidateValidator, ReleaseMetadataWriter metadataWriter) {
        this.channel = ReleaseChannel.PREVIEW;
        this.candidateValidator = candidateValidator;
        this.metadataWriter = metadataWriter;
    }

    public String prepare(PreviewPublicationRequest request) {
        ReleasePublicationRequest publication = new ReleasePublicationRequest(
                channel,
                request.releaseRecord(),
                request.sourceEvidence(),
                request.candidates(),
                request.previousChannel(),
                request.previousIndex(),
                request.expectedControllerSha(),
                request.expectedControllerRunId(),
                request.output());
        ValidatedCandidateRelease release = candidateValidator.validate(publication);
        metadataWriter.write(
                release,
                publication.previousChannel(),
                publication.previousIndex(),
                publication.output());
        return release.version();
    }
}
