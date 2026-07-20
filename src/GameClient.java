import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.border.TitledBorder;

public class GameClient {

    private final String serverIp;
    private static final int SERVER_PORT = 9090;

    // Background image is bundled on the classpath (src/math.jpg) so the game
    // works no matter which directory it is launched from.
    static final ImageIcon BACKGROUND = loadBackground();

    private static ImageIcon loadBackground() {
        java.net.URL url = GameClient.class.getResource("/math.jpg");
        return url != null ? new ImageIcon(url) : new ImageIcon();
    }

    // Networking and GUI components
    private Socket socket; // Socket for communication with the server
    private BufferedReader in; // Input stream to receive messages from the server
    private PrintWriter out; // Output stream to send messages to the server
    private String username; // Username of the player
    private String myGameRoomId="";
    private boolean  startGame = true;
    private String winner;
    // GUI components
    private JTextArea chatArea; // Text area to display chat and server messages
    private DefaultListModel<String> playerListModel; // List model for connected players
    private DefaultListModel<String> waitingListModel; // List model for players in the waiting room
    private DefaultListModel<String> playRoomListModel = new DefaultListModel<>(); // List model for players in game rooms
    private Timer gameRoomUpdateTimer; // Polls for game start and switches to the board
    private ScoreUpdateListener scoreListener;
    private JList<String> circularPlayerList;
    private boolean isGameEnded = false; // Flag to check if the game has ended
    private JFrame mainFrame;

   public GameClient(String username, String serverIp) {
        this.username = username;
        this.serverIp = serverIp;
       createGUI();
        connectToServer();  }

private void createGUI() {
        mainFrame = new JFrame("Game Quick Count");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);
        mainFrame.setLayout(new BorderLayout());
      


        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        CardLayout cardLayout = new CardLayout();

        JPanel cardPanel = new JPanel(cardLayout) {
        @Override
        protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(BACKGROUND.getImage(), 0, 0, getWidth(), getHeight(), this);
    }
};
        JLabel titleLabel = new JLabel("قائمة المتصلين في العد السريع", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Dubai", Font.BOLD, 40));
        titleLabel.setForeground(new Color(255, 255, 255));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 10, 0));

        JPanel connectedPlayersPanel = new JPanel(new BorderLayout());
        playerListModel = new DefaultListModel<>();
        JList<String> playerList = new JList<>(playerListModel);
        JScrollPane playerScrollPane = new JScrollPane(playerList);
        JPanel playerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,0,50));
        playerScrollPane.setPreferredSize(new Dimension(400, 300));
        playerPanel.add(playerScrollPane);
        playerList.setBackground(new Color(240, 248, 255));
        playerList.setForeground(new Color(25, 25, 112));
        playerList.setSelectionBackground(new Color(100, 149, 237));

        playerList.setFont(new Font("Dubai", Font.PLAIN, 20));
        playerScrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180), 2));



        playerScrollPane.getVerticalScrollBar().setUnitIncrement(16);


        JButton pairRequestButton = new JButton("ابدأ اللعبة ");
        pairRequestButton.setOpaque(true);
        pairRequestButton.setContentAreaFilled(true);



        // Enhance the appearance of the "Join Game" button
pairRequestButton.setBackground(new Color(240,248,255)); // Set a vibrant blue background color
pairRequestButton.setForeground(new Color(25,25,112)); // Set the text color to white for better contrast
pairRequestButton.setFont(new Font("Dubai", Font.BOLD, 20)); // Increase font size and make it bold
pairRequestButton.setFocusPainted(false); // Remove focus border when clicked
pairRequestButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // Add internal padding

// Add rounded corners using a custom Border
pairRequestButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(70, 130, 180), 2, true), // Rounded border with darker blue color
        BorderFactory.createEmptyBorder(10, 20, 10, 20) // Internal padding for better spacing
));

