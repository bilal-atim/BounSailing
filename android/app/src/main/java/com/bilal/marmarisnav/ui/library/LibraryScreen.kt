package com.bilal.marmarisnav.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.library.Library
import com.bilal.marmarisnav.library.SearchHit
import com.bilal.marmarisnav.library.Topic

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, modifier: Modifier = Modifier) {
    val library by viewModel.library.collectAsState()
    val stack by viewModel.stack.collectAsState()

    val loaded = library
    if (loaded == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (val route = stack.last()) {
        LibraryRoute.Home -> LibraryHome(loaded, viewModel, modifier)

        is LibraryRoute.TopicView -> {
            val topic = loaded.topic(route.id)
            if (topic == null) {
                MissingContent("Konu bulunamadı: ${route.id}", viewModel::back, modifier)
            } else {
                TopicScreen(loaded, topic, viewModel, modifier)
            }
        }

        is LibraryRoute.SourceView -> {
            val doc = loaded.source(route.id)
            if (doc == null) {
                MissingContent("Kaynak bulunamadı: ${route.id}", viewModel::back, modifier)
            } else {
                SourceScreen(loaded, doc, viewModel, modifier)
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun LibraryHome(library: Library, viewModel: LibraryViewModel, modifier: Modifier) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val expanded by viewModel.expandedCategory.collectAsState()

    Column(modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onClear = viewModel::clearQuery,
        )

        if (query.isNotBlank()) {
            SearchResults(results, query, viewModel::openTopic)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = "Eğitim Konuları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                    )
                    Text(
                        text = "${library.topics.size} konu · ${library.sources.size} kaynak belge",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }

                items(library.categories, key = { it.id }) { category ->
                    val topics = library.topicsIn(category.id)
                    CategoryRow(
                        title = category.title,
                        subtitle = category.subtitle,
                        count = topics.size,
                        expanded = expanded == category.id,
                        onClick = { viewModel.toggleCategory(category.id) },
                    )
                    AnimatedVisibility(visible = expanded == category.id) {
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            for (topic in topics) {
                                TopicRow(topic) { viewModel.openTopic(topic.id) }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                item {
                    Text(
                        text = "Kaynak Belgeler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
                    )
                    Text(
                        text = "Konuların derlendiği kulüp dokümanlarının tam metni",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }

                items(library.sources, key = { it.id }) { doc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSource(doc.id) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(doc.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Konu veya terim ara…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Aramayı temizle")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun SearchResults(results: List<SearchHit>, query: String, onOpen: (String) -> Unit) {
    if (results.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("\"$query\" için sonuç yok", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Terimi kısaltmayı deneyin. Arama Türkçe karakter farkını yok sayar; " +
                    "\"kavanca\" ile \"kavança\" aynı sonucu verir.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text = "${results.size} konu bulundu",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        items(results, key = { it.topic.id }) { hit ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(hit.topic.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(hit.topic.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                hit.matchedKeyword?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "anahtar kelime: $it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hit.snippet,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopicRow(topic: Topic, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
    ) {
        Text(topic.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (topic.summary.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = topic.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- detail

@Composable
private fun TopicScreen(
    library: Library,
    topic: Topic,
    viewModel: LibraryViewModel,
    modifier: Modifier,
) {
    val backlinks = remember(topic.id) { library.backlinks(topic.id) }

    DetailPage(
        title = topic.title,
        onBack = { viewModel.back() },
        modifier = modifier,
    ) {
        if (topic.summary.isNotEmpty()) {
            Text(
                text = topic.summary,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        MarkdownBody(
            markdown = topic.body,
            onLink = { target -> handleLink(target, viewModel) },
            labelFor = { id -> library.labelFor(id) },
        )

        if (backlinks.isNotEmpty()) {
            SectionTitle("Bu konuya bağlanan konular")
            for (other in backlinks) {
                LinkRow(other.title) { viewModel.openTopic(other.id) }
            }
        }

        val sources = topic.sourceIds.mapNotNull { library.source(it) }
        if (sources.isNotEmpty()) {
            SectionTitle("Kaynaklar")
            Text(
                text = "Bu konu aşağıdaki kulüp dokümanlarından derlendi.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            for (doc in sources) {
                LinkRow(doc.title, icon = true) { viewModel.openSource(doc.id) }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SourceScreen(
    library: Library,
    doc: com.bilal.marmarisnav.library.SourceDoc,
    viewModel: LibraryViewModel,
    modifier: Modifier,
) {
    val citing = remember(doc.id) {
        library.topics.filter { doc.id in it.sourceIds }.sortedBy { it.title }
    }

    DetailPage(title = doc.title, onBack = { viewModel.back() }, modifier = modifier) {
        Text(
            text = "Kulüp kaynak belgesi — tam metin",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (citing.isNotEmpty()) {
            SectionTitle("Bu kaynağı kullanan eğitimler")
            for (topic in citing) {
                LinkRow(topic.title) { viewModel.openTopic(topic.id) }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
        }

        MarkdownBody(
            markdown = doc.body,
            onLink = { target -> handleLink(target, viewModel) },
            labelFor = { id -> library.labelFor(id) },
        )
        Spacer(Modifier.height(32.dp))
    }
}

private fun handleLink(target: LinkTarget, viewModel: LibraryViewModel) {
    when (target) {
        is LinkTarget.TopicRef -> viewModel.openTopic(target.id)
        is LinkTarget.SourceRef -> viewModel.openSource(target.id)
        // Source documents carry the odd http link; the library is offline, so
        // there is nowhere useful to send it.
        is LinkTarget.External -> Unit
    }
}

private fun Library.labelFor(id: String): String = when {
    id.startsWith("src:") -> source(id.removePrefix("src:"))?.title ?: prettyLabel(id)
    else -> topic(id)?.title ?: prettyLabel(id)
}

@Composable
private fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Keyed on the title so following a reference opens the new page at the
        // top instead of inheriting the previous page's scroll offset.
        val scroll = remember(title) { ScrollState(0) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LinkRow(text: String, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MissingContent(message: String, onBack: () -> Unit, modifier: Modifier) {
    DetailPage(title = "Bulunamadı", onBack = { onBack() }, modifier = modifier) {
        Text(message)
    }
}
