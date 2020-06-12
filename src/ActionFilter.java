import rts.UnitAction;
import rts.units.Unit;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;


/**
 * WIP
 */
public class ActionFilter {

    Unit unit;
    List<UnitAction> unitActions;

    Random random = new Random();

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
    // True if a produce action is possible.
    boolean canProduce = false;

    public ActionFilter(Unit unit, List<UnitAction> unitActions) {
        this.unitActions = unitActions;
        this.unit = unit;
        decomposeActions();
    }

    private void decomposeActions() {
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
                    canProduce = true;
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

    /*public List<UnitAction> getHarvesterActions(int maxProduceActionsChosen,
                                                int maxBarracks, int maxBases, int nbBarracks, int nbBases, int buildingMode) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Harvesting is possible (Return/Harvest)
        if (!harvestActions.isEmpty())
            filteredActions.addAll(harvestActions);
        else {
            // In between the harvesting actions, check if building is possible under the limits.
            if (!produceBarracksActions.isEmpty() && (maxBarracks == -1 || nbBarracks < maxBarracks))
                switch (buildingMode) {
                    case AdaptiveActionGenerator.BUILD_TRAIN_RANDOM_LOCATION:
                        filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));
                    case AdaptiveActionGenerator.BUILD_AWAY_FROM_BASE:
                        filteredActions.addAll(
                                choseBuildingPositionsNonAdjacentToBase(unit, produceBarracksActions, maxProduceActionsChosen));
                }
            else if (!produceBaseActions.isEmpty() && (maxBases == -1 || nbBases < maxBases))
                filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));

            // Move towards the closest base or the closest resource deposit
            if (!moveActions.isEmpty())
                filteredActions.add(getHarvestActions(unit, moveActions));
        }
        return filteredActions;
    }

    *//**
     * Selects a number of random actions from a list of given unit actions.
     * @param unitActions
     * @param maxChoices
     * @return
     *//*
    private List<UnitAction> choseRandomActionsFrom(List<UnitAction> unitActions, int maxChoices) {

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

    *//**
     * Chose a building position as far as possible from base.
     * @param unit
     * @param unitActions
     * @param maxChoices
     * @return
     *//*
    private List<UnitAction> choseBuildingPositionsNonAdjacentToBase(Unit unit, List<UnitAction> unitActions, int maxChoices) {

        if (bases.isEmpty())
            return choseRandomActionsFrom(unitActions, maxChoices);

        if (maxChoices == -1 || maxChoices >= unitActions.size())
            return unitActions;

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();
        Unit base = bases.get(0);
        int positionX = unit.getX(), positionY = unit.getY();

        for (int counter = 0; counter < maxChoices; counter++) {
            UnitAction bestAction = null;
            int maxDistanceToBase = 0;

            for (UnitAction action : actions) {
                switch (action.getDirection()) {
                    case UnitAction.DIRECTION_UP:
                        positionY--;
                        break;
                    case UnitAction.DIRECTION_RIGHT:
                        positionX++;
                        break;
                    case UnitAction.DIRECTION_DOWN:
                        positionY++;
                        break;
                    case UnitAction.DIRECTION_LEFT:
                        positionX--;
                        break;
                }
                int distance = Math.abs(positionX - base.getX()) + Math.abs(positionY - base.getY());
                if (maxDistanceToBase == 0 || distance > maxDistanceToBase) {
                    maxDistanceToBase = distance;
                    bestAction = action;
                }
            }

            chosenActions.add(bestAction);
            actions.remove(bestAction);
        }

        return chosenActions;
    }

    *//**
     * Returns the next action to consider for a harvesting unit. Either move towards the closest resource deposit,
     * or move towards the closest base.
     * TODO : join attack units if harvesting is not possible.
     * @param unit The worker unit in question.
     * @param possibleMoveActions The fallback move actions, in case path finding fails.
     * @return A TYPE_MOVE unit action.
     *//*
    private UnitAction getHarvestActions(Unit unit, List<UnitAction> possibleMoveActions) {

        if (unit.getResources() > 0) {
            Unit base = getClosestBaseTo(unit);
            if (base != null)
                return getMoveActionToTargetAdjacent(unit, base, possibleMoveActions);
        } else {
            Unit resource = getClosestResourceTo(unit);
            if (resource != null)
                return getMoveActionToTargetAdjacent(unit, resource, possibleMoveActions);
        }

        return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }

    *//**
     * Returns the closest friendly base to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest base.
     *//*
    private Unit getClosestBaseTo(Unit unit) {
        Unit closestBase = null;
        int closestDistance = 0;
        for (Unit possibleBase : physicalGameState.getUnits()) {
            if (possibleBase.getPlayer() == playerID && possibleBase.getType().isStockpile) {
                int distance = Math.abs(possibleBase.getX() - unit.getX()) + Math.abs(possibleBase.getY() - unit.getX());
                if (closestBase == null || distance < closestDistance) {
                    closestDistance = distance;
                    closestBase = possibleBase;
                }
            }
        }
        return closestBase;
    }

    *//**
     * Returns the closest resource deposit to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest resource deposit.
     *//*
    private Unit getClosestResourceTo(Unit unit) {
        Unit closestResource = null;
        int closestDistance = 0;
        for (Unit possibleResource : physicalGameState.getUnits()) {
            if (possibleResource.getType().isResource) {
                int distance = Math.abs(possibleResource.getX() - unit.getX()) + Math.abs(possibleResource.getY() - unit.getX());
                if (closestResource == null || distance < closestDistance) {
                    closestDistance = distance;
                    closestResource = possibleResource;
                }
            }
        }
        return closestResource;
    }

    *//**
     * Returns a UnitAction.TYPE_MOVE action leading to an adjacent position to a target unit, meant for use by workers
     * to harvest and return resources. A fallback list of possible moves is given in case path finding fails to find
     * a move action.
     *
     * @param unit The unit in question.
     * @param target The target in question.
     * @param possibleMoveActions Fallback move actions.
     * @return A unit action of type : TYPE_MOVE
     *//*
    private UnitAction getMoveActionToTargetAdjacent(Unit unit, Unit target, List<UnitAction> possibleMoveActions) {
        int targetPosition = target.getX() + target.getY() * physicalGameState.getWidth();
        UnitAction moveTowardsTarget = pathFinder.findPathToAdjacentPosition(
                unit, targetPosition, gameState, resourceUsage);
        if (moveTowardsTarget != null)
            return moveTowardsTarget;
        else
            return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }*/


}

