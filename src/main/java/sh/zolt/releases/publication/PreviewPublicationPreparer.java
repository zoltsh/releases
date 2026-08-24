package sh.zolt.releases.publication;

import sh.zolt.releases.policy.ReleaseChannel;

public final class PreviewPublicationPreparer {
    private final CandidateReleaseValidator candidateValidator;
    private final ReleaseMetadataWriter metadataWriter;

    public PreviewPublicationPreparer() {
        this(new CandidateReleaseValidator(), new ReleaseMetadataWriter());
    }

    PreviewPublicationPreparer(
            CandidateReleaseValidator candidateValidator, ReleaseMetadataWriter metadataWriter) {
        this.candidateValidator = candidateValidator;
        this.metadataWriter = metadataWriter;
    }

    public String prepare(PreviewPublicationRequest request) {
        ReleasePublicationRequest publication = new ReleasePublicationRequest(
                ReleaseChannel.PREVIEW,
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
