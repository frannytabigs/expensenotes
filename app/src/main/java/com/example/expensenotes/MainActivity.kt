package com.example.expensenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensenotes.screens.NewExpenseScreen
import com.example.expensenotes.screens.ViewExpensesScreen
import com.example.expensenotes.screens.DeleteExpensesScreen
import com.example.expensenotes.screens.AboutScreen
import com.example.expensenotes.ui.theme.ExpenseNotesTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseNotesTheme {
                val navController = rememberNavController()

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet( modifier = Modifier.width(200.dp).padding(0.dp) ) {
                            Text(
                                text = "X",
                                fontSize = 35.sp,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable {
                                        scope.launch {
                                            drawerState.close()
                                        }
                                    }
                            )

                            NavigationDrawerItem(
                                label = { Text("New Expense") },
                                selected = currentRoute == "newexpense",
                                onClick = { scope.launch {
                                    drawerState.close()
                                }
                                    navController.navigate("newexpense") }
                            )
                            NavigationDrawerItem(
                                label = { Text("View Expenses") },
                                selected = currentRoute == "viewexpenses",
                                onClick = { scope.launch {
                                    drawerState.close()
                                }
                                    navController.navigate("viewexpenses") }
                            )
                            NavigationDrawerItem(
                                label = { Text("Delete Expenses") },
                                selected = currentRoute == "deleteexpenses",
                                onClick = { scope.launch {
                                    drawerState.close()
                                }
                                    navController.navigate("deleteexpenses") }
                            )

                            NavigationDrawerItem(
                                label = { Text("About") },
                                selected = currentRoute == "about",
                                onClick = { scope.launch {
                                    drawerState.close()
                                }
                                    navController.navigate("about") }
                            )

                        }
                    }
                ) {
                    Scaffold(
                        topBar = {

                            CenterAlignedTopAppBar(
                                title = { Text("\uD83D\uDCB8 Expense Notes") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "newexpense",
                            modifier = Modifier.padding(innerPadding),
                        ) {
                            composable("newexpense") { NewExpenseScreen() }
                            composable("viewexpenses") { ViewExpensesScreen()}
                            composable("deleteexpenses") {DeleteExpensesScreen()}
                            composable("about") {AboutScreen()}

                        }
                    }
                }
            }
        }
    }
}