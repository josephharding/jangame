package org.alchemicstudio;

import org.alchemicstudio.Sprite.PriorityComparator;

import android.util.Log;

public class RenderSystem extends BaseObject {

	private final static int DRAW_QUEUE_COUNT = 2;
	private final static int MAX_DRAWABLE_ELEMENTS = 400;
	
	private PriorityComparator priorityComparator = new PriorityComparator();
	private FixedSizeArray<Sprite> spriteList[] = new FixedSizeArray[DRAW_QUEUE_COUNT];
	private FixedSizeArray<TextBox> textBoxList[] = new FixedSizeArray[DRAW_QUEUE_COUNT];
	private int drawBufferIndex;
	private int writeBufferIndex;

	public RenderSystem() {
		super();
		for(int i = 0; i < DRAW_QUEUE_COUNT; i++) {
			spriteList[i] = new FixedSizeArray<Sprite>(MAX_DRAWABLE_ELEMENTS);
			textBoxList[i] = new FixedSizeArray<TextBox>(MAX_DRAWABLE_ELEMENTS);
			spriteList[i].setComparator(priorityComparator);
		}
		drawBufferIndex = 0;
		writeBufferIndex = 0;
	}
	
	public void scheduleForWrite(TextBox tBox) {
		if (tBox != null) {
			textBoxList[writeBufferIndex].add(tBox);
		}
	}


	public void scheduleForDraw(Sprite sprite) {
		if (sprite != null) {
			spriteList[drawBufferIndex].add(sprite);
		}
	}

	public void sendUpdates(GameRenderer renderer) {
		// ensures that the spriteArray contains sprites sorted by their
		// priorities, prevents background from being drawn over nodes
		spriteList[drawBufferIndex].sort(false);

		renderer.setDrawQuadQueue(spriteList[drawBufferIndex]);
		renderer.setTextBoxQueue(textBoxList[writeBufferIndex]);
		
		final int lastDrawQueue = (drawBufferIndex == 0) ? DRAW_QUEUE_COUNT - 1 : drawBufferIndex - 1;
		final int lastWriteQueue = (writeBufferIndex == 0) ? DRAW_QUEUE_COUNT - 1 : writeBufferIndex - 1;
		
		clearQueue(spriteList[lastDrawQueue]);
		clearQueue(textBoxList[lastWriteQueue]);
		
		drawBufferIndex = (drawBufferIndex + 1) % DRAW_QUEUE_COUNT;
		writeBufferIndex = (writeBufferIndex + 1) % DRAW_QUEUE_COUNT;
	}

	private void clearQueue(FixedSizeArray objects) {
		final int count = objects.getCount();
		for (int i = count - 1; i >= 0; i--) {
			objects.removeLast();
		}
	}
	
	/* Empties all draw queues and disconnects the game thread from the renderer. */
    public void emptyDrawQueues(GameRenderer renderer) {
        renderer.setDrawQuadQueue(null); 
        for (int x = 0; x < DRAW_QUEUE_COUNT; x++) {
            FixedSizeArray<Sprite> objects = spriteList[x];
            clearQueue(objects);
        }
    }
    
    public void emptyWriteQueues(GameRenderer renderer) {
        renderer.setTextBoxQueue(null); 
        for (int x = 0; x < DRAW_QUEUE_COUNT; x++) {
            FixedSizeArray<TextBox> objects = textBoxList[x];
            clearQueue(objects);
        }
    }
}
