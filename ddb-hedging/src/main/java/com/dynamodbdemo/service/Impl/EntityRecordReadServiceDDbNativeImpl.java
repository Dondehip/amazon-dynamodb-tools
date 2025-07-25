package com.dynamodbdemo.service.Impl;

import com.dynamodbdemo.dao.EntityRecordDDbNativeDAO;
import com.dynamodbdemo.model.DDBMetaDataAccessor;
import com.dynamodbdemo.model.DDBResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service("EntityRecordReadServiceDDbNativeImpl")
public class EntityRecordReadServiceDDbNativeImpl extends AbstractEntityRecordReadServiceImpl {

    private final EntityRecordDDbNativeDAO entityRecordDDbNativeDAO;

    public EntityRecordReadServiceDDbNativeImpl(EntityRecordDDbNativeDAO entityRecordDDbNativeDAO) {
        this.entityRecordDDbNativeDAO = entityRecordDDbNativeDAO;
    }

    @Override
    public CompletableFuture<List<DDBMetaDataAccessor>> getEntityRecordsAsync(String recordId, String entityNumber, float delayInMillis) {
        return null;
    }

    @Override
    public List<DDBMetaDataAccessor> getEntityRecords(String recordId, String entityNumber, float delayInMillis) throws InterruptedException {

        long startTime = System.nanoTime();


        DDBResponse fetchByClientIDAndAppNumResponse;
        try {
            fetchByClientIDAndAppNumResponse = entityRecordDDbNativeDAO.fetchByRecordIDAndEntityNumberAsync(recordId, entityNumber).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }


        long endTime = System.nanoTime();
        fetchByClientIDAndAppNumResponse.setActualLatency(endTime - startTime);

        List<DDBMetaDataAccessor> metaDataAccessors = new ArrayList<>();
        metaDataAccessors.add(fetchByClientIDAndAppNumResponse);

        return metaDataAccessors;
    }

}