// Add hover effect to change background color on mouse over
pairRequestButton.addMouseListener(new java.awt.event.MouseAdapter() {
    @Override
    public void mouseEntered(java.awt.event.MouseEvent evt) {
        pairRequestButton.setBackground(new Color(244, 255, 255)); // Darker shade on hover
    }

    @Override
    public void mouseExited(java.awt.event.MouseEvent evt) {
        pairRequestButton.setBackground(new Color(240, 248, 255)); // Revert to the original color
    }
});



        playerPanel.setOpaque(false);
        playerScrollPane.setOpaque(false);
        playerList.setOpaque(false);



        connectedPlayersPanel.setOpaque(false);
        connectedPlayersPanel.add(titleLabel, BorderLayout.NORTH);
        connectedPlayersPanel.add(playerPanel, BorderLayout.CENTER);
        connectedPlayersPanel.add(pairRequestButton, BorderLayout.SOUTH);

// Create a panel for the title of the waiting room
JLabel waitingRoomTitle = new JLabel("اللاعبين الذين سيتحدونك بعد قليل", SwingConstants.CENTER);
waitingRoomTitle.setFont(new Font("Dubai", Font.BOLD, 40)); // Adjust font size and style
waitingRoomTitle.setForeground(new Color(255, 255, 255)); // Set text color
waitingRoomTitle.setBorder(BorderFactory.createEmptyBorder(50, 0, 0, 0)); // Add padding for spacing
waitingRoomTitle.setAlignmentX(Component.CENTER_ALIGNMENT);


        JPanel waitingRoomContainer = new JPanel(new BorderLayout());
        waitingRoomContainer.setLayout(new BoxLayout(waitingRoomContainer, BoxLayout.Y_AXIS));
        waitingRoomContainer.setOpaque(false); // Make it transparent to blend with background

         waitingListModel = new DefaultListModel<>();
         JList<String> waitingList = new JList<>(waitingListModel);
         JScrollPane waitingScrollPane = new JScrollPane(waitingList);

waitingScrollPane.setPreferredSize(new Dimension(400, 300)); // Same width as connected players list
waitingList.setBackground(new Color(240, 248, 255));
waitingList.setForeground(new Color(25, 25, 112));
waitingList.setSelectionBackground(new Color(100, 149, 237));
waitingList.setFont(new Font("Dubai", Font.PLAIN, 20));
waitingScrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180), 2));
waitingScrollPane.getVerticalScrollBar().setUnitIncrement(16);
// Create a panel to hold the list and apply centered alignment
JPanel waitingRoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 50));
waitingRoomPanel.add(waitingScrollPane);
waitingRoomPanel.setOpaque(false); // Make it transparent to blend with background


// Add title and list to the container
waitingRoomContainer.add(Box.createVerticalStrut(20)); // Add spacing above the title
waitingRoomContainer.add(waitingRoomTitle);
waitingRoomContainer.add(Box.createVerticalStrut(10)); // Add spacing below the title
waitingRoomContainer.add(waitingRoomPanel);
waitingRoomContainer.add(Box.createVerticalStrut(10)); // Add spacing below the list

// Add the waiting room container to the main panel
connectedPlayersPanel.add(waitingRoomContainer, BorderLayout.EAST);




      
         cardPanel.add(connectedPlayersPanel, "ConnectedPlayers");
         cardPanel.add(waitingRoomContainer, "WaitingRoom");
         cardLayout.show(cardPanel, "ConnectedPlayers");
         
         //mainFrame.add(createChatPanel(), BorderLayout.EAST);
         mainFrame.add(cardPanel);
         mainFrame.setLocationRelativeTo(null);
         mainFrame.setVisible(true);

  gameRoomUpdateTimer = new Timer(1000, e -> {
   // requestGameRooms();
    if (startGame==false ) {

       SwingUtilities.invokeLater(() -> {
        mainFrame.dispose();
        Sudoku Sudoku= new Sudoku();
        setScoreListener(Sudoku);
       });
        gameRoomUpdateTimer.stop();
    }

});


   pairRequestButton.addActionListener(e -> {
    if (out != null) {
        out.println("PAIR_REQUEST " + username);

       SwingUtilities.invokeLater(() -> {
            cardLayout.show(cardPanel, "WaitingRoom");
              gameRoomUpdateTimer.start();
        });
    }
});
}



   /* private JPanel createLabeledPanel(String title, JScrollPane content) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title, JLabel.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }*/
public interface ScoreUpdateListener {
    void onScoreUpdate(Map<String, Integer> scores);
}
 public void setScoreListener(ScoreUpdateListener listener) {
    this.scoreListener = listener;
}
     private JPanel backgroundPanel;
     JPanel boardPanel = new JPanel();
    JPanel buttonsPanel = new JPanel();
    Timer countdownTimer; 

