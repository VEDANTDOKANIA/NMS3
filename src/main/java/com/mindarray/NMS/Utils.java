package com.mindarray.NMS;

import com.mindarray.ProcessHandler;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

    public static Future<JsonObject> checkAvailability(JsonObject credential) {
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if ((!credential.containsKey("ip")) || credential.getString("ip") == null) {
            errors.add("IP address is null in check availability");
            LOGGER.error("IP address is null in check availability");
        } else {
            var processBuilder = new NuProcessBuilder(Arrays.asList("fping", "-c", "3", "-t", "1000", "-q", credential.getString("ip")));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            var process = processBuilder.start();
            handler.onStart(process);
            try {
                process.waitFor(4000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                errors.add(exception.getCause().getMessage());
                Thread.currentThread().interrupt();
            }
            var result = handler.output();
            if (result == null) {
                errors.add("Request time out occurred");
            } else {
                var pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+\\s+\\:\\s+\\w+/\\w+/%\\w+\\s+\\=\\s+\\d+/\\d+/(\\d+)%");
                var matcher = pattern.matcher(result);
                if (matcher.find()) {
                    if (!matcher.group(1).equals("0")) {
                        errors.add(" packet loss percentage is :" + matcher.group(1));
                    }
                }
            }
        }
        if (errors.isEmpty()) {
            promise.complete(credential);
        } else {
            promise.fail(errors.toString());
        }
        return promise.future();
    }

    public static Future<JsonObject> spwanProcess(JsonObject credential) {
        var output = new JsonObject();
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if (credential == null) {
            errors.add("credential is null");
            LOGGER.error("credential is null");
        } else {
            String encoder = (Base64.getEncoder().encodeToString((credential).toString().getBytes(StandardCharsets.UTF_8)));
            var processBuilder = new NuProcessBuilder(Arrays.asList("./plugin.exe", encoder));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            var process = processBuilder.start();
            handler.onStart(process);
            try {
                process.waitFor(60000, TimeUnit.MILLISECONDS);
            } catch (Exception exception) {
                errors.add(exception.getCause().getMessage());
                Thread.currentThread().interrupt();
            }
            var result = handler.output();
            if (result == null) {
                errors.add("Request time out occurred");
            } else {
                output = new JsonObject(result);
            }
        }
        if(output.getString(STATUS).equals(FAIL)){
            errors.add(output.getValue(ERROR).toString());
        }
        if (errors.isEmpty()) {
            promise.complete(credential.put(RESULT, output));

        } else {
            promise.fail(errors.toString());
        }
        return promise.future();
    }

    public static Future<JsonObject> checkPort(JsonObject credential){
        Promise<JsonObject>  promise = Promise.promise();
        try( var socket = new Socket(credential.getString(IP),credential.getInteger(PORT))){
           promise.complete(new JsonObject().put(STATUS,SUCCESS));
        }catch (Exception exception){
        promise.fail(exception.getMessage());
        }
        return promise.future();
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
