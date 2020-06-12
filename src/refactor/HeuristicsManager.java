package refactor;

import ai.abstraction.pathfinding.PathFinding;
import rts.UnitAction;
import rts.units.Unit;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class HeuristicsManager {

    Random random = new Random();
    StateMonitor stateMonitor;
    Unit unit;

    // unitActions decomposition into each respective action type.
    List<UnitAction> moveActions = new LinkedList<>(); // TYPE_MOVE
    List<UnitAction> harvestActions = new LinkedList<>(); // TYPE_HARVEST and TYPE_RETURN
    List<UnitAction> attackActions = new LinkedList<>(); // TYPE_ATTACK_LOCATION

    // TYPE_PRODUCE, each correspond to the unit type to produce.
    List<UnitAction> produceBarracksActions = new LinkedList<>();
    List<UnitAction> produceBaseActions = new LinkedList<>();
    List<UnitAction> produceWorkerActions = new LinkedList<>();
    List<UnitAction> produceLightActions = new LinkedList<>();
    List<UnitAction> produceRangedActions = new LinkedList<>();
    List<UnitAction> produceHeavyActions = new LinkedList<>();

    // TYPE_NONE
    UnitAction waitAction = null;

    public HeuristicsManager(StateMonitor stateMonitor, Unit unit, List<UnitAction> unitActions) {

        this.unit = unit;
        this.stateMonitor = stateMonitor;
        // The decomposition of unitActions list.
        for (UnitAction unitAction : unitActions) {
            switch (unitAction.getType()) {
                case UnitAction.TYPE_HARVEST:
                case UnitAction.TYPE_RETURN:
                    harvestActions.add(unitAction);
                    break;
                case UnitAction.TYPE_ATTACK_LOCATION:
                    attackActions.add(unitAction);
                    break;
                case UnitAction.TYPE_PRODUCE:
                    switch (unitAction.getUnitType().name) {
                        case "Barracks":
                            produceBarracksActions.add(unitAction); break;
                        case "Base":
                            produceBaseActions.add(unitAction); break;
                        case "Worker":
                            produceWorkerActions.add(unitAction); break;
                        case "Light":
                            produceLightActions.add(unitAction); break;
                        case "Ranged":
                            produceRangedActions.add(unitAction); break;
                        case "Heavy":
                            produceHeavyActions.add(unitAction); break;
                        default:
                            break;
                    }
                    break;
                case UnitAction.TYPE_MOVE:
                    moveActions.add(unitAction);
                    break;
                case UnitAction.TYPE_NONE:
                    waitAction = unitAction;
                    break;
            }
        }
    }

    /**
     * Selects a number of random actions from a list of given unit actions.
     * @param unitActions The list of unit-actions to chose from.
     * @param maxChoices The maximum number of actions to chose.
     * @return A unit-actions list.
     */
    List<UnitAction> choseRandomActionsFrom(List<UnitAction> unitActions, int maxChoices) {

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();

        if (maxChoices == -1 || maxChoices >= unitActions.size())
            return unitActions;
        else {
            for (int counter = 0; counter < maxChoices; counter++)
                chosenActions.add(actions.remove(random.nextInt(actions.size())));

            return chosenActions;
        }
    }

    /**
     * Returns move unit-actions targeting a set of units, using a path-finding algorithm.
     *
     * @param unit The unit in question.
     * @param targetUnits The units to target. (move towards)
     * @return A list of move unit-actions list.
     */
    List<UnitAction> getMoveActionsToTargetsInRange(Unit unit, List<Unit> targetUnits, PathFinding pathFinder) {
        List<UnitAction> directedMoveActions = new LinkedList<>();
        for (Unit targetUnit : targetUnits) {
            int targetPosition = targetUnit.getX() + targetUnit.getY() * stateMonitor.getMapWidth();
            UnitAction directedMove = pathFinder.findPathToPositionInRange(
                    unit, targetPosition, unit.getAttackRange(), stateMonitor.getGameState(), stateMonitor.getResourceUsage());
            if (directedMove != null)
                directedMoveActions.add(directedMove);
        }
        return directedMoveActions;
    }

    /**
     * Returns a UnitAction.TYPE_MOVE action leading to an adjacent position to a target unit, meant for use by workers
     * to harvest and return resources. A fallback list of possible moves is given in case path finding fails to find
     * a move action.
     *
     * @param unit The unit in question.
     * @param target The target in question.
     * @param possibleMoveActions Fallback move actions.
     * @return A unit action of type : TYPE_MOVE
     */
    UnitAction getMoveActionToTargetAdjacent(Unit unit, Unit target, List<UnitAction> possibleMoveActions, PathFinding pathFinder) {
        int targetPosition = target.getX() + target.getY() * stateMonitor.getMapWidth();
        UnitAction moveTowardsTarget = pathFinder.findPathToAdjacentPosition(
                unit, targetPosition, stateMonitor.getGameState(), stateMonitor.getResourceUsage());
        if (moveTowardsTarget != null)
            return moveTowardsTarget;
        else
            return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }

    /**
     * Counts the number of occupied cells surrounding a given position. The scanned area is determined by the given
     * relative intervals. Resources/Bases/Barracks/Walls are counted twice because of their immobility.
     *
     * @param positionX X coordinate of the given position.
     * @param positionY Y coordinate of the given position.
     * @param scanStartX Relative X position (increment) of the start of the scan interval.
     * @param scanEndX Relative X position (increment) of the end of the scan interval.
     * @param scanStartY Relative Y position (increment) of the start of the scan interval.
     * @param scanEndY Relative Y position (increment) of the end of the scan interval.
     * @return The number of cells occupied in the scanned region.
     */
    int scanOccupiedCellsAround(int positionX, int positionY, int scanStartX, int scanEndX, int scanStartY, int scanEndY) {

        int occupiedCellsCount = 0;
        boolean[][] allFreeCells = stateMonitor.getPhysicalGameState().getAllFree();

        for (int xIncrement = scanStartX; xIncrement <= scanEndX; xIncrement++)
            for (int yIncrement = scanStartY; yIncrement <= scanEndY; yIncrement++)
                // Skip scanning own position.
                if (!(xIncrement == 0 && yIncrement == 0)) {
                    int scanX = positionX + xIncrement, scanY = positionY + yIncrement;
                    if ((scanX >= 0 && scanX < stateMonitor.getMapWidth() &&
                            scanY >= 0 && scanY < stateMonitor.getMapHeight())
                            && !allFreeCells[scanX][scanY]) {

                        Unit unitInPosition = stateMonitor.getPhysicalGameState().getUnitAt(scanX,scanY);
                        if (unitInPosition != null && unitInPosition.getType().canMove)
                            occupiedCellsCount++;
                        else
                            occupiedCellsCount += 2; // Double the number for base, barracks, resources, or walls.
                    }
                }
        return occupiedCellsCount;
    }

//    public List<UnitAction> filterActions() {};

}
