package com.mindarray.NMS;

import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.nio.ByteBuffer;

public class ProcessHandler extends NuAbstractProcessHandler {
    private NuProcess nuProcess;
    private int statusCode =  0;
    private String data = null;

    @Override
    public void onStart(NuProcess nuProcess) {
        this.nuProcess = nuProcess;

    }

    @Override
    public void onExit(int code) {
        statusCode = code;
    }


    public void onStdout(ByteBuffer buffer, boolean closed) {
        if (!closed) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            System.out.println(new String(bytes));
            data = new String(bytes);
            nuProcess.closeStdin(true);
        }
    }
    public void onStderr(ByteBuffer buffer, boolean closed){
        if (!closed) {
             var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
           data=  new String(bytes);
            nuProcess.closeStdin(true);
        }

    }
    public String output() {
        return data;
    }

}
