package com.cgoller.batchdemo.configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

public class VinPartitioner implements Partitioner {

    private final String vinsParam;
    private final String queryIdParam;

    public VinPartitioner(String vinsParam, String queryIdParam) {
        this.vinsParam = vinsParam;
        this.queryIdParam = queryIdParam;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // parse the VIN list from vinsParam
        List<String> allVins = Arrays.asList(vinsParam.split(","));

        // compute how many partitions from the query ID
        int partitionCount = parseQueryIdComplexity(queryIdParam);

        Map<String, ExecutionContext> partitions = new HashMap<>();

        // If there's fewer VINs than partitions, some partitions might be empty
        int sliceSize = (int) Math.ceil((double) allVins.size() / partitionCount);

        for (int i = 0; i < partitionCount; i++) {
            int startIndex = i * sliceSize;
            int endIndex = Math.min(startIndex + sliceSize, allVins.size());

            List<String> slice = Collections.emptyList();
            if (startIndex < allVins.size()) {
                slice = allVins.subList(startIndex, endIndex);
            }

            // each partition’s context
            ExecutionContext context = new ExecutionContext();
            context.putString("vinSlice", String.join(",", slice));

            partitions.put("partition-" + i, context);
        }

        return partitions;
    }

    private int parseQueryIdComplexity(String queryIdParam) {
        // we would calculate query complexity here
        int qId = Integer.parseInt(queryIdParam);
        switch (qId) {
            case 1: return 3;
            case 2: return 2;
            default: return 1;
        }
    }
}