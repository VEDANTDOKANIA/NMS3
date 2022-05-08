package com.mindarray.NMS;

public class Constant {
    public static final String SELECT = "Select column from table where condition";
    public static final String INSERT = "insert into table columns data ;";

    //Port Constants
    public static final int HTTP_PORT = 9999;
    public static final int SSH_PORT =22;
    public static final  int WINRM_PORT =5985;
    public static final int SNMP_PORT =161;

    //Event Bus Constants
    public static final String DISCOVERY_ENDPOINT = "/Discovery";
    public static final String CREDENTIAL_ENDPOINT ="/Credential";
    public static final String API_SERVER_DISCOVERY_DISCOVERY_ADDRESS = "discovery";
    public static  final String DISCOVERY_DATABASE_CHECK_PING = "checkPing";
    public static  final String DISCOVERY_DATABASE_ADD_DATA = "addData";
    public static final String HEADER_TYPE = "application/json";
    public static final String DISCOVERY_DATABASE_CHECK_DISCOVERY ="discoveryName";



    // Json constants
    public static final String IP_ADDRESS= "ip.address";
    public static final String METRIC_GROUP ="metric.group";
    public static final String PORT = "port" ;
    public static final String METRIC_TYPE ="metric.type";
    public static  final String COMMUNITY = "community";
    public static final String VERSION ="version";
    public static final String USERNAME ="username" ;
    public static final String PASSWORD ="password";
    public static final String STATUS ="status" ;
    public static final String ERROR ="error";


    // Constant Messages

    public static final String SUCCESS ="success" ;

    public static final String FAIL ="fail" ;
}
