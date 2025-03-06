package com.cgoller.batchdemo.boundary;

import com.cgoller.batchdemo.dto.JobRequest;
import java.util.concurrent.CompletableFuture;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobLauncher jobLauncher;
    private final Job demoJob;

    public JobController(JobLauncher jobLauncher, Job demoJob) {
        this.jobLauncher = jobLauncher;
        this.demoJob = demoJob;
    }

    @PostMapping(value = "/launch", produces = "application/text")
    public ResponseEntity<String> launchJob(@RequestBody JobRequest jobRequest) {
        // Convert VIN list to a comma-separated string
        String vinParam = String.join(",", jobRequest.getVins());

        // Build JobParameters
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("vins", vinParam)
                .addString("queryId", String.valueOf(jobRequest.getQueryId()))
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // Launch asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                jobLauncher.run(demoJob, jobParameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Return immediately
        return ResponseEntity.ok("Job submitted asynchronously");
    }
}