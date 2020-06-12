package refactor;

import ai.abstraction.pathfinding.*;
import rts.GameState;
import rts.UnitAction;
import rts.units.Unit;
import util.Pair;

import java.util.*;

/**
 * This class implements an adaptive move generation approach inspired by the CmabAsymmetricGenerator by @rubens
 * Not final, WIP.
 * @author Acemad
 */
public class ParametricActionGenerator {

    // THE DESIRED ARMY COMPOSITION ********************************************************************************
    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    private int maxBases = 1;          // The number of Bases to build, at most.
    private int maxBarracks = 2 ;      // The number of Barracks to build, at most.
    private int maxHarvesters = 3;     // The number of harvesting Workers to train, at most.
    private int maxOffenseWorkers = 4; // The number of offense Workers to train, at most.
    private int maxOffenseLights = 0;  // The number of offense Lights to train, at most.
    private int maxOffenseRanged = 0;  // The number of offense Ranged to train, at most.
    private int maxOffenseHeavies = 0; // The number of offense Heavies to train, at most.
    private int maxDefenseWorkers = 0; // The number of defense Workers to train, at most.
    private int maxDefenseLights = 3;  // The number of defense Lights to train, at most.
    private int maxDefenseRanged = -1; // The number of defense Ranged to train, at most.
    private int maxDefenseHeavies = 2; // The number of defense Heavies to train, at most.

    // Unit assignment priority, either assign Defense units first or Offense units first.
    private int priority = FunctionalGroupsMonitor.DEFENSE_PRIORITY;

    // DEFENSE : parameters defining the defense behavior. **********************************************************
    // Rectangular defense perimeter: If both vertical and horizontal distances are > 0, a rectangular defense perimeter
    // is set, otherwise, it is disabled and a circular defense perimeter may take its place.
    // The horizontal distance from base.
    private int horizontalDistanceFromBase = 0;
    // The vertical distance from base.
    private int verticalDistanceFromBase = 0;

    // Circular defense perimeter: if the radius is > 0, a circular defense perimeter is set, otherwise, it is disabled.
    // If both perimeter types dimensions are set to 0, defense is disabled.
    // The defense radius from base.
    private int radiusFromBase = 7;

    // The maximum number of enemy units to target on defense mode.
    private int maxTargetsOnDefense = 2;

    // The defense mode in place. Whom to attack while defending, either base attackers or self attackers.
    private int defenseMode = DefenseManager.DEFEND_BASE;

    // The maximum number of cycles defense units will stay inside the perimeter. -1: stay indefinitely.
    // After each defense period, a stance switch is triggered, from def to attack. -1: disabled.
    private int timeTriggerDefensePeriod = -1;
    // The amount of time in which defense units are allowed to switch stance, after the time trigger.
    private int timeTriggerSwitchDelay = 200;
    // The number of defense units that triggers a DefToOff switch when reached. -1: disabled.
    private int unitCountTriggerThreshold = -1;
    // The additional score percent needed by the player, above the opponent's score, to trigger a switch.
    // -1: disabled, 0: the minimum score greater than the opponent's score.
    private float scoreTriggerOverpowerFactor = 0.25f;
    // The value of assault units with respect to worker units when calculating the trigger score.
    private int scoreTriggerAssaultUnitValue = 4;
    // The number of units concerned by the stance switch, -1: switch all defense units.
    private int switchingUnitsCount = -1;

    // OFFENSE : parameters defining the attack behavior. ************************************************************
    // If a unit can attack and also move, maxEscapes limit the number of move actions allowed
    private int maxEscapes = 1;
    // The maximum number of enemy units to target on attack mode.
    private int maxTargetsOnOffense = 2;
    // The attack mode in use.
    private int offenseTargetMode = OffenseManager.TARGET_CLOSEST;

    // A fixed target that is always added to the targeted units. Can be the opponent base or barracks.
    private int fixedTarget = OffenseManager.FIXED_TARGET_ALL_STRUCTURES;

    // PRODUCTION : Building / Training *****************************************************************************
    // The Barracks building location. HARVESTERS:------------------------------
    private int buildLocation = HarvestManager.BUILD_AT_ISOLATED_LOCATION;
    // If a unit can produce, limit the number of produce actions to chose from.
    private int maxBuildActionsChosen = 2;

    // In case isolated build location is activated, this parameter defines the radius to scan around the future build
    // location.
    private int isolatedBuildScanRadius = 1;
    // Indicates the maximum number of occupied cells tolerated in the scanned region.
    private int isolatedBuildMaxOccupiedCells = 1;

