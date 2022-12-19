package bguspl.set.ex;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * A blocking queue that stores the keys were pressed by the player thread
     */
    public BlockingQueue<Integer> keyPressed;
    public Dealer dealer;
    private boolean shouldPunished ;
    public ArrayList<Integer> playerTokens ;
    boolean shouldRemoveToken = false ;
    public int tokenToRemove ;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        // TODO : check if need to remove init dealer
        this.dealer = dealer;
        this.keyPressed = new LinkedBlockingQueue<Integer>(env.config.featureSize);
        this.shouldPunished = false;
        this.playerTokens = new ArrayList<Integer>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try
            {
                Integer key = keyPressed.take();
                //TODO : REMOVE CHEAT
                //Integer key = keyPressedSimulator();
                //if(this.id == 0)
                //    key = cheatKey();
                System.out.println("key " + key);
                //Check if there is a token that needs to be removed
                if(shouldRemoveToken)
                {
                    if (playerTokens.contains(tokenToRemove))
                    {
                        System.out.println("I remove my token " + id );
                        playerTokens.remove(tokenToRemove);
                        table.removeToken(id,tokenToRemove);
                        shouldRemoveToken = false;
                        System.out.println("1454544");
                    }
                }

                if (playerTokens.contains(key)) {
                    playerTokens.remove(key);
                    table.removeToken(id,key);
                    System.out.println("Remove TOKEN id: " + id);
                }
                else // The token is not exist
                {
                    if(playerTokens.size() < env.config.featureSize) {
                        playerTokens.add(key);
                        table.placeToken(id,key);
                        System.out.println("Add TOKEN id: " + id);
                        //insures that set sent to dealer even if new token placed
                        if (playerTokens.size() == env.config.featureSize) {
                            table.waitingForCheck.put(id);
                            synchronized (dealer){
                                try {
                                    dealer.notifyAll();
                                } catch (Exception e) {
                                    System.out.println(e.getMessage());
                                }
                            }
                            synchronized (this) {
                                try {
                                    wait();
                                } catch (InterruptedException e) {}
                                if (shouldPunished)
                                    penalty();
                                else
                                    point();
                            }
                        }
                    }
                }
            }
            catch (Exception e) {}
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                int chosenSlot = keyPressedSimulator();
                keyPressed(chosenSlot);
                //TODO: check sync lines
//                try {
//                    synchronized (this) { wait(); }
//                } catch (InterruptedException ignored) {}
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    //Added
    public int getId() { return id; }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        while(playerThread.isAlive())
        {
            try {
                playerThread.join();
            } catch (InterruptedException ignored) {}
        }
    }

    //Added
    private int keyPressedSimulator()
    {
        int rndSlot = (int)(Math.random()*env.config.tableSize);
        return rndSlot;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (keyPressed.size() < env.config.featureSize && table.getSlotToCard()[slot] != null) {
            try {
                keyPressed.put(slot);
            } catch (InterruptedException ignored) {
                System.out.println("KeyPressed Exception " );
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score = score + 1;
        env.ui.setFreeze(this.id,env.config.pointFreezeMillis);
        env.ui.setScore(id, score);
        try{
            playerThread.sleep(env.config.pointFreezeMillis);
        }catch (InterruptedException e){}
        env.ui.setFreeze(this.id,0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(this.id,env.config.penaltyFreezeMillis);
        try{
            playerThread.sleep(env.config.penaltyFreezeMillis);
        }catch (InterruptedException e){}
        env.ui.setFreeze(this.id,0);
        shouldPunished = false;
    }

    public int score() {
        return score;
    }

    public void setPunish(){
        shouldPunished = true;
    }

    //TODO : delete this function
    public int cheatKey()
    {
        int output = 0;
        if(table.cheat.size()>0)
        {
            output = table.cheat.get(table.cheatIndex);
            table.cheatIndex++;
            if (table.cheatIndex > 2)
                table.cheatIndex = 0;
        }
        return output;
    }
}
