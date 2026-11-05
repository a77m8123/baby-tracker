package com.babytracker.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.babytracker.logic.VaccineCalendar
import com.babytracker.logic.newId
import com.babytracker.logic.nowMs
import com.babytracker.model.ActiveFeed
import com.babytracker.model.AppData
import com.babytracker.model.Diaper
import com.babytracker.model.DiaperKind
import com.babytracker.model.FeedType
import com.babytracker.model.Feeding
import com.babytracker.model.Growth
import com.babytracker.model.MedDose
import com.babytracker.model.Medicine
import com.babytracker.model.Milk
import com.babytracker.model.Settings
import com.babytracker.model.Side
import com.babytracker.model.Sleep
import com.babytracker.model.Vaccine

const val STORAGE_KEY = "malysh-v2"

/** Всё состояние приложения; каждое изменение сразу сохраняется в [storage]. */
class BabyStore(private val storage: KeyValueStorage) {

    var data: AppData by mutableStateOf(AppDataCodec.decode(storage.read(STORAGE_KEY)))
        private set

    var saveFailed: Boolean by mutableStateOf(false)
        private set

    /** Вызывается после каждого изменения — платформа перепланирует напоминания и обновляет виджет. */
    var onChanged: ((AppData) -> Unit)? = null

    /** Перечитать хранилище: данные могли поменяться из виджета, пока приложение было в фоне. */
    fun reload() {
        val fresh = AppDataCodec.decode(storage.read(STORAGE_KEY))
        if (fresh != data) data = fresh
    }

