package com.agrocalc.app

import android.os.Bundle
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private val Green = Color(0xFF21834A)
private val DarkGreen = Color(0xFF14532D)
private val Bg = Color(0xFFF3F7F3)
private val Gray = Color(0xFF718078)
private const val PREFS = "agrocalc_v30"

data class Animal(
    val id: Long,
    val name: String,
    val emoji: String,
    val count: Int,
    val hay30: Double,
    val grain30: Double,
    val feed30: Double,
    val water30: Double
)

data class Stock(
    val hay: Double = 0.0,
    val grain: Double = 0.0,
    val feed: Double = 0.0,
    val water: Double = 0.0
)

private fun dateNow() =
    SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())

private fun number(v: Double) =
    String.format(Locale("ru", "RU"), "%,.1f", v)

private fun cash(v: Double) =
    String.format(Locale("ru", "RU"), "%,.0f ₸", v)

private fun parse(s: String): Double {
    val value = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    return if (value.isFinite() && value >= 0.0) value else 0.0
}

private fun animalJson(a: Animal) = JSONObject()
    .put("id", a.id)
    .put("name", a.name)
    .put("emoji", a.emoji)
    .put("count", a.count)
    .put("hay30", a.hay30)
    .put("grain30", a.grain30)
    .put("feed30", a.feed30)
    .put("water30", a.water30)

private fun readAnimals(root: JSONObject): List<Animal> {
    val ar = root.optJSONArray("animals") ?: JSONArray()
    return (0 until ar.length()).mapNotNull { i ->
        runCatching {
            val o = ar.getJSONObject(i)
            Animal(
                o.optLong("id"),
                o.optString("name"),
                o.optString("emoji", "🐐"),
                o.optInt("count"),
                o.optDouble("hay30", o.optDouble("hay180") / 6.0),
                o.optDouble("grain30", o.optDouble("grain180") / 6.0),
                o.optDouble("feed30", o.optDouble("feed180") / 6.0),
                o.optDouble("water30", o.optDouble("waterDay") * 30.0)
            )
        }.getOrNull()
    }
}

private fun readStock(root: JSONObject): Stock {
    val o = root.optJSONObject("stock") ?: JSONObject()

    fun safeNumber(key: String): Double {
        val value = o.optDouble(key, 0.0)
        return if (value.isFinite() && value >= 0.0) value else 0.0
    }

    return Stock(
        hay = safeNumber("hay"),
        grain = safeNumber("grain"),
        feed = safeNumber("feed"),
        water = safeNumber("water")
    )
}

private fun stockJson(s: Stock) = JSONObject()
    .put("hay", s.hay.takeIf { it.isFinite() && it >= 0 } ?: 0.0)
    .put("grain", s.grain.takeIf { it.isFinite() && it >= 0 } ?: 0.0)
    .put("feed", s.feed.takeIf { it.isFinite() && it >= 0 } ?: 0.0)
    .put("water", s.water.takeIf { it.isFinite() && it >= 0 } ?: 0.0)

private fun list(root: JSONObject, key: String): List<JSONObject> {
    val a = root.optJSONArray(key) ?: JSONArray()
    return (0 until a.length()).mapNotNull {
        runCatching { a.getJSONObject(it) }.getOrNull()
    }
}

