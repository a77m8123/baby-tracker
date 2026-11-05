@file:OptIn(ExperimentalEncodingApi::class)

package com.babytracker.web

import com.babytracker.NowPlaying
import com.babytracker.Platform
import com.babytracker.SoundPlayer
import com.babytracker.data.KeyValueStorage
import com.babytracker.logic.PdfPage
import com.babytracker.logic.Reminder
import com.babytracker.logic.WidgetStatus
import com.babytracker.logic.nowMs
import com.babytracker.sound.SoundSession
import com.babytracker.sound.SoundType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Мостики в браузерный JS. Только простые типы — так interop Kotlin/Wasm надёжнее всего.

@JsFun("(key) => { try { return localStorage.getItem(key); } catch (e) { return null; } }")
private external fun lsGet(key: String): String?

@JsFun("(key, value) => { localStorage.setItem(key, value); }")
private external fun lsSet(key: String, value: String)

@JsFun(
    """(fill) => {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        const ctx = new Ctx({ sampleRate: 44100 });
        const node = ctx.createScriptProcessor(4096, 1, 1);
        node.onaudioprocess = (e) => fill(e.outputBuffer.getChannelData(0));
        node.connect(ctx.destination);
        ctx.resume();
        return ctx;
    }"""
)
private external fun audioStart(fill: (JsAny) -> Unit): JsAny

@JsFun("(ctx) => { try { ctx.close(); } catch (e) {} }")
private external fun audioStop(ctx: JsAny)

@JsFun("(arr) => arr.length")
private external fun jsLength(arr: JsAny): Int

@JsFun("(arr, i, v) => { arr[i] = v; }")
private external fun setSample(arr: JsAny, i: Int, v: Float)

@JsFun("() => { if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission(); }")
private external fun requestNotificationPermission()

@JsFun(
    """(ms, title, body) => setTimeout(() => {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        const options = { body: body, icon: 'icons/icon-192.png' };
        try {
            new Notification(title, options);
        } catch (e) {
            // Chrome на Android показывает уведомления только через service worker
            if (navigator.serviceWorker) navigator.serviceWorker.ready.then((r) => r.showNotification(title, options));
        }
    }, ms)"""
)
private external fun notifyLater(ms: Int, title: String, body: String): Int

@JsFun("(id) => clearTimeout(id)")
private external fun clearTimer(id: Int)

@JsFun(
    """(name, mime, b64) => {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const file = new File([bytes], name, { type: mime });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
            navigator.share({ files: [file] }).catch(() => {});
            return;
        }
        const a = document.createElement('a');
        a.href = URL.createObjectURL(file);
        a.download = name;
        document.body.appendChild(a);
        a.click();
        setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    }"""
)
private external fun saveFile(name: String, mime: String, base64: String)

@JsFun(
    """(done) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json,application/json,text/plain';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { done(null); return; }
            file.text().then((t) => done(t), () => done(null));
        };
        input.click();
    }"""
)
private external fun chooseFile(done: (String?) -> Unit)

@JsFun(
    """(html) => {
        const w = window.open('', '_blank');
        if (!w) return false;
        w.document.open();
        w.document.write(html);
        w.document.close();
        w.focus();
        setTimeout(() => w.print(), 400);
        return true;
    }"""
)
private external fun openPrintWindow(html: String): Boolean

class LocalStorage : KeyValueStorage {
    override fun read(key: String): String? = lsGet(key)
    override fun write(key: String, value: String) = lsSet(key, value)
}

class WebPlatform : Platform {
    override val isIos = false

    override val sound = WebSoundPlayer()

    private val timers = mutableListOf<Int>()

    /** Браузер не будит закрытую вкладку — напоминания приходят, только пока она открыта. */
    override fun scheduleReminders(reminders: List<Reminder>) {
        timers.forEach(::clearTimer)
        timers.clear()
        val now = nowMs()
        reminders.forEach { r ->
            val delay = r.atMs - now
            // setTimeout не умеет больше ~24 дней
            if (delay in 1..2_000_000_000L) timers += notifyLater(delay.toInt(), r.title, r.text)
        }
    }

    override fun requestNotifications() = requestNotificationPermission()

    override fun publishStatus(status: WidgetStatus) {}

    override fun shareFile(name: String, mime: String, bytes: ByteArray) =
        saveFile(name, mime, Base64.Default.encode(bytes))

    override fun pickTextFile(onResult: (String?) -> Unit) = chooseFile(onResult)

    /** PDF собирает сам браузер: открываем отчёт в окне печати, там «Сохранить как PDF». */
    override fun renderPdf(pages: List<PdfPage>): ByteArray {
        if (!openPrintWindow(reportHtml(pages))) error("Окно печати заблокировано")
        return ByteArray(0)
    }

    private fun reportHtml(pages: List<PdfPage>): String = buildString {
        append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>Малыш — отчёт</title><style>")
        append("@page{size:A4;margin:0}body{margin:0;font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif}")
        append(".p{position:relative;width:595pt;height:842pt;overflow:hidden;page-break-after:always}")
        append(".p div{position:absolute;white-space:pre}</style></head><body>")
        pages.forEach { page ->
            append("<section class=\"p\" style=\"width:${page.width}pt;height:${page.height}pt\">")
            page.items.forEach { item ->
                val color = "#" + item.color.toString(16).padStart(6, '0')
                if (item.kind == "rect") {
                    append("<div style=\"left:${item.x}pt;top:${item.y}pt;width:${item.w}pt;height:${item.h}pt;background:$color\"></div>")
                } else {
                    append("<div style=\"left:${item.x}pt;top:${item.y}pt;font-size:${item.size}pt;line-height:1.15;color:$color")
                    if (item.bold) append(";font-weight:700")
                    append("\">")
                    append(item.text.escapeHtml())
                    append("</div>")
                }
            }
            append("</section>")
        }
        append("</body></html>")
    }

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Web Audio: браузер сам просит порции по 4096 сэмплов, их считает общий синтезатор. */
class WebSoundPlayer : SoundPlayer() {
    private var context: JsAny? = null
    private var session: SoundSession? = null
    private var scratch = FloatArray(4096)

    override fun play(type: SoundType, timerMinutes: Int, volume: Float) {
        halt()
        val s = SoundSession(type, timerMinutes, volume)
        session = s
        context = audioStart { channel -> fill(s, channel) }
        nowPlaying = NowPlaying(type, if (timerMinutes > 0) nowMs() + timerMinutes * 60_000L else null)
    }

    private fun fill(s: SoundSession, channel: JsAny) {
        val n = jsLength(channel)
        if (scratch.size < n) scratch = FloatArray(n)
        if (session !== s) {
            for (i in 0 until n) setSample(channel, i, 0f)
            return
        }
        s.fill(scratch, n)
        for (i in 0 until n) setSample(channel, i, scratch[i])
        if (s.finished) stop()
    }

    override fun setVolume(volume: Float) {
        session?.volume = volume
    }

    override fun stop() {
        halt()
        nowPlaying = null
    }

    private fun halt() {
        session = null
        context?.let(::audioStop)
        context = null
    }
}
