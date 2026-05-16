package com.calis10x.system

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.time.*
import java.util.*
import kotlin.math.*
import kotlin.random.Random

data class Exercise(val name: String, val done: Int = 0, val target: Int = 100)
data class Quest(val title: String, val reward: Int, val complete: Boolean = false)
data class PlayerState(
    val name: String = "",
    val id: String = "SYS-" + UUID.randomUUID().toString().take(8).uppercase(),
    val trophies: Int = 0,
    val longestStreak: Int = 7,
    val currentStreak: Int = 0,
    val exercises: List<Exercise> = listOf("Push-ups","Chin-ups","Pull-ups","Squats","Dips").map { Exercise(it) },
    val dailyQuests: List<Quest> = listOf(Quest("Finish daily workout before 23:59",5), Quest("Do 100x of every workout option",5)),
    val socialBanUntilMillis: Long = 0,
    val fullLockActive: Boolean = true,
    val lastCheckedDate: String = LocalDate.now().toString(),
    val inventory: Map<String, Int> = emptyMap(),
    val activePunishment: String = "",
    val rank: String = "E"
)

val socialPackages = setOf("com.zhiliaoapp.musically","com.google.android.youtube","com.instagram.android","com.snapchat.android","com.facebook.katana","com.twitter.android","com.reddit.frontpage")
val punishments = listOf("Wipeout","Weak Grip","Early Bird","War Cry","Summoning Circle","Sticky-bit Orc","Goblin Thief","Slithering Snake","Meditating Monk","Frozen Elf","God Statue")
data class StoreItem(val name: String, val price: Int, val description: String, val rarity: String)
val storeItems = listOf(
    StoreItem("Bundle of 10",5,"Removes 10 reps from every exercise for today.","B"),
    StoreItem("Scroll of Joy",25,"Allows 5 TikTok scrolls.","B"),
    StoreItem("Time Spent Well",50,"Gives 30 minutes of screen time.","A"),
    StoreItem("Shield of Iron",15,"Blocks punishment under S-rank.","A"),
    StoreItem("Total Recovery",35,"Finishes your daily workout.","S"),
    StoreItem("Blood-red Commander Igris",50,"Blocks even S-rank threats.","S"),
    StoreItem("Necromancer",150,"Spin every other day for shadow soldiers.","S"),
    StoreItem("Shadow Monarch",500,"Spin every day for stronger shadow soldiers.","SS")
)

class StateRepo(private val context: Context) {
    private val prefs = context.getSharedPreferences("calis10x_state", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun load(): PlayerState = prefs.getString("state", null)?.let { runCatching { gson.fromJson(it, PlayerState::class.java) }.getOrNull() } ?: PlayerState()
    fun save(state: PlayerState) { prefs.edit().putString("state", gson.toJson(state)).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); scheduleMidnightCheck(this); setContent { SystemApp() } }
}

@Composable
fun SystemApp() {
    val context = LocalContext.current
    val repo = remember { StateRepo(context) }
    var state by remember { mutableStateOf(repo.load()) }
    var tab by remember { mutableStateOf("Gate") }
    var burst by remember { mutableStateOf(0) }
    fun update(next: PlayerState) {
        val wasLocked = state.fullLockActive
        state = autoCompleteDaily(next)
        if (wasLocked && !state.fullLockActive) burst++
        repo.save(state)
        RestrictionService.refresh(context)
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF38BDF8), secondary=Color(0xFFF97316), background=Color(0xFF040B18), surface=Color(0xFF071425), onBackground=Color(0xFFE5F6FF), onSurface=Color(0xFFE5F6FF))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0B2447), Color(0xFF040B18)), radius=1400f))) {
            RuneBackground()
            if (state.name.isBlank()) Onboarding(state) { update(state.copy(name = it.ifBlank { "Player" })) }
            else Column(Modifier.fillMaxSize().padding(16.dp)) {
                SystemHeader(state); Spacer(Modifier.height(12.dp)); NavTabs(tab) { tab = it }; Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) { item {
                    when(tab) {
                        "Gate" -> GateScreen(state, ::update)
                        "Quests" -> QuestScreen(state)
                        "Wheel" -> WheelScreen(state, ::update)
                        "Store" -> StoreScreen(state, ::update)
                        else -> SystemScreen(state, ::update)
                    }
                }}
            }
            CompletionBurst(burst)
        }
    }
}

