package com.mindarray.NMS;

import com.mindarray.ProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mindarray.NMS.Constant.*;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    public static JsonObject checkAvailability(JsonObject entries) {
        var errors = new ArrayList<String>();
        if ((!entries.containsKey("ip")) || entries.getString("ip") == null) {
            errors.add("ip address is null in check availability");
            LOGGER.error("error occurred :{}","ip address is null in check availability");
            return entries.put(STATUS, FAIL).put(ERROR, "ip address is null in check availability");
        } else {
            var processBuilder = new NuProcessBuilder(Arrays.asList("fping", "-c", "3", "-t", "1000", "-q", entries.getString("ip")));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            var process = processBuilder.start();
            try {
                process.waitFor(4000, TimeUnit.MILLISECONDS);
                var result = handler.output();
                if (result == null) {
                    errors.add("request time out occurred");
                } else {
                    var pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+\\s+:\\s+\\w+/\\w+/%\\w+\\s+=\\s+\\d+/\\d+/(\\d+)%");
                    var matcher = pattern.matcher(result);
                    if (matcher.find() && (!matcher.group(1).equals("0"))) {
                            errors.add(" packet loss percentage is :" + matcher.group(1));
                    }
                }
            } catch (Exception exception) {
                errors.add(exception.getCause().getMessage());
            } finally {
                process.destroy(true);
            }
        }
        if (errors.isEmpty()) {
            return entries.put(STATUS, SUCCESS);
        } else {
            return entries.put(STATUS, FAIL).put(ERROR, errors);
        }
    }

    public static JsonObject spawnProcess(JsonObject entries) {
        var output = new JsonObject();
        var errors = new ArrayList<String>();
        NuProcess process = null;
        if (entries == null) {
            LOGGER.error("error occurred :{}","entries is null");
            return new JsonObject().put(STATUS, FAIL).put(ERROR, "entries is null");
        }
        try {
            var encoder = (Base64.getEncoder().encodeToString((entries).toString().getBytes(StandardCharsets.UTF_8)));
            var processBuilder = new NuProcessBuilder(Arrays.asList("./com.mindarray.nms", encoder));
            var handler = new ProcessHandler();
            processBuilder.setProcessListener(handler);
            process = processBuilder.start();
            process.waitFor(6000, TimeUnit.MILLISECONDS);
            var result = handler.output();
            if (result == null) {
                errors.add("request time out occurred");
            } else {
                output = new JsonObject(result);
            }
            if (output.containsKey(STATUS) && output.getString(STATUS).equals(FAIL)) {
                errors.add(output.getString(ERROR));
            }
        } catch (Exception exception) {
            errors.add(exception.getMessage());
        } finally {
            if(process != null){
                process.destroy(true);
            }
        }
        if (errors.isEmpty()) {
            entries.put(STATUS, SUCCESS).put(RESULT, output);
        } else {
            entries.put(STATUS, FAIL).put(ERROR, errors);
        }
        return entries;
    }

    public static JsonObject checkPort(JsonObject entries) {
        if( entries == null ||entries.getString(IP) == null || entries.getString(TYPE) ==null || entries.getInteger(PORT) ==null){
            return new JsonObject().put(STATUS,FAIL).put(MESSAGE,"entries is null");
        }else{
            if ( entries.containsKey(TYPE) && entries.getString(TYPE).equals("snmp")) {
                return entries.put(STATUS, SUCCESS);
            } else {
                try( var socket = new Socket(entries.getString(IP),entries.getInteger(PORT))){
                    entries.put(STATUS,SUCCESS);
                }catch (Exception exception){
                    entries.put(STATUS,FAIL).put(MESSAGE,exception.getMessage());
                }
            }
            return entries;
        }
    }

    public static JsonArray metricGroup(String type) {
        var metric = new JsonArray();
        if (type.equals("linux")) {
            metric.add(new JsonObject().put(METRIC_GROUP, CPU).put(TIME, 60000));
            metric.add(new JsonObject().put(METRIC_GROUP, DISK).put(TIME, 70000));
            metric.add(new JsonObject().put(METRIC_GROUP, MEMORY).put(TIME, 80000));
            metric.add(new JsonObject().put(METRIC_GROUP, PROCESS).put(TIME, 60000));
            metric.add(new JsonObject().put(METRIC_GROUP, SYSTEM).put(TIME, 100000));
            metric.add(new JsonObject().put(METRIC_GROUP, PING).put(TIME, 60000));
        } else if (type.equals("windows")) {
            metric.add(new JsonObject().put(METRIC_GROUP, CPU).put(TIME, 80000));
            metric.add(new JsonObject().put(METRIC_GROUP, DISK).put(TIME, 100000));
            metric.add(new JsonObject().put(METRIC_GROUP, MEMORY).put(TIME, 110000));
            metric.add(new JsonObject().put(METRIC_GROUP, PROCESS).put(TIME, 80000));
            metric.add(new JsonObject().put(METRIC_GROUP, SYSTEM).put(TIME, 120000));
            metric.add(new JsonObject().put(METRIC_GROUP, PING).put(TIME, 60000));
        } else if (type.equals("snmp")) {
            metric.add(new JsonObject().put(METRIC_GROUP, SYSTEM).put(TIME, 80000));
            metric.add(new JsonObject().put(METRIC_GROUP, INTERFACE).put(TIME, 70000));
            metric.add(new JsonObject().put(METRIC_GROUP, PING).put(TIME, 60000));
        }
        return metric;
    }

    public static int[] groupTime(String type, String metricGroup) {
        int[] time = new int[2];
        if (type.equals("linux")) {
            if (metricGroup.equals(CPU)) {
                time[0] = 60000;
                time[1] = 86300000;
            } else if (metricGroup.equals(DISK)) {
                time[0] = 70000;
                time[1] = 86400000;
            } else if (metricGroup.equals(MEMORY)) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals(PROCESS)) {
                time[0] = 90000;
                time[1] = 86400000;
            } else if (metricGroup.equals(SYSTEM)) {
                time[0] = 100000;
                time[1] = 86400000;
            } else if (metricGroup.equals(PING)) {
                time[0] = 60000;
                time[1] = 86400000;
            }
        } else if (type.equals("windows")) {
            if (metricGroup.equals(CPU)) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals(DISK)) {
                time[0] = 100000;
                time[1] = 86400000;
            } else if (metricGroup.equals(MEMORY)) {
                time[0] = 110000;
                time[1] = 86400000;
            } else if (metricGroup.equals(PROCESS)) {
                time[0] = 80000;
                time[1] = 86300000;
            } else if (metricGroup.equals(SYSTEM)) {
                time[0] = 120000;
                time[1] = 86400000;
            } else if (metricGroup.equals(PING)) {
                time[0] = 60000;
                time[1] = 86400000;
            }

        } else if (type.equals("snmp")) {
            if (metricGroup.equals(SYSTEM)) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals(INTERFACE)) {
                time[0] = 70000;
                time[1] = 86400000;
            } else if (metricGroup.equals(PING)) {
                time[0] = 60000;
                time[1] = 86400000;
            }
        }
        return time;
    }

    public static List<String> keyList(String api) {
        var keys = new ArrayList<String>();
        if (api.equals(DISCOVERY_TABLE)) {
            keys.add(DISCOVERY_NAME);
            keys.add(DISCOVERY_ID);
            keys.add(IP);
            keys.add(TYPE);
            keys.add(PORT);
            keys.add(CREDENTIAL_PROFILE);
            keys.add(CREDENTIAL_ID);
        } else if (api.equals(CREDENTIAL_TABLE)) {
            keys.add(CREDENTIAL_NAME);
            keys.add(CREDENTIAL_ID);
            keys.add(PROTOCOL);
            keys.add(USERNAME);
            keys.add(PASSWORD);
            keys.add(CREDENTIAL_PROFILE);
            keys.add(COMMUNITY);
            keys.add(VERSION);
        } else if (api.equals(MONITOR_TABLE)) {
            keys.add(IP);
            keys.add(TYPE);
            keys.add(PORT);
            keys.add(MONITOR_ID);
            keys.add(CREDENTIAL_ID);
            keys.add(CREDENTIAL_PROFILE);
            keys.add(OBJECTS);
            keys.add(HOST);
        } else if (api.equals(METRIC_TABLE)) {
            keys.add(TIME);
            keys.add(METRIC_ID);
        }
        return keys;
    }
}
