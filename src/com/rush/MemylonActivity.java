package com.rush;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.*;

import java.util.Random;

public class MemylonActivity extends Activity {
	DrawThread mDrawThread;
	MainView mView;
	
	Bitmap mBgBitmap;
	Bitmap mCardBitmap;
	
	Rect mSrcRect = new Rect();
	Rect mDstRect = new Rect();
	
	String[] mCardNames;
	
	int[] mCards;
	Animation[] mCardAnims;
	Animation mTopAnim;
	
	int mFlippedCardIdx = -1;
	boolean mIsGameEnded = false;
	
	int mNumCardsH;
	int mNumCardsW;

	private static final int CARD_W = 64;
	private static final int CARD_H = 60;
	private static final int CARDS_IN_ATLAS_ROW = 12;
	private static final int CARDS_NUM_VARIATIONS = 105;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//  game window setup
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		//  load resources
		Resources res = getResources();
		mCardNames = res.getStringArray(R.array.card_names);
		assert(mCardNames.length == CARDS_NUM_VARIATIONS);
		mBgBitmap = BitmapFactory.decodeResource(res, R.drawable.background);
		mCardBitmap = BitmapFactory.decodeResource(res, R.drawable.cards);
		
		mView = new MainView(this, null);
		setContentView(mView);
		mDrawThread = new DrawThread(mView.getHolder());
		
