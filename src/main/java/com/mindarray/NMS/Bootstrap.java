package com.mindarray.NMS;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bootstrap {
    public static Vertx vertx = Vertx.vertx();
    public static Vertx getVertx() {
        return vertx;
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);
    public static void main(String[] args) {
        start(ApiRouter.class.getName()).
                compose( handler -> start(DatabaseEngine.class.getName())).
                compose(handler -> start(DiscoveryEngine.class.getName())).
                compose(handler -> start(Poller.class.getName())).
                onComplete(
                        handler->{
                            if(handler.succeeded()){
                                LOGGER.info("All verticles Deployed");
                            }else{
                                LOGGER.error(handler.cause().getMessage());
                            }
                        }
                );

    }
    public static Future<Void> start(String verticle) {
        Promise<Void> promise = Promise.promise();
       vertx.deployVerticle(verticle, handler -> {
            if (handler.succeeded()) {
                promise.complete();
            } else {
                promise.fail(handler.cause().getMessage());
            }
        });
        return promise.future();
    }
}
