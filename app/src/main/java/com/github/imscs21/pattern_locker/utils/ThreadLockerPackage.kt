package com.github.imscs21.pattern_locker.utils

import com.github.imscs21.pattern_locker.views.PatternLockerDataController

data class ThreadLockerPackage
    (
    protected var _exitFlag:Boolean = false,
    protected val _exitFlagLocker:Any = Any()
            ){
        public fun getFlag():Boolean{
            val result = synchronized(_exitFlagLocker){
                _exitFlag
            }
            return result
        }
    public fun setFlag(value:Boolean){
        synchronized(_exitFlagLocker){
            _exitFlag = value
        }
    }
    public fun getRawFlagLocker():Any{
        return _exitFlagLocker
    }
}