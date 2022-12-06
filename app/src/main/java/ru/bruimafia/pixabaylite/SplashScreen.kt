package ru.bruimafia.pixabaylite

import android.annotation.SuppressLint
import android.content.Intent
import com.daimajia.androidanimations.library.Techniques
import com.viksaa.sssplash.lib.activity.AwesomeSplash
import com.viksaa.sssplash.lib.cnst.Flags
import com.viksaa.sssplash.lib.model.ConfigSplash
import ru.bruimafia.pixabaylite.main.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashScreen : AwesomeSplash() {

    override fun initSplash(configSplash: ConfigSplash?) {

        configSplash?.let {
            //Customize Circular Reveal
            it.backgroundColor = R.color.base //any color you want from colors.xml
            it.animCircularRevealDuration = 0 //int ms
            it.revealFlagX = Flags.REVEAL_RIGHT  //or Flags.REVEAL_LEFT
            it.revealFlagY = Flags.REVEAL_BOTTOM //or Flags.REVEAL_TOP

            //Customize Logo
            it.logoSplash = R.drawable.logo_big //or any other drawable
            it.animLogoSplashDuration = 1500 //int ms
            it.animLogoSplashTechnique = Techniques.FadeIn //choose one form Techniques (ref: https://github.com/daimajia/AndroidViewAnimations)

            //Customize Path
//            it.pathSplash = "" //set path String
//            it.originalHeight = 20 //in relation to your svg (path) resource
//            it.originalWidth = 20 //in relation to your svg (path) resource
//            it.animPathStrokeDrawingDuration = 1000
//            it.pathSplashStrokeSize = 1 //I advise value be <5
//            it.pathSplashStrokeColor = R.color.base_level1 //any color you want from colors.xml
//            it.animPathFillingDuration = 1000
//            it.pathSplashFillColor = R.color.accent_200 //path object filling color

            //Customize Title
            it.titleSplash = ""
//            it.titleTextColor = R.color.white
//            it.titleTextSize = 30f //float value
            it.animTitleDuration = 0
//            it.animTitleTechnique = Techniques.Bounce
//            it.titleFont = "fonts/myfont.ttf" //provide string to your font located in assets/fonts/
        }

    }

    override fun animationsFinished() {
        startActivity(Intent(this@SplashScreen, MainActivity::class.java))
        finish()
    }

}