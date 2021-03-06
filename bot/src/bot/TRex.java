/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bot;

import java.util.ArrayList;
import java.util.List;
import ai.RandomBiasedAI;
import ai.core.AI;
import ai.core.ParameterSpecification;
import ai.evaluation.EvaluationFunction;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;


/**
 *
 * @author Stomps
 */

// The AI class
// I wanted to do this as a simple extension of the AI rather than 'WithComputationBudget implements InterruptibleAI' or similar

/**
 * The artificial intelligence bot that will initialise gameState analysis and upon calling the getAction function, will initiate a monte carlo tree search (mcts) using upper confidence bounds (UCB) = (UCT)
 * 
 * @param evaluationFunction The function used to evaluate a game state returning a score reflecting the passed in player's favour in the game.
 * @param simulationEnemyAI The Ai which will be played against in a simulation, and acts acts a fall back playerAction choice were the mcts algorithm not to find a viable player action.
 * @param treeRootNode The root node from which the tree is grown from.
 * @param initialGameState An initial clone of the original game state to refer to later if the used clones change through simulation.
 * @param MAXSIMULATIONTIME The time allowance given to return a move by.
 * @param MAX_TREE_DEPTH The lookahead amount that the tree will grow to before an evaluation is forced.
 * @param SIMULATION_PLAYOUTS The amount of simulations to play out if using the NSimulate function.
 * @param totalNodeVisits The total visits value that is used to calculate the UCB score
 * @param playerNumber The game's allocated number for this player.
 * @param halfMapDistance The Cartesian distance from one corner of the map to the centre. Used as measure to determine the local resources.
 * @param physicalGameState The physical representation of the gameState.
 * @param baseType The UnitType of a base.
 * @param workerType The UnitType of a worker.
 * @param endTime The cut off time at which a move must be returned by.
 */

public class TRex extends AI
{
	// experimented here and found this to be the optimum value
    float C = 0.05f;
	
	// Game evaluation function that returns a value based on units and resources available
    EvaluationFunction evaluationFunction = new SimpleSqrtEvaluationFunction3();
    
    // Simulations require an opponent to play out against, RandomBiasedAI is a slightly stronger opponent than RandomAI, Or maybe choose stronger?
    AI simulationEnemyAI = new RandomBiasedAI();
    
    Node treeRootNode;
    GameState initialGameState;
    
    // The time allowance that is given to the main loop before breaking and finding the best found child
    int MAXSIMULATIONTIME;
    
    // The look ahead depth allowance of nodes in the tree
    int MAX_TREE_DEPTH;
    
    // If doing a NSimulate evaluation then average random play outs over this many simulations
    int SIMULATION_PLAYOUTS;
    
    int totalNodeVisits;
    
    // The 0 or 1 identifier number of this player
    int playerNumber;
    
    // For finding the near side resources
    int halfMapDistance;
    
    // Used in the alternate analysis.setAnalysisWeightings() option
//    int playerNumberDifference;
    
    // for epsilon greedy?
//    Random random = new Random();
    
    // GameState and board information
    PhysicalGameState physicalGameState;
    UnitType baseType;
    UnitType workerType;
    
    long endTime;
    
    // The Analysis class that scores the gameState and moves generated by the action generator
    Analysis analysis;
    
    public TRex(UnitTypeTable utt) {
        baseType = utt.getUnitType("Base");
        workerType = utt.getUnitType("Worker");
    }      
    
    
    public TRex() {
    }
    
    
    public void reset() {
        totalNodeVisits = 0;
        simulationEnemyAI = new RandomBiasedAI();
    }
    
    
    public void resetSearch() {
        totalNodeVisits = 0;
        simulationEnemyAI = new RandomBiasedAI();
    }
    
    
    public AI clone() {
        return new TRex();
    }  
    
