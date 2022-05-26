package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import com.mindarray.ProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mindarray.NMS.Constant.*;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    public static JsonObject checkAvailability(JsonObject entries) {
        var errors = new ArrayList<String>();
           if ((!entries.containsKey("ip")) || entries.getString("ip") == null) {
               errors.add("Ip address is null in check availability");
               LOGGER.error("Ip address is null in check availability");
               return entries.put(STATUS,FAIL).put(ERROR,"Ip address is null in check availability");
           } else {
               var processBuilder = new NuProcessBuilder(Arrays.asList("fping", "-c", "3", "-t", "1000", "-q", entries.getString("ip")));
               var handler = new ProcessHandler();
               processBuilder.setProcessListener(handler);
               var process = processBuilder.start();
               try{
                  // handler.onStart(process);
                   process.waitFor(4000, TimeUnit.MILLISECONDS);
                   var result = handler.output();
                   if (result == null) {
                       errors.add("request time out occurred");
                   } else {
                       var pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+\\s+\\:\\s+\\w+/\\w+/%\\w+\\s+\\=\\s+\\d+/\\d+/(\\d+)%");
                       var matcher = pattern.matcher(result);
                       if (matcher.find()) {
                           if (!matcher.group(1).equals("0")) {
                               errors.add(" packet loss percentage is :" + matcher.group(1));
                           }
                       }
                   }
               }catch (Exception exception){
                   errors.add(exception.getCause().getMessage());
               }finally {
                   process.destroy(true);
               }
           }
        if(errors.isEmpty()){
            return entries.put(STATUS,SUCCESS);
        }else{
            return entries.put(STATUS,FAIL).put(ERROR,errors);
        }
    }

    public static JsonObject spawnProcess(JsonObject entries) {
            var output = new JsonObject();
            var errors = new ArrayList<String>();
            NuProcess process = null;
          if (entries == null) {
              LOGGER.error("entries is null");
              return entries.put(STATUS, FAIL).put(ERROR, "entries is null");
          }
            try{
                    var encoder = (Base64.getEncoder().encodeToString((entries).toString().getBytes(StandardCharsets.UTF_8)));
                    var processBuilder = new NuProcessBuilder(Arrays.asList("./plugin.exe", encoder));
                    var handler = new ProcessHandler();
                    processBuilder.setProcessListener(handler);
                    process = processBuilder.start();
                    process.waitFor(30000, TimeUnit.MILLISECONDS);
                    var result = handler.output();
                    if (result == null) {
                        errors.add("request time out occurred");
                    } else {
                        output = new JsonObject(result);
                    }
                    if(output.containsKey(STATUS) && output.getString(STATUS).equals(FAIL)){
                        errors.add(output.getString(ERROR));
                    }
            }catch (Exception exception){
                errors.add(exception.getMessage());
            }finally {
                process.destroy(true);
            }
            if (errors.isEmpty()) {
                entries.put(STATUS,SUCCESS).put(RESULT,output);
            } else {
                entries.put(STATUS,FAIL).put(ERROR,errors);
            }
            return entries;
    }

    public static JsonObject checkPort(JsonObject entries){
           if(entries != null && entries.containsKey(TYPE) && entries.getString(TYPE).equals("snmp")){
               return entries.put(STATUS,SUCCESS);
           }else{
               NetClient client = Bootstrap.getVertx().createNetClient();
               client.connect(entries.getInteger(PORT),entries.getString(IP),asyncResult ->{
                   if(asyncResult.succeeded()){
                       entries.put(STATUS,SUCCESS);
                   }else {
                       entries.put(STATUS,FAIL).put(ERROR,asyncResult.cause().getMessage());
                   }
               });
               return entries;
           }
    }

    public static JsonArray metricGroup(String type) {
        var metric = new JsonArray();
        switch (type) {
            case "linux" -> {
                metric.add(new JsonObject().put("metric.group", "cpu").put("time", 50000));
                metric.add(new JsonObject().put("metric.group", "disk").put("time", 60000));
                metric.add(new JsonObject().put("metric.group", "memory").put("time", 80000));
                metric.add(new JsonObject().put("metric.group", "process").put("time", 50000));
                metric.add(new JsonObject().put("metric.group", "system").put("time", 90000));
                metric.add(new JsonObject().put("metric.group","ping").put("time",60000));
            }
            case "windows" -> {
                metric.add(new JsonObject().put("metric.group", "cpu").put("time", 80000));
                metric.add(new JsonObject().put("metric.group", "disk").put("time", 100000));
                metric.add(new JsonObject().put("metric.group", "memory").put("time", 110000));
                metric.add(new JsonObject().put("metric.group", "process").put("time", 80000));
                metric.add(new JsonObject().put("metric.group", "system").put("time", 120000));
                metric.add(new JsonObject().put("metric.group","ping").put("time",60000));
            }
            case "snmp" -> {
                metric.add(new JsonObject().put("metric.group", "system").put("time", 80000));
                metric.add(new JsonObject().put("metric.group", "interface").put("time", 50000));
                metric.add(new JsonObject().put("metric.group","ping").put("time",60000));
            }
        }
        return metric;
    }
}
