package preselection;

public class EventsManager {

    StateMonitor stateMonitor;
    FunctionalGroupsMonitor groupsMonitor;

    public EventsManager(StateMonitor stateMonitor, FunctionalGroupsMonitor groupsMonitor) {
        this.stateMonitor = stateMonitor;
        this.groupsMonitor = groupsMonitor;
    }

    /**
     * Runs the event manager and check for triggers. If one of the triggers get activated, a stance switch happens for
     * the set number of switching units.
     *
     * @param defensePeriod Time Trigger: The period in which units remain in defense mode.
     * @param switchDelay Time Trigger: The delay given to defense units to quit the defense perimeter.
     * @param unitsCountThreshold Unit Count Trigger: The threshold unit count after which units switch stance.
     * @param overpowerFactor Score Trigger: The factor by which the player's score should surpass the opponent's score.
     * @param assaultUnitValue Score Trigger: The value of assault units, with respect to workers.
     * @param switchingUnitsCount The number of units to switch from defense to offense. -1: switch all units.
     */
    public void run(int defensePeriod, int switchDelay, int unitsCountThreshold,
                    float overpowerFactor, int assaultUnitValue, int switchingUnitsCount) {

        if (defenseTimeTrigger(defensePeriod, switchDelay) ||
            defenseUnitsCountTrigger(unitsCountThreshold) ||
            unitCompositionScoreTrigger(overpowerFactor, assaultUnitValue)) {
            // Switch defense units to attack.
            groupsMonitor.fromDefenseToOffense(switchingUnitsCount);
        }
    }

    /**
     * A defense time trigger. Causes a stance switch when a defense period has elapsed, and keeps the switch active
     * for a given delay to allow units to move out of the defense perimeter. Defense units staying in the perimeter
     * after switchDelay are kept as defense units.
     *
     * @param defensePeriod The period in which units remain in defense mode.
     * @param switchDelay A delay given to defense players in order to switch stance.
     * @return True if the defense period has elapsed and the switch delay still in effect. False otherwise.
     */
    private boolean defenseTimeTrigger(int defensePeriod, int switchDelay) {
        return (stateMonitor.getTime() > 0 && defensePeriod > 0 &&
               (stateMonitor.getTime() % defensePeriod == 0 ||
                (stateMonitor.getTime() % defensePeriod < switchDelay && stateMonitor.getTime() > switchDelay)));
    }

    /**
     * A simple trigger that counts the number of units in defense and returns true when the count surpasses a given
     * threshold.
     *
     * @param unitsCountThreshold The number of units in defense that may cause the trigger.
     * @return True, if the number of defense units is higher or equals the threshold. False otherwise.
     */
    private boolean defenseUnitsCountTrigger(int unitsCountThreshold) {
        return (unitsCountThreshold > 0 &&
                groupsMonitor.getDefenseUnits().size() >= unitsCountThreshold);
    }

    /**
     * A unit composition score trigger. Returns true (triggers) when the score of the current player is superior to
     * that of the opponent. The player score may get discounted by a factor to allow room for significantly higher
     * player score to cause the trigger. This has the effect of overpowering the opponent forces.
     *
     * @param overpowerFactor The percent by which the player's score should surpass the opponent's in order to trigger
     *                        the switch.
     * @param assaultUnitValue The value of the assault units.
     * @return True when the player's score is greater that the opponent's. False otherwise.
     */
    private boolean unitCompositionScoreTrigger(float overpowerFactor, int assaultUnitValue) {
        if (overpowerFactor < 0f) return false;
        int playerScore = unitCompositionScore(stateMonitor.getPlayerID(), assaultUnitValue);
        int opponentScore = unitCompositionScore(stateMonitor.getOpponentID(), assaultUnitValue);
        //return playerScore * (1 - overpowerFactor) > opponentScore;
//        System.out.println((playerScore / (float) opponentScore));
        return (playerScore / (float) opponentScore) >= overpowerFactor;
    }

    /**
     * Calculates a simple score reflecting the strength of the army composition of a player. Assault units normally
     * weight more than worker units. This score only considers mobile units and only two categories: workers and
     * all assault units (no distinction between Light, Ranged and Heavy). The value of a worker is multiplied by one.
     *
     * @param playerID The ID of the player whom we would like to calculate a composition score.
     * @param assaultUnitValue The value of assault units with respect to workers.
     * @return The calculated score for the given player.
     */
    private int unitCompositionScore(int playerID, int assaultUnitValue) {
        if (playerID == stateMonitor.getPlayerID())
            return stateMonitor.getPlayerAssaultUnits().size() * assaultUnitValue +
                    stateMonitor.getPlayerWorkers().size();
        if (playerID == stateMonitor.getOpponentID())
            return stateMonitor.getOpponentAssaultUnits().size() * assaultUnitValue +
                    stateMonitor.getOpponentWorkers().size();
        return -1;
    }
}
