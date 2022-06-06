import com.mindarray.Bootstrap;

public class ver {
    public static void main(String[] args) {
        Bootstrap.getVertx().executeBlocking(handler ->{
            System.out.println(Thread.currentThread().getName());
            System.out.println("hi");
            handler.fail("hii");
            System.out.println(Thread.currentThread().getName());
            handler.complete();
            System.out.println(Thread.currentThread().getName());
        }).onComplete(completehandler ->{
            System.out.println(Thread.currentThread().getName());
            if(completehandler.succeeded()){
                System.out.println("hii");
            }else {
                System.out.println("failed");
            }
        });
    }
}
