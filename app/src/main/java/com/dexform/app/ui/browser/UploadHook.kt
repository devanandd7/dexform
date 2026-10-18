package com.dexform.app.ui.browser

import org.json.JSONObject
import org.json.JSONTokener

const val UPLOAD_HOOK_JS = """
(function(){
  if (window.__dexform) return;
  var S = window.__dexform = {last: null};
  function txt(el){ try { return (el.innerText || el.textContent || '').replace(/\s+/g,' ').trim(); } catch(e){ return ''; } }
  function hints(input){
    var out = [];
    if (input.labels && input.labels.length) out.push(txt(input.labels[0]));
    ['aria-label','title','placeholder','data-original-title'].forEach(function(a){
      var v = input.getAttribute(a); if (v) out.push(v);
    });
    var p = input.previousElementSibling; if (p) out.push(txt(p));
    var n = input.nextElementSibling; if (n) out.push(txt(n));
    var el = input.parentElement, d = 0;
    while (el && d < 5) { var t = txt(el); if (t) out.push(t); el = el.parentElement; d++; }
    return out.filter(function(s){ return s && s.length <= 400; }).slice(0, 10);
  }
  document.addEventListener('click', function(e){
    var t = e.target, input = null;
    if (t && t.tagName === 'INPUT' && t.type === 'file') input = t;
    else if (t && t.closest) {
      var l = t.closest('label');
      if (l && l.control && l.control.type === 'file') input = l.control;
      else input = t.closest('input[type=file]');
    }
    if (!input) return;
    S.last = { ts: Date.now(), accept: input.accept || '', multiple: !!input.multiple,
               id: input.id || '', name: input.name || '', hints: hints(input) };
  }, true);
})();
"""

data class PageHint(
    val accept: String,
    val multiple: Boolean,
    val id: String,
    val name: String,
    val hints: List<String>
)

fun parsePageHint(raw: String?): PageHint? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val inner = try {
        JSONTokener(raw).nextValue() as? String
    } catch (_: Exception) {
        null
    } ?: return null
    if (inner == "null") return null
    val o = try {
        JSONObject(inner)
    } catch (_: Exception) {
        return null
    }
    if (System.currentTimeMillis() - o.optLong("ts") > 5000) return null // stale click
    val arr = o.optJSONArray("hints")
    val list = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optString(it) }
    return PageHint(
        accept = o.optString("accept"),
        multiple = o.optBoolean("multiple"),
        id = o.optString("id"),
        name = o.optString("name"),
        hints = list
    )
}

fun ruleKey(host: String, h: PageHint?): String {
    val field = h?.id?.ifBlank { null }
        ?: h?.name?.ifBlank { null }
        ?: h?.hints?.firstOrNull()?.take(40)?.hashCode()?.toString()
        ?: "unknown"
    return "$host|$field"
}
