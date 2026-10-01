package com.retailshop.mini

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ========== DATA MODELS ==========
data class Product(val id: Long, val name: String, val price: Double, val stock: Double, val unit: String = "pcs")
data class SaleItem(val productId: Long, val name: String, val qty: Double, val rate: Double) {
    val total get() = qty * rate
}
data class Sale(val id: Long, val invoiceNo: String, val customer: String,
                val items: List<SaleItem>, val total: Double, val date: Long,
                val cash: Double, val upi: Double) {
    val paid get() = cash + upi
    val credit get() = (total - paid).coerceAtLeast(0.0)
}
data class AppData(
    val products: MutableList<Product> = mutableListOf(),
    val sales: MutableList<Sale> = mutableListOf(),
    var nextPid: Long = 1,
    var nextSid: Long = 1,
    var nextInv: Int = 1
)

// ========== STORAGE ==========
class Store(val ctx: Context) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = File(ctx.filesDir, "retail_data.json")

    var data by mutableStateOf(load())
        private set

    private fun load(): AppData = try {
        if (file.exists()) gson.fromJson(file.readText(), AppData::class.java) ?: AppData() else AppData()
    } catch (e: Exception) { AppData() }

    fun save() {
        try { file.writeText(gson.toJson(data)) } catch (_: Exception) {}
        data = AppData(data.products.toMutableList(), data.sales.toMutableList(), data.nextPid, data.nextSid, data.nextInv)
    }

    fun addProduct(name: String, price: Double, stock: Double) {
        data.products.add(Product(data.nextPid++, name, price, stock))
        save()
    }

    fun updateProduct(p: Product) {
        val i = data.products.indexOfFirst { it.id == p.id }
        if (i >= 0) { data.products[i] = p; save() }
    }

    fun deleteProduct(id: Long) {
        data.products.removeAll { it.id == id }
        save()
    }

    fun createSale(customer: String, items: List<SaleItem>, cash: Double, upi: Double): Sale {
        val total = items.sumOf { it.total }
        val inv = "INV-" + data.nextInv.toString().padStart(4, '0')
        data.nextInv++
        items.forEach { item ->
            val i = data.products.indexOfFirst { it.id == item.productId }
            if (i >= 0) {
                val p = data.products[i]
                data.products[i] = p.copy(stock = (p.stock - item.qty).coerceAtLeast(0.0))
            }
        }
        val s = Sale(data.nextSid++, inv, customer.ifBlank { "Walk-in" },
            items, total, System.currentTimeMillis(), cash, upi)
        data.sales.add(0, s)
        save()
        return s
    }

    fun exportJson(): String = gson.toJson(data)

    fun importJson(json: String): Boolean = try {
        val d = gson.fromJson(json, AppData::class.java) ?: return false
        data = AppData(d.products.toMutableList(), d.sales.toMutableList(), d.nextPid, d.nextSid, d.nextInv)
        save()
        true
    } catch (e: Exception) { false }
}

// ========== APP ==========
enum class Screen { HOME, PRODUCTS, NEW_SALE, HISTORY, BACKUP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MaterialTheme { AppRoot(store) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(store: Store) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (screen) {
                    Screen.HOME -> "Retail Shop"
                    Screen.PRODUCTS -> "Products"
                    Screen.NEW_SALE -> "New Sale"
                    Screen.HISTORY -> "Sales History"
                    Screen.BACKUP -> "Backup & Restore"
                }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = { screen = Screen.HOME }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (screen) {
                Screen.HOME -> HomeScreen(store) { screen = it }
                Screen.PRODUCTS -> ProductsScreen(store)
                Screen.NEW_SALE -> NewSaleScreen(store) { screen = Screen.HOME }
                Screen.HISTORY -> HistoryScreen(store)
                Screen.BACKUP -> BackupScreen(store)
            }
        }
    }
}

