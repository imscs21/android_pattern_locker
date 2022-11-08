package com.github.imscs21.pattern_locker_test

import android.content.Context
import android.graphics.Canvas
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import com.github.imscs21.pattern_locker_test.R
import com.github.imscs21.pattern_locker.views.PatternLockView
import com.github.imscs21.pattern_locker.views.PatternLockerDataController
import com.github.imscs21.pattern_locker.views.PointItem
import com.github.imscs21.pattern_locker.views.SelectedPointItem
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    protected lateinit var patternLockView: PatternLockView

    protected lateinit var patternSwitcher:AppCompatSpinner

    protected lateinit var resetPatternButton:AppCompatButton

    protected lateinit var taskPatternButton:AppCompatButton

    protected lateinit var editModeButton:SwitchMaterial

    protected lateinit var patternDataContoller:PatternLockerDataController<String>

    protected lateinit var forceErrorButton: AppCompatButton

    protected var listAdapterIndex:Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        patternLockView = findViewById(R.id.pattern_lock_view)

        patternSwitcher = findViewById(R.id.pattern_selector)
        editModeButton = findViewById(R.id.switch_edit_mode)
        resetPatternButton = findViewById(R.id.button_reset_pattern)
        taskPatternButton = findViewById(R.id.button_pattern_task)
        forceErrorButton = findViewById(R.id.button_use_error)
        val sharedPreference = getSharedPreferences("pattern_locker_preferences", Context.MODE_PRIVATE)
        val lastPatternTypeIndex  = sharedPreference.getInt("pattern_type",-99)
        val lockTypes = PatternLockView.LockType.values()
        var lastLockType:PatternLockView.LockType? = null
        if(0<lastPatternTypeIndex&&lastPatternTypeIndex<=lockTypes.size){
            for(lt in lockTypes){
                if(lt.intValue==lastPatternTypeIndex){
                    lastLockType = lt
                    break
                }
            }
        }
        forceErrorButton.setOnClickListener {

            patternLockView.apply {

                isReadOnlyMode = true
                patternLockView.turnErrorIndicator(
                    true,
                    invalidateView = true,
                    semiAutoTurningOffMills = 4000
                )
                onTurnOffErrorIndicatorListener =
                    object : PatternLockView.OnTurnOffErrorIndicatorListener {
                        override fun onTurnOff(view: PatternLockView, fromDelayedTime: Boolean) {
                            if (fromDelayedTime) {
                                view.isReadOnlyMode = false
                            }
                        }
                    }
            }
        }
        patternDataContoller = PatternLockerDataController.PatternLockerDataControllerFactory.getInstance().build<String>(this,PatternLockerDataController.SimpleDataStorageBehavior(this))
        patternDataContoller.onCheckingSelectedPointsListener = object:PatternLockerDataController.OnCheckingSelectedPointsListener{
            override fun onError(errorID: Int) {
                patternLockView.resetSelectedPoints(force = true)
            }

            override fun onSuccess(result: Boolean) {
                runOnUiThread {
                    Toast.makeText(applicationContext,"check result is ${result}",Toast.LENGTH_SHORT).show()
                    patternLockView.resetSelectedPoints(force = true,invalidateView = true)
                }
            }
        }
        editModeButton.setOnCheckedChangeListener { buttonView, isChecked ->
            val tmp:String = (if(isChecked){
                "save"
            }
            else{
                "check"
            })
            taskPatternButton.text = tmp+"\npattern"
            if(!isChecked){
                patternLockView.resetSelectedPoints(force=true)
            }
        }
        taskPatternButton.setOnClickListener {
            val editMode = editModeButton.isChecked
            if(editMode){
                val result =
                    patternDataContoller.savePattern(patternLockView.getLockTypes(),patternLockView.getSelectedPointss())

                Toast.makeText(applicationContext,"pattern save state:${result}",Toast.LENGTH_SHORT).show()
                patternLockView.resetSelectedPoints(force = true)
            }
            else{
                patternDataContoller.requestToCheckSelectedPoints(patternLockView.getLockTypes(),patternLockView.getSelectedPointss())
            }

        }
        resetPatternButton.setOnClickListener {
            resetPatternMethodForOnlyExample()
            patternLockView.resetSelectedPoints(force=true)
        }
        patternLockView.onTaskPatternListener = object:PatternLockView.OnTaskPatternListener{
            override fun onNothingSelected() {

            }

            override fun onFinishedPatternSelected(
                view:PatternLockView,
                editModeFromView: Boolean,
                lockType: PatternLockView.LockType,
                copiedSelectedPoints: ArrayList<SelectedPointItem>
            ) {
                if(editModeButton.isChecked){
                    //in pattern edit mode
                }
                else{
                    patternDataContoller.requestToCheckSelectedPoints(lockType, copiedSelectedPoints)
                }
            }

        }

        val patternList = listOf<String>(
            "square 3x3",
            "square 3x3 checker",
            "square 4x4",
            "square 5x5",
            "square 6x6",
            "square 7x7",
            "pentagon pattern",
            "pentagon pattern high density",
            "hexagon pattern",
            "hexagon pattern high density"
        )
        val arrayList = ArrayList<String>(patternList)

        ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,arrayList).also {
            listAdapterIndex = -1
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            patternSwitcher.adapter = it
            patternSwitcher.onItemSelectedListener = object:OnItemSelectedListener{
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    when(position){
                        1->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_3X3_WITH_CHECKER_PATTERN)
                        }
                        2->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_4X4)
                        }
                        3->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_5X5)
                        }
                        4->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_6X6)
                        }
                        5->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_7X7)
                        }
                        6->{
                            patternLockView.setLockTypes(PatternLockView.LockType.PENTAGON_DEFAULT)
                        }
                        7->{
                            patternLockView.setLockTypes(PatternLockView.LockType.PENTAGON_HIGH_DENSITY)
                        }
                        8->{
                            patternLockView.setLockTypes(PatternLockView.LockType.HEXAGON_DEFAULT)
                        }
                        9->{
                            patternLockView.setLockTypes(PatternLockView.LockType.HEXAGON_HIGH_DENSITY)
                        }
                        else->{
                            patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_3X3)
                        }
                    }
                    //if(0<=position&&position<lockTypes.size) {patternLockView.setLockTypes(lockTypes.get(position))}
                    if(listAdapterIndex!=-1 && listAdapterIndex!=position) {
                        resetPatternMethodForOnlyExample()
                        if(0<=position&&position<lockTypes.size) {
                            sharedPreference.edit().let {
                                it.putInt("pattern_type", lockTypes[position].intValue)
                                it.commit()
                            }
                        }
                    }
                    listAdapterIndex = position
                    //patternLockView.invalidate()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

            }
            if(0<lastPatternTypeIndex&&lastPatternTypeIndex<=arrayList.size) {
                patternSwitcher.setSelection(lastPatternTypeIndex-1)
            }
        }

    }
    private final fun resetPatternMethodForOnlyExample(){
        patternDataContoller?.resetPattern()
        runOnUiThread {
            Toast.makeText(applicationContext,"Pattern has been reset.",Toast.LENGTH_SHORT).show()
            patternLockView.resetSelectedPoints()
        }

    }
}