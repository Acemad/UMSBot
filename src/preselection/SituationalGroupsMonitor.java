package preselection;

import rts.units.Unit;

import java.util.LinkedList;
import java.util.List;

public class SituationalGroupsMonitor {

    // Front-Line Selection Modes *************************************************************************************
    public static final int SELECT_BY_PLAYER_RANGE = 0; // Select units having an opponent unit in their attack range.
    public static final int SELECT_BY_OPPONENT_RANGE = 1; // Select units in the attack range of opponent units.

    StateMonitor stateMonitor;

    List<Unit> frontLineUnits = new LinkedList<>();
    List<Unit> backUnits = new LinkedList<>();

    public SituationalGroupsMonitor(StateMonitor stateMonitor, int selectionMode, int maxUnits, int tacticalDistance) {
        this.stateMonitor = stateMonitor;
        selectFrontLineUnits(selectionMode, maxUnits, tacticalDistance);
    }

    /**
     * Selects the front-line units depending on the chosen selection mode, and the number of maximum units to select.
     *
     * @param selectionMode The selection method.
     * @param maxUnits The maximum number of units to return.
     * @param tacticalDistance The tactical distance added.
     */
    private void selectFrontLineUnits(int selectionMode, int maxUnits, int tacticalDistance) {

        switch (selectionMode) {
            case SELECT_BY_PLAYER_RANGE:
                selectFrontLineUnitsByPlayerRange(maxUnits, tacticalDistance);
                break;
            case SELECT_BY_OPPONENT_RANGE:
                selectFrontLineUnitsByOpponentRange(maxUnits, tacticalDistance);
                break;
        }
    }

    /**
     * Selects own units that have an opponent unit within their attack range.
     * @param maxUnits The maximum number of units to considered.
     * @param tacticalDistance A distance added to the attackRange in order to allow for tactical reasoning apriori.
     */
    private void selectFrontLineUnitsByPlayerRange(int maxUnits, int tacticalDistance) {

        for (Unit playerUnit : stateMonitor.getPlayerMobileUnits()) {
            for (Unit opponentUnit : stateMonitor.getOpponentMobileUnits()) {
                if (Math.abs(opponentUnit.getX() - playerUnit.getX()) <= playerUnit.getAttackRange() + tacticalDistance &&
                    Math.abs(opponentUnit.getY() - playerUnit.getY()) <= playerUnit.getAttackRange() + tacticalDistance) {

                    if (maxUnits < 0 || frontLineUnits.size() < maxUnits)
                        frontLineUnits.add(playerUnit);
                    break;
                }
            }
            if (maxUnits >= 0 && frontLineUnits.size() >= maxUnits)
                break;
        }
    }

    /**
     * Selects own units that are in the attack range of an opponent unit.
     * @param maxUnits The maximum number of units to considered.
     * @param tacticalDistance A distance added to the attackRange in order to allow for tactical reasoning apriori.
     */
    private void selectFrontLineUnitsByOpponentRange(int maxUnits, int tacticalDistance) {

        for (Unit opponentUnit : stateMonitor.getOpponentMobileUnits()) {
            for (Unit playerUnit : stateMonitor.getPlayerMobileUnits()) {
                if (!frontLineUnits.contains(playerUnit) &&
                    (Math.abs(playerUnit.getX() - opponentUnit.getX()) <= opponentUnit.getAttackRange() + tacticalDistance &&
                     Math.abs(playerUnit.getY() - opponentUnit.getY()) <= opponentUnit.getAttackRange() + tacticalDistance))

                    if (maxUnits < 0 || frontLineUnits.size() < maxUnits)
                        frontLineUnits.add(playerUnit);
                    else
                        break;
            }
            if (maxUnits >= 0 && frontLineUnits.size() >= maxUnits)
                break;
        }
    }

    /**
     * Returns the list of Back units (non-front-line units)
     * @return A Unit list.
     */
    public List<Unit> getBackUnits() {
        selectBackUnits();
        return backUnits;
    }

    /**
     * The remaining unselected units that are not in the front-line units list, are all considered back units.
     */
    private void selectBackUnits() {
        for (Unit unit : stateMonitor.getAllPlayerUnits()) {
            if (!frontLineUnits.contains(unit)) backUnits.add(unit);
        }
    }

    public List<Unit> getFrontLineUnits() {
        return frontLineUnits;
    }
}
