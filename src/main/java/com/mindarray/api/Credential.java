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
        router.route().setName("delete").method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT+"/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().setName("get").method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT +"/:id/").handler(this::validate).handler(this::getById);
    }
    private void validate(RoutingContext context) {
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        HttpServerResponse response = context.response();
        //trim data
        try {
            if ((!(context.currentRoute().getName().equals("get"))) && (!(context.currentRoute().getName().equals("delete")))) {
                var credentials = context.getBodyAsJson();
                credentials.stream().forEach(value -> {
                    if (credentials.getValue(value.getKey()) instanceof String) {
                        credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                    }
                });
                context.setBody(credentials.toBuffer());
            }
            switch (context.currentRoute().getName()) {
                case "create" -> {
                    if (context.getBodyAsJson().containsKey("protocol")) {
                        if (context.getBodyAsJson().getString("protocol").equals("ssh") || context.getBodyAsJson().getString("protocol").equals("powershell")) {
                            if (!(context.getBodyAsJson().containsKey("username"))) {
                                error.add("Username not provided");
                            } else if (!(context.getBodyAsJson().containsKey("password"))) {
                                error.add("Password not provided");
                            }
                        } else if (context.getBodyAsJson().getString("protocol").equals("snmp")) {
                            if (!(context.getBodyAsJson().containsKey("community"))) {
                                error.add("Community not provided");
                            } else if (!(context.getBodyAsJson().containsKey("version"))) {
                                error.add("Version not provided");
                            }
                        } else {
                            error.add("Wrong protocol selected");
                        }
                    }
                    if(error.isEmpty()){
                        eventBus.<JsonObject>request(CREDENTIAL_DATABASE, context.getBodyAsJson().put(METHOD, CREDENTIAL_DATABASE_CHECK_NAME), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
                            }
                            else {
                                    response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                    response.end(new JsonObject().put("message", handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                                }

                        });

                    }else{
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put("message", error).put(STATUS, FAIL).encodePrettily());
                    }
                }
                case "delete" -> {
                    var id = context.pathParam("id");
                    if ( id != null) {
                        eventBus.<JsonObject>request(CREDENTIAL_DATABASE, new JsonObject().put(METHOD, CREDENTIAL_DATABASE_CHECK_ID).put(CREDENTIAL_ID,id), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
                            } else {
                                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put("message", handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                            }
                        });
                    } else {
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put("message", "id is null").put(STATUS, FAIL).encodePrettily());
                    }
                }
                case "update" -> {
                    if ((!(context.getBodyAsJson().containsKey(CREDENTIAL_ID))) || context.getBodyAsJson().getString(CREDENTIAL_ID) == null) {
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    } else {
                        eventBus.<JsonObject>request(CREDENTIAL_DATABASE, context.getBodyAsJson().put(METHOD, CREDENTIAL_DATABASE_CHECK_MULTIPLE), handler -> {
                            if (handler.succeeded()) {
                                context.next();
                            } else {
                                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put(ERROR, new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage())).encodePrettily());
                            }
                        });
                    }
                }
                case "get"    -> {
                    if (context.pathParam("id") == null) {
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    } else {
                        eventBus.<JsonObject>request(CREDENTIAL_DATABASE, new JsonObject().put(CREDENTIAL_ID, (context.pathParam("id"))).put(METHOD, CREDENTIAL_DATABASE_CHECK_ID), handler -> {
                            if (handler.succeeded() ) {
                                context.next();
                            } else {
                                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put("message", handler.cause().getMessage()).put(STATUS,FAIL).encodePrettily());
                            }
                        });
                    }


                }
            }
        }catch (Exception e){
            response.setStatusCode(400).putHeader("content-type",HEADER_TYPE);
            response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,"Wrong Json Format").put(ERROR,e.getCause().getMessage()).encodePrettily());
        }
    }
    private void delete(RoutingContext context) {
        HttpServerResponse response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE,new JsonObject().put(METHOD,CREDENTIAL_DATABASE_DELETE).put(CREDENTIAL_ID,context.pathParam("id")),handler ->{
            if(handler.succeeded()){
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
            }else{
                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
            }

        });
    }
    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE,context.getBodyAsJson().put(METHOD,CREDENTIAL_DATABASE_UPDATE),handler ->{
            if(handler.succeeded()){
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
            }else{
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
            }
        });
    }
    private void create(RoutingContext context) {
       var eventBus = Bootstrap.vertx.eventBus();
       var response = context.response();
       eventBus.<JsonObject>request(CREDENTIAL_DATABASE,context.getBodyAsJson().put(METHOD,CREDENTIAL_DATABASE_CREATE),handler ->{
           if(handler.succeeded()){
               response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
               response.end(new JsonObject().put(STATUS,SUCCESS).put(MESSAGE,handler.result().body().getString(MESSAGE)).encodePrettily());
           }else{
               response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
               response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
           }
       });

    }
    private void get(RoutingContext context)    {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String msg = "all";
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE,new JsonObject().put(METHOD,CREDENTIAL_DATABASE_GET).put("message",msg),handler ->{
            if(handler.succeeded() && handler.result().body() != null){
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
            }else{
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put("message",handler.cause().getMessage()).put(STATUS,FAIL).encodePrettily());
            }
        });
    }
    private void getById(RoutingContext context){
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String id = context.pathParam("id");
        System.out.println(id);
        eventBus.<JsonObject>request(CREDENTIAL_DATABASE,new JsonObject().put(METHOD,CREDENTIAL_DATABASE_GET_ID).put(CREDENTIAL_ID,id),handler ->{
            if(handler.succeeded() && handler.result().body() != null){
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
            }else{
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put("message",handler.cause().getMessage()).put(STATUS,FAIL).encodePrettily());
            }
        });
    }
}
