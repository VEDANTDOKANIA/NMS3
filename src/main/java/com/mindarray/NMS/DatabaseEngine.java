package com.mindarray.NMS;

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
                    Future<JsonObject> result = null;
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        result = insert(handler.body().getString(TABLE), handler.body());
                    }
                    result.onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(completeHandler.result());
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }

                    });
                }
                case DATABASE_DELETE -> {
                    Future<JsonObject> result = null;
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        if (handler.body().getString(TABLE).equals(CREDENTIAL_TABLE)) {
                            result = delete(CREDENTIAL_TABLE, "credential_id", handler.body().getString(CREDENTIAL_ID));
                        } else {
                            result = delete(DISCOVERY_TABLE, "discovery_id", handler.body().getString(DISCOVERY_ID));
                        }
                    }
                    result.onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(completeHandler.result());
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }
                    });
                }
                case DATABASE_GET -> {
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
                    handler.body().remove(METHOD);
                    Future<JsonObject> result = null;
                    if (handler.body().containsKey(TABLE) && handler.body().getString(TABLE) != null) {
                        var table = handler.body().getString(TABLE);
                        handler.body().remove(TABLE);
                        result = update(table, handler.body());
                    }

                    result.onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(completeHandler.result());
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }
                    });
                }
                case DATABASE_CHECK -> {
                    var table = handler.body().getString(TABLE);
                    handler.body().remove(TABLE);
                    handler.body().remove(METHOD);
                    var futures = new ArrayList<Future>();
                    handler.body().stream().forEach(key -> {

                        if (key.getKey().contains(".")) {
                            futures.add(check(table, key.getKey().replace(".", "_"), handler.body().getString(key.getKey())));
                        } else {
                            futures.add(check(table, key.getKey(), handler.body().getString(key.getKey())));
                        }

                    });
                    CompositeFuture.all(futures).onComplete(context -> {
                        if (context.succeeded()) {
                            handler.reply(handler.body());
                        } else {
                            handler.fail(-1, context.cause().getMessage());
                        }
                    });

                }
                case CREDENTIAL_DATABASE_CHECK_ID -> {
                    var checkId = check(CREDENTIAL_TABLE, "credential_id", handler.body().getString(CREDENTIAL_ID));
                    var checkProfile = check(DISCOVERY_TABLE, "credential_profile", handler.body().getString(CREDENTIAL_ID));
                    CompositeFuture.join(checkId, checkProfile).onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(handler.body());
                        } else {
                            if (checkProfile.failed()) {
                                handler.fail(-1, "cannot delete because profile already exsists in discovery table");
                            } else {
                                handler.fail(-1, completeHandler.cause().getMessage());
                            }
                        }
                    });
                }
                case DISCOVERY_DATABASE_CHECK_NAME -> {
                    var name = check(DISCOVERY_TABLE, "discovery_name", handler.body().getString(DISCOVERY_NAME));
                    var profile = check(CREDENTIAL_TABLE, "credential_id", handler.body().getString(CREDENTIAL_PROFILE));
                    CompositeFuture.all(name, profile).onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(handler.body().put(STATUS, SUCCESS));
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }
                    });
                }
                case DISCOVERY_DATABASE_CHECK_MULTIPLE -> {
                    Future<JsonObject> nameCheck;
                    var futures = new ArrayList<Future>();
                    var errors = new ArrayList<String>();
                    var idCheck = check(DISCOVERY_TABLE, "discovery_id", handler.body().getString(DISCOVERY_ID));
                    futures.add(idCheck);
                    if (handler.body().containsKey(DISCOVERY_NAME)) {
                        nameCheck = check(DISCOVERY_TABLE, "discovery_name", handler.body().getString(DISCOVERY_NAME));
                        futures.add(nameCheck);
                    }
                    if (handler.body().containsKey(CREDENTIAL_PROFILE)) {
                        var profileCheck = check(CREDENTIAL_TABLE, "credential_id", handler.body().getString(CREDENTIAL_PROFILE));
                        futures.add(profileCheck);
                    }
                    CompositeFuture.all(futures).onComplete(future -> {
                        if (future.failed()) {
                            errors.add(future.cause().getMessage());
                        }
                        if (errors.isEmpty()) {
                            handler.reply(handler.body().put(STATUS, SUCCESS));
                        } else {
                            handler.fail(-1, errors.toString());
                        }
                    });
                }
                case GET_QUERY -> {
                    getQuery(handler.body().getString(QUERY)).onComplete(completeHandler -> {
                        if (completeHandler.succeeded()) {
                            handler.reply(completeHandler.result());
                        } else {
                            handler.fail(-1, completeHandler.cause().getMessage());
                        }

                    });
                }
                case EXECUTE_QUERY -> {
                    executeQuery(handler.body().getString(QUERY), handler.body().getString("condition")).onComplete(context -> {
                        if (context.succeeded()) {
                            handler.reply(handler.body());
                        } else {
                            handler.fail(-1, context.cause().getMessage());
                        }
                    });
                }
                default -> {
                    LOGGER.error("Error occurred :{}", "No matching method found");
                }
            }
        });
        eventBus.<JsonObject>localConsumer(PROVISION, handler -> {
            switch (handler.body().getString(METHOD)) {
                case "runProvision" -> {
                    var futures = new ArrayList<Future>();
                    var errors = new ArrayList<String>();
                    getQuery(handler.body().getString(QUERY)).compose(futureResult -> check(MONITOR_TABLE, IP, handler.body().getString(IP))).compose(futureResult -> insert(MONITOR_TABLE, new JsonObject().put(TYPE, handler.body().getString(TYPE)).put(PORT, handler.body().getInteger(PORT)).put(IP, handler.body().getString(IP)))).onComplete(futureResult -> {
                        if (futureResult.succeeded()) {
                            var metric = new JsonObject().put("monitor.id", futureResult.result().getString("id")).put(CREDENTIAL_PROFILE, handler.body().getString(CREDENTIAL_PROFILE));
                            if (handler.body().containsKey("objects")) {
                                var object = handler.body().getJsonObject("objects").getJsonArray("interfaces").stream().map(JsonObject::mapFrom).filter(value -> value.getString("interface.operational.status").equals("up")).toList();
                                metric.put("objects", object);
                            }

                            Utils.metricGroup(handler.body().getString(TYPE)).stream().map(JsonObject::mapFrom).forEach(value -> futures.add(insert(METRIC_TABLE, metric.mergeIn(value))));
                            CompositeFuture.join(futures).onComplete(completeHandler -> {
                                if (completeHandler.succeeded()) {
                                    handler.reply(new JsonObject().put(MESSAGE, "your unique id is " + metric.getString("monitor.id")));
                                } else {
                                    errors.add(completeHandler.cause().getMessage());
                                }
                            });
                        } else {
                            errors.add(futureResult.cause().getMessage());
                            handler.fail(-1, errors.toString());
                        }
                    });
                }
                case "check" -> {
                    check(MONITOR_TABLE, "monitor_id", handler.body().getString(MONITOR_ID)).onComplete(result -> {
                        if (result.succeeded()) {
                            handler.reply(result.result());
                        } else {
                            handler.fail(-1, result.cause().getMessage());
                        }
                    });
                }
                case "delete" -> {
                    delete(MONITOR_TABLE, "monitor_id", handler.body().getString(MONITOR_ID)).compose(handler1 -> executeQuery(handler.body().getString(QUERY), "delete")).onComplete(handler1 -> {
                        if (handler1.succeeded()) {
                            handler.reply(handler1.result());
                        } else {
                            handler.fail(-1, handler1.cause().getMessage());
                        }
                    });
                }
                case "get" -> {
                    getQuery(handler.body().getString(QUERY)).onComplete(result -> {
                        if (result.succeeded()) {
                            handler.reply(result.result());
                        } else {
                            handler.fail(-1, result.cause().getMessage());
                        }
                    });
                }
            }
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
            LOGGER.error("Exception Occurred :{}", e.getMessage());
        }
        return connection;
    }

    private Future<Integer> getId(String table) {
        Promise<Integer> promise = Promise.promise();
        Bootstrap.vertx.executeBlocking(handler -> {
            try (var connection = connect()) {
                var statement = connection.createStatement();
                statement.execute("use nms");
                var resultSet = statement.executeQuery("select max(" + table + "_id) from " + table + ";");
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
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if (table == null || column == null || data == null) {
            errors.add("null occurred");
        } else {
            Bootstrap.getVertx().<JsonObject>executeBlocking(handler -> {
                try (var connection = connect()) {
                    var statement = connection.createStatement();
                    statement.execute("use nms");
                    var query = "select exists(select *  from " + table + " where " + column + "=\"" + data + "\");";
                    var resultSet = statement.executeQuery(query);
                    while (resultSet.next()) {
                        if (column.equals(table + "_" + "name") || column.equals(IP) || column.equals("credential_profile")) {
                            if (resultSet.getInt(1) == 1) {
                                errors.add(column + " is not unique");
                            }
                        } else {
                            if (resultSet.getInt(1) == 0) {
                                errors.add(table + "." + column + " does not exists in table ");
                            }
                        }
                    }
                } catch (Exception exception) {
                    errors.add(exception.getCause().getMessage());
                    LOGGER.error(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                if (errors.isEmpty()) {
                    promise.complete(new JsonObject().put(STATUS, SUCCESS));
                } else {
                    promise.fail(String.valueOf(errors));
                }
                LOGGER.info("Create Function executed successfully");
            });
        }
        return promise.future();
    }

    private Future<JsonObject> delete(String table, String column, String data) {
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        if (table == null || column == null || data == null) {
            errors.add("null occurred");
        } else {
            Bootstrap.getVertx().executeBlocking(handler -> {
                try (var connection = connect()) {
                    var statement = connection.createStatement();
                    statement.execute("use nms");
                    var query = "Delete from " + table + " where " + column + " = " + "\"" + data + "\";";
                    statement.execute(query);
                } catch (Exception exception) {
                    errors.add(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                if (errors.isEmpty()) {
                    promise.complete(new JsonObject().put(STATUS, SUCCESS));
                } else {
                    promise.fail(errors.toString());
                }
            });
        }
        return promise.future();
    }

    private Future<JsonObject> update(String table, JsonObject credential) {
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var query = new StringBuilder();
        query.append("Update ").append(table).append(" set ");
        credential.stream().forEach(value -> {
            var column = value.getKey();
            var data = credential.getValue(column);
            if (column.contains(".")) {
                column = column.replace(".", "_");
            }
            query.append(column).append("=");
            if (data instanceof String) {
                query.append("\"").append(data).append("\",");
            } else {
                query.append(data).append(",");
            }
        });
        query.setLength(query.length() - 1);
        query.append(" where ").append(table).append("_id=\"").append(credential.getString(table + ".id")).append("\";");
        Bootstrap.getVertx().executeBlocking(handler -> {
            try (var connection = connect()) {
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var flag = statement.executeUpdate(query.toString());
                if (flag == 0) {
                    error.add("No data to update");
                }
            } catch (Exception exception) {
                error.add(exception.getCause().getMessage());
            }
            handler.complete();
        }).onComplete(completeHandler -> {
            if (error.isEmpty()) {
                promise.complete(credential.put(STATUS, SUCCESS));
            } else {
                promise.fail(error.toString());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> insert(String table, JsonObject credential) {
        credential.remove("method");
        credential.remove(TABLE);
        var errors = new ArrayList<String>();
        Promise<JsonObject> promise = Promise.promise();
        var query = new StringBuilder();
        var mapper = transform(credential);
        query.append("insert into ").append(table).append(" ( ");
        query.append(mapper.getString("columns")).append(")").append("values (");
        if (credential.isEmpty()) {
            errors.add("Empty Credentials");
            promise.complete(new JsonObject().put(STATUS, FAIL).put(ERROR, errors));
        } else {
            Bootstrap.vertx.executeBlocking(handler -> {
                query.append(mapper.getString("values")).append(");");
                try (var connection = connect()) {
                    var statement = connection.createStatement();
                    statement.execute("use nms");

                    statement.execute(String.valueOf(query));
                } catch (Exception exception) {
                    errors.add(exception.getCause().getMessage());
                }
                handler.complete();
            }).onComplete(completeHandler -> {
                getId(table).onComplete(id -> {
                    if (errors.isEmpty()) {
                        promise.complete(credential.put(MESSAGE, "Your unique id is " + id.result()).put("id", id.result()));
                    } else {
                        promise.fail(String.valueOf(errors));
                    }
                });

            });
        }
        return promise.future();
    }

    private Future<JsonObject> get(JsonObject credential) {
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var resultAll = new JsonObject();
        var query = new StringBuilder();
        if (credential.getString("condition").equals("all")) {
            query.append("Select * from ").append(credential.getString(TABLE)).append(";");
        } else {
            query.append("Select * from ").append(credential.getString(TABLE)).append(" where ").append(credential.getString("column"));
            if (credential.getValue("value") instanceof String) {
                query.append("=\"").append(credential.getString("value")).append("\"").append(";");
            } else {
                query.append("=").append(credential.getString("value")).append(";");
            }
        }
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect()) {
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var resultSet = statement.executeQuery(query.toString());
                var rsmd = resultSet.getMetaData();
                if (resultSet.next() == false) {
                    error.add("No data to show");
                } else {
                    do {
                        var data = new JsonObject();
                        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                            var column = rsmd.getColumnName(i);
                            if (column.contains("_")) {
                                column = column.replace("_", ".");
                            }
                            if (resultSet.getObject(i) != null) {
                                if (rsmd.getColumnTypeName(i).equals("VARCHAR")) {
                                    data.put(column, resultSet.getString(i));
                                } else {
                                    data.put(column, resultSet.getObject(i));
                                }
                            }
                        }
                        result.add(data);
                    } while (resultSet.next());
                }

            } catch (Exception exception) {
                error.add(exception.getCause().getMessage());
            }
            handler.complete(result);

        }).onComplete(completeHandler -> {
            if (error.isEmpty()) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(error.toString());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> getQuery(String query) {
        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var resultAll = new JsonObject();
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect()) {
                var statement = connection.createStatement();
                statement.execute("use nms;");
                var resultSet = statement.executeQuery(query);
                var rsmd = resultSet.getMetaData();
                if (!resultSet.next()) {
                    error.add("Wrong id provided or no result to showcase");
                } else {
                    do {
                        var data = new JsonObject();
                        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                            var column = rsmd.getColumnName(i);
                            if (column.contains("_")) {
                                column = column.replace("_", ".");
                            }
                            if (resultSet.getObject(i) != null) {
                                if (rsmd.getColumnTypeName(i).equals("VARCHAR")) {
                                    data.put(column, resultSet.getString(i));
                                } else if (rsmd.getColumnTypeName(i).equals("JSON")) {
                                    data.put(column, resultSet.getObject(i));
                                } else {
                                    data.put(column, resultSet.getInt(i));
                                }
                            }
                        }
                        result.add(data);
                    } while (resultSet.next());
                }
            } catch (Exception exception) {
                error.add(exception.getCause().getMessage());
            }
            handler.complete(result);
        }).onComplete(completeHandler -> {
            if (error.isEmpty()) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(error.toString());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> executeQuery(String query, String condition) {

        Promise<JsonObject> promise = Promise.promise();
        var error = new ArrayList<String>();
        var resultAll = new JsonObject();
        Bootstrap.getVertx().executeBlocking(handler -> {
            var result = new JsonArray();
            try (var connection = connect()) {
                var statement = connection.createStatement();
                statement.execute("use nms;");
                if (condition.equals("update")) {
                    statement.executeUpdate(query);
                } else {
                    statement.execute(query);
                }

            } catch (Exception exception) {
                error.add(exception.getCause().getMessage());
            }
            handler.complete(result);

        }).onComplete(completeHandler -> {
            if (error.isEmpty()) {
                resultAll.put(RESULT, completeHandler.result());
                resultAll.put(STATUS, SUCCESS);
                promise.complete(resultAll);
            } else {
                promise.fail(error.toString());
            }
        });
        return promise.future();
    }

    private JsonObject transform(JsonObject credentials) {
        var column = new StringBuilder();
        var value = new StringBuilder();
        credentials.stream().forEach(key -> {
            if (credentials.getValue(key.getKey()) != null) {
                if (key.getKey().contains(".")) {
                    column.append(key.getKey().replace(".", "_")).append(",");
                } else {
                    column.append(key.getKey()).append(",");
                }
                var data = credentials.getValue(key.getKey());
                if (data instanceof String) {
                    value.append("\"").append(data).append("\"").append(",");
                } else if (data instanceof JsonArray || data instanceof JsonObject) {
                    value.append("\'").append(data).append("\'").append(",");
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
