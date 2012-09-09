package org.alchemicstudio;

import android.util.Log;

public class Grid extends BaseObject {
	
	/** the number of track segments for each node */
	public final static int CONNECTIONS_PER_NODE = 4;
	
	/** the array index of the track segment used for visualizing where your current track piece will connect */
	private final static int POINTER_TRACK_SEGMENT_INDEX = 0;
	
	/** number of sparks to be released on touch */
	private final static int SPARKS_PER_TOUCH = 5;
	
	/** max number of node connections that can be stored in the chain */
	private final static int MAX_CHAIN_LENGTH = 30;
	
	/** TODO - what? */
	private final static float TRACK_SCALAR = (7.0f / 28.0f);
	
	/** node size */
	private final static float NODE_DIMENSION = 32.0f;
	
	/** dragable track offset x */
	private final static float TRACK_OFFSET_X = 8.0f;
	
	/** dragable track offset y */
	private final static float TRACK_OFFSET_Y = 14.0f;
	
	/** the amount to hide the border nodes beyond the screen by 30.0f */
	private final static float BORDER_NODE_OFFSET = 30.0f;

	/** current number of track segments we have */
	private int mNumTrackSegments = 1;
	
	/** maximum number of track segments we'll have to allocate, based on size of grid passed in */
	private int mMaxTrackSegments;
	
	/** the base unit that track segments connect between */
	private Node[][] mNodes;
	
	/** the base unit that surrounds the game board */
	private Node[] mBorderNodes;
	
	/** the array of track segments */
	private TrackSegment[] mTrackSegments;

	/** the space between nodes */
	private int mSpacing;
	
	/** height in nodes of this grid */
	private int mHeight;
	
	/** width in nodes of this grid */
	private int mWidth;

	/** the i index for the node being used as the source for the current track segment */
	private int mCurrentTrackSourceNodeI;
	
	/** the j index for the node being used as the source for the current track segment */
	private int mCurrentTrackSourceNodeJ;

	/** the screen width */
	private float mScreenWidth;
	
	/** the screen height */
	private float mScreenHeight;
	
	/** how much buffer space we have in the x direction */
	private float mSideBufferX;
	
	/** how much buffer space we have in the y direction */
	private float mSideBufferY;
	
	/** array that tracks setting of node connection preferences */
	private NodeConnection[] mNodeChain;
	
	/** index for the node chain array */
	private int mNodeChainIndex = 0;
	
