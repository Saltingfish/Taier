package com.dtstack.engine.master.impl;

import com.dtstack.engine.api.annotation.Forbidden;
import com.dtstack.engine.dao.ScheduleFillDataJobDao;
import com.dtstack.engine.api.domain.ScheduleFillDataJob;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * company: www.dtstack.com
 * author: toutian
 * create: 2019/10/22
 */
@Service
public class ScheduleFillDataJobService implements com.dtstack.engine.api.service.ScheduleFillDataJobService {

    @Autowired
    private ScheduleFillDataJobDao scheduleFillDataJobDao;

    @Forbidden
    public boolean checkExistsName(String jobName, long projectId) {
        ScheduleFillDataJob scheduleFillDataJob = scheduleFillDataJobDao.getByJobName(jobName, projectId);
        return scheduleFillDataJob != null;
    }

    @Forbidden
    public List<ScheduleFillDataJob> getFillJobList(List<String> fillJobName, long projectId){
        if(CollectionUtils.isEmpty(fillJobName)){
            return Lists.newArrayList();
        }

        return scheduleFillDataJobDao.listFillJob(fillJobName, projectId);
    }

    @Transactional
    @Forbidden
    public ScheduleFillDataJob saveData(String jobName, long tenantId, long projectId, String runDay,
                                        String fromDay, String toDay, long userId, Integer appType, Long dtuicTenantId) {

        Timestamp currTimeStamp = Timestamp.valueOf(LocalDateTime.now());

        ScheduleFillDataJob fillDataJob = new ScheduleFillDataJob();
        fillDataJob.setJobName(jobName);
        fillDataJob.setFromDay(fromDay);
        fillDataJob.setToDay(toDay);
        fillDataJob.setRunDay(runDay);
        fillDataJob.setTenantId(tenantId);
        fillDataJob.setProjectId(projectId);
        fillDataJob.setCreateUserId(userId);
        fillDataJob.setGmtModified(currTimeStamp);
        fillDataJob.setGmtCreate(currTimeStamp);
        fillDataJob.setAppType(appType);
        fillDataJob.setDtuicTenantId(dtuicTenantId);
        scheduleFillDataJobDao.insert(fillDataJob);
        return fillDataJob;
    }

}