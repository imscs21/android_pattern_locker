package com.github.imscs21.pattern_locker.views

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random
import android.content.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.PrintWriter
import java.io.StringWriter

typealias Position = Pair<Float,Float>//x,y position values

typealias PointItem = Pair<Int,Position>//pointID(index)number,Point Position

typealias SelectedPointItem = Pair<Long,PointItem>//task time order id(sequence id),PointItem

class PatternLockerDataController<T:Any> {


    public interface OnCheckingSelectedPointsListener{
        public fun onError(errorID:Int)
        public fun onSuccess(result:Boolean)
    }


    public interface DataStorageBehavior<T:Any>{
        public val innerValue:MutableLiveData<T>
        public fun selectValue():T?
        public fun savePattern(lockType: PatternLockView.LockType, selectedPoints:ArrayList<SelectedPointItem>):Boolean
        public fun loadPattern():T?
        public fun checkPattern(value:T?,lockType: PatternLockView.LockType,selectedPoints:ArrayList<SelectedPointItem>):Boolean
        public fun resetPattern():Boolean
    }


    public open class SimpleDataStorageBehavior:DataStorageBehavior<String>{

        private val sharedPreference:SharedPreferences

        private val SHAREDPREFERENCE_KEY_RANDOM_STATES = "random_states"

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
            //val spref_fn = "pattern_locker_preferences"
            //val key_random_states = "random_states"
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



        //protected var tmpHashString:String? = null

        protected val hashLocker:Any

        protected lateinit var random_states:ByteArray

        private val random_state_factory:RandomStateFactory

        constructor(context: Context){
            innerValue = MutableLiveData<String>()
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
            innerValue.value =  loadPattern()
        }


        override fun savePattern(
            lockType: PatternLockView.LockType,
            selectedPoints: ArrayList<SelectedPointItem>
        ): Boolean {
            try {
                if(selectedPoints.size==0){
                    return false
                }
                selectedPoints.sortBy { it.first }
                val md = MessageDigest.getInstance("MD5")
                md.update(lockType.value.toByteArray(Charsets.UTF_8))

                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints.size).array())
                for (i in selectedPoints.indices) {
                    md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints[i].second.first).array())
                }
                synchronized(hashLocker) {
                    innerValue. value = Base64.encode(md.digest(), Base64.DEFAULT).toString(Charsets.UTF_8)
                }
                try{
                    sharedPreference.edit().let {
                        it.putInt("pattern_type",lockType.intValue)
                        it.putString("pattern_value",innerValue. value)
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


        override fun loadPattern(): String? {
            try{
                val defvalue:String = "defvalue"

                val tmp = sharedPreference.getString("pattern_value",defvalue)
                tmp?.let {
                    return if (it.trim().isEmpty()||it.trim().equals(defvalue)) {
                         null
                    }
                    else{
                         it.trim()
                    }

                }
            }catch(e:Exception){

            }
            return null
        }


        override fun checkPattern(
            value: String?,
            lockType: PatternLockView.LockType,
            selectedPoints: ArrayList<SelectedPointItem>
        ): Boolean {
            try{
                if(selectedPoints.size>0){
                    selectedPoints.sortBy { it.first }
                selectedPoints.let{
                            val md = MessageDigest.getInstance("MD5")
                            md.update(lockType.value.toByteArray(Charsets.UTF_8))

                            md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints.size).array())
                            for(i in selectedPoints.indices){

                                val item = selectedPoints[i].second
                                if(item.first<0 || item.second.let{it.first<0 || it.second<0}){
                                    return@let it.size<0&&!(item.first<0 || item.second.let{it.first<0 || it.second<0})
                                }
                                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(item.first).array())
                            }
                            return it.size>1&&value!=null&&value!!.trim().equals(android.util.Base64.encode(md.digest(),Base64.DEFAULT).toString(Charsets.UTF_8).trim())
                    }
                }
            }
            catch(e:Exception){

            }
            return false//it.size>1&&tmpHashString==null&&tmpHashString!=null
        }


        override fun resetPattern(): Boolean {
            synchronized(hashLocker) {
                innerValue. value = null
            }
            try{
                sharedPreference.edit().let {
                    it.remove("pattern_value")
                    //it.putString("pattern_value",tmpHashString)
                    it.commit()
                }

            }
            catch(e2:Exception){
                return false
            }
            return true
        }


        override val innerValue: MutableLiveData<String>


        override fun selectValue(): String? {
            return innerValue.value
        }


    }

    public open class PatternLockerDataControllerFactory{

        protected var tmpInstance:PatternLockerDataController<Any>? = null

        protected val maxInstanceCount:Int

        protected var currentInstanceCount:Int = 0

        private constructor(maxInstanceCount:Int=2){
            currentInstanceCount = 0
            this.maxInstanceCount = maxInstanceCount
            tmpInstance = null
        }

        
        protected final fun<T:Any> createNewTempInstance(context: Context,dataStorageBehavior: DataStorageBehavior<T>){
            currentInstanceCount++
            tmpInstance = PatternLockerDataController<T>(context,dataStorageBehavior) as PatternLockerDataController<Any>
        }

        
        public fun<T:Any> build(context: Context,dataStorageBehavior: DataStorageBehavior<T>):PatternLockerDataController<T>{
            if(tmpInstance==null){
                createNewTempInstance<T>(context,dataStorageBehavior)
            }
            val result = tmpInstance!!
            tmpInstance = null
            return result as PatternLockerDataController<T>
        }

        
        companion object{
            private var factoryObj:PatternLockerDataControllerFactory? = null
            public fun getInstance():PatternLockerDataControllerFactory{
                if(factoryObj==null){
                    factoryObj = PatternLockerDataControllerFactory()
                }
                return factoryObj!!
            }
        }
    }

    public var onCheckingSelectedPointsListener:OnCheckingSelectedPointsListener? = null

    private val dataStorageBehavior:DataStorageBehavior<T>

    public val context:Context

    public fun resetPattern(){
        try {
            dataStorageBehavior?.resetPattern()
        }catch(e:Exception){

        }
    }

    public fun savePattern(lockType: PatternLockView.LockType, selectedPoints:ArrayList<SelectedPointItem>):Boolean{
        return dataStorageBehavior.savePattern(lockType, selectedPoints)
    }

    
    public fun requestToCheckSelectedPoints(lockType: PatternLockView.LockType,selectedPoints:ArrayList<SelectedPointItem>){

        try{

            onCheckingSelectedPointsListener?.onSuccess(
                dataStorageBehavior.checkPattern(dataStorageBehavior.selectValue(), lockType=lockType, selectedPoints = selectedPoints))
        }catch(e:Exception){
            onCheckingSelectedPointsListener?.onError(-1)
        }
    }


    private constructor(context: Context,dataStorageBehavior: DataStorageBehavior<T>){
        this.context = context
        this.dataStorageBehavior = dataStorageBehavior

    }

    
}