package com.mindarray.NMS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;

public class DiscoveryEngine extends AbstractVerticle {
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        var eventBus = vertx.eventBus();
        eventBus.<JsonObject>localConsumer(RUN_DISCOVERY_DISCOVERY_ENGINE, handler -> {
            if(!handler.body().getString(TYPE).equals("snmp")){
                vertx.executeBlocking(blockingHandler ->{
                    Utils.checkAvailability(handler.body()).
                            compose(future ->Utils.checkPort(handler.body())).
                            compose(future -> Utils.spwanProcess(handler.body()))
                            .onComplete( completeHandler->{
                                if(completeHandler.succeeded() && completeHandler.result().getJsonObject(RESULT).getString(STATUS).equals(SUCCESS)){
                                    handler.reply(completeHandler.result().getJsonObject(RESULT));
                                }else{
                                    handler.fail(-1, completeHandler.cause().getMessage());
                                }
                                blockingHandler.complete();
                            });
                });

            }else{
                vertx.executeBlocking(blockingHandler ->{
                    Utils.checkAvailability(handler.body()).
                            compose(future -> Utils.spwanProcess(handler.body()))
                            .onComplete( completeHandler->{
                                if(completeHandler.succeeded() && completeHandler.result().getJsonObject(RESULT).getString(STATUS).equals(SUCCESS)){
                                    handler.reply(completeHandler.result().getJsonObject(RESULT));
                                }else{
                                    handler.fail(-1, completeHandler.cause().getMessage());
                                }
                                blockingHandler.complete();
                            });
                });

            }

           /* var availability = Utils.checkAvailability(handler.body());
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
            });*/
        });
        startPromise.complete();
    }
}