    /*
     * Returns a PlayerAction for the game to play.
     */
    public PlayerAction getAction(int player, GameState gameState) throws Exception
    {
        playerNumber = player;
        initialGameState = gameState;
    	totalNodeVisits = 0;
    	
        if (!gameState.canExecuteAnyAction(player)) return new PlayerAction();
        
        // Epsilon greedy?
 //       if (random.nextFloat() < 0.07f) return new PlayerActionGenerator(gameState, player).getRandom();
        
        MAXSIMULATIONTIME = 100;
        MAX_TREE_DEPTH = 10;
        SIMULATION_PLAYOUTS = 5;
        
        // Time limit
        endTime = System.currentTimeMillis() + MAXSIMULATIONTIME;
        
        // For determining nearby resources
        physicalGameState = gameState.getPhysicalGameState();
        halfMapDistance = (physicalGameState.getWidth() + physicalGameState.getHeight()) / 2 + 1;
        
        // The initial analysis can be expensive
        analysis = new Analysis(playerNumber, gameState, halfMapDistance, baseType, workerType);
        analysis.analyseGameState();
        
        int gameStateTime = gameState.getTime();
        
        // Sets the weightings for the action analysis. Magnitude not relevant, just the comparative values to each other
        
        // Key for analysis.setAnalysisWeightings function arguments:
        // Harvest action weight | move To harvest position weight | attack action weight | move towards enemy weight | produce weight | distance can see Enemy
        if 		(gameStateTime < 100) 	analysis.setAnalysisWeightings(100,		20,		70,		0,		0,		6);
        else if (gameStateTime < 500)	analysis.setAnalysisWeightings(80,		15,		80,		5,		100,	8);
        else if (gameStateTime < 800)	analysis.setAnalysisWeightings(60,		10,		90,		20,		70,		halfMapDistance);
        else if (gameStateTime < 1200)	analysis.setAnalysisWeightings(20,		0,		100,	15,		20,		halfMapDistance*2);
        else if (gameStateTime < 2000)	analysis.setAnalysisWeightings(5,		0,		100,	10,		0,		halfMapDistance*2);
        else 							analysis.setAnalysisWeightings(0,		0,		100,	10,		0,		halfMapDistance*2); 
  
/*
 		// A different set of evaluation conditions that change the analysis weightings based upon the difference in unit numbers between this player and the enemy
 		
        playerNumberDifference = tree.getPlayerUnitDifference();
        
        if		(tree.getEnemyListSize() <= 2 && gameState.getTime() > 1000)	tree.setAnalysisWeightings(0,		0,		100,	10,		0,		halfMapDistance*2); 
        else if (playerNumberDifference < 2) 	tree.setAnalysisWeightings(100,		1,		100,	0,		0,		6);
        else if (playerNumberDifference < 3)	tree.setAnalysisWeightings(80,		5,		80,		5,		100,	8);
        else if (playerNumberDifference < 4)	tree.setAnalysisWeightings(100,		2,		200,	80,		50,		halfMapDistance);
        else if (playerNumberDifference < 5)	tree.setAnalysisWeightings(5,		0,		100,	10,		20,		halfMapDistance*2);
        else if (playerNumberDifference < 6)	tree.setAnalysisWeightings(5,		0,		100,	10,		10,		halfMapDistance*2);
        else if (gameState.getTime() > 4000)	tree.setAnalysisWeightings(0,		0,		100,	10,		0,		halfMapDistance*2); 
*/       
        
        
        // Initialise the tree as a new Node with parent = null
        treeRootNode = new Node(playerNumber, 1-playerNumber, null, gameState.clone(), analysis, endTime);
        
        
        // Main loop
        while (true)
        {
        	// Breaks out when the time exceeds
            if (System.currentTimeMillis() > endTime) break;
            
        	// Tries to get a new unexplored action from the tree
            Node newNode = treeRootNode.selectNewAction(playerNumber, 1-playerNumber, endTime, MAX_TREE_DEPTH);

        	// Creating Nodes is expensive so check again!
            if (System.currentTimeMillis() > endTime) break;
            
            // If no new actions then null is returned
            if (newNode != null)
            {
            	// Clone the gameState for use in the simulation
                GameState gameStateClone = newNode.getGameState().clone();
                
                // Not too sure here, the evaluation tends towards zero as the time increases
                int time = gameStateClone.getTime() - initialGameState.getTime();
                
                // Simulate a play out of that gameState
                simulate(gameStateClone, player);
                
                // Evaluate the resulting gameState with the evaluation function and apply a time based decay to the result score to prioritise early found results
                double evaluation = evaluationFunction.evaluate(playerNumber, 1-playerNumber, gameStateClone) * Math.pow(0.99, time/10.0);
                
                // Back propagation, cycle though each node's parents until the tree root is reached
                while(newNode != null)
                {
                    newNode.addScore(evaluation);
                    newNode.incrementVisitCount();
                    newNode = newNode.getParent();
                }
            }
        }
        
        // Sanity check
        if (treeRootNode.getChildrenList() == null)
        {
        	System.out.println("Nope");
        	return simulationEnemyAI.getAction(player, gameState);// new PlayerAction();
       	}
        
        // Temporary variable
        Node tempMostVisited = null;
        
        for (Node child : treeRootNode.getChildrenList())
        {
        	// if no other value has been assigned then assign child
            if (tempMostVisited == null ||
            		// or if child is better than temp variable then replace
            		child.getVisitCount() > tempMostVisited.getVisitCount() ||
            		// or if visits are the same but child's score is better then replace
            		(child.getVisitCount() == tempMostVisited.getVisitCount() && child.getScore() > tempMostVisited.getScore()))
            {
            	// Update temporary variable
                tempMostVisited = child;
            }
        }
        
        // Sanity check
        if (tempMostVisited == null)
        {
        	return simulationEnemyAI.getAction(player, gameState);// new PlayerAction();
       	}
        return treeRootNode.getActionFromChildNode(tempMostVisited);
    }
      
    

/*    
 * ---------------------------------
 * This could be a better simulation environment? Averaging N play outs.
 * ---------------------------------
*/
    // modified from the UCT example by the author santi
    // gets the best action, evaluates it for 'N' times using a simulation, and returns the average obtained value:
    public float NSimulate(GameState gameStateClone, int player, long time, int N) throws Exception
    {
        float accum = 0;
        int iteration = 0;
	       
        for(int i = 0; i < N; i++)
        {
        	if (System.currentTimeMillis() < endTime)
	        {
	        	iteration++;
	            GameState thisNGS = gameStateClone.clone();
	            simulate(thisNGS,thisNGS.getTime() + MAXSIMULATIONTIME);
	            // Discount factor:
	            accum += evaluationFunction.evaluate(player, 1-player, thisNGS)*Math.pow(0.99,time/10.0);
	        }
        	else return accum/iteration;
        }
       
            
        return accum/N;
    }    
    
    // author santi from the UCT example
    public void simulate(GameState gameState, int time) throws Exception
    {
        boolean gameover = false;

        do
        {
            if (gameState.isComplete())
            {
                gameover = gameState.cycle();
            }
            else
            {
                gameState.issue(simulationEnemyAI.getAction(0, gameState));
                gameState.issue(simulationEnemyAI.getAction(1, gameState));
            }
        }while(!gameover && gameState.getTime() < time);   
    }
    
    @Override
    public List<ParameterSpecification> getParameters()
    {
        return new ArrayList<>();
    }
}
