import java.sql.Array;
import java.util.ArrayList;
import java.util.Scanner;

public class test1 {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        var array = new ArrayList<>();
        for(int i =0;i<10;i++){
            while (true){
                System.out.println("Enter value " + i);
                var value = scn.next();
                var flag = false;
                try{
                    var number = Integer.parseInt(value);
                    flag=true;
                }catch (Exception e){
                    flag =false;
                }
                if(flag){
                    array.add(value);
                    break;
                }
            }
        }
        System.out.println(array);
    }
}
