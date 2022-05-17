import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.nio.ByteBuffer;

public class ProcessHandler extends NuAbstractProcessHandler {
    private NuProcess nuProcess;
    private String pingData;
    private int statusCode =  0;

   private Promise<String> promise =Promise.promise();

    @Override
    public void onStart(NuProcess nuProcess) {
        this.nuProcess = nuProcess;

    }

    @Override
    public void onExit(int statusCodedefined) {
       statusCode = statusCodedefined;
    }


    public void onStdout(ByteBuffer buffer, boolean closed) {
        if (!closed) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            System.out.println(new String(bytes));
            nuProcess.closeStdin(true);
        }
    }
    public void onStderr(ByteBuffer buffer, boolean closed){
        byte[] bytes = new byte[0];
        if (!closed) {
             bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
         // System.out.println(new String(bytes));
            promise.complete(new String(bytes));
            nuProcess.closeStdin(true);
        }
     //   promise.complete(pingData);

    }
    public Future<String> output() throws InterruptedException {
        return promise.future();
    }
    public int exit(){
        return statusCode;
    }
   /* public Future<String> future(){
        return promise.future();
    }*/

}
