package cn.ziyis.cqustwifi

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import top.msfxp.schedule.data.db.MIGRATION_1_2

class PortalAndMigrationTest {
    @Test fun examMigrationPreservesExistingData(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="migration-fixture-${System.nanoTime()}.db"
        fun open(version:Int):SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(version){
                override fun onCreate(db:SupportSQLiteDatabase){db.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)");db.execSQL("INSERT INTO sentinel VALUES ('fixture')")}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){MIGRATION_1_2.migrate(db)}
            }).build())
        try{
            open(1).use{it.writableDatabase}
            open(2).use{helper->
                val db=helper.writableDatabase
                db.query("SELECT value FROM sentinel").use{assertTrue(it.moveToFirst());assertEquals("fixture",it.getString(0))}
                db.query("SELECT name FROM sqlite_master WHERE name='exam_records'").use{assertTrue(it.moveToFirst())}
            }
        }finally{context.deleteDatabase(name)}
    }
    @Test fun portalWebViewHasNoFileAccessOrMixedContent(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<PortalActivity>(Intent(context,PortalActivity::class.java)).use{scenario->scenario.onActivity{activity->
            fun find(view:View):WebView?{if(view is WebView)return view;if(view is ViewGroup)for(i in 0 until view.childCount){find(view.getChildAt(i))?.let{return it}};return null}
            val web=find(activity.window.decorView)!!
            assertTrue(web.settings.javaScriptEnabled)
            assertFalse(web.settings.allowFileAccess);assertFalse(web.settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,web.settings.mixedContentMode)
            assertFalse(activity.packageManager.getActivityInfo(activity.componentName,0).exported)
        }}
    }
}
