package com.mindarray;

import com.mindarray.NMS.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bootstrap {
    public static final Vertx vertx = Vertx.vertx();

    public static Vertx getVertx() {
        return vertx;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            start(ApiRouter.class.getName()).
                    compose(handler -> start(DatabaseEngine.class.getName())).
                    compose(handler -> start(DiscoveryEngine.class.getName())).
                    compose(handler -> start(Poller.class.getName())).
                    compose(handler ->start(MetricScheduler.class.getName())).
                    onComplete(handler -> {
                                if (handler.succeeded()) {
                                    LOGGER.info("all verticles deployed");
                                } else {
                                    LOGGER.error("error occurred :{}",handler.cause().getMessage());
                                }
                            });
        }catch (Exception exception){
            LOGGER.error("error occurred :{}",exception.getMessage());
        }
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
