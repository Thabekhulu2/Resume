package com.resume.backend.scoring;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ScoreResumeFitWorkflow {
    @WorkflowMethod
    ScoreResumeFitResult run(ScoreResumeFitRequest request);
}
