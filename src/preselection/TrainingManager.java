package preselection;

import rts.UnitAction;
import rts.units.Unit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TrainingManager extends HeuristicsManager {

    // Training Modes *************************************************************************************************
    public static final int TRAIN_AT_RANDOM_SIDE = 0; // Train at a random side (adjacent to base/barracks)
    public static final int TRAIN_AT_ISOLATED_SIDE = 1; // Train units in the outer side of the structures.

    FunctionalGroupsMonitor groupsMonitor;

    public TrainingManager(StateMonitor stateMonitor, FunctionalGroupsMonitor groupsMonitor, Unit unit, List<UnitAction> unitActions) {
        super(stateMonitor, unit, unitActions);
        this.groupsMonitor = groupsMonitor;
    }

    /**
     * Filter unit-actions of a Barracks unit.
     * If the Barracks can produce a specific unit type, check whether the defense or attack group limits of that
     * unit type are reached or not, if not, allow production.
     *
     * @param maxProduceActionsChosen The maximum number of produce actions to chose, per unit-type.
     * @return A filtered unit-actions list.
     */
    public List<UnitAction> filterBarracksActions(int maxDefenseLights, int maxDefenseRanged, int maxDefenseHeavies,
                                                  int maxAttackLights, int maxAttackRanged, int maxAttackHeavies,
                                                  int maxProduceActionsChosen, int trainingSide,
                                                  int scanWidth, int scanDepth, int maxOccupiedCells,
                                                  boolean shuffleActions) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceLightActions.isEmpty()) {
            if ((maxDefenseLights == -1 || groupsMonitor.getDefenseLights().size() + stateMonitor.futurePlayerLights < maxDefenseLights) ||
                    (maxAttackLights == -1 || groupsMonitor.getOffenseLights().size() + stateMonitor.futurePlayerLights < maxAttackLights))
                filteredActions.addAll(selectTrainingActions(trainingSide, produceLightActions, maxProduceActionsChosen,
                        scanWidth, scanDepth, maxOccupiedCells));
        }

        if (!produceRangedActions.isEmpty()) {
            if ((maxDefenseRanged == -1 || groupsMonitor.getDefenseRanged().size() + stateMonitor.futurePlayerRanged < maxDefenseRanged) ||
                    (maxAttackRanged == -1 || groupsMonitor.getOffenseRanged().size() + stateMonitor.futurePlayerRanged < maxAttackRanged))
                filteredActions.addAll(selectTrainingActions(trainingSide, produceRangedActions, maxProduceActionsChosen,
                        scanWidth, scanDepth, maxOccupiedCells));
        }

        if (!produceHeavyActions.isEmpty()) {
            if ((maxDefenseHeavies == -1 || groupsMonitor.getDefenseHeavies().size() + stateMonitor.futurePlayerHeavies < maxDefenseHeavies) ||
                    (maxAttackHeavies == -1 || groupsMonitor.getOffenseHeavies().size() + stateMonitor.futurePlayerHeavies < maxAttackHeavies))
                filteredActions.addAll(selectTrainingActions(trainingSide, produceHeavyActions, maxProduceActionsChosen,
                        scanWidth, scanDepth, maxOccupiedCells));
        }

        if (shuffleActions) Collections.shuffle(filteredActions);

        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Filter unit-actions of a Base unit.
     * If training a worker is possible, then check the harvester/defense/attack limits of workers, and produce if limits
     * are not reached yet.
     *
     * @param maxProduceActionsChosen The maximum number of produce actions to chose.
     * @return A filtered list of unit-actions.
     */
    public List<UnitAction> filterBaseActions(int maxHarvesters, int maxDefenseWorkers, int maxAttackWorkers,
                                              int maxProduceActionsChosen, int trainingSide,
                                              int scanWidth, int scanDepth, int maxOccupiedCells,
                                              boolean shuffleActions) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceWorkerActions.isEmpty() && // Production/Training is possible. Check limits and perform.
                ((maxHarvesters == -1 || groupsMonitor.getHarvestUnits().size() < maxHarvesters) ||
                        (maxDefenseWorkers == -1 || groupsMonitor.getDefenseWorkers().size() < maxDefenseWorkers) ||
                        (maxAttackWorkers == -1 || groupsMonitor.getOffenseWorkers().size() < maxAttackWorkers)))

            filteredActions.addAll(selectTrainingActions(trainingSide, produceWorkerActions, maxProduceActionsChosen,
                    scanWidth, scanDepth, maxOccupiedCells));

        if (shuffleActions) Collections.shuffle(filteredActions);

        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Selects the production unit-actions, depending on the training location chosen.
     *
     * @param unitActions The production unit-actions possible.
     * @param maxChoices The maximum number of choices.
     * @return A filtered unit-actions list.
     */
    private List<UnitAction> selectTrainingActions(int trainingSide, List<UnitAction> unitActions, int maxChoices,
                                                   int scanWidth, int scanDepth, int maxOccupiedCells) {
        switch (trainingSide) {
            case TRAIN_AT_RANDOM_SIDE:
                return choseRandomActionsFrom(unitActions, maxChoices);
            case TRAIN_AT_ISOLATED_SIDE:
                return choseIsolatedTrainingPosition(unitActions, maxChoices, scanWidth, scanDepth, maxOccupiedCells);
            default:
                return unitActions;
        }
    }

    /**
     * Selects actions from a list of possible unit training actions. The selection is done by comparing the number
     * of occupied cells in the direction chosen by a possible action, to a given maximum number of occupied cells. The
     * action with the lower occupied cells count in its direction is preserved. The occupied cells are counted by
     * scanning the direction of the action according to a width and depth.
     *
     * @param unitActions The possible training unit actions.
     * @param maxChoices The maximum number of actions to return.
     * @param scanWidth The width of the scanned zone, from both sides of the training direction.
     * @param scanDepth The depth of the scanned zone, directly across the training direction.
     * @param maxOccupiedCells The maximum number of occupied cells to consider.
     * @return A list of UnitActions.
     */
    private List<UnitAction> choseIsolatedTrainingPosition(List<UnitAction> unitActions, int maxChoices,
                                                           int scanWidth, int scanDepth, int maxOccupiedCells) {

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();

        for (UnitAction action : actions) {
            int occupiedCells = 0;
            if (chosenActions.size() < maxChoices) {
                switch (action.getDirection()) {
                    case UnitAction.DIRECTION_UP:
                        occupiedCells = scanOccupiedCellsAround(unit.getX(), unit.getY() - 1,
                                -scanWidth, scanWidth, -scanDepth, 0);
                        break;
                    case UnitAction.DIRECTION_RIGHT:
                        occupiedCells = scanOccupiedCellsAround(unit.getX() + 1, unit.getY(),
                                0, scanDepth, -scanWidth, scanWidth);
                        break;
                    case UnitAction.DIRECTION_DOWN:
                        occupiedCells = scanOccupiedCellsAround(unit.getX(), unit.getY() + 1,
                                -scanWidth, scanWidth, 0, scanDepth);
                        break;
                    case UnitAction.DIRECTION_LEFT:
                        occupiedCells = scanOccupiedCellsAround(unit.getX() - 1, unit.getY(),
                                -scanDepth, 0, -scanWidth, scanWidth);
                        break;
                }
            } else
                return chosenActions;

            if (occupiedCells <= maxOccupiedCells)
                chosenActions.add(action);
        }
        return chosenActions;
    }

}
