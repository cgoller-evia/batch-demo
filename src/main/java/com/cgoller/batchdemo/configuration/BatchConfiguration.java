package com.cgoller.batchdemo.configuration;

import java.util.Arrays;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.PartitionStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {

    /**
     * 1) The overall job:
     *    We start with the partitionStep.
     */
    @Bean
    public Job demoJob(JobRepository jobRepository, Step partitionStep) {
        return new JobBuilder("demoJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(partitionStep)
                .build();
    }

    /**
     * 2) The partition step:
     *    - Uses a custom VinPartitioner to split the VINs into slices.
     *    - For each slice, it calls 'workerStep' in parallel.
     *    - The concurrency is controlled by 'taskExecutor'.
     */
    @Bean
    public Step partitionStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              Partitioner vinPartitioner,
                              Step workerStep,
                              TaskExecutor taskExecutor) {

        return new PartitionStepBuilder(new StepBuilder("partitionStep", jobRepository))
                .partitioner("workerStep", vinPartitioner)
                .step(workerStep)
                .taskExecutor(taskExecutor)
                .build();
    }

    /**
     * 3) The worker step (executed per partition).
     *    - chunk(1) reads from partitionedVinReader -> processor -> writer
     *    - This step is single-threaded within each partition,
     *      but multiple partitions will run in parallel.
     */
    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ItemProcessor<String, String> processor,
                           ItemWriter<String> writer,
                           ListItemReader<String> partitionedVinReader) {

        return new StepBuilder("workerStep", jobRepository)
                .<String, String>chunk(1, transactionManager)
                .reader(partitionedVinReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * 4) A Partitioner that:
     *    - Reads the "vins" parameter from jobParameters (comma-separated).
     *    - Reads the "queryId" to decide how many partitions to create
     *      (i.e. parallelism = complexity).
     *    - Splits the VIN list into 'partitionCount' slices.
     *    - Puts each slice (as comma-separated) into the StepExecutionContext
     *      so the worker step can read it.
     */
    @Bean
    @StepScope
    public Partitioner vinPartitioner(
            @Value("#{jobParameters['vins']}") String vinsParam,
            @Value("#{jobParameters['queryId']}") String queryIdParam
    ) {
        return new VinPartitioner(vinsParam, queryIdParam);
    }
    /**
     * 5) The worker step’s ItemReader, using step-scoped parameters from the partition.
     *    - Each partition sets 'vinSlice' in its ExecutionContext, containing
     *      only that partition’s VIN subset (as a comma-separated string).
     *    - We parse that subset into a ListItemReader.
     *
     *    We *could* also use a SynchronizedItemStreamReader if
     *    we planned to multi-thread inside each partition,
     *    but typically each partition is single-threaded, so ListItemReader alone is fine here.
     */
    @Bean
    @StepScope
    public ListItemReader<String> partitionedVinReader(
            @Value("#{stepExecutionContext['vinSlice']}") String vinSlice) {

        List<String> vinList = Arrays.asList(vinSlice.split(","));
        return new ListItemReader<>(vinList);
    }

    /**
     * 6) A step-scoped processor that:
     *    - Sleeps 2 seconds to simulate I/O
     *    - Prints "Executing Query X for vin: Y"
     *    - Reads the "queryId" from jobParameters (the same in each partition)
     */
    @Bean
    @StepScope
    public ItemProcessor<String, String> processor(
            @Value("#{jobParameters['queryId']}") String queryIdParam) {

        return vin -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Executing Query " + queryIdParam + " for vin: " + vin);
            return vin;
        };
    }

    /**
     * 7) A step-scoped no-op writer.
     *    (You could insert data into a DB, call a service, etc.)
     */
    @Bean
    @StepScope
    public ItemWriter<String> writer() {
        return items -> {
            // no-op
        };
    }

    /**
     * 8) The TaskExecutor used by the partition step to run partitions in parallel.
     *    Also uses 'queryId' to set parallelism (like a "complexity" factor).
     */
    @Bean
    @StepScope
    public TaskExecutor taskExecutor(@Value("#{jobParameters['queryId']}") String queryIdParam) {
        int queryId = Integer.parseInt(queryIdParam);
        int poolSize = getComplexity(queryId);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setThreadNamePrefix("Partition-");
        executor.afterPropertiesSet();
        executor.setAllowCoreThreadTimeOut(true);
        return executor;
    }

    private int getComplexity(int queryId) {
        switch (queryId) {
            case 1: return 1;
            case 2: return 2;
            case 3: return 3;
            default: return 1;
        }
    }
}