fun autoCompleteDaily(s: PlayerState): PlayerState {
    val workoutDone = s.exercises.all { it.done >= it.target }
    val quests = s.dailyQuests.map { if (it.title.contains("workout", true) || it.title.contains("100x", true)) it.copy(complete = workoutDone) else it }
    val reward = quests.filter { q -> q.complete && !s.dailyQuests.first { it.title == q.title }.complete }.sumOf { it.reward }
    val trophies = s.trophies + reward
    val rank = when { trophies >= 500 -> "SS"; trophies >= 150 -> "S"; trophies >= 75 -> "A"; trophies >= 30 -> "B"; trophies >= 10 -> "C"; else -> "E" }
    return s.copy(dailyQuests=quests, fullLockActive=!(workoutDone && quests.all { it.complete }), trophies=trophies, rank=rank)
}

@Composable fun RuneBackground() {
    val inf = rememberInfiniteTransition(label="runes")
    val shift by inf.animateFloat(0f,1f,infiniteRepeatable(tween(9000, easing=LinearEasing), RepeatMode.Restart), label="shift")
    Canvas(Modifier.fillMaxSize()) {
        repeat(40) {
            val x = ((it * 83 + shift * 250) % size.width)
            val y = ((it * 157 + shift * 480) % size.height)
            drawCircle(Color(0x1738BDF8), radius=2f+(it%5), center=Offset(x,y))
        }
        repeat(12) {
            val y = it * size.height / 12f + shift * 40
            drawLine(Color(0x1138BDF8), Offset(0f,y), Offset(size.width,y+90), strokeWidth=1.2f)
        }
    }
}

@Composable fun Onboarding(state: PlayerState, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.Center) {
        Text("THE SYSTEM", fontSize=44.sp, fontWeight=FontWeight.Black, color=Color(0xFFE0F2FE))
        Text("A daily dungeon for self-improvement.", color=Color(0xFF93C5FD))
        Spacer(Modifier.height(24.dp))
        RuneCard {
            Text("Player Registration", fontSize=24.sp, fontWeight=FontWeight.Black)
            Text("Generated ID: ${state.id}", color=Color(0xFF94A3B8), fontSize=13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Name")}, singleLine=true, modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick={onDone(name.trim())}, modifier=Modifier.fillMaxWidth()) { Text("Awaken") }
        }
    }
}

@Composable fun SystemHeader(state: PlayerState) {
    RuneCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column {
                Text(state.name, fontSize=26.sp, fontWeight=FontWeight.Black)
                Text(state.id, color=Color(0xFF94A3B8), fontSize=12.sp)
                Text(if(state.fullLockActive) "Gate Status: LOCKED" else "Gate Status: CLEARED", color=if(state.fullLockActive) Color(0xFFF97316) else Color(0xFF34D399), fontWeight=FontWeight.Bold)
            }
            RankBadge(state.rank)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            StatPill("${state.trophies}🏆","Trophies"); StatPill("${state.currentStreak}","Streak"); StatPill("${state.longestStreak}","Best")
        }
    }
}

@Composable fun RankBadge(rank: String) {
    val color = when(rank) {"SS"->Color(0xFFFBBF24); "S"->Color(0xFFF97316); "A"->Color(0xFF38BDF8); "B"->Color(0xFF34D399); "C"->Color(0xFFA78BFA); else->Color(0xFF94A3B8)}
    val inf = rememberInfiniteTransition(label="rank")
    val glow by inf.animateFloat(.65f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse), label="glow")
    Box(Modifier.size(76.dp).shadow((18*glow).dp, CircleShape).border(2.dp, color.copy(alpha=glow), CircleShape).background(Color(0xFF020617), CircleShape), contentAlignment=Alignment.Center) {
        Text(rank, fontSize=24.sp, fontWeight=FontWeight.Black, color=color)
    }
}

