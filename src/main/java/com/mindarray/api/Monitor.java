package com.mindarray.api;

import com.mindarray.Bootstrap;
import com.mindarray.NMS.Utils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
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
        router.route().method(HttpMethod.POST).path(MONITOR_ENDPOINT + "/provision").handler(this::filter).handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.PUT).path(MONITOR_ENDPOINT+"/:id").handler(this::filter).handler(this::validate).handler(this::update);
        router.route().method(HttpMethod.DELETE).path(MONITOR_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT + "/:id").handler(this::validate).handler(this::getId);
        router.route().method(HttpMethod.GET).path(MONITOR_ENDPOINT + "/:id" + "/polling").handler(this::validate).handler(this::priorityPolling);
    }

    private void filter(RoutingContext context) {
        try{
            var credentials = new JsonObject();
            var keyList = Utils.keyList("monitor");
            context.getBodyAsJson().forEach(value -> {
                if (keyList.contains(value.getKey())) {
                    if (context.getBodyAsJson().getValue(value.getKey()) instanceof String) {
                        credentials.put(value.getKey(), context.getBodyAsJson().getString(value.getKey()).trim());
                    } else {
                        credentials.put(value.getKey(), context.getBodyAsJson().getValue(value.getKey()));
                    }
                }

            });
            context.setBody(credentials.toBuffer());
            context.next();
        }catch (Exception exception){
            context.response().setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            context.response().end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong Json format").put(ERROR, exception.getMessage()).encodePrettily());
          LOGGER.error("exception occurred :",exception);
        }
    }

    private void validate(RoutingContext context) {
    LOGGER.info("routing context path :{} , routing context method :{}", context.normalizedPath(), context.request().method());
    var error = new ArrayList<String>();
    var eventBus = Bootstrap.getVertx().eventBus();
    var response = context.response();
    try {
        switch (context.request().method().toString()) {
            case "POST" -> {
                var entries = context.getBodyAsJson();
                if (!(entries.containsKey(CREDENTIAL_PROFILE)) || entries.isEmpty()) {
                    error.add("credential profile is null");
                }
                if (!(entries.containsKey(IP)) || entries.getString(IP).isEmpty()) {
                    error.add("ip is null");
                }
                if (!(entries.containsKey(PORT)) || entries.getString(PORT).isEmpty()) {
                    error.add("port is null");
                }
                if (!(entries.containsKey(TYPE)) || entries.getString(TYPE).isEmpty()) {
                    error.add("type is null");
                }
                if (error.isEmpty()) {
                    context.next();
                    LOGGER.info("validation performed successfully :{}", context.request().method());
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, error).encodePrettily());
                    LOGGER.error("error occurred :{}", error);
                }
            }
            case "DELETE","GET" -> {
                if (context.pathParam("id") == null) {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("id is null");
                } else {
                    eventBus.<JsonObject>request(METRIC_DATA, new JsonObject().put(MONITOR_ID, context.pathParam("id")).put(METHOD, DATABASE_CHECK).put(TABLE, MONITOR_TABLE), handler -> {
                        if (handler.succeeded()) {
                            context.next();
                            LOGGER.info("validation performed successfully :{}", context.request().method());
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                            LOGGER.error(handler.cause().getMessage());
                        }
                    });
                }
            }
            case "PUT" -> {
                var entries = context.getBodyAsJson();
                if (context.pathParam("id") == null) {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("id is null");
                } else if(!(entries.containsKey(PORT) && entries.getInteger(PORT)>0 && entries.getInteger(PORT)<=65534)){
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, "port is null or not in proper range").put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("port is null or not in proper range");
                }else {
                    eventBus.<JsonObject>request(METRIC_DATA, new JsonObject().put(MONITOR_ID, context.pathParam("id")).put(METHOD, DATABASE_CHECK).put(TABLE, MONITOR_TABLE), handler -> {
                        if (handler.succeeded()) {
                            context.next();
                            LOGGER.info("validation performed successfully :{}", context.request().method());
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                            LOGGER.error(handler.cause().getMessage());
                        }
                    });
                }
            }
            default -> LOGGER.error("error occurred :{}", context.request().method());
        }
    } catch (Exception exception) {
        response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
        LOGGER.error("exception occurred :",exception);
    }
    }

    private void create(RoutingContext context) {
    var eventBus = Bootstrap.getVertx().eventBus();
    var response = context.response();
    var entries = context.getBodyAsJson();
    try {
        Promise<JsonObject> promise = Promise.promise();
        Future<JsonObject> future = promise.future();
        var query = "select discovery_id from discovery where JSON_SEARCH(discovery_result,\"one\",\"success\") and credential_profile=\"" + entries.getString(CREDENTIAL_PROFILE) + "\" and "
                + " discovery.ip= \"" + entries.getString(IP) + "\" "+
                " and discovery.type =\"" + entries.getString(TYPE) + "\" " +" and discovery.port=" +entries.getInteger(PORT)+" ;";
        eventBus.<JsonObject>request(METRIC_DATA, new JsonObject().put(METHOD, "runProvision").put(QUERY, query).mergeIn(entries), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, "your unique monitor id is " + handler.result().body().getString("id")).encodePrettily());
                    promise.complete(context.getBodyAsJson().put("id", handler.result().body().getString("id")));
                    LOGGER.info(" context :{}, status :{} , message :{}", context.getBodyAsJson(), SUCCESS, handler.result().body().getString(MESSAGE));
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    if(handler.cause().getMessage().equals("[wrong id provided or no result to showcase]")){
                        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong credentials provided or discovery result is fail").encodePrettily());
                    }else{
                        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    }
                    promise.fail("provision failed");
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error("exception occurred :",exception);
            }
        });
        future.onComplete(handler -> {
            if (handler.succeeded()) {
                var getQuery = "select metric_id,time from credential,metric where metric.credential_profile= credential_id and credential_id=" + handler.result().getInteger(CREDENTIAL_PROFILE) + ";";
                eventBus.<JsonObject>request(METRIC_DATA, handler.result().put(METHOD, "get").put(QUERY, getQuery), result -> {
                    if (result.succeeded() && result.result().body() != null) {
                        eventBus.send(PROVISION_SCHEDULER, result.result().body());
                    } else {
                        LOGGER.error("error occurred :{}", handler.cause().getMessage());
                    }
                });
            } else {
                LOGGER.error("error occurred :{}", handler.cause().getMessage());
            }
        });
    } catch (Exception exception) {
        response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
        response.end(new JsonObject().put(MESSAGE, exception.getMessage()).put(STATUS, FAIL).encodePrettily());
       LOGGER.error("exception occurred :",exception);
    }

    }

    private void delete(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        try {
            Promise<JsonObject> promise = Promise.promise();
            var future = promise.future();
            var query = "delete from metric where monitor_id =" + context.pathParam("id") + ";";
            var metricDelete = "select metric_id from metric where monitor_id=" + context.pathParam("id") + ";";
            eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, metricDelete), handler -> {
                if (handler.succeeded() && handler.result().body() != null) {
                    promise.complete(handler.result().body());
                } else {
                    promise.fail(handler.cause().getMessage());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            });
            future.onComplete(completeHandler -> {
                if (completeHandler.succeeded()) {
                    eventBus.request(METRIC_DATA, new JsonObject().put(QUERY, query).put(METHOD, DATABASE_DELETE).put(MONITOR_ID, context.pathParam("id")), handler -> {
                        if (handler.succeeded() && handler.result().body() != null) {
                            response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                            eventBus.send(MONITOR_SCHEDULER_DELETE, completeHandler.result());
                            LOGGER.info("context :{}, status :{}", context.pathParam("id"), SUCCESS);
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                            LOGGER.error("error occurred :{}", handler.cause().getMessage());
                        }
                    });
                } else {
                    LOGGER.error("error occurred :{}", completeHandler.cause().getMessage());
                }
            });
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong json format").put(ERROR, exception.getMessage()).encodePrettily());
           LOGGER.error("exception occurred :",exception);
        }
    }

    private void get(RoutingContext context) {
        var query = "select * from monitor";
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(METRIC_DATA, new JsonObject().put(METHOD, DATABASE_GET).put(QUERY, query), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error("exception occurred :",exception);
            }

        });
    }

    private void getId(RoutingContext context) {
        var response = context.response();
        var query = "select * from monitor where monitor_id=" + context.pathParam("id") + ";";
        Bootstrap.getVertx().eventBus().<JsonObject>request(METRIC_DATA, new JsonObject().put(METHOD, DATABASE_GET).put(QUERY, query), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                    LOGGER.info("context :{}, status :{}", context.pathParam("id"), SUCCESS);
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error("exception occurred :",exception);
            }

        });
    }

    private void update(RoutingContext context) {
    var response = context.response();
    Bootstrap.getVertx().eventBus().<JsonObject>request(METRIC_DATA,new JsonObject().put(METHOD,"update").put(PORT,context.getBodyAsJson().getInteger(PORT)).put(MONITOR_ID,context.pathParam("id")),handler->{
        try {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS,SUCCESS).encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                LOGGER.error("error occurred :{}", handler.cause().getMessage());
            }
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
            LOGGER.error("exception occurred :",exception);
        }
    });

    }

    private void priorityPolling(RoutingContext context) {
        var response = context.response();
        try {
            var id = context.pathParam("id");
            var eventBus = Bootstrap.getVertx().eventBus();
            var query = "select monitor.monitor_id,metric_id,metric_group,time,type,ip,port,username,password,community,version from credential,monitor,metric where monitor.monitor_id = metric.monitor_id and credential.credential_id=metric.credential_profile and monitor.monitor_id=" + id + ";";
            eventBus.<JsonObject>request(METRIC_DATA, new JsonObject().put(QUERY, query).put(METHOD, "get"), handler -> {
                try {
                    if (handler.succeeded() && handler.result().body() != null) {
                        var object = handler.result().body().getJsonArray(RESULT);
                        for (int index = 0; index < object.size(); index++) {
                            var data = object.getJsonObject(index);
                            eventBus.send(SCHEDULER_POLLING, data.put(CATEGORY, "polling").put("id", System.currentTimeMillis()));
                        }
                        response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(handler.result().body().encodePrettily());
                        LOGGER.info("context :{}, status :{}", context.pathParam("id"), SUCCESS);
                    } else {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(handler.cause().getMessage());
                        LOGGER.error("error occurred :{}", handler.cause().getMessage());
                    }
                } catch (Exception exception) {
                    response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                    LOGGER.error("exception occurred :",exception);
                }

            });
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong json format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error("exception occurred :",exception);
        }
    }
}
