===================================================
Intelligent Systems Programming (2025-2026)
===================================================

1. STUDENT INFORMATION
---------------------------------------------------
Name: Havva Nur Aslan
Lab ID: 3


2. HOW TO COMPILE AND RUN
---------------------------------------------------
A. Requirements:
 - Java 21 JDK 
 - JADE 4.6.0 (jade.jar)
 - UTF-8 file encoding

B. Project Structure:
   The .java source files are organized as follows:
   /src/MainAgent.java
   /src/MainAgentGUI.java
   /src/agents/RandomAgent.java
   /src/agents/RL_Agent.java
   /src/agents/NN_Agent.java
   /src/agents/RL_NN_Agent.java  

C. Compilation (from the project's root 'GUI' folder):
   (Note: The wildcard src/agents/*.java automatically includes all agents.)

   Windows:
   javac -d bin -cp "lib/jade.jar" src/*.java src/agents/*.java

   Linux/macOS:
   javac -d bin -cp "lib/jade.jar:." src/*.java src/agents/*.java

D. Execution (from the project's root 'GUI' folder):
   To run the simulation with all developed agents (Manager, Random, RL, NN, and Hybrid):

   Windows:
   java -classpath "lib/jade.jar;bin" jade.Boot -agents "MainAgent:MainAgent;PlayerRand:agents.RandomAgent;PlayerRL:agents.RL_Agent;PlayerNN:agents.NN_Agent;PlayerHybrid:agents.RL_NN_Agent"

   Linux/macOS:
   java -classpath "lib/jade.jar:bin" jade.Boot -agents "MainAgent:MainAgent;PlayerRand:agents.RandomAgent;PlayerRL:agents.RL_Agent;PlayerNN:agents.NN_Agent;PlayerHybrid:agents.RL_NN_Agent"


3. ALGORITHM DESCRIPTIONS
---------------------------------------------------

* **RandomAgent (agents.RandomAgent.java)**
    This agent implements the simplest baseline strategy. It generates a random integer between 0 and (S-1) for every 'Position' request. It has no memory or learning capability.

* **RL_Agent (agents.RL_Agent.java)**
    This agent implements a **Q-Learning (Reinforcement Learning)** algorithm.
    - **Strategy:** It maintains a Q-Table of size S, where each index represents the expected reward (quality) of choosing that specific row.
    - **Learning:** After receiving the 'Results' message from the MainAgent, it updates the Q-value of the chosen action using the standard Q-learning update rule:
      Q(a) = Q(a) + alpha * (Reward - Q(a))
    - **Selection:** It uses an **Epsilon-Greedy** strategy. It explores randomly with a probability of epsilon (0.1) and exploits the best-known action (highest Q-value) with a probability of (1 - epsilon).
    - **Parameters:** Learning Rate (alpha) = 0.1; Exploration Rate (epsilon) = 0.1.

* **NN_Agent (agents.NN_Agent.java)**
    This agent uses a custom **Multilayer Perceptron (Neural Network)** implemented from scratch without external libraries.
    - **Architecture:**
        1. **Input Layer:** S neurons. It receives the opponent's last move (One-hot encoded).
        2. **Hidden Layer:** 5 neurons with Sigmoid activation to capture patterns.
        3. **Output Layer:** S neurons (Linear). Each output represents the predicted payoff for selecting a specific row.
    - **Learning:** It uses the **Backpropagation** algorithm. After each round, the agent calculates the error between the predicted reward and the actual received reward, 
    then adjusts the weights (Input-Hidden and Hidden-Output) to minimize this error.
    - **Selection:** It feeds the opponent's previous move into the network and selects the action corresponding to the output neuron with the highest value (ArgMax).

* **RL_NN_Agent (agents.RL_NN_Agent.java)**
    This is a hybrid agent designed to leverage the strengths of both reinforcement learning and neural networks to win the tournament.
    - **Concept:** "Switching Strategy based on Prediction Confidence".
    - **Mechanism:**
        1. **Prediction:** It uses a Neural Network to predict the opponent's *next* move based on their **last move**. It uses a widened hidden layer (size S*3) to better capture non-linear patterns.
        2. **Confidence Check:** It tracks the prediction accuracy of the Neural Network over the last 25 rounds.
        3. **Hybrid Decision:**
           - If NN accuracy > 75%: The agent assumes it knows the opponent's move with high confidence. It then searches its learned payoff matrix to find the **Best Response**.
           - If NN accuracy <= 75%: The agent assumes the opponent is playing randomly or the pattern is too complex. It falls back to standard **Q-Learning** to minimize risk.
    - **Advantage:** Against predictable opponents, it exploits them perfectly using the NN. Against random opponents, it plays safely using Q-Learning.

4. FINAL TOURNAMENT AGENT
---------------------------------------------------
The agent submitted for the final tournament is the **RL_NN_Agent**.
Reasoning: It offers the best of both worlds. It adapts faster than pure Q-Learning against patterned opponents and is more stable than a pure Neural Network against random opponents.


5. USE OF GENERATIVE AI
---------------------------------------------------
Generative AI (Gemini, ChatGPT) was used as a programming assistant throughout Phase 1 and Phase 2.

* **Phase 1 (Basic):** Scaffolding JADE structure, fixing classpath errors, and implementing the GUI.
* **Phase 2 (Intelligent Agents):**
    * **Mathematical Logic:** Verified the mathematical formulas for Q-Learning updates and Neural Network Backpropagation (calculating gradients and deltas).
    * **Algorithm Implementation:** Assisted in writing the `SimpleNeuralNet` inner class from scratch to avoid dependency on prohibited external AI libraries.
    * **Hybrid Strategy Design:** Helped design the logic for the `RL_NN_Agent`, specifically the mechanism for switching between Q-Learning and Neural Network prediction based on real-time accuracy tracking.
    * **Debugging:** Helped identify index-out-of-bounds errors when mapping the opponent's moves to the input layer of the Neural Network.
    * **Documentation:** Assisted in summarizing the algorithmic logic for this Readme file.


6. EXTRA FUNCTIONALITIES / FINAL COMMENTS
---------------------------------------------------
* **Self-Contained AI:** The Neural Network agent does not require any external .jar files or libraries; the math is implemented in pure Java.
* **Robustness:** All intelligent agents (RL, NN, Hybrid) handle the "NewGame" message by resetting (or partially retaining) their internal state to adapt to new payoff matrices.
* **GUI Features:** The "Enable Detailed Log" and "Remove Selected Player" features (implemented in Phase 1) are fully functional and helpful for debugging the learning process of the new agents.