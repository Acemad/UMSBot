package nmcts;

import ai.RandomBiasedAI;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.InterruptibleAI;
import ai.core.ParameterSpecification;
import ai.evaluation.EvaluationFunction;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import com.eclipsesource.json.JsonObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import preselection.PreSelectionParameters;
import rts.GameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;
import scala.util.parsing.combinator.testing.Str;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ParametricNMCTS extends AIWithComputationBudget implements InterruptibleAI {

    // Members ************************************************************************************

    private GameState initialGameState;
    private EvaluationFunction evaluationFunction = new SimpleSqrtEvaluationFunction3();
    private AI playoutPolicy = new RandomBiasedAI();
    private ParametricNMCTSNode tree;
    private PreSelectionParameters parameters;

    private int player;
    private int simulationTime = 1024;
    private int maxDepth = 10;
    private long maxActions = 0;
    private int currentIteration = 0;

    // NaïveMCTS Specific attributes. The epsilon-greedy strategies parameters.
    private float epsilon0 = 0.2f;
    private float epsilonGlobal = 0.0f;
    private float epsilonLocal = 0.25f;

    private int globalStrategy = ParametricNMCTSNode.EPSILON_GREEDY;
    private boolean exploreNonSampledActions = true;
    private double evaluationBound;

    // Stats
    private int treeDepth = 0;
    private long totalRuns = 0;
    private long totalCyclesExecuted = 0;
    private long totalActionsIssued = 0;
    private long totalTime = 0;

    // Inactivity Filtering
    private float ipaAllowProbability = 0.0f;

    public ParametricNMCTS(UnitTypeTable unitTypeTable, float ipaAllowProbability) {
        this(unitTypeTable);
        this.ipaAllowProbability = ipaAllowProbability;
    }

    /**
     *
     * @param unitTypeTable
     */
    public ParametricNMCTS(UnitTypeTable unitTypeTable) {
        this(100, -1, 100, 15, 0.4f, 0.0f, 0.3f,
              ParametricNMCTSNode.EPSILON_GREEDY, new RandomBiasedAI(), new SimpleSqrtEvaluationFunction3(), true,
                0.0f, new PreSelectionParameters());
    }

    public ParametricNMCTS(UnitTypeTable unitTypeTable, int simulationTime, int maxDepth, float epsilon0, float epsilonGlobal,
                           float epsilonLocal, float ipaAllowProbability, PreSelectionParameters parameters) {
        this(unitTypeTable, 100, -1, simulationTime, maxDepth, epsilon0, epsilonGlobal,
                epsilonLocal, ipaAllowProbability, parameters);
    }

    /**
     *
     * @param unitTypeTable
     * @param timeBudget
     * @param iterationBudget
     * @param simulationTime
     * @param maxDepth
     * @param epsilon0
     * @param epsilonGlobal
     * @param epsilonLocal
     * @param ipaAllowProbability
     * @param parameters
     */
    public ParametricNMCTS(UnitTypeTable unitTypeTable, int timeBudget, int iterationBudget,  int simulationTime,
                           int maxDepth, float epsilon0, float epsilonGlobal, float epsilonLocal, float ipaAllowProbability,
                           PreSelectionParameters parameters) {

        this(timeBudget, iterationBudget, simulationTime, maxDepth, epsilon0, epsilonGlobal, epsilonLocal,
             ParametricNMCTSNode.EPSILON_GREEDY, new RandomBiasedAI(), new SimpleSqrtEvaluationFunction3(),
                true, ipaAllowProbability, parameters);
    }

    /**
     * Constructs the controller with the specified time and iterations budget
     *
     * @param timeBudget       time in milliseconds
     * @param iterationsBudget number of allowed iterations
     */
    public ParametricNMCTS(int timeBudget, int iterationsBudget, int simulationTime, int maxDepth,
                           float epsilon0, float epsilonGlobal, float epsilonLocal, int globalStrategy, AI playoutPolicy,
                           EvaluationFunction evaluationFunction, boolean exploreNonSampledActions, float ipaAllowProbability,
                           PreSelectionParameters parameters) {
        super(timeBudget, iterationsBudget);
        this.simulationTime = simulationTime;
        this.maxDepth = maxDepth;
        this.epsilon0 = epsilon0;
        this.epsilonGlobal = epsilonGlobal;
        this.epsilonLocal = epsilonLocal;
        this.globalStrategy = globalStrategy;
        this.playoutPolicy = playoutPolicy;
        this.evaluationFunction = evaluationFunction;
        this.exploreNonSampledActions = exploreNonSampledActions;
        this.ipaAllowProbability = ipaAllowProbability;
        this.parameters = parameters;
    }

    /**
     *
     * @param player ID of the player to move. Use it to check whether units are yours or enemy's
     * @param gameState
     * @return
     * @throws Exception
     */
    @Override
    public PlayerAction getAction(int player, GameState gameState) throws Exception {
        if (gameState.canExecuteAnyAction(player)) {
            startNewComputation(player, gameState.clone());
            computeDuringOneGameFrame();
            return getBestActionSoFar();
        } else
            return new PlayerAction();
    }

    /**
     * Initiates the computation of the optimal action to return.
     * @param player the index of the player have its action calculated
     * @param gameState
     * @throws Exception
     */
    @Override
    public void startNewComputation(int player, GameState gameState) throws Exception {
        this.player = player;
        currentIteration = 0;
        initialGameState = gameState;
        // Create the search tree, and increase the currentIteration counter afterwards.
        tree = new ParametricNMCTSNode(player, gameState, null, currentIteration++, exploreNonSampledActions, parameters);
        evaluationBound = evaluationFunction.upperBound(gameState);

        if (tree.getActionGenerator() == null)
            maxActions = 0;
        else
            maxActions = Math.max(tree.getActionGenerator().getSize(), maxActions);
    }

    @Override
    public void computeDuringOneGameFrame() throws Exception {
        // Time budget
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        // Iteration budget
        long iterations = 0;

        // Main loop
        while (true) {
            if (!monteCarloRun(player)) break;
            iterations++;
            endTime = System.currentTimeMillis();
            if (TIME_BUDGET >= 0 && (endTime - startTime) >= TIME_BUDGET) break;
            if (ITERATIONS_BUDGET >= 0 && iterations >= ITERATIONS_BUDGET) break;
        }

        // Stats
        totalTime += (endTime - startTime);
        totalCyclesExecuted++;
    }

    private boolean monteCarloRun(int player) throws Exception {
        // (1) Selection and Expansion
        ParametricNMCTSNode selected = tree.selectLeaf(player, epsilon0, epsilonGlobal, epsilonLocal,
                globalStrategy, maxDepth, currentIteration++, evaluationBound, ipaAllowProbability);

        if (selected.getDepth() > treeDepth)
            treeDepth = selected.getDepth();

        if (selected != null) {
            // (2) Simulation and evaluation
            GameState simGameState = selected.getGameState().clone();
            simulate(simGameState, simGameState.getTime() + simulationTime);

            int time = simGameState.getTime() - initialGameState.getTime();
            double evaluation = evaluationFunction.evaluate(player, 1 - player, simGameState);
            evaluation *= Math.pow(0.99, time / 10.0);

            // (3) Backpropagation
            selected.backpropagate(evaluation, null);
            totalRuns++;
        } else {
            System.err.println(this.getClass().getSimpleName() + " : Claims there are no more leafs to explore !");
            return false;
        }
        return true;
    }

    @Override
    public PlayerAction getBestActionSoFar() throws Exception {
        int bestActionIndex = getMostVisitedActionIndex();
        if (bestActionIndex == -1)
            return new PlayerAction();
        return tree.getActions().get(bestActionIndex);
    }

    private int getMostVisitedActionIndex() {
        totalActionsIssued++;

        ParametricNMCTSNode mostVisitedChild = null; // The most visited child
        int mostVisitedChildIndex = -1; // The most visited child index

        if (tree.getChildren() == null)
            return -1;

        // Find the most visited child.
        for (int index = 0; index < tree.getChildren().size(); index++) {
            ParametricNMCTSNode child = tree.getChildren().get(index);
            if (mostVisitedChild == null || child.getVisitCount() > mostVisitedChild.getVisitCount()) {
                mostVisitedChild = child;
                mostVisitedChildIndex = index;
            }
        }

        return mostVisitedChildIndex;
    }

    private int getHighestEvaluatedActionIndex() {
        totalActionsIssued++;

        ParametricNMCTSNode highestEvaluatedChild = null; // The highest evaluated child.
        int highestEvaluatedChildIndex = -1; // The highest evaluated child index.

        if (tree.getChildren() == null)
            return -1;

        // Find the highest evaluated child.
        for (int index = 0; index < tree.getChildren().size(); index++) {
            ParametricNMCTSNode child = tree.getChildren().get(index);
            if (highestEvaluatedChild == null ||
               (child.getAccumulatedEvaluation() / (double) child.getVisitCount()) >
                       (highestEvaluatedChild.getAccumulatedEvaluation() / (double) highestEvaluatedChild.getVisitCount())) {
                highestEvaluatedChild = child;
                highestEvaluatedChildIndex = index;
            }
        }

        return highestEvaluatedChildIndex;
    }

    private void simulate(GameState gameState, int simTime) throws Exception {
        boolean gameOver = false;

        do {
            if (gameState.isComplete())
                gameOver = gameState.cycle();
            else {
                gameState.issue(playoutPolicy.getAction(0, gameState));
                gameState.issue(playoutPolicy.getAction(1, gameState));
            }
        } while (!gameOver && gameState.getTime() < simTime);
    }

    @Override
    public void reset() {
        tree = null;
        initialGameState = null;
        currentIteration = 0;
        // Reset stats.
        totalRuns = 0;
        totalCyclesExecuted = 0;
        totalActionsIssued = 0;
        totalTime = 0;
        treeDepth = 0;
    }

    public void resetSearch() {
        tree = null;
        initialGameState = null;
    }

    @Override
    public AI clone() {
        return new ParametricNMCTS(TIME_BUDGET, ITERATIONS_BUDGET, simulationTime, maxDepth,
                         epsilon0, epsilonGlobal, epsilonLocal, globalStrategy, playoutPolicy,
                         evaluationFunction, exploreNonSampledActions, ipaAllowProbability, parameters.clone());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + TIME_BUDGET + ", " + ITERATIONS_BUDGET + ", " + simulationTime +
               ", " + maxDepth + ", " + epsilon0 + ", " + epsilonGlobal + ", " + epsilonLocal + ", " + ipaAllowProbability +
                ", " + globalStrategy + ", " + playoutPolicy + ", " + evaluationFunction + ", " + exploreNonSampledActions;
    }

    @Override
    public String statisticsString() {
        return "Total Runs: " + totalRuns +
               ", Runs per action: " + (totalRuns / (float) totalActionsIssued) +
               ", Runs per cycle: " + (totalRuns / (float) totalCyclesExecuted) +
               ", Average time per cycle: " + (totalTime / (float) totalCyclesExecuted) +
               ", Max branching factor: " + maxActions +
               ", Max Tree Depth: " + treeDepth;
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("TimeBudget", int.class, 100));
        parameters.add(new ParameterSpecification("IterationsBudget", int.class, -1));
        parameters.add(new ParameterSpecification("SimulationTime", int.class, 100));
        parameters.add(new ParameterSpecification("MaxDepth", int.class, 10));

        parameters.add(new ParameterSpecification("Epsilon0", float.class, 0.0f));
        parameters.add(new ParameterSpecification("EpsilonGlobal", float.class, 0.0f));
        parameters.add(new ParameterSpecification("EpsilonLocal", float.class, 0.0f));

        parameters.add(new ParameterSpecification("PlayoutPolicy", AI.class, playoutPolicy));
        parameters.add(new ParameterSpecification("EvaluationFunction", EvaluationFunction.class, new SimpleSqrtEvaluationFunction3()));
        parameters.add(new ParameterSpecification("ExploreNonSampledActions", boolean.class, true));

        return parameters;
    }

    public String printPreselectionParameters() {
        String output = parameters.toString();
        output += "\n[MCTS]:   epsilon0:" + epsilon0 + " epsilonGlobal:" + epsilonGlobal +
                " epsilonLocal:" + epsilonLocal + " IPAAllowProb:" + ipaAllowProbability;
        return output;
    }

    public String toJSONStr() throws Exception {
        return "{\"epsilon0\":" + epsilon0 + ", " +
               "\"epsilonLocal\":" + epsilonLocal + ", " +
               "\"epsilonGlobal\":" + epsilonGlobal + ", " +
               "\"ipaAllowProbability\":" + ipaAllowProbability + ", " +
               "\"simulationTime\":" + simulationTime + ", " +
               "\"maxDepth\":" + maxDepth + ", " +
               "\"parameters\":" + parameters.toJSONStr() + "}";
    }

    public void toJSONFile(String path) throws Exception {
        FileWriter fileWriter = new FileWriter(path);
        fileWriter.write(toJSONStr());
        fileWriter.close();
    }

    public ParametricNMCTS(UnitTypeTable unitTypeTable, String json) throws Exception {
        super(100, -1);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = new ObjectMapper().readTree(json);
        this.epsilon0 = (float) node.get("epsilon0").asDouble();
        this.epsilonGlobal = (float) node.get("epsilonGlobal").asDouble();
        this.epsilonLocal = (float) node.get("epsilonLocal").asDouble();
        this.ipaAllowProbability = (float) node.get("ipaAllowProbability").asDouble();
        //this.simulationTime = node.get("simulationTime").asInt();
        //this.maxDepth = node.get("maxDepth").asInt();
        this.parameters = PreSelectionParameters.fromJSON(node.get("parameters").toString());
    }

    public void setSimulationTime(int simulationTime) {
        this.simulationTime = simulationTime;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public float getIpaAllowProbability() {
        return this.ipaAllowProbability;
    }

    public void setIpaAllowProbability(float allowProbability) {
        this.ipaAllowProbability = allowProbability;
    }

    public int getSimulationTime() {
        return this.simulationTime;
    }

    public int getMaxDepth() {
        return this.maxDepth;
    }

    public float getEpsilon0() {
        return epsilon0;
    }

    public void setEpsilon0(float epsilon0) {
        this.epsilon0 = epsilon0;
    }

    public float getEpsilonGlobal() {
        return epsilonGlobal;
    }

    public void setEpsilonGlobal(float epsilonGlobal) {
        this.epsilonGlobal = epsilonGlobal;
    }

    public float getEpsilonLocal() {
        return epsilonLocal;
    }

    public void setEpsilonLocal(float epsilonLocal) {
        this.epsilonLocal = epsilonLocal;
    }




}
