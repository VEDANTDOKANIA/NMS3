package com.mindarray.api;

import com.mindarray.Bootstrap;
import com.mindarray.NMS.Constant;
import com.mindarray.NMS.Utils;
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

public class Discovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(Discovery.class);

    public void init(Router router) {
        router.route().method(HttpMethod.POST).path(DISCOVERY_ENDPOINT).handler(this::validate).handler(this::create);
        router.route().method(HttpMethod.PUT).path(DISCOVERY_ENDPOINT).handler(this::validate).handler(this::update);
        router.route().method(HttpMethod.DELETE).path(DISCOVERY_ENDPOINT + "/:id").handler(this::validate).handler(this::delete);
        router.route().method(HttpMethod.GET).path(DISCOVERY_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.GET).path(DISCOVERY_ENDPOINT + "/:id").handler(this::validate).handler(this::getById);
        router.route().method(HttpMethod.POST).path(DISCOVERY_ENDPOINT + "/:id/run").handler(this::runDiscovery);
    }


    private void validate(RoutingContext context) {
        LOGGER.info("routing context path :{} , routing context method : {}", context.normalizedPath(), context.request().method());
        HttpServerResponse response = context.response();
        var error = new ArrayList<String>();
        var eventBus = Bootstrap.getVertx().eventBus();
        try {
            if ((!(context.request().method().toString().equals("GET"))) && (!(context.request().method().toString().equals("DELETE")))) {
                var credentials = new JsonObject();
                var keyList = Utils.keyList("discovery");
                context.getBodyAsJson().forEach(value -> {
                    if (keyList.contains(value.getKey())) {
                        if (context.getBodyAsJson().getValue(value.getKey()) instanceof String) {
                            credentials.put(value.getKey(), context.getBodyAsJson().getString(value.getKey()).trim());
                        }else{
                            credentials.put(value.getKey(),context.getBodyAsJson().getValue(value.getKey()));
                        }
                    }
                });
                context.setBody(credentials.toBuffer());
            }
            switch (context.request().method().toString()) {
                case "POST" -> {
                    if (!(context.getBodyAsJson().containsKey(DISCOVERY_NAME)) || context.getBodyAsJson().getString(DISCOVERY_NAME) == null) {
                        error.add("discovery name is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE) == null) {
                        error.add("credential profile is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(DISCOVERY_IP)) || context.getBodyAsJson().getString(DISCOVERY_IP) == null) {
                        error.add("ip is null");
                    }
                    if (!(context.getBodyAsJson().containsKey(DISCOVERY_TYPE)) || context.getBodyAsJson().getString(DISCOVERY_TYPE) == null) {
                        error.add("no type defined for discovery");
                    }
                    if (!(context.getBodyAsJson().containsKey(DISCOVERY_PORT)) || context.getBodyAsJson().getInteger(DISCOVERY_PORT) == null) {
                        error.add("port not defined for discovery");
                    }
                    if (!(context.getBodyAsJson().containsKey(CREDENTIAL_PROFILE)) || context.getBodyAsJson().getString(CREDENTIAL_PROFILE) == null) {
                        error.add("credential profile not defined for discovery");
                    }
                    //Unique Discovery Name
                    if (error.isEmpty()) {
                        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DISCOVERY_DATABASE_CHECK_NAME), handler -> {
                            if (handler.succeeded() && handler.result().body()!=null) {
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
                        response.end(new JsonObject().put(ERROR, error).put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("An error occurred {}", error);
                    }
                }
                case "DELETE" -> {
                    if (context.pathParam("id") != null) {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(DISCOVERY_ID, context.pathParam("id")).put(METHOD, DATABASE_CHECK).put(TABLE, DISCOVERY_TABLE), handler -> {
                            if (handler.succeeded() && handler.result().body()!=null) {
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
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("id is null");
                    }
                }
                case "GET" -> {
                    if (context.pathParam("id") == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(MESSAGE, "id is null").put(STATUS, FAIL).encodePrettily());
                        LOGGER.error("error occurred :{}", "id is null");
                    } else {
                        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(DISCOVERY_ID, context.pathParam("id")).put(METHOD, DATABASE_CHECK).put(TABLE, DISCOVERY_TABLE), handler -> {
                            if (handler.succeeded() && handler.result().body()!=null) {
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
                    if (!(context.getBodyAsJson().containsKey(DISCOVERY_ID)) || context.getBodyAsJson().getString(DISCOVERY_ID) == null) {
                        response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Id is null").encodePrettily());
                        LOGGER.error("error occurred :{}", "id is null");
                    } else {
                        eventBus.<JsonObject>request(DATABASE, context.getBodyAsJson().put(METHOD, DISCOVERY_DATABASE_CHECK_MULTIPLE), handler -> {
                            if (handler.succeeded() && handler.result().body()!=null) {
                                context.next();
                                LOGGER.info("validation performed successfully :{}", context.request().method());
                            } else {
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                                LOGGER.error(handler.cause().getMessage());
                            }
                        });
                    }
                }
                default -> {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "no matching route").encodePrettily());
                    LOGGER.error("error occurred :{}", "No matching route");
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getCause().getMessage()).encodePrettily());
            LOGGER.error("error occurred :{}", exception.getMessage());
        }
    }

    private void create(RoutingContext context) {
        var response = context.response();
        var data = context.getBodyAsJson();
        data.remove("discovery.id");
        data.remove("discovery.result");
        data.remove("credential.id");
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, data.put(METHOD, DATABASE_CREATE).put(TABLE, DISCOVERY_TABLE), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, handler.result().body().getString(MESSAGE)).encodePrettily());
                    LOGGER.info(" context:{}, status :{} , message :{}", context.getBodyAsJson(), "success", handler.result().body().getString(MESSAGE));
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, handler.cause().getMessage()).encodePrettily());
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
        var data = context.getBodyAsJson();
        data.remove("discovery.result");
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, data.put(METHOD, DATABASE_UPDATE).put(TABLE, DISCOVERY_TABLE), handler -> {
            try {
                if (handler.succeeded() && handler.result().body()!=null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                    LOGGER.info("context:{}, status :{}", context.getBodyAsJson(), "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error(handler.cause().getMessage());
                }
            } catch (Exception exception) {
                response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                LOGGER.error(exception.getMessage());
            }
        });
    }

    private void delete(RoutingContext context) {
        var response = context.response();
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(DISCOVERY_ID, context.pathParam("id")).put(METHOD, DATABASE_DELETE).put(TABLE, DISCOVERY_TABLE), handler -> {
            try {
                if (handler.succeeded() && handler.result().body()!=null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                    LOGGER.info(" context:{}, status :{}", context.pathParam("id"), "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error(handler.cause().getMessage());
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
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, Constant.DATABASE_GET).put(TABLE, DISCOVERY_TABLE).put("condition", "all"), handler -> {
            try {
                if (handler.succeeded() && handler.result().body() != null) {
                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, SUCCESS).put(RESULT, handler.result().body().getJsonArray(RESULT)).encodePrettily());
                    LOGGER.info(" status :{}", "success");
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error(handler.cause().getMessage());
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
        var id = context.pathParam("id");
        Bootstrap.getVertx().eventBus().<JsonObject>request(DATABASE, new JsonObject().put(METHOD, DATABASE_GET).put(TABLE, DISCOVERY_TABLE).put("condition", "individual").put("column", "discovery_id").put("value", id)
                , handler -> {
                    try {
                        if (handler.succeeded() && handler.result().body() != null) {
                            response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, SUCCESS).put(RESULT, handler.result().body().getJsonArray(RESULT)).encodePrettily());
                            LOGGER.info(" context:{}, status :{}", context.pathParam("id"), "success");
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                            LOGGER.error(handler.cause().getMessage());
                        }
                    } catch (Exception exception) {
                        response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                        LOGGER.error(exception.getMessage());
                    }

                });
    }

    private void runDiscovery(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        try {
            var id = context.pathParam("id");
            var query = "select discovery_id , ip,type,port,username,password,community,version from credential , discovery where discovery_id = \"" + id + "\" and discovery.credential_profile = credential.credential_id;";
            var errors = new ArrayList<String>();
            Promise<JsonObject> promise = Promise.promise();
            Future<JsonObject> future = promise.future();
            Promise<String> promiseQuery = Promise.promise();
            Future<String> futureQuery = promiseQuery.future();
            eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
                try {
                    if (handler.succeeded() && handler.result().body() != null) {
                        if (handler.result().body().getJsonArray(RESULT).isEmpty()) {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "id does not exist in table").encodePrettily());
                            promise.fail("id does not exist in table");
                            LOGGER.error("error occurred :{}", "id does not exist in table");
                        } else {
                            promise.complete(handler.result().body().getJsonArray(RESULT).getJsonObject(0).put("category", "discovery"));
                        }
                    } else {
                        promise.fail(handler.cause().getMessage());
                        LOGGER.error("error occurred :{}", handler.cause().getMessage());
                    }
                } catch (Exception exception) {
                    promise.fail(exception.getMessage());
                    LOGGER.error(exception.getMessage());
                }

            });
            future.onComplete(handler -> {
                try {
                    if (handler.succeeded() && handler.result() !=null) {
                        eventBus.<JsonObject>request(RUN_DISCOVERY_DISCOVERY_ENGINE, handler.result(), run -> {
                            if (run.succeeded() && run.result().body()!=null) {
                                promiseQuery.complete("update discovery set discovery_result = " + "'" + run.result().body() + "'" + " where discovery_id =\"" + id + "\";");
                            } else {
                                errors.add(run.cause().getMessage());
                                var updateQuery = "update discovery set discovery_result = " + "'" + new JsonObject().put(STATUS, FAIL).put(ERROR, run.cause().getMessage()) + "'" + " where discovery_id =\"" + id + "\";";
                                promiseQuery.complete(updateQuery);
                            }
                        });
                    } else {
                       promiseQuery.fail(handler.cause().getMessage());
                    }
                } catch (Exception exception) {
                    promiseQuery.fail(exception.getCause().getMessage());
                    LOGGER.error(exception.getMessage());
                }
            });
            futureQuery.onComplete(handler -> {
                if(handler.succeeded() && handler.result() !=null){
                    eventBus.<JsonObject>request(DATABASE, new JsonObject().put(QUERY, handler.result()).put(METHOD, EXECUTE_QUERY).put("condition", "update"), queryHandler -> {
                        try {
                            if (queryHandler.failed()) {
                                errors.add(queryHandler.cause().getMessage());
                            } else {
                                if (errors.isEmpty()) {
                                    response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                    response.end(new JsonObject().put(STATUS, SUCCESS).put(MESSAGE, "Discovered successfully").encodePrettily());
                                    LOGGER.info("context :{} , status :{}", context.pathParam("id"), SUCCESS);
                                } else {
                                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                                    LOGGER.error("error occurred :{}", errors);
                                }
                            }
                        } catch (Exception exception) {
                            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, exception.getMessage()).encodePrettily());
                            LOGGER.error(exception.getMessage());
                        }

                    });
                }else{
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, handler.cause().getMessage()).encodePrettily());
                    LOGGER.error("error occurred :{}",handler.cause().getMessage());
                }
            });
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "Wrong Json Format").put(ERROR, exception.getMessage()).encodePrettily());
            LOGGER.error(exception.getMessage());
        }

    }

}
