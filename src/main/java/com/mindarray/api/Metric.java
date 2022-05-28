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

public class Metric {
    private static final Logger LOGGER = LoggerFactory.getLogger(Metric.class);

    public void init(Router router) {
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT+"/:id").handler(this::validate).handler(this::get);
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT+"/Monitor/:id").handler(this::validate).handler(this::get);
        router.route().method(HttpMethod.GET).path(METRIC_ENDPOINT).handler(this::get);
        router.route().method(HttpMethod.PUT).path(METRIC_ENDPOINT).handler(this::validate).handler(this::update);
    }
    private void validate(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();

        switch (context.request().method().toString()) {
            case "GET" -> {
                if (context.pathParam("id") == null) {
                    response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                    response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, "id is null").encodePrettily());
                    LOGGER.error("error occurred {}", "id is null");
                } else {
                    context.next();
                }
            }
            case "PUT" -> {
                var errors = new ArrayList<String>();
                if ((!context.getBodyAsJson().containsKey(TIME)) || (context.getBodyAsJson().getInteger(TIME) == null)) {
                    errors.add("wrong datatype provided for time or time field is absent");
                }
                if (!context.getBodyAsJson().containsKey(METRIC_ID) || context.getBodyAsJson().getInteger(METRIC_ID) == null) {
                    errors.add("metric_id is null or wrong data type provided");
                }
                   if(errors.isEmpty()){
                       var query ="select type,metric_group from metric,monitor where metric.monitor_id=monitor.monitor_id and metric_id="+context.getBodyAsJson().getInteger(METRIC_ID)+";";
                       eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, GET_QUERY).put(QUERY, query), handler -> {
                           if (handler.succeeded() && handler.result().body()!=null) {
                            var time=   Utils.groupTime(handler.result().body().getJsonArray(RESULT).getJsonObject(0).getString(TYPE),handler.result().body().getJsonArray(RESULT).getJsonObject(0).getString(METRIC_GROUP));
                              if(!(time[0]<=context.getBodyAsJson().getInteger(TIME) && time[1]>=context.getBodyAsJson().getInteger(TIME))) {
                                  errors.add("time is not in proper range");
                                  response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                                  response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                              }else{
                                  context.next();
                              }
                               LOGGER.info("validation performed successfully :{}", context.request().method());
                           } else {
                               errors.add(handler.cause().getMessage());
                               response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                               response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                               LOGGER.error(handler.cause().getMessage());
                           }
                       });
                   }else{
                       response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                       response.end(new JsonObject().put(STATUS, FAIL).put(MESSAGE, errors).encodePrettily());
                       LOGGER.error("error occurred :{}",errors);
                   }


            }

        }
    }

    private void get(RoutingContext context) {
        String query;
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        if(context.normalizedPath().contains("Monitor")){
            query = "select * from metric where monitor_id=" +context.pathParam("id")+";";
        }else if(context.normalizedPath().equals("/api/Metric")){
            query = "select * from metric;";
        }
        else {
            query = "select * from metric where metric_id=" +context.pathParam("id")+";";
        }
       eventBus.<JsonObject>request(DATABASE,new JsonObject().put(METHOD,GET_QUERY).put(QUERY,query),handler->{
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
    private void update(RoutingContext context) {
        var response = context.response();
        var eventBus = Bootstrap.getVertx().eventBus();
        var query = "update metric set time ="+context.getBodyAsJson().getInteger(TIME)+ " where metric_id = " +context.getBodyAsJson().getInteger(METRIC_ID);
        eventBus.<JsonObject>request(DATABASE, new JsonObject().put(METHOD, EXECUTE_QUERY).put(QUERY,query).put("condition","update"), handler -> {
            if (handler.succeeded()) {
                response.setStatusCode(200).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, SUCCESS).encodePrettily());
                eventBus.send(METRIC_SCHEDULER_UPDATE,context.getBodyAsJson());
                LOGGER.info("context:{}, status :{}",context.getBodyAsJson(),"success");
            } else {
                response.setStatusCode(400).putHeader(CONTENT_TYPE, HEADER_TYPE);
                response.end(new JsonObject().put(STATUS, FAIL).put(ERROR, handler.cause().getMessage()).encodePrettily());
                LOGGER.error(handler.cause().getMessage());
            }
        });
    }

}
