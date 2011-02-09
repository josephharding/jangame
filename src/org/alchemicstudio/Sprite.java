package org.alchemicstudio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Comparator;
import javax.microedition.khronos.opengles.GL10;

import org.alchemicstudio.Texture;

import android.util.Log;

public class Sprite {
	
	public float xOffset;
	public float yOffset;
	public float rotation;
	public boolean cameraRelative;
	public int currentTextureIndex;
	public float xScale, yScale;
	
	private Texture[] mTexture;
	private int textureIndex;
	private int mPriority;
	private FloatBuffer vertexBuffer;
	private FloatBuffer textureBuffer;
	private ByteBuffer indexBuffer;
	
	private float widthScale;
	private float heightScale;
	private float opacity;

	public Sprite(int priority) {
		byte[] indices = { 1, 0, 2, 3 };

		float[] vertices = {
				-1.0f, -1.0f, // 0 bottom left
				-1.0f, 1.0f, // 1 top left
				1.0f, 1.0f, // 2 top right
				1.0f, -1.0f, // 3 bottom right
		};

		float[] texture = { 
				1.0f, 1.0f, //
				1.0f, 0.0f, //
				0.0f, 0.0f, //
				0.0f, 1.0f, //
		};
		
		mTexture = new Texture[3];
		currentTextureIndex = 0;
		opacity = 1.0f;
		mPriority = priority;
		xScale = 1.0f;
		yScale = 1.0f;
		
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(vertices.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		vertexBuffer = byteBuf.asFloatBuffer();
		vertexBuffer.put(vertices);
		vertexBuffer.position(0);

		byteBuf = ByteBuffer.allocateDirect(texture.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		textureBuffer = byteBuf.asFloatBuffer();
		textureBuffer.put(texture);
		textureBuffer.position(0);

		indexBuffer = ByteBuffer.allocateDirect(indices.length);
		indexBuffer.put(indices);
		indexBuffer.position(0);
	}
	
	public void setPosition(float x, float y) {
		xOffset = x;
		yOffset = -y;
	}
	
	public void setOpacity(float value) {
		opacity = value;
	}
	
	public void setScale(float x, float y) {
		xScale = x;
		yScale = y;
	}
	
	public void setRotation(float angle) {
		rotation = (float)(180 * (angle / Math.PI));
	}
	
	public Vector2 getPosition() {
		return new Vector2(xOffset, yOffset);
	}

	public int getPriority() {
		return mPriority;
	}

	public void setTexture(Texture texture, float width, float height) {
		mTexture[textureIndex] = texture;
		widthScale = width;
		heightScale = height;
		textureIndex++;
	}

	public void draw(GL10 gl, float angle, float x, float y) {
		gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexture[currentTextureIndex].name);
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
		
		gl.glColor4f(opacity, opacity, opacity, opacity);
		gl.glTranslatef(x, y, 0);
		gl.glRotatef(rotation, 0, 0, 1);
		gl.glScalef(widthScale * xScale, heightScale * yScale, 0);
		
		//gl.glTranslatef(widthScale/2, heightScale/2, 0);
		
		gl.glDrawElements(GL10.GL_TRIANGLE_STRIP, 4, GL10.GL_UNSIGNED_BYTE, indexBuffer);
		gl.glLoadIdentity();
		
		gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
	}

	public final static class PriorityComparator implements Comparator<Sprite> {

		@Override
		public int compare(Sprite s1, Sprite s2) {
			int s1P = s1.getPriority();
			int s2P = s2.getPriority();

			if (s1P > s2P)
				return 1;
			else if (s1P < s2P)
				return -1;
			else
				return 0;
		}
	}
}