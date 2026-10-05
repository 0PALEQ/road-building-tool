package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ConstructionJobCancellationService {
    public Outcome cancel(
            ConstructionJobCancelC2SPacket request,
            UUID playerId,
            RoadBuilderConstructionJobSavedData data
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(data, "data");
        if (!data.isCompatible()) {
            return outcome(
                    request,
                    ConstructionJobCancelResultCode.PERSISTENCE_INCOMPATIBLE
            );
        }

        ConstructionJob job = data.job(request.jobId()).orElse(null);
        if (job == null) {
            return outcome(request, ConstructionJobCancelResultCode.UNKNOWN_JOB);
        }
        if (!job.requestedBy().equals(playerId)) {
            return outcome(request, ConstructionJobCancelResultCode.NOT_AUTHORIZED);
        }
        if (job.phase() == ConstructionJobPhase.CANCELLING) {
            return outcome(
                    request,
                    ConstructionJobCancelResultCode.ALREADY_CANCELLING
            );
        }
        if (job.phase() == ConstructionJobPhase.CANCELLED) {
            return outcome(
                    request,
                    ConstructionJobCancelResultCode.ALREADY_CANCELLED
            );
        }
        if (job.phase() == ConstructionJobPhase.COMPLETING
                || job.phase() == ConstructionJobPhase.QUARANTINED) {
            return outcome(request, ConstructionJobCancelResultCode.INVALID_PHASE);
        }

        ConstructionJob updated = job.requestCancellation();
        data.replace(updated);
        return new Outcome(
                ConstructionJobCancelResultS2CPacket.current(
                        request,
                        ConstructionJobCancelResultCode.ACCEPTED
                ),
                Optional.of(updated)
        );
    }

    private static Outcome outcome(
            ConstructionJobCancelC2SPacket request,
            ConstructionJobCancelResultCode code
    ) {
        return new Outcome(
                ConstructionJobCancelResultS2CPacket.current(request, code),
                Optional.empty()
        );
    }

    public record Outcome(
            ConstructionJobCancelResultS2CPacket result,
            Optional<ConstructionJob> updatedJob
    ) {
        public Outcome {
            Objects.requireNonNull(result, "result");
            updatedJob = Objects.requireNonNull(updatedJob, "updatedJob");
            if ((result.resultCode() == ConstructionJobCancelResultCode.ACCEPTED)
                    != updatedJob.isPresent()) {
                throw new IllegalArgumentException(
                        "accepted cancellation must include the updated job"
                );
            }
        }
    }
}
