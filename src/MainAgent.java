import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.KillAgent;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.content.onto.basic.Action;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainAgent extends Agent {

    // Tournament Parameters
    private int N; // Total number of players 
    private int S; // Size of the payoff matrix 
    private int R; // Maximum number of rounds
    private int R_prime; // Effective rounds per game

    // State
    private ArrayList<AID> players = new ArrayList<>();
    private MainAgentGUI gui; 
    private int[] payoffMatrix; // S x S matrix
    private Random rand = new Random();

    // Tournament Management
    private ArrayList<GameMatch> tournamentSchedule = new ArrayList<>();
    private int currentGameIndex = 0;
    private Map<AID, Integer> tournamentScores = new HashMap<>();
    private Map<AID, Integer> gamesWon = new HashMap<>();

    // State for stop/resume
    private boolean tournamentRunning = false;
    private boolean tournamentPaused = false;
    private Behaviour currentGameBehaviour;

    protected void setup() {
        System.out.println("MainAgent " + getAID().getName() + " is ready.");
        getContentManager().registerLanguage(new jade.content.lang.sl.SLCodec());
        getContentManager().registerOntology(JADEManagementOntology.getInstance());
        // Launch the GUI on the Swing Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            gui = new MainAgentGUI(this);
            gui.setVisible(true);
        });

        // Register the agent with the Directory Facilitator (DF) 
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("tournament-manager");
        sd.setName("JADE-PSI-Tournament");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    protected void takeDown() {
        // Deregister from the DF
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Dispose the GUI
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.dispose());
        }
        System.out.println("MainAgent " + getAID().getName() + " terminating.");
    }

    // --- Public methods called by the GUI ---

    /**
     * Starts a new tournament. 
     * This is called by the GUI.
     */
    public void startNewTournament() {
        if (tournamentRunning) {
            gui.log("Error: A tournament is already in progress.");
            return;
        }

        // 1. Get parameters from GUI
        this.R = gui.getRValue(); 
        this.S = 4; // Default S=4 as it's not set in GUI. Can be changed. 
        gui.log("Starting new tournament... R=" + R + ", S=" + S);

        // 2. Discover all "player" agents
        discoverPlayers(); 
        this.N = players.size(); 
        if (N < 2) { 
            gui.log("Error: Need at least 2 players to start. Found " + N);
            gui.resetButtons();
            return;
        }
        gui.log("Discovered " + N + " players.");

        // 3. Reset stats and create schedule
        resetStatisticsInternal();
        updatePlayerListInGUIWithScores(); 
        createTournamentSchedule(); 

        // 4. Send initial info to all players
        sendInitialInfoToPlayers(); 

        // 5. Start the first game
        currentGameIndex = 0;
        tournamentRunning = true;
        tournamentPaused = false;
        if (!tournamentSchedule.isEmpty()) {
            startGame(tournamentSchedule.get(currentGameIndex)); 
        } else {
            gui.log("Tournament finished (no games to play).");
            tournamentRunning = false;
            gui.resetButtons();
        }
    }

    /**
     * Stops/Pauses the tournament. 
     */
    public void stopTournament() {
        if (!tournamentRunning || tournamentPaused)
            return;
        tournamentPaused = true;
        if (currentGameBehaviour != null) {
            gui.log("Tournament Paused. Will stop after current round.");
        }
    }

    /**
     * Resumes the tournament. 
     */
    public void resumeTournament() {
        if (!tournamentRunning || !tournamentPaused)
            return;
        tournamentPaused = false;
        gui.log("Tournament Resumed.");
        if (currentGameBehaviour.done()) {
            startNextGame();
        }
    }

    /**
     * Resets player statistics. 
     */
    public void resetStatistics() {
        if (tournamentRunning) {
            gui.log("Cannot reset stats while tournament is running.");
            return;
        }
        resetStatisticsInternal();
        gui.log("Statistics reset.");
        updatePlayerListInGUIWithScores();
    }

    /**
     * Removes a selected player from the internal list AND sends a kill
     * request to the JADE platform.
     */
    public void removePlayer(String playerLocalName) {
        if (tournamentRunning) {
            gui.log("Cannot remove players while tournament is running.");
            return;
        }

        AID playerToRemove = null;
        for (AID p : players) {
            if (p.getLocalName().equals(playerLocalName)) {
                playerToRemove = p;
                break;
            }
        }

        if (playerToRemove != null) {
            gui.log("Attempting to kill agent: " + playerLocalName);
            try {
                AID ams = getAMS();

                KillAgent ka = new KillAgent();
                ka.setAgent(playerToRemove); 

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(ams); 

                request.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
                request.setOntology(JADEManagementOntology.getInstance().getName());

                Action action = new Action(ams, ka);
                getContentManager().fillContent(request, action);

                send(request);

                players.remove(playerToRemove);
                N = players.size(); 
                gui.log("Player " + playerLocalName + " remove request sent.");

                resetStatisticsInternal();
                updatePlayerListInGUIWithScores();

            } catch (Exception e) {
                gui.log("Error trying to remove agent: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            gui.log("Could not find player " + playerLocalName + " in the list.");
        }
    }

    // Internal Tournament Logic

    private void resetStatisticsInternal() {
        tournamentScores.clear();
        gamesWon.clear();
        for (AID player : players) {
            tournamentScores.put(player, 0);
            gamesWon.put(player, 0);
        }
    }

    private void discoverPlayers() {
        players.clear();
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player"); 
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            for (int i = 0; i < result.length; ++i) {
                players.add(result[i].getName());
            }
            // Sort players to have a consistent ID order
            Collections.sort(players, (a1, a2) -> a1.getLocalName().compareTo(a2.getLocalName()));
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private void createTournamentSchedule() {
        tournamentSchedule.clear();
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                tournamentSchedule.add(new GameMatch(players.get(i), players.get(j)));
            }
        }
        // Total games = N * (N - 1) / 2 
        gui.log("Created schedule with " + tournamentSchedule.size() + " games.");
    }

    private void sendInitialInfoToPlayers() {
        for (int i = 0; i < players.size(); i++) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(players.get(i));
            // Format: Id#<id>#<N,S,R> 
            // Player ID 0 is the first player 
            String content = String.format("Id#%d#%d,%d,%d", i, N, S, R);
            msg.setContent(content);
            send(msg);
        }
        gui.log("Sent initial parameters to all players.");
    }

    private void startGame(GameMatch match) {
        gui.log("--- Starting Game " + (currentGameIndex + 1) + "/" + tournamentSchedule.size() + " ---");

        int idA = getPlayerId(match.player1);
        int idB = getPlayerId(match.player2);

        AID playerA, playerB;
        if (idA < idB) { // Player with lower id is player A
            playerA = match.player1;
            playerB = match.player2;
        } else {
            playerA = match.player2;
            playerB = match.player1;
            int temp = idA;
            idA = idB;
            idB = temp;
        }

        gui.log("Player A (ID " + idA + "): " + playerA.getLocalName());
        gui.log("Player B (ID " + idB + "): " + playerB.getLocalName());

        // 1. Generate symmetrical payoff matrix 
        generatePayoffMatrix();

        // 2. Calculate effective rounds R' 
        // R' in [R, 1.1*R]
        this.R_prime = R + rand.nextInt((int) (R * 0.1) + 1);
        gui.log("Game will have " + R_prime + " rounds (R=" + R + ").");

        // 3. Send NewGame message
        String newGameContent = String.format("NewGame#%d,%d", idA, idB);
        ACLMessage newGameMsg = new ACLMessage(ACLMessage.INFORM);
        newGameMsg.addReceiver(playerA);
        newGameMsg.addReceiver(playerB);
        newGameMsg.setContent(newGameContent);
        send(newGameMsg);

        // 4. Add a behavior to manage this specific game
        currentGameBehaviour = new GameCoordinatorBehaviour(playerA, playerB);
        addBehaviour(currentGameBehaviour);
    }

    private void generatePayoffMatrix() {
        payoffMatrix = new int[S * S * 2]; // Flat 3D array [row][col][payoffA/B]
        for (int i = 0; i < S; i++) {
            for (int j = 0; j < S; j++) {
                if (i == j) {
                    // Diagonal
                    int payoffA = rand.nextInt(10); // 0-9 
                    int payoffB = rand.nextInt(10); // 0-9
                    setPayoff(i, j, payoffA, payoffB);
                } else if (i < j) {
                    // Upper triangle
                    int payoffA = rand.nextInt(10);
                    int payoffB = rand.nextInt(10);
                    setPayoff(i, j, payoffA, payoffB);
                    // Set symmetrical part 
                    // (i,j)[0] == (j,i)[1] and (i,j)[1] == (j,i)[0]
                    setPayoff(j, i, payoffB, payoffA);
                }
            }
        }

        if (gui.isDetailedInfoEnabled()) { 
            gui.log("Generated " + S + "x" + S + " matrix:");
            logMatrix();
        }
    }

    // Helper for 3D matrix logic
    private void setPayoff(int row, int col, int payoffA, int payoffB) {
        payoffMatrix[(row * S + col) * 2 + 0] = payoffA;
        payoffMatrix[(row * S + col) * 2 + 1] = payoffB;
    }

    private int[] getPayoff(int row, int col) {
        return new int[] {
                payoffMatrix[(row * S + col) * 2 + 0], // Player A payoff
                payoffMatrix[(row * S + col) * 2 + 1] // Player B payoff
        };
    }

    private void logMatrix() {
        for (int i = 0; i < S; i++) {
            StringBuilder sb = new StringBuilder("Row " + i + ": ");
            for (int j = 0; j < S; j++) {
                int[] payoffs = getPayoff(i, j);
                sb.append(String.format("(%d,%d) ", payoffs[0], payoffs[1]));
            }
            gui.log(sb.toString());
        }
    }

    private int getPlayerId(AID playerAid) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).equals(playerAid)) {
                return i; // Player ID is their index in the sorted list
            }
        }
        return -1; 
    }

    private void endGame(AID playerA, AID playerB, int scoreA, int scoreB) {
        gui.log("--- Game Over ---");
        gui.log("Final Score: " + playerA.getLocalName() + " (A): " + scoreA + " | " +
                playerB.getLocalName() + " (B): " + scoreB);

        // Assign tournament points 
        if (scoreA > scoreB) {
            gui.log("Winner: " + playerA.getLocalName());
            tournamentScores.put(playerA, tournamentScores.get(playerA) + 3);
            gamesWon.put(playerA, gamesWon.get(playerA) + 1); 
        } else if (scoreB > scoreA) {
            gui.log("Winner: " + playerB.getLocalName());
            tournamentScores.put(playerB, tournamentScores.get(playerB) + 3);
            gamesWon.put(playerB, gamesWon.get(playerB) + 1); 
        } else {
            gui.log("Result: Tie");
            tournamentScores.put(playerA, tournamentScores.get(playerA) + 1);
            tournamentScores.put(playerB, tournamentScores.get(playerB) + 1);
        }

        // Send EndGame message 
        ACLMessage endMsg = new ACLMessage(ACLMessage.INFORM);
        endMsg.addReceiver(playerA);
        endMsg.addReceiver(playerB);
        endMsg.setContent("EndGame");
        send(endMsg);

        // Update GUI with scores 
        updatePlayerListInGUIWithScores();

        // Start next game
        startNextGame();
    }

    private void startNextGame() {
        if (tournamentPaused) {
            gui.log("Tournament is paused. Click Resume to continue.");
            return;
        }

        currentGameIndex++;
        if (currentGameIndex < tournamentSchedule.size()) {
            startGame(tournamentSchedule.get(currentGameIndex)); 
        } else {
            gui.log("--- TOURNAMENT FINISHED ---");
            displayFinalRanking();
            tournamentRunning = false;
            tournamentPaused = false;
            gui.resetButtons();
        }
    }

    private void updatePlayerListInGUIWithScores() {
        ArrayList<String> playerNamesForList = new ArrayList<>();

        // Create a sorted list for display
        List<AID> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers, (p1, p2) -> {
            int score1 = tournamentScores.getOrDefault(p1, 0);
            int score2 = tournamentScores.getOrDefault(p2, 0);
            if (score1 != score2) {
                return Integer.compare(score2, score1); // Higher score first
            }
            int wins1 = gamesWon.getOrDefault(p1, 0);
            int wins2 = gamesWon.getOrDefault(p2, 0);
            if (wins1 != wins2) {
                return Integer.compare(wins2, wins1); // Higher wins first
            }
            return p1.getLocalName().compareTo(p2.getLocalName()); // Alphabetical
        });

        boolean isFinalRanking = (currentGameIndex >= tournamentSchedule.size());
        if (isFinalRanking) {
            gui.log("Final Ranking:");
        }

        int rank = 1;
        for (AID player : sortedPlayers) {
            String name = player.getLocalName();
            int score = tournamentScores.getOrDefault(player, 0);
            int wins = gamesWon.getOrDefault(player, 0);

            String rankingString = String.format("%d. %s (Score: %d, Wins: %d)", rank++, name, score, wins);

            playerNamesForList.add(rankingString); 

            if (isFinalRanking) {
                gui.log(rankingString);
            }
        }
        gui.updatePlayerList(playerNamesForList); 
    }

    private void displayFinalRanking() {
        // The logic is already in updatePlayerListInGUIWithScores
        updatePlayerListInGUIWithScores();
    }

    // Inner class for a single Game Match
    private static class GameMatch {
        AID player1;
        AID player2;

        GameMatch(AID p1, AID p2) {
            this.player1 = p1;
            this.player2 = p2;
        }
    }

    // Inner class for Game Management Behaviour 
    private class GameCoordinatorBehaviour extends Behaviour {

        private AID playerA, playerB;
        private int currentRound = 0, scoreA = 0, scoreB = 0, state = 0;
        private int choiceA = -1, choiceB = -1;
        private MessageTemplate mtA, mtB;
        private long roundStartTime;

        private static final long ROUND_TIMEOUT_MS = 5000; // 5 second timeout

        GameCoordinatorBehaviour(AID playerA, AID playerB) {
            this.playerA = playerA;
            this.playerB = playerB;
        }

        public void action() {
            // If paused, just block and wait
            if (tournamentPaused) {
                block(1000); // Check every second if resumed
                return;
            }

            switch (state) {
                case 0:
                    // State 0: Request positions
                    if (currentRound >= R_prime) { 
                        state = 4; // Game over
                        break;
                    }
                    if (gui.isDetailedInfoEnabled()) {
                        gui.log("Round " + (currentRound + 1) + "/" + R_prime);
                    }

                    // Send REQUEST for Position to both players 
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(playerA);
                    request.addReceiver(playerB);
                    request.setContent("Position");
                    String convId = "game-" + currentGameIndex + "-round-" + currentRound;
                    request.setConversationId(convId);
                    myAgent.send(request);

                    // Prepare templates to receive replies

                    // Create the individual matching criteria
                    // (We can re-use these)
                    MessageTemplate mtPerformative = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    MessageTemplate mtConvId = MessageTemplate.MatchConversationId(convId);

                    // Template for Player A
                    MessageTemplate mtSenderA = MessageTemplate.MatchSender(playerA);
                    mtA = MessageTemplate.and(mtPerformative,
                            MessageTemplate.and(mtSenderA, mtConvId));

                    // Template for Player B
                    MessageTemplate mtSenderB = MessageTemplate.MatchSender(playerB);
                    mtB = MessageTemplate.and(mtPerformative,
                            MessageTemplate.and(mtSenderB, mtConvId));

                    choiceA = -1;
                    choiceB = -1;
                    roundStartTime = System.currentTimeMillis();
                    state = 1;
                    break;

                case 1: // State 1 & 2: Wait for replies (with timeout)
                case 2:
                    // Check for Player A's reply
                    ACLMessage replyA = myAgent.receive(mtA);
                    if (replyA != null && choiceA == -1) {
                        try {
                            // Format: Position#<row> 
                            choiceA = Integer.parseInt(replyA.getContent().split("#")[1]);
                            if (choiceA < 0 || choiceA >= S)
                                throw new NumberFormatException();
                        } catch (Exception e) {
                            gui.log("Error parsing Player A's choice. Defaulting to 0.");
                            choiceA = 0;
                        }
                    }

                    // Check for Player B's reply
                    ACLMessage replyB = myAgent.receive(mtB);
                    if (replyB != null && choiceB == -1) {
                        try {
                            // Format: Position#<col> 
                            choiceB = Integer.parseInt(replyB.getContent().split("#")[1]);
                            if (choiceB < 0 || choiceB >= S)
                                throw new NumberFormatException();
                        } catch (Exception e) {
                            gui.log("Error parsing Player B's choice. Defaulting to 0.");
                            choiceB = 0;
                        }
                    }

                    if (choiceA != -1 && choiceB != -1) {
                        state = 3; // Both replied
                    } else if (System.currentTimeMillis() - roundStartTime > ROUND_TIMEOUT_MS) {
                        // Timeout
                        gui.log("Round timeout. Assigning default move (0) to non-responsive players.");
                        if (choiceA == -1) {
                            gui.log("Player A did not respond.");
                            choiceA = 0; // Default to 0 on error
                        }
                        if (choiceB == -1) {
                            gui.log("Player B did not respond.");
                            choiceB = 0;
                        }
                        state = 3;
                    } else {
                        block(50); // Wait briefly
                    }
                    break;

                case 3:
                    // State 3: Both replied or timed out, send results
                    if (gui.isDetailedInfoEnabled()) {
                        gui.log("Choices: A(row)=" + choiceA + ", B(col)=" + choiceB);
                    }

                    // Get payoffs 
                    int[] payoffs = getPayoff(choiceA, choiceB);
                    int payoffA = payoffs[0]; 
                    int payoffB = payoffs[1]; 

                    // Add to game score
                    scoreA += payoffA;
                    scoreB += payoffB;

                    if (gui.isDetailedInfoEnabled()) {
                        gui.log("Payoffs: A gets " + payoffA + ", B gets " + payoffB);
                        gui.log("Current Score: A=" + scoreA + ", B=" + scoreB);
                    }

                    // Send Results#<row>,<col>#<payoff1>,<payoff2> 
                    String resultContent = String.format("Results#%d,%d#%d,%d", choiceA, choiceB, payoffA, payoffB);
                    ACLMessage resultMsg = new ACLMessage(ACLMessage.INFORM);
                    resultMsg.addReceiver(playerA);
                    resultMsg.addReceiver(playerB);
                    resultMsg.setContent(resultContent);
                    myAgent.send(resultMsg);

                    currentRound++;
                    state = 0; // Loop back to request next position
                    break;

                case 4:
                    // State 4: Game has finished
                    // (Done)
                    break;
            }
        }

        public boolean done() {
            if (state == 4) {
                // Game is over, tell MainAgent to process scores
                endGame(playerA, playerB, scoreA, scoreB);
                return true; // This behavior is finished
            }
            return false;
        }
    }
}