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
        router.route().setName("delete").method(HttpMethod.DELETE).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT).handler(this::get);
        router.route().setName("get").method(HttpMethod.GET).path(CREDENTIAL_ENDPOINT + "/:id").handler(this::validate).handler(this::getById);
    }

    private void validate(RoutingContext context) {
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        HttpServerResponse response = context.response();
        System.out.println(context.request().method());
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
                    if (error.isEmpty()) {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD,DATABASE_CHECK).put(TABLE,"credential").put(CREDENTIAL_NAME,context.getBodyAsJson().getString(CREDENTIAL_NAME)), handler -> {
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
                        LOGGER.error(error.toString());
                    }
                }
                case "delete" -> {
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
                case "update" -> {
                    var conditions = new JsonObject();
                    if ((!(context.getBodyAsJson().containsKey(CREDENTIAL_ID))) || context.getBodyAsJson().getString(CREDENTIAL_ID) == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    } else {
                        if (context.getBodyAsJson().containsKey(CREDENTIAL_ID)) {
                            conditions.put(CREDENTIAL_ID,context.getBodyAsJson().getString(CREDENTIAL_ID));
                        }
                        if (context.getBodyAsJson().containsKey(CREDENTIAL_NAME)) {
                            conditions.put(CREDENTIAL_NAME,context.getBodyAsJson().getString(CREDENTIAL_NAME));
                        }
                        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_CHECK).put(TABLE,"credential").mergeIn(conditions), handler -> {
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
                case "get" -> {
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
            }
        } catch (Exception exception) {
            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getCause().getMessage()).encodePrettily());
            LOGGER.error(exception.getCause().getMessage());
        }
    }

    private void delete(RoutingContext context) {
        HttpServerResponse response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_DELETE).put(CREDENTIAL_ID, context.pathParam("id")).put(TABLE,"credential"), handler -> {
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

    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_UPDATE).put(TABLE,"credential"), handler -> {
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

    private void create(RoutingContext context) {
        var eventBus = Bootstrap.vertx.eventBus();
        var response = context.response();
        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DATABASE_CREATE).put(TABLE,"credential"), handler -> {
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

    private void get(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String msg = "all";
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put(MESSAGE, msg).put(TABLE,"credential").put("condition",msg), handler -> {
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
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        String id = context.pathParam("id");
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put("column", "credential_id").put("value",id).put(TABLE,"credential").put("condition","individual"), handler -> {
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
