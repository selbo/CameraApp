package com.example.levyy.camera;

import android.view.View;
import android.graphics.Canvas;
import android.content.Context;

class DrawOnTop extends View {
	public DrawOnTop(Context context) {
		super(context);

	}
	@Override
	protected void onDraw(Canvas canvas) {
		//if (bitmap != null) {
		//	canvas.drawBitmap(bitmap, 0, 0, null);
		//}
		super.onDraw(canvas);
	}

}
