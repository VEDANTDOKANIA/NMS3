package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mindarray.NMS.Constant.*;

public class DiscoveryEngine extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine.class);
    @Override
    public void start(Promise<Void> startPromise){
        Bootstrap.getVertx().eventBus().<JsonObject>localConsumer(RUN_DISCOVERY_DISCOVERY_ENGINE, handler -> {
              vertx.executeBlocking(blockingHandler -> {
                 if(handler.body() != null){
                     var availability = Utils.checkAvailability(handler.body());
                     if (availability != null && availability.getString(STATUS).equals(SUCCESS)) {
                         var portCheck = Utils.checkPort(handler.body());
                         if (handler.body().getString(STATUS).equals(SUCCESS)) {
                             var process = Utils.spawnProcess(handler.body().put("category", "discovery"));
                             if (process.getString(STATUS).equals(SUCCESS)) {
                                 handler.reply(process.getJsonObject(RESULT));
                             } else {
                                 handler.fail(-1, process.getString(ERROR));
                             }
                         } else {
                             handler.fail(-1, portCheck.getString(ERROR));
                         }
                     } else {
                         handler.fail(-1, availability.getString(ERROR));
                     }
                 } else{
                     handler.fail(-1,"handler body is null");
                 }
              });
        });
        startPromise.complete();
    }
}
