package com.github.imscs21.pattern_locker

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.imscs21.pattern_locker.views.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class SimplePatternLockerDataControllerInstTest {
    var patternLockerDataController: PatternLockerDataController.SimpleDataStorageBehavior? = null
    @Before
    fun setUP(){

    }
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        //Assert.assertEquals("com.github.imscs21.pattern_locker", appContext.packageName)
        patternLockerDataController = PatternLockerDataController.SimpleDataStorageBehavior(appContext.applicationContext)
        Assert.assertNotNull(patternLockerDataController)
        patternLockerDataController?.let {controller->

            Assert.assertEquals(true,
                controller.resetPattern()
            )
            Assert.assertNull(
                controller.loadPattern()
            )
            val tmpList = ArrayList<Int>()

            for(i in 1 until 99999){
                tmpList.add(i)
            }
            for(lockType in PatternLockView.LockType.values()){
                val selectedPoints = ArrayList<SelectedPointItem>()
                val randInts = IntArray(10, { Random.nextInt(2, tmpList.size / 2 - 1) })
                for(ri in randInts){
                    tmpList.shuffle()
                    selectedPoints.clear()
                    for(i in 0 until ri){
                        selectedPoints.add(SelectedPointItem(i.toLong(), PointItem(tmpList[i], Position(23f,43f))))
                    }
                    val tmpHash = selectedPoints.let{
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
                        Base64.encode(md.digest(),Base64.DEFAULT).toString(Charsets.UTF_8)
                    }.toString().trim()

                    Assert.assertEquals(true,
                        controller.savePattern(lockType,selectedPoints)
                    )
                    val doTestLoadingData = false

                    if(doTestLoadingData) {
                        val loadedPattern = controller.loadPattern()
                        Assert.assertNotNull(loadedPattern)
                        Assert.assertEquals(tmpHash, loadedPattern!!.trim())
                    }
                    Assert.assertEquals(true,
                        controller.checkPattern(tmpHash.toString(),lockType,selectedPoints)
                    )
                }
            }
        }
    }
}