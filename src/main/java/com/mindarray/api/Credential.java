package com.mindarray.api;

import com.mindarray.NMS.Bootstrap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static com.mindarray.NMS.Constant.*;

public class Credential {
    public void init(Router router) {
        router.route().setName("create").method(HttpMethod.POST).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().setName("update").method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().setName("delete").method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::delete);
    }

    private void validate(RoutingContext context) {

        var eventBus = Bootstrap.getVertx().eventBus();
        context.getBodyAsJson().stream().forEach(value->{
            if(context.getBodyAsJson().getValue(value.getKey()) instanceof String) {
                context.getBodyAsJson().put(value.getKey(), context.getBodyAsJson().getString(value.getKey()).trim());
            } });
        System.out.println("In validate");
        switch (context.currentRoute().getName()){
            case "create" -> {
                //Unique Credential Name
                eventBus.<JsonObject>request(DISCOVERY_DATABASE_CHECK_DISCOVERY,context.getBodyAsJson(), handler ->{
                });
            }
        }
        context.next();
    }
    private void delete(RoutingContext context) {
    }

    private void update(RoutingContext context) {
    }

    private void create(RoutingContext context) {
    }


}
