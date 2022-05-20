import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;



public class DynamicCreate {
    public static void main(String[] args) {
        var user = new JsonObject();
        user.put("credential.name","vedant");
       // user.put(PROTOCOL,"ssh");
        user.put("id","abc");
        user.put("username","vedant");
        user.put("password","Mind@123");
      // create("credential",user);


    }
    /*public static JsonObject transform(JsonObject credentials){
        var column = new StringBuilder();
        var value = new StringBuilder();
        credentials.stream().forEach(key ->{
            column.append(columnMapper().getString(key.getKey())+",");
            var data =credentials.getValue(key.getKey());
            if(data instanceof String){
                value.append("\""+data+"\""+",");
            }else{
                value.append(data+",");
            }
        });
        column.setLength(column.length()-1);
       value.setLength(value.length()-1);
        return new JsonObject().put("columns",column).put("values",value);
    }*/
   /* public static void create(String table , JsonObject credential){
        var query = new StringBuilder();
        query.append("insert into ").append(table).append(" ( ");
        var mapper = transform(credential);
        query.append(mapper.getString("columns")+")").append("values (");
        query.append(mapper.getString("values")+");");
        System.out.println(query);
    }*/
    public static void get(String condition , JsonObject credential){

    }
}
