package com.github.imscs21.pattern_locker.views

import android.content.*
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import android.graphics.*
import android.os.*
import android.widget.Toast
import androidx.core.content.PackageManagerCompat
import androidx.core.os.ExecutorCompat
import com.github.imscs21.pattern_locker.R
import com.github.imscs21.pattern_locker.utils.ThreadLockerPackage
import com.github.imscs21.pattern_locker.utils.VibrationUtil
import kotlinx.coroutines.selects.select
import java.util.PriorityQueue
import java.util.concurrent.Executor
import java.util.concurrent.locks.Lock
import kotlin.math.*

/**
 * Pattern View
 * @author imscs21
 * @since 2022-11-7
 */
class PatternLockView : View ,View.OnTouchListener {

    /**
     * callback listener when user has done to draw pattern
     */
    public interface OnTaskPatternListener{
        /**
         * used when there is no any selected points
         */
        public fun onNothingSelected()

        /**
         *
         */
        public fun onFinishedPatternSelected( patternLockView:PatternLockView,editModeFromView:Boolean,lockType: LockType,copiedSelectedPoints:ArrayList<SelectedPointItem>)
    }

    /**
     * callback listener when drawing custom shape
     */
    public interface OnCalculateCustomShapePositionListener{
        /**
         * This method is for calculating custom shape
         * @param canvasWidth width of view canvas
         * @param canvasHeight height of view canvas
         * @param directCanvas direct view canvas instance if available
         * @param pointsOfPatternContainer list of points of pattern
         * @param pointsOfPatternContainerMaxLength [pointsOfPatternContainer] can contain items util [pointsOfPatternContainerMaxLength]
         * @param pointRadius radius value of each points of pattern(a.k.a. circle radius)
         * @param spacingValuesIfWrapContent <spacing unit,spacing size> between points
         * @param isPointContainerClearedBeforeThisMethod flag whether pointsOfPatternContainer had been cleared or not before entering this method
         * @param customParams custom params is not currenly used
         */
        public fun onCalculateCustomShape(canvasWidth:Int,canvasHeight:Int,
                                          directCanvas:Canvas?,
                                          pointsOfPatternContainer:ArrayList<PointItem>,
                                          pointsOfPatternContainerMaxLength:UInt,
                                          pointRadius:Float,
                                          spacingValuesIfWrapContent:Pair<SpacingTypeIfWrapContent,Float>,
                                          isPointContainerClearedBeforeThisMethod:Boolean = true,
                                          customParams : Any? = null)

        /**
         * this method is used to calculate spacing size when type of SpacingTypeIfWrapContent is total
         */
        public fun getSpacingCount():Int

    }
    public interface OnFinishInitializePoints{
        public fun onFinished(view:PatternLockView)
    }
    //public var onFinishInitializePoints:OnFinishInitializePoints? = null //for instrument test
    /**
     * callback listener for turning off error indicator
     */
    public interface OnTurnOffErrorIndicatorListener{
        public fun onTurnOff(view:PatternLockView,fromDelayedTime:Boolean)
    }

    /**
     * callback listener for custom point id(index)
     */
    public interface OnCalculateCustomIndexOfPointOfPatternListener{
        /**
         * this method is for custom id for custom calculating storing and checking algorithm
         * @param originalIndex original index of pattern point
         * @param pointPosition r/o point position which id coincided with [originalIndex] for checking position
         */
        public fun getPointIndex(originalIndex:Int,pointPosition:Position):Int
    }

    /**
     * Pattern types
     * @property value string identity value of each enums
     * @property intValue integer identity value of each enums
     */
    public enum class LockType(val value:String,val intValue:Int){
        SQUARE_3X3("SQUARE_3X3",1),
        SQUARE_3X3_WITH_CHECKER_PATTERN("SQUARE_3X3_CHECKER",2),//total (9+4) points
        SQUARE_4X4("SQUARE_4X4",3),
        SQUARE_5X5("SQUARE_5X5",4),
        SQUARE_6X6("SQUARE_6X6",5),
        SQUARE_7X7("SQUARE_7X7",6),
        PENTAGON_DEFAULT("PENTAGON_SHAPE",7),
        PENTAGON_HIGH_DENSITY("PENTAGON_SHAPE_HD",8),
        HEXAGON_DEFAULT("HEXAGON_SHAPE",9),
        HEXAGON_HIGH_DENSITY("HEXAGON_SHAPE_HD",10),

        /**
         * Custom type can be set in only kotlin
         * and
         * you should also set [onCalculateCustomPointOfPatternListener] property in code
         * if you wanna use custom shape.
         */
        CUSTOM("DEVELOPER_CUSTOM",99)
    }

    /**
     * spacing types that how to measure spacing among points when using wrap content for view size
     * @property intValue integer identity value of each enums
     */
    public enum class SpacingTypeIfWrapContent(val intValue:Int){
        TOTAL(1),
        FIXED(2)
    }

    /**
     *  listener property for calculating your own custom index
     */
    public var onCalculateCustomPointOfPatternListener:OnCalculateCustomIndexOfPointOfPatternListener? = null

    /**
     * listener property for calculating custom shape when selecting custom lock type
     */
    public var onCalculateCustomShapePositionListener:OnCalculateCustomShapePositionListener? = null

    /**
     * listener property for turning off error indicator
     */
    public var onTurnOffErrorIndicatorListener:OnTurnOffErrorIndicatorListener? = null

    /**
     * flag that checking abnormal judgement padding size for other developer`s mistakes
     * when setting [clickingJudgementPaddingRadius] property
     */
    public var canPreventAbnormalJudgementPadding:Boolean = true
    set(value) {
        field = value
        if(value&&dip1>0) {
            clickingJudgementPaddingRadius = max(MIN_JDGMNT_PAD_RADIUS, min(clickingJudgementPaddingRadius,MAX_JDGMNT_PAD_RADIUS))
        }
    }


    /**
     * radius value of one of pattern points
     */
    protected var pointRadius = 0f

    /**
     * @see [LockType]
     */
    protected lateinit var lockType:LockType

    /**
     * pre-defined android dimension(a.k.a. android measurement size) for 1 of dip unit(a.k.a. 1.0dip)
     * to ease calculating
     */
    protected var dip1:Float = 0f
    set(value){
        field = value
        MIN_POINT_RADIUS = value*2
        MAX_POINT_RADIUS = value*200

        MIN_JDGMNT_PAD_RADIUS = value*(-50)
        MAX_JDGMNT_PAD_RADIUS = value*(1000)
    }

    /**
     * minimum radius value of one of pattern points
     */
    protected var MIN_POINT_RADIUS:Float = 0f

    /**
     * maximum radius value of one of pattern points
     */
    protected var MAX_POINT_RADIUS:Float = 110f

    /**
     * minimum radius value of judgement padding
     */
    protected var MIN_JDGMNT_PAD_RADIUS:Float = -50f

    /**
     * maximum radius value of judgement padding
     */
    protected var MAX_JDGMNT_PAD_RADIUS:Float = 500f

    /**
     * list of series of current pre-defined position of all pattern points
     */
    protected lateinit var points:ArrayList<PointItem>

    /**
     * list of series of current pre-defined position and sequence id of selected pattern points
     */
    protected lateinit var selectedPoints:ArrayList<SelectedPointItem>

    /**
     * locker instance for synchonizing data of [selectedPoints]
     */
    protected val selectedPointsLocker:Any by lazy{Any()}

    /**
     * state of when we can put met pattern points to [selectedPoints] or not
     */
    @Volatile
    protected var canCollectSelectedPoints = false

    /**
     * @see [OnTaskPatternListener]
     */
    public var onTaskPatternListener:OnTaskPatternListener? = null

    /**
     * position of where we touched view
     * if property value is null, it would be non-touched
     */
    protected var floatingPoint:Pair<Float,Float>? = null

    /**
     * flag whether using CheckAlgorithm in CustomShape or not
     */
    public var useCheckAlgorithmInCustomShape:Boolean = true

    /**
     * canvas paint instance of trajectory lines
     */
    protected lateinit var linePaint:Paint

    /**
     * canvas paint instance of pattern points
     */
    protected lateinit var pointPaint:Paint

    /**
     * handler for updating UI
     */
    protected lateinit var mainHandler:Handler

    /**
     * handler from other thread(a.k.a. multithreading handler)
     */
    protected lateinit var secondHandler:Handler

    /**
     * indicatorBlurDx
     */
    protected var indicatorBlurDx:Float = 1f

    /**
     * indicatorBlurDy
     */
    protected  var indicatorBlurDy:Float = 1f

    /**
     * indicatorBlurColor
     */
    protected var indicatorBlurColor:Int = Color.GRAY

