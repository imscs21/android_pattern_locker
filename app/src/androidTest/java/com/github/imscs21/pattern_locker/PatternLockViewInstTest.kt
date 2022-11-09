package com.github.imscs21.pattern_locker

import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.imscs21.pattern_locker.views.PatternLockView
import com.github.imscs21.pattern_locker.views.PatternLockerDataController
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.view.*
import com.github.imscs21.pattern_locker.views.PointItem
import com.github.imscs21.pattern_locker.views.Position
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PatternLockViewInstTest {
    protected lateinit var dynamicPatternLockView: PatternLockView
    protected lateinit var patternLockView: PatternLockView
    @Before
    fun setUP(){

    }
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        //Assert.assertEquals("com.github.imscs21.pattern_locker", appContext.packageName)
        Assert.assertNotNull(appContext)
        val xmlView = LayoutInflater.from(appContext).inflate(com.github.imscs21.pattern_locker.R.layout.layout,null,false)

        Assert.assertNotNull(xmlView)
        Assert.assertTrue(xmlView is PatternLockView)
        patternLockView = xmlView as PatternLockView
        val random = Random(Random.nextInt(0,999999))
        patternLockView.apply {

            onCalculateCustomShapePositionListener = object:PatternLockView.OnCalculateCustomShapePositionListener{
                override fun onCalculateCustomShape(
                    canvasWidth: Int,
                    canvasHeight: Int,
                    directCanvas: Canvas?,
                    pointsOfPatternContainer: ArrayList<PointItem>,
                    pointsOfPatternContainerMaxLength: UInt,
                    pointRadius: Float,
                    spacingValuesIfWrapContent: Pair<PatternLockView.SpacingTypeIfWrapContent, Float>,
                    isPointContainerClearedBeforeThisMethod: Boolean,
                    customParams: Any?
                ) {
                    val tmp_size = 2500000
                    val xposes = FloatArray(tmp_size,{Random.nextInt(0,canvasWidth).toFloat()})
                    val yposes = FloatArray(tmp_size,{Random.nextInt(0,canvasHeight).toFloat()})
                    for(i in xposes.indices){
                        pointsOfPatternContainer.add(PointItem(i, Position(xposes[i],yposes[i])))
                    }

                }

                override fun getSpacingCount(): Int {
                    return 10
                }
            }
            /*onFinishInitializePoints = object:PatternLockView.OnFinishInitializePoints{
                override fun onFinished(view: PatternLockView) {
                    Assert.assertEquals(4000,patternLockView.getTotalNumberOfPatternPoints())
                }

            }*/
            setLockTypes(PatternLockView.LockType.CUSTOM)
        }
        patternLockView.invalidate()
        val listsize = -1//patternLockView.doInitPts4InstTest()
        val dupcount = patternLockView.getDupPointCount()
        Assert.assertNotEquals(0,listsize)
        Assert.assertTrue("all points are duplicated",listsize>dupcount)
        Assert.assertEquals(0,dupcount)
        //Assert.assertEquals(123,random.nextInt(0,500))
        //Assert.assertEquals(4000,patternLockView.doInitPts4InstTest())
        //Assert.assertEquals(4000,patternLockView.getTotalNumberOfPatternPoints())

        dynamicPatternLockView = PatternLockView(appContext)

    }
}