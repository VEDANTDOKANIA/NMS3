package com.mindarray.NMS;

import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;

import java.nio.ByteBuffer;

public class ProcessHandler extends NuAbstractProcessHandler {
    private NuProcess nuProcess;
    private int statusCode = 0;
    private String data = null;

    @Override
    public void onStart(NuProcess nuProcess) {
        this.nuProcess = nuProcess;

    }

    @Override
    public void onExit(int code) {
        code = statusCode;
    }

    @Override
    public void onStdout(ByteBuffer buffer, boolean closed) {
        if (!closed) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            data = new String(bytes);
            nuProcess.closeStdin(true);
        }
    }

    @Override
    public void onStderr(ByteBuffer buffer, boolean closed) {
        if (!closed) {
            var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            data = new String(bytes);
            nuProcess.closeStdin(true);
        }

    }

    public String output() {
        return data;
    }

}
