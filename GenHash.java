import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("admin123");
        System.out.println("HASH=" + hash);
        System.out.println("MATCH=" + encoder.matches("admin123", hash));
    }
}