public class Sudoku implements ScoreUpdateListener{
   

   @Override
   public void onScoreUpdate(Map<String, Integer> scores) {
        updatePlayRoomListWithScores1(scores);
    }
     
    class Tile extends JButton {
        int r, c;
        Tile(int r, int c) {
            this.r = r;
            this.c = c;
        }
    }

    int boardWidth = 800;
    int boardHeight = 600;
    int currentLevel = 0;

    String[][] puzzles = {
        {   // Easy (7x7)
            "- * + 8 = 17 -",
            "- - - + - - -",
            "- 5 - * - - -",
            "5 + * = 13 - -",
            "- 4 - 13 - - -",
            "- = - - - - -",
            "- * + 8 = * -"
        },
        {   // Medium (9x9 placeholder)
           "11 - * - 40 - - - -",
            "+ - _ - _ - - - -",
            "14 + 19 = * - 3 - -",
            "= - = - = - + - -",
            "* - * + * = * - *",
            "- - - - - - = - +",
            "- - - - 7 + * = 37",
            "- - - - - - - - =",
            "- - - - * + 15 = 41"
           
        },
        {   // Hard (11x11 placeholder)
            "- - - - * + 12 = * - -",
            "- - - - + - - - _ - -",
            "- - - - 4 - * _ 11 = 27",
            "- - - - = - _ - = - -",
            "- - 6 + * = 17 - * - -",
            "- - + - - - = - - - -",
            "* _ * = 5 - * + * = 35",
            "- - = - - - - - - - +",
            "- - 23 - - - - - - - 13",
            "- - - - - - - - - - =",
            "- - - - - - - - - - *"
        }
    };

    String[][] solutions = {
        {   // Easy
            "- 9 + 8 = 17 -",
            "- - - + - - -",
            "- 5 - 5 - - -",
            "5 + 8 = 13 - -",
            "- 4 - 13 - - -",
            "- = - - - - -",
            "- 9 + 8 = 17 -"
        },
        {   // Medium (dummy solution)
            "11 - 39 - 40 - - - -",
            "+ - _ - _ - - - -",
            "14 + 19 = 33 - 3 - -",
            "= - = - = - + - -",
            "25 - 20 + 7 = 27 - 4",
            "- - - - - - = - +",
            "- - - - 7 + 30 = 37",
            "- - - - - - - - =",
            "- - - - 26 + 15 = 41"
        },
        {   // Hard (dummy solution)
            "- - - - 7 + 12 = 19 - -",
            "- - - - + - - - _ - -",
            "- - - - 4 - 38 _ 11 = 27",
            "- - - - = - _ - = - -",
            "- - 6 + 11 = 17 - 8 - -",
            "- - + - - - = - - - -",
            "22 _ 17 = 5 - 21 + 14 = 35",
            "- - = - - - - - - - +",
            "- - 23 - - - - - - - 13",
            "- - - - - - - - - - =",
            "- - - - - - - - - - 48"
        }
    };

    JFrame frame = new JFrame("Game Quick Count");
    //JPanel boardPanel = new JPanel();
    //JPanel buttonsPanel = new JPanel();
    JButton numSelected = null;
    JPanel timerPanel = new JPanel();  // Panel to hold the timer label
    JLabel timerLabel;  // Label to display the countdown
    int remainingTime = 300; 

    Sudoku() {
        frame.setSize(boardWidth, boardHeight);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
         

               // Set up the background
 backgroundPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(BACKGROUND.getImage(), 0, 0, getWidth(), getHeight(), this);
        }
    };
backgroundPanel.setLayout(new BorderLayout());
backgroundPanel.setOpaque(true);
backgroundPanel.setPreferredSize(new Dimension(boardWidth, boardHeight));
frame.setContentPane(backgroundPanel);
frame.setVisible(true);

        boardPanel.setLayout(new GridLayout(7, 7));
        boardPanel.setPreferredSize(new Dimension(500, 500));
        boardPanel.setOpaque(false);
        setupTiles();
      
        backgroundPanel.add(boardPanel, BorderLayout.CENTER);

        buttonsPanel.setLayout(new GridLayout(2, 9));
        buttonsPanel.setOpaque(false);
        setupButtons();
        backgroundPanel.add(buttonsPanel, BorderLayout.SOUTH);
        setupCircularPlayerList(); 
        scoreListener = this::updatePlayRoomListWithScores1;

     