	/** reference to effects overlay */
	private DrawableOverlay mOverlay;
	
	
	public Grid(ParsedMapData dataSet, float screenWidth, float screenHeight, DrawableOverlay overlay) {
		mHeight = dataSet.mMapHeight;
		mWidth = dataSet.mMapWidth;
		mSpacing = dataSet.mMapSpacing;

		mMaxTrackSegments = (mWidth * mHeight) * CONNECTIONS_PER_NODE;

		mScreenWidth = screenWidth;
		mScreenHeight = screenHeight;
		
		mOverlay = overlay;

		mNodes = new Node[mWidth][mHeight];
		mBorderNodes = new Node[2*mWidth + 2*mHeight];
		mNodeChain = new NodeConnection[MAX_CHAIN_LENGTH];
		mTrackSegments = new TrackSegment[mMaxTrackSegments];
		// this has to happen before createTrackSegmentBetweenPoints
		for (int k = 0; k < mMaxTrackSegments; k++) {
			mTrackSegments[k] = new TrackSegment();
			mTrackSegments[k].mSprite.setScale(0.0f, 0.0f);
		}
		mTrackSegments[POINTER_TRACK_SEGMENT_INDEX].setInUse(true);

		mSideBufferX = (screenWidth - ((mWidth*NODE_DIMENSION) + ((mWidth-1)*mSpacing)))/2;
		mSideBufferY = (screenHeight - ((mHeight*NODE_DIMENSION) + ((mHeight-1)*mSpacing)))/2;
		
		int nodeDataLen = dataSet.mNodes.getCount();
		
		// create nodes from data
		for (int k = 0; k < nodeDataLen; k++) {
			int tempI = dataSet.mNodes.get(k).i;
			int tempJ = dataSet.mNodes.get(k).j;
			int tempK = dataSet.mNodes.get(k).k;

			int maxSpeedLimit = dataSet.mNodes.get(k).maxSpeed;
			int minSpeedLimit = dataSet.mNodes.get(k).minSpeed;
			int keyId = dataSet.mNodes.get(k).keyId;
			String type = dataSet.mNodes.get(k).type;

			if(type.equals(Node.NODE_TYPE_EMPTY)) {
				mNodes[tempI][tempJ] = null;
			} else if(tempK == -1) {
				Vector2 nodePosition = new Vector2(mSideBufferX + ((mSpacing + NODE_DIMENSION) * tempI), mSideBufferY + ((mSpacing + NODE_DIMENSION) * tempJ));
				mNodes[tempI][tempJ] = new Node(tempI, tempJ, tempK, nodePosition, maxSpeedLimit, minSpeedLimit, keyId, type);
			}
		}
		
		// create border nodes from data
		Vector2[] borderNodePositions = getBorderNodePositions();
		for (int k = 0; k < nodeDataLen; k++) {
			int tempI = dataSet.mNodes.get(k).i;
			int tempJ = dataSet.mNodes.get(k).j;
			int tempK = dataSet.mNodes.get(k).k;
			String type = dataSet.mNodes.get(k).type;
			if(tempK != -1) {
				Vector2 nodePosition = borderNodePositions[tempK];
				mBorderNodes[tempK] = new Node(tempI, tempJ, tempK, nodePosition, 0, 0, 0, type);
			}
		}
		
		// create preconnections, must wait until previous loop is complete
		// NOTE - for this block we assume no border node has a preconnection defined
		for (int k = 0; k < nodeDataLen; k++) {
			int tempI = dataSet.mNodes.get(k).i;
			int tempJ = dataSet.mNodes.get(k).j;
			int tempK = dataSet.mNodes.get(k).k;
			for(int p = 0; p < dataSet.mNodes.get(k).mPreconnections.getCount(); p++) {
				int preconnectionIIndex = dataSet.mNodes.get(k).mPreconnections.get(p).i;
				int preconnectionJIndex = dataSet.mNodes.get(k).mPreconnections.get(p).j;
				int preconnectionKIndex = dataSet.mNodes.get(k).mPreconnections.get(p).k;
				if(preconnectionKIndex == -1) {
					conditionallyCreateConnectionBetweenNodes(tempI, tempJ, preconnectionIIndex, preconnectionJIndex, true);
				} else {
					conditionallyCreateConnectionWithBorder(tempI, tempJ, tempK, preconnectionKIndex, true);
				}
			}
		}
	}
	
	/**
	 * Start at node[0][0] and place the first border node directly to the north, then proceed heading west,
	 * then turn south, then east, then north again placing the border nodes just off the screen but even with
	 * the game board nodes
	 * 
	 * @return
	 */
	private Vector2[] getBorderNodePositions() {
		Vector2[] result = new Vector2[mBorderNodes.length];
		int index = 0;
		// new Vector2(mSideBufferX + ((mSpacing + NODE_DIMENSION) * i), mSideBufferY + ((mSpacing + NODE_DIMENSION) * i));
		// North
		for( int i = 0; i < mWidth; i++) {
			float x = mSideBufferX + ((mSpacing + NODE_DIMENSION) * i);
			float y = -BORDER_NODE_OFFSET;
			result[index] = new Vector2(x, y);
			index++;
		}
		// East
		for( int i = 0; i < mHeight; i++) {
			float x = mScreenWidth + BORDER_NODE_OFFSET;
			float y = mSideBufferY + ((mSpacing + NODE_DIMENSION) * i);
			result[index] = new Vector2(x, y);
			index++;
		}
		// South
		for( int i = mWidth-1; i > -1; i--) {
			float x = mSideBufferX + ((mSpacing + NODE_DIMENSION) * i);
			float y = mScreenHeight + BORDER_NODE_OFFSET;
			result[index] = new Vector2(x, y);
			index++;
		}
		// West
		for( int i = mHeight-1; i > -1; i--) {
			float x = -BORDER_NODE_OFFSET; 
			float y = mSideBufferY + ((mSpacing + NODE_DIMENSION) * i);
			result[index] = new Vector2(x, y);
			index++;
		}
		return result;
	}
	
