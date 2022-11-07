package com.github.imscs21.pattern_locker.views

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random
import android.content.*
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import java.io.StringWriter

/**
 * nickname of Position object by using android default pre-defined [Pair] class
 */
typealias Position = Pair<Float,Float>//x,y position values

/**
* nickname of PointItem object by using android default pre-defined [Pair] class
*/
typealias PointItem = Pair<Int,Position>//pointID(index)number,Point Position

/**
 * nickname of SelectedPointItem object by using android default pre-defined [Pair] class
 */
typealias SelectedPointItem = Pair<Long,PointItem>//task time order id(sequence id),PointItem

/**
 *
 * [T] is data type of pattern value
 * @author imscs21
 * @since 2022-11-1
 */
class PatternLockerDataController<T:Any> {

    /**
     * callback listener for notice of result after checking pattern
     */
    public interface OnCheckingSelectedPointsListener{
        /**
         * use this function when abnormal process had been occured
         *
         */
        public fun onError(errorID:Int)

        /**
         * useed to notice result whether checking result is success or fail
         * @param result result value whether checking result is success or fail, if success, value is true.
         */
        public fun onSuccess(result:Boolean)
    }

    /**
     * abstract data storage behavior(a.k.a. method of m of mvc pattern)
     * [T] is data type of pattern value
     */
    public interface DataStorageBehavior<T:Any>{
        /**
         * pre-defined cached pattern value
         */
        public val innerValue:MutableLiveData<T>

        /**
         * function that help to select data variable of answer value of pattern
         * currently used for selecting whether using cached value(a.k.a. [innerValue]) or your custom value
         * [T] is data type of pattern value
         */
        public fun selectValue():T?

        /**
         * function that helping to save pattern to your data storage(a.k.a. m of mvc pattern)
         * @param lockType which pattern type you want to store pattern value
         * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
         * @return true or false whether succeeding to store pattern or not
         */
        public fun savePattern(lockType: PatternLockView.LockType, selectedPoints:ArrayList<SelectedPointItem>):Boolean

        /**
         * function that try to restore and obtain pattern value from your data storage
         * [T] is data type of pattern value
         * @return (null or [T]) if null,it means failing to load pattern
         */
        public fun loadPattern():T?

        /**
         * function that helping to check pattern between stored pattern and currently selected pattern points
         * @param value stored pattern value(current value is from selectValue function)
         * @param lockType which pattern type you want to check pattern value
         * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
         * @return true or false whether both pattern value is identical or not
         */
        public fun checkPattern(value:T?,lockType: PatternLockView.LockType,selectedPoints:ArrayList<SelectedPointItem>):Boolean

        /**
         * function that helping to delete or reset pattern value from your data storage
         * @return true or false whether (succeeding to delete or reset) or not
         */
        public fun resetPattern():Boolean
    }

    /**
     * pre-defined simple behavior of [DataStorageBehavior] and example of that how to implement
     *  algorithm of storing data has weakness points due to md5 hashing in this class
     */
    public open class SimpleDataStorageBehavior:DataStorageBehavior<String>{
        /**
         * local storage(a.k.a. m of mvc pattern) for storing pattern info
         */
        private val sharedPreference:SharedPreferences

        /**
         * searching key for random state value in local storage(a.k.a. [sharedPreference])
         */
        private val SHAREDPREFERENCE_KEY_RANDOM_STATES = "random_states"

