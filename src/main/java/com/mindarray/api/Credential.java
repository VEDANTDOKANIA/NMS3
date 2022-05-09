package com.mindarray.api;

import com.mindarray.NMS.Bootstrap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;

public class Credential {
    private static final Logger LOGGER = LoggerFactory.getLogger(Credential.class);
    public void init(Router router) {
        router.route().setName("create").method(HttpMethod.POST).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().setName("update").method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().setName("delete").method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().setName("get").method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT +"/:id/").handler(this::validate).handler(this::getById);
    }




    private void validate(RoutingContext context) {
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        //trim data
        if(!(context.currentRoute().getName().equals("get"))){
            var credentials = context.getBodyAsJson();
            credentials.stream().forEach(value->{
                if(credentials.getValue(value.getKey()) instanceof String) {
                    credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                } });
            context.setBody(credentials.toBuffer());
        }


        switch (context.currentRoute().getName()){
            case "create" -> {
                //Check for protocol
                if(context.getBodyAsJson().containsKey("protocol")){
                    if(context.getBodyAsJson().getString("protocol").equals("ssh") || context.getBodyAsJson().getString("protocol").equals("powershell")){
                        if(!(context.getBodyAsJson().containsKey("username"))){
                            error.add("Username not provided");
                        } else if (!(context.getBodyAsJson().containsKey("password"))) {
                            error.add("Password not provided");
                        }
                    }else if(context.getBodyAsJson().getString("protocol").equals("powershell")){
                        if(!(context.getBodyAsJson().containsKey("community"))){
                            error.add("Community not provided");
                        } else if (!(context.getBodyAsJson().containsKey("version"))) {
                            error.add("Version not provided");
                        }
                    }else{
                        error.add("Wrong protocol selected");
                    }
                }
                //Unique Credential Name
                eventBus.<JsonObject>request(CREDENTIAL_DATABASE_CHECK_NAME,context.getBodyAsJson(), handler ->{
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
                if(context.getBodyAsJson().containsKey(CREDENTIAL_NAME) && context.getBodyAsJson().getString(CREDENTIAL_NAME)!= null){
                }else{                   context.next();

                    HttpServerResponse response = context.response();
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put("message","Unable to find credential name").put(STATUS,FAIL).encodePrettily());
                }
            }
            case "update" ->{
                var response = context.response();
                if((!(context.getBodyAsJson().containsKey(CREDENTIAL_ID))) || context.getBodyAsJson().getString(CREDENTIAL_ID).equals(null) ){
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE,"id is null").put(STATUS,FAIL).encodePrettily());
                }else{
                    eventBus.<JsonObject>request(CREDENTIAL_DATABASE_CHECK_MULTIPLE,context.getBodyAsJson(),handler->{
                        if(handler.result().body().getString(STATUS).equals(SUCCESS)){
                            context.next();
                        }else{
                            response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                            response.end(new JsonObject().put(ERROR,handler.result().body().getValue(ERROR)).put(STATUS,FAIL).encodePrettily());

                        }
                    });
                }
            }
            case "get" ->{
                HttpServerResponse response = context.response();
                if(context.pathParam("id")==null){
                    response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE,"id is null").put(STATUS,FAIL).encodePrettily());
                }else{
                    eventBus.<JsonObject>request(CREDENTIAL_DATABASE_CHECK_ID,context.pathParam("id"),handler ->{
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
        }

    }
    private void delete(RoutingContext context) {
        HttpServerResponse response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE_DELETE,context.getBodyAsJson(),handler ->{
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
    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE_UPDATE,context.getBodyAsJson(),handler ->{
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
    private void create(RoutingContext context) {
       var eventBus = Bootstrap.vertx.eventBus();
       var response = context.response();

       eventBus.<JsonObject>request(CREDENTIAL_DATABASE_CREATE,context.getBodyAsJson(),handler ->{
           if(handler.succeeded()){
               if(handler.result().body().getString(STATUS).equals(FAIL)){
                   response.setStatusCode(401).putHeader("content-type", HEADER_TYPE);
                   response.end(new JsonObject().put("message",handler.result().body().getValue(ERROR)).put(STATUS,FAIL).encodePrettily());
               }else{
                   response.setStatusCode(200).putHeader("content-type",HEADER_TYPE);
                   response.end( new JsonObject().put("message",handler.result().body().getString("message")).put(STATUS,SUCCESS).encodePrettily());
               }
           }else{
               response.setStatusCode(500).putHeader("content-type", HEADER_TYPE);
               response.end(new JsonObject().put("message","Internal Server Error Occurred").encodePrettily());
           }
       });

    }
    private void get(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String msg = "all";
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE_GET,msg,handler ->{
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
    private void getById(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String msg = context.pathParam("id");

        eventBus.<JsonObject>request(CREDENTIAL_DATABASE_GET_ID,msg,handler ->{
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