	/**
	 * creates a segment of track between a game board node and a border node
	 * 
	 * @param ai
	 * @param aj
	 * @param borderIndex
	 */
	private void conditionallyCreateConnectionWithBorder(int ai, int aj, int ak, int borderIndex, boolean fixed) {
		if (mNumTrackSegments < mMaxTrackSegments) {
			
			float ax = mNodes[ai][aj].getPosition().x + TRACK_OFFSET_X;
			float ay = mNodes[ai][aj].getPosition().y - TRACK_OFFSET_Y;
			
			float bx = mBorderNodes[borderIndex].getPosition().x + TRACK_OFFSET_X;
			float by = mBorderNodes[borderIndex].getPosition().y - TRACK_OFFSET_Y;
			
			int trackID = createTrackSegmentBetweenPoints(ax, ay, bx, by);
			
			mNodes[ai][aj].setConnection(mBorderNodes[borderIndex].getI(), mBorderNodes[borderIndex].getJ(), borderIndex, trackID, fixed);
			mBorderNodes[borderIndex].setConnection(ai, aj, ak, trackID, fixed);
		}
	}

	/**
	 * create a segment of track between the two node indicies passed in, 
	 * 
	 * @param ai				node a, i index
	 * @param aj				node a, j index
	 * @param bi				node b, i index
	 * @param bj				node b, j index
	 */
	private void conditionallyCreateConnectionBetweenNodes(int ai, int aj, int bi, int bj, boolean fixed) {
		if(!mNodes[ai][aj].hasMaxConnections()) {
			if(!mNodes[bi][bj].hasMaxConnections()) {
				if(!connectionBetween(ai, aj, bi, bj)) {
					if (mNumTrackSegments < mMaxTrackSegments) {

						float ax = mNodes[ai][aj].getPosition().x + TRACK_OFFSET_X;
						float ay = mNodes[ai][aj].getPosition().y - TRACK_OFFSET_Y;

						float bx = mNodes[bi][bj].getPosition().x + TRACK_OFFSET_X;
						float by = mNodes[bi][bj].getPosition().y - TRACK_OFFSET_Y;

						int trackID = createTrackSegmentBetweenPoints(ax, ay, bx, by);

						mNodes[ai][aj].setConnection(bi,bj,-1,trackID, fixed);
						mNodes[bi][bj].setConnection(ai,aj,-1,trackID, fixed);

						Log.d("DEBUG", "Created connection between: ("+ai+","+aj+") and ("+bi+","+bj+")");
					} else {
						Log.d("DEBUG", "Failed to create a connection between: ("+ai+","+aj+") and ("+bi+","+bj+") - reached max track segments");
					}
				} else {
					Log.d("DEBUG", "Failed to create a connection between: ("+ai+","+aj+") and ("+bi+","+bj+") - a connection already existed");
				}
			} else {
				Log.d("DEBUG", "Failed to create a connection between: ("+ai+","+aj+") and ("+bi+","+bj+") - reached ("+bi+","+bj+")'s connection limit");
			}
		} else {
			Log.d("DEBUG", "Failed to create a connection between: ("+ai+","+aj+") and ("+bi+","+bj+") - reached ("+ai+","+aj+")'s connection limit");
		}
	}

	/**
	 * create a track segment between two points
	 * 
	 * @param ax
	 * @param ay
	 * @param bx
	 * @param by
	 * @return
	 */
	private int createTrackSegmentBetweenPoints(float ax, float ay, float bx, float by) {
		int result = -1;
		float distance = Math.abs(new Vector2(bx, by).distance(new Vector2(ax, ay)));
		
		double angle= Math.atan((by - ay)/(ax - bx)) - (Math.PI / 2);
		if((ax-bx) < 0) angle += Math.PI;

		// start at index 1 here as we use the segment at position 0 for the "pointer track"
		for (int i = 1; i < mMaxTrackSegments; i++) {
			if (mTrackSegments[i].getInUse() == false) {
				result = i;
				mTrackSegments[i].mSprite.setPosition(bx, by);
				mTrackSegments[i].mSprite.setScale(1.0f, distance * TRACK_SCALAR);
				mTrackSegments[i].mSprite.setRotation((float) angle);
				mTrackSegments[i].setInUse(true);
				mNumTrackSegments++;
				break;
			}
		}
		return result;
	}

