package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.*;
import java.util.logging.Level;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    //Added
    long gameStartTime;
    List<Integer> slotsToClear = new ArrayList<Integer>(); //set we have to discard

    /**
     * List of players threads. loop this list to stop each thread
     */
    List<Thread> playersThreads = new ArrayList<Thread>();

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        long gameStartTime;
        reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
    }

    // creates thread for each player
    private void createPlayers() {
        String[] playersNames = env.config.playerNames;
        for (int i = 0; i < players.length; i++) {
            Thread player = new Thread(players[i], playersNames[i]);
            playersThreads.add(player);
            player.start();
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        // first cards before init players threads
        placeCardsOnTable();
        createPlayers();
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            //TODO : change to bonus
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        removeAllCardsFromTable();
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        gameStartTime = System.currentTimeMillis();
        // TODO : fix turnTimeoutMillis for bonus !!!!!
        reshuffleTime = gameStartTime + env.config.turnTimeoutMillis;
//        reshuffleTime = gameStartTime + 60000;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            placeCardsOnTable();
            //TODO: remove hints
            table.hints();
            removeCardsFromTable();
        }
        updateTimerDisplay(false);
        removeAllCardsFromTable();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
//        for (int i=players.length-1; i>=0; i--)
//            players[i].terminate();
        Thread.currentThread().interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || !hasSet(deck, table.getSlotToCard());
    }

    //check if there is available set on deck and table
    private boolean hasSet(List<Integer> deck, Integer[] slotToCard) {
        List<Integer> allAvailableCards = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++)
            allAvailableCards.add(deck.get(i));
        for (int j = 0; j < slotToCard.length; j++) {
            if (slotToCard[j] != null)
                allAvailableCards.add(slotToCard[j]);
        }
        return env.util.findSets(allAvailableCards, 1).size() > 0;
    }

    /**
     * Checks cards should be removed from the table and removes them. discard those cards.
     * Use only with valid Set
     */
    private void removeCardsFromTable() {
        for (Integer i : slotsToClear) {
            table.removeCard(i);
            removeAllTokensOfSlot(i);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Integer[] slotToCard = table.getSlotToCard();
        if (deck.size() > 0) //at least one card on deck
        {
            for (int i = 0; i < slotToCard.length; i++) { //check each slot
                if (slotToCard[i] == null && deck.size() > 0) {//no card in this place and at least one card on deck
                    updateTimerDisplay(false);
                    //take random card from deck and place it on table
                    double rnd = Math.random();
                    int cardToPlace = 0;
                    if (deck.size() > 1) //more than one option to choose from
                        cardToPlace = (int) (rnd * (deck.size()));
                    table.placeCard(deck.get(cardToPlace), i);
                    deck.remove(cardToPlace);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (this) {
                this.wait(1000);
            }
            // Check if there are any possible set's
            if (table.waitingForCheck.size() > 0) {
                Player claimPlayer = getPlayerById(table.waitingForCheck.take());
                synchronized (claimPlayer)
                {
                    int[] tokensPlace = listToArray(claimPlayer.playerTokens); //the place of tokens of this player
                    int[] possibleSet = new int[tokensPlace.length]; //cards that player put tokens on
                    boolean validCards = true;
                    //Change each slot to the card in it
                    for (int j = 0; j < possibleSet.length && validCards; j++)
                    {
                        validCards = ((Integer)table.slotToCard[tokensPlace[j]] != null);
                        possibleSet[j] = table.slotToCard[tokensPlace[j]];
                    }
                    //check if there are some duplicates values and that the set has valid size
                    boolean equalsValues = (possibleSet.length != env.config.featureSize || !validCards);
                    for(int l=0; l< possibleSet.length-1 && !equalsValues; l++)
                        if(possibleSet[l] == possibleSet[l+1])
                            equalsValues = true;
                    //clear all pressed keys
                    claimPlayer.keyPressed.clear();
                    //Adding the slots that need to be removed
                    for (Integer i : claimPlayer.playerTokens)
                        slotsToClear.add(i);
                    if (!equalsValues && env.util.testSet(possibleSet)) {
                        System.out.println("True set " + claimPlayer.getId());
                        System.out.println(Arrays.toString(possibleSet) );
                        //clear all tokens,of certain player, from the table
                        removeAllTokensOfPlayer(claimPlayer);

//                        while (claimPlayer.playerTokens.size() > 0)
//                        {
////                            System.out.println("While " + claimPlayer.getId() );
////                            System.out.println(" While " + claimPlayer.playerTokens.get(0));
//                            updateTimerDisplay(false);
//                            table.removeToken(claimPlayer.getId(), claimPlayer.playerTokens.get(0));
//                            claimPlayer.playerTokens.remove(0);
//                        }


                        //clear valid set cards from the table
                        removeCardsFromTable();
                        slotsToClear.clear();
                        claimPlayer.setPoint();
                        updateTimerDisplay(false);
                        // if the dealer removed a token of waiting player the player should not be punished or reward
                    }
                    else if(equalsValues)
                    {
                        System.out.println("Not False and not True set id: "+claimPlayer.getId());
                        //clear all tokens,of certain player, from the table
                        removeAllTokensOfPlayer(claimPlayer);


//                        while (claimPlayer.playerTokens.size() > 0)
//                        {
//                            table.removeToken(claimPlayer.getId(), claimPlayer.playerTokens.get(0));
//                            claimPlayer.playerTokens.remove(0);
//                        }


                    }
                    else
                    {
                        System.out.println("False set id: "+claimPlayer.getId());
                        claimPlayer.setPunish();
                        slotsToClear.clear();
                    }
                    updateTimerDisplay(false);
                    claimPlayer.notifyAll();
                }
            }
        } catch (Exception e) {
            System.out.println("dealer.run " + e.getMessage());
            System.err.println(e);
        };
    }

    private Player getPlayerById(int id) {
        for(int i=0; i<players.length; i++)
            if(players[i].getId() == id)
                return players[i];
        return null;
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
        long timeLeft = reshuffleTime - System.currentTimeMillis();
        if (timeLeft < 0)
            timeLeft = 0;
        if (timeLeft > env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(timeLeft, false);
        } else {
            env.ui.setCountdown(timeLeft, true);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // firstly we remove all tokens
        for (int j = 0; j < players.length; j++)
            removeAllTokensOfPlayer(players[j]);
        // secondly we remove all cards from the table
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null) {
                Integer cardToRemove = table.slotToCard[i];
                deck.add(cardToRemove);
                table.removeCard(i);
            }
        }
    }

    private void removeAllTokensOfPlayer(Player p) {
        //clear all tokens from the table
        synchronized (p)
        {
            List<Integer> removeListTokens = new ArrayList<>();
            for(Integer i : p.playerTokens)
                removeListTokens.add(i);
            for (Integer j : removeListTokens)
            {
                table.removeToken(p.getId(), j);
                p.playerTokens.remove(j);
            }
            //clear all pressed keys
            p.keyPressed.clear();
        }
    }


    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int[] playersScores = new int[players.length];
        int maxScore = 0;
        for (int i = 0; i < playersScores.length; i++)
        {
            playersScores[i] = players[i].score();
            if (maxScore < playersScores[i])
                maxScore = playersScores[i];
        }
        int numOfWinners = 0;
        for (int j = 0; j < playersScores.length; j++)
            if (playersScores[j] == maxScore)
                numOfWinners++;
        int[] winners = new int[numOfWinners];
        for (int k = 0; k < winners.length; k++)
            winners[k] = playersScores[playersScores.length - 1 - k];
        env.ui.announceWinner(winners);
    }

    /**
     * This function takes list of player's tokens and change it into array
     */
    private int[] listToArray(List<Integer> list) {
        int[] output = new int[env.config.featureSize];
        for (int i = 0; i < list.size(); i++)
            output[i] = list.get(i);
        return output;
    }

    /**
     * remove all the token we placed on a slot. Use when discard card
     * @param slot
     */
    public void removeAllTokensOfSlot (int slot)
    {
        List<Integer> allTokensList = new ArrayList<>();
        List<Integer> removeListTokens = new ArrayList<>();
        for(Player p : players)
        {
            synchronized (p)
            {
                allTokensList =  p.playerTokens;
                for (Integer i : allTokensList)
                    if (i == slot)
                        removeListTokens.add(i);
                for (Integer j :removeListTokens )
                {
                    p.playerTokens.remove(j);
                    table.removeToken(p.id, j);
                }
            }
        }
    }
}