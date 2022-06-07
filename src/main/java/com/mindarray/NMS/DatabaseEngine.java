package com.mindarray.NMS;

import com.mindarray.Bootstrap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;

import static com.mindarray.NMS.Constant.*;

public class DatabaseEngine extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseEngine.class);

    @Override
    public void start(Promise<Void> startPromise) {
        var eventBus = vertx.eventBus();
        eventBus.<JsonObject>localConsumer(DATABASE, handler -> {
            switch (handler.body().getString(METHOD)) {
                case DATABASE_CREATE -> {
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        insert(handler.body().getString(TABLE), handler.body()).onComplete(completeHandler -> {
                            if (completeHandler.succeeded()) {
                                handler.reply(completeHandler.result());
                            } else {
                                handler.fail(-1, completeHandler.cause().getMessage());
                            }
                        });
                    } else {
                        handler.fail(-1, "no table selected for create");
                    }
                }
                case DATABASE_DELETE -> {
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        Future<JsonObject> result;
                        if (handler.body().getString(TABLE).equals(CREDENTIAL_TABLE)) {
                            result = delete(CREDENTIAL_TABLE, "credential_id", handler.body().getString(CREDENTIAL_ID));
                        } else {
                            result = delete(DISCOVERY_TABLE, "discovery_id", handler.body().getString(DISCOVERY_ID));
                        }
                        result.onComplete(completeHandler -> {
                            if (completeHandler.succeeded()) {
                                handler.reply(completeHandler.result());
                            } else {
                                handler.fail(-1, completeHandler.cause().getMessage());
                            }
                        });
                    }

                }
                case DATABASE_GET ->    {
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        get(handler.body()).onComplete(completeHandler -> {
                            if (completeHandler.succeeded()) {
                                handler.reply(completeHandler.result());
                            } else {
                                handler.fail(-1, completeHandler.cause().getMessage());
                            }
                        });
                    }
                }
                case DATABASE_UPDATE -> {
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        update(handler.body().getString(TABLE), handler.body()).onComplete(completeHandler -> {
                            if (completeHandler.succeeded()) {
                                handler.reply(completeHandler.result());
                            } else {
                                handler.fail(-1, completeHandler.cause().getMessage());
                            }
                        });
                    } else {
                        handler.fail(-1, "no table selected for create");
                    }
                }
                case DATABASE_CHECK -> {
                    var futures = new ArrayList<Future>();
                    var checkData = handler.body().getJsonArray(PARAMETER);
                    for (int index = 0; index < checkData.size(); index++) {
                        var data = checkData.getJsonObject(index);
                        futures.add(check(data.getString(TABLE), data.getString(COLUMN), data.getString(VALUE)));
                    }
                    CompositeFuture.join(futures).onComplete(completeHandler -> {
                        if (completeHandler.succeeded() && completeHandler.result() != null) {
                            handler.reply(handler.body());
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }
                    });
                }
                case GET_QUERY -> getQuery(handler.body().getString(QUERY)).onComplete(completeHandler -> {
                    if (completeHandler.succeeded()) {
                        handler.reply(completeHandler.result());
                    } else {
                        handler.fail(-1, completeHandler.cause().getMessage());
                    }
                });
                case EXECUTE_QUERY -> executeQuery(handler.body().getString(QUERY), handler.body().getString("condition")).onComplete(context -> {
                            if (context.succeeded()) {
                                handler.reply(handler.body());
                            } else {
                                handler.fail(-1, context.cause().getMessage());
                            }
                        });
                default -> {
                    handler.fail(-1, "no matching method found");
                    LOGGER.error("Error occurred :{}", "no matching method found");
                }
            }
        });
        eventBus.<JsonObject>localConsumer(METRIC_DATA, handler -> {
            try {
                switch (handler.body().getString(METHOD)) {
                    case "runProvision" -> {
                        var futures = new ArrayList<Future>();
                        var errors = new ArrayList<String>();
                        getQuery(handler.body().getString(QUERY)).compose(futureResult -> check(MONITOR_TABLE, IP, handler.body().getString(IP))).compose(futureResult ->
                                insert(MONITOR_TABLE, new JsonObject().put(TYPE, handler.body().getString(TYPE)).put(PORT, handler.body().getInteger(PORT)).put(IP, handler.body().getString(IP)).put("host", handler.body().getString("host")))).onComplete(futureResult -> {
                            try {
                                if (futureResult.succeeded()) {
                                    var metric = new JsonObject().put("monitor.id", futureResult.result().getString("id")).put(CREDENTIAL_PROFILE, handler.body().getString(CREDENTIAL_PROFILE));
                                    var objects = new JsonObject();
                                    if (handler.body().containsKey("objects") && handler.body().getString(TYPE).equals("snmp")) {
                                        var object = handler.body().getJsonObject("objects").getJsonArray("interfaces").stream().map(JsonObject::mapFrom).filter(value -> value.getString("interface.operational.status").equals("up")).toList();
                                        objects.put("objects", object);
                                    }
                                    for (int index = 0; index < Utils.metricGroup(handler.body().getString(TYPE)).size(); index++) {
                                        var groups = Utils.metricGroup(handler.body().getString(TYPE)).getJsonObject(index);
                                        if (groups.getString("metric.group").equals("interface")) {
                                            metric.mergeIn(objects);
                                        }
                                        futures.add(insert(METRIC_TABLE, metric.mergeIn(groups)));
                                        metric.remove("objects");
                                    }
                                    CompositeFuture.join(futures).onComplete(completeHandler -> {
                                        if (completeHandler.succeeded()) {
                                            handler.reply(new JsonObject().put("id", metric.getString("monitor.id")));
                                        } else {
                                            errors.add(completeHandler.cause().getMessage());
                                        }
                                    });
                                } else {
                                    errors.add(futureResult.cause().getMessage());
                                    handler.fail(-1, errors.toString());
                                }
                            } catch (Exception exception) {
                                handler.fail(-1, exception.getMessage());
                            }

                        });
                    }
                    case "check" -> check(MONITOR_TABLE, MONITOR_ID, handler.body().getString(MONITOR_ID)).onComplete(result -> {
                                if (result.succeeded()) {
                                    handler.reply(result.result());
                                } else {
                                    handler.fail(-1, result.cause().getMessage());
                                }
                            });
                    case "delete" -> delete(MONITOR_TABLE, MONITOR_ID, handler.body().getString(MONITOR_ID)).compose(futureResult -> executeQuery(handler.body().getString(QUERY), "delete")).onComplete(futureResult -> {
                                if (futureResult.succeeded() && futureResult.result() != null) {
                                    handler.reply(futureResult.result());
                                } else {
                                    handler.fail(-1, futureResult.cause().getMessage());
                                }
                            });
                    case "get" -> getQuery(handler.body().getString(QUERY)).onComplete(result -> {
                        if (result.succeeded() && result.result() != null) {
                            handler.reply(result.result());
                        } else {
                            handler.fail(-1, result.cause().getMessage());
                        }
                    });
                    case "update" -> update(MONITOR_TABLE,handler.body());
                    default -> {
                        handler.fail(-1, "no matching method found");
                        LOGGER.error("error occurred :{}", "no matching method found");
                    }
                }
            } catch (Exception exception) {
                handler.fail(-1, exception.getMessage());
            }
        });
        eventBus.<JsonObject>localConsumer(POLLER_DATABASE, handler -> {
            if (handler.body() != null) {
                insert(handler.body().getString(TABLE), handler.body()).onComplete(result -> {
                    if (result.succeeded()) {
                        LOGGER.info("data dumped into database");
                    } else {
                        LOGGER.error("error occurred :{}", "unable to dump the data in database");
                    }
                });
            }
        });
        startPromise.complete();
    }

    private Connection connect() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/nms", "vedant.dokania", "Mind@123");
        } catch (Exception exception) {
            LOGGER.error("error occurred :{}", exception.getMessage());
        }
        return connection;
    }

    private Future<Integer> getId(String table) {
        Promise<Integer> promise = Promise.promise();
        Bootstrap.vertx.executeBlocking(handler -> {
            try (var connection = connect(); var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("select max(" + table + "_id) from " + table + ";")) {
                while (resultSet.next()) {
                    promise.complete(resultSet.getInt(1));
                }
            } catch (Exception exception) {
                promise.fail(exception.getCause().getMessage());
            }
            handler.complete();
        });
        return promise.future();
    }

    private Future<JsonObject> check(String table, String column, String data) {
        if (column != null && column.contains(".")) {
            column = column.replace(".", "_");
        }
        Promise<JsonObject> promise = Promise.promise();
        if (table == null || column == null || data == null) {
            promise.fail("null occurred");
        } else {
            String finalColumn = column;
            Bootstrap.getVertx().<JsonObject>executeBlocking(handler -> {
                var query = "select exists(select *  from " + table + " where " + finalColumn + "=\"" + data + "\");";
                try (var connection = connect(); var statement = connection.createStatement(); var resultSet = statement.executeQuery(query)) {
                    while (resultSet.next()) {
                        if (finalColumn.equals(table + "_" + "name") || finalColumn.equals(IP) || finalColumn.equals("credential_profile")) {
                            if (resultSet.getInt(1) == 1) {
                                handler.fail(finalColumn + " is not unique");
                            }
                        } else {
                            if (resultSet.getInt(1) == 0) {
                                handler.fail( finalColumn + " does not exists in table ");
                            }
                        }
                    }
                } catch (Exception exception) {
                    handler.fail(exception.getCause().getMessage());
                    LOGGER.error(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                if (completeHandler.succeeded()) {
                    promise.complete(new JsonObject().put(STATUS, SUCCESS));
                } else {
                    promise.fail(completeHandler.cause().getMessage());
                }
            });
        }
        return promise.future();
    }

    private Future<JsonObject> delete(String table, String column, String data) {
        if (column != null && column.contains(".")) {
            column = column.replace(".", "_");
        }
        Promise<JsonObject> promise = Promise.promise();
        if (table == null || column == null || data == null) {
            promise.fail("null occurred");
        } else {
            String finalColumn = column;
            Bootstrap.getVertx().executeBlocking(handler -> {
                try (var connection = connect(); var statement = connection.createStatement()) {
                    var query = "Delete from " + table + " where " + finalColumn + " = " + "\"" + data + "\";";
                    statement.execute(query);
                } catch (Exception exception) {
                    handler.fail(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                if (completeHandler.succeeded()) {
                    promise.complete(new JsonObject().put(STATUS, SUCCESS));
                } else {
                    promise.fail(completeHandler.cause().getMessage());
                }
            });
        }
        return promise.future();
    }

    private Future<JsonObject> update(String table, JsonObject entries) {
        entries.remove(METHOD);
        entries.remove(TABLE);
        entries.remove(PROTOCOL);
        entries.remove(PARAMETER);
        Promise<JsonObject> promise = Promise.promise();
        var query = new StringBuilder();
        query.append("Update ").append(table).append(" set ");
        entries.forEach(value -> {
            var column = value.getKey();
            var data = entries.getValue(column);
            if (column.contains(".")) {
                column = column.replace(".", "_");
            }
            query.append(column).append("=");
            if (data instanceof String) {
                query.append("\"").append(data).append("\",");
            }else if(data instanceof JsonObject || data instanceof JsonArray){
                query.append("\'").append(data).append("\',");
            } else {
                query.append(data).append(",");
            }
        });
        query.setLength(query.length() - 1);
        query.append(" where ").append(table).append("_id=\"").append(entries.getString(table + ".id")).append("\";");
        Bootstrap.getVertx().executeBlocking(handler -> {
            try (var connection = connect(); var statement = connection.createStatement()) {
                var flag = statement.executeUpdate(query.toString());
                if (flag == 0) {
                    handler.fail("no data to update");
                }
            } catch (Exception exception) {
                handler.fail(exception.getCause().getMessage());
            }
            handler.complete();
        }).onComplete(completeHandler -> {
            if (completeHandler.succeeded()) {
                promise.complete(entries.put(STATUS, SUCCESS));
            } else {
                promise.fail(completeHandler.cause().getMessage());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> insert(String table, JsonObject entries) {
        entries.remove(METHOD);
        entries.remove(TABLE);
        entries.remove(PARAMETER);
        Promise<JsonObject> promise = Promise.promise();
        var query = new StringBuilder();
        var mapper = transform(entries);
        query.append("insert into ").append(table).append(" ( ");
        query.append(mapper.getString("columns")).append(")").append("values (");
        if (entries.isEmpty()) {
            promise.fail("null credentials");
        } else {
            Bootstrap.vertx.executeBlocking(handler -> {
                query.append(mapper.getString("values")).append(");");
                try (var connection = connect(); var statement = connection.createStatement()) {
                    statement.execute(String.valueOf(query));
                } catch (Exception exception) {
                    handler.fail(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                if (completeHandler.succeeded()) {
                    getId(table).onComplete(id -> promise.complete(entries.put(MESSAGE, "Your unique id is " + id.result()).put("id", id.result())));
                } else {
                    promise.fail(completeHandler.cause().getMessage());
                }
            });
        }
        return promise.future();
    }

    private Future<JsonObject> get(JsonObject entries) {
        Promise<JsonObject> promise = Promise.promise();
        var resultAll = new JsonObject();
        var query = new StringBuilder();
        if (entries.getString("condition").equals("all")) {
            query.append("Select * from ").append(entries.getString(TABLE)).append(";");
        } else {
            query.append("Select * from ").append(entries.getString(TABLE)).append(" where ").append(entries.getString("column"));
            if (entries.getValue("value") instanceof String) {
                query.append("=\"").append(entries.getString("value")).append("\"").append(";");
            } else {
                query.append("=").append(entries.getString("value")).append(";");
            }
        }
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect(); var statement = connection.createStatement(); var resultSet = statement.executeQuery(query.toString())) {
                var resultSetMetaData = resultSet.getMetaData();
                if (!resultSet.next()) {
                    if(!entries.getString("condition").equals("all")){
                        handler.fail("no data to show wrong credentials provided");
                    }
                } else {
                    do {
                        var data = new JsonObject();
                        for (int index = 1; index <= resultSetMetaData.getColumnCount(); index++) {
                            var column = resultSetMetaData.getColumnName(index);
                            if (column.contains("_")) {
                                column = column.replace("_", ".");
                            }
                            if (resultSet.getObject(index) != null) {
                                if (resultSetMetaData.getColumnTypeName(index).equals("VARCHAR")) {
                                    data.put(column, resultSet.getString(index));
                                } else {
                                    data.put(column, resultSet.getObject(index));
                                }
                            }
                        }
                        result.add(data);
                    } while (resultSet.next());
                }
            } catch (Exception exception) {
                handler.fail(exception.getCause().getMessage());
            }
            handler.complete(result);
        }).onComplete(completeHandler -> {
            if (completeHandler.succeeded() && completeHandler.result() != null) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(completeHandler.cause().getMessage());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> getQuery(String query) {
        Promise<JsonObject> promise = Promise.promise();
        var resultAll = new JsonObject();
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect(); var statement = connection.createStatement(); var resultSet = statement.executeQuery(query)) {
                var resultSetMetaData = resultSet.getMetaData();
                if (!resultSet.next()) {
                    handler.fail("wrong id provided or no result to showcase");
                } else {
                    do {
                        var data = new JsonObject();
                        for (int index = 1; index <= resultSetMetaData.getColumnCount(); index++) {
                            var column = resultSetMetaData.getColumnName(index);
                            if (column.contains("_")) {
                                column = column.replace("_", ".");
                            }
                            if (resultSet.getObject(index) != null) {
                                if (resultSetMetaData.getColumnTypeName(index).equals("VARCHAR")) {
                                    data.put(column, resultSet.getString(index));
                                } else if (resultSetMetaData.getColumnTypeName(index).equals("JSON")) {
                                    data.put(column, resultSet.getObject(index));
                                } else if (resultSetMetaData.getColumnTypeName(index).equals("BIGINT")) {
                                    data.put(column, resultSet.getLong(index));
                                } else {
                                    data.put(column, resultSet.getInt(index));
                                }
                            }
                        }
                        result.add(data);
                    } while (resultSet.next());
                }
            } catch (Exception exception) {
                handler.fail(exception.getCause().getMessage());
            }
            handler.complete(result);
        }).onComplete(completeHandler -> {
            if (completeHandler.succeeded() && completeHandler.result() != null) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(completeHandler.cause().getMessage());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> executeQuery(String query, String condition) {
        Promise<JsonObject> promise = Promise.promise();
        var resultAll = new JsonObject();
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect(); var statement = connection.createStatement()) {
                if (condition.equals("update")) {
                    statement.executeUpdate(query);
                } else {
                    statement.execute(query);
                }
            } catch (Exception exception) {
                handler.fail(exception.getCause().getMessage());
            }
            handler.complete(result);
        }).onComplete(completeHandler -> {
            if (completeHandler.succeeded() && completeHandler.result() != null) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(completeHandler.cause().getMessage());
            }
        });
        return promise.future();
    }

    private JsonObject transform(JsonObject entries) {
        var column = new StringBuilder();
        var value = new StringBuilder();
        entries.forEach(key -> {
            if (entries.getValue(key.getKey()) != null) {
                if (key.getKey().contains(".")) {
                    column.append(key.getKey().replace(".", "_")).append(",");
                } else {
                    column.append(key.getKey()).append(",");
                }
                var data = entries.getValue(key.getKey());
                if (data instanceof String) {
                    value.append("\"").append(data).append("\"").append(",");
                } else if (data instanceof JsonArray || data instanceof JsonObject) {
                    value.append("'").append(data).append("'").append(",");
                } else {
                    value.append(data).append(",");
                }
            }
        });
        value.setLength(value.length() - 1);
        column.setLength(column.length() - 1);
        return new JsonObject().put("columns", column).put("values", value);
    }

}
