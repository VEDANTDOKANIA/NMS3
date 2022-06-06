package com.mindarray.NMS;


public class Constant {
    //End points and responses
    public static final String MONITOR_ENDPOINT = "/monitor";
    public static final String DISCOVERY_ENDPOINT = "/discovery";
    public static final String CREDENTIAL_ENDPOINT = "/credential";
    public static final String METRIC_ENDPOINT = "/metric";
    public static final String HEADER_TYPE = "application/json";
    public static final String CONTENT_TYPE = "content-type";

    //Event Bus consumer
    public static final String METRIC_DATA = "metric_data";
    public static final String DATABASE = "Database";
    public static final String METRIC_SCHEDULER_UPDATE = "metric_scheduler_update";

    //Database methods
    public static final String DATABASE_CREATE = "create";
    public static final String DATABASE_DELETE = "delete";
    public static final String DATABASE_GET = "get";
    public static final String DATABASE_UPDATE = "Update";
    public static final String DATABASE_CHECK = "check";
    public static final String GET_QUERY = "getQuery";
    public static final String EXECUTE_QUERY = "executeQuery";


    //Discovery Database Address
    public static final String RUN_DISCOVERY_DISCOVERY_ENGINE = "runDiscovery";
    public static final String PROVISION_SCHEDULER = "scheduler";
    public static final String SCHEDULER_POLLING = "scheduler_polling";
    public static final String POLLER_DATABASE = "poller_database";
    public static final String MONITOR_SCHEDULER_DELETE = "monitor_scheduler_delete";

    // Json constants
    public static final String IP = "ip";
    public static final String METRIC_GROUP = "metric.group";
    public static final String PORT = "port";
    public static final String TYPE = "type";


    //Credential JSON
    public static final String CREDENTIAL_ID = "credential.id";
    public static final String CREDENTIAL_NAME = "credential.name";
    public static final String CREDENTIAL_PROFILE = "credential.profile";
    public static final String PROTOCOL = "protocol";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String COMMUNITY = "community";
    public static final String VERSION = "version";


    //Discovery Json
    public static final String DISCOVERY_NAME = "discovery.name";
    public static final String DISCOVERY_IP = "ip";
    public static final String DISCOVERY_TYPE = "type";
    public static final String DISCOVERY_PORT = "port";
    public static final String DISCOVERY_ID = "discovery.id";

    //Monitor Json
    public static final String MONITOR_ID = "monitor.id";
    public static final String METRIC_ID = "metric.id";
    public static final String TIME = "time";
    public static final String TIMESTAMP ="timestamp";
    public static final String OBJECT ="object";
    public static final String OBJECTS ="objects";
    public static final String HOST ="host";



    // Constant Messages
    public static final String RESULT = "result";
    public static final String METHOD = "method";
    public static final String SUCCESS = "success";
    public static final String FAIL = "fail";
    public static final String MESSAGE = "message";
    public static final String STATUS = "status";
    public static final String ERROR = "error";
    public static final String QUERY = "query";
    public static final String PARAMETER ="parameter";
    public static final String COLUMN ="column";
    public static final String VALUE ="value";
    public static final String CATEGORY ="category";

    //Constant Table
    public static final String TABLE = "table";
    public static final String DISCOVERY_TABLE = "discovery";
    public static final String CREDENTIAL_TABLE = "credential";
    public static final String MONITOR_TABLE = "monitor";
    public static final String METRIC_TABLE = "metric";
    public static final String POLLING_TABLE ="polling";

    //Metric Groups
    public static final String CPU ="cpu";
    public static final String DISK ="disk";
    public static final String MEMORY="memory";
    public static final String PROCESS ="process";
    public static final String SYSTEM ="system";
    public static final String PING ="ping";
    public static final String INTERFACE ="interface";
}
