package com.mindarray.api;

import com.mindarray.NMS.Bootstrap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;

public class Discovery {
    public void init(Router router) {
        router.route().setName("create").method(HttpMethod.POST).path(DISCOVERY_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().setName("update").method(HttpMethod.PUT).path(DISCOVERY_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().setName("delete").method(HttpMethod.DELETE).path(DISCOVERY_ENDPOINT+ "/:name/").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(DISCOVERY_ENDPOINT).handler(this::get);
        router.route().setName("get").method(HttpMethod.GET).path(DISCOVERY_ENDPOINT+"/:id/").handler(this::validate).handler(this::getById);
    }



    private void validate(RoutingContext context) {
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        if((!(context.currentRoute().getName().equals("get"))) && (!(context.currentRoute().getName().equals("delete"))) ){
            var credentials = context.getBodyAsJson();
            credentials.stream().forEach(value->{
                if(credentials.getValue(value.getKey()) instanceof String) {
                    credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                } });
            context.setBody(credentials.toBuffer());
        }

        switch (context.currentRoute().getName()){
            case "create" -> {
                if(!(context.getBodyAsJson().containsKey(DISCOVERY_NAME)) || context.getBodyAsJson().getString(DISCOVERY_NAME)==null){
                    error.add("Discovery name is null");
                }
                else if(!(context.getBodyAsJson().containsKey(DISCOVERY_IP)) || context.getBodyAsJson().getString(DISCOVERY_IP)==null){
                    error.add("IP is null");
                }
                else if(!(context.getBodyAsJson().containsKey(DISCOVERY_TYPE)) || context.getBodyAsJson().getString(DISCOVERY_TYPE)==null){
                    error.add("No type defined for discovery");
                }
                else if(!(context.getBodyAsJson().containsKey(DISCOVERY_PORT)) || context.getBodyAsJson().getInteger(DISCOVERY_PORT)==null){
                    error.add("Port not defined for discovery");
                }
                else if(!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE)==null){
                    error.add("Credential Profile not defined for discovery");
                }

                //Unique Discovery Name
                eventBus.<JsonObject>request(DISCOVERY_DATABASE_CHECK_NAME,context.getBodyAsJson(), handler ->{
                    if(handler.succeeded() || handler.result().body() != null){
                        if(handler.result().body().getString(STATUS).equals(FAIL)){
                            error.add(handler.result().body().getString(ERROR));
                        }
                        if(error.isEmpty()){
                            context.next();
                        }else{
                            HttpServerResponse response = context.response();
                            response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                            response.end(new JsonObject().put("message",error).put(STATUS,FAIL).encodePrettily());
                        }
                    }else{
                        HttpServerResponse response = context.response();
                        response.setStatusCode(500).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put("message","Internal Server Error Occurred").encodePrettily());

                    }
                });


            }
            case "delete" ->{
                if(context.pathParam("name")==null){
                    HttpServerResponse response = context.response();
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put("message","Unable to find discovery name").put(STATUS,FAIL).encodePrettily());
                }else{
                    context.next();
                }
            }
            case "get" ->{
                HttpServerResponse response = context.response();
                if(context.pathParam("id")==null){
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE,"id is null").put(STATUS,FAIL).encodePrettily());
                }else{
                    eventBus.<JsonObject>request(DISCOVERY_DATABASE_CHECK_ID,context.pathParam("id"),handler ->{
                        if(handler.succeeded() || handler.result().body() != null){
                            if(handler.result().body().getString(STATUS).equals(FAIL)){
                                error.add(handler.result().body().getString(ERROR));
                            }
                            if(error.isEmpty()){
                                context.next();
                            }else{
                                response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE,error).put(STATUS,FAIL).encodePrettily());
                            }
                        }else{

                            response.setStatusCode(500).putHeader("content-type", HEADER_TYPE);
                            response.end(new JsonObject().put("message","Internal Server Error Occurred").encodePrettily());

                        }
                    });
                }
            }
            case "update" ->{
                context.next();
            }
        }

    }
    private void create(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE_CREATE,context.getBodyAsJson(),handler ->{
          if(handler.result().body().getString(STATUS).equals(SUCCESS)){
              response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
              response.end(new JsonObject().put(STATUS,SUCCESS).put(MESSAGE,handler.result().body().getString(MESSAGE)).encodePrettily());
          }else{
              response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
              response.end(new JsonObject().put(STATUS,FAIL).put(ERROR,handler.result().body().getString(ERROR)).encodePrettily());
          }

        });
    }
    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE_UPDATE,context.getBodyAsJson(),handler ->{
            if(handler.succeeded()){
                if(handler.result().body().getString(STATUS).equals(SUCCESS)){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(ERROR,handler.result().body().getString(ERROR)).encodePrettily());
                }
            }
        });
    }
    private void delete(RoutingContext context) {
        HttpServerResponse response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE_DELETE,new JsonObject().put(DISCOVERY_NAME,context.pathParam("name")),handler ->{
            if(handler.succeeded()){
                if(handler.result().body().getString(STATUS).equals(SUCCESS)){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.result().body().getValue(ERROR)).encodePrettily());
                }

            }

        });
    }
    private void get(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        var msg ="all";
        eventBus.<JsonObject>request(DISCOVERY_DATABASE_GET,msg,handler ->{
            if(handler.succeeded()){
                if(handler.result().body().getString(STATUS).equals(SUCCESS)){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).put("result",handler.result().body().getJsonArray("result")).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.result().body().getValue(ERROR)).encodePrettily());
                }
            }
        });
    }
    private void getById(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String msg = context.pathParam("id");

        eventBus.<JsonObject>request(DISCOVERY_DATABASE_GET_ID,msg,handler ->{
            if(handler.succeeded() && handler.result().body() != null){
                if(handler.result().body().getString(STATUS).equals(SUCCESS)){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                }else{
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                }
            }else{
                response.setStatusCode(500).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put("message","Internal Server Error Occurred").encodePrettily());
            }
        });
    }

}
