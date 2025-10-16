import com.safesphere.ui.LoginScreen;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        System.out.println("SafeSphere started. Ready for login...");
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
    }
}
