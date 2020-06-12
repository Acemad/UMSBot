package refactor;

import rts.*;
import rts.units.Unit;

import java.util.*;

/**
 * A wrapper around GameState that provides direct access to unit groups.
 * @author Acemad
 */
public class StateMonitor {

    int playerID;
    GameState gameState;

    // Decomposition of the player's units.
    List<Unit> allPlayerUnits = new ArrayList<>();
    List<Unit> playerBases = new ArrayList<>();
    List<Unit> playerBarracks = new ArrayList<>();
    List<Unit> playerWorkers = new ArrayList<>();
    List<Unit> playerLights = new ArrayList<>();
    List<Unit> playerRanged = new ArrayList<>();
    List<Unit> playerHeavies = new ArrayList<>();

    // Decomposition of the opponent's units.
    List<Unit> allOpponentUnits = new ArrayList<>();
    List<Unit> opponentBases = new ArrayList<>();
    List<Unit> opponentBarracks = new ArrayList<>();
    List<Unit> opponentWorkers = new ArrayList<>();
    List<Unit> opponentLights = new ArrayList<>();
    List<Unit> opponentRanged = new ArrayList<>();
    List<Unit> opponentHeavies = new ArrayList<>();

    // Resource deposits group.
    List<Unit> allResourceDeposits = new ArrayList<>();

    // The number of player units under construction/training, by type.
    int futurePlayerBases = 0;
    int futurePlayerBarracks = 0;
    int futurePlayerWorkers = 0;
    int futurePlayerLights = 0;
    int futurePlayerRanged = 0;
    int futurePlayerHeavies = 0;

    // The number of opponent units under construction/training, by type.
    int futureOpponentBases = 0;
    int futureOpponentBarracks = 0;
    int futureOpponentWorkers = 0;
    int futureOpponentLights = 0;
    int futureOpponentRanged = 0;
    int futureOpponentHeavies = 0;

    public StateMonitor(GameState gameState, int playerID) {

        this.gameState = gameState;
        this.playerID = playerID;
        groupByUnitType();
        countFuturePlayerUnits();
        countFutureOpponentUnits();
    }

    /**
     * Identify all units on the map and assign each one to its type-specific and player-specific group.
     */
    private void groupByUnitType() {

        PhysicalGameState physicalGameState = gameState.getPhysicalGameState();
        // Type grouping
        for (Unit unit : physicalGameState.getUnits()) {

            switch (unit.getType().name) {
                case "Resource":
                    allResourceDeposits.add(unit);
                    break;
                case "Base":
                    addToCorrectOwner(unit, playerBases, opponentBases);
                    break;
                case "Barracks":
                    addToCorrectOwner(unit, playerBarracks, opponentBarracks);
                    break;
                case "Worker":
                    addToCorrectOwner(unit, playerWorkers, opponentWorkers);
                    break;
                case "Light":
                    addToCorrectOwner(unit, playerLights, opponentLights);
                    break;
                case "Ranged":
                    addToCorrectOwner(unit, playerRanged, opponentRanged);
                    break;
                case "Heavy":
                    addToCorrectOwner(unit, playerHeavies, opponentHeavies);
                    break;
            }
        }
    }

    /**
     * Adds a unit to its correct player-specific, and type-specific groups.
     * @param unit The unit in question.
     * @param playerUnitTypeGroup The player-unit-type group.
     * @param opponentUnitTypeGroup The opponent-unit-type group.
     */
    private void addToCorrectOwner(Unit unit, List<Unit> playerUnitTypeGroup, List<Unit> opponentUnitTypeGroup) {
        if (unit.getPlayer() == playerID) {
            allPlayerUnits.add(unit);
            playerUnitTypeGroup.add(unit);
        } else {
            allOpponentUnits.add(unit);
            opponentUnitTypeGroup.add(unit);
        }
    }

