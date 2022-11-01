package com.github.imscs21.pattern_locker

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import com.github.imscs21.pattern_locker.views.PatternLockView
import com.github.imscs21.pattern_locker.views.PatternLockerDataController
import com.github.imscs21.pattern_locker.views.PointItem
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    protected lateinit var patternLockView: PatternLockView
    protected lateinit var patternSwitcher:AppCompatSpinner
    protected lateinit var resetPatternButton:AppCompatButton
    protected lateinit var taskPatternButton:AppCompatButton
    protected lateinit var editModeButton:SwitchMaterial
    protected lateinit var patternDataContoller:PatternLockerDataController
    protected var listAdapterIndex:Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        patternLockView = findViewById(R.id.pattern_lock_view)
        patternSwitcher = findViewById(R.id.pattern_selector)
        editModeButton = findViewById(R.id.switch_edit_mode)
        resetPatternButton = findViewById(R.id.button_reset_pattern)
        taskPatternButton = findViewById(R.id.button_pattern_task)
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
        patternDataContoller = PatternLockerDataController.PatternLockerDataControllerFactory.getInstance(context = this).build()
        patternDataContoller.onCheckingSelectedPointsListener = object:PatternLockerDataController.OnCheckingSelectedPointsListener{
            override fun onError(errorID: Int) {
                patternLockView.resetSelectedPoints(force = true)
            }

            override fun onSuccess(result: Boolean) {
                runOnUiThread {
                    Toast.makeText(applicationContext,"check result is ${result}",Toast.LENGTH_SHORT).show()
                    patternLockView.resetSelectedPoints(force = true)
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
                editModeFromView: Boolean,
                lockType: PatternLockView.LockType,
                selectedPoints: ArrayList<PointItem>
            ) {
                if(editModeButton.isChecked){

                }
                else{
                    patternDataContoller.requestToCheckSelectedPoints(lockType, selectedPoints)
                }
            }

        }
        val patternList = listOf<String>(
            "square 3x3",
            "square 3x3 check",
            "square 4x4",
            "square 5x5",
            "square 6x6",
            "square 7x7",
            "pentagon pattern",
            "hexagon pattern"
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
                           patternLockView.setLockTypes(PatternLockView.LockType.HEXAGON_DEFAULT)
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