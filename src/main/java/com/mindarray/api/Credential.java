package com.mindarray.api;

import com.mindarray.Bootstrap;
import com.mindarray.NMS.Utils;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
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
        router.route().method(HttpMethod.POST).path(CREDENTIAL_ENDPOINT).handler(this::filter).handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::filter).handler(this::validate).handler(this::update);
        router.route().method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::getById);
    }

    private void filter(RoutingContext context) {
        try{
            var credentials = new JsonObject();
            var entries = context.getBodyAsJson();
            var keyList = Utils.keyList("credential");
            if (keyList.isEmpty()) {
                LOGGER.error("error occurred :{}","wrong credential selected or empty json");
            } else {
                entries.forEach(value -> {
                    if (keyList.contains(value.getKey())) {
                        if (entries.getValue(value.getKey()) instanceof String) {
                            credentials.put(value.getKey(), entries.getString(value.getKey()).trim());
                        } else {
                            credentials.put(value.getKey(), entries.getValue(value.getKey()));
                        }
                    }
                });
                context.setBody(credentials.toBuffer());
                context.next();
            }
        }catch (Exception exception){
           context.response().setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
           context.response().end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong Json format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error(exception.getCause().getMessage());
        }


    }

    private void validate(RoutingContext context) {
        LOGGER.info("routing context path :{} , routing context method : {}", context.normalizedPath(), context.request().method());
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        try {
            switch (context.request().method().toString()) {
                case "POST" -> {
                    var entries = context.getBodyAsJson();
                    if (entries.containsKey(PROTOCOL)) {
                        if (entries.getString(PROTOCOL).equals("ssh") || entries.getString(PROTOCOL).equals("powershell")) {
                            if (!(entries.containsKey("username"))) {
                                error.add("username not provided");
                            }
                            if (!(entries.containsKey("password"))) {
                                error.add("password not provided");
                            }
                            if (entries.containsKey(VERSION)) {
                                error.add("wrong key version in type " + entries.getString(PROTOCOL));
                            }
                            if (entries.containsKey(COMMUNITY)) {
                                error.add("wrong key community in type " + entries.getString(PROTOCOL));
                            }
                        } else if (entries.getString(PROTOCOL).equals("snmp")) {
                            if (!(entries.containsKey("community"))) {
                                error.add("community not provided");
                            }
                            if (!(entries.containsKey("version"))) {
                                error.add("version not provided");
                            }
                            if (entries.containsKey(USERNAME)) {
                                error.add("username does not comes in snmp");
                            }
                            if (entries.containsKey(PASSWORD)) {
                                error.add("password does not comes in snmp");
                            }
                        } else {
                            error.add("wrong protocol selected");
                        }
                    }else{
                        error.add("protocol is empty");
                    }
                    if ((!entries.containsKey(CREDENTIAL_NAME)) || context.getBodyAsJson().getString(CREDENTIAL_NAME).equals("")) {
                        error.add("credential name should be provided");
                    }
                    if (error.isEmpty()) {
                        var parameters = new JsonArray().add(new JsonObject().put(TABLE,CREDENTIAL_TABLE).put(COLUMN,CREDENTIAL_NAME).put(VALUE,entries.getString(CREDENTIAL_NAME)));
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_CHECK).put(PARAMETER,parameters), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
                                LOGGER.info("validation performed successfully :{}", context.request().method());
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
                            }
                        });
                    } else {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, error).put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("Error occurred{}", error);
                    }
                }
                case "DELETE" ->{
                    var id = context.pathParam("id");
                    if (id != null) {
                        var parameters = new JsonArray();
                        parameters.add(new JsonObject().put(TABLE,CREDENTIAL_TABLE).put(COLUMN,CREDENTIAL_ID).put(VALUE,id));
                        parameters.add(new JsonObject().put(TABLE,DISCOVERY_TABLE).put(COLUMN,CREDENTIAL_PROFILE).put(VALUE,id));
                        parameters.add(new JsonObject().put(TABLE,METRIC_TABLE).put(COLUMN,CREDENTIAL_PROFILE).put(VALUE,id));
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_CHECK).put(PARAMETER, parameters), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
                                LOGGER.info("validation performed successfully :{}", context.request().method());
                            } else {
                                if (handler.cause().getMessage().equals("[credential_profile is not unique]")) {
                                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "credential already exists in metric table").encodePrettily());
                                } else {
                                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                                }
                            }
                        });
                    } else {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    }
                }
                case "PUT" -> {
                    var conditions = new JsonObject();
                    var parameters = new JsonArray();
                    var entries = context.getBodyAsJson();
                    var id = context.pathParam("id");
                    if (id == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    } else {
                        conditions.put(TABLE,CREDENTIAL_TABLE).put(COLUMN,CREDENTIAL_ID).put(VALUE,id);
                        parameters.add(conditions);
                        if (entries.containsKey(CREDENTIAL_NAME)) {
                            conditions.put(TABLE,CREDENTIAL_TABLE).put(COLUMN,CREDENTIAL_NAME).put(VALUE,entries.getString(CREDENTIAL_NAME));
                            parameters.add(conditions);
                        }
                        eventBus.<JsonObject>request(DATABASE, entries.put(METHOD, DATABASE_CHECK).put(PARAMETER, parameters), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
                                LOGGER.info("validation performed successfully :{}", context.request().method());
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                                LOGGER.error("error occurred :{}", handler.cause().getMessage());
                            }
                        });
                    }
                }
                case "GET" -> {
                    if (context.pathParam("id") == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    } else {
                        context.next();
                        LOGGER.info("validation performed successfully :{}", context.request().method());
                    }
                }
                default ->    {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong method selected").encodePrettily());
                    LOGGER.error("error occurred {} ", context.request().method());
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong Json format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error(exception.getCause().getMessage());
        }
    }

    private void create(RoutingContext context) {
        var data = context.getBodyAsJson();
        data.remove(CREDENTIAL_ID);
        data.remove(CREDENTIAL_PROFILE);
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, data.put(METHOD, DATABASE_CREATE).put(TABLE, CREDENTIAL_TABLE), handler -> {
            try {
                if (handler.succeeded()) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, handler.result().body().getString(MESSAGE)).encodePrettily());
                    LOGGER.info(" context:{}, status :{} , message :{}", context.getBodyAsJson(), "success", handler.result().body().getString(MESSAGE));
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error(exception.getMessage());
            }
        });
    }

    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        var data = context.getBodyAsJson().put(CREDENTIAL_ID,context.pathParam("id"));
        data.remove(PROTOCOL);
        var query = "select protocol from credential where credential_id=" + context.pathParam("id") + ";";
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                if (handler.result().body().getJsonArray(RESULT).getJsonObject(0).getString(PROTOCOL).equals("snmp")) {
                    data.remove(USERNAME);
                    data.remove(PASSWORD);
                } else {
                    data.remove(COMMUNITY);
                    data.remove(VERSION);
                }
                eventBus.<JsonObject>request(DATABASE, data.put(METHOD, DATABASE_UPDATE).put(TABLE, CREDENTIAL_TABLE), result -> {
                    try {
                        if (result.succeeded() && result.result().body() != null) {
                            response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                            LOGGER.info("context :{}, status :{}", context.getBodyAsJson(), "success");
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, result.cause().getMessage()).encodePrettily());
                            LOGGER.error("error occurred :{}", result.cause().getMessage());
                        }
                    } catch (Exception exception) {
                        response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                        LOGGER.error(exception.getMessage());
                    }
                });
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                LOGGER.error("error occurred: {}", handler.cause().getMessage());
            }
        });

    }

    private void delete(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_DELETE).put(CREDENTIAL_ID, context.pathParam("id")).put(TABLE, CREDENTIAL_TABLE), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                    LOGGER.info("context :{}, status :{}", context.pathParam("id"), "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error("error occurred: {}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error(exception.getMessage());
            }
        });
    }

    private void get(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put(TABLE, CREDENTIAL_TABLE).put("condition", "all"), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                    LOGGER.info("status :{}", "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error(exception.getMessage());
            }

        });
    }

    private void getById(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put("column", "credential_id").put("value", context.pathParam("id")).put(TABLE, CREDENTIAL_TABLE).put("condition", "individual"), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(handler.result().body().encodePrettily());
                    LOGGER.info("context :{}, status :{}", context.pathParam("id"), "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                    LOGGER.error("error occurred :{}", handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error(exception.getMessage());
            }

        });
    }
}
