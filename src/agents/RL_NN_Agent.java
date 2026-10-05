package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import java.util.Random;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * RL_NN_Agent (Optimized V2): 
 * Q-Learning is the main driver. NN takes over ONLY when highly confident.
 */
public class RL_NN_Agent extends Agent {

    private int S; 
    private Random rand;
    
    // --- Q-Learning Components ---
    private double[] qTable;
    private double alpha = 0.1; 
    private double epsilon = 0.1; 
    
    // --- Neural Network Components ---
    private SimpleNeuralNet opponentPredictor;
    private int lastOpponentAction = -1;
    private ArrayList<Boolean> predictionHistory; 
    private int historyWindow = 25; // Look at a longer history window
    private double confidenceThreshold = 0.75; // Do not trust NN until 75% accuracy is reached!
    
    // --- Game State ---
    private int myLastAction = -1;
    private int[][] currentPayoffMatrix; 

    protected void setup() {
        rand = new Random();
        predictionHistory = new ArrayList<>();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player");
        sd.setName("Hybrid_V2"); // V2 to avoid name conflicts
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new GamePlayBehaviour());
    }

    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException fe) {}
    }

    private class GamePlayBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();

                if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (content.startsWith("Id#")) {
                        String[] parts = content.split("#");
                        String[] params = parts[2].split(",");
                        S = Integer.parseInt(params[1]);
                        
                        qTable = new double[S];
                        // Wider hidden layer (S * 3) for better pattern recognition
                        opponentPredictor = new SimpleNeuralNet(S, S * 3, S); 
                        
                        currentPayoffMatrix = new int[S][S]; 
                        for(int[] row : currentPayoffMatrix) Arrays.fill(row, -999); // Set unknowns to a very low value

                    } else if (content.startsWith("NewGame#")) {
                        Arrays.fill(qTable, 0.0);
                        // Reset NN to avoid bias from previous game (matrix changes)
                        opponentPredictor = new SimpleNeuralNet(S, S * 3, S); 
                        predictionHistory.clear();
                        lastOpponentAction = -1;
                        myLastAction = -1;
                        for(int[] row : currentPayoffMatrix) Arrays.fill(row, -999);

                    } else if (content.startsWith("Results#")) {
                        try {
                            String[] parts = content.split("#");
                            String[] moves = parts[1].split(",");
                            String[] payoffs = parts[2].split(",");
                            
                            int rowSelected = Integer.parseInt(moves[0]);
                            int colSelected = Integer.parseInt(moves[1]);
                            
                            int myMove, oppMove;
                            double myReward;
                            
                            // Determine who played which role and learn the matrix
                            if (myLastAction == rowSelected) {
                                myMove = rowSelected;
                                oppMove = colSelected;
                                myReward = Double.parseDouble(payoffs[0]);
                                currentPayoffMatrix[rowSelected][colSelected] = (int)myReward; 
                            } else {
                                myMove = colSelected;
                                oppMove = rowSelected;
                                myReward = Double.parseDouble(payoffs[1]);
                                currentPayoffMatrix[myMove][oppMove] = (int)myReward;
                            }

                            // --- 1. NN Training ---
                            if (lastOpponentAction != -1) {
                                double[] inputs = new double[S];
                                inputs[lastOpponentAction] = 1.0;
                                
                                double[] targets = new double[S];
                                targets[oppMove] = 1.0; 
                                
                                // Was the prediction correct?
                                int predictedMove = getArgMax(opponentPredictor.feedForward(inputs));
                                boolean accurate = (predictedMove == oppMove);
                                updateAccuracy(accurate);
                                
                                // Train
                                opponentPredictor.train(inputs, targets);
                            }
                            lastOpponentAction = oppMove;

                            // --- 2. Q-Learning Update (ALWAYS DO) ---
                            // Even if Hybrid, Q-Table must run in background to be ready for fallback.
                            double oldQ = qTable[myMove];
                            qTable[myMove] = oldQ + alpha * (myReward - oldQ);

                        } catch (Exception e) { e.printStackTrace(); }
                    }
                    
                } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                    if (content.equals("Position")) {
                        int selectedAction = -1;
                        
                        // --- ADVANCED HYBRID LOGIC ---
                        
                        int predictedOpponentMove = -1;
                        if (lastOpponentAction != -1) {
                            double[] inputs = new double[S];
                            inputs[lastOpponentAction] = 1.0;
                            predictedOpponentMove = getArgMax(opponentPredictor.feedForward(inputs));
                        }
                        
                        double accuracy = getAccuracy();
                        
                        // Use NN ONLY if:
                        // 1. We are confident (>75%)
                        // 2. We have a valid prediction
                        // 3. We actually KNOW the payoff for the counter-move (Risk management)
                        if (accuracy > confidenceThreshold && predictedOpponentMove != -1) {
                            int bestResponse = getBestResponse(predictedOpponentMove);
                            
                            // If bestResponse is not -1, it means we know the outcome. Go for it!
                            if (bestResponse != -1) {
                                selectedAction = bestResponse;
                            }
                        }

                        // FALLBACK: If NN didn't trigger, wasn't confident, or outcome was unknown -> Use Q-Learning
                        if (selectedAction == -1) {
                            if (rand.nextDouble() < epsilon) {
                                selectedAction = rand.nextInt(S); 
                            } else {
                                selectedAction = getArgMaxDouble(qTable); 
                            }
                        }

                        myLastAction = selectedAction;
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Position#" + selectedAction);
                        myAgent.send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    // --- Helper Methods ---

    private void updateAccuracy(boolean correct) {
        predictionHistory.add(correct);
        if (predictionHistory.size() > historyWindow) {
            predictionHistory.remove(0);
        }
    }

    private double getAccuracy() {
        if (predictionHistory.isEmpty()) return 0.0;
        int correct = 0;
        for (boolean b : predictionHistory) if (b) correct++;
        return (double) correct / predictionHistory.size();
    }

    // Our move that yields the highest score against opponent's move
    private int getBestResponse(int opponentMove) {
        int bestAction = -1;
        int maxReward = -900; // Threshold value for "unknown"
        
        for (int i = 0; i < S; i++) {
            int reward = currentPayoffMatrix[i][opponentMove];
            // If we never visited this cell (it is -999), DO NOT treat it as a valid move option.
            if (reward > -900 && reward > maxReward) {
                maxReward = reward;
                bestAction = i;
            }
        }
        return bestAction; // Returns -1 if all options are unknown
    }

    private int getArgMax(double[] array) {
        int maxIdx = 0;
        double maxVal = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) {
                maxVal = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }
    
    private int getArgMaxDouble(double[] array) {
        int maxIdx = 0;
        double maxVal = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) {
                maxVal = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    // --- Neural Network (Same structure, Learning Rate increased) ---
    private class SimpleNeuralNet {
        int inputNodes, hiddenNodes, outputNodes;
        double[][] weightsIH, weightsHO;
        double[] hiddenBiases, outputBiases;
        double learningRate = 0.2; // Learn slightly faster

        public SimpleNeuralNet(int i, int h, int o) {
            this.inputNodes = i; this.hiddenNodes = h; this.outputNodes = o;
            weightsIH = new double[i][h]; weightsHO = new double[h][o];
            hiddenBiases = new double[h]; outputBiases = new double[o];
            randomizeWeights();
        }

        private void randomizeWeights() {
            for(int i=0; i<inputNodes; i++) for(int j=0; j<hiddenNodes; j++) weightsIH[i][j] = rand.nextGaussian() * 0.1;
            for(int i=0; i<hiddenNodes; i++) for(int j=0; j<outputNodes; j++) weightsHO[i][j] = rand.nextGaussian() * 0.1;
        }

        public double[] feedForward(double[] inputs) {
            double[] hiddenOutputs = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double sum = hiddenBiases[j];
                for (int i = 0; i < inputNodes; i++) sum += inputs[i] * weightsIH[i][j];
                hiddenOutputs[j] = Math.tanh(sum);
            }
            double[] outputs = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) {
                double sum = outputBiases[k];
                for (int j = 0; j < hiddenNodes; j++) sum += hiddenOutputs[j] * weightsHO[j][k];
                outputs[k] = sum;
            }
            return outputs;
        }

        public void train(double[] inputs, double[] targets) {
            double[] hiddenOutputs = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double sum = hiddenBiases[j];
                for (int i = 0; i < inputNodes; i++) sum += inputs[i] * weightsIH[i][j];
                hiddenOutputs[j] = Math.tanh(sum);
            }
            double[] outputs = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) {
                double sum = outputBiases[k];
                for (int j = 0; j < hiddenNodes; j++) sum += hiddenOutputs[j] * weightsHO[j][k];
                outputs[k] = sum;
            }
            double[] outputErrors = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) outputErrors[k] = targets[k] - outputs[k];

            for (int j = 0; j < hiddenNodes; j++) {
                for (int k = 0; k < outputNodes; k++) weightsHO[j][k] += outputErrors[k] * learningRate * hiddenOutputs[j];
            }
            for (int k = 0; k < outputNodes; k++) outputBiases[k] += outputErrors[k] * learningRate;

            double[] hiddenErrors = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double error = 0;
                for (int k = 0; k < outputNodes; k++) error += outputErrors[k] * weightsHO[j][k];
                hiddenErrors[j] = error;
            }
            for (int i = 0; i < inputNodes; i++) {
                for (int j = 0; j < hiddenNodes; j++) {
                    double gradient = 1 - (hiddenOutputs[j] * hiddenOutputs[j]);
                    weightsIH[i][j] += hiddenErrors[j] * gradient * learningRate * inputs[i];
                }
            }
            for (int j = 0; j < hiddenNodes; j++) {
                double gradient = 1 - (hiddenOutputs[j] * hiddenOutputs[j]);
                hiddenBiases[j] += hiddenErrors[j] * gradient * learningRate;
            }
        }
    }
}