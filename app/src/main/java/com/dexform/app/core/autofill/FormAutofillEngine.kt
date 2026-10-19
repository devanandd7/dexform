package com.dexform.app.core.autofill

import android.webkit.WebView
import com.dexform.app.AppContainer
import com.dexform.app.data.db.FieldDefEntity
import com.dexform.app.data.db.FieldValueEntity
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class AutofillResult(
    val filledCount: Int,
    val filledFields: List<String>,
    val skippedCount: Int = 0
)

data class AutofillRule(
    val label: String,
    val value: String,
    val keywords: List<String>
)

object FormAutofillEngine {

    /**
     * Builds list of autofill candidates from active profile, dynamic bio, and sub-bio documents.
     */
    suspend fun prepareAutofillRules(
        container: AppContainer,
        profile: UserProfileEntity
    ): List<AutofillRule> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<AutofillRule>()

        fun addRule(label: String, value: String, vararg syns: String) {
            val v = value.trim()
            if (v.isNotBlank()) {
                val kwList = (listOf(label.lowercase()) + syns.map { it.lowercase() }).distinct()
                rules.add(AutofillRule(label = label, value = v, keywords = kwList))
            }
        }

        // 1. Core Profile Details
        addRule("Full Name", profile.name, "candidate name", "applicant name", "name", "full_name", "first name", "नाम")
        addRule("Father's Name", profile.fatherName, "father name", "father's name", "guardian name", "पिता का नाम", "s/o", "d/o")
        addRule("Mother's Name", profile.motherName, "mother name", "mother's name", "माता का नाम")
        addRule("Date of Birth", profile.dob, "dob", "birth date", "date of birth", "d.o.b", "जन्म तिथि")
        addRule("Gender", profile.gender, "sex", "gender", "लिंग")
        addRule("Category", profile.category, "caste", "category", "social category", "community", "वर्ग")
        addRule("Mobile Number", profile.mobile, "mobile", "phone", "contact", "mobile no", "contact no", "cell", "मोबाइल")
        addRule("Email Address", profile.email, "email", "e-mail", "email id", "mail", "ईमेल")
        addRule("Address", profile.address, "permanent address", "residential address", "address line", "full address", "पता")
        addRule("ID Number", profile.idNumber, "aadhaar", "aadhaar number", "pan", "pan number", "id proof", "identity number")

        // 2. 10th Academic Info
        addRule("10th Roll Number", profile.tenthRollNo, "10th roll", "matric roll", "high school roll", "secondary roll")
        addRule("10th Registration No", profile.tenthRegNo, "10th reg", "matric reg", "10th enrollment", "high school reg")
        addRule("10th Passing Year", profile.tenthPassingYear, "10th year", "matric year", "10th pass year", "year of passing 10th")
        addRule("10th Board", profile.tenthBoard, "10th board", "matric board", "high school board", "secondary board")
        addRule("10th Marks / %", profile.tenthMarks, "10th marks", "matric marks", "10th percentage", "10th aggregate")
        addRule("10th Division", profile.tenthDivision, "10th division", "matric division", "10th grade")

        // 3. 12th Academic Info
        addRule("12th Roll Number", profile.twelfthRollNo, "12th roll", "inter roll", "intermediate roll", "higher secondary roll", "sr secondary roll")
        addRule("12th Registration No", profile.twelfthRegNo, "12th reg", "inter reg", "12th enrollment")
        addRule("12th Passing Year", profile.twelfthPassingYear, "12th year", "inter year", "12th pass year", "year of passing 12th")
        addRule("12th Board", profile.twelfthBoard, "12th board", "inter board", "higher secondary board", "intermediate board")
        addRule("12th Marks / %", profile.twelfthMarks, "12th marks", "inter marks", "12th percentage", "12th aggregate")
        addRule("12th Division", profile.twelfthDivision, "12th division", "inter division", "12th grade")

        // 4. Dynamic Bio Values (docId == null)
        val bioValues = container.fieldValueDao.getBioValuesOnce(profile.id)
        val defsList = container.fieldDefDao.getApprovedDefs()
        val defMap = defsList.associateBy { it.key }

        for (bv in bioValues) {
            val def = defMap[bv.fieldKey]
            val label = def?.label ?: bv.fieldKey.substringAfterLast('.').replace('_', ' ').capitalizeWords()
            val extraSyns = parseSynonyms(def?.synonymsJson)
            addRule(label, bv.valueEnc, *extraSyns.toTypedArray())
        }

        // 5. Saved Document Records & Sub-Bio Values
        val docRecords = container.docRecordDao.getDocsForProfileOnce(profile.id)
        for (doc in docRecords) {
            val docValues = container.fieldValueDao.getDocValuesOnce(profile.id, doc.id)
            for (dv in docValues) {
                val def = defMap[dv.fieldKey]
                val fieldLabel = def?.label ?: dv.fieldKey.substringAfterLast('.').replace('_', ' ').capitalizeWords()
                val fullLabel = "${doc.title} - $fieldLabel"
                val extraSyns = parseSynonyms(def?.synonymsJson)
                val docSpecificSyns = extraSyns.map { "${doc.docType.lowercase()} $it" } + extraSyns
                addRule(fullLabel, dv.valueEnc, *docSpecificSyns.toTypedArray())
            }
        }

