package com.mindarray.NMS;

import com.mindarray.api.Monitor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

import static com.mindarray.NMS.Constant.*;
public class MetricScheduler extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricScheduler.class);
    @Override
    public void start(Promise<Void> startPromise) {
      var eventBus = vertx.eventBus();
        ConcurrentHashMap<String, JsonObject> pollingData = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> poller = new ConcurrentHashMap<>();
      eventBus.<JsonObject>localConsumer(INITIAL_POLL_DATA,handler ->{
          if(handler.body() != null){
              handler.body().getJsonArray("result").stream().map(JsonObject::mapFrom).forEach(value ->{
                  pollingData.put(value.getString("metric.id"),value.put("category","polling"));
               //   var map = new ConcurrentHashMap<>(value.getMap());
                  poller.put(value.getString("metric.id"), value.getInteger("time"));
              });
          }else {
              LOGGER.error("error occurred :{}","initial polling data handler is null");
          }
      });

     eventBus.<JsonObject>localConsumer(PROVISION_SCHEDULER,handler->{
      handler.body().forEach(value ->{
       pollingData.put(value.getKey(), handler.body().getJsonObject(value.getKey()));
          poller.put(value.getKey(), handler.body().getJsonObject(value.getKey()).getInteger("time"));
      });
   });



   vertx.setPeriodic(10000,handler->{
       if(poller.size()>0){
           poller.forEach( (key,value)->{
              var timeLeft = value-10000;
              if(timeLeft <=0){
                  var originalTime =  pollingData.get(key).getInteger("time");
                  poller.put(key,originalTime);
                  eventBus.send(SCHEDULER_POLLING,pollingData.get(key));
              }else{
                  poller.put(key,timeLeft);
              }
           });
       }
   });
        startPromise.complete();
    }
}