// Create the timer label
timerLabel = new JLabel("Time Left: " + remainingTime + "s", SwingConstants.CENTER);
timerLabel.setFont(new Font("Arial", Font.BOLD, 20));
timerLabel.setForeground(Color.WHITE);

// Create the timer panel with FlowLayout aligned to the right
timerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
timerPanel.setOpaque(false);  // Make the panel transparent
timerPanel.add(timerLabel);  // Add the timer label to the panel

// Create a container for the timer panel
JPanel timerContainer = new JPanel();
timerContainer.setLayout(new BoxLayout(timerContainer, BoxLayout.X_AXIS)); // Same BoxLayout to avoid stretching
timerContainer.setOpaque(false);  // Make the container transparent
timerContainer.add(timerPanel, BorderLayout.EAST);  // Align timer to the right

// Add the timer container to the backgroundPanel at the top
backgroundPanel.add(timerContainer, BorderLayout.NORTH);  // Place it at the top-center

        
        frame.setVisible(true);
        showRulesDialog();
        startCountdownTimer(); // Start the timer here
        
        // Inside the Sudoku class constructor, after setting up other components
// In the Sudoku constructor:
JButton exitButton = new JButton("خروج");
exitButton.setFont(new Font("Arial", Font.BOLD, 20));
exitButton.setBackground(Color.RED);
exitButton.setForeground(Color.WHITE);
exitButton.setFocusable(false);

exitButton.addActionListener(e -> {
    int confirm = JOptionPane.showConfirmDialog(frame, 
        "هل أنت متأكد أنك تريد مغادرة اللعبة؟", 
        "تأكيد الخروج", 
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);
    
            
    if (confirm == JOptionPane.YES_OPTION) {        
        out.println("PLAYER_DISCONNECT:" + username);
        SwingUtilities.invokeLater(() -> {
            frame.dispose();
            System.exit(0);
            
        });
    }
    
    if(2 == playRoomListModel.getSize()){
                System.out.println(playRoomListModel.getSize());
                endGame(winner);
            }
    
});

// Add the button to your UI (e.g., timerPanel)
timerPanel.add(exitButton);
    }
    
 private void startCountdownTimer() {
     countdownTimer = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            remainingTime--;
            timerLabel.setText("Time Left: " + remainingTime + "s");

            if (remainingTime <= 0) {
                endGame(winner);
                ((Timer) e.getSource()).stop();
                     }
        }
    });
    countdownTimer.start();
}

    private void setupCircularPlayerList() {
        circularPlayerList = new JList<>(playRoomListModel);
        circularPlayerList.setFont(new Font("Arial", Font.BOLD, 18));
        circularPlayerList.setBackground(Color.white);
        circularPlayerList.setPreferredSize(new Dimension(200, 200));
        
        
TitledBorder titleBorder = BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(Color.BLACK),
        "اللاعبون & النقاط",
        TitledBorder.RIGHT, 
        TitledBorder.TOP, 
        new Font("Arial", Font.BOLD, 20),
        Color.black
    );
       circularPlayerList.setBorder(titleBorder);
         circularPlayerList.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(circularPlayerList);
        scrollPane.setPreferredSize(new Dimension(220, 200));
        scrollPane.getViewport().setBackground(Color.white);
        
        JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS)); 
    rightPanel.add(Box.createVerticalGlue());
    rightPanel.add(scrollPane); 
    rightPanel.add(Box.createVerticalGlue()); 
    rightPanel.setOpaque(false);
    backgroundPanel.add(rightPanel, BorderLayout.EAST);
    }

 private void updatePlayRoomListWithScores1(Map<String, Integer> scores) {
    SwingUtilities.invokeLater(() -> {
        playRoomListModel.clear();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            playRoomListModel.addElement(entry.getKey() + " - " + entry.getValue());
        }
    });
}

