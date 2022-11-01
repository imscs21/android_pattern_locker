package com.github.imscs21.pattern_locker.utils

import android.content.Context
import android.os.*
import androidx.annotation.RequiresApi

class VibrationUtil {
    protected open abstract class BaseVibrationUnit{
        private val context:Context
        constructor(context:Context){
            this.context = context
        }
        public abstract fun vibrate(milliseconds:Long,amplitude:Int)
        public abstract fun vibrateAsClick()

    }
    @RequiresApi(Build.VERSION_CODES.S)
    protected inner class Api31VibrationUnit:BaseVibrationUnit{
        public val vibratorManager:VibratorManager
        constructor():super(context){
            vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        }

        override fun vibrate(milliseconds:Long,amplitude:Int) {
            vibratorManager.vibrate(CombinedVibration.createParallel(VibrationEffect.createOneShot(milliseconds,amplitude)))
        }

        override fun vibrateAsClick() {
            vibratorManager.vibrate(CombinedVibration.createParallel(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)))
        }
    }
    protected inner class PreviousVibrationUnit:BaseVibrationUnit{
        public val vibratorManager:Vibrator
        constructor():super(context){
            vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        override fun vibrate(milliseconds:Long,amplitude:Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibratorManager.vibrate(VibrationEffect.createOneShot(milliseconds,amplitude))
            }else {
                vibratorManager.vibrate(milliseconds)
            }
        }

        override fun vibrateAsClick() {
            vibrate(60,64)

        }

    }
    protected val context:Context
    protected val vibrationUnit:BaseVibrationUnit

    constructor( context: Context){
        this.context = context
        vibrationUnit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             Api31VibrationUnit()
        }
        else{
            PreviousVibrationUnit()
        }


    }
    public fun vibrate(milliseconds:Long,amplitude:Int){
        vibrationUnit?.vibrate(milliseconds, amplitude)
    }
    public fun vibrateAsClick(){
        vibrationUnit?.vibrateAsClick()
    }

}