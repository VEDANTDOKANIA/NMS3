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

public class Metric {
    private static final Logger LOGGER = LoggerFactory.getLogger(Metric.class);

    public void init(Router router) {
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT + "/:id").handler(this::validate).handler(this::get);
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT + "/limit/:id").handler(this::validate).handler(this::getPollingData);
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.PUT).path(METRIC_ENDPOINT + "/:id").handler(this::filter).handler(this::validate).handler(this::update);
    }

    private void filter(RoutingContext context) {
        var credentials = new JsonObject();
        var entries = context.getBodyAsJson();
        var keyList = Utils.keyList("metric");
        entries.forEach(value -> {
            if (keyList.contains(value.getKey())) {
                if (credentials.getValue(value.getKey()) instanceof String) {
                    credentials.put(value.getKey(), entries.getString(value.getKey()).trim());
                } else {
                    credentials.put(value.getKey(), entries.getValue(value.getKey()));
                }
            }
        });
        context.setBody(credentials.toBuffer());
        context.next();
    }

    private void validate(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        try {
            String request = context.request().method().toString();
            if ("GET".equals(request)) {
                if (context.pathParam("id") == null) {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, "id is null").encodePrettily());
                    LOGGER.error("error occurred {}", "id is null");
                } else {
                    context.next();
                }
            } else if ("PUT".equals(request)) {
                var errors = new ArrayList<String>();
                var entries = context.getBodyAsJson();
                if ((!entries.containsKey(TIME)) || entries.getInteger(TIME) <= 0 || entries.getInteger(TIME) % 1000 != 0) {
                    errors.add("wrong datatype provided for time or time field is absent");
                }
                if (context.pathParam("id") == null) {
                    errors.add("metric_id is null or wrong data type provided");
                }
                if (errors.isEmpty()) {
                    var query = "select type,metric_group from metric,monitor where metric.monitor_id=monitor.monitor_id and metric_id= " + context.pathParam("id") + ";";
                    eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
                        if (handler.succeeded() && handler.result().body() != null) {
                            var result = handler.result().body().getJsonArray(RESULT).getJsonObject(0);
                            var time = Utils.groupTime(result.getString(TYPE), result.getString(METRIC_GROUP));
                            if (!(time[0] <= entries.getInteger(TIME) && time[1] >= entries.getInteger(TIME))) {
                                errors.add("time is not in proper range :" + time[0] + "-" + time[1]);
                                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                            } else {
                                context.next();
                                LOGGER.info("validation performed successfully :{}", context.request().method());
                            }
                        } else {
                            errors.add(handler.cause().getMessage());
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                            LOGGER.error(handler.cause().getMessage());
                        }
                    });
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                    LOGGER.error("error occurred :{}", errors);
                }
            }
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, "wrong json format").put(ERROR, exception.getCause().getMessage()).encodePrettily());
            LOGGER.error("error occurred :{}", exception.getMessage());
        }
    }

    private void get(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        try {
            String query;
            var type = context.queryParam("type");
            if (!type.isEmpty() && type.get(0).equals("monitor")) {
                query = "select * from metric where monitor_id=" + context.pathParam("id") + ";";
            } else if (context.normalizedPath().equals("/api/Metric")) {
                query = "select * from metric;";
            } else {
                query = "select * from metric where metric_id=" + context.pathParam("id") + ";";
            }
            eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
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
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(MESSAGE, exception.getMessage()).put(STATUS, FAIL).encodePrettily());
            LOGGER.error("error occurred :{}", exception.getMessage());

        }
    }

    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        try {
            var query = "update metric set time =" + context.getBodyAsJson().getInteger(TIME) + " where metric_id = " + context.pathParam("id") + ";";
            eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, EXECUTE_QUERY).put(QUERY, query).put("condition", "update"), handler -> {
                try {
                    if (handler.succeeded() && handler.result().body() != null) {
                        response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                        response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                        eventBus.send(METRIC_SCHEDULER_UPDATE, context.getBodyAsJson());
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
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(MESSAGE, exception.getMessage()).put(STATUS, FAIL).encodePrettily());
            LOGGER.error("error occurred :{}", exception.getMessage());
        }

    }

    private void getPollingData(RoutingContext context) {
        var eventBus = Bootstrap.getVertx().eventBus();
        var response = context.response();
        try {
            Promise<String> promise = Promise.promise();
            Future<String> future = promise.future();
            int limitValue;
            String getQuery = "select type from monitor where monitor_id=" + context.pathParam("id") + ";";
            String query = "select * from polling where monitor_id= idValue and metric_group= \"groupValue\" order by id desc limit limitValue";
            var groups = context.queryParam("group");
            var limit = context.queryParam("limit");
            if (limit.isEmpty()) {
                limitValue = 10;
            } else {
                limitValue = Integer.parseInt(context.queryParam("limit").get(0));
            }
            if (!groups.isEmpty()) {
                query = query.replace("idValue", context.pathParam("id")).replace("groupValue", groups.get(0)).replace("limitValue", String.valueOf(limitValue)) + ";";
                promise.complete(query);
            } else {
                String finalQuery = query;
                eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, getQuery), handler -> {
                    try {
                        if (handler.succeeded() && handler.result().body() != null) {
                            var type = handler.result().body().getJsonArray(RESULT).getJsonObject(0).getString(TYPE);
                            var queryBuilder = new StringBuilder();
                            var metrics = Utils.metricGroup(type);
                            for (int index = 0; index < metrics.size(); index++) {
                                var tempQuery = finalQuery.replace("idValue", context.pathParam("id")).replace("groupValue", metrics.getJsonObject(index).getString("metric.group")).replace("limitValue", String.valueOf(limitValue));
                                queryBuilder.append("( ").append(tempQuery).append(") ").append("union");
                            }
                            queryBuilder.setLength(queryBuilder.length() - 5);
                            queryBuilder.append(";");
                            promise.complete(queryBuilder.toString());
                        } else {
                            promise.fail(handler.cause().getMessage());
                        }
                    } catch (Exception exception) {
                        promise.fail(exception.getCause().getMessage());
                    }
                });
            }
            future.onComplete(completeHandler -> {
                if (completeHandler.succeeded() && completeHandler.result() != null) {
                    eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, completeHandler.result()), handler -> {
                        if (handler.succeeded() && handler.result().body() != null) {
                            response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(handler.result().body().encodePrettily());
                            LOGGER.info("context :{}, status :{}", context.pathParam("id"), "success");
                        } else {
                            response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                            response.end(new JsonObject().put(MESSAGE, handler.cause().getMessage()).put(STATUS, FAIL).encodePrettily());
                            LOGGER.error("error occurred :{}", handler.cause().getMessage());
                        }
                    });
                } else {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, completeHandler.cause().getMessage()).encodePrettily());
                    LOGGER.error(completeHandler.cause().getMessage());
                }
            });
        } catch (Exception exception) {
            response.setStatusCode(500).putHeader(CONTENT_TYPE, HEADER_TYPE);
            response.end(new JsonObject().put(MESSAGE, exception.getMessage()).put(STATUS, FAIL).encodePrettily());
            LOGGER.error("error occurred :{}", exception.getMessage());
        }
    }

}
