package org.star.circuit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.star.R;
import org.star.common.game.BaseObject;
import org.star.common.game.DrawableOverlay;
import org.star.common.game.HUD;
import org.star.common.types.FixedSizeArray;
import org.star.common.types.Vector2;

public class ReleaseManager extends BaseObject {

    /** index representing the silver badge */
    public final static int BADGE_INDEX_SILVER = 0;

    /** index representing the gold badge */
    public final static int BADGE_INDEX_GOLD = 1;

    private final static int[] sBadgeImage = {R.drawable.silver_coin, R.drawable.gold_coin};

    /** constant, number of miliseconds in a minute */
    private final static int MS_PER_MIN = 60*1000;

    /** constant, number of miliseconds in a second */
    private final static int MS_PER_SEC = 1000;

    /** the nodes that make up the track */
    private Node[][] mNodes = null;

    /** the nodes that make up the outside of the track */
    private Node[] mBorderNodes = null;

    /** the spark that traverses the track */
    private Spark mSpark = null;

    /** the node at which the spark begins its journey */
    private Node mStartNode = null;

    /** the node at which the spark ends its journey */
    private Node mEndNode = null;

    /** the node the spark just departed from */
    private Node mPreviousNode = null;

    /** reference to the drawable overlay which we use for effects */
    private DrawableOverlay mOverlay;

    /** reference to the base activity */
    private Context mContext;

    /** the current map data */
    private ParsedMapData mParsedMapData;

    /** records how long it takes to get the spark over the finish line */
    private long mPlayTime;

    /** handles the level complete ui callback */
    private Handler mHandler;

    /** id maps the save file to this level */
    private int mSaveId;

    /** string resource ids to be used as message when badge is unlocked */
    private int[] sBadgeMessage = {R.string.silver_badge_message, R.string.gold_badge_message};

    public ReleaseManager(DrawableOverlay overlay, Context context, Handler handler, int mapNameResource, int saveId, ParsedMapData parsedMapData) {
        mOverlay = overlay;
        mContext = context;
        mHandler = handler;
        mParsedMapData = parsedMapData;
        mSaveId = saveId;

        int parTime = 0;
        int numPotentialBadges = mParsedMapData.mBadges.getCount();
        outerloop:
        for(int i = 0; i < numPotentialBadges; i++) {
            FixedSizeArray<ParsedMapData.RequirementTemplate> reqs = mParsedMapData.mBadges.get(i).mRequirements;
            for(int j = 0; j < reqs.getCount(); j++) {
                String reqType = reqs.get(j).getType();
                int reqValue = reqs.get(j).getValue();
                if(reqType.equals(CircuitConstants.BADGE_TYPE_TIME)) {
                    parTime = reqValue;
                    break outerloop;
                }
            }
        }

        mSpark = new Spark();
        String mapName = mContext.getResources().getString(mapNameResource);
        HUD.getInstance().addTextElement(-1,mapName, 36, Color.GREEN, CircuitConstants.TYPE_FACE_AGENCY, 30, 60, true, CircuitConstants.UNIQUE_ELEMENT_MAP_NAME);
        HUD.getInstance().addTextElement(-1,convertMilisecondsToDisplayTime(mPlayTime), 36, Color.CYAN, CircuitConstants.TYPE_FACE_AGENCY, 150, 60, true, CircuitConstants.UNIQUE_ELEMENT_PLAY_TIME);
        HUD.getInstance().addTextElement(-1,convertMilisecondsToDisplayTime(parTime), 36, Color.YELLOW, CircuitConstants.TYPE_FACE_AGENCY, 300, 60, true, CircuitConstants.UNIQUE_ELEMENT_PAR_TIME);
    }

    /**
     * convert a milisecond value into a formated MM:SS:MM string
     *
     * @param longMS
     * @return
     */
    private String convertMilisecondsToDisplayTime(long longMS) {
        int minutes = (int) longMS / MS_PER_MIN;
        int seconds = (int) ((longMS - minutes*MS_PER_MIN)/ MS_PER_SEC);
        int miliseconds = (int) (longMS - (minutes*MS_PER_MIN) - (seconds*MS_PER_SEC));

        String minString = (minutes <= 9) ? "0" : "";
        minString += Long.toString(minutes);

        String secString = (seconds <= 9) ? "0" : "";
        secString += Long.toString(seconds);

        String miliString = (miliseconds <= 99) ? "0" : "";
        miliString += Long.toString(miliseconds);
        miliString = miliString.substring(0, 2);

        String finalTime = minString + ":" + secString + ":" + miliString;
        return finalTime;
    }

    /**
     * load the nodes
     *
     * @param nodes
     */
    public void loadNodes(Node[][] nodes, Node[] borderNodes) {
        mNodes = nodes;
        mBorderNodes = borderNodes;
        int len = borderNodes.length;
        for(int i = 0; i < len; i++) {
            if(borderNodes[i].getType().equals(Node.NODE_TYPE_START)) {
                mStartNode = borderNodes[i];
            }
            if(borderNodes[i].getType().equals(Node.NODE_TYPE_END)) {
                mEndNode = borderNodes[i];
            }
        }
    }

