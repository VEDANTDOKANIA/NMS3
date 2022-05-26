import io.vertx.core.json.JsonObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class JsonIteration {
    public static void main(String[] args) throws ParseException {
       /*var list= Utils.metricGroup("linux").stream().map(JsonObject::mapFrom).filter(value -> value.getString("metric_group").equals("Cpu")).collect(Collectors.toList());
          System.out.println(list);*/




        System.out.println("****************************");

        //millis to date
        var milliss = 1653571263305L;
        DateFormat simple = new SimpleDateFormat("dd/MM/yy HH:mm:ss:ssss");
        Date result = new Date(milliss);
        System.out.println(simple.format(result));

        String myDate = "26/05/22 18:51:03:0003";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm:ssss");
        Date date = sdf.parse(myDate);
        System.out.println(date.getTime());


    }
}