private fun initialData(): JSONObject {
    val animals = JSONArray()
    listOf(
        Animal(1, "Овцы", "🐑", 10, 137.5 / 6, 100.0 / 6, 0.0, 40.0),
        Animal(2, "Быки", "🐂", 2, 2062.5 / 6, 0.0, 0.0, 1125.0),
        Animal(3, "Куры", "🐔", 50, 0.0, 30.0 / 6, 0.0, 0.0),
        Animal(4, "Лошади", "🐎", 2, 687.5 / 6, 500.0 / 6, 0.0, 600.0)
    ).forEach { animals.put(animalJson(it)) }

    return JSONObject()
        .put("animals", animals)
        .put("stock", JSONObject())
        .put("feeds", JSONArray())
        .put("money", JSONArray())
        .put("events", JSONArray())
        .put("weights", JSONArray())
        .put("health", JSONArray())
        .put("movements", JSONArray())
        .put("baleWeight", 275.0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgroCalc() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgroCalc() {
    val context = LocalContext.current

    var root by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS, 0)
                .getString("data", null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: initialData()
        )
    }

    fun save() {
        context.getSharedPreferences(PREFS, 0)
            .edit().putString("data", root.toString()).apply()
    }

    fun refresh() {
        save()
        root = JSONObject(root.toString())
    }

    fun saveAnimals(items: List<Animal>) {
        val a = JSONArray()
        items.forEach { a.put(animalJson(it)) }
        root.put("animals", a)
        refresh()
    }

    fun saveStock(s: Stock) {
        root.put("stock", stockJson(s))
        refresh()
    }

    fun addRecord(key: String, item: JSONObject) {
        val a = root.optJSONArray(key) ?: JSONArray()
        a.put(item)
        root.put(key, a)
        refresh()
    }

    val animals = readAnimals(root)
    val stock = readStock(root)
    val bale = root.optDouble("baleWeight", 275.0)

    val total = animals.sumOf { it.count }
    val hayNeed = animals.sumOf { it.count * it.hay30 }
    val grainNeed = animals.sumOf { it.count * it.grain30 }
    val feedNeed = animals.sumOf { it.count * it.feed30 }
    val waterNeed = animals.sumOf { it.count * it.water30 }
    val hayBales = if (bale > 0) ceil(hayNeed / bale).toInt() else 0

    val money = list(root, "money")
    val income = money.filter { it.optString("type") == "Доход" }
        .sumOf { it.optDouble("amount") }
    val expenses = money.filter { it.optString("type") == "Расход" }
        .sumOf { it.optDouble("amount") }

    var tab by remember { mutableIntStateOf(0) }
    var showAnimal by remember { mutableStateOf(false) }
    var editingAnimal by remember { mutableStateOf<Animal?>(null) }
    var showFeed by remember { mutableStateOf(false) }
    var showMoney by remember { mutableStateOf(false) }
    var showMovement by remember { mutableStateOf(false) }
    var showEvent by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(root.toString(2).toByteArray())
            }
            message = "Резервная копия сохранена"
        }.onFailure { message = "Ошибка: ${it.message}" }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val data = context.contentResolver.openInputStream(uri)!!
                .bufferedReader().use { it.readText() }
            root = JSONObject(data)
            refresh()
            message = "Данные восстановлены"
        }.onFailure { message = "Ошибка восстановления: ${it.message}" }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(makeCsv(root).toByteArray(Charsets.UTF_8))
            }
            message = "Excel-отчёт сохранён"
        }.onFailure { message = "Ошибка: ${it.message}" }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(makePdf(root))
            }
            message = "PDF-отчёт сохранён"
        }.onFailure { message = "Ошибка: ${it.message}" }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            secondary = DarkGreen,
            background = Bg
        )
    ) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "🌱 AgroCalc 3.0",
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val labels = listOf(
                        "Главная", "Животные", "Склад",
                        "Журнал", "Финансы", "Ещё"
                    )
                    val icons = listOf("🏠", "🐄", "📦", "📝", "💰", "☰")
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icons[i]) },
                            label = { Text(label, fontSize = 9.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(
                    padding, total, hayNeed, hayBales,
                    grainNeed, feedNeed, waterNeed,
                    stock, income - expenses, animals
                )

                1 -> AnimalsScreen(
                    padding,
                    animals,
                    add = {
                        editingAnimal = null
                        showAnimal = true
                    },
                    edit = {
                        editingAnimal = it
                        showAnimal = true
                    },
                    update = { id, count ->
                        saveAnimals(animals.map {
                            if (it.id == id) it.copy(count = count) else it
                        })
                    },
                    delete = { id ->
                        saveAnimals(animals.filterNot { it.id == id })
                    }
                )

                2 -> StockScreen(
                    padding, stock,
                    { saveStock(it) },
                    { showMovement = true },
                    root
                )

                3 -> JournalScreen(
                    padding, root,
                    { showFeed = true },
                    { showWeight = true },
                    { showHealth = true }
                )

                4 -> FinanceScreen(
                    padding, money, income, expenses,
                    { showMoney = true }
                )

                else -> MoreScreen(
                    padding,
                    root,
                    bale,
                    {
                        root.put("baleWeight", it)
                        refresh()
                    },
                    { showEvent = true },
                    { backupLauncher.launch("agrocalc_backup.json") },
                    { restoreLauncher.launch(arrayOf("application/json")) },
                    { csvLauncher.launch("agrocalc_report.csv") },
                    { pdfLauncher.launch("agrocalc_report.pdf") },
                    { message = purchaseAdvice(root) }
                )
            }
        }
    }

    if (showAnimal) {
        AnimalForm(
            initial = editingAnimal,
            dismiss = {
                showAnimal = false
                editingAnimal = null
            }
        ) { a ->
            saveAnimals(
                animals.filterNot { it.id == a.id } + a
            )
            showAnimal = false
            editingAnimal = null
        }
    }

    if (showFeed) {
        FeedForm(animals, { showFeed = false }) { o ->
            addRecord("feeds", o)

            val s = readStock(root)
            saveStock(
                s.copy(
                    hay = (s.hay - o.optDouble("hay")).coerceAtLeast(0.0),
                    grain = (s.grain - o.optDouble("grain")).coerceAtLeast(0.0),
                    feed = (s.feed - o.optDouble("feed")).coerceAtLeast(0.0)
                )
            )
            showFeed = false
        }
    }

    if (showMoney) {
        MoneyForm({ showMoney = false }) {
            addRecord("money", it)
            showMoney = false
        }
    }

    if (showMovement) {
        MovementForm(
            dismiss = { showMovement = false }
        ) { o ->
            val currentStock = readStock(root)
            val amount = o.optDouble("amount", 0.0)
            val incoming = o.optString("type") == "Приход"
            val item = o.optString("item")

            if (!amount.isFinite() || amount <= 0.0) {
                message = "Укажи правильное количество"
            } else {
                val updated = when (item) {
                    "Сено" -> {
                        if (!incoming && amount > currentStock.hay) {
                            message = "На складе недостаточно сена"
                            null
                        } else {
                            currentStock.copy(
                                hay = if (incoming) currentStock.hay + amount
                                else currentStock.hay - amount
                            )
                        }
                    }

                    "Зерно" -> {
                        if (!incoming && amount > currentStock.grain) {
                            message = "На складе недостаточно зерна"
                            null
                        } else {
                            currentStock.copy(
                                grain = if (incoming) currentStock.grain + amount
                                else currentStock.grain - amount
                            )
                        }
                    }

                    "Комбикорм" -> {
                        if (!incoming && amount > currentStock.feed) {
                            message = "На складе недостаточно комбикорма"
                            null
                        } else {
                            currentStock.copy(
                                feed = if (incoming) currentStock.feed + amount
                                else currentStock.feed - amount
                            )
                        }
                    }

                    "Вода" -> {
                        if (!incoming && amount > currentStock.water) {
                            message = "На складе недостаточно воды"
                            null
                        } else {
                            currentStock.copy(
                                water = if (incoming) currentStock.water + amount
                                else currentStock.water - amount
                            )
                        }
                    }

                    else -> null
                }

                if (updated != null) {
                    saveStock(updated)
                    addRecord("movements", o)
                    showMovement = false
                }
            }
        }
    }

    if (showEvent) {
        EventForm(animals, { showEvent = false }) { o ->
            addRecord("events", o)

            val name = o.optString("animal")
            val count = o.optInt("count")
            val type = o.optString("type")

            saveAnimals(animals.map {
                if (it.name == name) {
                    val newCount = when (type) {
                        "Рождение" -> it.count + count
                        else -> (it.count - count).coerceAtLeast(0)
                    }
                    it.copy(count = newCount)
                } else it
            })

            if (type == "Продажа") {
                addRecord(
                    "money",
                    JSONObject()
                        .put("id", System.currentTimeMillis())
                        .put("date", dateNow())
                        .put("title", "Продажа: $name")
                        .put("amount", o.optDouble("amount"))
                        .put("type", "Доход")
                        .put("category", "Продажа животных")
                )
            }
            showEvent = false
        }
    }

    if (showWeight) {
        SimpleAnimalForm(
            "Запись веса", animals, { showWeight = false },
            "Вес, кг", "weight"
        ) { addRecord("weights", it); showWeight = false }
    }

    if (showHealth) {
        HealthForm(animals, { showHealth = false }) {
            addRecord("health", it)
            showHealth = false
        }
    }

    if (message.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { message = "" },
            title = { Text("AgroCalc") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { message = "" }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun Page(
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String = "") {
    Column {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 12.sp, color = Gray)
        }
    }
}

