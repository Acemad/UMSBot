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
 * This class implements an adaptive move generation approach inspired by the CmabAsymmetricGenerator by @rubens
 * Not final, WIP.
 * @author Acemad
 */
public class AdaptiveActionGenerator {

    // For random number generation.
    private Random random = new Random();

    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    public static final int
            maxBases = 1,
            maxBarracks = 1,//2,

            maxAttackWorkers = 4,
            maxAttackLights = 0,//-1,
            maxAttackRanged = -1,
            maxAttackHeavies = 0,

            maxHarvesters = 2,

            maxDefenseWorkers = 0,
            maxDefenseLights = 0,
            maxDefenseRanged = 1,
            maxDefenseHeavies = 0;

    // Exploration parameter for movements.
    public static final float epsilonMovement = 0.05f;

    // The current count of each own unit type in this game state.
    public int
            nbBases = 0,
            nbBarracks = 0,

            nbAttackWorkers = 0,
            nbAttackLights = 0,
            nbAttackRanged = 0,
            nbAttackHeavies = 0,

            nbHarvesters = 0,

            nbDefenseWorkers = 0,
            nbDefenseLights = 0,
            nbDefenseRanged = 0,
            nbDefenseHeavies = 0;

    // The current game state and resource usage.
    private GameState gameState;
    private PhysicalGameState physicalGameState;
    private ResourceUsage resourceUsage;
    private int playerID; // ID of the searching player.
    private List<Pair<Unit, List<UnitAction>>> choices; // The list of action choices, for each unit.
    private long size = 1; // Size of the possible choice combination.

    // The path finding algorithm used to direct movement.
    PathFinding pathFinder = new AStarPathFinding();

    // Units decomposition based on opponent proximity, used for adaptive wait durations.
    List<Unit> frontLineUnits = new ArrayList<>();
    List<Unit> backUnits = new ArrayList<>();

    // Decomposition of own units, based on UnitType
    List<Unit> bases = new LinkedList<>();
    List<Unit> barracks = new LinkedList<>();
    List<Unit> workerUnits = new LinkedList<>();
    List<Unit> lightUnits = new LinkedList<>();
    List<Unit> rangedUnits = new LinkedList<>();
    List<Unit> heavyUnits = new LinkedList<>();
    List<Unit> harvestingWorkers = new LinkedList<>();

    // Decomposition of own mobile units, based on tactical stance.
    List<Unit> attackingUnits = new LinkedList<>();
    List<Unit> defendingUnits = new LinkedList<>();

    // All the opponent's units.
    List<Unit> opponentUnits = new LinkedList<>();