    // The mobile units training location. STRUCTURES:--------------------------
    private int trainSide = TrainingManager.TRAIN_AT_ISOLATED_SIDE;
    // Limit of the number of training actions to chose from.
    private int maxTrainActionsChosen = 2;

    // In case isolated train location is activated, this parameter defines the width of the zone to scan from either
    // sides of the future train location.
    private int isolatedTrainScanWidth = 1;
    // Defines the depth of the scanned zone ahead of the future train location.
    private int isolatedTrainScanDepth = 2;
    // Indicates the maximum number of occupied cells tolerated in the scanned region.
    private int isolatedTrainMaxOccupiedCells = 2;

    // Exploration parameter for movements. 1 : Random, 0 : Focused (using path finding) ****************************
    // The exploration parameter of harvesting units.
    private float epsilonHarvestMovement = 0.0f;
    // The exploration parameter of defense units.
    private float epsilonDefenseMovement = 0.05f;
    // The exploration parameter of offense units.
    private float epsilonOffenseMovement = 0.25f;

    // Front Line tactics, -1 maxFontLineUnits means take all units in the front-line *******************************
    // Front-Line selection method.
    private int frontLineSelectionMode = SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE;
    // Maximum number of front-line units to consider. -1: take all, 0: take none.
    private int maxFrontLineUnits = 3;
    // The tactical distance ahead of attack range.
    private int frontLineTacticalDistance = 1;
    // Wait duration of front-line units.
    private int frontLineWaitDuration = 3;
    // Wait duration of back units.
    private int backWaitDuration = 10;

    // Shuffles the returned actions.
    private boolean shuffleActions = false;

    // The path finding algorithm used to direct movement, for each unit group.
    private PathFinding harvestPathFinder = new AStarPathFinding(); /*new FloodFillPathFinding();*/ /*new BFSPathFinding();*/ /*new GreedyPathFinding();*/
    private PathFinding defensePathFinder = new FloodFillPathFinding();
    private PathFinding offensePathFinder = new AStarPathFinding();
    private PathFinding frontLinePathFinder = new FloodFillPathFinding();

    // *************************************************************** Parameters End.

    private StateMonitor stateMonitor; // A game state wrapper exposing useful unit selection methods.
    private FunctionalGroupsMonitor groupsMonitor; // Assigns/monitors defense, offense, and harvester units.
    private SituationalGroupsMonitor situationalGroupsMonitor; // Assigns/monitor front-line units.
    private EventsManager eventsManager; // Manages event-based heuristics.

    // The list of action choices, for each unit.
    private List<Pair<Unit, List<UnitAction>>> choices = new ArrayList<>();;
    // Size of the possible choice combination.
    private long size = 1;

    /**
     * The main constructor, populates the choices list with all the possible actions, relying on the different
     * decompositions and heuristics.
     *
     * @param gameState
     * @param playerID
     * @throws Exception
     */
    public ParametricActionGenerator(GameState gameState, int playerID) throws Exception {

        // Enumerates and decomposes all the units on the map.
        stateMonitor = new StateMonitor(gameState, playerID);
        groupsMonitor = new FunctionalGroupsMonitor(stateMonitor, priority, maxHarvesters,
                maxDefenseWorkers, maxDefenseLights, maxDefenseRanged, maxDefenseHeavies,
                maxOffenseWorkers, maxOffenseLights, maxOffenseRanged, maxOffenseHeavies);

        // Units decomposition based on opponent proximity, used for adaptive wait durations.
        situationalGroupsMonitor = new SituationalGroupsMonitor(stateMonitor, frontLineSelectionMode,
                maxFrontLineUnits, frontLineTacticalDistance);

        eventsManager = new EventsManager(stateMonitor, groupsMonitor);
        eventsManager.run(timeTriggerDefensePeriod, timeTriggerSwitchDelay, unitCountTriggerThreshold,
                scoreTriggerOverpowerFactor, scoreTriggerAssaultUnitValue, switchingUnitsCount);

//        if (playerID == 1)
//            System.out.println("Front Lines : " + frontLineUnits.size());

        // Assign unit actions to each unit group.
        generateUnitActionChoices(true);

        if (choices.size() == 0) {
            System.err.println("Problematic game state:");
            System.err.println(gameState);
            throw new Exception(
                    "Move generator for player " + playerID + " created with no units that can execute actions! (status: "
                            + gameState.canExecuteAnyAction(0) + ", " + gameState.canExecuteAnyAction(1) + ")"
            );
        }
    }

