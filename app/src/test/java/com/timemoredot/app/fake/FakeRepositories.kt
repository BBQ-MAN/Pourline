package com.timemoredot.app.fake

import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.RecipeRepository
import com.timemoredot.app.data.SessionDraft
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 메서드 이름 -> 다음 한 번만 던질 예외. Flow 메서드는 collect 시점에 던진다. */
open class FailureInjector {
    private val failures = mutableMapOf<String, Throwable>()
    fun failNext(method: String, e: Throwable) { failures[method] = e }
    protected fun check(method: String) { failures.remove(method)?.let { throw it } }
}

class FakeRecipeRepository(initial: List<Recipe> = emptyList()) : FailureInjector(), RecipeRepository {
    val recipes = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    val upserted = mutableListOf<Recipe>()
    val deletedIds = mutableListOf<Long>()
    val getIds = mutableListOf<Long>()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<Recipe>> {
        calls += "observeAll"
        return recipes.map { check("observeAll"); it.sortedBy(Recipe::name) }
    }

    override suspend fun get(id: Long): Recipe? { calls += "get"; getIds += id; check("get"); return recipes.value.firstOrNull { it.id == id } }

    override suspend fun upsert(recipe: Recipe): Long {
        calls += "upsert"; upserted += recipe; check("upsert")
        val saved = if (recipe.id == 0L) recipe.copy(id = nextId++) else recipe
        recipes.value = recipes.value.filter { it.id != saved.id } + saved
        return saved.id
    }

    override suspend fun delete(id: Long) {
        calls += "delete"; deletedIds += id; check("delete")
        recipes.value = recipes.value.filter { it.id != id }
    }
}

class FakeBrewRepository : FailureInjector(), BrewRepository {
    private class Row(var summary: SessionSummary, var blob: ByteArray)

    private val rows = MutableStateFlow<Map<Long, Row>>(emptyMap())
    private var tick = 0   // rows는 가변이라 값이 같아도 Flow가 다시 내도록 맵을 새로 만든다
    val calls = mutableListOf<String>()
    val drafts = mutableListOf<SessionDraft>()
    val finishCalls = mutableListOf<Triple<Long, Double?, SessionStatus>>()
    val deletedIds = mutableListOf<Long>()
    val findDraftArgs = mutableListOf<Long?>()
    val noteUpdates = mutableListOf<Pair<Long, String>>()
    private var nextId = 1L
    /** null이 아니면 saveDraft가 이 신호까지 멈춘다(주기 저장 경합 테스트). */
    var saveDraftGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun touch() { tick++; rows.value = LinkedHashMap(rows.value) }

    override fun observeSessions(): Flow<List<SessionSummary>> {
        calls += "observeSessions"
        return rows.map { m ->
            check("observeSessions")
            m.values.map { it.summary }.filter { it.status != SessionStatus.InProgress }
                .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
        }
    }

    override suspend fun summary(id: Long): SessionSummary? { calls += "summary"; check("summary"); return rows.value[id]?.summary }

    override suspend fun samples(id: Long): SampleBuffer? {
        calls += "samples"; check("samples")
        return rows.value[id]?.let { runCatching { SampleBuffer.fromBlob(it.blob) }.getOrNull() }
    }

    override suspend fun saveDraft(draft: SessionDraft): Long {
        calls += "saveDraft"; drafts += draft
        saveDraftGate?.let { saveDraftGate = null; it.await() }   // 한 번만: 테스트가 저장 진행 중 상태를 만든다
        check("saveDraft")
        if (draft.id != null && rows.value[draft.id]?.summary?.status != SessionStatus.InProgress) return draft.id   // 실제와 같은 규칙: in_progress 행만 갱신
        val id = draft.id ?: nextId++
        val s = SessionSummary(id, draft.startedAtEpochMs, draft.recipe?.name, draft.recipe, null, draft.finalWeightGrams, draft.durationMs, SessionStatus.InProgress, "")
        rows.value[id]?.let { it.summary = s; it.blob = draft.samplesBlob } ?: run { rows.value = rows.value + (id to Row(s, draft.samplesBlob)) }
        touch()
        return id
    }

    override suspend fun findDraft(excludingId: Long?): SessionSummary? {
        calls += "findDraft"; findDraftArgs += excludingId; check("findDraft")
        return rows.value.values.map { it.summary }.filter { it.status == SessionStatus.InProgress && it.id != excludingId }
            .maxWithOrNull(compareBy<SessionSummary> { it.startedAtEpochMs }.thenBy { it.id })
    }

    override suspend fun finish(id: Long, doseGrams: Double?, note: String, status: SessionStatus) {
        calls += "finish"; finishCalls += Triple(id, doseGrams, status); check("finish")
        rows.value[id]?.let { it.summary = it.summary.copy(doseGrams = doseGrams, note = note, status = status) }
        touch()
    }

    override suspend fun deleteSession(id: Long) {
        calls += "deleteSession"; deletedIds += id; check("deleteSession")
        rows.value = rows.value - id
    }

    override suspend fun updateNote(id: Long, note: String) {
        calls += "updateNote"; noteUpdates += id to note; check("updateNote")
        rows.value[id]?.let { it.summary = it.summary.copy(note = note) }
        touch()
    }
}
