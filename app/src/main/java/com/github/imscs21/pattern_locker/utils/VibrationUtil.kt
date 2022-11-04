package com.github.imscs21.pattern_locker.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import androidx.annotation.RequiresApi


/**
 * This class helps to ease to use device vibration
 * @property context android context class
 * @since 2022-11-1
 */
class VibrationUtil {
    /**
     * Basic class for Behavior of Vibration
     */
    protected open abstract class BaseVibrationUnit{
        /**
         * mainly used for initializing vibrationmanager
         */
        private val context:Context

        /**
         * @param context [Context]
         */
        constructor(context:Context){
            this.context = context
        }

        /**
         * device vibrate function for different api methods
         *  @param milliseconds how long vibrate by millisecond unit
         *  @param amplitude strength of vibration if available
         */
        public abstract fun vibrate(milliseconds:Long,amplitude:Int)

        /**
         * pre-defined behavior when clicking
         */
        public abstract fun vibrateAsClick()

    }

    @RequiresApi(Build.VERSION_CODES.S)
    protected inner class Api31VibrationUnit:BaseVibrationUnit{

        public val vibratorManager:VibratorManager

        constructor():super(context){
            vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        }
        /**
         * device vibrate function for different api methods
         * @param milliseconds how long vibrate by millisecond unit
         *  @param amplitude strength of vibration if available
         */
        override fun vibrate(milliseconds:Long,amplitude:Int) {
            vibratorManager.vibrate(CombinedVibration.createParallel(VibrationEffect.createOneShot(milliseconds,amplitude)))
        }

        /**
         * pre-defined behavior when clicking
         */
        override fun vibrateAsClick() {
            vibratorManager.vibrate(CombinedVibration.createParallel(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)))
        }
    }

    protected inner class PreviousVibrationUnit:BaseVibrationUnit{
        
        public val vibratorManager:Vibrator
        
        constructor():super(context){
            vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        /**
         * device vibrate function for different api methods
         * @param milliseconds how long vibrate by millisecond unit
         *  @param amplitude strength of vibration if available
         */
        override fun vibrate(milliseconds:Long,amplitude:Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibratorManager.vibrate(VibrationEffect.createOneShot(milliseconds,amplitude))
            }else {
                vibratorManager.vibrate(milliseconds)
            }
        }

        /**
         * pre-defined behavior when clicking
         */
        override fun vibrateAsClick() {
            vibrate(60,64)

        }

    }

    /**
     * mainly used for initializing vibrationmanager
     */
    protected val context:Context

    /**
     * vibration behavior instance for different api method
     */
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

    /**
     * function that doing device vibratation with different api methods capability
     * @param milliseconds how long vibrate by millisecond unit
     * @param amplitude strength of vibration if available
     */
    public fun vibrate(milliseconds:Long,amplitude:Int){
        vibrationUnit?.vibrate(milliseconds, amplitude)
    }

    /**
     * pre-defined behavior of device vibration function when clicking something in app with different api methods capability
     */
    public fun vibrateAsClick(){
        vibrationUnit?.vibrateAsClick()
    }

}