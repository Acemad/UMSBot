import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.Pair;

import java.util.*;

/**
 * This class implements an adaptive move generation approach that relies on asymmetric abstraction to control
 * different sets of units using different sets of actions. Inspired by CmabAsymmetricGenerator by @rubens
 * @author Acemad
 */
public class AdaptiveActionGenerator {

    private Random random = new Random();
    // The current count of each own unit type in this game state.
    public int
        workerCount = 0,
        barracksCount = 0,
        baseCount = 0,
        lightCount = 0,
        rangedCount = 0,
        heavyCount = 0;

    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    public static final int
        workerLimit = 6,
        harvestersLimit = 2,
        barracksLimit = 1,//2,
        baseLimit = 1,
        lightLimit = 0,//-1,
        rangedLimit = -1,
        heavyLimit = 0;

    public static final float epsilonMovement = 1f; // Exploration parameter for movements.

    private GameState gameState; // The current game state.
    private PhysicalGameState physicalGameState;
    private ResourceUsage resourceUsage;
    private int playerID; // ID of the searching player.
    private List<Pair<Unit, List<UnitAction>>> choices; // The list of action choices, for each unit.
    private long size = 1;

    List<Unit> frontLineUnits = new ArrayList<>();
    List<Unit> backUnits = new ArrayList<>();

    List<Unit> workerUnits = new LinkedList<>();
    List<Unit> harvestingWorkers = new LinkedList<>();
    List<Unit> assaultUnits = new LinkedList<>();
    List<Unit> lightUnits = new LinkedList<>();
    List<Unit> rangedUnits = new LinkedList<>();
    List<Unit> heavyUnits = new LinkedList<>();
    List<Unit> opponentUnits = new LinkedList<>();
    List<Unit> bases = new LinkedList<>();
    List<Unit> barracks = new LinkedList<>();

    PathFinding pathFinder = new AStarPathFinding();

