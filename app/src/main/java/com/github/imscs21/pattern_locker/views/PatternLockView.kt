package com.github.imscs21.pattern_locker.views

import android.content.*
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import kotlin.math.min
import android.graphics.*
import android.os.*
import android.widget.Toast
import androidx.core.content.PackageManagerCompat
import androidx.core.os.ExecutorCompat
import com.github.imscs21.pattern_locker.R
import com.github.imscs21.pattern_locker.utils.ThreadLockerPackage
import com.github.imscs21.pattern_locker.utils.VibrationUtil
import kotlinx.coroutines.selects.select
import java.util.concurrent.Executor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Pattern View
 * @author imscs21
 * @since 2022-11-1
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
        HEXAGON_HIGH_DENSITY("HEXAGON_SHAPE_HD",10)
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
        MIN_POINT_RADIUS = value*3
        MAX_POINT_RADIUS = value*100
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
     * color property of pattern point
     */
    protected var pointColor:Int = Color.CYAN

    /**
     * vibration util instance when clicking pattern points
     */
    protected var vibrationUtil: VibrationUtil? = null

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
                handler.post {
                    invalidate()
                }
            }catch(e:Exception){

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
            handler.post {
                invalidate()
            }
        }catch(e:Exception){

        }
    }

    constructor(context:Context):super(context){
        initVars(context)
    }
    constructor(context:Context,attrs:AttributeSet):super(context,attrs){
        initVars(context)
        setAttrs(context, attrs)
    }
    constructor(context: Context,attrs:AttributeSet,defStyle:Int):super(context, attrs,defStyle){
        initVars(context)
        setAttrs(context, attrs, defStyle)
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
                6
            }
            LockType.PENTAGON_DEFAULT ->{
                6
            }
            LockType.PENTAGON_HIGH_DENSITY->{
                6
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
                    points.add(Pair<Int,Pair<Float,Float>>(++index,Pair<Float,Float>(px,py)))
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
                    points.add(Pair<Int,Pair<Float,Float>>(++index,Pair<Float,Float>(px,py)))
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

        val yOffset = height/2
        val xOffset = width/2

        /**
         * outer pentagon shape radius
         */
        val radius = commonSize/2.0f - pointRadius
        var index = 0
        if(useHighDensity){
            throw UnsupportedOperationException("Not supported yet. :P")
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

                val pts1 = Pair<Float,Float>(px,py)
                val pts2 = Pair<Float,Float>(px2,py2)
                val pts1Half = Pair<Float,Float>(px_half,py_half)
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1Half))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts2))
            }
            /**
             * real center position of pentagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of pentagon shape
             */
            points.add(Pair<Int,Pair<Float,Float>>(++index,centerPt))
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
        val radius = commonSize/2.0f - pointRadius
        var index = 0
        if(useHighDensity){
            throw UnsupportedOperationException("Not supported yet. :P")
        }
        else{
            //canvas.drawCircle(cx.toFloat(),cy.toFloat(),100f,tmpPaint)
            /**
             * inner hexagon shape radius
             */
            val halfRadius = (radius*sin(Math.toRadians(60.0))).toFloat()
            for(i in 0 until 360 step 60){
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
                val py_half = ((sin(Math.toRadians((i+30)%360+0.0)).toFloat())*halfRadius)+yOffset
                val px_half = ((cos(Math.toRadians((i+30)%360+0.0)).toFloat())*halfRadius)+xOffset

                val pts1 = Pair<Float,Float>(px,py)
                val pts2 = Pair<Float,Float>(px2,py2)
                val pts1Half = Pair<Float,Float>(px_half,py_half)
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts1Half))
                points.add(Pair<Int,Pair<Float,Float>>(++index,pts2))
            }
            /**
             * real center position of hexagon shape
             */
            val centerPt = Pair<Float,Float>(xOffset.toFloat(),yOffset.toFloat())
            /**
             * add real center position of hexagon shape
             */
            points.add(Pair<Int,Pair<Float,Float>>(++index,centerPt))
            //canvas.drawPath(outerPaths,tmpPaint)
        }
    }

    /**
     * initialize positions of pattern points by lockType
     * @param canvas view canvas
     * @param lockType pivot lock type
     */
    protected fun initalizePointsBy(canvas: Canvas,lockType: LockType){
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
                    else ->{//LockType.SQUARE_4X4
                        initalizePointsAsSquare(canvas,4)
                    }

            }
        }
    }

    /**
     *  initialize inner variables commonly
     */
    protected final fun initVars(context:Context){
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
            if(theme.resolveAttribute(android.R.attr.textAppearance,typedValue,true)){
                val res_id = typedValue.resourceId //current_themed_value.data
                var tmp_typed_array: TypedArray = context.theme.obtainStyledAttributes(
                    res_id,
                    IntArray(1, { android.R.attr.textColor })
                )
                if (Build.VERSION.SDK_INT >= 23) {
                    def_color =
                        context.resources.getColor(tmp_typed_array.getResourceId(0, 0), context.theme)
                } else {
                    def_color = context.resources.getColor(tmp_typed_array.getResourceId(0, 0))
                }
            }
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
        pointPaint.apply{
            this.setShadowLayer(dip1,1f,1f,Color.GRAY)
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



        var attr_indicator_radius = attributes.getDimension(R.styleable.PatternLockView_indicatorRadius,pointRadius)
        pointRadius = min(max( attr_indicator_radius,MIN_POINT_RADIUS),MAX_POINT_RADIUS)

        clickingJudgementPaddingRadius = attributes.getDimension(R.styleable.PatternLockView_recommendedClickingJudgementAreaPaddingRadius,clickingJudgementPaddingRadius)


        useVibratorIfAvaliable = attributes.getBoolean(R.styleable.PatternLockView_useVibratorIfAvailable,useVibratorIfAvaliable)
        shouldShowTrajectoryLines = attributes.getBoolean(R.styleable.PatternLockView_showTrajectoryLines,shouldShowTrajectoryLines)
        useTrajectoryLineShadow = attributes.getBoolean(R.styleable.PatternLockView_useTrajectoryLineShadow,useTrajectoryLineShadow)


        var attr_trajectory_line_thickness = attributes.getDimension(R.styleable.PatternLockView_trajectoryLineThickness,linePaint.strokeWidth)
        linePaint.strokeWidth = attr_trajectory_line_thickness
        var attr_trajectory_line_color = attributes.getColor(R.styleable.PatternLockView_trajectoryLineColor,linePaint.color)
        linePaint.color = attr_trajectory_line_color



        val attr_pattern_type = attributes.getInt(R.styleable.PatternLockView_patternType,lockType.intValue)
        for(lt in LockType.values()){
            if(attr_pattern_type==lt.intValue){
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
        pointPaint.color = pointColor
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
     */
    protected fun drawLines(canvas: Canvas,points:ArrayList<SelectedPointItem>,with_lastline:Boolean = false,only_lastline:Boolean = false){

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
            initalizePointsBy(canvas,lockType)
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
                    val startSearchingTime = System.currentTimeMillis()
                    var isFoundAvailablePoint = false
                    for (i in points.indices) {
                        val item = points[i]
                        val index_number = item.first
                        val area = item.second

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
                                        selectedPoints.sortedBy {  it.first}
                                    }

                                }
                            }
                            break
                        }
                    }
                    if(isFoundAvailablePoint){
                        handler.post {
                            invalidate()
                        }
                    }
                    //invalidate()
                }
                if(selectedPoints.size>0){
                    floatingPoint = Pair<Float,Float>(x,y)
                    handler.post {
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
                    handler.post {
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