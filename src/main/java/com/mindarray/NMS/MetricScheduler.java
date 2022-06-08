package com.mindarray.NMS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static com.mindarray.NMS.Constant.*;

public class MetricScheduler extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricScheduler.class);
    HashMap<Integer, Integer> metric = new HashMap<>();
    HashMap<Integer, Integer> scheduledMetric = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        var eventBus = vertx.eventBus();
        var initialQuery = "select metric_id,time from metric;";
        var contextQuery = "select username,password,community,version,type,metric_group,ip,port,metric.monitor_id from metric,monitor,credential where metric.credential_profile= credential.credential_id and metric.monitor_id=monitor.monitor_id and metric_id =number ;";
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(QUERY, initialQuery).put(METHOD, GET_QUERY), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                var result = handler.result().body().getJsonArray(RESULT);
                for (int index = 0; index < result.size(); index++) {
                    var object = result.getJsonObject(index);
                    metric.put(object.getInteger("metric.id"), object.getInteger("time"));
                    scheduledMetric.put(object.getInteger("metric.id"), object.getInteger("time"));
                }
            } else {
                LOGGER.info("message :{}", "metric table is empty");
            }
        });
        eventBus.<JsonObject>localConsumer(PROVISION_SCHEDULER, handler -> {
            if (handler.body() != null && handler.body().containsKey(RESULT) && handler.body().getJsonArray(RESULT) !=null) {
                var result = handler.body().getJsonArray(RESULT);
                for (int index = 0; index < result.size(); index++) {
                    var object = result.getJsonObject(index);
                    metric.put(object.getInteger("metric.id"), object.getInteger("time"));
                    scheduledMetric.put(object.getInteger("metric.id"), object.getInteger("time"));
                }
            } else {
                LOGGER.error("error occurred :{}", "handler body is null");
            }
        });
        eventBus.<JsonObject>localConsumer(MONITOR_SCHEDULER_DELETE ,handler ->{
            if((handler.body() != null && handler.body().containsKey(RESULT) && handler.body().getJsonArray(RESULT) !=null)){
                var result = handler.body().getJsonArray(RESULT);
                for (int index = 0; index < result.size(); index++) {
                    var object = result.getJsonObject(index);
                    metric.remove(object.getInteger("metric.id"));
                    scheduledMetric.remove(object.getInteger("metric.id"));
                }
            } else {
                LOGGER.error("error occurred :{}", "handler body is null");
            }
        });
        eventBus.<JsonObject>localConsumer(METRIC_SCHEDULER_UPDATE,handler ->{
            if(handler.body() != null){
                metric.put(handler.body().getInteger(METRIC_ID),handler.body().getInteger(TIME));
                scheduledMetric.put(handler.body().getInteger(METRIC_ID),handler.body().getInteger(TIME));
            } else {
                LOGGER.error("error occurred :{}", "handler body is null");
            }
        });
        vertx.setPeriodic(10000, handler -> {
            if (scheduledMetric.size() > 0) {
                scheduledMetric.forEach((key, value) -> {
                    var timeLeft = value - 10000;
                    if (timeLeft <= 0) {
                        var originalTime = metric.get(key);
                        scheduledMetric.put(key, originalTime);
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, contextQuery.replace("number", key.toString())), message -> {
                            if (message.succeeded() && message.result().body() != null) {
                                eventBus.send(SCHEDULER_POLLING, message.result().body().getJsonArray(RESULT).getJsonObject(0).put(CATEGORY, "polling").put(TIMESTAMP, System.currentTimeMillis()));
                            } else {
                                LOGGER.error("error occurred :{}", message.cause().getMessage());
                            }
                        });
                    } else {
                        scheduledMetric.put(key, timeLeft);
                    }
                });
            }
        });
        startPromise.complete();
    }
}