    /**
     *
     * @param gameState
     * @param playerID
     * @throws Exception
     */
    public AdaptiveActionGenerator(GameState gameState, int playerID) throws Exception {

        this.gameState = gameState;
        physicalGameState = gameState.getPhysicalGameState();
        choices = new ArrayList<>();
        this.playerID = playerID;

        resourceUsage = gameState.getResourceUsage();

        // Enumerate all the units on the map.
        countUnits();

        // Select front line units and back units.
        selectFrontLineUnitsByOpponentRange(1, 3);
        selectBackUnits();

        // Assign unit actions to each unit group.
        generateLowLevelActionsFor(frontLineUnits, 3, true);
        generateLowLevelActionsFor(backUnits, 10, true);

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
     * Enumerate units count and put each unit on a respective container.
     */
    private void countUnits() {
        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == playerID) {
                switch (unit.getType().name) {
                    case "Base":
                        bases.add(unit);
                        baseCount++;
                        break;
                    case "Worker":
                        workerUnits.add(unit);
                        workerCount++;
                        // Designate harvesting units, if there is a base.
                        if (baseCount > 0 && (harvestersLimit == -1 || workerCount <= harvestersLimit))
                            harvestingWorkers.add(unit);
                        else
                            assaultUnits.add(unit);
                        // Count the Barracks being built.
                        UnitAction action = gameState.getUnitAction(unit);
                        if (action != null &&
                                action.getType() == UnitAction.TYPE_PRODUCE &&
                                action.getUnitType().name.equals("Barracks"))
                            barracksCount++;
                        break;
                    case "Barracks":
                        barracks.add(unit);
                        barracksCount++;
                        break;
                    case "Light":
                        assaultUnits.add(unit);
                        lightUnits.add(unit);
                        lightCount++;
                        break;
                    case "Ranged":
                        assaultUnits.add(unit);
                        rangedUnits.add(unit);
                        rangedCount++;
                        break;
                    case "Heavy":
                        assaultUnits.add(unit);
                        heavyUnits.add(unit);
                        heavyCount++;
                        break;
                    default:
                        break;
                }
            } else // Enumerate opponent units.
                if (unit.getPlayer() == 1 - playerID)
                    opponentUnits.add(unit);
        }
    }

    /**
     * Selects own units that have an opponent unit within their attack range.
     * @param tacticalDistance
     * @param unitLimit
     */
    private void selectFrontLineUnitsByOwnRange(int tacticalDistance, int unitLimit) {

        frontLineUnits.clear();

        for (Unit unit : physicalGameState.getUnits()) {

            if (unit.getPlayer() == playerID && unit.getType().canMove) {

                for (Unit possibleUnit : physicalGameState.getUnits()) {
                    if (possibleUnit.getPlayer() == 1 - playerID &&
                            possibleUnit.getType().canMove &&
                            !frontLineUnits.contains(unit) &&
                            (Math.abs(possibleUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                                    Math.abs(possibleUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance))

                        frontLineUnits.add(unit);

                    if (unitLimit > 0 && frontLineUnits.size() >= unitLimit)
                        break;
                }
            }

            if (unitLimit > 0 && frontLineUnits.size() >= unitLimit)
                break;
        }
    }

    /**
     * Selects own units that are in the attack range of an opponent unit.
     * @param tacticalDistance
     * @param unitLimit
     */
    private void selectFrontLineUnitsByOpponentRange(int tacticalDistance, int unitLimit) {

        frontLineUnits.clear();

        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == 1 - playerID && unit.getType().canMove) {

                for (Unit possibleUnit : physicalGameState.getUnits()) {
                    if (possibleUnit.getPlayer() == playerID &&
                        possibleUnit.getType().canMove &&
                        !frontLineUnits.contains(possibleUnit) &&
                        (Math.abs(possibleUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                         Math.abs(possibleUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance))

                        frontLineUnits.add(possibleUnit);

                    if (unitLimit > 0 && frontLineUnits.size() >= unitLimit)
                        break;
                }
            }

            if (unitLimit > 0 && frontLineUnits.size() >= unitLimit)
                break;
        }
    }

    /**
     * The remaining unselected units, that are not in the front line units list, are all considered back units.
     */
    private void selectBackUnits() {

        backUnits.clear();

        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == playerID && !frontLineUnits.contains(unit))
                backUnits.add(unit);
        }
    }

    /**
     * Generate low-level unit actions for a given group of units. The wait action generated is assigned a predefined
     * duration. Unit-actions are filtered heuristically before returning.
     *
     * @param unitGroup The considered group of units.
     * @param waitDuration The duration of the wait action.
     * @param heuristicFiltering If true, heuristic filtering is applied to each unit and its unit actions.
     */
    private void generateLowLevelActionsFor(List<Unit> unitGroup, int waitDuration, boolean heuristicFiltering) {
        for (Unit unit : unitGroup) {
            if (gameState.getUnitActions().get(unit) == null) {
                List<UnitAction> unitActions = unit.getUnitActions(gameState, waitDuration);
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
     * This method performs what we call heuristic filtering or pruning on the set of possible unit-actions of a unit.
     * Depending on the unit type it will discard a number of actions judged less useful depending on the context.
     * It also reduces the number of produce actions to a single direction, instead of four.
     *
     * @param unit The unit in question.
     * @param unitActions All the possible unit-actions.
     * @return A filtered list of unit-actions.
     */
    private List<UnitAction> heuristicFiltering(Unit unit, List<UnitAction> unitActions) {

        // The filtered list of unit-actions to return
        List<UnitAction> filteredActions = new LinkedList<>();

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

        /*
        //***
        if (harvestingWorkers.contains(unit)) {
//           if (!harvestActions.isEmpty())
               filteredActions.addAll(harvestActions);

           if (!moveActions.isEmpty())
                filteredActions.add(getHarvestActions(unit, moveActions));

           if (!produceBarracksActions.isEmpty() &&
                   (barracksLimit == -1 || barracksCount < barracksLimit))
               filteredActions.add(choseBuildingPositionNonAdjacentToBase(unit, produceBarracksActions));
           else if (!produceBaseActions.isEmpty() &&
                   (baseLimit == -1 || baseCount < baseLimit))
               filteredActions.add(produceBaseActions.remove(random.nextInt(produceBaseActions.size())));



           filteredActions.add(waitAction);
           return filteredActions;
        }

        if (assaultUnits.contains(unit)) {
            if (!attackActions.isEmpty()) {
                filteredActions.addAll(attackActions);
                filteredActions.addAll(moveActions);
            } else if (!moveActions.isEmpty()) {
                if (random.nextFloat() >= epsilonMovement)
                    filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit,2));
                else
                    filteredActions.addAll(moveActions);
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        if (barracks.contains(unit)) {
            // Production is possible, either train a Light, Ranged, or Heavy unit, within the limits.
            if (canProduce) {
                if (!produceLightActions.isEmpty() && (lightLimit == -1 || lightCount < lightLimit))
                    filteredActions.add(produceLightActions.remove(random.nextInt(produceLightActions.size())));
                if (!produceRangedActions.isEmpty() && (rangedLimit == -1 || rangedCount < rangedLimit))
                    filteredActions.add(produceRangedActions.remove(random.nextInt(produceRangedActions.size())));
                if (!produceHeavyActions.isEmpty() && (heavyLimit == -1 || heavyCount < heavyLimit))
                    filteredActions.add(produceHeavyActions.remove(random.nextInt(produceHeavyActions.size())));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        if (bases.contains(unit)) {
            // Production is possible, train workers within the limits.
            if (canProduce) {
                if (!produceWorkerActions.isEmpty() && (workerLimit == -1 || workerCount < workerLimit))
                    filteredActions.add(produceWorkerActions.remove(random.nextInt(produceWorkerActions.size())));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        return unitActions;
        //****
        */


        // Heuristic move pruning or filtering depending on "common sense" heuristics.
        switch (unit.getType().name) {
            case "Worker" :
                // Attacking is possible, ignore all other actions, and provide a retreat option.

                // ****************
                if (assaultUnits.contains(unit)) {
                    filteredActions.addAll(attackActions);
                    if (!moveActions.isEmpty())
//                        filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, 2));
                    filteredActions.addAll(restrictMovementAroundBase(unit, moveActions, 6));
                // *****************

                /*if (!attackActions.isEmpty()) {
                    filteredActions.addAll(attackActions);
                    if (!moveActions.isEmpty())
                        filteredActions.add(moveActions.remove(random.nextInt(moveActions.size())));*/

                } else if (!harvestActions.isEmpty()) // Harvesting is possible (Return/Harvest), ignore all other actions.
                    filteredActions.addAll(harvestActions);
                else {
                    // Building Barracks is possible, within the limits, ignore all other actions.
                    if (!produceBarracksActions.isEmpty() && (barracksLimit == -1 || barracksCount < barracksLimit))
                        filteredActions.add(choseBuildingPositionNonAdjacentToBase(unit, produceBarracksActions));
                    // Building a Base is possible, within the limits, ignore all other actions.
                    else if (!produceBaseActions.isEmpty() && (baseLimit == -1 || baseCount < baseLimit))
                        filteredActions.add(produceBaseActions.remove(random.nextInt(produceBaseActions.size())));

                    // Movement is possible
                    if (!moveActions.isEmpty())
                        // The worker belongs to the harvesting group, move towards a base or a resource.
//                        if (harvestingWorkers.contains(unit))
                            filteredActions.add(getHarvestActions(unit, moveActions));

                        /*
                        else // The worker is free, move towards the closest enemy unit.
                            if (random.nextFloat() >= epsilonMovement) // Exploit
                                filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, 2));
                            else // Explore
//                                filteredActions.add(moveActions.remove(random.nextInt(moveActions.size())));
                                  filteredActions.addAll(moveActions);
//                                  filteredActions.addAll(restrictMovementAroundBase(unit, moveActions, (physicalGameState.getWidth()/2) - 2));*/
                }

//                if (!moveActions.isEmpty())
//                    if (harvestingWorkers.contains(unit))
//                    filteredActions.add(getHarvestActions(unit, moveActions));

                break;
            case "Light":
            case "Ranged":
            case "Heavy":
                // Attacking is possible, ignore all other actions, and provide a retreat option.
                if (!attackActions.isEmpty()) {
                    filteredActions.addAll(attackActions);
                    if (!moveActions.isEmpty())
                        //filteredActions.add(moveActions.remove(random.nextInt(moveActions.size())));
                        filteredActions.addAll(moveActions);
                } else if (!moveActions.isEmpty())
                    // Move towards the closest enemy unit.
                    if (random.nextFloat() >= epsilonMovement)
                        filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, 2));
                    else
//                        if (assaultUnits.size() + workerUnits.size() - harvestersLimit < 10)
                        filteredActions.addAll(restrictMovementAroundBase(unit, moveActions, (physicalGameState.getWidth()/2) - 2));
//                        else filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, 2));
//                        filteredActions.add(moveActions.remove(random.nextInt(moveActions.size())));
                break;
            case "Barracks":
                // Production is possible, either train a Light, Ranged, or Heavy unit, within the limits.
                if (canProduce) {
                    if (!produceLightActions.isEmpty() && (lightLimit == -1 || lightCount < lightLimit))
                        filteredActions.add(produceLightActions.remove(random.nextInt(produceLightActions.size())));
                    if (!produceRangedActions.isEmpty() && (rangedLimit == -1 || rangedCount < rangedLimit))
                        filteredActions.add(produceRangedActions.remove(random.nextInt(produceRangedActions.size())));
                    if (!produceHeavyActions.isEmpty() && (heavyLimit == -1 || heavyCount < heavyLimit))
                        filteredActions.add(produceHeavyActions.remove(random.nextInt(produceHeavyActions.size())));
                }
                break;
            case "Base":
                // Production is possible, train workers within the limits.
                if (canProduce) {
                    if (!produceWorkerActions.isEmpty() && (workerLimit == -1 || workerCount < workerLimit))
                        filteredActions.add(produceWorkerActions.remove(random.nextInt(produceWorkerActions.size())));
                }
                break;
        }

        // A wait action is always added.
        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Returns a list of opponent units around a given unit, in the immediate square range.
     * @param unit The unit considered.
     * @param squareRange The maximum distance searched around the unit.
     * @return
     */
    private List<Unit> getOpponentUnitsAround(Unit unit, int squareRange) {
        List<Unit> closeUnits = new LinkedList<>();

        for (Unit possibleUnit : physicalGameState.getUnits()) {
            if (possibleUnit.getPlayer() == 1 - playerID &&
                (Math.abs(possibleUnit.getX() - unit.getX()) <= squareRange &&
                 Math.abs(possibleUnit.getY() - unit.getY()) <= squareRange)) {
                closeUnits.add(possibleUnit);
            }
        }
        return closeUnits;
    }

    /**
     * Returns a list of UnitAction.TYPE_MOVE unit actions, each targeting an opponent unit. The opponent units are chosen
     * based on their proximity to the current unit.
     *
     * @param unit The units in question.
     * @param maxClosestOpponentUnits The number of close opponent units to consider.
     * @return UnitAction.TYPE_MOVE list.
     */
    private List<UnitAction> getMoveActionsToClosestOpponentUnits(Unit unit, int maxClosestOpponentUnits) {

        List<UnitAction> targetedMovements = new LinkedList<>();
        List<Unit> closestOpponentUnits = getClosestOpponentUnitsTo(unit, maxClosestOpponentUnits);

        for (Unit opponentUnit : closestOpponentUnits) {
            int targetPosition = opponentUnit.getX() + opponentUnit.getY() * physicalGameState.getWidth();
            UnitAction targetedMove = pathFinder.findPathToPositionInRange(
                    unit, targetPosition, unit.getAttackRange(), gameState, resourceUsage);
            if (targetedMove != null)
                targetedMovements.add(targetedMove);
        }

        return targetedMovements;
    }

    /**
     * Finds and returns the closest n opponent units to the given unit.
     * @param unit The unit in question.
     * @param maxClosestOpponentUnits The number of close opponent units to consider.
     * @return a Unit list.
     */
    private List<Unit> getClosestOpponentUnitsTo(Unit unit, int maxClosestOpponentUnits) {

        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> closestOpponentUnits = new LinkedList<>();

        int counter = 0;

        if (opponentUnits.size() > maxClosestOpponentUnits) {
            while (counter < maxClosestOpponentUnits) {
                int closestDistance = 0;
                Unit closestEnemy = null;
                for (Unit opponentUnit : opponentUnits) {
                    int distance = Math.abs(unit.getX() - opponentUnit.getX()) + Math.abs(unit.getY() - opponentUnit.getY());
                    if (closestEnemy == null || distance < closestDistance) {
                        closestDistance = distance;
                        closestEnemy = opponentUnit;
                    }
                }
                closestOpponentUnits.add(closestEnemy);
                opponentUnits.remove(closestEnemy);
                counter++;
            }
        } else
            return opponentUnits;

        return closestOpponentUnits;
    }

    /**
     * Returns the next action to consider for a harvesting unit. Either move towards the closest resource deposit,
     * or move towards the closest base.
     *
     * @param unit The worker unit in question.
     * @param possibleMoveActions The fallback move actions, in case path finding fails.
     * @return A TYPE_MOVE unit action.
     */
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

    /**
     * Returns the closest friendly base to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest base.
     */
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

    /**
     * Returns the closest resource deposit to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest resource deposit.
     */
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
    private UnitAction getMoveActionToTargetAdjacent(Unit unit, Unit target, List<UnitAction> possibleMoveActions) {
        int targetPosition = target.getX() + target.getY() * physicalGameState.getWidth();
        UnitAction moveTowardsTarget = pathFinder.findPathToAdjacentPosition(
                unit, targetPosition, gameState, resourceUsage);
        if (moveTowardsTarget != null)
            return moveTowardsTarget;
        else
            return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }

    /**
     * Restricts the movements of the given unit to a predefined radius from own base.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move actions.
     * @param radius The distance limit from the base.
     * @return A list of move actions, not moving beyond the perimeter around the base defined by radius.
     */
    private List<UnitAction> restrictMovementAroundBase(Unit unit, List<UnitAction> moveActions, int radius) {

        if (bases.isEmpty())
            return moveActions;

        Unit base = bases.get(0);

        // Unit already out.
        if (unit.getX() > base.getX() + radius || unit.getX() < base.getX() - radius ||
            unit.getY() > base.getY() + radius || unit.getY() < base.getY() - radius)
            return moveActions;

        List<UnitAction> restrictedMoveActions = new LinkedList<>();

        for (UnitAction moveAction : moveActions) {
            switch (moveAction.getDirection()) {
                case UnitAction.DIRECTION_UP:
                    if (unit.getY() - 1 <= base.getY() - radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_RIGHT:
                    if (unit.getX() + 1 <= base.getX() + radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_DOWN:
                    if (unit.getY() + 1 <= base.getY() + radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_LEFT:
                    if (unit.getX() - 1 <= base.getX() - radius)
                        restrictedMoveActions.add(moveAction);
                    break;
            }
        }
        return restrictedMoveActions;
    }

    private UnitAction choseBuildingPositionNonAdjacentToBase(Unit unit, List<UnitAction> unitActions) {

        if (bases.isEmpty())
            return unitActions.remove(random.nextInt(unitActions.size()));

        UnitAction bestAction = null;
        Unit base = bases.get(0);

        int positionX = unit.getX(), positionY = unit.getY();

        int maxDistanceToBase = 0;

        for (UnitAction action : unitActions) {
            switch (action.getDirection()) {
                case UnitAction.DIRECTION_UP:
                    positionY--; break;
                case UnitAction.DIRECTION_RIGHT:
                    positionX++; break;
                case UnitAction.DIRECTION_DOWN:
                    positionY++; break;
                case UnitAction.DIRECTION_LEFT:
                    positionX--; break;
            }
            int distance = Math.abs(positionX - base.getX()) + Math.abs(positionY - base.getY());
            if (maxDistanceToBase == 0 || distance > maxDistanceToBase) {
                maxDistanceToBase = distance;
                bestAction = action;
            }
        }

        return bestAction;
    }

    public List<Pair<Unit, List<UnitAction>>> getChoices() {
        return choices;
    }

    public List<Unit> getBackUnits() {
        return backUnits;
    }

    public List<Unit> getFrontLineUnits() {
        return frontLineUnits;
    }

    public long getSize() {
        return size;
    }
}
