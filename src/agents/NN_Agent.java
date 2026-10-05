package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import java.util.Random;

public class NN_Agent extends Agent {

    private int S;
    private Random rand;
    private SimpleNeuralNet nn;
    private int lastMyAction = -1;
    private int lastOpponentAction = -1; // -1: No move yet (first round)

    protected void setup() {
        System.out.println("NN_Agent " + getAID().getLocalName() + " is ready.");
        rand = new Random();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player");
        sd.setName("NN-Player");
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
        System.out.println("NN_Agent terminating.");
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
                        
                        // Initialize Neural Network: Input(S) -> Hidden(5) -> Output(S)
                        // Input size S: We will one-hot encode the opponent's last move.
                        nn = new SimpleNeuralNet(S, 5, S); 
                        
                    } else if (content.startsWith("NewGame#")) {
                        lastOpponentAction = -1; // Reset memory
                        // We don't necessarily reset weights every game (should it continue learning?)
                        // Rule: Since the matrix changes, previous learnings might be invalid for this game.
                        // Therefore, it is safer to reset.
                        nn = new SimpleNeuralNet(S, 5, S); 

                    } else if (content.startsWith("Results#")) {
                        // Receive reward and train NN (Backpropagation)
                        try {
                            String[] parts = content.split("#");
                            String[] moves = parts[1].split(",");
                            String[] payoffs = parts[2].split(",");
                            
                            int rowSelected = Integer.parseInt(moves[0]);
                            int colSelected = Integer.parseInt(moves[1]);
                            
                            double reward = 0;
                            int opponentMove = -1;

                            // Which role did we play?
                            if (lastMyAction == rowSelected) {
                                reward = Double.parseDouble(payoffs[0]);
                                opponentMove = colSelected;
                            } else {
                                reward = Double.parseDouble(payoffs[1]);
                                opponentMove = rowSelected;
                            }
                            
                            // 1. Prepare Training Data
                            // Input: Opponent's move in the previous round (based on which we made the decision)
                            // If it is the first round (lastOpponentAction == -1), input can be all 0s.
                            double[] inputs = new double[S];
                            if (lastOpponentAction != -1) {
                                inputs[lastOpponentAction] = 1.0;
                            }
                            
                            // 2. Target Output
                            // We only know the value of the action we chose (reward).
                            // We will keep NN's current prediction for other actions.
                            double[] currentPrediction = nn.feedForward(inputs);
                            double[] targets = currentPrediction.clone();
                            targets[lastMyAction] = reward; // Update expected value with actual reward
                            
                            // 3. Train
                            nn.train(inputs, targets);
                            
                            // Save this opponent move for the next round
                            lastOpponentAction = opponentMove;

                        } catch (Exception e) { e.printStackTrace(); }
                    }
                    
                } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                    if (content.equals("Position")) {
                        // Ask NN: Which move yields the highest reward?
                        double[] inputs = new double[S];
                        if (lastOpponentAction != -1) {
                            inputs[lastOpponentAction] = 1.0;
                        }
                        
                        double[] outputs = nn.feedForward(inputs);
                        
                        // Select index with highest output (ArgMax)
                        int bestAction = 0;
                        double maxVal = outputs[0];
                        for(int i=1; i<outputs.length; i++){
                            if(outputs[i] > maxVal){
                                maxVal = outputs[i];
                                bestAction = i;
                            }
                        }
                        
                        // 5% chance random (to avoid local maxima)
                        if(rand.nextDouble() < 0.05) {
                            bestAction = rand.nextInt(S);
                        }

                        lastMyAction = bestAction;
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Position#" + bestAction);
                        myAgent.send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    // --- Simple Neural Network Class (Inner Class) ---
    private class SimpleNeuralNet {
        int inputNodes, hiddenNodes, outputNodes;
        double[][] weightsIH; // Input -> Hidden
        double[][] weightsHO; // Hidden -> Output
        double[] hiddenBiases;
        double[] outputBiases;
        double learningRate = 0.1;

        public SimpleNeuralNet(int i, int h, int o) {
            this.inputNodes = i;
            this.hiddenNodes = h;
            this.outputNodes = o;
            
            weightsIH = new double[i][h];
            weightsHO = new double[h][o];
            hiddenBiases = new double[h];
            outputBiases = new double[o];
            
            randomizeWeights();
        }

        private void randomizeWeights() {
            for(int i=0; i<inputNodes; i++)
                for(int j=0; j<hiddenNodes; j++)
                    weightsIH[i][j] = Math.random() * 2 - 1; // Between -1 and 1
            
            for(int i=0; i<hiddenNodes; i++)
                for(int j=0; j<outputNodes; j++)
                    weightsHO[i][j] = Math.random() * 2 - 1;
        }

        // Feed Forward
        public double[] feedForward(double[] inputs) {
            // Calculate Hidden Layer
            double[] hiddenOutputs = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double sum = hiddenBiases[j];
                for (int i = 0; i < inputNodes; i++) {
                    sum += inputs[i] * weightsIH[i][j];
                }
                hiddenOutputs[j] = sigmoid(sum);
            }

            // Calculate Output Layer
            double[] outputs = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) {
                double sum = outputBiases[k];
                for (int j = 0; j < hiddenNodes; j++) {
                    sum += hiddenOutputs[j] * weightsHO[j][k];
                }
                // We don't use activation in the output layer (Linear), 
                // because the reward can be any value between 0-9.
                outputs[k] = sum; 
            }
            return outputs;
        }

        // Training with Backpropagation
        public void train(double[] inputs, double[] targets) {
            // 1. Recalculate Forward Pass values
            double[] hiddenOutputs = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double sum = hiddenBiases[j];
                for (int i = 0; i < inputNodes; i++) {
                    sum += inputs[i] * weightsIH[i][j];
                }
                hiddenOutputs[j] = sigmoid(sum);
            }

            double[] outputs = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) {
                double sum = outputBiases[k];
                for (int j = 0; j < hiddenNodes; j++) {
                    sum += hiddenOutputs[j] * weightsHO[j][k];
                }
                outputs[k] = sum;
            }

            // 2. Output Layer Errors
            double[] outputErrors = new double[outputNodes];
            for (int k = 0; k < outputNodes; k++) {
                outputErrors[k] = targets[k] - outputs[k];
            }

            // 3. Update Hidden -> Output Weights
            // Delta = error * learning_rate * hiddenOutput
            for (int j = 0; j < hiddenNodes; j++) {
                for (int k = 0; k < outputNodes; k++) {
                    weightsHO[j][k] += outputErrors[k] * learningRate * hiddenOutputs[j];
                }
            }
            // Update Output Bias
            for (int k = 0; k < outputNodes; k++) {
                outputBiases[k] += outputErrors[k] * learningRate;
            }

            // 4. Hidden Layer Errors
            double[] hiddenErrors = new double[hiddenNodes];
            for (int j = 0; j < hiddenNodes; j++) {
                double error = 0;
                for (int k = 0; k < outputNodes; k++) {
                    error += outputErrors[k] * weightsHO[j][k];
                }
                hiddenErrors[j] = error;
            }

            // 5. Update Input -> Hidden Weights
            // Sigmoid derivative: out * (1 - out)
            for (int i = 0; i < inputNodes; i++) {
                for (int j = 0; j < hiddenNodes; j++) {
                    double gradient = hiddenOutputs[j] * (1 - hiddenOutputs[j]);
                    weightsIH[i][j] += hiddenErrors[j] * gradient * learningRate * inputs[i];
                }
            }
            // Update Hidden Bias
            for (int j = 0; j < hiddenNodes; j++) {
                double gradient = hiddenOutputs[j] * (1 - hiddenOutputs[j]);
                hiddenBiases[j] += hiddenErrors[j] * gradient * learningRate;
            }
        }

        private double sigmoid(double x) {
            return 1 / (1 + Math.exp(-x));
        }
    }
}