    /**
     * reset the spark
     */
    public void resetSpark() {
        mSpark.resetSpark();
    }

    /**
     * release the spark
     */
    public void releaseSpark() {
        mPlayTime = 0;
        HUD.getInstance().removeTextElement(CircuitConstants.BADGE_TYPE_COMPLETE);
        mSpark.resetSpark();
        if(mStartNode != null) {
            mSpark.setPosition(mStartNode.getPosition().x, mStartNode.getPosition().y);
            mSpark.setTarget(resolveNextTargetNode(mStartNode));
            mSpark.setStartingSpeed(Spark.STARTING_VELOCITY);
        } else {
            Log.e("ERROR", "no start node set");
        }

    }

    @Override
    public void update(long timeDelta) {
        if(mSpark.getReleased()) {
            mSpark.update(timeDelta);
            if(mSpark.getReadyForNextTarget()) {
                Node currentNode = mSpark.getTarget();
                if(canAdvancePastThisNode(currentNode)) {
                    if(currentNode.getType().equals(Node.NODE_TYPE_KEY)) {
                        mSpark.setCurrentKey(currentNode.getKeyId());
                    }
                    mSpark.setTarget(resolveNextTargetNode(currentNode));
                    mSpark.updateSprite(timeDelta);
                } else {
                    handleCircuitIncomplete();
                }
            }
            mPlayTime += timeDelta;
            HUD.getInstance().modifyTextElement(convertMilisecondsToDisplayTime(mPlayTime), CircuitConstants.UNIQUE_ELEMENT_PLAY_TIME);
        }
    }

    /**
     * Can the spark make it past the current node without being destroyed?
     *
     * @param currentNode
     * @return
     */
    private boolean canAdvancePastThisNode(Node currentNode) {
        boolean result = false;
        if(currentNode.getType().equals(Node.NODE_TYPE_SPEED_TRAP)) {
            double speed = mSpark.getCurrentSpeed();
            if(currentNode.getMinSpeedLimit() < speed && currentNode.getMaxSpeedLimit() > speed) {
                result = true;
            }
        } else if(currentNode.getType().equals(Node.NODE_TYPE_GATE)) {
            if(mSpark.getCurrentKey() == currentNode.getKeyId()) {
                result = true;
            }
        } else {
            result = true;
        }
        return result;
    }

    /**
     * Figure out what the next node connection will be from any given node
     *
     * @param currentNode
     * @return
     */
    private Node resolveNextTargetNode(Node currentNode) {
        Node result = null;
        if(currentNode.getK() == mEndNode.getK()) {
            handleCircuitComplete();
        } else {
            NodeConnection pConnection = currentNode.getPreferredConnection();
            // don't add the connection to this node that the spark just came from
            if(pConnection != null && (mPreviousNode.getI() != pConnection.getI() || mPreviousNode.getJ() != pConnection.getJ())) {
                result = getNodeFromConnection(pConnection);
            } else {
                NodeConnection[] connections = currentNode.getConnections();
                int lastResortNodeIndex = -1;
                for(int i = 0; i < connections.length; i++) {
                    // if there is no previous connection we assume we are just starting
                    if(mPreviousNode == null) {
                        result = getNodeFromConnection(connections[i]);
                        // if there is a node straight ahead, take that one, ^ is the exclusive or (XOR) operator in java
                    } else if(mPreviousNode.getI() == connections[i].getI() ^ mPreviousNode.getJ() == connections[i].getJ()) {
                        result = getNodeFromConnection(connections[i]);
                        break;
                        // else use any node not including the one you just came from
                    } else if(mPreviousNode.getI() != connections[i].getI() || mPreviousNode.getJ() != connections[i].getJ()) {
                        lastResortNodeIndex = i;
                    }
                }
                if(result == null) {
                    if(lastResortNodeIndex > -1) {
                        result = getNodeFromConnection(connections[lastResortNodeIndex]);
                    } else {
                        handleCircuitIncomplete();
                    }
                }
            }
        }
        mPreviousNode = currentNode;
        return result;
    }

    /**
     * Makes a callback to the UI activity class to display a popup
     * informing the user of the result of their release
     *
     * @param complete
     * @param badgesWon
     */
    private void showFinishedDialog(boolean complete, FixedSizeArray<Integer> badgesWon) {
        Message msg = mHandler.obtainMessage();
        Bundle bund = new Bundle();

        int completeTextId = R.string.circuit_incomplete;
        if(complete) {
            completeTextId = R.string.circuit_complete;
        }
        bund.putString("title", mContext.getResources().getString(completeTextId));

        if(badgesWon != null) {
            int numBadgesWon = badgesWon.getCount();
            String[] textArray = new String[numBadgesWon];
            int[] badgeImageArray = new int[numBadgesWon];
            for(int i = 0; i < numBadgesWon; i++) {
                textArray[i] = mContext.getString(sBadgeMessage[badgesWon.get(i)]);
                badgeImageArray[i] = sBadgeImage[badgesWon.get(i)];
            }
            bund.putStringArray("textArray", textArray);
            bund.putIntArray("resIDArray", badgeImageArray);
        }

        msg.setData(bund);
        mHandler.sendMessage(msg);
    }

