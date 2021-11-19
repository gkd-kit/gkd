package li.songe.ad_closer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class EntryActivity:AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}