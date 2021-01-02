import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.ParameterSpecification;
import nmcts.ParametricNMCTS;
import preselection.*;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;

import java.util.List;

public class UMSBot extends AIWithComputationBudget {

    UnitTypeTable unitTypeTable;
    AI agent;
    boolean started = false;
    int player;

    int simulationTime = 0, maxDepth = 0; // Uninitialized.
    float ipaPruneRate, epsilon0, epsilonGlobal, epsilonLocal;

    StateMonitor stateMonitor;
    PreSelectionParameters parameters = new PreSelectionParameters();

    /**
     * Constructs the controller with the specified time and iterations budget
     *
     * @param timeBudget       time in milliseconds
     * @param iterationsBudget number of allowed iterations
     */
    public UMSBot(int timeBudget, int iterationsBudget) {
        super(timeBudget, iterationsBudget);
    }

    public UMSBot(UnitTypeTable unitTypeTable) {
        super(100, -1);
        this.unitTypeTable = unitTypeTable;
    }

    public UMSBot(UnitTypeTable unitTypeTable, int simulationTime, int maxDepth) {
        this(unitTypeTable);
        this.simulationTime = simulationTime;
        this.maxDepth = maxDepth;
    }

    @Override
    public void reset() {
        this.started = false;
    }

    @Override
    public PlayerAction getAction(int player, GameState gameState) throws Exception {
        if (!started) {
            initialize(gameState);
            started = true;
            this.player = player;
        }
        return agent.getAction(player, gameState);
    }