@Composable
private fun Info(title: String, value: String, emoji: String = "📌") {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 27.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Gray, fontSize = 12.sp)
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun HomeScreen(
    p: PaddingValues,
    total: Int,
    hay: Double,
    bales: Int,
    grain: Double,
    feed: Double,
    water: Double,
    stock: Stock,
    profit: Double,
    animals: List<Animal>
) {
    Page(p) {
        Heading("Обзор хозяйства", "Потребность рассчитана на 30 дней")
        Info("Общее поголовье", "$total голов", "🐄")
        Info("Сено на 30 дней", "${number(hay)} кг · $bales тюков", "🌾")
        Info("Зерно на 30 дней", "${number(grain)} кг", "🌱")
        Info("Комбикорм на 30 дней", "${number(feed)} кг", "🥣")
        Info("Вода на 30 дней", "${number(water)} л", "💧")
        Heading("Остатки на складе")
        Info("Сено", "${number(stock.hay)} кг", "🌾")
        Info("Зерно", "${number(stock.grain)} кг", "🌱")
        Info("Комбикорм", "${number(stock.feed)} кг", "🥣")
        Info("Вода", "${number(stock.water)} л", "💧")
        Info("Финансовый результат", cash(profit), "💰")
        Heading("Поголовье")
        animals.forEach {
            Text("${it.emoji} ${it.name}: ${it.count} голов")
        }
    }
}