void showRulesDialog() {
    JDialog rulesDialog = new JDialog(frame, "قوانين اللعبة", true);
    rulesDialog.setSize(500, 500);
    rulesDialog.setLocationRelativeTo(frame);
    rulesDialog.setLayout(new BorderLayout());
    

    JLabel rulesLabel = new JLabel("<html><center>🎮 مرحبًا بك في لعبة العد السريع!<br><br>" +
            "📜 الهدف: حل الألغاز بإدخال الأرقام الصحيحة في المربعات.<br>" +
            "🏆 أول لاعب يكمل المرحلة يحصل على 10 نقاط!<br><br>" +
            "⌛ استمع للصوت، ثم ستبدأ اللعبة تلقائيًا.</center></html>", SwingConstants.CENTER);
    rulesDialog.add(rulesLabel, BorderLayout.CENTER);
    rulesLabel.setFont(new Font("Arial", Font.BOLD, 20));
    rulesDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    // Safety net: always close the (modal) rules dialog after a few seconds,
    // even if the intro sound is missing or fails, so the game never freezes.
    Timer autoClose = new Timer(6000, e -> rulesDialog.dispose());
    autoClose.setRepeats(false);
    autoClose.start();

    new Thread(() -> playSoundAndCloseDialog("/sounds/info2.wav", rulesDialog)).start();
    rulesDialog.setVisible(true);
}

