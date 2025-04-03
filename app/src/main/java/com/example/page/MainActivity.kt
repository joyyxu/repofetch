package com.example.page

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

// -----------------------------
// Data classes and API interface
// -----------------------------

data class GitHubRepo(
    val id: Long,
    val name: String,
    val description: String?
)

interface GitHubApi {
    @GET("users/{username}/repos")
    suspend fun getRepos(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): Response<List<GitHubRepo>>
}

object RetrofitInstance {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GitHubApi = retrofit.create(GitHubApi::class.java)
}

// -----------------------------
// UI State using Flow/StateFlow
// -----------------------------

sealed class RepoUiState {
    object Idle : RepoUiState()
    object Loading : RepoUiState()
    data class Success(val repos: List<GitHubRepo>, val hasNext: Boolean) : RepoUiState()
    data class Error(val message: String) : RepoUiState()
}

// -----------------------------
// ViewModel: handles API calls and pagination
// -----------------------------

class RepoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<RepoUiState>(RepoUiState.Idle)
    val uiState: StateFlow<RepoUiState> = _uiState

    private var currentPage = 1
    private var currentUsername: String? = null
    private val repoAccumulator = mutableListOf<GitHubRepo>()

    fun searchRepos(username: String) {
        currentUsername = username
        currentPage = 1
        repoAccumulator.clear()
        fetchRepos(username, currentPage, clear = true)
    }

    fun loadMore() {
        currentUsername?.let { username ->
            fetchRepos(username, currentPage + 1, clear = false)
        }
    }

    private fun fetchRepos(username: String, page: Int, clear: Boolean) {
        viewModelScope.launch {
            _uiState.value = RepoUiState.Loading
            try {
                val response = RetrofitInstance.api.getRepos(username, page)
                if (response.isSuccessful) {
                    val repos = response.body() ?: emptyList()
                    if (clear) repoAccumulator.clear()
                    repoAccumulator.addAll(repos)
                    // Check Link header for a "next" page indicator.
                    val linkHeader = response.headers()["Link"]
                    val hasNext = linkHeader?.contains("rel=\"next\"") ?: false
                    currentPage = page
                    _uiState.value = RepoUiState.Success(repoAccumulator.toList(), hasNext)
                } else {
                    _uiState.value = RepoUiState.Error("Error ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = RepoUiState.Error("Exception: ${e.localizedMessage}")
            }
        }
    }
}

// -----------------------------
// Composable UI: displays repositories and pagination
// -----------------------------

@Composable
fun RepoSearchScreen(viewModel: RepoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var usernameInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "GitHub Repositories",
            style = MaterialTheme.typography.headlineMedium
        )
        // Input row for username and search button.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.searchRepos(usernameInput.text) }) {
                Text(text = "Search")
            }
        }
        // Render UI based on current state.
        when (uiState) {
            is RepoUiState.Idle -> {
                Text(text = "Enter a GitHub username to begin.", fontSize = 16.sp)
            }
            is RepoUiState.Loading -> {
                CircularProgressIndicator()
            }
            is RepoUiState.Error -> {
                Text(
                    text = (uiState as RepoUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp
                )
            }
            is RepoUiState.Success -> {
                val state = uiState as RepoUiState.Success
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.repos) { repo ->
                        RepoItem(repo)
                    }
                    if (state.hasNext) {
                        item {
                            Button(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load More")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepoItem(repo: GitHubRepo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text = repo.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = repo.description ?: "No description provided",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RepoSearchScreen()
            }
        }
    }
}
