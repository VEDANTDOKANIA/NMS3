package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.mindarray.NMS.Constant.*;

public class Poller extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Poller.class);
    HashMap<String,String> pingData = new HashMap<>();
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        var eventBus = vertx.eventBus();
        WorkerExecutor executor = Bootstrap.getVertx().createSharedWorkerExecutor("poller-pool", 10, 60000, TimeUnit.MILLISECONDS);


        eventBus.<JsonObject>localConsumer(SCHEDULER_POLLING,handler->{
            if(handler.body()!=null){
                if (handler.body().getString("metric.group").equals("ping")) {
                    executor.executeBlocking( blockingHandler ->{
                      if( Utils.checkAvailability(handler.body()).getString(STATUS).equals(SUCCESS)){
                          pingData.put(handler.body().getString(IP),"up");
                      }else{
                          pingData.put(handler.body().getString(IP),"down");
                           }
                            blockingHandler.complete();
                        });
                } else {
                    if (!pingData.containsKey(handler.body().getString(IP)) || pingData.get(handler.body().getString(IP)).equals("up")) {
                        executor.executeBlocking( blockingHandler ->{
                            var result =Utils.spawnProcess(handler.body());
                           if( result.getString(STATUS).equals(SUCCESS)){
                               var data = new JsonObject().put(TABLE,"polling").put(MONITOR_ID,result.getString(MONITOR_ID))
                                               .put("id",handler.body().getString("id")).put(METRIC_GROUP,handler.body().getString(METRIC_GROUP))
                                               .put("object",result.getJsonObject(RESULT));
                               eventBus.send(POLLER_DATABASE,data);
                               LOGGER.info("Polling :{}", result.getJsonObject(RESULT));
                           }else{
                               LOGGER.error("error occurred :{}", result.getValue(ERROR));
                           }
                              blockingHandler.complete();
                            });
                    } else {
                        LOGGER.info("ip is down :{}", handler.body().getString(IP));
                    }
            }
            }else{
                LOGGER.error("error occurred :{}","scheduling polling handler body is null");
            }
        });
        startPromise.complete();
    }
}
