package agents;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import java.util.Random;

public class RandomAgent extends Agent {

    private int myId;
    private int S; // Size of the matrix [cite: 41]
    private Random rand;
    private AID mainAgentAID;

    protected void setup() {
        System.out.println("RandomAgent " + getAID().getName() + " is ready.");
        rand = new Random();
        
        // Register as a "player" with the DF 
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player"); // This MUST match what MainAgent searches for
        sd.setName("PSI-Random-Player");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Add behavior to handle messages from MainAgent
        addBehaviour(new GamePlayBehaviour());
    }

    protected void takeDown() {
        // Deregister from the DF
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("RandomAgent " + getAID().getName() + " terminating.");
    }

    private class GamePlayBehaviour extends CyclicBehaviour {

        public void action() {
            // Listen for messages
            ACLMessage msg = myAgent.receive();
            
            if (msg != null) {
                String content = msg.getContent();

                if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (content.startsWith("Id#")) {
                        // MainAgent provides ID and game parameters 
                        // Format: Id#<id>#<N,S,R>
                        mainAgentAID = msg.getSender(); // Store MainAgent's AID
                        String[] parts = content.split("#");
                        String[] params = parts[2].split(",");
                        myId = Integer.parseInt(parts[1]);
                        S = Integer.parseInt(params[1]); // Store S (matrix size) 
                    
                    } else if (content.startsWith("NewGame#")) {
                        // MainAgent starts a new game 
                        // Random agent doesn't need to do anything here.
                    
                    } else if (content.startsWith("Results#")) {
                        // MainAgent reports results of the round 
                        // A RandomAgent doesn't learn, so we ignore this.
                    
                    } else if (content.equals("EndGame")) {
                        // MainAgent signals end of game 
                        // No action needed.
                    }
                    
                } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                    if (content.equals("Position")) {
                        // MainAgent is requesting a move 
                        
                        // Choose a random action (row or column) 
                        int choice = rand.nextInt(S); // Random int from 0 to S-1
                        
                        // Create and send the reply
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        // Format: Position#<choice> 
                        reply.setContent("Position#" + choice);
                        myAgent.send(reply);
                    }
                }

            } else {
                // If no message, block to save CPU
                block();
            }
        }
    }
}