package com.mindarray.api;

import com.mindarray.Bootstrap;
import com.mindarray.NMS.Utils;
import io.vertx.core.http.HttpMethod;
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
        router.route().method(HttpMethod.POST).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::getById);
    }

    private void validate(RoutingContext context) {
        LOGGER.info("routing context path :{} , routing context method : {}", context.normalizedPath(), context.request().method());
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        try {
            if ((!(context.request().method().toString().equals("GET"))) && (!(context.request().method().toString().equals("DELETE")))) {
                var credentials = new JsonObject();
                var keyList = Utils.keyList("credential");
                if (keyList.isEmpty()) {
                    error.add("wrong credential selected or empty json");
                } else {
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
                }
            }
            switch (context.request().method().toString()) {
                case "POST" -> {
                    if (context.getBodyAsJson().containsKey(PROTOCOl)) {
                        if (context.getBodyAsJson().getString(PROTOCOl).equals("ssh") || context.getBodyAsJson().getString(PROTOCOl).equals("powershell")) {
                            if (!(context.getBodyAsJson().containsKey("username"))) {
                                error.add("username not provided");
                            }
                            if (!(context.getBodyAsJson().containsKey("password"))) {
                                error.add("password not provided");
                            }
                            if (context.getBodyAsJson().containsKey(VERSION)) {
                                error.add("wrong key version in type " + context.getBodyAsJson().getString(PROTOCOl));
                            }
                            if (context.getBodyAsJson().containsKey(COMMUNITY)) {
                                error.add("wrong key community in type " + context.getBodyAsJson().getString(PROTOCOl));
                            }
                        } else if (context.getBodyAsJson().getString(PROTOCOl).equals("snmp")) {
                            if (!(context.getBodyAsJson().containsKey("community"))) {
                                error.add("community not provided");
                            }
                            if (!(context.getBodyAsJson().containsKey("version"))) {
                                error.add("version not provided");
                            }
                            if (context.getBodyAsJson().containsKey(USERNAME)) {
                                error.add("username does not comes in snmp");
                            }
                            if (context.getBodyAsJson().containsKey(PASSWORD)) {
                                error.add("password does not comes in snmp");
                            }
                        } else {
                            error.add("wrong protocol selected");
                        }
                    }
                    if ((!context.getBodyAsJson().containsKey(CREDENTIAL_NAME)) || context.getBodyAsJson().getString(CREDENTIAL_NAME) == null) {
                        error.add("credential name should be provided");
                    }

                    if (error.isEmpty()) {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_CHECK).put(TABLE, CREDENTIAL_TABLE).put(CREDENTIAL_NAME, context.getBodyAsJson().getString(CREDENTIAL_NAME)), handler -> {
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
                case "DELETE" -> {
                    var id = context.pathParam("id");
                    if (id != null) {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, CREDENTIAL_DATABASE_CHECK_ID).put(CREDENTIAL_ID, id), handler -> {
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
                    if ((!(context.getBodyAsJson().containsKey(CREDENTIAL_ID))) || context.getBodyAsJson().getInteger(CREDENTIAL_ID) == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    } else {
                        conditions.put(CREDENTIAL_ID, context.getBodyAsJson().getString(CREDENTIAL_ID));
                        if (context.getBodyAsJson().containsKey(CREDENTIAL_NAME)) {
                            conditions.put(CREDENTIAL_NAME, context.getBodyAsJson().getString(CREDENTIAL_NAME));
                        }
                        eventBus.<JsonObject>request(DATABASE, conditions.put(METHOD, DATABASE_CHECK).put(TABLE, CREDENTIAL_TABLE), handler -> {
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
                default -> {
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
        var data = context.getBodyAsJson();
        data.remove(PROTOCOl);
        var query = "select protocol from credential where credential_id=" + context.getBodyAsJson().getInteger(CREDENTIAL_ID) + ";";
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                if (handler.result().body().getJsonArray(RESULT).getJsonObject(0).getString(PROTOCOl).equals("snmp")) {
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
                if (handler.succeeded()) {
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
