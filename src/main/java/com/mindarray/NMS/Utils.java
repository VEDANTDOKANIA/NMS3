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
            return entries.put(STATUS, FAIL).put(ERROR, "Ip address is null in check availability");
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
                    var pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+\\s+\\:\\s+\\w+/\\w+/%\\w+\\s+\\=\\s+\\d+/\\d+/(\\d+)%");
                    var matcher = pattern.matcher(result);
                    if (matcher.find()) {
                        //TODO ek karna hain
                        if (!matcher.group(1).equals("0")) {
                            errors.add(" packet loss percentage is :" + matcher.group(1));
                        }
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
            LOGGER.error("entries is null");
            return entries.put(STATUS, FAIL).put(ERROR, "entries is null");
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
            process.destroy(true);
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
            metric.add(new JsonObject().put("metric.group", "cpu").put("time", 60000));
            metric.add(new JsonObject().put("metric.group", "disk").put("time", 70000));
            metric.add(new JsonObject().put("metric.group", "memory").put("time", 80000));
            metric.add(new JsonObject().put("metric.group", "process").put("time", 60000));
            metric.add(new JsonObject().put("metric.group", "system").put("time", 100000));
            metric.add(new JsonObject().put("metric.group", "ping").put("time", 60000));
        } else if (type.equals("windows")) {
            metric.add(new JsonObject().put("metric.group", "cpu").put("time", 80000));
            metric.add(new JsonObject().put("metric.group", "disk").put("time", 100000));
            metric.add(new JsonObject().put("metric.group", "memory").put("time", 110000));
            metric.add(new JsonObject().put("metric.group", "process").put("time", 80000));
            metric.add(new JsonObject().put("metric.group", "system").put("time", 120000));
            metric.add(new JsonObject().put("metric.group", "ping").put("time", 60000));
        } else if (type.equals("snmp")) {
            metric.add(new JsonObject().put("metric.group", "system").put("time", 80000));
            metric.add(new JsonObject().put("metric.group", "interface").put("time", 70000));
            metric.add(new JsonObject().put("metric.group", "ping").put("time", 60000));
        }
        return metric;
    }

    public static int[] groupTime(String type, String metricGroup) {
        int[] time = new int[2];
        if (type.equals("linux")) {
            if (metricGroup.equals("cpu")) {
                time[0] = 60000;
                time[1] = 86300000;
            } else if (metricGroup.equals("disk")) {
                time[0] = 70000;
                time[1] = 86400000;
            } else if (metricGroup.equals("memory")) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals("process")) {
                time[0] = 90000;
                time[1] = 86400000;
            } else if (metricGroup.equals("system")) {
                time[0] = 100000;
                time[1] = 86400000;
            } else if (metricGroup.equals("ping")) {
                time[0] = 60000;
                time[1] = 86400000;
            }
        } else if (type.equals("windows")) {
            if (metricGroup.equals("cpu")) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals("disk")) {
                time[0] = 100000;
                time[1] = 86400000;
            } else if (metricGroup.equals("memory")) {
                time[0] = 110000;
                time[1] = 86400000;
            } else if (metricGroup.equals("process")) {
                time[0] = 80000;
                time[1] = 86300000;
            } else if (metricGroup.equals("system")) {
                time[0] = 120000;
                time[1] = 86400000;
            } else if (metricGroup.equals("ping")) {
                time[0] = 60000;
                time[1] = 86400000;
            }

        } else if (type.equals("snmp")) {
            if (metricGroup.equals("system")) {
                time[0] = 80000;
                time[1] = 86400000;
            } else if (metricGroup.equals("interface")) {
                time[0] = 70000;
                time[1] = 86400000;
            } else if (metricGroup.equals("ping")) {
                time[0] = 60000;
                time[1] = 86400000;
            }
        }
        return time;
    }

    public static ArrayList<String> keyList(String api) {
        var keys = new ArrayList<String>();
        if (api.equals("discovery")) {
            keys.add("discovery.name");
            keys.add("discovery.id");
            keys.add("ip");
            keys.add("type");
            keys.add("port");
            keys.add("credential.profile");
            keys.add("credential.id");
        } else if (api.equals("credential")) {
            keys.add("credential.name");
            keys.add("credential.id");
            keys.add("protocol");
            keys.add("username");
            keys.add("password");
            keys.add("credential.profile");
            keys.add("community");
            keys.add("version");
        } else if (api.equals("monitor")) {
            keys.add("ip");
            keys.add("type");
            keys.add("port");
            keys.add("monitor.id");
            keys.add("credential.id");
            keys.add("credential.profile");
            keys.add("objects");
            keys.add("host");
        } else if (api.equals("metric")) {
            keys.add("time");
            keys.add("metric.id");
        }
        return keys;
    }
}
