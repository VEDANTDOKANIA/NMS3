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
    HashMap<Integer, Integer> pollingData = new HashMap<>();
    HashMap<Integer, Integer> poller = new HashMap<>();
    @Override
    public void start(Promise<Void> startPromise) {
        var eventBus = vertx.eventBus();
        var initialQuery = "select metric_id,time from metric;";
        var query = "select username,password,community,version,type,metric_group,ip,port,metric.monitor_id from metric,monitor,credential where metric.credential_profile= credential.credential_id and metric.monitor_id=monitor.monitor_id and metric_id =number ;" ;
        eventBus.<JsonObject>request(DATABASE,new JsonObject().put(QUERY,initialQuery).put(METHOD,GET_QUERY), handler ->{
           if(handler.succeeded() && handler.result().body()!=null){
               var result = handler.result().body().getJsonArray(RESULT);
               for(int index =0 ; index < result.size() ;index++){
                   var object = result.getJsonObject(index);
                   pollingData.put(object.getInteger("metric.id"),object.getInteger("time"));
                   poller.put(object.getInteger("metric.id"),object.getInteger("time"));
               }
           }else{
               LOGGER.error("error occurred :{}",handler.cause().getMessage());
           }
        });
        eventBus.<JsonObject>localConsumer(PROVISION_SCHEDULER,handler ->{
            if( handler.body()!=null){
                var result = handler.body().getJsonArray(RESULT);
                for(int index =0 ; index < result.size() ;index++){
                    var object = result.getJsonObject(index);
                    pollingData.put(object.getInteger("metric.id"),object.getInteger("time"));
                    poller.put(object.getInteger("metric.id"),object.getInteger("time"));
                }
            }else{
                LOGGER.error("error occurred :{}","handler body is null");
            }
        });

        vertx.setPeriodic(10000, handler -> {
            if (poller.size() > 0) {
                var timestamp = System.currentTimeMillis();
                poller.forEach((key, value) -> {
                    var timeLeft = value - 10000;
                    if (timeLeft <= 0) {
                        var originalTime = pollingData.get(key);
                        poller.put(key, originalTime);
                        eventBus.<JsonObject>request(DATABASE,new JsonObject().put(METHOD,GET_QUERY).put(QUERY,query.replace("number",key.toString())),result ->{
                            if(result.succeeded() && result.result().body() !=null){
                                eventBus.send(SCHEDULER_POLLING,result.result().body().getJsonArray(RESULT).getJsonObject(0).put("category","polling").put("id",timestamp));
                            }else{
                                LOGGER.error("error occurred :{}",result.cause().getMessage());
                            }
                        });
                    } else {
                        poller.put(key, timeLeft);
                    }
                });
            }
        });
        startPromise.complete();
    }
}
