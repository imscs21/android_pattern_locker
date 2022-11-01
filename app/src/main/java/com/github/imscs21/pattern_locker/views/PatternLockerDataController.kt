package com.github.imscs21.pattern_locker.views

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random
import android.content.*
import java.io.PrintWriter
import java.io.StringWriter

typealias Position = Pair<Float,Float>//x,y position values
typealias PointItem = Pair<Int,Position>//pointID(index)number,Point Position
class PatternLockerDataController {
    public interface OnCheckingSelectedPointsListener{
        public fun onError(errorID:Int)
        public fun onSuccess(result:Boolean)
    }
    protected inner class RandomStateFactory{
        private val randomStateNumber:Int
        private val randomObject:Random
        private val context:Context

        constructor(context: Context, randomStateNumber:Int){
            this.randomStateNumber = randomStateNumber
            this.context = context
            randomObject = Random(randomStateNumber)
            val spref_fn = "pattern_locker_preferences"
            //val key_random_states = "random_states"
            //sharedPreference = context.getSharedPreferences(spref_fn,Context.MODE_PRIVATE)
        }
        public fun createNewRawRandomStates(size:Int):ByteArray{
            return randomObject.nextBytes(size)
        }
        public fun createNewRandomStates(size:Int):IntArray{
            val tmp  = createNewRawRandomStates(size)
            val rst = IntArray(tmp.size,{0})
            tmp.forEachIndexed { index, byte ->
                rst[index] = byte.toInt()
            }
            return rst
        }
        public fun loadRawRandomStates():ByteArray?{
            try {
                sharedPreference.let { spref ->
                    spref.getString(SHAREDPREFERENCE_KEY_RANDOM_STATES, null)?.let {
                        return Base64.decode(it, Base64.DEFAULT)
                    }
                }
            }catch(e:Exception){

            }
            return null
        }
        public fun loadRandomStates():IntArray?{
            loadRawRandomStates()?.let{
                val result = IntArray(it.size,{0})
                it.forEachIndexed { index, byte ->
                    result[index] = byte.toInt()
                }
                return result
            }
            return null
        }
    }

    public open class PatternLockerDataControllerFactory{
        protected var tmpInstance:PatternLockerDataController? = null
        protected val maxInstanceCount:Int
        protected var currentInstanceCount:Int = 0
        private val context:Context
        private constructor(context: Context,maxInstanceCount:Int=2){
            currentInstanceCount = 0
            this.context = context
            this.maxInstanceCount = maxInstanceCount
            tmpInstance = null
        }

        protected final fun createNewTempInstance(){
            currentInstanceCount++
            tmpInstance = PatternLockerDataController(context)
        }
        public fun build():PatternLockerDataController{
            if(tmpInstance==null){
                createNewTempInstance()
            }
            val result = tmpInstance!!
            tmpInstance = null
            return result
        }
        companion object{
            private var factoryObj:PatternLockerDataControllerFactory? = null
            public fun getInstance(context: Context):PatternLockerDataControllerFactory{
                if(factoryObj==null){
                    factoryObj = PatternLockerDataControllerFactory(context)
                }
                return factoryObj!!
            }
        }
    }

    private val sharedPreference:SharedPreferences

    private val SHAREDPREFERENCE_KEY_RANDOM_STATES = "random_states"

    public var onCheckingSelectedPointsListener:OnCheckingSelectedPointsListener? = null
    
    protected var tmpHashString:String? = null

    protected val hashLocker:Any

    protected lateinit var random_states:ByteArray

    private val random_state_factory:RandomStateFactory

    public val context:Context


    public fun createOrLoadRandomStates():ByteArray{
        try {
            random_state_factory.loadRawRandomStates()?.let{
                return it
            }
        }catch(e:Exception){
        }
        return createNewRandomStates(32,true)
    }


    protected final fun createNewRandomStates(size:Int,try_to_store:Boolean = true):ByteArray{
            val new_random_states = random_state_factory.createNewRawRandomStates(size)
            if(try_to_store){
                try {
                    sharedPreference.edit().also {
                        it.putString(SHAREDPREFERENCE_KEY_RANDOM_STATES,
                            Base64.encode(new_random_states, Base64.DEFAULT)
                                .toString(Charsets.UTF_8)
                        )
                        it.commit()
                    }
                }catch(e:Exception){

                }
            }
        return new_random_states
    }


    public fun savePattern(lockType: PatternLockView.LockType, selectedPoints:ArrayList<PointItem>):Boolean{
        try {
            if(selectedPoints.size==0){
                return false
            }
            val md = MessageDigest.getInstance("MD5")
            md.update(lockType.value.toByteArray(Charsets.UTF_8))

            md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints.size).array())
            for (i in selectedPoints.indices) {
                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints[i].first).array())
            }
            synchronized(hashLocker) {
                tmpHashString = Base64.encode(md.digest(), Base64.DEFAULT).toString(Charsets.UTF_8)
            }
            try{
                sharedPreference.edit().let {
                    it.putInt("pattern_type",lockType.intValue)
                    it.putString("pattern_value",tmpHashString)
                    it.commit()
                }
            }
            catch(e2:Exception){

            }
            return true
        }catch(e:Exception){

        }
        return false
    }


    public fun resetPattern(){
        synchronized(hashLocker) {
            tmpHashString = null
        }
        try{
            sharedPreference.edit().let {
                it.remove("pattern_value")
                it.commit()
            }
        }
        catch(e2:Exception){

        }
    }


    public fun requestToCheckSelectedPoints(lockType: PatternLockView.LockType,selectedPoints:ArrayList<PointItem>){

        try{

            onCheckingSelectedPointsListener?.onSuccess(selectedPoints.let{
                try{
                val md = MessageDigest.getInstance("MD5")
                md.update(lockType.value.toByteArray(Charsets.UTF_8))

                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints.size).array())
                for(i in selectedPoints.indices){
                    val item = selectedPoints[i]
                    if(item.first<0 || item.second.let{it.first<0 || it.second<0}){
                        return@let it.size<0&&!(item.first<0 || item.second.let{it.first<0 || it.second<0})
                    }
                    md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(item.first).array())
                }
                    it.size>1&&tmpHashString!=null&&tmpHashString!!.trim().equals(android.util.Base64.encode(md.digest(),Base64.DEFAULT).toString(Charsets.UTF_8).trim())
                }
                catch(e:Exception){
                    false
                }
            }
            )
        }catch(e:Exception){
            onCheckingSelectedPointsListener?.onError(-1)
        }
    }


    private constructor(context: Context){
        this.context = context
        val spref_fn = "pattern_locker_preferences"
        sharedPreference = context.getSharedPreferences(spref_fn,Context.MODE_PRIVATE)
        random_state_factory = RandomStateFactory(context,1234)
        random_states = createOrLoadRandomStates()
        hashLocker =Any()
        try{
            sharedPreference.let{
                val key_pattern_type = "pattern_type"
                //it.getInt(key_pattern_type,-99)
                if(it.getInt(key_pattern_type,-99)<-1){
                    it.edit().let{editor->
                        editor.putInt(key_pattern_type,-1)
                        editor.commit()
                    }
                }

            }

        }catch(e:Exception){

        }
        try{
            val defvalue:String = "defvalue"
            tmpHashString = sharedPreference.getString("pattern_value",defvalue)
            tmpHashString?.let {
                if (it.trim().isEmpty()||it.trim().equals(defvalue)) {
                    tmpHashString = null
                }
                else{
                    tmpHashString = it.trim()
                }
            }
        }catch(e:Exception){

        }
    }


    
}