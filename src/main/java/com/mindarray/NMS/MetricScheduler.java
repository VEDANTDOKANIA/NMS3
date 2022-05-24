package com.mindarray.NMS;

import com.mindarray.api.Monitor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
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
      ConcurrentHashMap<String, JsonObject> poller = new ConcurrentHashMap<>();
     eventBus.<JsonObject>localConsumer(PROVISION_SCHEDULER,handler->{
      handler.body().forEach(value ->{
       pollingData.put(value.getKey(), handler.body().getJsonObject(value.getKey()));
          var map = new ConcurrentHashMap<>(handler.body().getJsonObject(value.getKey()).getMap());
          poller.put(value.getKey(), JsonObject.mapFrom(map));
      });
   });


   vertx.setPeriodic(10000,handler->{
       if(poller.size()>0){
           poller.forEach( (key,value)->{
              var timeLeft = value.getInteger("time")-10000;
              if(timeLeft <=0){
                  var originalTime =  pollingData.get(key).getInteger("time");
                  value.put("time",originalTime);
                  eventBus.send(SCHEDULER_POLLING,value);
              }else{
                  value.put("time",timeLeft);
              }
           });
       }
   });

        startPromise.complete();
    }
}