void playSoundAndCloseDialog(String soundResource, JDialog dialog) {
    try {
        java.net.URL url = getClass().getResource(soundResource);
        if (url == null) {
            throw new RuntimeException("⚠ الملف الصوتي غير موجود: " + soundResource);
        }

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        clip.start();


        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (clip.isRunning()) {
                    clip.stop();
                    clip.close();
                }
            }
        });

        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                SwingUtilities.invokeLater(dialog::dispose);
            }
        });

    } catch (Exception e) {
        e.printStackTrace();
        // If the sound could not play, close the dialog immediately so the
        // modal does not block the game waiting for a STOP event that never comes.
        SwingUtilities.invokeLater(dialog::dispose);
    }
}


    void setupTiles() {
        boardPanel.removeAll();
        int size = puzzles[currentLevel].length;
        boardPanel.setLayout(new GridLayout(size, size));
        
        for (int r = 0; r < size; r++) {
            String[] rowValues = puzzles[currentLevel][r].split(" ");
            for (int c = 0; c < size; c++) {
                Tile tile = new Tile(r, c);
                String tileValue = rowValues[c];
                
                tile.setFont(new Font("Arial", Font.PLAIN, 20));
                tile.setText((tileValue.equals("*") || tileValue.equals("-")) ? "" : tileValue);

                
                tile.setBackground(tileValue.equals("-") ? Color.white : Color.lightGray);
                tile.setBorder(BorderFactory.createLineBorder(Color.black));
                tile.setFocusable(false);
                
                boardPanel.add(tile);
                tile.addActionListener(e -> checkTile(tile));
            }
        }
        boardPanel.revalidate();
        boardPanel.repaint();
    }

    void checkTile(Tile tile) {
        if (numSelected != null) {
            String numSelectedText = numSelected.getText();
            String correctValue = solutions[currentLevel][tile.r].split(" ")[tile.c];

            if (correctValue.equals(numSelectedText)) {
                tile.setText(numSelectedText);
                tile.setFont(new Font("Arial", Font.BOLD, 20));
                tile.setBackground(Color.GREEN);
                tile.setOpaque(true);
                tile.setBorderPainted(false);
                
                new Thread(() -> playSound("/sounds/ahsant.wav")).start();
        JOptionPane.showMessageDialog(null, "إجابة صحيحة! 🎉", "تهانينا", JOptionPane.INFORMATION_MESSAGE);

                if (isLevelComplete()) {
                    notifyServerPlayerFinished(); 
                    nextLevel();
                    
                }
            }
            else {
                tile.setBackground(Color.red);
                new Thread(() -> playSound("/sounds/rong.wav")).start();

            JOptionPane.showMessageDialog(null, "إجابة خاطئة، حاول مرة أخرى!", "النتيجة", JOptionPane.ERROR_MESSAGE);
        }
        }
    }
    void playSound(String soundResource) {
    try {
        java.net.URL url = getClass().getResource(soundResource);
        if (url == null) {
            throw new RuntimeException("⚠ الملف الصوتي غير موجود: " + soundResource);
        }
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        clip.start();
    } catch (Exception e) {
        e.printStackTrace();
    }
}

    boolean isLevelComplete() {
        for (int r = 0; r < puzzles[currentLevel].length; r++) {
            String[] rowValues = puzzles[currentLevel][r].split(" ");
            for (int c = 0; c < rowValues.length; c++) {
                if (rowValues[c].equals("*")) {  // Only check if * tiles are filled
                    Component tile = boardPanel.getComponent(r * rowValues.length + c);
                    if (tile instanceof JButton && ((JButton) tile).getText().equals("")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
     private void notifyServerPlayerFinished() {
         out.println("LEVEL_COMPLETE:" + username+ ":" + currentLevel);

    }
    

    void nextLevel() {
        if (currentLevel < puzzles.length - 1) {
            currentLevel++;
            setupTiles();
            setupButtons();
        }
    }
   

    void setupButtons() {
        buttonsPanel.removeAll();
    
        // Define number sets for each level
        int[][] levelNumbers = {
            {17, 5, 9, 8, 9},    // Easy Level Numbers
            {20, 39, 4, 27, 33, 26, 30, 25, 7},    // Medium Level Numbers
            {17, 38, 11, 22, 8, 7, 21, 48, 14, 19} // Hard Level Numbers
        };
    
        // Get the correct number set for the current level
        int[] numbers = levelNumbers[currentLevel];
    
        for (int num : numbers) {
            JButton button = new JButton(String.valueOf(num));
            button.setFont(new Font("Arial", Font.BOLD, 20));
            button.setFocusable(false);
            button.setBackground(Color.white);
            buttonsPanel.add(button);
    
            button.addActionListener(e -> {
                if (numSelected != null) numSelected.setBackground(Color.white);
                numSelected = (JButton) e.getSource();
                numSelected.setBackground(Color.lightGray);
            });
        }
    
        buttonsPanel.revalidate();
        buttonsPanel.repaint();
    }
    
 
}

  // Method to connect to the server
    private void connectToServer() {
        try {
            socket = new Socket(serverIp, SERVER_PORT); // Create a socket connection to the server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Set up input stream
            out = new PrintWriter(socket.getOutputStream(), true); // Set up output stream

            out.println("CONNECT " + username); // Send the username to the server
            chatArea.append("Connected to the server as " + username + "\n"); // Display connection message

            new Thread(new ServerListener()).start(); // Start a thread to listen for server messages
        } catch (IOException e) {
            chatArea.append("Failed to connect to server.\n"); // Display error message if connection fails
        }
    }

      public void endGame(String Winner) {
              if (!isGameEnded) { // Check if the game hasn't ended yet
                  isGameEnded = true;
                  
                
                  if (Winner == null){
           JOptionPane.showMessageDialog(null, "انتهت اللعبه لا يوجد فائز 😢");
       }else{
       JOptionPane.showMessageDialog(null, Winner +" :انتهت اللعبه الفائز هو" );}
       
    // Disable buttons in boardPanel
for (Component comp : boardPanel.getComponents()) {
    if (comp instanceof JButton) {
        comp.setEnabled(false);
    }
}

// Disable buttons in buttonsPanel
for (Component comp : buttonsPanel.getComponents()) {
    if (comp instanceof JButton) {
        comp.setEnabled(false);
    }
}

    countdownTimer.stop();
}
      
}
      
     
     
  private class ServerListener implements Runnable {
    public void run() {
        startGame = true;
        try {
            String response;
            while ((response = in.readLine()) != null) {
                if (response.startsWith("PLAYER_LIST")) {
                    updatePlayerList(response.substring(12));
                } else if (response.startsWith("WAITING_ROOM")) {
                    updateWaitingList(response.substring(13));
                } else if (response.startsWith("PLAYER_JOINED")) {
                    chatArea.append(response.substring(14) + " joined the waiting room.\n");
                } else if (response.startsWith("GAME_START")) {
                    startGame = false;
                    
                    String[] parts = response.split(":");
                    if (parts.length > 1) {
                        String roomId = parts[1];
                        myGameRoomId = roomId;

                        Timer autoUpdateScores = new Timer(5000, null);
                        autoUpdateScores.addActionListener(e -> {
                            if (playRoomListModel.getSize() > 0) {
                                autoUpdateScores.stop();
                            } else {
                                out.println("GET_GAMEROOM_PLAYERS:" + roomId);
                                out.flush();
                            }
                        });
                        autoUpdateScores.start();

                        chatArea.append("Game started! You are in Room: " + roomId + "\n");
                    }
                } else if (response.startsWith("ROOM_PLAYERS:")) {
                    handleRoomPlayers(response);
                } else if (response.startsWith("SCORE_UPDATE")) {
                    handleScoreUpdate(response);
                } else if (response.startsWith("WINNER:")) {
                    String[] parts = response.split(":");
                    if (parts.length >= 4 && parts[2].equals("LEVEL")) {
                        winner = parts[1];
                        String levelStr = parts[3];
                        int level = Integer.parseInt(levelStr);
                    
                        if(2 == level){
                            endGame(winner);
                        }else if(1 == playRoomListModel.getSize()){
                            endGame(winner);
                        } else {       
                            JOptionPane.showMessageDialog(null, 
                                winner + " فاز في المرحلة رقم " + (level + 1) + "! 🎉",
                                "فوز مرحلة", JOptionPane.INFORMATION_MESSAGE);
                        }
                        
                        if (myGameRoomId != null && !myGameRoomId.isEmpty()) {
                            out.println("GET_GAMEROOM_PLAYERS:" + myGameRoomId);
                            out.flush();
                        }
                    }
                } else if (response.startsWith("TIMER_START")) {
                    try {
                        int delay = Integer.parseInt(response.substring(12).trim());
                        startCountdown(delay);
                    } catch (NumberFormatException e) {
                        chatArea.append("Invalid timer delay received from server.\n");
                    }
                } else if (response.startsWith("PLAYER_LEFT:")) {
                    String[] parts = response.split(":");
                    if (parts.length >= 2) {
                        String playerLeft = parts[1];
                        chatArea.append(playerLeft + " has left the game.\n");
                        
                        // Request updated player list to refresh scores
                        if (myGameRoomId != null && !myGameRoomId.isEmpty()) {
                            out.println("GET_GAMEROOM_PLAYERS:" + myGameRoomId);
                        }
                        
                        if (playerLeft.equals(username)) {
                            SwingUtilities.invokeLater(() -> {
                                GameClient.this.mainFrame.dispose();
                                System.exit(0);
                            });
                        }
                    }
                } else {
                    chatArea.append(response + "\n");
                }
            }
        } catch (IOException e) {
            chatArea.append("Disconnected from server.\n");
        }
    }
}

private void handleScoreUpdate(String message) {
    // Server format: "SCORE_UPDATE:name,score:name,score:..."
    // Each colon-separated part is a "name,score" pair.
    String data = message.replace("SCORE_UPDATE:", "");
    String[] parts = data.split(":");
    Map<String, Integer> scores = new HashMap<>();

    for (String part : parts) {
        String[] playerData = part.split(",");
        if (playerData.length == 2 && !playerData[0].isEmpty()) {
            try {
                int score = Integer.parseInt(playerData[1]);
                scores.put(playerData[0], score);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing score for player: " + playerData[0]);
            }
        }
    }

    if (scoreListener != null) {
        scoreListener.onScoreUpdate(scores);
    } else {
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        SwingUtilities.invokeLater(() -> {
            playRoomListModel.clear();
            for (Map.Entry<String, Integer> entry : sortedScores) {
                playRoomListModel.addElement(entry.getKey() + ": " + entry.getValue());
            }
        });
    }
}

private void handleRoomPlayers(String response) {
    String[] parts = response.split(":");
    if (parts.length >= 2) {
        String roomId = parts[1];
        myGameRoomId = roomId;

        Map<String, Integer> playerScores = new HashMap<>();
        for (int i = 2; i < parts.length; i++) {
            String[] playerData = parts[i].split(",");
            if (playerData.length == 2) {
                try {
                    int score = Integer.parseInt(playerData[1]);
                    playerScores.put(playerData[0], score);
                } catch (NumberFormatException e) {
                    System.out.println("Error parsing score for player: " + playerData[0]);
                }
            }
        }

        if (scoreListener != null) {
            scoreListener.onScoreUpdate(playerScores);
        } 

        chatArea.append("Updated room players list.\n");
    } else {
        chatArea.append("Invalid ROOM_PLAYERS message received.\n");
    }
    
    
}




// Method to start a countdown timer on the client
private void startCountdown(int delay) {
    SwingUtilities.invokeLater(() -> {
        JLabel countdownLabel = new JLabel("ستبدأ اللعبة بعد" + delay + " ثانية...", SwingConstants.CENTER);
        countdownLabel.setFont(new Font("Dubai", Font.BOLD, 20));
        countdownLabel.setForeground(Color.black);

        JDialog countdownDialog = new JDialog();
        countdownDialog.setLayout(new BorderLayout());
        countdownDialog.add(countdownLabel, BorderLayout.CENTER);
        countdownDialog.setSize(300, 100);
        countdownDialog.setLocationRelativeTo(null);
        countdownDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        countdownDialog.setVisible(true);

        // Use AtomicInteger to make the delay mutable
        AtomicInteger remainingTime = new AtomicInteger(delay);

        // Update the countdown every second
        Timer timer = new Timer(1000, e -> {
            int currentTime = remainingTime.decrementAndGet(); // Decrement the time
            if (currentTime > 0) {
                countdownLabel.setText("ستبدأ اللعبة بعد " + currentTime + " ثانية...");
            } else {
                ((Timer) e.getSource()).stop();
                countdownDialog.dispose();
            }
        });
        timer.start();
    });
}
 
   // Method to update the player list in the GUI
    private void updatePlayerList(String players) {
        playerListModel.clear(); // Clear the current player list
        for (String player : players.split(", ")) {
            playerListModel.addElement(player); // Add each player to the list
        }
        System.out.print(playerListModel.getSize());
        if(1 == playerListModel.getSize() && !startGame ){
            startGame = true;
            endGame(winner);
        }
    }

    private void updateWaitingList(String waitingPlayers) {
    waitingListModel.clear(); // Clear the current waiting list
    if (!waitingPlayers.isEmpty()) {
        for (String player : waitingPlayers.split(", ")) {
            waitingListModel.addElement(player); // Add each player to the list
        }
    }
}



  


        // Main method to start the client
    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginScreen::new);  // Launch the GUI on the Event Dispatch Thread

    }
}

class LoginScreen {
    public LoginScreen() {
        JFrame frame = new JFrame("Game Login");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(GameClient.BACKGROUND.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
        };
        panel.setLayout(new GridBagLayout());

         GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
         gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel welcomeLabel = new JLabel("مرحبا بكم في لعبة العد السريع ", SwingConstants.CENTER);
        welcomeLabel.setForeground(new Color(255, 255, 255));
        welcomeLabel.setFont(new Font("Dubai", Font.BOLD, 40));
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 10, 0));
        welcomeLabel.setOpaque(false);


        panel.add(welcomeLabel, gbc);


        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        JTextField usernameField = new JTextField(27);
        panel.add(usernameField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;


        JLabel usernameLabel = new JLabel("أدخل الاسم للبدأ :");
        usernameLabel.setForeground(Color.WHITE);
        usernameLabel.setFont(new Font("Dubai", Font.BOLD, 25));
        panel.add(usernameLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        JTextField serverIpField = new JTextField("localhost", 27);
        panel.add(serverIpField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel serverIpLabel = new JLabel("عنوان السيرفر (IP) :");
        serverIpLabel.setForeground(Color.WHITE);
        serverIpLabel.setFont(new Font("Dubai", Font.BOLD, 25));
        panel.add(serverIpLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;

        gbc.anchor = GridBagConstraints.CENTER;



        JButton connectButton = new JButton("إتصل");
        connectButton.setFont(new Font("Dubai", Font.BOLD, 25));
        connectButton.setForeground(new Color(25,25,112));
        panel.add(connectButton, gbc);


        connectButton.setPreferredSize(new Dimension(150, 50));

        frame.revalidate();
        frame.repaint();

        frame.add(panel, BorderLayout.CENTER);

        connectButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String serverIp = serverIpField.getText().trim();
            if (!username.isEmpty() && !serverIp.isEmpty()) {
                frame.dispose();
                new GameClient(username, serverIp);
            }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}