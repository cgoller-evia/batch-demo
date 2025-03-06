package com.cgoller.batchdemo.boundary;

import com.cgoller.batchdemo.dto.JobRequest;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
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
        try {
            // Convert VINs list to a comma-separated string
            String vinParam = String.join(",", jobRequest.getVins());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("vins", vinParam)
                    .addString("queryId", String.valueOf(jobRequest.getQueryId()))
                    // Add a unique parameter so the job can run multiple times
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(demoJob, jobParameters);
            return ResponseEntity.ok("Job started. Execution ID: " + jobExecution.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Job failed to start: " + e.getMessage());
        }
    }
}