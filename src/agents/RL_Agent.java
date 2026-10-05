package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import java.util.Random;
import java.util.Arrays;

public class RL_Agent extends Agent {

    private int S; // Matrix size
    private Random rand;
    
    // Q-Learning Parameters
    private double[] qTable; // Q-values for each row
    private int lastAction = -1; // Last selected row
    private double alpha = 0.1; // Learning Rate
    private double epsilon = 0.1; // Exploration Rate

    protected void setup() {
        System.out.println("RL_Agent " + getAID().getLocalName() + " is ready.");
        rand = new Random();

        // Register as a player
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player");
        sd.setName("RL-Player");
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
        System.out.println("RL_Agent " + getAID().getLocalName() + " terminating.");
    }

    private class GamePlayBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();

                if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (content.startsWith("Id#")) {
                        // Retrieve parameters: Id#id#N,S,R
                        String[] parts = content.split("#");
                        String[] params = parts[2].split(",");
                        S = Integer.parseInt(params[1]);
                        
                        // Initialize Q-Table (size S)
                        qTable = new double[S];
                        // Initially, all values can be 0 or small random numbers
                        Arrays.fill(qTable, 0.0);
                        
                    } else if (content.startsWith("NewGame#")) {
                        // When a new game starts, we might want to reset Q-values
                        // Or keep them for 'transfer learning'. 
                        // Tournament rule: Each game has a new matrix, so we reset.
                        if (qTable != null) Arrays.fill(qTable, 0.0);
                        lastAction = -1;

                    } else if (content.startsWith("Results#")) {
                        
                        try {
                            String[] parts = content.split("#");
                            String[] moves = parts[1].split(",");
                            String[] payoffs = parts[2].split(",");
                            
                            int rowSelected = Integer.parseInt(moves[0]);
                            int colSelected = Integer.parseInt(moves[1]);
                            
                            double reward = 0;
                            
                            if (lastAction == rowSelected) {
                                reward = Double.parseDouble(payoffs[0]);
                            } else {
                                reward = Double.parseDouble(payoffs[1]);
                            }

                            // Update Q-Value (Update Rule)
                            // Q(s,a) = Q(s,a) + alpha * (r - Q(s,a))
                            if (lastAction != -1) {
                                double oldQ = qTable[lastAction];
                                qTable[lastAction] = oldQ + alpha * (reward - oldQ);
                            }
                            
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                    if (content.equals("Position")) {
                        // Epsilon-Greedy Strategy
                        int choice;
                        if (rand.nextDouble() < epsilon) {
                            // Explore: Select random
                            choice = rand.nextInt(S);
                        } else {
                            // Exploit: Select highest Q-value
                            choice = getArgMax(qTable);
                        }
                        
                        lastAction = choice;
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Position#" + choice);
                        myAgent.send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    // Find index with highest value
    private int getArgMax(double[] array) {
        int maxIdx = 0;
        double maxVal = array[0];
        // Adding randomness in case of ties is good, but let's keep it simple
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) {
                maxVal = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}