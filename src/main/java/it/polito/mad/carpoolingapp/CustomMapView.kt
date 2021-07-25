package it.polito.mad.carpoolingapp

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import org.osmdroid.views.MapView

class CustomMapView(context: Context, attributeSet: AttributeSet) : MapView(context, attributeSet) {

    private var touchable = false

    fun setTouchable(bool: Boolean){
        touchable = bool
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_UP -> parent.requestDisallowInterceptTouchEvent(false)
            MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if(touchable)
            return super.onTouchEvent(event)
        return true
    }
}