    /**
     * The main constructor, populates the choices list with all the possible actions, relying on the different
     * decompositions and heuristics.
     *
     * @param gameState
     * @param playerID
     * @throws Exception
     */
    public AdaptiveActionGenerator(GameState gameState, int playerID) throws Exception {

        this.gameState = gameState;
        physicalGameState = gameState.getPhysicalGameState();
        resourceUsage = gameState.getResourceUsage();

        this.playerID = playerID;
        choices = new ArrayList<>();

        // Enumerates and decomposes all the units on the map.
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
     * Enumerate and decompose units by UnitType and tactical stance.
     * TODO : remove count variables and replace with a call to size.
     */
    private void countUnits() {

        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == playerID) {
                switch (unit.getType().name) {

                    case "Base":
                        bases.add(unit);
                        nbBases++;
                        break;

                    case "Barracks":
                        barracks.add(unit);
                        nbBarracks++;
                        break;

                    case "Worker":
                        workerUnits.add(unit);

                        // Account for the Barracks being built.
                        UnitAction action = gameState.getUnitAction(unit);
                        if (action != null &&
                                action.getType() == UnitAction.TYPE_PRODUCE &&
                                action.getUnitType().name.equals("Barracks"))
                            nbBarracks++;

                        // Designate harvesting units, if there is a base.
                        if (nbBases > 0 && (maxHarvesters == -1 || nbHarvesters < maxHarvesters)) {
                            harvestingWorkers.add(unit);
                            nbHarvesters++;
                        }
                        else if (maxDefenseWorkers == -1 || nbDefenseWorkers < maxDefenseWorkers) {
                            defendingUnits.add(unit);
                            nbDefenseWorkers++;
                        }
                        else if (maxAttackWorkers == -1 || nbAttackWorkers < maxAttackWorkers) {
                            attackingUnits.add(unit);
                            nbAttackWorkers++;
                        }

                        break;

                    case "Light":
                        lightUnits.add(unit);

                        if (maxDefenseLights == -1 || nbDefenseLights < maxDefenseLights) {
                            defendingUnits.add(unit);
                            nbDefenseLights++;
                        }
                        else if (maxAttackLights == -1 || nbAttackLights < maxAttackLights) {
                            attackingUnits.add(unit);
                            nbAttackLights++;
                        }

                        break;

                    case "Ranged":
                        rangedUnits.add(unit);

                        if (maxDefenseRanged == -1 || nbDefenseRanged < maxDefenseRanged) {
                            defendingUnits.add(unit);
                            nbDefenseRanged++;
                        }
                        else if (maxAttackRanged == -1 || nbAttackRanged < maxAttackRanged) {
                            attackingUnits.add(unit);
                            nbAttackRanged++;
                        }

                        break;

                    case "Heavy":
                        heavyUnits.add(unit);

                        if (maxDefenseHeavies == -1 || nbDefenseHeavies < maxDefenseHeavies) {
                            defendingUnits.add(unit);
                            nbDefenseHeavies++;
                        }
                        else if (maxAttackHeavies == -1 || nbAttackHeavies < maxAttackHeavies) {
                            attackingUnits.add(unit);
                            nbAttackHeavies++;
                        }

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

        // The unit is a harvesting worker.
        if (harvestingWorkers.contains(unit)) {

           filteredActions.addAll(
                   filterHarvesterActions(unit, harvestActions, moveActions, produceBarracksActions, produceBaseActions, 1));

           filteredActions.add(waitAction);
           return filteredActions;
        }

        // The unit is adopting a defensive stance.
        if (defendingUnits.contains(unit)) {

            filteredActions.addAll(filterDefenseActions(unit, moveActions, attackActions, 6, 3, 2));
            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is adopting an attacking stance.
        if (attackingUnits.contains(unit)) {

            filteredActions.addAll(filterAttackActions(unit, moveActions, attackActions, 2));

            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is a barracks.
        if (barracks.contains(unit)) {
            // Production is possible, either train a Light, Ranged, or Heavy unit, within the limits.
            if (canProduce) {
                filteredActions.addAll(filterBarracksActions(produceLightActions, produceRangedActions,
                        produceHeavyActions, 1));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is a base.
        if (bases.contains(unit)) {
            // Production is possible, train workers within the limits.
            if (canProduce) {
                filteredActions.addAll(filterBaseActions(produceWorkerActions, 1));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        return unitActions;
    }

    private List<UnitAction> filterHarvesterActions(Unit unit, List<UnitAction> harvestActions,List<UnitAction> moveActions,
                                                    List<UnitAction> produceBarracksActions,
                                                    List<UnitAction> produceBaseActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!harvestActions.isEmpty()) // Harvesting is possible (Return/Harvest)
            filteredActions.addAll(harvestActions);
        else { // In between the harvesting actions, check if building is possible under the limits.
            if (!produceBarracksActions.isEmpty() && (maxBarracks == -1 || nbBarracks < maxBarracks))
                filteredActions.addAll(
                        choseBuildingPositionsNonAdjacentToBase(unit, produceBarracksActions, maxProduceActionsChosen));
            else if (!produceBaseActions.isEmpty() && (maxBases == -1 || nbBases < maxBases))
                filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));
            // Move towards the closest base or the closest resource deposit
            if (!moveActions.isEmpty())
                filteredActions.add(getHarvestActions(unit, moveActions));
        }
        
        return filteredActions;
    }

    private List<UnitAction> filterDefenseActions(Unit unit, List<UnitAction> moveActions, List<UnitAction> attackActions,
                                                  int horizontalDistance, int verticalDistance, int maxClosestOpponentUnits) {

        List<UnitAction> filteredActions = new LinkedList<>();

        filteredActions.addAll(attackActions); // Attack actions are always added.
        if (!moveActions.isEmpty()) { // Movement is possible.
            if (random.nextFloat() >= epsilonMovement)
                filteredActions.addAll(
                        restrictMovementAroundBase(unit, // Restrict movement around the predefined perimeter around the base.
                                // Chase units inside the perimeter.
                                getMoveActionsToClosestOpponentUnits(unit, maxClosestOpponentUnits),
                                horizontalDistance, verticalDistance));
            else
                filteredActions.addAll(
                        restrictMovementAroundBase(unit, moveActions, horizontalDistance, verticalDistance));

        }

        return filteredActions;
    }

    private List<UnitAction> filterAttackActions(Unit unit, List<UnitAction> moveActions,
                                                 List<UnitAction> attackActions, int maxClosestOpponentUnits) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!attackActions.isEmpty()) { // Attacking is possible.
            filteredActions.addAll(attackActions);
            filteredActions.addAll(moveActions); // Add all possible move actions (escapes) // TODO : restrict escape moves
        } else if (!moveActions.isEmpty()) {
            if (random.nextFloat() >= epsilonMovement) // Exploit. Chase a number of close opponent units.
                filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit,maxClosestOpponentUnits));
            else // Explore. Return all movement directions.
                filteredActions.addAll(moveActions);
        }

        return filteredActions;
    }

    private List<UnitAction> filterBaseActions(List<UnitAction> produceWorkerActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceWorkerActions.isEmpty() && // Production/Training is possible. Check limits and perform.
                ((maxHarvesters == -1 || nbHarvesters < maxHarvesters) ||
                (maxDefenseWorkers == -1 || nbDefenseWorkers < maxDefenseWorkers) ||
                (maxAttackWorkers == -1 || nbAttackWorkers < maxAttackWorkers)))

            filteredActions.addAll(choseRandomActionsFrom(produceWorkerActions, maxProduceActionsChosen));

        return filteredActions;
    }

