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
        router.route().setName("delete").method(HttpMethod.DELETE).path(DISCOVERY_ENDPOINT+ "/:id/").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(DISCOVERY_ENDPOINT).handler(this::get);
        router.route().setName("get").method(HttpMethod.GET).path(DISCOVERY_ENDPOINT+"/:id/").handler(this::validate).handler(this::getById);
    }
    private void validate(RoutingContext context) {
        HttpServerResponse response = context.response();
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        try{
            if((!(context.currentRoute().getName().equals("get"))) && (!(context.currentRoute().getName().equals("delete"))) ){
                var credentials = context.getBodyAsJson();
                credentials.stream().forEach(value->{
                    if(credentials.getValue(value.getKey()) instanceof String) {
                        credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                    }});
                context.setBody(credentials.toBuffer());
            }
            switch (context.currentRoute().getName()){
                case "create" ->{
                    if(!(context.getBodyAsJson().containsKey(DISCOVERY_NAME)) || context.getBodyAsJson().getString(DISCOVERY_NAME)==null){
                        error.add("Discovery name is null");
                    }
                    else if(!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE)==null){
                        error.add("Credential Profile is null");
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
                    if(error.isEmpty()){
                        eventBus.<JsonObject>request(DISCOVERY_DATABASE,context.getBodyAsJson().put(METHOD,DISCOVERY_DATABASE_CHECK_NAME), handler ->{
                            if(handler.succeeded() ){
                                context.next();
                            }else{
                                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE,handler.cause().getMessage()).put(STATUS,FAIL).encodePrettily());
                            }
                        });
                    }else{
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put(ERROR, error).put(STATUS, FAIL).encodePrettily());
                    }
                }
                case "delete" ->{
                    if (context.pathParam("id")!= null) {
                        eventBus.<JsonObject>request(DISCOVERY_DATABASE, new JsonObject().put(DISCOVERY_ID,context.pathParam("id")).put(METHOD, DISCOVERY_DATABASE_CHECK_ID), handler -> {
                            if (handler.succeeded() ) {
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
                case "get"    ->{
                    if(context.pathParam("id")==null){
                        response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE,"id is null").put(STATUS,FAIL).encodePrettily());
                    }else{
                        eventBus.<JsonObject>request(DISCOVERY_DATABASE,new JsonObject().put(DISCOVERY_ID,context.pathParam("id")).put(METHOD,DISCOVERY_DATABASE_CHECK_ID),handler ->{
                            if(handler.succeeded() ){
                                context.next();
                            }else{
                                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE,handler.cause().getMessage()).put(STATUS,FAIL).encodePrettily());
                            }
                        });
                    }
                }
                case "update" ->{
                    if(!(context.getBodyAsJson().containsKey(DISCOVERY_ID)) || context.getBodyAsJson().getString(DISCOVERY_ID)==null){
                        response.setStatusCode(400).putHeader("content-type",HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,"Id is null").encodePrettily());
                    }else{
                        eventBus.<JsonObject>request(DISCOVERY_DATABASE,context.getBodyAsJson().put(METHOD,DISCOVERY_DATABASE_CHECK_MULTIPLE),handler ->{
                            if(handler.succeeded()){
                               context.next();
                            }else{
                                response.setStatusCode(400).putHeader("content-type",HEADER_TYPE);
                                response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
                            }
                        });
                    }
                }
            }
        }
        catch (Exception exception){
            response.setStatusCode(400).putHeader("content-type",HEADER_TYPE);
            response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,"Wrong Json Format").put(ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }
    private void create(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE,context.getBodyAsJson().put(METHOD,DISCOVERY_DATABASE_CREATE),handler ->{
          if(handler.succeeded() && handler.result().body()!=null){
              response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
              response.end(new JsonObject().put(STATUS,SUCCESS).put(MESSAGE,handler.result().body().getString(MESSAGE)).encodePrettily());
          }else{
              response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
              response.end(new JsonObject().put(STATUS,FAIL).put(ERROR,handler.cause().getMessage()).encodePrettily());
          }
        });
    }
    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE,context.getBodyAsJson().put(METHOD,DISCOVERY_DATABASE_UPDATE),handler ->{
            if(handler.succeeded()){
                if(handler.succeeded()){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(ERROR,handler.cause().getMessage()).encodePrettily());
                }
            }
        });
    }
    private void delete(RoutingContext context) {
        HttpServerResponse response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DISCOVERY_DATABASE,new JsonObject().put(DISCOVERY_ID,context.pathParam("id")).put(METHOD,DISCOVERY_DATABASE_DELETE),handler ->{
            if(handler.succeeded()){
                if(handler.succeeded()){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
                }

            }

        });
    }
    private void get(RoutingContext context)    {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        var msg ="all";
        eventBus.<JsonObject>request(DISCOVERY_DATABASE,new JsonObject().put(MESSAGE,msg).put(METHOD,DISCOVERY_DATABASE_GET),handler ->{
            if(handler.succeeded()){
                if(handler.succeeded() && handler.result().body()!=null){
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,SUCCESS).put("result",handler.result().body().getJsonArray("result")).encodePrettily());
                }else{
                    response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
                }
            }
        });
    }
    private void getById(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        var id = context.pathParam("id");
        eventBus.<JsonObject>request(DISCOVERY_DATABASE,new JsonObject().put(METHOD,DISCOVERY_DATABASE_GET_ID).put(DISCOVERY_ID,id),handler ->{
            if(handler.succeeded() && handler.result().body() != null){
                response.setStatusCode(200).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,SUCCESS).put("result",handler.result().body().getJsonArray("result")).encodePrettily());
            }else{
                response.setStatusCode(400).putHeader("content-type", HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,FAIL).put(MESSAGE,handler.cause().getMessage()).encodePrettily());
            }
        });
    }

}
