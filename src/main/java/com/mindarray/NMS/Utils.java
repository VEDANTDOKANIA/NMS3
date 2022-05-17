package com.mindarray.NMS;

import com.mindarray.api.Discovery;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mindarray.NMS.Constant.*;
public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    public static Future<JsonObject> checkAvailability(JsonObject credential) {
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if( (!credential.containsKey("ip")) || credential.getString("ip") == null){
            errors.add("IP address is null in check availability");
            LOGGER.error("IP address is null in check availability");
        }else{
            var processBuilder = new NuProcessBuilder(Arrays.asList("fping","-c","3","-t","1000","-q",credential.getString("ip")));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            var process = processBuilder.start();
            handler.onStart(process);
            try {
                process.waitFor(4000, TimeUnit.MILLISECONDS);
            }catch (InterruptedException exception){
                errors.add(exception.getCause().getMessage());
                Thread.currentThread().interrupt();
            }
           var result = handler.output();
           if(result == null){
               errors.add("Request time out occurred");
           }else {
               var pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+\\s+\\:\\s+\\w+/\\w+/%\\w+\\s+\\=\\s+\\d+/\\d+/(\\d+)%");
               var matcher = pattern.matcher(result);
               if (matcher.find()) {
                   if (!matcher.group(1).equals("0")) {
                       errors.add("Loss percentage is :" + matcher.group(1));
                   }
               }
           }
        }
        if(errors.isEmpty()){
            promise.complete(credential);
        }else{
            promise.fail(errors.toString());
        }
        return promise.future();
    }
    public static Future<JsonObject> spwanProcess(JsonObject credential){
        var output = new JsonObject();
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if (credential == null){
            errors.add("credential is null");
            LOGGER.error("credential is null");
        }else{
            String encoder = (Base64.getEncoder().encodeToString((credential).toString().getBytes(StandardCharsets.UTF_8)));
            var processBuilder = new NuProcessBuilder(Arrays.asList("./plugin.exe",encoder));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            var process = processBuilder.start();
            handler.onStart(process);
            try {
                process.waitFor(8000, TimeUnit.MILLISECONDS);
            }catch (Exception exception){
                errors.add(exception.getCause().getMessage());
                Thread.currentThread().interrupt();
            }
            var result = handler.output();
            if(result == null){
                errors.add("Request time out occurred");
            }else{
                output = new JsonObject(result);
            }
        }
        if(errors.isEmpty()){
            promise.complete(credential.put("result",output));
        }else{
            promise.fail(errors.toString());
        }
        return promise.future();
    }
}