    /**
     * color property of pattern point
     */
    public var pointColor:Int = Color.CYAN
    set(value) {
        field = getColorIntFromInt(value)
    }

    /**
     * color property of pattern point when occured error
     */
    public var pointErrorColor:Int = Color.MAGENTA
        set(value) {
            field = getColorIntFromInt(value)
        }
    /**
     * color property of selected pattern points
     */
    public var selectedPointColor:Int = Color.CYAN
        set(value) {
            field = getColorIntFromInt(value)
        }
    /**
     * color property of selected pattern points when occured error
     */
    public var selectedPointErrorColor:Int = Color.RED
        set(value) {
            field = getColorIntFromInt(value)
        }

    /**
     * vibration util instance when clicking pattern points
     */
    protected var vibrationUtil: VibrationUtil? = null

    /**
     * locker for [isReadOnlyMode]
     */
    private val isReadOnlyModeLocker by lazy{Any()}

    /**
     * property for non-touchable mode
     */
    public var isReadOnlyMode:Boolean = false
    get() {
        val result = synchronized(isReadOnlyModeLocker){
            field
        }
        return result
    }
    set(value){
        synchronized(isReadOnlyModeLocker){
            field = value
        }
        if(value){
            floatingPoint = null
        }
    }

    /**
     * @see [SpacingTypeIfWrapContent]
     */
    protected var spacingTypeIfWrapContent:SpacingTypeIfWrapContent = SpacingTypeIfWrapContent.TOTAL

    /**
     * how many size of we space padding among pattern points
     * unit of size is [spacingTypeIfWrapContent]
     * @see [SpacingTypeIfWrapContent]
     */
    protected var spacingSizeIfWrapContent:Float = 100f

    /**
     * padding radius is used to have tolerance for boundary of each pattern points when judging to click pattern points
     */
    public var clickingJudgementPaddingRadius:Float = 5f
    set(value) {

        if(canPreventAbnormalJudgementPadding){

            var value2 = max(MIN_JDGMNT_PAD_RADIUS,min(value,MAX_JDGMNT_PAD_RADIUS))
            field = value2
        }
        else{

            val value2 = max(Float.MIN_VALUE.toDouble(),min(value.toDouble()+pointRadius.toDouble(),Float.MAX_VALUE.toDouble())).toFloat()
            if(value2>=Float.MAX_VALUE){
                field = value - pointRadius
            }
            else if(value2<=Float.MIN_VALUE){
                field = value + pointRadius
            }
            else{
                field = value
            }

        }
    }

    /**
     * Is this view in pattern edit mode?
     * this property is not mainly used nowadays.
     */
    public var editMode:Boolean = false

    /**
     * on/off value whether using device vibration or not
     */
    public var useVibratorIfAvaliable:Boolean = true

    /**
     * on/off value whether showing shadow of selected pattern trajectory lines or not
     */
    public var useTrajectoryLineShadow:Boolean = false
        set(value)  {
            field = value
            if(value){
                linePaint.setShadowLayer(dip1,1f,1f,Color.GRAY)
            }
            else{
                linePaint.clearShadowLayer()
            }
            try{
                mainHandler.post {
                    invalidate()
                }
            }catch(e:Exception){

            }
        }

    /**
     * thread locker for [isUsingErrorIndicator] property
     */
    protected val isUsingErrorIndicatorLocker:Any by lazy{Any()}

    /**
     * flag whether using error indicator or not
     */
    public var isUsingErrorIndicator:Boolean = false
    public get() {
        val rst = synchronized(isUsingErrorIndicatorLocker){field}
        return rst
    }
    protected set(value) {
        synchronized(isUsingErrorIndicatorLocker) {
            field = value
        }
    }

    /**
     * flag whether view should recalculate view size when used wrap_content or not
     */
    protected var shouldRecalculateSize = false

    /**
     * not currently used
     */
    protected var innerViewGravity: Int = Gravity.CENTER

    /**
     * on/off value whether showing selected pattern trajectory lines or not
     */
    public var shouldShowTrajectoryLines:Boolean = true
    set(value)  {
        field = value
        try{
            mainHandler.post {
                invalidate()
            }
        }catch(e:Exception){

        }
    }

    constructor(context:Context):super(context){
        initializeVars(context)
    }
    constructor(context:Context,attrs:AttributeSet):super(context,attrs){
        initializeVars(context)
        setAttrs(context, attrs)
    }
    constructor(context: Context,attrs:AttributeSet,defStyle:Int):super(context, attrs,defStyle){
        initializeVars(context)
        setAttrs(context, attrs, defStyle)
    }