    private inline fun update(transform: (AppData) -> AppData) {
        val next = transform(data)
        if (next == data) return
        data = next
        saveFailed = runCatching { storage.write(STORAGE_KEY, AppDataCodec.encode(next)) }.isFailure
        onChanged?.invoke(next)
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { it.copy(settings = transform(it.settings)) }

    // Сон

    fun startSleep(now: Long = nowMs()) = update { d ->
        if (d.activeSleep != null) d else d.copy(sleeps = d.sleeps + Sleep(newId(), now))
    }

    fun endSleep(now: Long = nowMs()) = update { d ->
        val active = d.activeSleep ?: return@update d
        d.copy(sleeps = d.sleeps.map { if (it.id == active.id) it.copy(end = now.coerceAtLeast(it.start)) else it })
    }

    fun toggleSleep(now: Long = nowMs()) = if (data.activeSleep != null) endSleep(now) else startSleep(now)

    fun saveSleep(sleep: Sleep) = update { d ->
        val list = d.sleeps.filter { it.id != sleep.id }
        // новый незакрытый сон закрывает предыдущий незакрытый
        val closed = if (sleep.end == null) list.map { if (it.end == null) it.copy(end = sleep.start.coerceAtLeast(it.start)) else it } else list
        d.copy(sleeps = closed + sleep)
    }

    fun deleteSleep(id: String) = update { d -> d.copy(sleeps = d.sleeps.filter { it.id != id }) }

    // Кормление

    fun startBreast(side: Side, now: Long = nowMs()) = update { d ->
        if (d.activeFeed != null) d else d.copy(activeFeed = ActiveFeed(startedAt = now, side = side, segmentStart = now))
    }

    fun switchSide(now: Long = nowMs()) = update { d ->
        val f = d.activeFeed ?: return@update d
        val other = if (f.side == Side.LEFT) Side.RIGHT else Side.LEFT
        d.copy(activeFeed = f.copy(
            side = other,
            segmentStart = now,
            leftMs = f.sideMs(Side.LEFT, now),
            rightMs = f.sideMs(Side.RIGHT, now),
        ))
    }

    fun togglePause(now: Long = nowMs()) = update { d ->
        val f = d.activeFeed ?: return@update d
        val next = if (f.paused) f.copy(segmentStart = now)
        else f.copy(segmentStart = null, leftMs = f.sideMs(Side.LEFT, now), rightMs = f.sideMs(Side.RIGHT, now))
        d.copy(activeFeed = next)
    }

    fun finishBreast(now: Long = nowMs()) = update { d ->
        val f = d.activeFeed ?: return@update d
        val feeding = Feeding(
            id = newId(),
            time = f.startedAt,
            type = FeedType.BREAST,
            leftSec = (f.sideMs(Side.LEFT, now) / 1000).toInt(),
            rightSec = (f.sideMs(Side.RIGHT, now) / 1000).toInt(),
            lastSide = f.side,
        )
        d.copy(activeFeed = null, feedings = d.feedings + feeding)
    }

    fun cancelBreast() = update { it.copy(activeFeed = null) }

    fun addBottle(ml: Int, milk: Milk, time: Long = nowMs()) = saveFeeding(
        Feeding(newId(), time, FeedType.BOTTLE, amountMl = ml.coerceIn(1, 1000), milk = milk),
    )

    fun addSolids(note: String, time: Long = nowMs()) = saveFeeding(
        Feeding(newId(), time, FeedType.SOLIDS, note = note.trim().take(80)),
    )

    fun saveFeeding(feeding: Feeding) = update { d ->
        d.copy(feedings = d.feedings.filter { it.id != feeding.id } + feeding)
    }

    fun deleteFeeding(id: String) = update { d -> d.copy(feedings = d.feedings.filter { it.id != id }) }

    // Подгузники

    fun addDiaper(kind: DiaperKind, time: Long = nowMs()) = saveDiaper(Diaper(newId(), time, kind))

    fun saveDiaper(diaper: Diaper) = update { d ->
        d.copy(diapers = d.diapers.filter { it.id != diaper.id } + diaper)
    }

    fun deleteDiaper(id: String) = update { d -> d.copy(diapers = d.diapers.filter { it.id != id }) }

    // Рост и вес

    fun saveGrowth(growth: Growth) = update { d ->
        d.copy(growth = (d.growth.filter { it.id != growth.id } + growth).sortedBy { it.time })
    }

    fun deleteGrowth(id: String) = update { d -> d.copy(growth = d.growth.filter { it.id != id }) }

    // Лекарства

    fun saveMedicine(medicine: Medicine) = update { d ->
        val exists = d.medicines.any { it.id == medicine.id }
        d.copy(medicines = if (exists) d.medicines.map { if (it.id == medicine.id) medicine else it } else d.medicines + medicine)
    }

    /** Удаляет лекарство вместе с историей приёмов. */
    fun deleteMedicine(id: String) = update { d ->
        d.copy(medicines = d.medicines.filter { it.id != id }, doses = d.doses.filter { it.medId != id })
    }

    fun giveDose(medId: String, time: Long = nowMs()) = update { d ->
        d.copy(doses = d.doses + MedDose(newId(), medId, time))
    }

    fun deleteDose(id: String) = update { d -> d.copy(doses = d.doses.filter { it.id != id }) }

    // Прививки

    /** Добавляет прививки из национального календаря, которых ещё нет в списке. */
    fun fillVaccineCalendar() = update { d ->
        val birth = d.settings.birthDate
        if (birth <= 0L) return@update d
        val existing = d.vaccines.map { it.name }.toSet()
        val added = VaccineCalendar.items
            .filter { it.name !in existing }
            .map { Vaccine(newId(), it.name, VaccineCalendar.dateFor(it, birth)) }
        d.copy(vaccines = (d.vaccines + added).sortedBy { it.plannedAt })
    }

    fun saveVaccine(vaccine: Vaccine) = update { d ->
        d.copy(vaccines = (d.vaccines.filter { it.id != vaccine.id } + vaccine).sortedBy { it.plannedAt })
    }

    fun toggleVaccineDone(id: String, now: Long = nowMs()) = update { d ->
        d.copy(vaccines = d.vaccines.map { if (it.id == id) it.copy(doneAt = if (it.doneAt == null) now else null) else it })
    }

    fun deleteVaccine(id: String) = update { d -> d.copy(vaccines = d.vaccines.filter { it.id != id }) }

    // Резервная копия

    fun exportJson(): String = AppDataCodec.encode(data)

    /** false — файл не похож на резервную копию «Малыша». */
    fun importJson(raw: String): Boolean {
        val parsed = AppDataCodec.decodeOrNull(raw) ?: return false
        update { parsed }
        return true
    }

    /** Стирает все записи, настройки остаются. */
    fun clearRecords() = update { AppData(settings = it.settings) }
}
