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
    public void start(Promise<Void> startPromise) {
        Bootstrap.getVertx().eventBus().<JsonObject>localConsumer(RUN_DISCOVERY_DISCOVERY_ENGINE, handler -> {
            try {
                vertx.<JsonObject>executeBlocking(blockingHandler -> {
                    if (handler.body() != null) {
                        var availability = Utils.checkAvailability(handler.body());
                        if (availability != null && availability.getString(STATUS).equals(SUCCESS)) {
                            var portCheck = Utils.checkPort(handler.body());
                            if (portCheck.getString(STATUS).equals(SUCCESS)) {
                                var process = Utils.spawnProcess(handler.body().put(CATEGORY, "discovery"));
                                if (process.getString(STATUS).equals(SUCCESS)) {
                                    blockingHandler.complete(process.getJsonObject(RESULT));
                                } else {
                                    blockingHandler.fail(process.getString(ERROR));
                                    LOGGER.error("error occurred :{}", process.getString(ERROR));
                                }
                            } else {
                                blockingHandler.fail(portCheck.getString(ERROR));
                                LOGGER.error("error occurred :{}", portCheck.getString(ERROR));
                            }
                        } else {
                            if (availability != null && availability.containsKey(ERROR) && !availability.getString(ERROR).isEmpty()) {
                                blockingHandler.fail(availability.getString(ERROR));
                                LOGGER.error("error occurred :{}", availability.getString(ERROR));
                            } else {
                                blockingHandler.fail("availability is null");
                                LOGGER.error("error occurred :{}", "availability is null");
                            }
                        }
                    } else {
                        blockingHandler.fail("handler body is null");
                    }
                }).onComplete(completeHandler -> {
                    if (completeHandler.succeeded()) {
                        handler.reply(completeHandler.result());
                    } else {
                        handler.fail(-1, completeHandler.cause().getMessage());
                    }
                });
            } catch (Exception exception) {
                handler.fail(-1, exception.getMessage());
            }
        });
        startPromise.complete();
    }
}
