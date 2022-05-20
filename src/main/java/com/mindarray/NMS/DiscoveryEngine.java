package com.mindarray.NMS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import static com.mindarray.NMS.Constant.*;

public class DiscoveryEngine extends AbstractVerticle {
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        var eventBus = vertx.eventBus();
        eventBus.<JsonObject>localConsumer(RUN_DISCOVERY_DISCOVERY_ENGINE, handler -> {
            var availability = Utils.checkAvailability(handler.body());
            var discovery = Utils.spwanProcess(handler.body());
            CompositeFuture.all(availability, discovery).onComplete(future -> {
                if (future.succeeded()) {
                    if (discovery.result().getJsonObject(RESULT).getString(STATUS).equals(SUCCESS)) {
                        handler.reply(discovery.result().getJsonObject(RESULT));
                    } else {
                        handler.fail(-1, discovery.result().getJsonObject(RESULT).getString(ERROR));
                    }
                } else {
                    handler.fail(-1, future.cause().getMessage());
                }
            });
        });
        startPromise.complete();
    }
}
