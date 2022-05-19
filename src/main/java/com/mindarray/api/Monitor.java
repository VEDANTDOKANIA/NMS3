package com.mindarray.api;

import com.mindarray.NMS.Bootstrap;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;
import static com.mindarray.NMS.Constant.ERROR;

public class Monitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Monitor.class);
    public void init(Router router) {
        router.route().method(HttpMethod.POST).path(MONITOR_ENDPOINT+"/Provision").handler(this::validate).handler(this::create);
    }

    private void validate(RoutingContext context) {
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        HttpServerResponse response = context.response();
        //trim data
        try {
            if ((!(context.request().method().toString().equals("GET"))) && (!(context.request().method().toString().equals("DELETE")))) {
                var credentials = context.getBodyAsJson();
                credentials.stream().forEach(value -> {
                    if (credentials.getValue(value.getKey()) instanceof String) {
                        credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                    }
                });
                context.setBody(credentials.toBuffer());
            }

            switch (context.request().method().toString()) {
                case "POST" -> {
                    if (!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE) == null){
                        error.add("Credential profile is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(IP)) || context.getBodyAsJson().getString(IP) == null){
                        error.add("IP is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(PORT)) || context.getBodyAsJson().getString(PORT) == null){
                        error.add("port is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(TYPE)) || context.getBodyAsJson().getString(TYPE) == null){
                        error.add("Type is null");
                    }
                     if(error.isEmpty()){
                         context.next();
                     }else{
                         response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                         response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, error).encodePrettily());
                         LOGGER.error("Error occurred {}",error);

                     }

                }
              
                default -> {
                    System.out.println(context.request().method().toString());
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error(exception.getCause().getMessage());
        }
    }

    private void create(RoutingContext context) {
        var errors = new ArrayList<String>();

       var eventBus = Bootstrap.getVertx().eventBus();
       var response = context.response();
       var query = "select discovery_id from discovery where JSON_SEARCH(discovery_result,\"all\",\"success\") and discovery.credential_profile=\""+context.getBodyAsJson().getString(CREDENTIAL_PROFILE)+"\" and "
               +" discovery.ip= \"" + context.getBodyAsJson().getString(IP) +"\" and "+ "discovery.port="+context.getBodyAsJson().getInteger(PORT)+
               " and discovery.type =\"" + context.getBodyAsJson().getString(TYPE)+"\" ;";
       eventBus.<JsonObject>request(PROVISION,new JsonObject().put(METHOD,"doProvision").put("query",query).mergeIn(context.getBodyAsJson()),handler ->{
           if(handler.succeeded() && handler.result().body()!=null){
               response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
               response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, handler.result().body().getString(MESSAGE)).encodePrettily());
           }else{
               response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
               response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
           }
       });
    }
}
