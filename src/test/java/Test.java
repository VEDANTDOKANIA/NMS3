import io.vertx.core.json.JsonObject;

public class Test {
    public static void main(String[] args) {
        var query = new StringBuilder();
        query.append("Update ").append("discovery ").append("set  ");
        JsonObject credential = new JsonObject();
        credential.put("name","vedant");
        credential.put("port",22);
        credential.put("id","abc");
        credential.stream().forEach( value ->{
            var column = value.getKey();
            if(column.equals("credential.profile")){
                column = "credential_profile" ;
            }
            var data = credential.getValue(column);
            if(!column.equals("id")) {
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

    }
}
