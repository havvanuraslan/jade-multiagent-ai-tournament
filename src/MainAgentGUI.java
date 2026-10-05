import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public class MainAgentGUI extends JFrame {

    private MainAgent myAgent;

    // Controls
    private JButton startButton, stopButton, resumeButton, resetButton, removePlayerButton, aboutButton;

    // Settings
    private JTextField rField;
    private JCheckBox detailCheck;

    // Display
    private JTextArea logArea;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;

    public MainAgentGUI(MainAgent agent) {
        super(agent.getLocalName());
        myAgent = agent;

        // Main Panel 
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // WEST: Controls and Settings 
        JPanel westPanel = new JPanel();
        westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));

        // Tournament Controls 
        JPanel tournamentControls = new JPanel(new GridLayout(0, 1, 5, 5));
        tournamentControls.setBorder(new TitledBorder("Tournament Controls"));

        startButton = new JButton("Start New Tournament"); 
        startButton.addActionListener(e -> startTournament());
        tournamentControls.add(startButton);

        stopButton = new JButton("Stop Tournament"); 
        stopButton.addActionListener(e -> stopTournament());
        stopButton.setEnabled(false);
        tournamentControls.add(stopButton);

        resumeButton = new JButton("Resume Tournament"); 
        resumeButton.addActionListener(e -> resumeTournament());
        resumeButton.setEnabled(false);
        tournamentControls.add(resumeButton);

        westPanel.add(tournamentControls);
        westPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Player Management 
        JPanel playerControls = new JPanel(new GridLayout(0, 1, 5, 5));
        playerControls.setBorder(new TitledBorder("Player Management"));

        resetButton = new JButton("Reset Statistics"); 
        resetButton.addActionListener(e -> myAgent.resetStatistics());
        playerControls.add(resetButton);

        removePlayerButton = new JButton("Remove Selected Player"); 
        removePlayerButton.addActionListener(e -> removeSelectedPlayer());
        playerControls.add(removePlayerButton);

        westPanel.add(playerControls);
        westPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Settings 
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(new TitledBorder("Settings"));

        JPanel rPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rPanel.add(new JLabel("Rounds (R):"));
        rField = new JTextField("500", 6); 
        rPanel.add(rField);
        settingsPanel.add(rPanel);

        detailCheck = new JCheckBox("Enable Detailed Log"); 
        settingsPanel.add(detailCheck);

        westPanel.add(settingsPanel);
        westPanel.add(Box.createVerticalGlue()); 

        // About 
        aboutButton = new JButton("About Author");
        aboutButton.addActionListener(e -> showAboutInfo());
        aboutButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        westPanel.add(aboutButton);

        mainPanel.add(westPanel, BorderLayout.WEST);

        // CENTER: Log Area
        logArea = new JTextArea(25, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(new TitledBorder("Event Log"));
        mainPanel.add(logScrollPane, BorderLayout.CENTER);

        // EAST: Player List & Scores
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane playerScrollPane = new JScrollPane(playerList);
        playerScrollPane.setPreferredSize(new Dimension(250, 0));
        playerScrollPane.setBorder(new TitledBorder("Players & Scores")); 
        mainPanel.add(playerScrollPane, BorderLayout.EAST);


        // Window Setup
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null); // Center on screen
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Let agent handle closing

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Tell the agent to terminate
                myAgent.doDelete();
            }
        });
    }

    // GUI Action Handlers 

    private void startTournament() {
        myAgent.startNewTournament();
        
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        resumeButton.setEnabled(false);
        resetButton.setEnabled(false);
        removePlayerButton.setEnabled(false);
    }

    private void stopTournament() {
        myAgent.stopTournament();
        
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        resumeButton.setEnabled(true);
        resetButton.setEnabled(true); // Can reset while paused
    }

    private void resumeTournament() {
        myAgent.resumeTournament();
        
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        resumeButton.setEnabled(false);
        resetButton.setEnabled(false);
    }

    private void removeSelectedPlayer() {
        String selectedPlayer = playerList.getSelectedValue();
        if (selectedPlayer != null) {
            // Extract just the name (e.g., from "1. player1 (Score: 0...)")
            String nameOnly = selectedPlayer.split(" ")[1];
            myAgent.removePlayer(nameOnly);
        } else {
            log("No player selected to remove.");
        }
    }

    private void showAboutInfo() {
        JOptionPane.showMessageDialog(this,
                "Intelligent Systems Programming (2025-2026)\n" +
                        "Author: Havva Nur Aslan\n" +
                        "Lab ID: 3",
                "About This Program",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // Public Methods for Agent to Call

    /**
     * Appends a message to the log area in a thread-safe way.
     */
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * Updates the list of players in the GUI in a thread-safe way.
     */
    public void updatePlayerList(List<String> players) {
        SwingUtilities.invokeLater(() -> {
            playerListModel.clear();
            for (String player : players) {
                playerListModel.addElement(player);
            }
        });
    }

    /**
     * Gets the R value from the settings.
     */
    public int getRValue() {
        try {
            return Integer.parseInt(rField.getText());
        } catch (NumberFormatException e) {
            log("Invalid R value. Using default 500.");
            return 500;
        }
    }

    /**
     * Checks if the detailed log checkbox is selected. 
     */
    public boolean isDetailedInfoEnabled() {
        return detailCheck.isSelected();
    }

    /**
     * Resets the button states when the tournament finishes or is reset.
     */
    public void resetButtons() {
         SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            resumeButton.setEnabled(false);
            resetButton.setEnabled(true);
            removePlayerButton.setEnabled(true);
         });
    }
}