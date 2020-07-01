package preselection;

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

    private StateMonitor stateMonitor; // A game state wrapper exposing useful unit selection methods.
    private FunctionalGroupsMonitor functionalGroupsMonitor; // Assigns/monitors defense, offense, and harvester units.
    private SituationalGroupsMonitor situationalGroupsMonitor; // Assigns/monitor front-line units.
    private EventsManager eventsManager; // Manages event-based heuristics.

    private PreSelectionParameters parameters;

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
    public ParametricActionGenerator(GameState gameState, int playerID, PreSelectionParameters parameters) throws Exception {

        this.parameters = parameters;
        // Enumerates and decomposes all the units on the map.
        stateMonitor = new StateMonitor(gameState, playerID);
        functionalGroupsMonitor = new FunctionalGroupsMonitor(stateMonitor,
                parameters.priority, parameters.maxHarvesters,
                parameters.maxDefenseWorkers, parameters.maxDefenseLights, parameters.maxDefenseRanged, parameters.maxDefenseHeavies,
                parameters.maxOffenseWorkers, parameters.maxOffenseLights, parameters.maxOffenseRanged, parameters.maxOffenseHeavies);

        // Units decomposition based on opponent proximity, used for adaptive wait durations.
        situationalGroupsMonitor = new SituationalGroupsMonitor(stateMonitor,
                parameters.frontLineSelectionMode, parameters.maxFrontLineUnits, parameters.frontLineTacticalDistance);

        eventsManager = new EventsManager(stateMonitor, functionalGroupsMonitor);

        eventsManager.run(
                parameters.timeTriggerDefensePeriod, parameters.timeTriggerSwitchDelay, parameters.unitCountTriggerThreshold,
                parameters.scoreTriggerOverpowerFactor, parameters.scoreTriggerAssaultUnitValue, parameters.switchingUnitsCount);

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
                    unitActions = unit.getUnitActions(stateMonitor.getGameState(), parameters.frontLineWaitDuration);
                else
                    unitActions = unit.getUnitActions(stateMonitor.getGameState(), parameters.defaultWaitDuration);

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
        if (functionalGroupsMonitor.getHarvestUnits().contains(unit)) {

            HarvestManager harvestManager = new HarvestManager(stateMonitor, parameters.harvestPathFinder, unit, unitActions);
            return harvestManager.filterActions(parameters.maxBases, parameters.maxBarracks, parameters.buildLocation,
                    parameters.maxBuildActionsChosen, parameters.isolatedBuildScanRadius, parameters.isolatedBuildMaxOccupiedCells,
                    parameters.epsilonHarvestMovement, parameters.shuffleActions);
        }

        // The unit is adopting a defensive stance.
        if (functionalGroupsMonitor.getDefenseUnits().contains(unit)) {

            PathFinding pathFinder = situationalGroupsMonitor.getFrontLineUnits().contains(unit) ?
                            parameters.frontLinePathFinder : parameters.defensePathFinder;

            DefenseManager defenseManager = new DefenseManager(stateMonitor, pathFinder, unit, unitActions);
            if (defenseManager.unitOutsideDefensePerimeter(
                    parameters.horizontalDistanceFromBase, parameters.verticalDistanceFromBase, parameters.radiusFromBase))
                functionalGroupsMonitor.fromDefenseToOffenseUnit(unit); // Unit outside perimeter.
            else
                return defenseManager.filterActions(parameters.horizontalDistanceFromBase, parameters.verticalDistanceFromBase,
                        parameters.radiusFromBase, parameters.maxTargetsOnDefense, parameters.defenseMode,
                        parameters.epsilonDefenseMovement, parameters.shuffleActions);
        }

        // The unit is adopting an offensive stance.
        if (functionalGroupsMonitor.getOffenseUnits().contains(unit)) {

            PathFinding pathFinder = situationalGroupsMonitor.getFrontLineUnits().contains(unit) ?
                    parameters.frontLinePathFinder : parameters.offensePathFinder;

            OffenseManager offenseManager = new OffenseManager(stateMonitor, pathFinder, unit, unitActions);
            return offenseManager.filterActions(parameters.maxTargetsOnOffense, parameters.maxEscapes, parameters.offenseTargetMode,
                    parameters.epsilonOffenseMovement, parameters.fixedTarget, parameters.shuffleActions);
        }

        // The unit is a barracks.
        if (stateMonitor.getPlayerBarracks().contains(unit)) {

            TrainingManager trainingManager = new TrainingManager(stateMonitor, functionalGroupsMonitor, unit, unitActions);
            return trainingManager.filterBarracksActions(
                    parameters.maxDefenseLights, parameters.maxDefenseRanged, parameters.maxDefenseHeavies,
                    parameters.maxOffenseLights, parameters.maxOffenseRanged, parameters.maxOffenseHeavies,
                    parameters.maxTrainActionsChosen, parameters.trainSide, parameters.isolatedTrainScanWidth,
                    parameters.isolatedTrainScanDepth, parameters.isolatedTrainMaxOccupiedCells, parameters.shuffleActions);
        }

        // The unit is a base.
        if (stateMonitor.getPlayerBases().contains(unit)) {

            TrainingManager trainingManager = new TrainingManager(stateMonitor, functionalGroupsMonitor, unit, unitActions);
            return trainingManager.filterBaseActions(
                    parameters.maxHarvesters, parameters.maxDefenseWorkers, parameters.maxOffenseWorkers,
                    parameters.maxTrainActionsChosen, parameters.trainSide, parameters.isolatedTrainScanWidth,
                    parameters.isolatedTrainScanDepth, parameters.isolatedTrainMaxOccupiedCells, parameters.shuffleActions);
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
