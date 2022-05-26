package com.mindarray.NMS;

import com.mindarray.api.Credential;
import com.mindarray.api.Discovery;
import com.mindarray.api.Monitor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiRouter extends AbstractVerticle {
    // * bbyby
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRouter.class);

    @Override
    public void start(Promise<Void> startPromise) {
        Router mainRouter = Router.router(vertx);
        Router router = Router.router(vertx);
        mainRouter.mountSubRouter("/api/", router);
        mainRouter.route().handler(BodyHandler.create());
        router.route().handler(BodyHandler.create());
        new Discovery().init(router);
        new Credential().init(router);
        new Monitor().init(router);
        vertx.createHttpServer().requestHandler(mainRouter).listen(5555).onComplete(handler -> {
                    if (handler.succeeded()) {
                        LOGGER.info("Http server started");
                        startPromise.complete();
                    } else {
                        LOGGER.info("unable to start HTTP server :{}", handler.cause().getMessage());
                        startPromise.fail(handler.cause().getMessage());
                    }

                }
        );


    }
}
