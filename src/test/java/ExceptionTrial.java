import com.mindarray.NMS.DatabaseEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ExceptionTrial {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionTrial.class);
    public static void main(String[] args) {
        try {
            System.out.println(4/0);
        }catch (Exception exception){
           // System.out.println(exception.getMessage());
           // System.out.println(Arrays.toString(exception.getStackTrace()));
           // System.out.println(exception.getMessage());
            LOGGER.error("exception occurred :",exception);
           // exception.printStackTrace();
        }
    }
}