	/**
	 * does a connection exist between these two nodes, indicies provided
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return		true if a connection exists between the two passed in nodes
	 */
	private boolean connectionBetween(int x1, int y1, int x2, int y2) {
		Boolean result = false;
		NodeConnection[] pArray = mNodes[x1][y1].getConnections();
		for (int i = 0; i < pArray.length; i++) {
			if (pArray[i].hasValueOf(x2, y2,-1)) {
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * handle touch up on different nodes
	 * 
	 * @param i		the i index for node being touched up
	 * @param j		the j index for node being touched up
	 */
	private void nodeReleased(int i, int j) {
		if(mNodes[i][j] != null) {
			Log.d("DEBUG", "Attempting connection between: " + connectionBetween(mCurrentTrackSourceNodeI, mCurrentTrackSourceNodeJ, i, j));
			// if the user clicked a released the same node
			if (mCurrentTrackSourceNodeI == i && mCurrentTrackSourceNodeJ == j) {
				deactivateNode(i, j);
				mOverlay.createParticle((int) (mNodes[i][j].getPosition().x + 16.0f), (int) (mNodes[i][j].getPosition().y - 16.0f), SPARKS_PER_TOUCH);
			}
			// if the user clicked a node and released on a node to the direct left or right, or above or below (just not diagonal)
			else if ((mCurrentTrackSourceNodeI == i && mCurrentTrackSourceNodeJ != j) || (mCurrentTrackSourceNodeI != i && mCurrentTrackSourceNodeJ == j)) {
				int startI = -1;
				int startJ = -1;
				int endI = -1;
				int endJ = -1;
				if (mCurrentTrackSourceNodeI == i) {
					int bigJ = Math.max(mCurrentTrackSourceNodeJ, j);
					int littleJ = Math.min(mCurrentTrackSourceNodeJ, j);
					int diff = bigJ - littleJ;
					for(int p = 0; p < diff+1; p++) {
						if(reachedDeadNode(i, littleJ+p) && startI != -1 && startJ != -1) {
							startI = -1;
							startJ = -1;
						} else if(eligibleToMakeConnection(i, littleJ+p)) {
							if(startI == -1 && startJ == -1) {
								startI = i;
								startJ = littleJ+p;
							} else {
								endI = i;
								endJ = littleJ+p;
							}
							if(endI != -1 && endJ != -1) {
								conditionallyCreateConnectionBetweenNodes(startI, startJ, endI, endJ, false);
								startI = endI;
								startJ = endJ;
								endI = -1;
								endJ = -1;
							}
						}
					}
				} else if (mCurrentTrackSourceNodeJ == j) {
					int bigI = Math.max(mCurrentTrackSourceNodeI, i);
					int littleI = Math.min(mCurrentTrackSourceNodeI, i);
					int diff = bigI - littleI;
					for(int p = 0; p < diff+1; p++) {
						if(reachedDeadNode(littleI+p, j) && startI != -1 && startJ != -1) {
							startI = -1;
							startJ = -1;
						} else if(eligibleToMakeConnection(littleI+p, j)) {
							if(startI == -1 && startJ == -1) {
								startI = littleI+p;
								startJ = j;
							} else {
								endI = littleI+p;
								endJ = j;
							}
							if(endI != -1 && endJ != -1) {
								conditionallyCreateConnectionBetweenNodes(startI, startJ, endI, endJ, false);
								startI = endI;
								startJ = endJ;
								endI = -1;
								endJ = -1;
							}
						}
					}
				}
			}
		}
	}
	
	private boolean eligibleToMakeConnection(int i, int j) {
		boolean result = false;
		if(mNodes[i][j] != null) {
			if(!mNodes[i][j].getType().equals(Node.NODE_TYPE_DEAD)) {
				result = true;
			}
		}
		return result;
	}
	
	private boolean reachedDeadNode(int i, int j) {
		boolean result = false;
		if(mNodes[i][j] != null) {
			if(mNodes[i][j].getType().equals(Node.NODE_TYPE_DEAD)) {
				result = true;
			}
		}
		return result;
	}
	
	/**
	 * deactivate the node specified by index, deactivate that node and remove all connections to it from other nodes
	 * 
	 * @param i
	 * @param j
	 */
	private void deactivateNode(int i, int j) {
		NodeConnection[] nodeConnections = mNodes[i][j].getConnections();
		for(int n = 0; n < nodeConnections.length; n++) {
			if(!nodeConnections[n].getFixed()) {
				mNodes[nodeConnections[n].getI()][nodeConnections[n].getJ()].removeConnection(i, j);
				mTrackSegments[nodeConnections[n].getTrackID()].setInUse(false);
			}
		}	
		mNodes[i][j].removeAllConnections();
	}

	/**
	 * translates a position (x,y) the user touches to the nearest node
	 * NOTE: CAN return a null node location
	 * 
	 * @param x		position user is touching, x
	 * @param y		position user is touching, y
	 * @return		Point that is closest to the above coordinates
	 */
	private NodeConnection determineNodeTouched(int x, int y) {
		//Log.d("DEBUG", "x, y : " + x + ", " + y);
		//Log.d("DEBUG", "mSpacing : " + mSpacing);
		//Log.d("DEBUG", "xSideBuffer, ySideBuffer : " + mSideBufferX + ", " + mSideBufferY);
		int xIndex = (int) Math.round((x - mSideBufferX) / (mSpacing + 32));
		int yIndex = (int) Math.round((y - mSideBufferY) / (mSpacing + 32));
		//Log.d("DEBUG", "xIndex, yIndex : " + xIndex + ", " + yIndex);

		if (xIndex < 0) {
			xIndex = 0;
		} else if (xIndex > mWidth - 1) {
			xIndex = mWidth - 1;
		}
		
		if (yIndex < 0) {
			yIndex = 0;
		} else if (yIndex > mHeight - 1) {
			yIndex = mHeight - 1;
		}
		
		return new NodeConnection(xIndex, yIndex, -1);
	}
	
	/**
	 * updates the position of the current track being laid
	 * 
	 * @param x		the position of the user's finger, x
	 * @param y		the position of the user's finger, y
	 */
	public void updateTrackDrag(int x, int y) {
		Vector2 dragPoint = new Vector2(x, y);
		Node tempNode = mNodes[mCurrentTrackSourceNodeI][mCurrentTrackSourceNodeJ];

		//Log.d("DEBUG", "current track i, j: " + mCurrentTrackSourceNodeI + ", " + mCurrentTrackSourceNodeJ);
		
		float trackOriginX = tempNode.getPosition().x + TRACK_OFFSET_X;
		float trackOriginY = tempNode.getPosition().y - TRACK_OFFSET_Y;
		
		double angle= Math.atan((dragPoint.y - trackOriginY)/(trackOriginX - dragPoint.x)) - (Math.PI / 2);
		//.02 tolerance stops the angle from exploding to infinity and the track switching signs
		if((dragPoint.x - trackOriginX) <= .02) angle += Math.PI;

		float distance = dragPoint.distance(new Vector2(trackOriginX, trackOriginY));

		mTrackSegments[POINTER_TRACK_SEGMENT_INDEX].mSprite.setPosition(trackOriginX, trackOriginY);
		mTrackSegments[POINTER_TRACK_SEGMENT_INDEX].mSprite.setScale(1.0f, distance * TRACK_SCALAR);
		mTrackSegments[POINTER_TRACK_SEGMENT_INDEX].mSprite.setRotation((float) angle);
	}
	
	/**
	 * set the node pressed as the current track source node
	 * 
	 * @param x		position user touched, x
	 * @param y		position user touched, y
	 */
	public void checkNodePress(int x, int y) {
		NodeConnection node = determineNodeTouched(x, y);
		if(mNodes[node.getI()][node.getJ()] != null) {
			mCurrentTrackSourceNodeI = node.getI();
			mCurrentTrackSourceNodeJ = node.getJ();
			Log.d("DEBUG", "You pressed node: (" + mCurrentTrackSourceNodeI + ", " + mCurrentTrackSourceNodeJ + ")");
		}
	}
	
	/**
	 * set the user removed his finger from to 'released'
	 * 
	 * @param x
	 * @param y
	 */
	public void checkNodeRelease(int x, int y) {
		NodeConnection node = determineNodeTouched(x, y);
		mTrackSegments[POINTER_TRACK_SEGMENT_INDEX].mSprite.setScale(0.0f, 0.0f);
		nodeReleased(node.getI(),node.getJ());
	}
	
	/**
	 * @return	the nodes of the grid
	 */
	public Node[][] getNodes() {
		return mNodes;
	}
	
	/**
	 * @return	the nodes around the border
	 */
	public Node[] getBorderNodes() {
		return mBorderNodes;
	}
	
	/**
	 * begin linking together a 'preferred connection' route that will be used when resolving
	 * which path to take for the spark as it travels through the nodes
	 * 
	 * @param x
	 * @param y
	 */
	public void startTrackSwitchChain(int x, int y) {
		NodeConnection node = determineNodeTouched(x, y);
		if(mNodes[node.getI()][node.getJ()] != null) {
			mNodeChain[mNodeChainIndex] = node;
			mNodeChainIndex++;
		}
	}
	
	private boolean eligibleToCreatePreferredConnection(NodeConnection nodeOrigin, NodeConnection nodeChild) {
		boolean result = false;
		if(mNodes[nodeChild.getI()][nodeChild.getJ()].getConnections().length == Node.PREFERRED_CONNECTION_REQ_NUM) {
			if(nodeOrigin.getI() == nodeChild.getI()) {
				int difference = nodeChild.getJ() - nodeOrigin.getJ();
				if(!mNodes[nodeChild.getI()][nodeChild.getJ()].hasConnectionTo(nodeChild.getI(), nodeChild.getJ()+difference)) {
					result = true;
				}
			} else {
				int difference = nodeChild.getI() - nodeOrigin.getI();
				if(!mNodes[nodeChild.getI()][nodeChild.getJ()].hasConnectionTo(nodeChild.getI()+difference, nodeChild.getJ())) {
					result = true;
				}
			}
		}
		return result;
	}
	
	/**
	 * cease to grow the preferred connection array and get the preferred connections
	 * 
	 */
	public void stopTrackSwitchChain() {
		for(int i = 1; i < mNodeChain.length-1; i++) {
			if(mNodeChain[i-1] != null && mNodeChain[i+1] != null) {
				if(eligibleToCreatePreferredConnection(mNodeChain[i-1], mNodeChain[i])) {
					mNodes[mNodeChain[i].getI()][mNodeChain[i].getJ()].setPreferredConnection(mNodeChain[i-1], mNodeChain[i+1]);
				}
			}
			mNodeChain[i] = null;
		}
		mNodeChain[mNodeChain.length-1] = null;
		mNodeChainIndex=0;
	}
	
	/**
	 * add another link on the preferred connection array
	 * 
	 * @param x
	 * @param y
	 */
	public void growTrackSwitchChain(int x, int y) {	
		NodeConnection newNodeConnection = determineNodeTouched(x, y);
		if(mNodes[newNodeConnection.getI()][newNodeConnection.getJ()] != null) {
			if(mNodeChainIndex < MAX_CHAIN_LENGTH) {
				if(!containsConnection(mNodeChain, newNodeConnection)) {
					NodeConnection lastLink = mNodeChain[mNodeChainIndex-1];
					if(mNodes[lastLink.getI()][lastLink.getJ()].hasConnectionTo(newNodeConnection.getI(), newNodeConnection.getJ())) {
						mNodeChain[mNodeChainIndex] = newNodeConnection;
						mNodeChainIndex++;
					}
				}
			}
		}
	}
	
	/**
	 * does this chain already contain this node?
	 * 
	 * @param chain
	 * @param node
	 * @return
	 */
	private boolean containsConnection(NodeConnection[] chain, NodeConnection node) {
		boolean result = false;
		for(int i = 0; i < chain.length; i++) {
			if(chain[i] != null) {
				if(chain[i].getI() == node.getI() && chain[i].getJ() == node.getJ()) {
					result = true;
					break;
				}
			}
		}
		return result;
	}

	@Override
	public void update(long timeDelta) {	
		
		for(int w = 0; w < mNodes.length; w++) {
			for(int q = 0; q < mNodes[w].length; q++) {
				if(mNodes[w][q] != null) {
					mNodes[w][q].update(timeDelta);
				}
			}
		}
		
		/*
		//Uncomment this when you need to debug the border nodes, also remember to set the offset
		//BORDER_NODE_OFFSET = -50.0f seems to work
		  
		for(int q = 0; q < mBorderNodes.length; q++) {
			mBorderNodes[q].update(timeDelta);
		}
		*/
		
		for(int e = 0; e < mTrackSegments.length; e++) {
			mTrackSegments[e].update(timeDelta);
		}
	}
}