@Composable fun StatPill(value:String,label:String) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x6638BDF8)).padding(horizontal=16.dp, vertical=10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value, fontWeight=FontWeight.Black); Text(label, fontSize=11.sp, color=Color(0xFFBFDBFE))
    }
}

@Composable fun NavTabs(selected:String,onSelect:(String)->Unit) {
    val tabs = listOf("Gate","Quests","Wheel","Store","System")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        tabs.forEach { FilterChip(selected=selected==it, onClick={onSelect(it)}, label={Text(it)}, leadingIcon={ if(selected==it) Icon(Icons.Default.AutoAwesome,null,Modifier.size(16.dp)) }) }
    }
}

@Composable fun GateScreen(state:PlayerState, update:(PlayerState)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        DungeonBanner(state)
        Text("Daily Workout", fontSize=23.sp, fontWeight=FontWeight.Black)
        state.exercises.forEachIndexed { i, ex -> ExerciseRune(ex) { amount ->
            val next = state.exercises.toMutableList()
            next[i] = ex.copy(done=(ex.done+amount).coerceAtMost(ex.target))
            update(state.copy(exercises=next))
        }}
    }
}

@Composable fun DungeonBanner(state:PlayerState) {
    RuneCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(if(state.fullLockActive) Icons.Default.Lock else Icons.Default.CheckCircle, null, tint=if(state.fullLockActive) Color(0xFFF97316) else Color(0xFF34D399), modifier=Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if(state.fullLockActive) "Daily Dungeon Active" else "Dungeon Cleared", fontWeight=FontWeight.Black, fontSize=20.sp)
                Text(if(state.fullLockActive) "Workout and daily quests must be completed." else "Restrictions lifted. Victory achieved.", color=Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable fun ExerciseRune(ex:Exercise,onAdd:(Int)->Unit) {
    RuneCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(ex.name, fontSize=18.sp, fontWeight=FontWeight.Bold)
            Text("${ex.done}/${ex.target}", color=Color(0xFF38BDF8), fontWeight=FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress={ex.done.toFloat()/ex.target}, modifier=Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)), color=Color(0xFFF97316), trackColor=Color(0x4438BDF8))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(5,10,25,50).forEach { AssistChip(onClick={onAdd(it)}, label={Text("+$it")}) } }
    }
}

@Composable fun QuestScreen(state:PlayerState) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Quest Log", fontSize=23.sp, fontWeight=FontWeight.Black)
        state.dailyQuests.forEach { q -> RuneCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(if(q.complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint=if(q.complete) Color(0xFF34D399) else Color(0xFFF97316))
                Spacer(Modifier.width(12.dp))
                Column { Text(q.title, fontWeight=FontWeight.Bold); Text("Reward: +${q.reward}🏆", color=Color(0xFFFFD166)) }
            }
        }}
    }
}

@Composable fun WheelScreen(state:PlayerState, update:(PlayerState)->Unit) {
    var selected by remember { mutableStateOf(state.activePunishment.ifBlank{"None"}) }
    var spin by remember { mutableStateOf(0f) }
    val animated by animateFloatAsState(spin, tween(1600, easing=FastOutSlowInEasing), label="wheel")
    Column(verticalArrangement=Arrangement.spacedBy(12.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Text("Punishment Wheel", fontSize=23.sp, fontWeight=FontWeight.Black)
        Box(Modifier.size(290.dp), contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(animated)) {
                val sweep = 360f / punishments.size
                punishments.forEachIndexed { idx, _ -> drawArc(if(idx%2==0) Color(0xFF0EA5E9) else Color(0xFFF97316), idx*sweep, sweep, true) }
                drawCircle(Color(0xFF020617), radius=size.minDimension*.22f)
                drawCircle(Color(0xAAE0F2FE), radius=size.minDimension*.48f, style=Stroke(4f))
            }
            Text("🎰", fontSize=44.sp)
        }
        Text("Result: $selected", fontWeight=FontWeight.Black, color=Color(0xFFFFD166))
        Button(onClick={ val result=punishments.random(); selected=result; spin += 720f + Random.nextInt(0,360); update(applyPunishment(state,result)) }) { Text("Spin the Wheel") }
    }
}

