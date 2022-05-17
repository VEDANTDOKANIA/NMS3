import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.NuProcessHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class NUProcess {
    public static void main(String[] args) throws InterruptedException {
        NuProcessBuilder processBuilder = new NuProcessBuilder(Arrays.asList("fping","-c","3","-t","1000","-q", "10.20.4.140"));
        ProcessHandler handler = new ProcessHandler();
        processBuilder.setProcessListener(handler);
        var process = processBuilder.start();
        handler.onStart(process);
       process.waitFor(10000,TimeUnit.MILLISECONDS);
        handler.output().onComplete( context ->{
            if(context.succeeded()){
                System.out.println(context.result());
            }
        });


    }
}