@Composable
private fun AnimalsScreen(
    p: PaddingValues,
    animals: List<Animal>,
    add: () -> Unit,
    edit: (Animal) -> Unit,
    update: (Long, Int) -> Unit,
    delete: (Long) -> Unit
) {
    Page(p) {
        Heading("Мои животные", "Нормы потребления на одну голову за 30 дней")
        ActionButton("+ Добавить животное", add)

        animals.forEach { a ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${a.emoji} ${a.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Сено: ${number(a.hay30)} кг\n" +
                                "Зерно: ${number(a.grain30)} кг\n" +
                                "Комбикорм: ${number(a.feed30)} кг\n" +
                                "Вода: ${number(a.water30)} л",
                        fontSize = 13.sp,
                        color = Gray
                    )

                    var countText by remember(a.id) {
                        mutableStateOf(a.count.toString())
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val n = (a.count - 1).coerceAtLeast(0)
                                countText = n.toString()
                                update(a.id, n)
                            }
                        ) { Text("−") }

                        OutlinedTextField(
                            value = countText,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    countText = value
                                    value.toIntOrNull()?.let {
                                        update(a.id, it)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Голов") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )

                        OutlinedButton(
                            onClick = {
                                val n = a.count + 1
                                countText = n.toString()
                                update(a.id, n)
                            }
                        ) { Text("+") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { edit(a) },
                            modifier = Modifier.weight(1f)
                        ) { Text("✏ Изменить") }

                        OutlinedButton(
                            onClick = { delete(a.id) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockScreen(
    p: PaddingValues,
    stock: Stock,
    change: (Stock) -> Unit,
    movement: () -> Unit,
    root: JSONObject
) {
    var hayText by remember(stock) {
        mutableStateOf(stock.hay.toString())
    }
    var grainText by remember(stock) {
        mutableStateOf(stock.grain.toString())
    }
    var feedText by remember(stock) {
        mutableStateOf(stock.feed.toString())
    }
    var waterText by remember(stock) {
        mutableStateOf(stock.water.toString())
    }

    var error by remember { mutableStateOf("") }

    Page(p) {
        Heading("Умный склад", "Введи новые остатки и нажми «Сохранить»")

        OutlinedTextField(
            value = hayText,
            onValueChange = {
                if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                    hayText = it
                    error = ""
                }
            },
            label = { Text("Сено, кг") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = grainText,
            onValueChange = {
                if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                    grainText = it
                    error = ""
                }
            },
            label = { Text("Зерно, кг") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = feedText,
            onValueChange = {
                if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                    feedText = it
                    error = ""
                }
            },
            label = { Text("Комбикорм, кг") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = waterText,
            onValueChange = {
                if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                    waterText = it
                    error = ""
                }
            },
            label = { Text("Вода, л") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true
        )

        if (error.isNotBlank()) {
            Text(error, color = Color.Red)
        }

        Button(
            onClick = {
                val values = listOf(
                    hayText,
                    grainText,
                    feedText,
                    waterText
                )

                val valid = values.all {
                    it.isNotBlank() && parse(it) >= 0
                }

                if (valid) {
                    change(
                        Stock(
                            hay = parse(hayText),
                            grain = parse(grainText),
                            feed = parse(feedText),
                            water = parse(waterText)
                        )
                    )
                    error = ""
                } else {
                    error = "Заполни все поля корректными числами"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить остатки")
        }

        OutlinedButton(
            onClick = movement,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Приход / списание")
        }

        Heading("Текущие остатки")
        Info("Сено", "${number(stock.hay)} кг", "🌾")
        Info("Зерно", "${number(stock.grain)} кг", "🌱")
        Info("Комбикорм", "${number(stock.feed)} кг", "🥣")
        Info("Вода", "${number(stock.water)} л", "💧")

        Heading("История движений")

        list(root, "movements")
            .takeLast(30)
            .asReversed()
            .forEach {
                Text(
                    "${it.optString("date")} · " +
                            "${it.optString("type")} " +
                            "${it.optString("item")}: " +
                            "${number(it.optDouble("amount"))}"
                )
            }
    }
}

@Composable
private fun JournalScreen(
    p: PaddingValues,
    root: JSONObject,
    feed: () -> Unit,
    weight: () -> Unit,
    health: () -> Unit
) {
    Page(p) {
        Heading("Журнал хозяйства")
        ActionButton("+ Записать кормление", feed)
        ActionButton("+ Записать вес", weight)
        ActionButton("+ Ветеринарная запись", health)

        Heading("Кормление")
        list(root, "feeds").takeLast(30).asReversed().forEach {
            Text(
                "${it.optString("date")} · ${it.optString("animal")}: " +
                        "сено ${number(it.optDouble("hay"))} кг, " +
                        "зерно ${number(it.optDouble("grain"))} кг, " +
                        "комбикорм ${number(it.optDouble("feed"))} кг"
            )
        }

        Heading("Вес животных")
        list(root, "weights").takeLast(20).asReversed().forEach {
            Text("${it.optString("date")} · ${it.optString("animal")}: ${it.optDouble("weight")} кг")
        }

        Heading("Здоровье животных")
        list(root, "health").takeLast(20).asReversed().forEach {
            Text("${it.optString("date")} · ${it.optString("animal")}: ${it.optString("title")}")
        }
    }
}

@Composable
private fun FinanceScreen(
    p: PaddingValues,
    entries: List<JSONObject>,
    income: Double,
    expenses: Double,
    add: () -> Unit
) {
    Page(p) {
        Heading("Финансы")
        Info("Доходы", cash(income), "📈")
        Info("Расходы", cash(expenses), "📉")
        Info("Прибыль / убыток", cash(income - expenses), "💰")
        ActionButton("+ Добавить операцию", add)
        Heading("История операций")
        entries.asReversed().forEach {
            Text(
                "${it.optString("date")} · ${it.optString("type")} · " +
                        "${it.optString("title")}: ${cash(it.optDouble("amount"))}"
            )
        }
    }
}

@Composable
private fun MoreScreen(
    p: PaddingValues,
    root: JSONObject,
    bale: Double,
    setBale: (Double) -> Unit,
    event: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    csv: () -> Unit,
    pdf: () -> Unit,
    planner: () -> Unit
) {
    Page(p) {
        Heading("Дополнительно")
        ActionButton("+ Продажа / рождение / падёж", event)
        NumberField("Вес одного тюка сена, кг", bale) {
            if (it > 0) setBale(it)
        }
        ActionButton("Прогноз закупок на 30 дней", planner)
        ActionButton("Сохранить отчёт PDF", pdf)
        ActionButton("Сохранить таблицу Excel (CSV)", csv)
        ActionButton("Создать резервную копию", backup)
        ActionButton("Восстановить резервную копию", restore)

        Heading("История событий")
        list(root, "events").takeLast(30).asReversed().forEach {
            Text(
                "${it.optString("date")} · ${it.optString("type")} · " +
                        "${it.optString("animal")}: ${it.optInt("count")} голов"
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Double,
    onChange: (Double) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(if (value == 0.0) "" else value.toString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                text = it
                onChange(parse(it))
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun TextFieldApp(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun FormDialog(
    title: String,
    dismiss: () -> Unit,
    save: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = {
            Button(onClick = save) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun AnimalForm(
    initial: Animal?,
    dismiss: () -> Unit,
    save: (Animal) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var emoji by remember(initial?.id) { mutableStateOf(initial?.emoji ?: "🐐") }
    var count by remember(initial?.id) { mutableStateOf((initial?.count ?: 0).toString()) }
    var hay by remember(initial?.id) { mutableStateOf((initial?.hay30 ?: 0.0).toString()) }
    var grain by remember(initial?.id) { mutableStateOf((initial?.grain30 ?: 0.0).toString()) }
    var feed by remember(initial?.id) { mutableStateOf((initial?.feed30 ?: 0.0).toString()) }
    var water by remember(initial?.id) { mutableStateOf((initial?.water30 ?: 0.0).toString()) }

    FormDialog(
        if (initial == null) "Новое животное" else "Изменить животное",
        dismiss,
        {
            if (name.isNotBlank()) {
                save(
                    Animal(
                        initial?.id ?: System.currentTimeMillis(),
                        name,
                        emoji,
                        (count.toIntOrNull() ?: 0).coerceAtLeast(0),
                        parse(hay),
                        parse(grain),
                        parse(feed),
                        parse(water)
                    )
                )
            }
        }
    ) {
        TextFieldApp("Название", name) { name = it }
        TextFieldApp("Эмодзи", emoji) { emoji = it }
        TextFieldApp("Количество голов", count) { count = it }
        TextFieldApp("Сено, кг / 1 голову за 30 дней", hay) { hay = it }
        TextFieldApp("Зерно, кг / 1 голову за 30 дней", grain) { grain = it }
        TextFieldApp("Комбикорм, кг / 1 голову за 30 дней", feed) { feed = it }
        TextFieldApp("Вода, л / 1 голову за 30 дней", water) { water = it }
    }
}

@Composable
private fun AnimalPicker(
    animals: List<Animal>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selected.isBlank()) "Выбрать животное" else selected)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            animals.forEach {
                DropdownMenuItem(
                    text = { Text(it.name) },
                    onClick = {
                        onSelected(it.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedForm(
    animals: List<Animal>,
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit
) {
    var animal by remember { mutableStateOf(animals.firstOrNull()?.name ?: "") }
    var hay by remember { mutableStateOf("0") }
    var grain by remember { mutableStateOf("0") }
    var feed by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }

    FormDialog("Запись кормления", dismiss, {
        save(
            JSONObject()
                .put("id", System.currentTimeMillis())
                .put("date", dateNow())
                .put("animal", animal)
                .put("hay", parse(hay))
                .put("grain", parse(grain))
                .put("feed", parse(feed))
                .put("note", note)
        )
    }) {
        AnimalPicker(animals, animal) { animal = it }
        TextFieldApp("Сено, кг", hay) { hay = it }
        TextFieldApp("Зерно, кг", grain) { grain = it }
        TextFieldApp("Комбикорм, кг", feed) { feed = it }
        TextFieldApp("Примечание", note) { note = it }
    }
}

@Composable
private fun MoneyForm(
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Расход") }
    var category by remember { mutableStateOf("Корма") }

    FormDialog("Финансовая операция", dismiss, {
        if (title.isNotBlank()) {
            save(
                JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("date", dateNow())
                    .put("title", title)
                    .put("amount", parse(amount))
                    .put("type", type)
                    .put("category", category)
            )
        }
    }) {
        TextFieldApp("Название операции", title) { title = it }
        TextFieldApp("Сумма, ₸", amount) { amount = it }

        Row {
            listOf("Расход", "Доход").forEach {
                FilterChip(
                    selected = type == it,
                    onClick = { type = it },
                    label = { Text(it) }
                )
            }
        }

        TextFieldApp("Категория", category) { category = it }
    }
}

@Composable
private fun MovementForm(
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit
) {
    var item by remember { mutableStateOf("Сено") }
    var type by remember { mutableStateOf("Приход") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    FormDialog(
        "Движение склада",
        dismiss,
        {
            val value = amount.replace(',', '.').toDoubleOrNull()

            if (value == null || !value.isFinite() || value <= 0.0) {
                error = "Введи корректное количество больше нуля"
            } else {
                save(
                    JSONObject()
                        .put("id", System.currentTimeMillis())
                        .put("date", dateNow())
                        .put("item", item)
                        .put("amount", value)
                        .put("type", type)
                        .put("note", note)
                )
            }
        }
    ) {
        Text("Выбери корм или воду")

        listOf("Сено", "Зерно", "Комбикорм", "Вода").forEach {
            FilterChip(
                selected = item == it,
                onClick = { item = it },
                label = { Text(it) }
            )
        }

        Text("Тип операции")

        Row {
            listOf("Приход", "Списание").forEach {
                FilterChip(
                    selected = type == it,
                    onClick = { type = it },
                    label = { Text(it) }
                )
            }
        }

        TextFieldApp("Количество", amount) {
            if (it.matches(Regex("[0-9]*[.,]?[0-9]*"))) {
                amount = it
                error = ""
            }
        }

        TextFieldApp("Поставщик / примечание", note) {
            note = it
        }

        if (error.isNotBlank()) {
            Text(error, color = Color.Red)
        }
    }
}

@Composable
private fun EventForm(
    animals: List<Animal>,
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit
) {
    var animal by remember { mutableStateOf(animals.firstOrNull()?.name ?: "") }
    var type by remember { mutableStateOf("Рождение") }
    var count by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }

    FormDialog("Событие с животными", dismiss, {
        save(
            JSONObject()
                .put("id", System.currentTimeMillis())
                .put("date", dateNow())
                .put("animal", animal)
                .put("type", type)
                .put("count", count.toIntOrNull() ?: 0)
                .put("amount", parse(amount))
                .put("note", note)
        )
    }) {
        AnimalPicker(animals, animal) { animal = it }

        Row {
            listOf("Рождение", "Продажа", "Падёж").forEach {
                FilterChip(
                    selected = type == it,
                    onClick = { type = it },
                    label = { Text(it) }
                )
            }
        }

        TextFieldApp("Количество голов", count) { count = it }

        if (type == "Продажа") {
            TextFieldApp("Сумма продажи, ₸", amount) { amount = it }
        }

        TextFieldApp("Примечание", note) { note = it }
    }
}

@Composable
private fun SimpleAnimalForm(
    title: String,
    animals: List<Animal>,
    dismiss: () -> Unit,
    field: String,
    key: String,
    save: (JSONObject) -> Unit
) {
    var animal by remember { mutableStateOf(animals.firstOrNull()?.name ?: "") }
    var value by remember { mutableStateOf("") }

    FormDialog(title, dismiss, {
        save(
            JSONObject()
                .put("id", System.currentTimeMillis())
                .put("date", dateNow())
                .put("animal", animal)
                .put(key, parse(value))
        )
    }) {
        AnimalPicker(animals, animal) { animal = it }
        TextFieldApp(field, value) { value = it }
    }
}

@Composable
private fun HealthForm(
    animals: List<Animal>,
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit
) {
    var animal by remember { mutableStateOf(animals.firstOrNull()?.name ?: "") }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Осмотр") }
    var next by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    FormDialog("Ветеринарная запись", dismiss, {
        save(
            JSONObject()
                .put("id", System.currentTimeMillis())
                .put("date", dateNow())
                .put("animal", animal)
                .put("title", title)
                .put("kind", kind)
                .put("nextDate", next)
                .put("note", note)
        )
    }) {
        AnimalPicker(animals, animal) { animal = it }
        TextFieldApp("Процедура / диагноз", title) { title = it }

        Row {
            listOf("Осмотр", "Прививка", "Лечение", "Обработка").forEach {
                FilterChip(
                    selected = kind == it,
                    onClick = { kind = it },
                    label = { Text(it) }
                )
            }
        }

        TextFieldApp("Следующая дата", next) { next = it }
        TextFieldApp("Примечание / препарат", note) { note = it }
    }
}

private fun purchaseAdvice(root: JSONObject): String {
    val animals = readAnimals(root)
    val stock = readStock(root)

    val hay = animals.sumOf { it.count * it.hay30 }
    val grain = animals.sumOf { it.count * it.grain30 }
    val feed = animals.sumOf { it.count * it.feed30 }
    val water = animals.sumOf { it.count * it.water30 }

    return "Прогноз на 30 дней:\n" +
            "Сено: нужно ${number(hay)} кг, на складе ${number(stock.hay)} кг. " +
            "${if (hay > stock.hay) "Нехватка ${number(hay - stock.hay)} кг." else "Запаса достаточно."}\n" +
            "Зерно: нужно ${number(grain)} кг, на складе ${number(stock.grain)} кг. " +
            "${if (grain > stock.grain) "Нехватка ${number(grain - stock.grain)} кг." else "Запаса достаточно."}\n" +
            "Комбикорм: нужно ${number(feed)} кг, на складе ${number(stock.feed)} кг. " +
            "${if (feed > stock.feed) "Нехватка ${number(feed - stock.feed)} кг." else "Запаса достаточно."}\n" +
            "Вода: ориентировочно ${number(water)} л."
}

private fun makeCsv(root: JSONObject): String {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    val out = StringBuilder("\uFEFFРаздел;Дата;Название;Количество;Тип;Примечание\n")

    list(root, "feeds").forEach {
        out.append(
            listOf(
                "Кормление",
                it.optString("date"),
                it.optString("animal"),
                "Сено ${it.optDouble("hay")} кг, зерно ${it.optDouble("grain")} кг, комбикорм ${it.optDouble("feed")} кг",
                "Выдача",
                it.optString("note")
            ).joinToString(";") { v -> q(v) }
        ).append('\n')
    }

    list(root, "money").forEach {
        out.append(
            listOf(
                "Финансы",
                it.optString("date"),
                it.optString("title"),
                it.optDouble("amount").toString(),
                it.optString("type"),
                it.optString("category")
            ).joinToString(";") { v -> q(v) }
        ).append('\n')
    }

    list(root, "events").forEach {
        out.append(
            listOf(
                "Животные",
                it.optString("date"),
                it.optString("animal"),
                it.optInt("count").toString(),
                it.optString("type"),
                it.optString("note")
            ).joinToString(";") { v -> q(v) }
        ).append('\n')
    }

    return out.toString()
}

private fun makePdf(root: JSONObject): ByteArray {
    val doc = PdfDocument()
    val paint = Paint().apply {
        textSize = 12f
        color = android.graphics.Color.BLACK
    }
    val titlePaint = Paint().apply {
        textSize = 20f
        isFakeBoldText = true
        color = android.graphics.Color.rgb(20, 83, 45)
    }

    val animals = readAnimals(root)
    val stock = readStock(root)
    val money = list(root, "money")

    val income = money.filter { it.optString("type") == "Доход" }
        .sumOf { it.optDouble("amount") }
    val expenses = money.filter { it.optString("type") == "Расход" }
        .sumOf { it.optDouble("amount") }

    val lines = mutableListOf<String>()
    lines += "Дата отчёта: ${dateNow()}"
    lines += "Поголовье: ${animals.sumOf { it.count }}"
    animals.forEach {
        lines += "${it.emoji} ${it.name}: ${it.count} голов"
    }

    lines += "СКЛАД"
    lines += "Сено: ${number(stock.hay)} кг"
    lines += "Зерно: ${number(stock.grain)} кг"
    lines += "Комбикорм: ${number(stock.feed)} кг"
    lines += "Вода: ${number(stock.water)} л"

    lines += "ФИНАНСЫ"
    lines += "Доходы: ${cash(income)}"
    lines += "Расходы: ${cash(expenses)}"
    lines += "Результат: ${cash(income - expenses)}"

    lines += "КОРМЛЕНИЕ"
    list(root, "feeds").takeLast(20).forEach {
        lines += "${it.optString("date")} ${it.optString("animal")}: " +
                "сено ${it.optDouble("hay")} кг, зерно ${it.optDouble("grain")} кг"
    }

    var page = 1
    var y = 55
    var info = PdfDocument.PageInfo.Builder(595, 842, page).create()
    var p = doc.startPage(info)
    var canvas = p.canvas

    canvas.drawText("AgroCalc 3.0 — отчёт хозяйства", 40f, 35f, titlePaint)

    lines.forEach { line ->
        if (y > 800) {
            doc.finishPage(p)
            page++
            info = PdfDocument.PageInfo.Builder(595, 842, page).create()
            p = doc.startPage(info)
            canvas = p.canvas
            y = 45
        }

        canvas.drawText(line.take(95), 40f, y.toFloat(), paint)
        y += 20
    }

    doc.finishPage(p)

    val bytes = java.io.ByteArrayOutputStream()
    doc.writeTo(bytes)
    doc.close()
    return bytes.toByteArray()
}