        rules
    }

    private fun parseSynonyms(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Executes autofill inside WebView with the provided rules.
     */
    suspend fun executeAutofill(
        webView: WebView,
        rules: List<AutofillRule>,
        overwriteExisting: Boolean = false
    ): AutofillResult = withContext(Dispatchers.Main) {
        if (rules.isEmpty()) return@withContext AutofillResult(0, emptyList())

        // Build JSON payload of rules
        val jsonRules = JSONArray().apply {
            for (rule in rules) {
                put(JSONObject().apply {
                    put("label", rule.label)
                    put("value", rule.value)
                    put("keywords", JSONArray(rule.keywords))
                })
            }
        }

        val jsCode = """
(function(rules, overwrite) {
    try {
        var filledCount = 0;
        var filledLabels = [];
        var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=file]):not([type=checkbox]):not([type=radio]), textarea, select'));
        
        function getFieldHints(el) {
            var tokens = [];
            if (el.name) tokens.push(el.name);
            if (el.id) tokens.push(el.id);
            if (el.placeholder) tokens.push(el.placeholder);
            if (el.getAttribute('aria-label')) tokens.push(el.getAttribute('aria-label'));
            if (el.getAttribute('title')) tokens.push(el.getAttribute('title'));
            
            // Associated labels
            if (el.labels && el.labels.length) {
                for (var i = 0; i < el.labels.length; i++) {
                    tokens.push(el.labels[i].innerText || el.labels[i].textContent || '');
                }
            }
            if (el.id) {
                var lbl = document.querySelector('label[for="' + el.id + '"]');
                if (lbl) tokens.push(lbl.innerText || lbl.textContent || '');
            }
            
            // Previous text sibling or parent label
            var p = el.previousElementSibling;
            if (p && (p.tagName === 'LABEL' || p.tagName === 'SPAN' || p.tagName === 'DIV' || p.tagName === 'B' || p.tagName === 'STRONG')) {
                tokens.push(p.innerText || p.textContent || '');
            }
            var parent = el.parentElement;
            if (parent && parent.tagName === 'TD') {
                var prevTd = parent.previousElementSibling;
                if (prevTd) tokens.push(prevTd.innerText || prevTd.textContent || '');
            }
            
            return tokens.join(' ').toLowerCase().replace(/[^a-z0-9\s]/g, ' ');
        }
        
        function setElementValue(el, val) {
            if (el.tagName === 'SELECT') {
                var normVal = val.toLowerCase().trim();
                var found = false;
                for (var i = 0; i < el.options.length; i++) {
                    var optText = el.options[i].text.toLowerCase().trim();
                    var optVal = el.options[i].value.toLowerCase().trim();
                    if (optText === normVal || optVal === normVal || optText.indexOf(normVal) !== -1 || normVal.indexOf(optText) !== -1) {
                        el.selectedIndex = i;
                        found = true;
                        break;
                    }
                }
                if (!found && normVal.length === 1) { // e.g. M / F
                    for (var i = 0; i < el.options.length; i++) {
                        if (el.options[i].value.toLowerCase().startsWith(normVal) || el.options[i].text.toLowerCase().startsWith(normVal)) {
                            el.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                }
            } else {
                el.value = val;
            }
            
            // Dispatch events for modern frameworks (React, Angular, Vue)
            el.dispatchEvent(new Event('focus', { bubbles: true }));
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            el.dispatchEvent(new Event('blur', { bubbles: true }));
            
            // Subtle visual highlight
            var prevBg = el.style.backgroundColor;
            var prevOutline = el.style.outline;
            el.style.backgroundColor = 'rgba(34, 197, 94, 0.15)';
            el.style.outline = '2px solid #22c55e';
            setTimeout(function() {
                el.style.backgroundColor = prevBg;
                el.style.outline = prevOutline;
            }, 3000);
        }
        
        for (var idx = 0; idx < inputs.length; idx++) {
            var input = inputs[idx];
            var curVal = (input.tagName === 'SELECT') ? (input.value || '') : (input.value || '').trim();
            if (!overwrite && curVal.length > 0 && curVal !== '0') {
                continue; // Skip already filled inputs
            }
            
            var hints = getFieldHints(input);
            if (!hints || hints.length < 2) continue;
            
            var matchedRule = null;
            var bestScore = 0;
            
            for (var r = 0; r < rules.length; r++) {
                var rule = rules[r];
                for (var k = 0; k < rule.keywords.length; k++) {
                    var kw = rule.keywords[k].trim();
                    if (kw.length >= 2 && hints.indexOf(kw) !== -1) {
                        var score = kw.length;
                        if (score > bestScore) {
                            bestScore = score;
                            matchedRule = rule;
                        }
                    }
                }
            }
            
            if (matchedRule && bestScore > 0) {
                setElementValue(input, matchedRule.value);
                filledCount++;
                if (filledLabels.indexOf(matchedRule.label) === -1) {
                    filledLabels.push(matchedRule.label);
                }
            }
        }
        
        return JSON.stringify({
            filledCount: filledCount,
            filledFields: filledLabels
        });
    } catch(err) {
        return JSON.stringify({
            filledCount: 0,
            filledFields: [],
            error: err.toString()
        });
    }
})(${jsonRules.toString()}, ${overwriteExisting});
        """.trimIndent()

        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(jsCode) { rawResponse ->
                try {
                    val unescaped = if (rawResponse != null && rawResponse.startsWith("\"") && rawResponse.endsWith("\"")) {
                        org.json.JSONTokener(rawResponse).nextValue() as? String ?: rawResponse
                    } else rawResponse

                    if (unescaped.isNullOrBlank() || unescaped == "null") {
                        continuation.resume(AutofillResult(0, emptyList()))
                        return@evaluateJavascript
                    }

                    val json = JSONObject(unescaped)
                    val count = json.optInt("filledCount", 0)
                    val arr = json.optJSONArray("filledFields")
                    val fields = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            fields.add(arr.getString(i))
                        }
                    }
                    continuation.resume(AutofillResult(filledCount = count, filledFields = fields))
                } catch (e: Exception) {
                    continuation.resume(AutofillResult(0, emptyList()))
                }
            }
        }
    }
}
