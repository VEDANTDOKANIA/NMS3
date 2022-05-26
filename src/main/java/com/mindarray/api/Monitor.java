package com.mindarray.api;

import com.mindarray.Bootstrap;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;

public class Monitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Monitor.class);

    public void init(Router router) {
        router.route().method(HttpMethod.POST).path(MONITOR_ENDPOINT + "/Provision").handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.DELETE).path(MONITOR_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT + "/:id").handler(this::validate).handler(this::getId);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT+"/:id"+"/Polling").handler(this::validate).handler(this::priorityPolling);
    }

    private void validate(RoutingContext context) {
        LOGGER.info("routing context path :{} , routing context method : {}",context.normalizedPath(),context.request().method());
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        HttpServerResponse response = context.response();
        //trim data
        try {
            if ((!(context.request().method().toString().equals("GET"))) && (!(context.request().method().toString().equals("DELETE")))) {
                var credentials = context.getBodyAsJson();
                credentials.forEach(value -> {
                    if (credentials.getValue(value.getKey()) instanceof String) {
                        credentials.put(value.getKey(), credentials.getString(value.getKey()).trim());
                    }
                });
                context.setBody(credentials.toBuffer());
            }
            switch (context.request().method().toString()) {
                case "POST" -> {
                    if (!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE) == null) {
                        error.add("Credential profile is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(IP)) || context.getBodyAsJson().getString(IP) == null) {
                        error.add("IP is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(PORT)) || context.getBodyAsJson().getString(PORT) == null) {
                        error.add("port is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(TYPE)) || context.getBodyAsJson().getString(TYPE) == null) {
                        error.add("Type is null");
                    }
                    if (error.isEmpty()) {
                        context.next();
                        LOGGER.info("validation performed successfully :{}",context.request().method());
                    } else {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, error).encodePrettily());
                        LOGGER.error("Error occurred {}", error);

                    }

                }
                case "DELETE", "GET" -> {
                    if (context.pathParam("id") == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    } else {
                        eventBus.<JsonObject>request(PROVISION, new JsonObject().put(MONITOR_ID, context.pathParam("id")).put(METHOD, DATABASE_CHECK).put(TABLE, MONITOR_TABLE), handler -> {
                            if (handler.succeeded()) {
                                context.next();
                                LOGGER.info("validation performed successfully :{}",context.request().method());
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
                            }
                        });
                    }
                }
                default -> {
                    LOGGER.error("error occurred {}", context.request().method());
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error(exception.getMessage());
        }
    }
    private void create(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        Promise<JsonObject> promise = Promise.promise();
        Future<JsonObject> future = promise.future();
        var response = context.response();
        var query = "select discovery_id from discovery where JSON_SEARCH(discovery_result,\"one\",\"success\") and credential_profile=\"" + context.getBodyAsJson().getString(CREDENTIAL_PROFILE) + "\" and "
                + " discovery.ip= \"" + context.getBodyAsJson().getString(IP) + "\" and " + "discovery.port=" + context.getBodyAsJson().getInteger(PORT) +
                " and discovery.type =\"" + context.getBodyAsJson().getString(TYPE) + "\" ;";
        eventBus.<JsonObject>request(PROVISION, new JsonObject().put(METHOD, "runProvision").put(QUERY, query).mergeIn(context.getBodyAsJson()), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, "your unique monitor id is "+handler.result().body().getString("id")).encodePrettily());
                promise.complete(context.getBodyAsJson().put("id",handler.result().body().getString("id")));
                LOGGER.info(" context :{}, status :{} , message :{}",context.getBodyAsJson(),"success",handler.result().body().getString(MESSAGE));
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                promise.fail("provision failed");
                LOGGER.error("error occurred :{}",handler.cause().getMessage());
            }
        });
        future.onComplete(handler ->{
            if(handler.succeeded()){
                var getQuery = "select metric_id,time from credential,metric where metric.credential_profile= credential_id and credential_id="+ handler.result().getInteger(CREDENTIAL_PROFILE)+";";
                eventBus.<JsonObject>request(PROVISION,handler.result().put(METHOD,"get").put(QUERY,getQuery),result ->{
                   if(result.succeeded()){
                      eventBus.send(PROVISION_SCHEDULER,result.result().body());
                   }else{
                       LOGGER.error("error occurred :{}",handler.cause().getMessage());
                   }
                });
            }else{
                LOGGER.error("error occurred :{}",handler.cause().getMessage());
            }
        });
    }
    private void delete(RoutingContext context) {
        var response = context.response();
        var query = "delete from metric where monitor_id =" + context.pathParam("id") + ";";
        Bootstrap.getVertx().eventBus().request(PROVISION, new JsonObject().put(QUERY, query).put(METHOD, DATABASE_DELETE).put(MONITOR_ID, context.pathParam("id")), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                LOGGER.info("context :{}, status :{}",context.pathParam("id"),"success");
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                LOGGER.error("error occurred :{}",handler.cause().getMessage());
            }
        });
    }
    private void get(RoutingContext context) {
        var query = "select * from monitor";
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(PROVISION, new JsonObject().put(METHOD, DATABASE_GET).put(QUERY, query), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
                LOGGER.info("status :{}","success");
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                LOGGER.error("error occurred :{}",handler.cause().getMessage());
            }
        });
    }
    private void getId(RoutingContext context) {
        var query = "select * from monitor where monitor_id=" + context.pathParam("id") + ";";
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(PROVISION, new JsonObject().put(METHOD, DATABASE_GET).put(QUERY, query), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
                LOGGER.info("context :{}, status :{}",context.pathParam("id"),"success");
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                LOGGER.error("error occurred :{}",handler.cause().getMessage());
            }
        });
    }
    private void priorityPolling(RoutingContext context) {
        var response = context.response();
        var id = context.pathParam("id");
        var eventBus = Bootstrap.getVertx().eventBus();
        var query = "select monitor.monitor_id,metric_id,metric_group,time,type,ip,port,username,password,community,version from credential,monitor,metric where monitor.monitor_id = metric.monitor_id and credential.credential_id=metric.credential_profile and monitor.monitor_id=" +id+";";
       eventBus.<JsonObject>request(PROVISION,new JsonObject().put(QUERY,query).put(METHOD,"get"),handler ->{
           if(handler.succeeded() && handler.result().body()!=null){
                var object =handler.result().body().getJsonArray(RESULT);
                for( int index =0 ;index <object.size() ;index++){
                    var data = object.getJsonObject(index);
                    eventBus.send(SCHEDULER_POLLING,data.put("category","polling").put("id",System.currentTimeMillis()));
                }
               response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
               response.end(handler.result().body().encodePrettily());
               LOGGER.info("context :{}, status :{}",context.pathParam("id"),"success");
           }else{
               response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
               response.end(handler.cause().getMessage());
               LOGGER.error("error occurred :{}",handler.cause().getMessage());
           }
       });



    }
}
