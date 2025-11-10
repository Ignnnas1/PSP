import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            int boardWidth = 1200;
            int boardHeight = 800;

            JFrame frame = new JFrame("Šviesoforų meistras");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            Game game = new Game(boardWidth, boardHeight);
            frame.setContentPane(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            game.start(); 
        });
    }
}