		//startGame(5, 4);
        startGame(4, 3);
	}
	
	void startDrawingThread() {
		mDrawThread.setRunning(true);
		mDrawThread.start();
	}
	
	void stopDrawingThread() {
		mDrawThread.setRunning(false);
		while (true) {
			try {
				mDrawThread.join();
				break;
			} catch (InterruptedException e) {
				// keep trying to stop the draw thread
			}
		}
	}
	
	/**
	 * Randomly shuffles an int array using Fisher-Yates algorithm
	 */
	public static void shuffleArray(int[] arr, Random generator) {
		for (int i = arr.length - 1; i > 0; i--) {
			int k = generator.nextInt(i + 1);
			int val = arr[k];
			arr[k] = arr[i];
			arr[i] = val;
		}
	}
	
	void startGame(int numCardsW, int numCardsH) {
		mNumCardsW = numCardsW;
		mNumCardsH = numCardsH;
		
		Random randGen = new Random();
		// select random subset of available card variations
		int variations[] = new int[CARDS_NUM_VARIATIONS];
		for (int i = 0; i < CARDS_NUM_VARIATIONS; i++) {
			variations[i] = i + 1;
		}
		shuffleArray(variations, randGen);
		
		// create and shuffle the cards
		int numCells = mNumCardsW * mNumCardsH;
		mCards = new int[numCells];
		mCardAnims = new CardAnimation[numCells];
		int numCards = numCells / 2;

		for (int i = 0; i < numCards; i++) {
			mCards[i * 2] = mCards[i * 2 + 1] = -variations[i];
		}
		shuffleArray(mCards, randGen);
	}
	
	public void drawCard(Canvas canvas, int cardIdx, int cardID,
			float cardScale, Paint paint) {
		if (cardID == 0) {
			return;
		}
		int cardGlyphID = cardID > 0 ? cardID : 0;
		int cardI = cardIdx % mNumCardsW;
		int cardJ = cardIdx / mNumCardsW;
		int cardW = canvas.getWidth() / mNumCardsW;
		int cardH = canvas.getHeight() / mNumCardsH;
		int cardSW = (int) (((float) cardW) * cardScale);
		mDstRect.offsetTo(cardI * cardW, cardJ * cardH);
		mDstRect.left += (cardW - cardSW) / 2;
		mDstRect.right = mDstRect.left + cardSW;
		mSrcRect.offsetTo((cardGlyphID % CARDS_IN_ATLAS_ROW) * CARD_W,
				(cardGlyphID / CARDS_IN_ATLAS_ROW) * CARD_H);
		canvas.drawBitmap(mCardBitmap, mSrcRect, mDstRect, paint);
	}
	
	public void update(float dt) {
		int numCards = mCards.length;
		for (int i = 0; i < numCards; i++) {
			Animation anim = mCardAnims[i];
			if (anim != null) {
				anim.update(dt);
				if (!anim.isPlaying()) {
					mCardAnims[i] = anim.getNextAnim();
				}
			}
		}
		if (mTopAnim != null) {
			mTopAnim.update(dt);
			if (!mTopAnim.isPlaying()) {
				mTopAnim = mTopAnim.getNextAnim();
			}
		}
	}

	public class MainView extends SurfaceView implements SurfaceHolder.Callback {
		public MainView(Context context, AttributeSet attrs) {
			super(context, attrs);
			getHolder().addCallback(this);
		}
		
		@Override
		public void onDraw(Canvas canvas) {
			mSrcRect.set(0, 0, mBgBitmap.getWidth(), mBgBitmap.getHeight());
			mDstRect.set(0, 0, getWidth(), getHeight());
			canvas.drawBitmap(mBgBitmap, mSrcRect, mDstRect, null);
			
			int cardW = getWidth() / mNumCardsW;
			int cardH = getHeight() / mNumCardsH;
			mDstRect.set(0, 0, cardW, cardH);
			mSrcRect.set(0, 0, CARD_W, CARD_H);
			
			int numCards = mCards.length;
			for (int i = 0; i < numCards; i++) {
				if (mCardAnims[i] == null) {
					drawCard(canvas, i, mCards[i], 1.0f, null);					
				} else {
					mCardAnims[i].draw(canvas);
				}
			}
			if (mTopAnim != null) {
				mTopAnim.draw(canvas);
			}
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (mIsGameEnded) {
				mIsGameEnded = false;
				startGame(mNumCardsW + 1, mNumCardsH + 1);
				return true;
			}
			int touchX = (int) event.getX();
			int touchY = (int) event.getY();
			int cardScreenW = getWidth() / mNumCardsW;
			int cardScreenH = getHeight() / mNumCardsH;
			int cardI = touchX / cardScreenW;
			int cardJ = touchY / cardScreenH;
			int cardIdx = cardJ * mNumCardsW + cardI;
			int cardID = mCards[cardIdx];
			if (mCardAnims[cardIdx] == null && cardID != 0 && cardIdx != mFlippedCardIdx)
			{
				CardAnimation nextAnim = null;
				if (mFlippedCardIdx == -1) {
					//  no other cards are flipped
					mFlippedCardIdx = cardIdx;
				} else {
					int flippedCardID = mCards[mFlippedCardIdx];
					if (Math.abs(cardID) == Math.abs(flippedCardID)) {	
						mCards[mFlippedCardIdx] = mCards[cardIdx] = 0;
						
						//  found a match - dissolve the cards
						mCardAnims[mFlippedCardIdx] = 
								new IdleCardAnimation(mFlippedCardIdx, flippedCardID, 
								new IdleCardAnimation(mFlippedCardIdx, flippedCardID, 
								new DissolveCardAnimation(mFlippedCardIdx, flippedCardID, null)));
						nextAnim = new DissolveCardAnimation(cardIdx, cardID, null);
						
						String caption = mCardNames[Math.abs(cardID) - 1];
						mTopAnim = new TextAnimation(caption, 0, getHeight(), 2.0f, null);
						
						//  check if all the cards are flipped
						boolean bAllFlipped = true;
						for (int i = mCards.length - 1; i >= 0 ; i--) {
							if (mCards[i] != 0) {
								bAllFlipped = false;
								break;
							}
						}
						if (bAllFlipped) {
							//mIsGameEnded = true;
							TextAnimation endCaption = new TextAnimation(caption, 30, 100, 20.0f, null);
							endCaption.setColor(0xFFAAAABB);
							mTopAnim = 
								new TextAnimation("This is it.", 30, 200, 2.0f,
								new TextAnimation("The language wars", 30, 100, 2.0f,
								new TextAnimation("ARE OVER.", 30, 200, 3.0f,
								new TextAnimation("...and the winner is", 2, 50, 7.0f, endCaption))));
						}
						
					} else {
						//  flip both cards back
						mCardAnims[mFlippedCardIdx] = 
								new IdleCardAnimation(mFlippedCardIdx, flippedCardID,
								new IdleCardAnimation(mFlippedCardIdx, flippedCardID,
								new FlipCardAnimation(mFlippedCardIdx, flippedCardID, null)));
						nextAnim = new FlipCardAnimation(cardIdx, -cardID, null);
						mCards[mFlippedCardIdx] = -mCards[mFlippedCardIdx];
						mCards[cardIdx] = -mCards[cardIdx];
					}
					mFlippedCardIdx = -1;
				}
				mCardAnims[cardIdx] = 
					new FlipCardAnimation(cardIdx, cardID, 
					new IdleCardAnimation(cardIdx, cardID, nextAnim));
				mCards[cardIdx] = -mCards[cardIdx];
			}
			return true;
		}

		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			startDrawingThread();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			stopDrawingThread();
		}
	}

	class DrawThread extends Thread {
		private SurfaceHolder mSurfaceHolder;
		private boolean mIsRunning = false;
		private long mLastTime;

		public DrawThread(SurfaceHolder surfaceHolder) {
			mSurfaceHolder = surfaceHolder;
		}

		public void setRunning(boolean isRunning) {
			mIsRunning = isRunning;
		}

		@Override
		public void run() {
			Canvas c;
			mLastTime = SystemClock.uptimeMillis();
			while (mIsRunning) {
				final long time = SystemClock.uptimeMillis();
                final float timeDelta = ((float)(time - mLastTime)) * 0.001f;
                mLastTime = time;
				update(timeDelta);
                c = null;
				try {
					c = mSurfaceHolder.lockCanvas(null);
					synchronized (mSurfaceHolder) {
						mView.onDraw(c);
					}
				} finally {
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}
	}

    /**
     * Base class for the game animations
     */
	class Animation {
		protected float mDuration = 0.5f;
		protected float mCurTime = 0.0f;
		protected boolean mIsPlaying = false;
		
		protected Animation mNextAnim = null;
		
		public Animation(Animation nextAnim) {
			mNextAnim = nextAnim;
			start();
		}
		
		public void start() {
			mCurTime = 0.0f;
			mIsPlaying = true;
		}
		
		public Animation getNextAnim() {
			return mNextAnim;
		}
		
		public void setDuration(float duration) {
			mDuration = duration;
		}
		
		public void update(double dt) {
			if (mIsPlaying) {
				mCurTime += dt;
				if (mCurTime > mDuration) {
					mIsPlaying = false;
				}
			}
		}
		
		public boolean isPlaying() {
			return mIsPlaying;
		}
		
		public void draw(Canvas canvas) {
		}
	}

    /**
     * Base class for card animations
     */
    class CardAnimation extends Animation {
		protected int mCardIdx = -1;
		protected int mCardID = -1;
		
		public CardAnimation(int cardIdx, int cardID, Animation nextAnim) {
			super(nextAnim);
			mCardIdx = cardIdx;
			mCardID = cardID;
		}
	}

    /**
     * The "idle" card animation (draws the card in static position)
     */
    class IdleCardAnimation extends CardAnimation {
		public IdleCardAnimation(int cardIdx, int cardID, CardAnimation nextAnim) {
			super(cardIdx, cardID, nextAnim);
		}

		@Override
		public void draw(Canvas canvas) {
			int cardID = Math.abs(mCardID);
			drawCard(canvas, mCardIdx, cardID, 1.0f, null);
		}
	}

    /**
     * Card flipping animation
     */
    class FlipCardAnimation extends CardAnimation {
		public FlipCardAnimation(int cardIdx, int cardID, CardAnimation nextAnim) {
			super(cardIdx, cardID, nextAnim);
		}

		@Override
		public void draw(Canvas canvas) {
			double scale = Math.cos(Math.PI*mCurTime/mDuration);
			int cardID = (scale > 0) ? mCardID : -mCardID;
			drawCard(canvas, mCardIdx, cardID, (float) Math.abs(scale), null);
		}
	}

    /**
     * Card dissolving animation
     */
    class DissolveCardAnimation extends CardAnimation {
		private Paint mPaint = new Paint();
		public DissolveCardAnimation(int cardIdx, int cardID, CardAnimation nextAnim) {
			super(cardIdx, cardID, nextAnim);
			mDuration = 1.0f;
		}
		@Override
		public void draw(Canvas canvas) {
			int cardID = Math.abs(mCardID);
			mPaint.setAlpha((int) (255.0f*(1 - mCurTime/mDuration)));
			drawCard(canvas, mCardIdx, cardID, 1.0f, mPaint);
		}
	}

    /**
     * Flying colored text animation
     */
    class TextAnimation extends Animation {
		private int mStartTextSize = 30;
		private int mEndTextSize = 300;
		private String mCaption = "";
		
		private Paint mPaint = new Paint();
		public TextAnimation(String caption, int startTextSize, int endTextSize, float duration, Animation nextAnim) {
			super(nextAnim);
			mDuration = duration;
			mCaption = caption;
			mEndTextSize = endTextSize;
			mStartTextSize = startTextSize;
			mPaint.setColor(0xFF2222AA);
			mPaint.setTypeface(Typeface.DEFAULT_BOLD);
			mPaint.setAntiAlias(true);
			mPaint.setTextAlign(Align.CENTER);
		}
		
		void setColor(int color) {
			mPaint.setColor(color);
		}
		@Override
		public void draw(Canvas canvas) {
			float textSize = mStartTextSize + (mEndTextSize - mStartTextSize)*mCurTime/mDuration;
			mPaint.setAlpha((int) (255.0f*(1 - mCurTime/mDuration)));
			mPaint.setTextSize(textSize);
			canvas.drawText(mCaption, canvas.getWidth()/2, (canvas.getHeight()+ textSize)/2 , mPaint);
		}
	}
}