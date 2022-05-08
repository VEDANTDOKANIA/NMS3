package com.mindarray.NMS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class DiscoveryEngine extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        startPromise.complete();
    }
}
