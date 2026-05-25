import org.mindrot.jbcrypt.BCrypt;

public class TestHash {
    public static void main(String[] args) {
        String hash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewJyoui/R6VoCPuO";
        System.out.println("Admin@1234: " + BCrypt.checkpw("Admin@1234", hash));
        System.out.println("Admin@123!: " + BCrypt.checkpw("Admin@123!", hash));
        
        // Generate new hash for Admin@1234
        System.out.println("New Hash for Admin@1234: " + BCrypt.hashpw("Admin@1234", BCrypt.gensalt(12)));
    }
}
