package refactor;

import ai.abstraction.pathfinding.PathFinding;
import rts.UnitAction;
import rts.units.Unit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HarvestManager extends HeuristicsManager {

    // Building / Training Modes **************************************************************************************
    public static final int BUILD_AT_RANDOM_LOCATION = 0; // Build at a random location (adjacent to unit)
    public static final int BUILD_AT_ISOLATED_LOCATION = 1; // Build barracks away from base (not adjacent)

    PathFinding pathFinder;

    public HarvestManager(StateMonitor stateMonitor, PathFinding pathFinder, Unit unit, List<UnitAction> unitActions) {
        super(stateMonitor, unit, unitActions);
        this.pathFinder = pathFinder;
    }

    /**
     * Filter unit-actions of a harvesting worker unit.
     * If the worker can execute a Return or Harvest action, then ignore the rest of action. If the worker can build a
     * Barracks or a Base within the limits, then do so and if it can move, either move towards a resource deposit, or
     * to a base.
     *
     * @param maxBarracks
     * @param maxBases
     * @param maxProduceActionsChosen The maximum number of production actions to chose.
     * @param epsilonHarvestMovement
     * @param buildingLocation
     * @return Filtered unit-actions list.
     */
    public List<UnitAction> filterActions(int maxBases, int maxBarracks, int buildingLocation, int maxProduceActionsChosen,
                                          int scanRadius, int maxOccupiedCells,
                                          float epsilonHarvestMovement, boolean shuffleActions) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Harvesting (Return/Harvest) is possible
        if (!harvestActions.isEmpty())
            filteredActions.addAll(harvestActions);
        else {
            // In between the harvesting actions, check if building is possible under the limits.
            if (!produceBarracksActions.isEmpty() &&
                    (maxBarracks == -1 || stateMonitor.getPlayerBarracks().size() + stateMonitor.futurePlayerBarracks < maxBarracks))
                switch (buildingLocation) {
                    case BUILD_AT_RANDOM_LOCATION:
                        filteredActions.addAll(
                                choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));
                        break;
                    case BUILD_AT_ISOLATED_LOCATION:
                        filteredActions.addAll(
                                choseIsolatedBuildingPosition(produceBarracksActions, maxProduceActionsChosen,
                                        scanRadius, maxOccupiedCells));
                        break;
                }
            else if (!produceBaseActions.isEmpty() && (maxBases == -1 || stateMonitor.getPlayerBases().size() < maxBases))
                filteredActions.addAll(choseRandomActionsFrom(produceBaseActions, maxProduceActionsChosen));

            // Add attack actions in case the harvester can attack.
            if (!attackActions.isEmpty())
                filteredActions.addAll(attackActions);
            // Move towards the closest base or the closest resource deposit
            else if (!moveActions.isEmpty())
                if (random.nextFloat() >= epsilonHarvestMovement)
                    filteredActions.add(getHarvestActions(moveActions));
                else
                    filteredActions.addAll(moveActions);
        }

        if (shuffleActions) Collections.shuffle(filteredActions);

        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Returns the next action to consider for a harvesting unit. Either move towards the closest resource deposit,
     * or move towards the closest base.
     * TODO : join attack units if harvesting is not possible.
     * @param possibleMoveActions The fallback move actions, in case path finding fails.
     * @return A TYPE_MOVE unit action.
     */
    private UnitAction getHarvestActions(List<UnitAction> possibleMoveActions) {

        if (unit.getResources() > 0) {
            Unit base = stateMonitor.getPlayerBaseClosestTo(unit);
            if (base != null)
                return getMoveActionToTargetAdjacent(unit, base, possibleMoveActions, pathFinder);
        } else {
            Unit resource = stateMonitor.getResourceDepositClosestTo(unit);
            if (resource != null)
                return getMoveActionToTargetAdjacent(unit, resource, possibleMoveActions, pathFinder);
        }

        return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }

    /**
     * Selects an action from the given build unit actions list. The action is chosen so that the building position
     * considered is surrounded my the lowest number of occupied cells. The area to scan around the building position is
     * determined by the scan radius parameter.
     *
     * @param unitActions The list of possible build unit actions.
     * @param maxChoices The maximum number of unit action to return.
     * @param scanRadius The radius of the area surrounding the chosen location subject to scanning.
     * @param maxOccupiedCells The maximum number of occupied cells surrounding the chosen location.
     * @return A UnitAction list.
     */
    private List<UnitAction> choseIsolatedBuildingPosition(List<UnitAction> unitActions, int maxChoices,
                                                           int scanRadius, int maxOccupiedCells) {

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();

        for (UnitAction action : actions) {
            int occupiedCells = 0;
            if (chosenActions.size() < maxChoices) {
                switch (action.getDirection()) {
                    case UnitAction.DIRECTION_UP:
                        occupiedCells = scanOccupiedCellsAround(unit.getX(), unit.getY() - 1,
                                -scanRadius, scanRadius, -scanRadius, scanRadius);
                        break;
                    case UnitAction.DIRECTION_RIGHT:
                        occupiedCells = scanOccupiedCellsAround(unit.getX() + 1, unit.getY(),
                                -scanRadius, scanRadius, -scanRadius, scanRadius);
                        break;
                    case UnitAction.DIRECTION_DOWN:
                        occupiedCells = scanOccupiedCellsAround(unit.getX(), unit.getY() + 1,
                                -scanRadius, scanRadius, -scanRadius, scanRadius);
                        break;
                    case UnitAction.DIRECTION_LEFT:
                        occupiedCells = scanOccupiedCellsAround(unit.getX() - 1, unit.getY(),
                                -scanRadius, scanRadius, -scanRadius, scanRadius);
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