        /**
         * this class is used to achieve random data
         */
        protected inner class RandomStateFactory{

            /**
             * this is used for initializing [Random] class
             */
            private val randomStateNumber:Int

            /**
             * instance of random data factory
             */
            private val randomObject:Random

            private val context:Context



            /**
             * @param context mainly used for obtaining instance of [sharedPreference]
             * @param randomStateNumber used for initializing [randomObject] instance
             */
            constructor(context: Context, randomStateNumber:Int){
                this.randomStateNumber = randomStateNumber
                this.context = context
                randomObject = Random(randomStateNumber)
                val spref_fn = "pattern_locker_preferences"
                //val key_random_states = "random_states"
                //sharedPreference = context.getSharedPreferences(spref_fn,Context.MODE_PRIVATE)
            }

            /**
             * function that achieving new random data
             * @param size how many random data achieved(a.k.a. length of array)
             * @return byte array of random data
             */
            public fun createNewRawRandomStates(size:Int):ByteArray{
                return randomObject.nextBytes(size)
            }

            /**
             * function that achieving new random data which each data type is integer
             * @param size how many random data achieved(a.k.a. length of array)
             * @return integer array of random data
             */
            public fun createNewRandomStates(size:Int):IntArray{
                val tmp  = createNewRawRandomStates(size)
                val rst = IntArray(tmp.size,{0})
                tmp.forEachIndexed { index, byte ->
                    rst[index] = byte.toString().toInt()
                }
                return rst
            }

            /**
             * function that restoring random states data from [sharedPreference](a.k.a. local data storage)
             * @return it returns (null value or array of random data) if null value, it means failing to load data from local data storages
             */
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

            /**
             * function that restoring random states data from [sharedPreference](a.k.a. local data storage)
             * @return it returns (null value or integer array of random data) if null value, it means failing to load data from local data storages
             */
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

        /**
         * create or load byte array of random data
         * @return new or existed random data
         */
        public fun createOrLoadRandomStates():ByteArray{
            try {
                random_state_factory.loadRawRandomStates()?.let{
                    return it
                }
            }catch(e:Exception){
            }
            //if fail to load from local data storage,achieve new random state
            return createNewRandomStates(32,true)
        }

        /**
         * achieve new random data
         * @param size how many data achieving
         * @param try_to_store try to store new random data to local storage after succeeding to achieve new random data
         * @return new random data
         */
        protected final fun createNewRandomStates(size:Int,try_to_store:Boolean = true):ByteArray{
            //val spref_fn = "pattern_locker_preferences"
            //val key_random_states = "random_states"
            val new_random_states = random_state_factory.createNewRawRandomStates(size)
            if(try_to_store){
                try {
                    /**
                     * try to store data as base64 string to data storage
                     */
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
        /**
         * used to synchonize data status
         */
        protected val hashLocker:Any

        /**
         * not currently used
         */
        protected lateinit var random_states:ByteArray

        /**
         * not currently used
         */
        private val random_state_factory:RandomStateFactory

        private val uiHandler:Handler


        constructor(context: Context,uiHandler:Handler = Handler(Looper.getMainLooper())){
            innerValue = MutableLiveData<String>()
            val spref_fn = "pattern_locker_preferences"
            this.uiHandler = uiHandler
            sharedPreference = context.getSharedPreferences(spref_fn,Context.MODE_PRIVATE)
            random_state_factory = RandomStateFactory(context,1234)
            random_states = createOrLoadRandomStates()
            hashLocker =Any()
            try{
                /**
                 * try to store default value if not exist
                 */
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
            loadPattern()?.let{
                uiHandler.post {
                    innerValue.value = it
                }
            }
        }

        /**
         * implemented function that helping to save pattern to your data storage(a.k.a. m of mvc pattern)
         * @param lockType which pattern type you want to store pattern value
         * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
         * @return true or false whether succeeding to store pattern or not
         */
        override fun savePattern(
            lockType: PatternLockView.LockType,
            selectedPoints: ArrayList<SelectedPointItem>
        ): Boolean {
            try {
                if(selectedPoints.size==0){
                    return false
                }
                /**
                 * try to store pattern value to data storage by using md5 hash algorithm
                 */
                val md = MessageDigest.getInstance("MD5")
                md.update(lockType.value.toByteArray(Charsets.UTF_8))
                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(lockType.intValue).array())
                md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints.size).array())
                for (i in selectedPoints.indices) {
                    md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(selectedPoints[i].second.first).array())
                }
                synchronized(hashLocker) {
                    Base64.encode(md.digest(), Base64.DEFAULT).toString(Charsets.UTF_8).let {
                        uiHandler.post {
                            innerValue.value =it
                        }
                    }
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

        /**
         * implemented function that try to restore and obtain pattern value from your data storage
         * [T] is data type of pattern value
         * @return (null or [T]) if null,it means failing to load pattern
         */
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

        /**
         * function that helping to check pattern between stored pattern and currently selected pattern points
         * @param value stored pattern value(current value is from selectValue function)
         * @param lockType which pattern type you want to check pattern value
         * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
         * @return true or false whether both pattern value is identical or not
         */
        override fun checkPattern(
            value: String?,
            lockType: PatternLockView.LockType,
            selectedPoints: ArrayList<SelectedPointItem>
        ): Boolean {
            try{
                if(selectedPoints.size>0){
                    //selectedPoints.sortBy { it.first }
                selectedPoints.let{
                            val md = MessageDigest.getInstance("MD5")
                            md.update(lockType.value.toByteArray(Charsets.UTF_8))
                            md.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(lockType.intValue).array())
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

        /**
         * function that helping to delete or reset pattern value from your data storage
         * @return true or false whether (succeeding to delete or reset) or not
         */
        override fun resetPattern(): Boolean {
            synchronized(hashLocker) {
                uiHandler.post {
                    innerValue.value = null
                }
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

        /**
         * pre-defined cached pattern value
         */
        override val innerValue: MutableLiveData<String>

        /**
         * function that help to select data variable of answer value of pattern
         * currently used for selecting whether using cached value(a.k.a. [innerValue]) or your custom value
         * [T] is data type of pattern value
         */
        override fun selectValue(): String? {
            return innerValue.value
        }


    }

    /**
     * This class helps to be easy to achieve data controller class instance and set options
     *
     */
    public open class PatternLockerDataControllerFactory{

        /**
         * temporary buffer of data controller instance
         * data type(a.k.a. 'Any') of PatternLockerDataController<Any> is data type of pattern value
         */
        protected var tmpInstance:PatternLockerDataController<Any>? = null

        /**
         * not mainly and currenly used
         */
        protected val maxInstanceCount:Int

        /**
         * not mainly and currenly used
         */
        protected var currentInstanceCount:Int = 0

        private constructor(maxInstanceCount:Int=2){
            currentInstanceCount = 0
            this.maxInstanceCount = maxInstanceCount
            tmpInstance = null
        }

        /**
         * create New Instance or
         * [T] is data type of pattern value
         *@param context Context
         * @param dataStorageBehavior essential option to operate data controller
         * @return new instance of [PatternLockerDataController]
         */
        protected final fun<T:Any> createNewTempInstance(context: Context,dataStorageBehavior: DataStorageBehavior<T>){
            currentInstanceCount++
            tmpInstance = PatternLockerDataController<T>(context,dataStorageBehavior) as PatternLockerDataController<Any>
        }

        /**
         *
         * [T] is data type of pattern value
         *@param context Context
         * @param dataStorageBehavior essential option to operate data controller
         * @return [PatternLockerDataController]
         */
        public fun<T:Any> build(context: Context, dataStorageBehavior: DataStorageBehavior<T>):PatternLockerDataController<T>{
            if(tmpInstance==null){
                createNewTempInstance<T>(context,dataStorageBehavior)
            }
            val result = tmpInstance!!
            tmpInstance = null
            return result as PatternLockerDataController<T>
        }

        
        companion object{
            /**
             * buffer of static instance of [PatternLockerDataControllerFactory]
             */
            private var factoryObj:PatternLockerDataControllerFactory? = null

            /**
             * obtain static factory instance
             */
            public fun getInstance():PatternLockerDataControllerFactory{
                if(factoryObj==null){
                    factoryObj = PatternLockerDataControllerFactory()
                }
                return factoryObj!!
            }
        }
    }

    /**
     * callback listener for interaction between controller and view
     */
    public var onCheckingSelectedPointsListener:OnCheckingSelectedPointsListener? = null

    /**
     * data controller storage behabior
     */
    private val dataStorageBehavior:DataStorageBehavior<T>


    public val context:Context
    /**
     * function that helping to delete or reset pattern value from your data storage
     * by using [dataStorageBehavior]
     */
    public fun resetPattern(){
        try {
            dataStorageBehavior?.resetPattern()
        }catch(e:Exception){

        }
    }
    /**
     * function that helping to save pattern to your data storage(a.k.a. m of mvc pattern)
     * by using [dataStorageBehavior]
     * @param lockType which pattern type you want to store pattern value
     * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
     * @return true or false whether succeeding to store pattern or not
     */
    public fun savePattern(lockType: PatternLockView.LockType, selectedPoints:ArrayList<SelectedPointItem>):Boolean{
        if(selectedPoints.size>1){
            selectedPoints.sortBy { it.first }
        }
        return dataStorageBehavior.savePattern(lockType, selectedPoints)
    }

    /**
     * function that helping to check pattern between stored pattern and currently selected pattern points
     * @param value stored pattern value(current value is from selectValue function)
     * @param lockType which pattern type you want to check pattern value
     * @param selectedPoints list of selected pattern points with sequence id(this is also sorted)
     * @return request result (true or false) whether both pattern value is identical or not
     */
    public fun requestToCheckSelectedPoints(lockType: PatternLockView.LockType,selectedPoints:ArrayList<SelectedPointItem>){
        if(selectedPoints.size>1){
            selectedPoints.sortBy { it.first }
        }
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