fun applyPunishment(s:PlayerState,p:String):PlayerState = when(p) {
    "Wipeout" -> s.copy(exercises=listOf("Push-ups","Chin-ups","Pull-ups","Squats","Dips").map { Exercise(it) }, trophies=(s.trophies-25).coerceAtLeast(0), activePunishment=p)
    "Weak Grip" -> s.copy(exercises=s.exercises.map { it.copy(target=it.target*2, done=0) }, trophies=(s.trophies/2-25).coerceAtLeast(0), activePunishment=p)
    "God Statue" -> s.copy(socialBanUntilMillis=System.currentTimeMillis()+48L*60L*60L*1000L, trophies=(s.trophies-35).coerceAtLeast(0), activePunishment=p)
    else -> s.copy(trophies=(s.trophies-25).coerceAtLeast(0), activePunishment=p)
}

@Composable fun StoreScreen(state:PlayerState, update:(PlayerState)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Dungeon Store", fontSize=23.sp, fontWeight=FontWeight.Black)
        storeItems.forEach { item -> RuneCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text(item.name, fontWeight=FontWeight.Black); Spacer(Modifier.width(8.dp)); SmallRank(item.rarity) }
                    Text(item.description, color=Color(0xFF94A3B8), fontSize=13.sp)
                }
                Button(enabled=state.trophies>=item.price, onClick={
                    val t = state.trophies - item.price
                    val next = when(item.name) {
                        "Total Recovery" -> state.copy(trophies=t, exercises=state.exercises.map { it.copy(done=it.target) })
                        "Bundle of 10" -> state.copy(trophies=t, exercises=state.exercises.map { it.copy(target=(it.target-10).coerceAtLeast(10)) })
                        else -> state.copy(trophies=t, inventory=state.inventory + (item.name to ((state.inventory[item.name] ?: 0)+1)))
                    }
                    update(next)
                }) { Text("${item.price}🏆") }
            }
        }}
    }
}

@Composable fun SmallRank(rank:String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x3338BDF8)).border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)).padding(horizontal=7.dp, vertical=2.dp)) {
        Text(rank, fontSize=11.sp, color=Color(0xFFBAE6FD), fontWeight=FontWeight.Black)
    }
}

@Composable fun SystemScreen(state:PlayerState, update:(PlayerState)->Unit) {
    val context = LocalContext.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("System Access", fontSize=23.sp, fontWeight=FontWeight.Black)
        PermissionCard("Usage Access","Needed to detect when restricted apps open.") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        PermissionCard("Display Over Apps","Needed for the System lock overlay.") { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
        PermissionCard("Start Guard","Runs the local restriction service.") { ContextCompat.startForegroundService(context, Intent(context, RestrictionService::class.java)) }
        RuneCard { Text("Restricted socials", fontWeight=FontWeight.Black); Text("TikTok, YouTube, Instagram, Snapchat, Facebook, X, Reddit", color=Color(0xFF94A3B8)) }
        OutlinedButton(onClick={update(PlayerState(name=state.name))}) { Text("Reset run") }
    }
}

@Composable fun PermissionCard(title:String, desc:String, action:()->Unit) {
    RuneCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight=FontWeight.Black); Text(desc, color=Color(0xFF94A3B8), fontSize=13.sp) }
            Button(onClick=action) { Text("Open") }
        }
    }
}

@Composable fun RuneCard(content:@Composable ColumnScope.()->Unit) {
    val inf = rememberInfiniteTransition(label="card")
    val alpha by inf.animateFloat(.35f,.85f,infiniteRepeatable(tween(2200),RepeatMode.Reverse), label="a")
    Card(Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(28.dp)).border(1.dp, Color(0xFF38BDF8).copy(alpha=alpha), RoundedCornerShape(28.dp)), shape=RoundedCornerShape(28.dp), colors=CardDefaults.cardColors(containerColor=Color(0xDD071425))) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawLine(Color(0x5538BDF8), Offset(18f,18f), Offset(70f,18f), 2f)
                drawLine(Color(0x55F97316), Offset(size.width-18f,size.height-18f), Offset(size.width-70f,size.height-18f), 2f)
            }
            Column(Modifier.padding(16.dp), content=content)
        }
    }
}

