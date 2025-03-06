package com.cgoller.batchdemo.dto;

import java.util.List;

public class JobRequest {

    private List<String> vins;
    private int queryId;

    public List<String> getVins() {
        return vins;
    }

    public void setVins(List<String> vins) {
        this.vins = vins;
    }

    public int getQueryId() {
        return queryId;
    }

    public void setQueryId(int queryId) {
        this.queryId = queryId;
    }
}