package com.example.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Read-only live view of the server wallet (wallets/{uid}); the client never writes balances. New users = 0 / 0. */
object Wallet {
    var coins by mutableIntStateOf(0)
    var diamonds by mutableIntStateOf(0)
    private var uid: String? = null
    private var l: ValueEventListener? = null
    fun start() {
        val u = FirebaseAuth.getInstance().currentUser?.uid
        if (u == uid) return
        val ref = FirebaseDatabase.getInstance("https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("wallets")
        uid?.let { old -> l?.let { ref.child(old).removeEventListener(it) } }
        coins = 0; diamonds = 0; uid = u
        if (u == null) return
        l = ref.child(u).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                coins = (s.child("coins").getValue(Long::class.java) ?: 0L).toInt()
                diamonds = (s.child("diamonds").getValue(Long::class.java) ?: 0L).toInt()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
}
