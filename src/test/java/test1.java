import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class test1 {
    public static final HashMap<String,String> hp = new HashMap<>();
    public static void main(String[] args) {
        hp.put("hi","hi");
        hp.put("hh","hh");
        hp.remove("hi");
        System.out.println(hp);
    }
}