val nf: NumberFormat get() = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@Composable
fun BigButton(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, accent: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
fun HomeScreen(store: Store, navigate: (Screen) -> Unit) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val todaySales = store.data.sales.filter { it.date >= todayStart }
    val todayTotal = todaySales.sumOf { it.total }
    val totalCredit = store.data.sales.sumOf { it.credit }
    val stockValue = store.data.products.sumOf { it.price * it.stock }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("New Sale", Icons.Default.Add, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { navigate(Screen.NEW_SALE) }
            BigButton("Products", Icons.Default.Inventory, Color(0xFF9C27B0), Modifier.weight(1f)) { navigate(Screen.PRODUCTS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("History", Icons.Default.History, Color(0xFF4CAF50), Modifier.weight(1f)) { navigate(Screen.HISTORY) }
            BigButton("Backup", Icons.Default.Backup, Color(0xFFFF9800), Modifier.weight(1f)) { navigate(Screen.BACKUP) }
        }

        Spacer(Modifier.height(4.dp))
        InfoCard("Today's Sales", nf.format(todayTotal), MaterialTheme.colorScheme.primary)
        InfoCard("Sales Count", store.data.sales.size.toString(), Color(0xFF2196F3))
        InfoCard("Products Count", store.data.products.size.toString(), Color(0xFF9C27B0))
        InfoCard("Stock Value (Selling)", nf.format(stockValue), Color(0xFF4CAF50))
        InfoCard("Pending Credit", nf.format(totalCredit), MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ProductsScreen(store: Store) {
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showDialog = true }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { pad ->
        if (store.data.products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("No products yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to add your first item", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(store.data.products, key = { it.id }) { p ->
                    Card(shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text("Price: ${nf.format(p.price)}", style = MaterialTheme.typography.bodySmall)
                                Text("Stock: ${p.stock} ${p.unit}", style = MaterialTheme.typography.bodySmall,
                                    color = if (p.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editTarget = p; showDialog = true }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { store.deleteProduct(p.id) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        ProductDialog(initial = editTarget,
            onDismiss = { showDialog = false },
            onSave = { name, price, stock ->
                if (editTarget == null) store.addProduct(name, price, stock)
                else store.updateProduct(editTarget!!.copy(name = name, price = price, stock = stock))
                showDialog = false
            })
    }
}

@Composable
fun ProductDialog(initial: Product?, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; err = null },
                    label = { Text("Product Name") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it; err = null },
                    label = { Text("Selling Price") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("Rs.") })
                OutlinedTextField(value = stock, onValueChange = { stock = it; err = null },
                    label = { Text("Stock") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { err = "Enter product name"; return@Button }
                val p = price.toDoubleOrNull()
                if (p == null || p < 0) { err = "Enter valid price"; return@Button }
                val s = stock.toDoubleOrNull() ?: 0.0
                onSave(name.trim(), p, s)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun NewSaleScreen(store: Store, onDone: () -> Unit) {
    val cart = remember { mutableStateListOf<SaleItem>() }
    var customer by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("") }
    var showProductPicker by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf<Sale?>(null) }
    val context = LocalContext.current

    val cartTotal = cart.sumOf { it.total }
    val cashD = cash.toDoubleOrNull() ?: 0.0
    val upiD = upi.toDoubleOrNull() ?: 0.0
    val paid = cashD + upiD
    val credit = (cartTotal - paid).coerceAtLeast(0.0)

    LaunchedEffect(Unit) {
        // Auto-fill full cash if empty
        if (cash.isBlank() && upi.isBlank()) cash = ""
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(nf.format(cartTotal), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (cart.isEmpty()) return@Button
                            if (paid > cartTotal + 0.01) {
                                Toast.makeText(context, "Paid > total", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val s = store.createSale(customer, cart.toList(), cashD, upiD)
                            showSuccess = s
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = cart.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Complete Sale", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            OutlinedTextField(value = customer, onValueChange = { customer = it },
                label = { Text("Customer Name (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

            Spacer(Modifier.height(8.dp))

            OutlinedButton(onClick = { showProductPicker = true },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Item")
            }

            Spacer(Modifier.height(8.dp))

            if (cart.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Cart is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cart, key = { it.productId }) { item ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text("${item.qty} × ${nf.format(item.rate)}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text(nf.format(item.total), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = {
                                    cart.removeAll { it.productId == item.productId }
                                }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cash, onValueChange = { cash = it },
                        label = { Text("Cash") }, modifier = Modifier.weight(1f),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = upi, onValueChange = { upi = it },
                        label = { Text("UPI") }, modifier = Modifier.weight(1f),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }

                if (credit > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Credit (Udhaar): ${nf.format(credit)}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showProductPicker) {
        ModalBottomSheet(onDismissRequest = { showProductPicker = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Product", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (store.data.products.isEmpty()) {
                    Text("No products. Add products first.", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(store.data.products) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text("${nf.format(p.price)} • Stock: ${p.stock} ${p.unit}") },
                                trailingContent = {
                                    TextButton(
                                        onClick = {
                                            val existing = cart.indexOfFirst { it.productId == p.id }
                                            if (existing >= 0) {
                                                cart[existing] = cart[existing].copy(qty = cart[existing].qty + 1)
                                            } else {
                                                if (p.stock <= 0) {
                                                    Toast.makeText(context, "Out of stock", Toast.LENGTH_SHORT).show()
                                                    return@TextButton
                                                }
                                                cart.add(SaleItem(p.id, p.name, 1.0, p.price))
                                            }
                                            showProductPicker = false
                                        },
                                        enabled = p.stock > 0
                                    ) { Text(if (p.stock > 0) "Add" else "Out") }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    showSuccess?.let { s ->
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp)) },
            title = { Text("Sale Completed") },
            text = { Text("Invoice: ${s.invoiceNo}\nTotal: ${nf.format(s.total)}\nPaid: ${nf.format(s.paid)}" +
                    if (s.credit > 0) "\nCredit: ${nf.format(s.credit)}" else "") },
            confirmButton = {
                Button(onClick = { showSuccess = null; onDone() }) { Text("OK") }
            })
    }
}

@Composable
fun HistoryScreen(store: Store) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    if (store.data.sales.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No sales yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(store.data.sales, key = { it.id }) { s ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.invoiceNo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(nf.format(s.total), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.customer, style = MaterialTheme.typography.bodyMedium)
                            Text(fmt.format(Date(s.date)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text("Cash: ${nf.format(s.cash)}") })
                            if (s.upi > 0) AssistChip(onClick = {}, label = { Text("UPI: ${nf.format(s.upi)}") })
                            if (s.credit > 0) AssistChip(onClick = {},
                                label = { Text("Credit: ${nf.format(s.credit)}") },
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupScreen(store: Store) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            out.write(store.exportJson().toByteArray())
                        }
                    }
                    status = "✅ Backup saved successfully!\nFile: ${it.lastPathSegment}"
                } catch (e: Exception) {
                    status = "❌ Backup failed: ${e.message}"
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                    }
                    val ok = store.importJson(json)
                    status = if (ok) "✅ Restore successful!\nProducts: ${store.data.products.size}, Sales: ${store.data.sales.size}"
                             else "❌ Invalid backup file"
                } catch (e: Exception) {
                    status = "❌ Restore failed: ${e.message}"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Why backup?", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Save all your products and sales to a file. Keep it safe on Google Drive, SD card, or Downloads.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                val fname = "RetailBackup_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())}.json"
                saveLauncher.launch(fname)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp))
            Text("BACKUP NOW", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp))
            Text("RESTORE FROM BACKUP", fontWeight = FontWeight.SemiBold)
        }

        status?.let {
            Card(shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (it.startsWith("✅")) Color(0xFFC8E6C9) else Color(0xFFFFCDD2))) {
                Text(it, Modifier.padding(14.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp))
                    Text("Current Data", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Products: ${store.data.products.size}")
                Text("Sales: ${store.data.sales.size}")
                Text("Next Invoice: INV-${store.data.nextInv.toString().padStart(4, '0')}")
            }
        }
    }
}