    /**
     * handle the circuit complete scenario
     */
    private void handleCircuitComplete() {
        // now we use the UI thread for this
        //HUD.getInstance().showStaticTextElement(-1, CircuitConstants.PRERENDERED_TEXT_INDEX_COMPLETE, 250, 100, true, HUD.UNIQUE_ELEMENT_COMPLETE);
        resetSpark();
        showFinishedDialog(true, checkForBadgesEarned());
    }

    /**
     * handle the circuit incomplete scenario
     */
    private void handleCircuitIncomplete() {
        // now we use the UI thread for this
        //HUD.getInstance().showStaticTextElement(-1, CircuitConstants.PRERENDERED_TEXT_INDEX_INCOMPLETE, 250, 100, true, HUD.UNIQUE_ELEMENT_COMPLETE);
        Vector2 pos = mSpark.getPosition();
        mOverlay.createParticle((int)(pos.x + (Grid.NODE_DIMENSION/2)), (int)(pos.y - (Grid.NODE_DIMENSION/2)) , 10);
        resetSpark();
        showFinishedDialog(false, null);
    }

    /**
     * large switch statement to evaluate each type of badge you can earn
     * NOTE: currently only supports one badge
     *
     * @param type
     * @param value
     * @return
     */
    private Boolean evaluateBadgeRequirement(String type, int value) {
        Boolean result = false;
        if(type.equals(CircuitConstants.BADGE_TYPE_TIME)) {
            if(value > mPlayTime) {
                result = true;
            }
        } else if(type.equals(CircuitConstants.BADGE_TYPE_COMPLETE)) {
            result = true;
        }
        return result;
    }

    /**
     * Check to see if the player has earned any badges and if so packages
     * up a message to send back to the UI thread to display to the player, also
     * handles saving of data to shared preferences
     *
     * @return
     */
    private FixedSizeArray<Integer> checkForBadgesEarned() {
        // temporary hack, this needs to be automatic
        int[] earnedBadgeArray = new int[2];
        earnedBadgeArray[BADGE_INDEX_SILVER] = 0;
        earnedBadgeArray[BADGE_INDEX_GOLD] = 0;

        // saving best time just for the heck of it right now
        SharedPreferences settings = mContext.getSharedPreferences(CircuitConstants.SHARED_PREFS_KEY, 0);
        SharedPreferences.Editor editor = settings.edit();
        long bestTime = settings.getLong(mSaveId + CircuitConstants.SAVE_POSTFIX_BEST_TIME, 999999);
        if(mPlayTime < bestTime) {
            editor.putLong(mSaveId + CircuitConstants.SAVE_POSTFIX_BEST_TIME, mPlayTime);
        }

        int numPotentialBadges = mParsedMapData.mBadges.getCount();
        for(int i = 0; i < numPotentialBadges; i++) {
            FixedSizeArray<ParsedMapData.RequirementTemplate> reqs = mParsedMapData.mBadges.get(i).mRequirements;
            Boolean pass = false;
            for(int j = 0; j < reqs.getCount(); j++) {
                String reqType = reqs.get(j).getType();
                int reqValue = reqs.get(j).getValue();
                pass = evaluateBadgeRequirement(reqType, reqValue);
                if(!pass) {
                    break;
                }
            }
            if(pass) {
                earnedBadgeArray[mParsedMapData.mBadges.get(i).getType()] = 1;
            }
        }

        FixedSizeArray<Integer> newBadges = new FixedSizeArray<Integer>(3);

        for(int k = 0; k < earnedBadgeArray.length; k++) {
            if(earnedBadgeArray[k] > 0) {
                Boolean hasBadge = settings.getBoolean(mSaveId + CircuitConstants.SAVE_POSTFIX_BADGE+k, false);
                if(!hasBadge) {
                    editor.putBoolean(mSaveId + CircuitConstants.SAVE_POSTFIX_BADGE+k, true);
                    newBadges.add(k);
                }
            }
        }

        editor.commit();
        return newBadges;
    }

    /**
     * @param nC	node connection
     * @return		node that matches the node connection passed in
     */
    private Node getNodeFromConnection(NodeConnection nC) {
        Node result = null;
        if(nC.getK() == -1) {
            result = mNodes[nC.getI()][nC.getJ()];
        } else {
            result = mBorderNodes[nC.getK()];
        }
        return result;
    }

}
