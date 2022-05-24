import com.mindarray.Bootstrap;
import io.vertx.core.WorkerExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class workerThreadPool {
    public static void main(String[] args) {
        AtomicInteger count = new AtomicInteger(10);
        WorkerExecutor executor = Bootstrap.getVertx().createSharedWorkerExecutor("my-worker-pool", 10, 50000, TimeUnit.MILLISECONDS);
        for(int i = 0; i< count.get(); i++){
            //queue check karu and i ko -1 karu
            count.decrementAndGet();
            executor.executeBlocking(handler ->{
            }).onComplete(completeHandler ->{
                count.getAndIncrement();
            });
        }
    }
}