    protected final fun getColorIntFromInt(colorInt:Int):Int{
        val a = Color.alpha(colorInt)
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)
        val tmp = listOf(a,r,g,b)
        for(c in tmp){
            if(0<=c&&c<=255||-128<=c&&c<=127){

            }
            else{
                throw Exception("Color Range Error")
            }
        }
        return Color.argb(a,r,g,b)
    }
    /**
     * set spaceing type and size which run when one of view sizes is wrap content
     * @param spacingType [SpacingTypeIfWrapContent]
     * @param spacingSize spacing size between pattern points
     * @param invalidateView flag whether view should redraw or not
     */
    public fun setSpacingTypeIfWrapContent(spacingType:SpacingTypeIfWrapContent,spacingSize:Float,invalidateView: Boolean = true){
        spacingSizeIfWrapContent = spacingSize
        spacingTypeIfWrapContent = spacingType
        if(shouldRecalculateSize){
            mainHandler.post {
                try{
                    requestLayout()
                }
                catch(e:Exception){

                }
            }
        }
        if(invalidateView){
            mainHandler.post {
                try{
                    invalidate()
                }
                catch(e:Exception){

                }
            }
        }

    }

    public fun turnErrorIndicator(stateValue:Boolean,invalidateView: Boolean = true,semiAutoTurningOffMills:Long = -1){
        isUsingErrorIndicator = stateValue
        if(!stateValue) {
            mainHandler.post {
                onTurnOffErrorIndicatorListener?.onTurnOff(this, false)
            }
        }
        if(invalidateView){
            mainHandler.post {
                try {
                    invalidate()
                }catch(e:Exception){

                }
            }
        }
        if(semiAutoTurningOffMills>0){
            secondHandler.postDelayed(
                object:Runnable{
                    override fun run() {
                        isUsingErrorIndicator = false
                        mainHandler.post {
                            try {
                                invalidate()
                            }catch(e:Exception){

                            }
                            try{
                                onTurnOffErrorIndicatorListener?.onTurnOff(this@PatternLockView, true)
                            }catch(e:Exception){

                            }
                        }
                    }
                },semiAutoTurningOffMills
            )
        }
    }

    protected final fun getPointItemIndex(originalIndex: Int,pointPosition: Position):Int{
        onCalculateCustomPointOfPatternListener?.let{
            return it.getPointIndex(originalIndex,pointPosition)
        }
            ?:run{
                return originalIndex
            }
    }

    /**
     * clear content of list of selected points
     * @param force flag whether clear forcedly or not
     * @param invalidateView flag whether view should redraw or not
     */
    public fun resetSelectedPoints(force:Boolean = false ,invalidateView: Boolean = true):Boolean{
        if(editMode || force){

            selectedPoints.clear()
            floatingPoint = null
            if(invalidateView) {
                mainHandler.post {
                    invalidate()
                }
            }
        }
        return editMode || force
    }

    /**
     * get currently set lock type
     * @see [LockType]
     * @return current locktype
     */
    public fun getLockTypes():LockType{
        return lockType
    }

    /**
     * get list of selected points
     * @return arraylist of selected points
     */
    public fun getSelectedPointss():ArrayList<SelectedPointItem>{
        return selectedPoints
    }

    /**
     * inherited method
     * -> setting view size method
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        var mHeight = heightSize
        var mWidth = widthSize
        val numOfPoints = when(lockType){

            LockType.SQUARE_3X3_WITH_CHECKER_PATTERN ->{
                5
            }
            LockType.SQUARE_4X4 ->{
                4
            }
            LockType.SQUARE_5X5->{
                5
            }
            LockType.SQUARE_6X6 ->{
                6
            }
            LockType.SQUARE_7X7 ->{
                7
            }
            LockType.HEXAGON_DEFAULT->{
                6
            }
            LockType.HEXAGON_HIGH_DENSITY ->{
                8
            }
            LockType.PENTAGON_DEFAULT ->{
                6
            }
            LockType.PENTAGON_HIGH_DENSITY->{
                8
            }
            LockType.CUSTOM->{
                onCalculateCustomShapePositionListener?.let{
                    max(1,it.getSpacingCount())
                }
                    ?:run{
                        1
                    }
            }
            else->{
                3
            }
        }
        val minSpacing = spacingSizeIfWrapContent.let{if(spacingTypeIfWrapContent==SpacingTypeIfWrapContent.TOTAL){ (it)/numOfPoints}else{it}}
        /**
         * set shouldRecalculateSize attribute dynamically
         */
        shouldRecalculateSize = (widthMode==MeasureSpec.AT_MOST||heightMode==MeasureSpec.AT_MOST)
        /**
         * check width mode and calculate width size if needed
         */
        when(widthMode){
            MeasureSpec.AT_MOST ->{
                mWidth = ((paddingLeft+paddingRight)+ ((2*(pointRadius+clickingJudgementPaddingRadius)+minSpacing)*(1+numOfPoints))).toInt()

            }
            MeasureSpec.EXACTLY->{

            }
        }
        /**
         * check height mode and calculate height size if needed
         */
        when(heightMode){
            MeasureSpec.AT_MOST ->{
                mHeight = ((paddingTop+paddingBottom)+ ((2*(pointRadius+clickingJudgementPaddingRadius)+minSpacing)*(1+numOfPoints))).toInt()
            }
            MeasureSpec.EXACTLY->{

            }
        }
        /**
         * set calculated width and height
         */
        setMeasuredDimension(mWidth,mHeight)
    }

    /**
     * set lock type with associated inner event cycle
     * @see [LockType]
     */
    
    public fun setLockTypes(lockType:LockType,invalidateView:Boolean = true){
        var flag = false
        /**
         * if locktype is different then clear list of pattern points due to recalculate position
         */
        if(this.lockType!=lockType){
            points.clear()
            flag = true
        }
        this.lockType = lockType
        /**
         * if(shouldRecalculateSize=true) then recalculate view size due to different lock type and differnt number of spacing among pattern points
         */
        if(flag&&shouldRecalculateSize){
            mainHandler.post {
                try{
                    requestLayout()
                }catch(e:Exception){

                }
            }

        }
        if(invalidateView){
            mainHandler.post {
                try{
                    invalidate()
                }catch(e:Exception){

                }
            }
        }
    }

    /**
     * set lock type from external class(means not in this class)
     * @param lockType value you want to set
     * @see LockType
     *
     */
    public fun switchPattern(lockType: LockType){
        setLockTypes(lockType,invalidateView = true)
    }

    /**
     * this is for instrumented test
     */
    public fun getTotalNumberOfPatternPoints():Int{
        return points?.let{it.size}?:run{-1}
    }
    public fun getDupPointCount():Int{
        var result = 0
        for(i in points.indices){
            val item = points[i].second
            var hasDup = false
            for(j in i+1 until points.size){
                val item2 = points[j].second
                if(calculateArea(item2.first,item2.second,item,2*pointRadius)){
                    hasDup = true
                    break
                }
            }
            if(hasDup){
                result++
            }
        }
        return result
    }
    /**
     * initialize [points] and calculate positions of pattern points as developer custom shape pattern
     * @param canvas [Canvas] class to obtain canvas width and height
     * @param patternSize let number of pattern points of square shape pattern you want = y*y , then patternSize is y
     */
    protected fun initializedPointsAsCustomShape(canvas: Canvas){
        val doClearPoints = true
        if(doClearPoints) {
            points.clear()
        }
        val maxPointSize = (Int.MAX_VALUE-2)/2
        val doCheckBoundary = true
        val useInsideManagementAlgorithm = useCheckAlgorithmInCustomShape
        onCalculateCustomShapePositionListener?.let {
            it.onCalculateCustomShape(canvasHeight = canvas.height,
                canvasWidth = canvas.width,
                directCanvas = null,
                pointsOfPatternContainer = points,
                pointsOfPatternContainerMaxLength = maxPointSize.toUInt(),
                pointRadius = pointRadius,
                spacingValuesIfWrapContent = Pair<SpacingTypeIfWrapContent,Float>(spacingTypeIfWrapContent,spacingSizeIfWrapContent),
                isPointContainerClearedBeforeThisMethod = doClearPoints,
                customParams = null
            )
            if(points.size>2*maxPointSize){
                val tmp = ArrayList<PointItem>()
                for(i in 0 until min(maxPointSize,points.size)){
                    tmp.add(points[i])
                }
                points = tmp
            }else{
                while(points.size>maxPointSize){
                    points.removeLast()
                }
            }
            if(doCheckBoundary){//it takes more time
                val tmp = points.filter{
                   val position = it.second
                    if(0<=position.first && position.first <= canvas.width
                        &&
                        0<=position.second && position.second <= canvas.height
                    ){
                        return@filter true
                    }
                    return@filter false
                }
                points.clear()
                points.addAll(tmp)

            }
            if(useInsideManagementAlgorithm){//it will be spent more time if enabled
                val useStableAlgorithm = true
                if(useStableAlgorithm){//it may spend more and more time in specific cases, but it will be stable and be able to have no errors
                    /**
                     * This Algorithm test result:
                     *  total test. means running PatternLockViewInstTest(contained purely adding points time) class in device , not mean only this algorithm block
                     *  1. In (1000dp X 1500dp) size canvas
                     *      a. With 10 random points
                     *          70ms in total test
                     *
                     *      b. With 50 random points
                     *          67ms in total test
                     *
                     *      c. With 100 random points
                     *          52ms in total test
                     *
                     *      d. With 500 random points
                     *          130ms in total test
                     *
                     *      e. With 1000 random points
                     *          174ms in total test
                     *
                     *      f. With 2000 random points
                     *          270ms in total test
                     *
                     *      g. With 5000 random points
                     *          812ms in total test
                     *
                     *      h. With 10000 random points
                     *          about 1s in total test
                     *
                     *      i. With 50000 random points
                     *          about 2s in total test
                     *
                     *      j. With 100000 random points
                     *          about 2s in total test
                     *
                     *      k. With 500000 random points
                     *          about 2s in total test
                     *
                     *      l. With 1000000 random points
                     *          about 4s in total test
                     *
                     *      m. With 2000000 random points
                     *          about 6s in total test
                     *
                     *      n. With 2500000 random points
                     *          about 8s in total test
                     *
                     *      o. With 3000000 random points
                     *          out of memory error(due to memory limitation in test env) in test environment
                     *
                     *
                     *  2. In (10000dp X 25000dp) size canvas
                     *      a. With 10 random points
                     *          51 in total test
                     *
                     *      b. With 50 random points
                     *          74ms in total test
                     *
                     *      c. With 100 random points
                     *          81ms in total test
                     *
                     *      d. With 500 random points
                     *          104ms in total test
                     *
                     *      e. With 1000 random points
                     *          142ms in total test
                     *
                     *      f. With 2000 random points
                     *          368ms in total test
                     *
                     *      g. With 5000 random points
                     *           about 1s in total test
                     *
                     *      i. With 10000 random points
                     *          about 5~6s in total test
                     *
                     *      k. With 30000 random points
                     *          1m 13second in total test
                     *
                     *      l. With 50000 random points
                     *          not occured error,but it's over 2minutes in total test
                     *
                     *      m. With 100000 random points
                     *          out of memory error (due to memory limitation in test env) in test environment
                     */

                    val point_radius = pointRadius
                    val divideUnit =  2*(point_radius)+0.000001f
                    val checkRadius = 2.0f*pointRadius
                    val groups = points.groupBy{
                        val item = it.second

                        return@groupBy Pair<Int,Int>((item.first/divideUnit).toInt(),(item.second/divideUnit).toInt())
                    }
                    val groups_with_filtered_content = HashMap<Pair<Int,Int>,List<PointItem>>()
                    for(k in groups.keys){
                        val pvt = groups.get(k)!!
                        val tmp = ArrayList<PointItem>()
                        for(i in pvt.indices){
                            val item1 = pvt[i].second
                            var ok = true
                            for(j in i+1 until pvt.size){
                                val item2 = pvt[j].second
                                if(calculateArea(
                                     item2.first,
                                     item2.second,
                                     item1,
                                        checkRadius
                                )){
                                   ok = false
                                    break
                                }
                            }
                            if(ok){
                                tmp.add(pvt[i])
                            }
                        }
                        groups_with_filtered_content.put(k,tmp.toList())
                    }
                    val _group_ids = groups_with_filtered_content.keys.toList()
                    val defaultComparator = object:Comparator<Pair<Int,Int>>{
                        override fun compare(o1: Pair<Int, Int>, o2: Pair<Int, Int>): Int {
                            if(o1.first==o2.first){
                                return o1.second-o2.second
                            }
                            else{
                                return o1.first-o2.first
                            }
                        }
                    }

                    val group_ids =_group_ids.sortedWith(defaultComparator)

                    val tmpTotList = ArrayList<PointItem>()
                    for(i in 0 until group_ids.size-1){
                        val pivotID = group_ids[i]
                        val pivotPointList = groups_with_filtered_content.get(pivotID)!!
                        var increment = 1
                        val pivotID1 = Pair<Int,Int>(pivotID.first,pivotID.second+increment)

                        val pivotID2 = Pair<Int,Int>(pivotID.first+increment,pivotID.second+increment)
                        val pivotID3 = Pair<Int,Int>(pivotID.first+increment,pivotID.second)
                        val pivotID4 = Pair<Int,Int>(pivotID.first+increment,pivotID.second-increment)

                        val pivotID5 = Pair<Int,Int>(pivotID.first-increment,pivotID.second-increment)
                        val pivotID6 = Pair<Int,Int>(pivotID.first-increment,pivotID.second+increment)
                        val pivotID7 = Pair<Int,Int>(pivotID.first-increment,pivotID.second)


                        val pivotID8 = Pair<Int,Int>(pivotID.first,pivotID.second-increment)
                        val useExtendedArea = false

                        val searchableList = arrayListOf(
                            group_ids.binarySearch(pivotID1,defaultComparator, fromIndex = i+1),
                            group_ids.binarySearch(pivotID2,defaultComparator, fromIndex = i+1) ,
                                group_ids.binarySearch(pivotID3,defaultComparator, fromIndex = i+1),
                            group_ids.binarySearch(pivotID4,defaultComparator, fromIndex = i+1),
                                    //group_ids.binarySearch(pivotID5,defaultComparator, fromIndex = 0),
                        //group_ids.binarySearch(pivotID6,defaultComparator, fromIndex = 0),
                          //      group_ids.binarySearch(pivotID7,defaultComparator, fromIndex = 0),
                           // group_ids.binarySearch(pivotID8,defaultComparator, fromIndex = 0),

                            )
                        if(useExtendedArea){
                            increment++
                            val pivotID9 = Pair<Int,Int>(pivotID.first,pivotID.second+increment)

                            val pivotID10 = Pair<Int,Int>(pivotID.first+increment,pivotID.second+increment)
                            val pivotID11 = Pair<Int,Int>(pivotID.first+increment,pivotID.second)
                            val pivotID12 = Pair<Int,Int>(pivotID.first+increment,pivotID.second-increment)

                            val pivotID13 = Pair<Int,Int>(pivotID.first-increment,pivotID.second-increment)
                            val pivotID14 = Pair<Int,Int>(pivotID.first-increment,pivotID.second+increment)
                            val pivotID15 = Pair<Int,Int>(pivotID.first-increment,pivotID.second)


                            val pivotID16 = Pair<Int,Int>(pivotID.first,pivotID.second-increment)
                            val extendedIds = arrayOf(
                                group_ids.binarySearch(pivotID9,defaultComparator, fromIndex = i+1),
                                group_ids.binarySearch(pivotID10,defaultComparator, fromIndex = i+1) ,
                                group_ids.binarySearch(pivotID11,defaultComparator, fromIndex = i+1),
                                group_ids.binarySearch(pivotID12,defaultComparator, fromIndex = i+1),
                                group_ids.binarySearch(pivotID13,defaultComparator, fromIndex = 0),
                                group_ids.binarySearch(pivotID14,defaultComparator, fromIndex = 0),
                                group_ids.binarySearch(pivotID15,defaultComparator, fromIndex = 0),
                                group_ids.binarySearch(pivotID16,defaultComparator, fromIndex = 0)
                            )
                            searchableList.addAll(extendedIds)
                        }
                        val distinctSearchableList = searchableList.toHashSet()
                        val filteredPointList = ArrayList<PointItem>()

                        for(pPoint in pivotPointList){
                            var ok = true
                            for(id in distinctSearchableList){
                                if(!ok){
                                    break
                                }
                                if(!(i<id&&id<group_ids.size)){
                                    continue
                                }
                                val tmpID1 = group_ids[id]
                                //if(abs(tmpID1.first-pivotID.first)>4 && abs(tmpID1.second-pivotID.second)>4){continue }
                                val tmpList = groups_with_filtered_content.get(tmpID1)!!
                                for(j in tmpList.indices){
                                    val tmpItem2 = tmpList[j].second
                                    if(calculateArea(tmpItem2.first,tmpItem2.second,pPoint.second,checkRadius)){
                                        ok = false
                                        break
                                    }
                                }
                            }
                            if(ok){
                                filteredPointList.add(pPoint)
                            }
                        }
                        groups_with_filtered_content.put(pivotID,filteredPointList)
                    }
                    val tmpResult = ArrayList<PointItem>()
                    for(id in groups_with_filtered_content.keys){
                        groups_with_filtered_content.get(id)?.let{
                            tmpResult.addAll(it)
                        }
                    }
                    if(tmpResult.size>0){
                        points.clear()
                        points = tmpResult
                    }
                }
                else{
                    /*
                    //In (1000dp X 2500dp) Canvas
                    // With 500000 random points
                    //      elapsed 6minutes 22s(382seconds) in test environment
                    // but algorithm in useStableAlgorithm flag is elapsed just 2s in same test environment
                    val tmpList = ArrayList<PointItem>()
                    for(i in points.indices){
                        val item = points[i].second
                        var hasDup = false
                        for(j in i+1 until points.size){
                            val item2 = points[j].second
                            if(calculateArea(item2.first,item2.second,item,2*pointRadius)){
                                hasDup = true
                                break
                            }
                        }
                        if(!hasDup){
                            tmpList.add(points[i])
                        }
                    }
                    points.clear()
                    points = tmpList
                    */

                try{
                val point_radius = pointRadius
                val divideUnit = point_radius+1
                val checkBetweenTwoLists:(List<PointItem>,List<PointItem>)->(Boolean) =  { x1,x2->
                    var result = true
                    var canContinueCheck = true
                    for(i in x1.indices){
                        val item1 = x1[i].second
                        for(j in x2.indices){
                            val item2 = x2[j].second
                            if(!calculateArea(item2.first,item2.second,item1,point_radius)){
                                canContinueCheck = false
                                 result = false
                                break
                            }
                        }
                        if(!canContinueCheck){
                            break
                        }
                    }
                     result
                }
                val searchSecond:(List<PointItem>)->(ArrayList<PointItem>)= {
                    val group2 = it.groupBy { (it.second.first/divideUnit).toInt() }
                    val _group_ids2 = group2.keys.toList()
                    val group_ids2 = _group_ids2.sortedBy { it }
                    val tmp_list = ArrayList<PointItem>()
                    var previous_group:List<PointItem>? = group2.get(group_ids2[0])!!
                    if(group_ids2.size>1) {
                        previous_group = null
                        tmp_list.clear()
                        previous_group = group2.get(group_ids2[0])!!
                        for (k in 1 until group_ids2.size) {

                            val current_group = group2.get(group_ids2[k])!!

                            previous_group = previous_group!!.filter {
                                var cnt = 0
                                val item1 = it.second
                                for (j in current_group.indices) {
                                    val item2 = current_group[j].second
                                    if (calculateArea(
                                            item2.first,
                                            item2.second,
                                            item1,
                                            2*pointRadius
                                        )
                                    ) {
                                        cnt++
                                    }
                                }
                                cnt == 0
                            }
                            previous_group?.let{tmp_list.addAll(it)}

                            previous_group = current_group
                        }
                        previous_group?.let {
                            tmp_list.addAll(it)
                        }
                    }

                    else if(group_ids2.size==1){
                        val grp = group2.get(group_ids2[0])!!
                        for(i in grp.indices){
                            var cnt = 0
                            val item1 = grp[i].second
                            for(j in i+1 until grp.size){
                                val item2 = grp[j].second
                                if (calculateArea(
                                        item2.first,
                                        item2.second,
                                        item1,
                                        point_radius
                                    )
                                ) {
                                    cnt++
                                }
                            }
                            if(cnt==0){
                                tmp_list.add(grp[i])
                            }
                        }
                    }
                    else{
                        tmp_list.addAll(it)
                    }
                    tmp_list
                }

                //points.sortBy { it.second.first }
                //val point_radius = pointRadius

                val group = points.groupBy { (it.second.second/divideUnit).toInt() }
                val _group_ids = group.keys.toList()
                val group_ids = _group_ids.sortedBy { it }
                //points.clear()
                if(group_ids.size>1) {
                    val tmp_list = ArrayList<PointItem>()
                    var previous_group:List<PointItem>? = group.get(group_ids[0])!!
                    for (k in 1 until group_ids.size) {

                        val current_group = group.get(group_ids[k])!!

                        previous_group = previous_group!!.filter{
                            var cnt = 0
                            val item1 = it.second
                            for(j in current_group.indices){
                                val item2 = current_group[j].second
                                if(calculateArea(item2.first,item2.second,item1,2*pointRadius)){
                                    cnt++
                                }
                            }
                            cnt==0
                        }
                        tmp_list.addAll(previous_group!!)

                        previous_group = current_group
                    }
                    previous_group?.let{
                        tmp_list.addAll(it)
                    }
                    var tmp = searchSecond(tmp_list)
                    //points.addAll(tmp_list)
                    tmp?.let {
                        if(it.size>0) {
                            points?.clear()
                            points = it
                        }
                    }
                }
                else if(group_ids.size==1){
                    searchSecond(group.get(group_ids[0])!!)?.let{
                        if(it.size>0){
                            points?.clear()
                            points = it
                        }
                    }

                }
                else{

                }
                }catch(e:Exception){

                }
                }
            }

        }
            ?:run{
                initalizePointsAsSquare(canvas,3)
            }

    }

    /**
     * initialize [points] and calculate positions of pattern points as square shape pattern
     * @param canvas [Canvas] class to obtain canvas width and height
     * @param patternSize let number of pattern points of square shape pattern you want = y*y , then patternSize is y
     */
    protected fun initalizePointsAsSquare(canvas: Canvas,patternSize: Int){
        val height = canvas.height
        val width = canvas.width
        val cx = width/2.0f
        val cy = height/2.0f
        val commonSize = min(max(0,width- (paddingLeft + paddingRight)),max(height - (paddingTop + paddingBottom),0)) - 2*pointRadius
        if(patternSize==-3){
            /**
             * calculate positions of pattern points of checker square shape pattern
             */
            var index = 0
            val fitCircleRadius:Float = (commonSize/(patternSize.toFloat()))
            val practicalCircleRadius = (commonSize/(((-patternSize)*2-1).toFloat()))

            for(i in 0 until (-patternSize)*2 - 1){
                for(j in 0 until (-patternSize)*2 - 1){
                    if(j%2==0 && i%2==1 || j%2==1 && i%2==0){
                        continue
                    }
                    val px = (cx-commonSize/2.0f+practicalCircleRadius/2.0f )+practicalCircleRadius*i.toFloat()
                    val py = (cy-commonSize/2.0f+practicalCircleRadius/2.0f)+practicalCircleRadius*j.toFloat()//min((fitCircleRadius+(2*fitCircleRadius)*i) + paddingTop,height - paddingBottom+0.0f)
                    points.add(Pair<Int,Position>(getPointItemIndex(++index, Position(px,py)),Position(px,py)))
                }
            }
        }else {
            /**
             * calculate positions of pattern points of normal square shape pattern
             */
            val fitCircleRadius:Float = (commonSize/(patternSize.toFloat()))
            var index = 0
            for(i in 0 until patternSize){
                for(j in 0 until patternSize){
                    val px = (cx-commonSize/2.0f+fitCircleRadius/2.0f)+fitCircleRadius*i.toFloat()
                    val py = (cy-commonSize/2.0f+fitCircleRadius/2.0f)+fitCircleRadius*j.toFloat()//min((fitCircleRadius+(2*fitCircleRadius)*i) + paddingTop,height - paddingBottom+0.0f)
                    points.add(Pair<Int,Position>(getPointItemIndex(++index, Position(px,py)),Position(px,py)))
                }
            }
        }
    }

    /**
     * calculate position of pattern points as pentagon shape
     * @param canvas view canvas
     * @param useHighDensity calculate with high density,currently not used
     */
    protected fun initalizePointsAsPentagonShape(canvas: Canvas,useHighDensity: Boolean){
        val height = canvas.height
        val width = canvas.width
        val commonSize = min(max(0,width- (paddingLeft + paddingRight)),max(height - (paddingTop + paddingBottom),0))

        val yOffset = height/2 + 2*pointRadius
        val xOffset = width/2

        /**
         * outer pentagon shape radius
         */
        val radius = commonSize/2.0f - 2*pointRadius
        var index = 0
        if(useHighDensity){
            /**
             * calculate positions of pattern points of normal square shape pattern
             */
            //canvas.drawCircle(cx.toFloat(),cy.toFloat(),100f,tmpPaint)
            /**
             * inner pentagon shape radius
             */
            val halfRadius = (radius*sin(Math.toRadians(54.0))).toFloat()
            val halfOuterRadius = (halfRadius/cos(Math.toRadians(54.0/6))).toFloat()
            val middleHalfRadius = (((2*radius)/3.0f)*sin(Math.toRadians(54.0))).toFloat()
            /**
             * frequency degree of position of outer peak point in pentagon shape
             */
            val stepSize= 72
            for(i in 0 until 360 step stepSize){
                /**
                 * angle degree offset
                 */
                val degOffset:Int = -18

                /**
                 * position of outer point
                 */
                val py = ((sin(Math.toRadians((i+degOffset)%360+0.0)).toFloat())*radius)+yOffset
                val px = ((cos(Math.toRadians((i+degOffset)%360+0.0)).toFloat())*radius)+xOffset

                /**
                 * position of middle point
                 */
                val py2 = ((sin(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*((2*radius)/3.0f))+yOffset
                val px2 = ((cos(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*((2*radius)/3.0f))+xOffset

                /**
                 * position of inner point
                 */
                val py3 = ((sin(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*(radius/3.0f))+yOffset
                val px3 = ((cos(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*(radius/3.0f))+xOffset

                /**
                 * center(a.k.a. average(?)) position of positions between two adjacent outer peak points
                 */
                val py_half = ((sin(Math.toRadians((i+degOffset+stepSize/2)%360+0.0)).toFloat())*middleHalfRadius)+yOffset
                val px_half = ((cos(Math.toRadians((i+degOffset+stepSize/2)%360+0.0)).toFloat())*middleHalfRadius)+xOffset


                val outer_offset_angle = stepSize/3.0f - 1.5
                val py_outer_half1 = ((sin(Math.toRadians((i+outer_offset_angle+degOffset)%360+0.0)).toFloat())*halfOuterRadius)+yOffset
                val px_outer_half1 = ((cos(Math.toRadians((i+outer_offset_angle+degOffset)%360+0.0)).toFloat())*halfOuterRadius)+xOffset

                val py_outer_half2 = ((sin(Math.toRadians((i+(stepSize-outer_offset_angle)+degOffset)%360+0.0)).toFloat())*halfOuterRadius)+yOffset
                val px_outer_half2 = ((cos(Math.toRadians((i+(stepSize-outer_offset_angle)+degOffset)%360+0.0)).toFloat())*halfOuterRadius)+xOffset

                /*
                val pts1 = Position(px,py)
                val pts2 = Position(px2,py2)
                val pts1Half = Position(px_half,py_half)
                points.add(PointItem(getPointItemIndex(++index,pts1),pts1))
                points.add(PointItem(getPointItemIndex(++index,pts1Half),pts1Half))
                points.add(PointItem(getPointItemIndex(++index,pts2),pts2))*/

                val pts1 = Position(px,py)
                val pts2 = Pair<Float,Float>(px2,py2)
                val pts3 = Pair<Float,Float>(px3,py3)
                val pts1Half = Pair<Float,Float>(px_half,py_half)
                val ptsOuterHalf1 = Position(px_outer_half1,py_outer_half1)
                val ptsOuterHalf2 = Position(px_outer_half2,py_outer_half2)

                points.add(PointItem(getPointItemIndex(++index,pts1),pts1))
                points.add(PointItem(getPointItemIndex(++index,pts1Half),pts1Half))
                points.add(PointItem(getPointItemIndex(++index,pts2),pts2))
                points.add(PointItem(getPointItemIndex(++index,pts3),pts3))
                points.add(PointItem(getPointItemIndex(++index,ptsOuterHalf1),ptsOuterHalf1))
                points.add(PointItem(getPointItemIndex(++index,ptsOuterHalf2),ptsOuterHalf2))
            }
            /**
             * real center position of pentagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of pentagon shape
             */
            points.add(PointItem(getPointItemIndex(++index,centerPt),centerPt))
            //canvas.drawPath(outerPaths,tmpPaint)
            //canvas.drawPath(innerPaths,tmpPaint2)
        }
        else{
            /**
             * calculate positions of pattern points of normal square shape pattern
             */
            //canvas.drawCircle(cx.toFloat(),cy.toFloat(),100f,tmpPaint)
            /**
             * inner pentagon shape radius
             */
            val halfRadius = (radius*sin(Math.toRadians(54.0))).toFloat()

            /**
             * frequency degree of position of outer peak point in pentagon shape
             */
            val stepSize= 72
            for(i in 0 until 360 step stepSize){
                /**
                 * angle degree offset
                 */
                val degOffset:Int = -18

                /**
                 * position of outer point
                 */
                val py = ((sin(Math.toRadians((i+degOffset)%360+0.0)).toFloat())*radius)+yOffset
                val px = ((cos(Math.toRadians((i+degOffset)%360+0.0)).toFloat())*radius)+xOffset

                /**
                 * position of inner point
                 */
                val py2 = ((sin(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*(radius/2))+yOffset
                val px2 = ((cos(Math.toRadians(((i+degOffset)%360)+0.0)).toFloat())*(radius/2))+xOffset

                /**
                 * center(a.k.a. average(?)) position of positions between two adjacent outer peak points
                 */
                val py_half = ((sin(Math.toRadians((i+degOffset+stepSize/2)%360+0.0)).toFloat())*halfRadius)+yOffset
                val px_half = ((cos(Math.toRadians((i+degOffset+stepSize/2)%360+0.0)).toFloat())*halfRadius)+xOffset

                val pts1 = Position(px,py)
                val pts2 = Position(px2,py2)
                val pts1Half = Position(px_half,py_half)
                points.add(PointItem(getPointItemIndex(++index,pts1),pts1))
                points.add(PointItem(getPointItemIndex(++index,pts1Half),pts1Half))
                points.add(PointItem(getPointItemIndex(++index,pts2),pts2))


            }
            /**
             * real center position of pentagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of pentagon shape
             */
            points.add(PointItem(getPointItemIndex(++index,centerPt),centerPt))
            //canvas.drawPath(outerPaths,tmpPaint)
            //canvas.drawPath(innerPaths,tmpPaint2)
        }
    }

    /**
     * calculate position of pattern points as hexagon shape
     * @param canvas view canvas
     * @param useHighDensity calculate with high density,currently not used
     */
    protected fun initalizePointsAsHexagonShape(canvas: Canvas,useHighDensity: Boolean){
        val height = canvas.height
        val width = canvas.width
        val commonSize = min(max(0,width- (paddingLeft + paddingRight)),max(height - (paddingTop + paddingBottom),0))
        val yOffset = height/2
        val xOffset = width/2
        val radius = commonSize/2.0f - 2*pointRadius
        var index = 0
        if(useHighDensity){
            //canvas.drawCircle(cx.toFloat(),cy.toFloat(),100f,tmpPaint)
            val getHalfRadius:(Float,Double)->(Float) = {r,angle->
                (r*sin(Math.toRadians(angle))).toFloat()
            }
            /**
             * inner hexagon shape radius
             */
            val halfRadius = getHalfRadius(radius,60.0)//(radius*sin(Math.toRadians(60.0))).toFloat()
            val halfOuterRadius = (halfRadius/ cos(Math.toRadians(10.0))).toFloat()
            val middleHalfRadius = getHalfRadius((2*radius)/3.0f,60.0)
            val stepSize = 60
            for(i in 0 until 360 step stepSize){
                /**
                 * position of outer point
                 */
                val py = ((sin(Math.toRadians(i+0.0)).toFloat())*radius)+yOffset
                val px = ((cos(Math.toRadians(i+0.0)).toFloat())*radius)+xOffset
                /**
                 * position of middle point
                 */
                val py2 = ((sin(Math.toRadians(((i)%360)+0.0)).toFloat())*((2*radius)/3))+yOffset
                val px2 = ((cos(Math.toRadians(((i)%360)+0.0)).toFloat())*((2*radius)/3))+xOffset

                /**
                 * position of inner point
                 */
                val py3 = ((sin(Math.toRadians(((i)%360)+0.0)).toFloat())*(radius/3))+yOffset
                val px3 = ((cos(Math.toRadians(((i)%360)+0.0)).toFloat())*(radius/3))+xOffset

                /**
                 * center(a.k.a. average(?)) position of positions between two adjacent outer peak points
                 */
                val py_half = ((sin(Math.toRadians((i+stepSize/2)%360+0.0)).toFloat())*middleHalfRadius)+yOffset
                val px_half = ((cos(Math.toRadians((i+stepSize/2)%360+0.0)).toFloat())*middleHalfRadius)+xOffset

                /**
                 * center(a.k.a. average(?)) position of positions between two adjacent outer peak points
                 */
                val outer_offset_angle = stepSize/3
                val py_outer_half1 = ((sin(Math.toRadians((i+outer_offset_angle)%360+0.0)).toFloat())*halfOuterRadius)+yOffset
                val px_outer_half1 = ((cos(Math.toRadians((i+outer_offset_angle)%360+0.0)).toFloat())*halfOuterRadius)+xOffset

                val py_outer_half2 = ((sin(Math.toRadians((i+(stepSize-outer_offset_angle))%360+0.0)).toFloat())*halfOuterRadius)+yOffset
                val px_outer_half2 = ((cos(Math.toRadians((i+(stepSize-outer_offset_angle))%360+0.0)).toFloat())*halfOuterRadius)+xOffset

                val pts1 = Position(px,py)
                val pts2 = Pair<Float,Float>(px2,py2)
                val pts3 = Pair<Float,Float>(px3,py3)
                val pts1Half = Pair<Float,Float>(px_half,py_half)
                val ptsOuterHalf1 = Position(px_outer_half1,py_outer_half1)
                val ptsOuterHalf2 = Position(px_outer_half2,py_outer_half2)
                /*points.add(Pair<Int,Pair<Float,Float>>(getPointItemIndex(++index,pts1),pts1))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1Half))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts2))*/
                points.add(PointItem(getPointItemIndex(++index,pts1),pts1))
                points.add(PointItem(getPointItemIndex(++index,pts1Half),pts1Half))
                points.add(PointItem(getPointItemIndex(++index,pts2),pts2))
                points.add(PointItem(getPointItemIndex(++index,pts3),pts3))

                //points.add(PointItem(getPointItemIndex(++index,pts2),pts2))
                points.add(PointItem(getPointItemIndex(++index,ptsOuterHalf1),ptsOuterHalf1))
                points.add(PointItem(getPointItemIndex(++index,ptsOuterHalf2),ptsOuterHalf2))
            }
            /**
             * real center position of hexagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of hexagon shape
             */
            //points.add(Pair<Int,Pair<Float,Float>>(++index,centerPt))
            points.add(PointItem(getPointItemIndex(++index,centerPt),centerPt))
            //canvas.drawPath(outerPaths,tmpPaint)
        }
        else{
            //canvas.drawCircle(cx.toFloat(),cy.toFloat(),100f,tmpPaint)
            /**
             * inner hexagon shape radius
             */
            val stepSize = 60
            val halfRadius = (radius*sin(Math.toRadians((180.0-stepSize.toDouble())/2.0))).toFloat()

            for(i in 0 until 360 step stepSize){
                /**
                 * position of outer point
                 */
                val py = ((sin(Math.toRadians(i+0.0)).toFloat())*radius)+yOffset
                val px = ((cos(Math.toRadians(i+0.0)).toFloat())*radius)+xOffset
                /**
                 * position of inner point
                 */
                val py2 = ((sin(Math.toRadians(((i)%360)+0.0)).toFloat())*(radius/2))+yOffset
                val px2 = ((cos(Math.toRadians(((i)%360)+0.0)).toFloat())*(radius/2))+xOffset
                /**
                 * center(a.k.a. average(?)) position of positions between two adjacent outer peak points
                 */
                val py_half = ((sin(Math.toRadians((i+stepSize/2)%360+0.0)).toFloat())*halfRadius)+yOffset
                val px_half = ((cos(Math.toRadians((i+stepSize/2)%360+0.0)).toFloat())*halfRadius)+xOffset

                val pts1 = Position(px,py)
                val pts2 = Pair<Float,Float>(px2,py2)
                val pts1Half = Pair<Float,Float>(px_half,py_half)
                /*points.add(Pair<Int,Pair<Float,Float>>(getPointItemIndex(++index,pts1),pts1))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1Half))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts2))*/
                points.add(PointItem(getPointItemIndex(++index,pts1),pts1))
                points.add(PointItem(getPointItemIndex(++index,pts1Half),pts1Half))
                points.add(PointItem(getPointItemIndex(++index,pts2),pts2))
            }
            /**
             * real center position of hexagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of hexagon shape
             */
            //points.add(Pair<Int,Pair<Float,Float>>(++index,centerPt))
            points.add(PointItem(getPointItemIndex(++index,centerPt),centerPt))
            //canvas.drawPath(outerPaths,tmpPaint)
        }
    }

    public fun doInitPts4InstTest():Int{
        initializePointsBy(Canvas(Bitmap.createBitmap((dip1*1000).toInt(),(dip1*2500).toInt(),Bitmap.Config.ALPHA_8)),lockType)
        return getTotalNumberOfPatternPoints()
    }

    /**
     * initialize positions of pattern points by lockType
     * @param canvas view canvas
     * @param lockType pivot lock type
     */
    protected fun initializePointsBy(canvas: Canvas,lockType: LockType){
        points.clear()
        selectedPoints.clear()
        lockType.let{
            when(it){

                    LockType.SQUARE_3X3 ->{
                        initalizePointsAsSquare(canvas,3)
                    }

                    LockType.SQUARE_3X3_WITH_CHECKER_PATTERN ->{
                        initalizePointsAsSquare(canvas,-3)
                    }
                    LockType.SQUARE_5X5->{
                        initalizePointsAsSquare(canvas,5)
                    }
                    LockType.SQUARE_6X6 ->{
                        initalizePointsAsSquare(canvas,6)
                    }
                    LockType.SQUARE_7X7 ->{
                        initalizePointsAsSquare(canvas,7)
                    }
                    LockType.HEXAGON_DEFAULT->{
                        initalizePointsAsHexagonShape(canvas,useHighDensity=false)
                    }
                    LockType.HEXAGON_HIGH_DENSITY ->{
                        initalizePointsAsHexagonShape(canvas,useHighDensity = true)
                    }
                    LockType.PENTAGON_DEFAULT ->{
                        initalizePointsAsPentagonShape(canvas,useHighDensity=false)
                    }
                    LockType.PENTAGON_HIGH_DENSITY->{
                        initalizePointsAsPentagonShape(canvas,useHighDensity=true)
                    }
                    LockType.CUSTOM ->{
                        initializedPointsAsCustomShape(canvas)
                    }
                    else ->{//LockType.SQUARE_4X4
                        initalizePointsAsSquare(canvas,4)
                    }

            }
        }
        //onFinishInitializePoints?.onFinished(this)
    }

    /**
     *  initialize inner variables commonly
     */
    protected final fun initializeVars(context:Context){
        if(!isInEditMode) {
            try {
                vibrationUtil = VibrationUtil(context)
            }catch(e:Exception){
                android.util.Log.e("VibrationUtil","Initialization Error: Your Device may not supported vibrator.")
            }
        }
        useVibratorIfAvaliable = true
        shouldShowTrajectoryLines = true
        
        secondHandler = Handler(Looper.myLooper()?.let{it}?:run{Looper.getMainLooper()})
        mainHandler = Handler(Looper.getMainLooper())
        //setLockTypes(Lock)
        val defaultLockType = LockType.SQUARE_3X3
        this.lockType = defaultLockType
        dip1 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,1f,context.resources.displayMetrics)
        setSpacingTypeIfWrapContent(SpacingTypeIfWrapContent.TOTAL,100*dip1,invalidateView = false)
        pointRadius = dip1*11
        clickingJudgementPaddingRadius = dip1*5
        setLockTypes(defaultLockType, invalidateView = false)
        points = ArrayList<Pair<Int,Pair<Float,Float>>>()
        points.clear()
        selectedPoints = ArrayList<SelectedPointItem>()
        selectedPoints.clear()
        linePaint = Paint()
        linePaint.apply{
            val typedValue =  TypedValue()
            val theme = context.theme
            //theme.resolveAttribute(R.attr.theme_color, typedValue, true);
            //@ColorInt int color = typedValue.data;

            var def_color = Color.BLACK
            try {
                if (theme.resolveAttribute(android.R.attr.textAppearance, typedValue, true)) {
                    val res_id = typedValue.resourceId //current_themed_value.data
                    var tmp_typed_array: TypedArray = context.theme.obtainStyledAttributes(
                        res_id,
                        IntArray(1, { android.R.attr.textColor })
                    )
                    if (Build.VERSION.SDK_INT >= 23) {
                        def_color =
                            context.resources.getColor(
                                tmp_typed_array.getResourceId(0, 0),
                                context.theme
                            )
                    } else {
                        def_color = context.resources.getColor(tmp_typed_array.getResourceId(0, 0))
                    }
                }
            }catch(e:Exception){

            }
            selectedPointColor = def_color
            selectedPointErrorColor = Color.RED

            this.color = def_color
            this.strokeCap = Paint.Cap.ROUND
            this.strokeWidth = 3*dip1//pointRadius/3
            this.style = Paint.Style.STROKE
            if(useTrajectoryLineShadow) {
                this.setShadowLayer(dip1, 1f, 1f, Color.GRAY)
            }
            else{
                this.clearShadowLayer()
            }
        }
        pointPaint = Paint()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pointColor = context.getColor(R.color.purple_700)

            } else {
                pointColor = context.resources.getColor(R.color.purple_700)
            }
        }catch(e:Exception){

        }
        pointErrorColor = Color.MAGENTA
        pointPaint.apply{
            indicatorBlurColor = Color.GRAY
            indicatorBlurDx = 1f
            indicatorBlurDy = 1f
            this.setShadowLayer(dip1,indicatorBlurDx,indicatorBlurDy,indicatorBlurColor)
            this.color = pointColor
            this.isAntiAlias = true
        }

        this.setOnTouchListener(this)

    }

    /**
     * parse and set attributes from layout xml
     */
    private final fun setAttrs(context: Context,attrs: AttributeSet,defStyle:Int = -1){
        var attributes: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.PatternLockView)
        attrs?.let{
            if(defStyle!=-1)
                attributes = context.obtainStyledAttributes(it, R.styleable.PatternLockView,defStyle,0)
        }
        val attr_indicator_color:Int = attributes.getColor(R.styleable.PatternLockView_indicatorColor,pointColor)
        pointColor = attr_indicator_color

        val attr_indicator_error_color:Int = attributes.getColor(R.styleable.PatternLockView_indicatorErrorColor,pointErrorColor)
        pointErrorColor = attr_indicator_error_color



        var attr_indicator_radius = attributes.getDimension(R.styleable.PatternLockView_indicatorRadius,pointRadius)
        pointRadius = min(max( attr_indicator_radius,MIN_POINT_RADIUS),MAX_POINT_RADIUS)

        clickingJudgementPaddingRadius = attributes.getDimension(R.styleable.PatternLockView_recommendedClickingJudgementAreaPaddingRadius,clickingJudgementPaddingRadius)


        useVibratorIfAvaliable = attributes.getBoolean(R.styleable.PatternLockView_useVibratorIfAvailable,useVibratorIfAvaliable)
        shouldShowTrajectoryLines = attributes.getBoolean(R.styleable.PatternLockView_showTrajectoryLines,shouldShowTrajectoryLines)
        useTrajectoryLineShadow = attributes.getBoolean(R.styleable.PatternLockView_useTrajectoryLineShadow,useTrajectoryLineShadow)


        var attr_trajectory_line_thickness = attributes.getDimension(R.styleable.PatternLockView_trajectoryLineThickness,linePaint.strokeWidth)
        linePaint.strokeWidth = attr_trajectory_line_thickness
        var attr_trajectory_line_color = attributes.getColor(R.styleable.PatternLockView_trajectoryLineColor,selectedPointColor)
        selectedPointColor = attr_trajectory_line_color
        //linePaint.color = attr_trajectory_line_color

        var attr_trajectory_line_error_color = attributes.getColor(R.styleable.PatternLockView_trajectoryLineErrorColor,selectedPointErrorColor)
        selectedPointErrorColor = attr_trajectory_line_error_color

        try {
            var attr_indicator_blur_color = attributes.getColor(
                R.styleable.PatternLockView_pointBlurColor,
                indicatorBlurColor
            )
            var attr_indicator_blur_dx = attributes.getDimension(
                R.styleable.PatternLockView_pointBlurDx,
                indicatorBlurDx
            )
            var attr_indicator_blur_dy = attributes.getDimension(
                R.styleable.PatternLockView_pointBlurDy,
                indicatorBlurDy
            )

            indicatorBlurColor = attr_indicator_blur_color
            indicatorBlurDx = attr_indicator_blur_dx
            indicatorBlurDy = attr_indicator_blur_dy
            pointPaint.setShadowLayer(dip1, indicatorBlurDx, indicatorBlurDy, indicatorBlurColor)
        }catch(e:Exception){

        }

        val attr_pattern_type = attributes.getInt(R.styleable.PatternLockView_patternType,lockType.intValue)
        for(lt in LockType.values()){
            if(attr_pattern_type==lt.intValue&&attr_pattern_type!=LockType.CUSTOM.intValue){
                setLockTypes(lt,invalidateView = false)
                break
            }
        }

        val attr_spacing_size_if_wrap_content = attributes.getDimension(R.styleable.PatternLockView_spacingSizeIfWrapContent,spacingSizeIfWrapContent)

        val attr_spacing_type_if_wrap_content = attributes.getInt(R.styleable.PatternLockView_spacingTypeIfWrapContent,spacingTypeIfWrapContent.intValue)
        for(lt in SpacingTypeIfWrapContent.values()){
            if(attr_spacing_type_if_wrap_content==lt.intValue){
                setSpacingTypeIfWrapContent(lt,attr_spacing_size_if_wrap_content)
                break
            }
        }

    }

    /**
     * render all pattern positions by your lock type
     * @param canvas view canvas
     * @param points list of positions of pattern points
     * @param point_radius how big size(a.k.a. circle radius) of points per one of pattern points
     */
    protected fun drawPoints(canvas: Canvas,points:ArrayList<Pair<Int,Pair<Float,Float>>>,point_radius:Float = pointRadius){
        pointPaint.color = if(isUsingErrorIndicator){pointErrorColor}else{pointColor}
        for(pts in points){

            val point_index = pts.first
            val point_pos = pts.second
            canvas.drawCircle(point_pos.first,point_pos.second,point_radius,pointPaint)
        }
    }


    /**
     * render trajectory lines from selected pattern points
     * @param canvas view canvas
     * @param points list of selected points
     * @param with_lastline render lines with position of you touched and position of last of selected points
     * @param only_lastline render only line between position of you touched and position of last of selected points
     * @param only_lastline render only line between position of you touched and position of last of selected points
     */
    protected fun drawLines(canvas: Canvas,points:ArrayList<SelectedPointItem>,with_lastline:Boolean = false,only_lastline:Boolean = false){
        linePaint.color = if(isUsingErrorIndicator){selectedPointErrorColor}else{selectedPointColor}
        if(selectedPoints.size>0){
            //linePaint.color = Color.BLACK
            if(!only_lastline){
                for(i in 1 until selectedPoints.size){
                    val beforePoint = selectedPoints.get(i-1).second.second
                    val curPoint = selectedPoints.get(i).second.second
                    canvas.drawLine(beforePoint.first,beforePoint.second,curPoint.first,curPoint.second,linePaint)
                }
            }
            if(with_lastline) {
                floatingPoint?.let {
                    val beforePoint = selectedPoints.get(selectedPoints.size - 1).second.second
                    val curPoint = it
                    canvas.drawLine(
                        beforePoint.first,
                        beforePoint.second,
                        curPoint.first,
                        curPoint.second,
                        linePaint
                    )
                }
            }
        }
    }

    /**
     * inherited method
     *
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(points.size==0){
            initializePointsBy(canvas,lockType)
        }
        if(shouldShowTrajectoryLines) {
            drawLines(canvas, selectedPoints, with_lastline = true)
        }
        drawPoints(canvas,points)
    }

    /**
     * calculate distance between pattern points and position you touched , and judge whether position you touched is in boundary of each pattern points or not
     * @param x x-axis position you touched
     * @param y y-axis position you touched
     * @param targetPoint pre-defined pivot position(e.g. one of pattern points)
     * @param radius boundary distance between center of [targetPoint] and boundary of [targetPoint] for judgement
     * @return true or false, if(true) then it means dissolved position (x,y) is in boundary of [targetPoint]
     */
    protected fun calculateArea(x:Float,y:Float,targetPoint:Pair<Float,Float>,radius:Float):Boolean{
        return ( Math.pow((targetPoint.first-x).toDouble(),2.0)+Math.pow((targetPoint.second-y).toDouble(),2.0))<=Math.pow(radius.toDouble(),2.0)
    }

    /**
     * check whether index of one of pattern points is in list of selected points(a.k.a. [selectedPoints]))
     * @param index index value of list of pattern points
     *
     * @return true or false, if(true) then it means that dissolved point is in list of selected points
     */
    protected fun checkIndexNumberExists(index:Int):Boolean{
        for(item in selectedPoints){
            if(index==item.second.first){
                return true
            }
        }
        return false
    }

    /**
     * inherited method
     * this method is for touch event
     */
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        //debug code
        /*val actionString = when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{
                "ACTION_DOWN"
            }
            MotionEvent.ACTION_MOVE->{
                "ACTION_MOVE"
            }
            MotionEvent.ACTION_UP->{
                "ACTION_UP"
            }
            MotionEvent.ACTION_CANCEL->{
                "ACTION_CANCEL"
            }
            MotionEvent.ACTION_HOVER_MOVE->{
                "ACTION_HOVER_MOVE"
            }
            MotionEvent.ACTION_HOVER_ENTER->{
                "ACTION_HOVER_ENTER"
            }
            MotionEvent.ACTION_HOVER_EXIT->{
                "ACTION_HOVER_EXIT"
            }
            MotionEvent.ACTION_SCROLL->{
                "ACTION_SCROLL"
            }

            MotionEvent.ACTION_POINTER_UP->{
                "ACTION_POINTER_UP"
            }
            MotionEvent.ACTION_POINTER_DOWN->{
                "ACTION_POINTER_DOWN"
            }
            MotionEvent.ACTION_BUTTON_PRESS->{
                "ACTION_BUTTON_PRESS"
            }
            MotionEvent.ACTION_BUTTON_RELEASE->{
                "ACTION_BUTTON_RELEASE"
            }

            else->{
                "action unknown"
            }
        }
        android.util.Log.e("onTouch",event.action.toString()+"  "+event.actionMasked.toString()+" "+actionString)
        */
        if(isReadOnlyMode){
            return true
        }
        when( event.actionMasked){
            MotionEvent.ACTION_DOWN , MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_ENTER
                ,MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_SCROLL,MotionEvent.ACTION_BUTTON_PRESS
            , MotionEvent.ACTION_POINTER_DOWN
            -> {
                /**
                 * start or continue draw pattern
                 */
                synchronized(selectedPointsLocker){
                    canCollectSelectedPoints = true
                }
                secondHandler.post {
                    /**
                     * check in background thread
                     */
                    val cpX = x
                    val cpY = y
                    val startSearchingTime = System.nanoTime()

                    System.nanoTime()
                    var isFoundAvailablePoint = false
                    for (i in points.indices) {
                        val item = points[i]
                        val index_number = item.first
                        val area = item.second
                        if(isReadOnlyMode){
                            break
                        }
                        if (calculateArea(
                                cpX,
                                cpY,
                                area,
                                pointRadius + clickingJudgementPaddingRadius
                            )
                        ) {
                            synchronized(selectedPointsLocker) {
                                if (//last_touch_point_index!=i&&
                                    canCollectSelectedPoints&&
                                    !checkIndexNumberExists(index_number)) {
                                    isFoundAvailablePoint = true
                                    if (useVibratorIfAvaliable) {
                                        vibrationUtil?.vibrateAsClick()
                                    }
                                    //last_touch_point_index = i

                                    selectedPoints.add(SelectedPointItem(startSearchingTime, item))
                                    if (selectedPoints.size == 1) {
                                        //closeMonitorInputThread()
                                        //startMonitorInputThread()

                                    }
                                    else if(selectedPoints.size>1){
                                        /**
                                         * sort list of selected point due to synchronizing sequence
                                         */
                                        selectedPoints.sortBy {  it.first}

                                    }

                                }
                            }
                            break
                        }
                    }
                    if(isFoundAvailablePoint){
                        mainHandler.post {
                            invalidate()
                        }
                    }
                    //invalidate()
                }
                if(selectedPoints.size>0&&!isReadOnlyMode){
                    floatingPoint = Pair<Float,Float>(x,y)
                    mainHandler.post {
                        invalidate()
                    }
                }

            }
            else ->{
                /*if(editMode) {
                    floatingPoint = null
                    invalidate()

                }else{
                    floatingPoint = null
                    invalidate()
                }*/
                floatingPoint?.also {
                    /**
                     * user do not touch any of this view, so release drawing pattern
                     */
                    floatingPoint = null
                    mainHandler.post {
                        invalidate()
                    }
                }
                val cloneSelectedPoints = synchronized(selectedPointsLocker) {canCollectSelectedPoints = false
                    ArrayList<SelectedPointItem>(selectedPoints)}
                if(cloneSelectedPoints.size>0) {
                    onTaskPatternListener?.let{it.onFinishedPatternSelected(
                        this,
                        editMode,
                        lockType,
                        cloneSelectedPoints
                    )}
                        ?:run{
                            /**
                             * this view is not manipulated or managed by data controller or activity or fragment
                             * so reset selected points self
                             */
                            resetSelectedPoints(force=true)
                        }
                }
                else{
                    onTaskPatternListener?.onNothingSelected()
                }
            }
        }
        /*if(selectedPoints.size>0){
            invalidate()
        }
        else{
            onTaskPatternListener?.onNothingSelected()
        }*/
        return true
    }

}