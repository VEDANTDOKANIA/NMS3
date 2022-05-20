import com.mindarray.NMS.Bootstrap;
import com.mindarray.NMS.Utils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.stream.Collectors;

public class JsonIteration {
    public static void main(String[] args) {
       /*var list= Utils.metricGroup("linux").stream().map(JsonObject::mapFrom).filter(value -> value.getString("metric_group").equals("Cpu")).collect(Collectors.toList());
          System.out.println(list);*/
        var id = System.currentTimeMillis();
        System.out.println(id);
    }
}
