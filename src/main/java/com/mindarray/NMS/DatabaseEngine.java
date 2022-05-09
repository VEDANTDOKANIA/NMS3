package com.mindarray.NMS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Base64;

import static com.mindarray.NMS.Constant.*;

public class DatabaseEngine extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseEngine.class);
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        var eventBus = vertx.eventBus();
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_CHECK_NAME, handler ->{
            var result = check("credential","name",handler.body().getString(CREDENTIAL_NAME));
            result.onComplete(context ->{
                if(result.succeeded()){
                    handler.reply(handler.body().put(STATUS,SUCCESS));
                }else{
                    handler.reply(handler.body().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }

            });
        });
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_CREATE,handler ->{
            if(handler.body() != null){
                var result = createCredential(handler.body());
                result.onComplete(completeHandler ->{
                    handler.reply(completeHandler.result());
                });
            }else{
                LOGGER.error("Handler received null during create");
                handler.reply( new JsonObject().put(STATUS,FAIL).put(ERROR,"Handler received null"));
            }


        });
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_DELETE,handler ->{
            var result =delete("credential","name",handler.body().getString(CREDENTIAL_NAME));
            result.onComplete(completeHandler ->{
                if(result.succeeded()){
                    handler.reply(result.result());
                }else{
                    handler.reply(handler.body().put(ERROR,result.cause().getMessage()).put(STATUS,FAIL));
                }

            });
        });
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_GET,handler ->{
          var result = getCredential("all",null,null);
          result.onComplete( completeHandler ->{
              handler.reply(result.result());
          });
        });
        eventBus.<String>localConsumer(CREDENTIAL_DATABASE_CHECK_ID,handler ->{
            var result = check("credential","id",handler.body());
            result.onComplete(context ->{
                if(result.succeeded()){
                    handler.reply( result.result());
                }else{
                    handler.reply(new JsonObject().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }
            });
        });
        eventBus.<String>localConsumer(CREDENTIAL_DATABASE_GET_ID,handler ->{
            var result = getCredential("individual","id",handler.body());
            result.onComplete( completeHandler ->{
                handler.reply(result.result());
            });
        });
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_CHECK_MULTIPLE,handler ->{
            var idCheck = check("credential","id",handler.body().getString(CREDENTIAL_ID));
            var errors = new ArrayList<String>();
            if(handler.body().containsKey(CREDENTIAL_NAME)){
                var nameCheck =check("credential","name",handler.body().getString(CREDENTIAL_NAME));
                nameCheck.onComplete(completeHandler ->{
                    if(completeHandler.succeeded()){
                        errors.add("Name is not unique");
                    }
                });
            }
            idCheck.onComplete( completeHandler ->{
                if(idCheck.succeeded()){
                    var protocol = getCredential("individual","id",handler.body().getString(CREDENTIAL_ID));
                    protocol.onComplete(protocolHandler ->{
                        if(protocolHandler.succeeded()){
                            var protocolValue = protocol.result().getJsonArray("result").getJsonObject(0).getString(CREDENTIAL_PROTOCOl);
                            if(protocolValue.equals("snmp")) {
                                if (handler.body().containsKey(USERNAME) || handler.body().containsKey(PASSWORD)) {
                                    errors.add("Username and password does not comes in snmp protocol");
                                }
                            } else {
                                    if(handler.body().containsKey(COMMUNITY) || handler.body().containsKey(VERSION)){
                                        errors.add("Community and version does not comes in "+ protocolValue);
                                    }
                                }
                        }else{
                            errors.add(protocolHandler.cause().getMessage());
                        }
                    });
                }else{
                    errors.add(idCheck.cause().getMessage());
                }

            });
        });
        eventBus.<JsonObject>localConsumer(CREDENTIAL_DATABASE_UPDATE,handler ->{
            var result = update("credential",handler.body());
            result.onComplete( completeHandler ->{
                if(result.succeeded()){
                    handler.reply(result.result());
                }else{
                    handler.reply(handler.body().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }
            });
        });


        eventBus.<String>localConsumer(DISCOVERY_DATABASE_CHECK_ID,handler ->{
            var result = check("discovery","id",handler.body());
            result.onComplete(context ->{
                if(result.succeeded()){
                    handler.reply( result.result());
                }else{
                    handler.reply(new JsonObject().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }
            });
        });
        eventBus.<String>localConsumer(DISCOVERY_DATABASE_GET_ID,handler ->{
            var result = getDiscovery("individual","id",handler.body());
            result.onComplete( completeHandler ->{
               if(result.succeeded()){
                   handler.reply(result.result());
               }else{
                   handler.reply( new JsonObject().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
               }
            });
        });
        eventBus.<JsonObject>localConsumer(DISCOVERY_DATABASE_CHECK_NAME, handler ->{
            var name = check("discovery","name",handler.body().getString(DISCOVERY_NAME));
            var profile = check("credential","id",handler.body().getString(CREDENTIAL_PROFILE));
            CompositeFuture.all(name,profile).onComplete( completeHandler ->{
                if(completeHandler.succeeded()){
                    handler.reply(handler.body().put(STATUS,SUCCESS));
                }else{
                        handler.reply(handler.body().put(STATUS,FAIL).put(ERROR,completeHandler.cause().getMessage()));
                }
            });
            });
        eventBus.<JsonObject>localConsumer(DISCOVERY_DATABASE_CREATE,handler ->{
            var result = createDiscovery(handler.body());
            result.onComplete(context ->{
                if(result.succeeded()){
                    handler.reply(result.result());
                }else if(result.failed()){
                    handler.reply(handler.body().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }
            });

        });
        eventBus.<JsonObject>localConsumer(DISCOVERY_DATABASE_DELETE,handler -> {
            var result = delete("discovery", "id", handler.body().getString(DISCOVERY_NAME));
            result.onComplete(completeHandler -> {
                if (result.succeeded()) {
                    handler.reply(result.result());
                } else {
                    handler.reply(handler.body().put(ERROR, result.cause().getMessage()).put(STATUS, FAIL));
                }
            });
                });
        eventBus.<JsonObject>localConsumer(DISCOVERY_DATABASE_GET,handler ->{
            var result = getDiscovery("all",null,null);
            result.onComplete( completeHandler ->{
                if(result.succeeded()){
                    handler.reply(result.result());
                }else{
                    handler.reply(handler.body().put(ERROR,result.cause().getMessage()).put(STATUS,FAIL));
                }
            });
        });
        eventBus.<JsonObject>localConsumer(DISCOVERY_DATABASE_UPDATE,handler ->{
            var result = update("discovery",handler.body());
            result.onComplete( completeHandler ->{
                if(result.succeeded()){
                    handler.reply(result.result());
                }else{
                    handler.reply(handler.body().put(STATUS,FAIL).put(ERROR,result.cause().getMessage()));
                }
            });
        });



        startPromise.complete();
    }


    private Connection connect() {
        Connection connection = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/", "vedant.dokania", "Mind@123");
            LOGGER.info("Database Connection Successful");
        } catch (Exception e) {
            LOGGER.error("Exception Occurred :" + e.getMessage());
        }
        return connection;
    }
    private String generate() {
        SecureRandom random = new SecureRandom();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }
    private Future<JsonObject> check(String table , String column , String data){
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if( table ==null || column == null || data ==null){
           errors.add("null occurred");
        }else{
              Bootstrap.getVertx().<JsonObject>executeBlocking( handler ->{
                  var entries = new JsonObject();
                try(var connection = connect();){
                    var statement = connection.createStatement();
                    statement.execute("use nms");
                    var query = "select exists(select *  from "+ table + " where " + column +"=\""+data+"\");";
                    var resultSet = statement.executeQuery(query);
                    while (resultSet.next()){
                        if(column.equals("name")){
                            if(resultSet.getInt(1)==1){
                                errors.add(table +"." +column +" is not unique");
                            }
                        }else{
                            if(resultSet.getInt(1)==0){
                                errors.add(table + "." +column +" does not exists in table ");
                            }
                        }

                    }
                }catch (Exception exception){
                    errors.add(exception.getCause().getMessage());
                    LOGGER.error(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler ->{
                if(errors.isEmpty()){
                    promise.complete( new JsonObject().put(STATUS,SUCCESS));
                }else{
                    promise.fail(String.valueOf(errors));
                }
                LOGGER.info("Create Function executed successfully");
            });
        }
       return promise.future();
    }
    private Future<JsonObject> delete(String table ,String column ,String data){
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if( table ==null || column == null || data ==null){
            errors.add("null occurred");
        }else{
            Bootstrap.getVertx().executeBlocking(handler ->{
                try (var connection = connect()){
                    var statement = connection.createStatement();
                    statement.execute("use nms");
                    var query ="Delete from " + table +" where " + column + " = " +"\""+data+"\";";
                    statement.execute(query);
                }catch (Exception exception){
                    errors.add(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete( completeHandler ->{
                if(errors.isEmpty()){
                    promise.complete(new JsonObject().put(STATUS,SUCCESS));
                }else{
                    promise.fail(errors.toString());
                }
            });
        }
        return promise.future();
    }
    private Future<JsonObject> createCredential(JsonObject credential){
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if(credential.isEmpty()){
            errors.add("Empty Credentials");
            promise.complete(new JsonObject().put(STATUS,FAIL).put(ERROR,errors));
        }else{
            Bootstrap.vertx.executeBlocking(handler ->{
                String id = generate();
                try(var connection = connect()){
                    var statement = connection.createStatement();
                    statement.execute("use nms");
                    String query ;
                    if(credential.getString(PROTOCOl).equals("snmp")){
                        query =" insert into credential (id,name,protocol,community,version)values (" + "\"" + id +"\"," +"\""+
                                credential.getString(CREDENTIAL_NAME) + "\",\"" + credential.getString(PROTOCOl) +"\"," +"\""+
                                credential.getString(COMMUNITY) +"\"," +"\"" +credential.getString(VERSION) +"\");";
                    }else{
                        query =" insert into credential (id,name,protocol,username,password) values (" + "\"" + id +"\"," +"\""+
                                credential.getString(CREDENTIAL_NAME) + "\",\"" + credential.getString(PROTOCOl) +"\"," +"\""+
                                credential.getString(USERNAME) +"\"," +"\"" +credential.getString(PASSWORD) +"\");";
                    }
                    statement.execute(query);
                }catch (Exception exception){
                    errors.add(exception.getCause().getMessage());
                }
                handler.complete(id);
            }).onComplete( completeHandler ->{
                if(errors.isEmpty()){
                    credential.put(STATUS,SUCCESS);
                    credential.put(MESSAGE,"Your unique id is : "+ completeHandler.result());
                }else{
                    credential.put(STATUS,FAIL);
                    credential.put(ERROR,errors);

                }
                promise.complete(credential);
            });
        }
      return promise.future();
    }
    private Future<JsonObject> getCredential(String condition ,String column ,String value){
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var result = new JsonObject();
        String query ;
        if(condition.equals("all")){
            query = "Select id,name,protocol,username,password,community,version from credential ;";
        }else{
            query = "Select id,name,protocol,username,password,community,version from credential where " + column +"= \""+value+"\";";
        }
        Bootstrap.getVertx().executeBlocking(handler ->{
            var credentials = new JsonArray();

            try(var connection = connect()){
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var resultSet = statement.executeQuery(query);
                while(resultSet.next()){
                    var data = new JsonObject();
                    data.put(CREDENTIAL_PROFILE,resultSet.getString(1));
                    data.put(CREDENTIAL_NAME,resultSet.getString(2));
                    data.put(CREDENTIAL_PROTOCOl,resultSet.getString(3));
                    if(data.getString(CREDENTIAL_PROTOCOl).equals("snmp")){
                        data.put(COMMUNITY,resultSet.getString(6));
                        data.put(VERSION,resultSet.getString(7));
                    }else{
                        data.put(USERNAME,resultSet.getString(4));
                        data.put(PASSWORD,resultSet.getString(5));
                    }
                    credentials.add(data);
                }
            }catch (Exception exception){
                error.add(exception.getCause().getMessage());
            }
            handler.complete(credentials);

        }).onComplete( completeHandler ->{
            if(error.isEmpty()){
                result.put("result",completeHandler.result());
                result.put(STATUS,SUCCESS);
            }else{
                result.put(STATUS,FAIL);
                result.put(ERROR,error);
            }
            promise.complete(result);
        });
   return promise.future();
    }
    private Future<JsonObject> createDiscovery(JsonObject credential){
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        credential.stream().forEach(value ->{
            if(credential.getValue(value.getKey())==null){
                errors.add(value.getKey()+ " is null");
            }
        });
        if(!errors.isEmpty()) {
            promise.fail(errors.toString());
        }
        var id= generate();
        Bootstrap.getVertx().executeBlocking(handler ->{
            try(var connection = connect()){
                var statement = connection.createStatement();
                statement.execute("use nms");
                var query = "insert into discovery (id,name,ip,type,credential_profile,port) values (\"" + id +"\","
                +"\""+ credential.getString(DISCOVERY_NAME)+"\",\"" +credential.getString(DISCOVERY_IP)+"\",\""+
                credential.getString(DISCOVERY_TYPE)+"\",\""+ credential.getString(CREDENTIAL_PROFILE)+"\"," +
                credential.getInteger(DISCOVERY_PORT)+");";
                statement.execute(query);
            }catch (Exception exception){
                errors.add(exception.getCause().getMessage());
            }
            handler.complete(id);
        }).onComplete( completeHandler ->{
            if(errors.isEmpty()){
                promise.complete(credential.put(STATUS,SUCCESS).put(MESSAGE,"Your Unique id is : "+completeHandler.result()));
            }else{
                promise.fail(errors.toString());
            }
        });
           return promise.future();
    }
    private Future<JsonObject> getDiscovery(String condition ,String column ,String value){
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var result = new JsonObject();
        String query ;
        if(condition.equals("all")){
            query = "Select id,name,ip,type,credential_profile,port from discovery ;";
        }else{
            query = "Select id,name,ip,type,credential_profile,port from discovery where " + column +"= \""+value+"\";";
        }
        Bootstrap.getVertx().executeBlocking(handler ->{
            var credentials = new JsonArray();
            try(var connection = connect()){
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var resultSet = statement.executeQuery(query);
                while(resultSet.next()){
                    var data = new JsonObject();
                    data.put(DISCOVERY_ID,resultSet.getString(1));
                    data.put(DISCOVERY_NAME,resultSet.getString(2));
                    data.put(DISCOVERY_IP,resultSet.getString(3));
                    data.put(DISCOVERY_TYPE,resultSet.getString(4));
                    data.put(CREDENTIAL_PROFILE,resultSet.getString(5));
                    data.put(DISCOVERY_PORT,resultSet.getInt(6));
                    credentials.add(data);
                }
            }catch (Exception exception){
                error.add(exception.getCause().getMessage());
            }
            handler.complete(credentials);

        }).onComplete( completeHandler ->{
            if(error.isEmpty()){
                result.put("result",completeHandler.result());
                result.put(STATUS,SUCCESS);
                promise.complete(result);
            }else{
                promise.fail(error.toString());
            }

        });
        return promise.future();
    }
    private Future<JsonObject> update(String table , JsonObject credential){
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var query = new StringBuilder();
        query.append("Update ").append(table).append(" set ");
        credential.stream().forEach( value ->{
            var column = value.getKey();
            var data = credential.getValue(column);
            if(column.equals("credential.profile")){
                column = "credential_profile" ;
            }else if(column.equals(table+"."+"name")){
                column="name";
            }
            if(!column.equals("id") && (!column.equals("type")) && (!column.equals("protocol"))  ) {
                if (data instanceof String) {
                    query.append(column + "=");
                    query.append("\"" + data + "\",");
                } else {
                    query.append(column + "=");
                    query.append(data + ",");
                }
            }
        });
        query.setLength(query.length()-1);
        query.append(" where id = \"").append(credential.getString("id")+"\";");
        System.out.println(query);
        Bootstrap.getVertx().executeBlocking(handler ->{
            try(var connection = connect()){
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var flag =statement.executeUpdate(query.toString());
                if(flag == 0){
                    error.add("No data to update");
                }
            }catch (Exception exception){
                error.add(exception.getCause().getMessage());
            }
            handler.complete();
        }).onComplete( completeHandler ->{
            if(error.isEmpty()){
                promise.complete(credential.put(STATUS,SUCCESS));
            }else{
                promise.fail(error.toString());
            }
        });
        return promise.future();
    }

}
