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
        router.route().method(HttpMethod.POST).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.PUT).path(CREDENTIAL_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::getById);
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
                    if (context.getBodyAsJson().containsKey(PROTOCOl)) {
                        if (context.getBodyAsJson().getString(PROTOCOl).equals("ssh") || context.getBodyAsJson().getString(PROTOCOl).equals("powershell")) {
                            if (!(context.getBodyAsJson().containsKey("username"))) {
                                error.add("Username not provided");
                            }
                            if (!(context.getBodyAsJson().containsKey("password"))) {
                                error.add("Password not provided");
                            }
                            if (context.getBodyAsJson().containsKey(VERSION)) {
                                error.add("wrong key version in type " + context.getBodyAsJson().getString(TYPE));
                            }
                            if (context.getBodyAsJson().containsKey(COMMUNITY)) {
                                error.add("wrong key community in type " + context.getBodyAsJson().getString(TYPE));
                            }
                        }
                        if (context.getBodyAsJson().getString(PROTOCOl).equals("snmp")) {
                            if (!(context.getBodyAsJson().containsKey("community"))) {
                                error.add("Community not provided");
                            }
                            if (!(context.getBodyAsJson().containsKey("version"))) {
                                error.add("Version not provided");
                            }
                            if (context.getBodyAsJson().containsKey(USERNAME)) {
                                error.add("username does not comes in snmp");
                            }
                            if (context.getBodyAsJson().containsKey(PASSWORD)) {
                                error.add("password does not comes in snmp");
                            }
                        } else {
                            error.add("Wrong protocol selected");
                        }
                    }
                    if (context.getBodyAsJson().containsKey(CREDENTIAL_NAME) && context.getBodyAsJson().getString(CREDENTIAL_NAME) != null) {
                        error.add("credential name should be provided");
                    }
                    if (error.isEmpty()) {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_CHECK).put(TABLE, CREDENTIAL_TABLE).put(CREDENTIAL_NAME, context.getBodyAsJson().getString(CREDENTIAL_NAME)), handler -> {
                            if (handler.succeeded() && handler.result().body() != null) {
                                context.next();
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
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
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
                    if ((!(context.getBodyAsJson().containsKey(CREDENTIAL_ID))) || context.getBodyAsJson().getString(CREDENTIAL_ID) == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    } else {
                        if (context.getBodyAsJson().containsKey(CREDENTIAL_ID)) {
                            conditions.put(CREDENTIAL_ID, context.getBodyAsJson().getString(CREDENTIAL_ID));
                        }
                        if (context.getBodyAsJson().containsKey(CREDENTIAL_NAME)) {
                            conditions.put(CREDENTIAL_NAME, context.getBodyAsJson().getString(CREDENTIAL_NAME));
                        }
                        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_CHECK).put(TABLE, CREDENTIAL_TABLE).mergeIn(conditions), handler -> {
                            if (handler.succeeded()) {
                                context.next();
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(ERROR, new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage())).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
                            }
                        });
                    }
                }
                case "GET" -> {
                    if (context.pathParam("id") == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                    } else {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(CREDENTIAL_ID, (context.pathParam("id"))).put(METHOD, CREDENTIAL_DATABASE_CHECK_ID), handler -> {
                            if (handler.succeeded()) {
                                context.next();
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
                            }
                        });
                    }


                }

                default -> {
                    LOGGER.error("Error occurred {} ", context.request().method());
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getCause().getMessage()).encodePrettily());
            LOGGER.error(exception.getCause().getMessage());
        }
    }

    private void create(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_CREATE).put(TABLE, CREDENTIAL_TABLE), handler -> {
            if (handler.succeeded()) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, handler.result().body().getString(MESSAGE)).encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }
        });

    }

    private void update(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_UPDATE).put(TABLE, CREDENTIAL_TABLE), handler -> {
            if (handler.succeeded()) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }
        });
    }

    private void delete(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_DELETE).put(CREDENTIAL_ID, context.pathParam("id")).put(TABLE, CREDENTIAL_TABLE), handler -> {
            if (handler.succeeded()) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }

        });
    }

    private void get(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put(TABLE, CREDENTIAL_TABLE).put("condition", "all"), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }
        });
    }

    private void getById(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put("column", "credential_id").put("value", context.pathParam("id")).put(TABLE, CREDENTIAL_TABLE).put("condition", "individual"), handler -> {
            if (handler.succeeded() && handler.result().body() != null) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(handler.result().body().encodePrettily());
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }
        });
    }
}