    private List<UnitAction> filterBarracksActions(List<UnitAction> produceLightActions, List<UnitAction> produceRangedActions,
                                                   List<UnitAction> produceHeavyActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Priority to defense units.
        if (!produceLightActions.isEmpty() &&
                (maxDefenseLights == -1 || nbDefenseLights < maxDefenseLights))
            filteredActions.addAll(choseRandomActionsFrom(produceLightActions, maxProduceActionsChosen));
        else if (!produceRangedActions.isEmpty() &&
                (maxDefenseRanged == -1 || nbDefenseRanged < maxDefenseRanged))
            filteredActions.addAll(choseRandomActionsFrom(produceRangedActions, maxProduceActionsChosen));
        else if (!produceHeavyActions.isEmpty() &&
                (maxDefenseHeavies == -1 || nbDefenseHeavies < maxDefenseHeavies))
            filteredActions.addAll(choseRandomActionsFrom(produceHeavyActions, maxProduceActionsChosen));

        else {
            // Train any unit.
            if (!produceLightActions.isEmpty() &&
                    (maxAttackLights == -1 || nbAttackLights < maxAttackLights))
                filteredActions.addAll(choseRandomActionsFrom(produceLightActions, maxProduceActionsChosen));

            if (!produceRangedActions.isEmpty() &&
                    (maxAttackRanged == -1 || nbAttackRanged < maxAttackRanged))
                filteredActions.addAll(choseRandomActionsFrom(produceRangedActions, maxProduceActionsChosen));

            if (!produceHeavyActions.isEmpty() &&
                    (maxAttackHeavies == -1 || nbAttackHeavies < maxAttackHeavies))
                filteredActions.addAll(choseRandomActionsFrom(produceHeavyActions, maxProduceActionsChosen));
        }

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
     * Restricts the movements of the given unit to a predefined vertical and horizontal distance away from own base.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move actions.
     * @param horizontalDistance The horizontal distance from base.
     * @param verticalDistance The vertical distance from base.
     * @return A list of move actions, without the moves going beyond the perimeter around the base defined by the
     * vertical and horizontal distances.
     */
    private List<UnitAction> restrictMovementAroundBase(Unit unit, List<UnitAction> moveActions,
                                                        int horizontalDistance, int verticalDistance) {

        if (bases.isEmpty())
            return moveActions;

        Unit base = bases.get(0);

        // Unit already out. // TODO : remove unit from defense group and add to attack group.
        if (unit.getX() > base.getX() + horizontalDistance || unit.getX() < base.getX() - horizontalDistance ||
            unit.getY() > base.getY() + verticalDistance || unit.getY() < base.getY() - verticalDistance)
            return moveActions;

        List<UnitAction> restrictedMoveActions = new LinkedList<>();

        for (UnitAction moveAction : moveActions) {
            switch (moveAction.getDirection()) {
                case UnitAction.DIRECTION_UP:
                    if (unit.getY() - 1 >= base.getY() - verticalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_RIGHT:
                    if (unit.getX() + 1 <= base.getX() + horizontalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_DOWN:
                    if (unit.getY() + 1 <= base.getY() + verticalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_LEFT:
                    if (unit.getX() - 1 >= base.getX() - horizontalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
            }
        }
        return restrictedMoveActions;
    }

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