    /**
     * Generate low-level unit actions for a given group of units. The wait action generated is assigned a predefined
     * duration. Unit-actions are filtered heuristically before returning.
     *
     * @param heuristicFiltering If true, heuristic filtering is applied to each unit and its unit actions.
     */
    private void generateUnitActionChoices(boolean heuristicFiltering) {
        for (Unit unit : stateMonitor.getAllPlayerUnits()) {
            if (stateMonitor.getUnitActions().get(unit) == null) {

                List<UnitAction> unitActions;
                if (situationalGroupsMonitor.getFrontLineUnits().contains(unit))
                    unitActions = unit.getUnitActions(stateMonitor.getGameState(), frontLineWaitDuration);
                else
                    unitActions = unit.getUnitActions(stateMonitor.getGameState(), backWaitDuration);

                if (heuristicFiltering)
                    unitActions = heuristicFiltering(unit, unitActions);


                choices.add(new Pair<>(unit, unitActions));

                long tmp = unitActions.size();
                if (Long.MAX_VALUE / size <= tmp)
                    size = Long.MAX_VALUE;
                else
                    size *= unitActions.size();
            }
        }
    }

    /**
     * This method performs what we call heuristic filtering, or pruning, on the set of possible unit-actions of a unit.
     * Depending on the unit type it will discard a number of actions judged less useful depending on the context.
     * It also reduces the number of produce actions to a single direction, instead of four.
     *
     * @param unit The unit in question.
     * @param unitActions All the possible unit-actions.
     * @return A filtered list of unit-actions.
     */
    private List<UnitAction> heuristicFiltering(Unit unit, List<UnitAction> unitActions) {

        // The unit is a harvesting worker.
        if (groupsMonitor.getHarvestUnits().contains(unit)) {

            HarvestManager harvestManager = new HarvestManager(stateMonitor, harvestPathFinder, unit, unitActions);
            return harvestManager.filterActions(maxBases, maxBarracks, buildLocation, maxBuildActionsChosen,
                    isolatedBuildScanRadius, isolatedBuildMaxOccupiedCells, epsilonHarvestMovement, shuffleActions);
        }

        // The unit is adopting a defensive stance.
        if (groupsMonitor.getDefenseUnits().contains(unit)) {

            PathFinding pathFinder = situationalGroupsMonitor.getFrontLineUnits().contains(unit) ?
                            frontLinePathFinder : defensePathFinder;

            DefenseManager defenseManager = new DefenseManager(stateMonitor, pathFinder, unit, unitActions);
            if (defenseManager.unitOutsideDefensePerimeter(horizontalDistanceFromBase, verticalDistanceFromBase,
                    radiusFromBase))
                groupsMonitor.fromDefenseToOffenseUnit(unit); // Unit outside perimeter.
            else
                return defenseManager.filterActions(horizontalDistanceFromBase, verticalDistanceFromBase, radiusFromBase,
                        maxTargetsOnDefense, defenseMode, epsilonDefenseMovement, shuffleActions);
        }

        // The unit is adopting an offensive stance.
        if (groupsMonitor.getOffenseUnits().contains(unit)) {

            PathFinding pathFinder = situationalGroupsMonitor.getFrontLineUnits().contains(unit) ?
                    frontLinePathFinder : offensePathFinder;

            OffenseManager offenseManager = new OffenseManager(stateMonitor, pathFinder, unit, unitActions);
            return offenseManager.filterActions(maxTargetsOnOffense, maxEscapes, offenseTargetMode, epsilonOffenseMovement,
                    fixedTarget, shuffleActions);
        }

        // The unit is a barracks.
        if (stateMonitor.getPlayerBarracks().contains(unit)) {

            TrainingManager trainingManager = new TrainingManager(stateMonitor, groupsMonitor, unit, unitActions);
            return trainingManager.filterBarracksActions(maxDefenseLights, maxDefenseRanged, maxDefenseHeavies,
                    maxOffenseLights, maxOffenseRanged, maxOffenseHeavies, maxTrainActionsChosen, trainSide,
                    isolatedTrainScanWidth, isolatedTrainScanDepth, isolatedTrainMaxOccupiedCells,
                    shuffleActions);
        }

        // The unit is a base.
        if (stateMonitor.getPlayerBases().contains(unit)) {

            TrainingManager trainingManager = new TrainingManager(stateMonitor, groupsMonitor, unit, unitActions);
            return trainingManager.filterBaseActions(maxHarvesters, maxDefenseWorkers, maxOffenseWorkers,
                    maxTrainActionsChosen, trainSide, isolatedTrainScanWidth, isolatedTrainScanDepth,
                    isolatedTrainMaxOccupiedCells, shuffleActions);
        }

        return unitActions;
    }

    public List<Pair<Unit, List<UnitAction>>> getChoices() {
        return choices;
    }

    public long getSize() {
        return size;
    }
}
