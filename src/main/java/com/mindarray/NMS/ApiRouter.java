package com.mindarray.NMS;

import com.mindarray.api.Credential;
import com.mindarray.api.Discovery;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mindarray.NMS.Constant.*;

public class ApiRouter extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRouter.class);
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Router mainRouter = Router.router(vertx);
        Router router = Router.router(vertx);
        mainRouter.mountSubRouter("/api/",router);
        mainRouter.route().handler(BodyHandler.create());
        router.route().handler(BodyHandler.create());
        var discovery = new Discovery();
        discovery.init(router);
        var credential = new Credential();
        credential.init(router);
        vertx.createHttpServer().requestHandler(mainRouter).listen(5555).onComplete( handler ->{
            if(handler.succeeded()){
                LOGGER.info("HTTP server started");
                startPromise.complete();
            }else{
                LOGGER.info("Unable to start HTTP server"+ handler.cause().getMessage());
                startPromise.fail(handler.cause().getMessage());
            }
                }
        );




    }
}