    void initialize(GameState gameState) {

        stateMonitor = new StateMonitor(gameState, player);
//        PhysicalGameState physicalGameState = gameState.getPhysicalGameState();
        int mapWidth = stateMonitor.getMapWidth();
        int mapHeight = stateMonitor.getMapHeight();

        if (mapWidth <= 8) { // 8x8
            parameters.setUnitComposition(1, 0, 1, -1, 0, 0, 0, 2, 0, 0, 0, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setHarvest(0.0f, PreSelectionParameters.FLOOD_FILL_PF);
            parameters.setDefense(2, 2, 0, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.0f);
            parameters.setOffense(3, 2, OffenseManager.TARGET_CLOSEST, OffenseManager.FIXED_TARGET_BASE_FIRST, PreSelectionParameters.A_STAR_PF, 0.0f);
            parameters.setDefenseSwitch(-1, 0, -1, 1.1f, 4, -1); // OldOverpowerFactor:0.1f
            parameters.setBuilding(HarvestManager.BUILD_AT_ISOLATED_LOCATION, 0, 1, 1);
            parameters.setTraining(TrainingManager.TRAIN_AT_ISOLATED_SIDE, 2, 1, 1, 2);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.9f;
            epsilon0 = 0.3f; epsilonGlobal = 0.3f; epsilonLocal = 0.3f;
        }
        else if (mapWidth == 9) { //9x8
            parameters.setUnitComposition(1, 1, 2, 0, 0, -1, 0, 0, 0, 0, 0, FunctionalGroupsMonitor.DEFENSE_PRIORITY);
            parameters.setHarvest(0.0f, PreSelectionParameters.FLOOD_FILL_PF);
            parameters.setDefense(0, 0, 0, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setOffense(2, 1, OffenseManager.TARGET_CLOSEST, OffenseManager.NO_FIXED_TARGET, PreSelectionParameters.A_STAR_PF, 0.0f);
            parameters.setDefenseSwitch(-1, 0, -1, -1f, 4, -1);
            parameters.setBuilding(HarvestManager.BUILD_AT_RANDOM_LOCATION, 10, 1, 2);
            parameters.setTraining(TrainingManager.TRAIN_AT_RANDOM_SIDE, 3, 0, 0, 0);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.9f;
            epsilon0 = 0.3f; epsilonGlobal = 0.2f; epsilonLocal = 0.2f;
        }
        else if (mapWidth <= 16) { //10x10 12x12 16x16
//            if (stateMonitor.getPlayerBarracks().size() > 0)
                parameters.setUnitComposition(1, 2, 2, 0, 2, 0, 0, 0, -1, -1, 2, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
//            else
//                parameters.setUnitComposition(1, 2, 2, 1, 2, 0, 0, 0, -1, -1, 2, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setHarvest(0.05f, PreSelectionParameters.FLOOD_FILL_PF);
            parameters.setDefense(0, 0, (mapWidth / 2) - 2, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setOffense(2, 1, OffenseManager.TARGET_CLOSEST, OffenseManager.FIXED_TARGET_BARRACKS_FIRST, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setDefenseSwitch(-1, 0, -1, 2f, 4, -1);
            parameters.setBuilding(HarvestManager.BUILD_AT_ISOLATED_LOCATION, 2, 1, 1);
            parameters.setTraining(TrainingManager.TRAIN_AT_ISOLATED_SIDE, 2, 1, 2, 2);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.0f;
            epsilon0 = 0.3f; epsilonGlobal = 0.3f; epsilonLocal = 0.3f;
        }
        else if (mapWidth <= 24) { //24x24
            parameters.setUnitComposition(1, 2, 2, 0, 2, 0, 0, 0, -1, -1, 2, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setHarvest(0.05f, PreSelectionParameters.FLOOD_FILL_PF);
            parameters.setDefense(0, 0, (mapWidth / 2) - 2, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setOffense(2, 1, OffenseManager.TARGET_CLOSEST, OffenseManager.FIXED_TARGET_ALL_STRUCTURES, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setDefenseSwitch(-1, 0, -1, 2f, 4, -1);
            parameters.setBuilding(HarvestManager.BUILD_AT_ISOLATED_LOCATION, 2, 1, 1);
            parameters.setTraining(TrainingManager.TRAIN_AT_ISOLATED_SIDE, 2, 1, 2, 2);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.0f;
            epsilon0 = 0.3f; epsilonGlobal = 0.3f; epsilonLocal = 0.3f;
        }
        else if (mapWidth <= 32) { //32x32
//            parameters.setUnitComposition(1, 1, 2, 0, 2, 0, 0, 0, -1, -1, 2, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setUnitComposition(1, 2, 4, 0, 2, 0, 0, 0, -1, -1, 2, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setHarvest(0.05f, PreSelectionParameters.A_STAR_PF);
            parameters.setDefense(0, 0, (mapWidth / 2) - 2, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setOffense(2, 1, OffenseManager.TARGET_CLOSEST, OffenseManager.FIXED_TARGET_ALL_STRUCTURES, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setDefenseSwitch(-1, 0, -1, 2f, 4, -1);
            parameters.setBuilding(HarvestManager.BUILD_AT_ISOLATED_LOCATION, 2, 1, 1);
            parameters.setTraining(TrainingManager.TRAIN_AT_ISOLATED_SIDE, 2, 1, 2, 2);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.0f;
            epsilon0 = 0.3f; epsilonGlobal = 0.3f; epsilonLocal = 0.3f;
        }
        else { //64x64 128x128 ++
            parameters.setUnitComposition(1, 1, 5, 0, 2, 0, 0, 0, -1, 0, 0, FunctionalGroupsMonitor.OFFENSE_PRIORITY);
            parameters.setHarvest(0.05f, PreSelectionParameters.A_STAR_PF);
            parameters.setDefense(0, 0, (mapWidth / 2) - 2, 1, DefenseManager.DEFEND_BASE, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setOffense(2, 1, OffenseManager.TARGET_CLOSEST, OffenseManager.FIXED_TARGET_ALL_STRUCTURES, PreSelectionParameters.A_STAR_PF, 0.05f);
            parameters.setDefenseSwitch(-1, 0, -1, 2f, 4, -1);
            parameters.setBuilding(HarvestManager.BUILD_AT_ISOLATED_LOCATION, 2, 1, 1);
            parameters.setTraining(TrainingManager.TRAIN_AT_ISOLATED_SIDE, 2, 1, 2, 2);
            parameters.setFrontLine(SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE, 3, 1, 3, PreSelectionParameters.A_STAR_PF);

            if (simulationTime == 0 && maxDepth == 0) {
                simulationTime = 200;
                maxDepth = 20;
            }
            ipaPruneRate = 0.0f;
            epsilon0 = 0.3f; epsilonGlobal = 0.3f; epsilonLocal = 0.3f;
        }

        agent = new ParametricNMCTS(unitTypeTable, getTimeBudget(), getIterationsBudget(), simulationTime, maxDepth,
                epsilon0, epsilonGlobal, epsilonLocal, ipaPruneRate, parameters);
    }

    @Override
    public void preGameAnalysis(GameState gameState, long milliseconds) throws Exception {
        initialize(gameState);
        started = true;
    }

    @Override
    public AI clone() {
        UMSBot cloned = new UMSBot(unitTypeTable, simulationTime, maxDepth);
        cloned.agent = agent;
        return cloned;
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return null;
    }

    @Override
    public String statisticsString() {
        return agent.statisticsString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + TIME_BUDGET + ", " + ITERATIONS_BUDGET + ", " + simulationTime + ", "
                + maxDepth + ")";
    }
}
