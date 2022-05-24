package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mindarray.NMS.Constant.*;

public class Poller extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricScheduler.class);
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        HashMap<String,String> pingData = new HashMap<>();
        var eventBus = vertx.eventBus();
       ConcurrentLinkedQueue<JsonObject>  queue = new ConcurrentLinkedQueue<>();
        eventBus.<JsonObject>localConsumer(SCHEDULER_POLLING,handler->{
            queue.add(handler.body());
        });

        new Thread(() -> {
            AtomicInteger poolSize = new AtomicInteger(10);
            WorkerExecutor executor = Bootstrap.getVertx().createSharedWorkerExecutor("poller-pool", 10, 50000, TimeUnit.MILLISECONDS);
            while (true){
                for(int i =0 ; i< poolSize.get() ;i++) {
                    if (!queue.isEmpty()) {
                        var polling = queue.poll();
                        poolSize.getAndDecrement();
                        if (polling.getString("metric.group").equals("ping")) {
                            executor.executeBlocking( handler ->{
                                Utils.checkAvailability(polling).onComplete(result -> {
                                    if (result.succeeded()) {
                                        pingData.put(polling.getString(IP), "up");
                                        poolSize.getAndIncrement();
                                    } else {
                                        pingData.put(polling.getString(IP), "down");
                                        poolSize.getAndIncrement();
                                    }
                                });
                            });
                        } else {
                            if (!pingData.containsKey(polling.getString(IP)) || pingData.get(polling.getString(IP)).equals("up")) {
                                executor.executeBlocking( handler ->{
                                    Utils.spwanProcess(polling).onComplete(result -> {
                                        if (result.succeeded()) {
                                            LOGGER.info("Polling :{}", result.result());

                                            poolSize.getAndIncrement();
                                            handler.complete();
                                        } else {
                                            LOGGER.info("Polling :{}", result.result());
                                            poolSize.getAndIncrement();
                                            handler.fail(result.result().encode());
                                        }
                                    });
                                });

                            } else {
                                LOGGER.info("ip is down :{}", polling.getString(IP));
                            }
                        }
                    }
                }
            }
        }).start();
        startPromise.complete();
    }
}
