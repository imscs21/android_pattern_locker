# android_pattern_locker
Pattern Locker Library for android

![Demo image](screenshots/demo.gif)



# How TO APPLY
### ※This version was not cleaned up to ease of applying

## 1. In XML
```
<com.github.imscs21.pattern_locker.views.PatternLockView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="30dp"
        app:recommendedClickingJudgementAreaPaddingRadius="9dp"
        app:spacingSizeIfWrapContent="100dp"
        app:spacingTypeIfWrapContent="total"
        app:indicatorColor="#80ff80"
        app:indicatorRadius="6dp"
        app:trajectoryLineColor="#ff8080"
        app:trajectoryLineThickness="5dp"
        app:showTrajectoryLines="true"
        app:useVibratorIfAvailable="true"
        app:useTrajectoryLineShadow="false"
        app:patternType="hexagon_shape_default" />
```
* xml attributes
    1. (supported) pattern types
        * square_3x3
        * square_3x3_with_checker_pattern
        * square_4x4
        * square_5x5
        * square_6x6
        * square_7x7
        * pentagon_shape_default
        * ~~pentagon_shape_with_high_density~~
        * hexagon_shape_default
        * ~~hexagon_shape_with_high_density~~
    2. spacingTypeIfWrapContent
       * total
       * fixed 
    3. etc

## 2. In Kotlin

```
//patternLockView = PatternLockView(context) //for initiate view in code

//setting attributes in code
patternLockView.useVibratorIfAvaliable = false
patternLockView.useTrajectoryLineShadow = false
patternLockView.shouldShowTrajectoryLines = false
patternLockView.setLockTypes(PatternLockView.LockType.SQUARE_3X3,invalidateView = true)
patternLockView.clickingJudgementPaddingRadius = 30f//dimension size(e.g. pixel size)
patternLockView.editMode = true // not using currently
patternLockView.onTaskPatternListener = ...

//finish setting attributes in code

val patternDataContoller = PatternLockerDataController.PatternLockerDataControllerFactory.getInstance().build<String>(this,PatternLockerDataController.SimpleDataStorageBehavior(this))
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
patternLockView.onTaskPatternListener = object:PatternLockView.OnTaskPatternListener{
            override fun onNothingSelected() {

            }

            override fun onFinishedPatternSelected(
                editModeFromView: Boolean,
                lockType: PatternLockView.LockType,
                selectedPoints: ArrayList<SelectedPointItem>
            ) {
                if(editModeButton.isChecked){
                    //in pattern edit mode
                }
                else{
                    patternDataContoller.requestToCheckSelectedPoints(lockType, selectedPoints)
                }
            }

        }
```

#### 2.1. Implement your own pattern storage behavior
```
val dataControllerFactory = PatternLockerDataController.PatternLockerDataControllerFactory.getInstance()

val dataStorageBehavior : PatternLockerDataController.DataStorageBehavior<Any> = object:PatternLockerDataController.DataStorageBehavior<Any>{
    override val innerValue: MutableLiveData<Any>
        get() = TODO("Not yet implemented")

    override fun selectValue(): Any? {
        TODO("Not yet implemented")
    }

    override fun savePattern(
        lockType: PatternLockView.LockType,
        selectedPoints: ArrayList<SelectedPointItem>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun loadPattern(): Any? {
        TODO("Not yet implemented")
    }

    override fun checkPattern(
        value: Any?,
        lockType: PatternLockView.LockType,
        selectedPoints: ArrayList<SelectedPointItem>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun resetPattern(): Boolean {
        TODO("Not yet implemented")
    }

}

val dataController = dataControllerFactory.build(context,dataStorageBehavior)
...

```

#### 2.2. Simple Data Storage Behavior 
This is pre-defined StorageBehavior.
But There are weak points due to md5 hashing. 
```
val simpleBehavior = PatternLockerDataController.SimpleDataStorageBehavior(this)
val dataControllerFactory = PatternLockerDataController.PatternLockerDataControllerFactory.getInstance()
val patternDataContoller = dataControllerFactory.build<String>(this,simpleBehavior)
```

### 3. Reset drawn pattern
```
patternLockView.resetSelectedPoints(force = true,invalidateView = true)
```

### 4. Reset stored pattern info
```
patternDataContoller?.resetPattern()
```