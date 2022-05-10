import java.security.SecureRandom;
import java.util.Base64;

public class TestGenerate {
    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        byte[] buffer = new byte[4];
        random.nextBytes(buffer);
        System.out.println(encoder.encodeToString(buffer));
    }
}
