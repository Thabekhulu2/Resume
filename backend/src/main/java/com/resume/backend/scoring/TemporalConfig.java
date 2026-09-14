package com.resume.backend.scoring;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs workflowServiceStubs(@Value("${temporal.address}") String address) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(address).build());
    }

    @Bean
    public WorkflowClient workflowClient(
            WorkflowServiceStubs serviceStubs, @Value("${temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(
                serviceStubs, WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory(
            WorkflowClient workflowClient,
            @Value("${temporal.task-queue}") String taskQueue,
            ResumeParsingActivities resumeParsingActivities,
            ScoringActivities scoringActivities,
            CoreEntityActivities coreEntityActivities) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(ScoreResumeFitWorkflowImpl.class);
        worker.registerActivitiesImplementations(resumeParsingActivities, scoringActivities, coreEntityActivities);
        factory.start();
        return factory;
    }
}