@Composable fun CompletionBurst(trigger:Int) {
    if(trigger <= 0) return
    val anim = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { anim.animateTo(1f, tween(1300)); anim.snapTo(0f) }
    Canvas(Modifier.fillMaxSize()) {
        if(anim.value > 0f) {
            val center = Offset(size.width/2, size.height/2)
            repeat(42) {
                val angle = it * (2 * Math.PI / 42)
                val dist = anim.value * 420f
                val p = Offset(center.x + cos(angle).toFloat()*dist, center.y + sin(angle).toFloat()*dist)
                drawCircle(Color(0xFFFFD166).copy(alpha=1f-anim.value), radius=5f, center=p)
            }
        }
    }
}

class RestrictionService : Service() {
    private var wm:WindowManager? = null
    private var overlay:View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object:Runnable { override fun run() { checkRestriction(); handler.postDelayed(this, 1500) } }
    override fun onCreate() { super.onCreate(); startForeground(10, notification()); wm = getSystemService(WINDOW_SERVICE) as WindowManager; handler.post(loop) }
    override fun onBind(intent:Intent?) = null
    private fun notification():Notification {
        val id = "calis10x_guard"
        if(Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id, "Calis10x System Guard", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, id).setContentTitle("The System is watching").setContentText("Daily dungeon restrictions active.").setSmallIcon(android.R.drawable.ic_lock_lock).build()
    }
    private fun checkRestriction() {
        val s = StateRepo(this).load()
        val current = foregroundPackage() ?: return
        val socialBan = System.currentTimeMillis() < s.socialBanUntilMillis
        val shouldBlock = (s.fullLockActive && current != packageName) || (socialBan && socialPackages.contains(current))
        if(shouldBlock) showOverlay(s) else hideOverlay()
    }
    private fun foregroundPackage():String? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end-10000, end).maxByOrNull { it.lastTimeUsed }?.packageName
    }
    private fun showOverlay(s:PlayerState) {
        if(!Settings.canDrawOverlays(this) || overlay != null) return
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.rgb(4,11,24)); gravity = Gravity.CENTER; setPadding(40,40,40,40)
            addView(TextView(context).apply { text="⚠ THE SYSTEM ⚠"; textSize=32f; setTextColor(android.graphics.Color.rgb(56,189,248)); gravity=Gravity.CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD })
            addView(TextView(context).apply { text="Daily Dungeon Active\\nComplete workout + quests to unlock.\\nPunishment: ${s.activePunishment.ifBlank{"None"}}"; textSize=18f; setTextColor(android.graphics.Color.WHITE); gravity=Gravity.CENTER; setPadding(0,24,0,24) })
            addView(Button(context).apply { text="Open Calis10x"; setOnClickListener { startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE)
        wm?.addView(view, params); overlay = view
    }
    private fun hideOverlay() { overlay?.let { wm?.removeView(it) }; overlay = null }
    override fun onDestroy() { handler.removeCallbacks(loop); hideOverlay(); super.onDestroy() }
    companion object { fun refresh(context:Context) { runCatching { ContextCompat.startForegroundService(context, Intent(context, RestrictionService::class.java)) } } }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context, intent:Intent?) {
        val repo = StateRepo(context)
        val s = repo.load()
        val next = if(s.fullLockActive) applyPunishment(s, punishments.random()).copy(fullLockActive=true) else s.copy(currentStreak=s.currentStreak+1, longestStreak=max(s.longestStreak, s.currentStreak+1))
        repo.save(next.copy(lastCheckedDate=LocalDate.now().toString()))
        scheduleMidnightCheck(context)
    }
}

fun scheduleMidnightCheck(context:Context) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = PendingIntent.getBroadcast(context, 99, Intent(context, MidnightReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val today = LocalDate.now().atTime(23,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val trigger = if(today > System.currentTimeMillis()) today else LocalDate.now().plusDays(1).atTime(23,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
}
