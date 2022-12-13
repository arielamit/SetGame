package bguspl.set.ex;

import bguspl.set.Env;

//Added by Ariel
import java.util.LinkedList;
import java.util.Queue;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
     *  --    Added by Ariel    --
     */
    private Integer[]slots = new Integer[3]; //??
    private Queue<Player> waitingForCheck = new LinkedList<>();

    private Thread dealerThread;
    /**
     *  finish --    Added by Ariel    --
     */

    // TODO : what is external event? -- probably press on X
    /**

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    //TODO : find out why reshuffleTime is maxValue?
    private long reshuffleTime = Long.MAX_VALUE;

    //Added:
    long gameStartTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        //Added:
        long gameStartTime;
    }

    //Added: creates thread for each player
    private void createPlayers()
    {
        String[]playersNames = env.config.playerNames;
        for(int i=0; i<players.length; i++)
        {
            Thread player = new Thread(players[i], playersNames[i]);
            player.start();
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //TODO : create players
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */

    //Omer
    private void timerLoop() {
        gameStartTime = System.currentTimeMillis();
        reshuffleTime = gameStartTime + 60000;

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            placeCardsOnTable();
            removeCardsFromTable();
        }
    }

    // TODO : what is externael event? -- probably terminate if press X

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        //Ariel
        Thread.currentThread().interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */

    //Ariel
    private void removeCardsFromTable() {
        // TODO implement
        if(slots[0] != null)
        {
            for(int i=0; i<3; i++)
            {
                table.removeCard(slots[i]);
                slots[i] = null;
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */

    //Ariel
    private void placeCardsOnTable() {
        // TODO implement
        Integer[] slotToCard = table.getSlotToCard();
        Integer[] cardToSlot = table.getCardToSlot();
        if (deck.size() > 0) //there are no cards in deck
        {
            //check each slot
            for (int i = 0; i < slotToCard.length; i++) {
                //there is no card in this place and there is at least one card on deck
                if (slotToCard[i] == null && deck.size() > 0) {
                    //take card from deck and place it on table
                    int cardToPlace = deck.get(0);
                    table.placeCard(cardToPlace, i);
                    deck.remove(0);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    // TODO : make sure update is correct
    //Omer
    private void updateTimerDisplay(boolean reset)
    {
        if(env.config.turnTimeoutMillis>-1)
        {
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            boolean warn = (timeLeft < 10000);   // TODO : findout how I find when the clock turns red
            env.ui.setCountdown(timeLeft,warn);
        }
        else
        {
            long timePassed = System.currentTimeMillis()- gameStartTime;
            env.ui.setCountdown(timePassed, false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */

    //Ariel
    private void removeAllCardsFromTable() {
        // TODO implement
        for(int i=0; i<table.slotToCard.length; i++)
        {
            if(table.slotToCard[i] != null)
            {
                Integer cardToRemove = table.slotToCard[i];
                table.removeCard(cardToRemove);
                deck.add(cardToRemove);
            }
        }
        //should throw an exception?
    }

    /**
     * Check who is/are the winner/s and displays them.
     */

    //Ariel
    private void announceWinners() {
        // TODO implement
        int maxScore = 0;
        List <Player> winners = null; //which type of list?
        for(int i=0; i< players.length; i++)
        {
            Player currPlayer = players[i];
            //add players with num of scores that bigger than the maximum current score
            if(currPlayer.getScore()>=maxScore)
            {
                maxScore = currPlayer.getScore();
                winners.add(currPlayer);
            }
        }
        //remove from list the players with less than maximum num of score
        for(int i=0; i<winners.size(); i++)
        {
            Player currPlayer = winners.get(i);
            if(currPlayer.getScore()<maxScore)
                winners.remove(i);
        }
        //print the winners id's
        for(int i=0; i<winners.size(); i++)
        {
            Player currPlayer = winners.get(i);
            System.out.println("Player "+currPlayer.getId()+" wins");
        }
    }
}
