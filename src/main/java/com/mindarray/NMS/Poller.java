package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static com.mindarray.NMS.Constant.*;

public class Poller extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Poller.class);
    HashMap<String, String> pingCheck = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise){
        var eventBus = vertx.eventBus();
        eventBus.<JsonObject>localConsumer(SCHEDULER_POLLING, handler -> {
            try {
                if (handler.body() != null) {
                    if (handler.body().getString(METRIC_GROUP).equals(PING)) {
                        Bootstrap.getVertx().executeBlocking(blockingHandler -> {
                            if (Utils.checkAvailability(handler.body()).getString(STATUS).equals(SUCCESS)) {
                                pingCheck.put(handler.body().getString(IP), "up");
                            } else {
                                pingCheck.put(handler.body().getString(IP), "down");
                            }
                            blockingHandler.complete();
                        });
                    } else {
                        if (!pingCheck.containsKey(handler.body().getString(IP)) || pingCheck.get(handler.body().getString(IP)).equals("up")) {
                            Bootstrap.getVertx().executeBlocking(blockingHandler -> {
                                var result = Utils.spawnProcess(handler.body());
                                if (result.getString(STATUS).equals(SUCCESS)) {
                                    var data = new JsonObject().put(TABLE, POLLING_TABLE).put(MONITOR_ID, result.getString(MONITOR_ID))
                                            .put(TIMESTAMP, handler.body().getString(TIMESTAMP)).put(METRIC_GROUP, handler.body().getString(METRIC_GROUP))
                                            .put(OBJECT, result.getJsonObject(RESULT));
                                    eventBus.send(POLLER_DATABASE, data);
                                    LOGGER.info("polling :{}", result.getJsonObject(RESULT));
                                } else {
                                    LOGGER.error("error occurred :{}", result.getValue(ERROR));
                                }
                                blockingHandler.complete();
                            });
                        } else {
                            LOGGER.info("ip is down :{}", handler.body().getString(IP));
                        }
                    }
                } else {
                    LOGGER.error("error occurred :{}", "scheduling polling handler body is null");
                }
            } catch (Exception exception) {
               LOGGER.error("exception occurred :",exception);
            }
        });
        startPromise.complete();
    }
}