    /**
     * Counts all the player's units/structures under training/construction.
     */
    private void countFuturePlayerUnits() {

        // Future workers.
        for (Unit base : playerBases) {
            UnitAction action = gameState.getUnitAction(base);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE &&
                    action.getUnitType().name.equals("Worker"))
                futurePlayerWorkers++;
        }

        // Future Light, Ranged and Heavies.
        for (Unit barracks : playerBarracks) {
            UnitAction action = gameState.getUnitAction(barracks);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE)
                switch (action.getUnitType().name) {
                    case "Light": futurePlayerLights++; break;
                    case "Ranged": futurePlayerRanged++; break;
                    case "Heavy": futurePlayerHeavies++; break;
                }
        }

        // Future Bases and Barracks.
        for (Unit worker : playerWorkers) {
            UnitAction action = gameState.getUnitAction(worker);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE)
                switch (action.getUnitType().name) {
                    case "Base": futurePlayerBases++; break;
                    case "Barracks": futurePlayerBarracks++; break;
                }
        }
    }

    /**
     * Counts all the opponent's units/structures under training/construction.
     */
    private void countFutureOpponentUnits() {

        // Future workers.
        for (Unit base : opponentBases) {
            UnitAction action = gameState.getUnitAction(base);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE &&
                    action.getUnitType().name.equals("Worker"))
                futureOpponentWorkers++;
        }

        // Future Light, Ranged and Heavies.
        for (Unit barracks : opponentBarracks) {
            UnitAction action = gameState.getUnitAction(barracks);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE)
                switch (action.getUnitType().name) {
                    case "Light": futureOpponentLights++; break;
                    case "Ranged": futureOpponentRanged++; break;
                    case "Heavy": futureOpponentHeavies++; break;
                }
        }

        // Future Bases and Barracks.
        for (Unit worker : opponentWorkers) {
            UnitAction action = gameState.getUnitAction(worker);
            if (action != null && action.getType() == UnitAction.TYPE_PRODUCE)
                switch (action.getUnitType().name) {
                    case "Base": futureOpponentBases++; break;
                    case "Barracks": futureOpponentBarracks++; break;
                }
        }
    }

    /**
     * Groups and returns all mobile units belonging to the player.
     * @return A Unit list.
     */
    public List<Unit> getPlayerMobileUnits() {
        List<Unit> mobileUnits = new LinkedList<>();
        mobileUnits.addAll(playerWorkers);
        mobileUnits.addAll(playerLights);
        mobileUnits.addAll(playerRanged);
        mobileUnits.addAll(playerHeavies);
        return mobileUnits;
    }

    /**
     * Groups and returns all mobile units belonging to the opponent.
     * @return
     */
    public List<Unit> getOpponentMobileUnits() {
        List<Unit> mobileUnits = new LinkedList<>();
        mobileUnits.addAll(opponentWorkers);
        mobileUnits.addAll(opponentLights);
        mobileUnits.addAll(opponentRanged);
        mobileUnits.addAll(opponentHeavies);
        return mobileUnits;
    }

    /**
     * Returns a list of opponent units around a given unit, in the immediate square range.
     *
     * @param unit The unit considered.
     * @param squareRange The maximum distance searched around the unit.
     * @return
     */
    public List<Unit> getOpponentUnitsAround(Unit unit, int squareRange) {
        List<Unit> closeUnits = new LinkedList<>();

        for (Unit opponentUnit : allOpponentUnits) {
            if (Math.abs(opponentUnit.getX() - unit.getX()) <= squareRange &&
                    Math.abs(opponentUnit.getY() - unit.getY()) <= squareRange) {
                closeUnits.add(opponentUnit);
            }
        }
        return closeUnits;
    }

    /**
     * Finds and returns the closest n opponent units to the given unit.
     * @param unit The unit in question.
     * @param maxUnits The number of close opponent units to consider.
     * @return a Unit list.
     */
    public List<Unit> getOpponentUnitsClosestTo(Unit unit, int maxUnits) {

        List<Unit> opponentUnits = new LinkedList<>(allOpponentUnits);
        List<Unit> closestOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (closestOpponentUnits.size() < maxUnits) {
                int closestDistance = 0;
                Unit closestEnemy = null;
                for (Unit opponentUnit : opponentUnits) {
                    int distance = Math.abs(unit.getX() - opponentUnit.getX()) +
                            Math.abs(unit.getY() - opponentUnit.getY());
                    if (closestEnemy == null || distance < closestDistance) {
                        closestDistance = distance;
                        closestEnemy = opponentUnit;
                    }
                }
                closestOpponentUnits.add(closestEnemy);
                opponentUnits.remove(closestEnemy);
            }
        } else
            return opponentUnits;

        return closestOpponentUnits;
    }

    /**
     * Returns a list of random opponent units.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    public List<Unit> getOpponentUnitsRandom(int maxUnits) {

        List<Unit> opponentUnits = new LinkedList<>(allOpponentUnits);
        List<Unit> randomOpponentUnits = new LinkedList<>();

        Random random = new Random();

        if (opponentUnits.size() > maxUnits) {
            while (randomOpponentUnits.size() < maxUnits) {
                randomOpponentUnits.add(
                        opponentUnits.remove(random.nextInt(opponentUnits.size())));
            }
        } else
            return opponentUnits;

        return randomOpponentUnits;
    }

    /**
     * Returns a list of opponent units having the highest HP.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    public List<Unit> getOpponentUnitsHighestHP(int maxUnits) {

        List<Unit> opponentUnits = new LinkedList<>(allOpponentUnits);
        List<Unit> highestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (highestHPOpponentUnits.size() < maxUnits) {
                int highestHP = 0;
                Unit highestHPUnit = null;
                for (Unit opponentUnit : opponentUnits) {
                    int unitHP = opponentUnit.getHitPoints();
                    if (highestHPUnit == null || unitHP > highestHP) {
                        highestHP = unitHP;
                        highestHPUnit = opponentUnit;
                    }
                }
                highestHPOpponentUnits.add(highestHPUnit);
                opponentUnits.remove(highestHPUnit);
            }

        } else
            return opponentUnits;

        return highestHPOpponentUnits;
    }

    /**
     * Returns a list of opponent units having the lowest HP.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    public List<Unit> getOpponentUnitsLowestHP(int maxUnits) {

        List<Unit> opponentUnits = new LinkedList<>(allOpponentUnits);
        List<Unit> lowestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (lowestHPOpponentUnits.size() < maxUnits) {
                int lowestHP = 0;
                Unit lowestHPUnit = null;
                for (Unit opponentUnit : opponentUnits) {
                    int unitHP = opponentUnit.getHitPoints();
                    if (lowestHPUnit == null || unitHP < lowestHP) {
                        lowestHP = unitHP;
                        lowestHPUnit = opponentUnit;
                    }
                }
                lowestHPOpponentUnits.add(lowestHPUnit);
                opponentUnits.remove(lowestHPUnit);
            }
        } else
            return opponentUnits;

        return lowestHPOpponentUnits;
    }

    /**
     * Returns the closest friendly base to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest base.
     */
    public Unit getPlayerBaseClosestTo(Unit unit) {
        Unit closestBase = null;
        int closestDistance = 0;
        for (Unit base : playerBases) {
            int distance = Math.abs(base.getX() - unit.getX()) + Math.abs(base.getY() - unit.getX());
            if (closestBase == null || distance < closestDistance) {
                closestDistance = distance;
                closestBase = base;
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
    public Unit getResourceDepositClosestTo(Unit unit) {
        Unit closestResource = null;
        int closestDistance = 0;
        for (Unit possibleResource : allResourceDeposits) {
            int distance = Math.abs(possibleResource.getX() - unit.getX()) + Math.abs(possibleResource.getY() - unit.getX());
            if (closestResource == null || distance < closestDistance) {
                closestDistance = distance;
                closestResource = possibleResource;
            }
        }
        return closestResource;
    }

    public int getPlayerResources() {
        return gameState.getPlayer(playerID).getResources();
    }

    public int getOpponentResources() {
        return gameState.getPlayer(1 - playerID).getResources();
    }

    public int getPlayerID() {
        return playerID;
    }

    public int getOpponentID() {
        return 1 - playerID;
    }

    public List<Unit> getPlayerAssaultUnits() {
        List<Unit> playerAssaultUnits = new LinkedList<>();
        playerAssaultUnits.addAll(playerLights);
        playerAssaultUnits.addAll(playerRanged);
        playerAssaultUnits.addAll(playerHeavies);
        return playerAssaultUnits;
    }

    public List<Unit> getOpponentAssaultUnits() {
        List<Unit> opponentAssaultUnits = new LinkedList<>();
        opponentAssaultUnits.addAll(opponentLights);
        opponentAssaultUnits.addAll(opponentRanged);
        opponentAssaultUnits.addAll(opponentHeavies);
        return opponentAssaultUnits;
    }

    public List<Unit> getAllResourceDeposits() {
        return allResourceDeposits;
    }

    public List<Unit> getAllPlayerUnits() {
        return allPlayerUnits;
    }

    public List<Unit> getPlayerBases() {
        return playerBases;
    }

    public List<Unit> getPlayerBarracks() {
        return playerBarracks;
    }

    public List<Unit> getPlayerWorkers() {
        return playerWorkers;
    }

    public List<Unit> getPlayerLights() {
        return playerLights;
    }

    public List<Unit> getPlayerRanged() {
        return playerRanged;
    }

    public List<Unit> getPlayerHeavies() {
        return playerHeavies;
    }

    public List<Unit> getAllOpponentUnits() {
        return allOpponentUnits;
    }

    public List<Unit> getOpponentBases() {
        return opponentBases;
    }

    public List<Unit> getOpponentBarracks() {
        return opponentBarracks;
    }

    public List<Unit> getOpponentWorkers() {
        return opponentWorkers;
    }

    public List<Unit> getOpponentLights() {
        return opponentLights;
    }

    public List<Unit> getOpponentRanged() {
        return opponentRanged;
    }

    public List<Unit> getOpponentHeavies() {
        return opponentHeavies;
    }

    public int getFuturePlayerBases() {
        return futurePlayerBases;
    }

    public int getFuturePlayerBarracks() {
        return futurePlayerBarracks;
    }

    public int getFuturePlayerWorkers() {
        return futurePlayerWorkers;
    }

    public int getFuturePlayerLights() {
        return futurePlayerLights;
    }

    public int getFuturePlayerRanged() {
        return futurePlayerRanged;
    }

    public int getFuturePlayerHeavies() {
        return futurePlayerHeavies;
    }

    public int getFutureOpponentBases() {
        return futureOpponentBases;
    }

    public int getFutureOpponentBarracks() {
        return futureOpponentBarracks;
    }

    public int getFutureOpponentWorkers() {
        return futureOpponentWorkers;
    }

    public int getFutureOpponentLights() {
        return futureOpponentLights;
    }

    public int getFutureOpponentRanged() {
        return futureOpponentRanged;
    }

    public int getFutureOpponentHeavies() {
        return futureOpponentHeavies;
    }

    public GameState getGameState() {
        return gameState;
    }

    public int getMapWidth() {
        return gameState.getPhysicalGameState().getWidth();
    }

    public int getMapHeight() {
        return gameState.getPhysicalGameState().getHeight();
    }

    public ResourceUsage getResourceUsage() {
        return gameState.getResourceUsage();
    }

    public PhysicalGameState getPhysicalGameState() {
        return gameState.getPhysicalGameState();
    }

    public int getTime() {
        return gameState.getTime();
    }

    public HashMap<Unit, UnitActionAssignment> getUnitActions() {
        return gameState.getUnitActions();
    }

}
